package dev.instablock.run

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import dev.instablock.BlockerService
import dev.instablock.i18n.Text
import dev.instablock.i18n.tx
import dev.instablock.input.Geometry
import dev.instablock.input.Touch
import dev.instablock.instagram.INSTAGRAM_PACKAGE
import dev.instablock.instagram.Screen
import dev.instablock.instagram.ViewIds
import dev.instablock.instagram.idName
import dev.instablock.instagram.profileUrl
import kotlinx.coroutines.delay

/**
 * The tools a run uses to work inside Instagram: read the screen, tap, wait for something to
 * appear, and move between screens. One instance per run, shared by [Runner] (the loop over
 * accounts) and [BlockFlow] (the steps for one account).
 */
class Driver(val svc: BlockerService) {
    val screen = Screen(svc)
    val touch = Touch(svc)
    val pacing = Pacing()

    /** Measured once at the start of the run by [measureScreen]. */
    lateinit var geo: Geometry
        private set

    /** Last time Instagram was seen in the foreground, for [checkInFront]. */
    private var lastSeenInFront = System.currentTimeMillis()

    fun log(msg: Text) = svc.log(msg)

    fun measureScreen() {
        geo = Geometry.measure(svc)
    }

    // --- Waiting ----------------------------------------------------------------------------

    /**
     * Looks at the screen with [probe] until it returns something, or [timeoutMs] passes.
     *
     * With [requireInstagram], throws [LeftInstagram] if Instagram stays out of the foreground
     * for [LEFT_INSTAGRAM_TIMEOUT_MS].
     */
    suspend fun <T> waitFor(timeoutMs: Long, requireInstagram: Boolean = true, probe: () -> T?): T? {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (requireInstagram) checkInFront()
            probe()?.let { return it }
            delay(pacing.pollMs())
        }
        return null
    }

    /**
     * [waitFor] for a [step] that starts with the action just taken. While profiling, records
     * how long Instagram took. Afterwards, doesn't return before the step's minimum wait, and
     * looks at the screen again once that wait is over.
     */
    suspend fun <T> timedWait(step: Step, timeoutMs: Long, probe: () -> T?): T? {
        val start = System.currentTimeMillis()
        val found = waitFor(timeoutMs, probe = probe) ?: return null
        val elapsed = System.currentTimeMillis() - start
        pacing.record(step, elapsed)
        val remaining = pacing.minWait(step) - elapsed
        if (remaining <= 0) return found
        delay(remaining)
        return probe() ?: waitFor(timeoutMs, probe = probe)
    }

    private fun checkInFront() {
        val now = System.currentTimeMillis()
        if (screen.instagramInFront()) {
            lastSeenInFront = now
        } else if (now - lastSeenInFront > LEFT_INSTAGRAM_TIMEOUT_MS) {
            throw LeftInstagram()
        }
    }

    // --- Navigation -------------------------------------------------------------------------

    /** Starts Instagram from scratch, dropping any screens left over from earlier use. */
    suspend fun launchInstagram() {
        svc.startActivity(launchIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        waitFor(15_000, requireInstagram = false) { if (screen.instagramInFront()) true else null }
            ?: throw Abort(tx("Instagram ne s'est pas ouvert", "Instagram did not come to the foreground"))
        lastSeenInFront = System.currentTimeMillis()
        touch.idle(1_500, 3_000) // let the feed load before the first link
    }

    /** Opens [user]'s profile from its link, the way a link in a browser or message would. */
    fun openProfile(user: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(profileUrl(user)))
            .setPackage(INSTAGRAM_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        svc.startActivity(intent)
    }

    /** Goes to the home screen, then reopens Instagram where it was, like tapping its icon. */
    suspend fun leaveAndReturn() {
        touch.idle(200, 500)
        svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        touch.idle(700, 1_400)
        svc.startActivity(launchIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
        lastSeenInFront = System.currentTimeMillis()
    }

    suspend fun goBack() {
        touch.idle(250, 650)
        svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        touch.idle(350, 800)
    }

    /** True while a menu or bottom sheet covers the profile. */
    fun overlayOpen(): Boolean =
        screen.instagramWindowCount() > 1 || screen.nodes().any { it.idName() in ViewIds.OVERLAYS }

    /** Closes any menu or sheet with Back. Never presses Back without one, so it can't leave the profile. */
    suspend fun dismissOverlays() {
        repeat(3) {
            if (!overlayOpen()) return
            goBack()
        }
    }

    private fun launchIntent(): Intent =
        svc.packageManager.getLaunchIntentForPackage(INSTAGRAM_PACKAGE)
            ?: throw Abort(tx("Instagram n'est pas installé", "Instagram is not installed"))

    companion object {
        /** How long Instagram may be out of the foreground before the run reacts. */
        private const val LEFT_INSTAGRAM_TIMEOUT_MS = 5_000L
    }
}
