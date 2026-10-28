package dev.instablock.run

import android.graphics.PointF
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import dev.instablock.data.Outcome
import dev.instablock.debug.ScreenDump
import dev.instablock.i18n.Text
import dev.instablock.i18n.tx
import dev.instablock.instagram.Label
import dev.instablock.instagram.Labels
import dev.instablock.instagram.ViewIds
import dev.instablock.instagram.bounds
import dev.instablock.instagram.hasLabel
import dev.instablock.instagram.idName
import kotlinx.coroutines.delay

/**
 * Blocks one account inside Instagram:
 *
 *  1. Open the profile from its link and wait for it ([loadProfile]).
 *  2. Tap ⋯, then "Block" in the menu ([openBlockSheet]).
 *  3. Tap "Block" on the confirmation sheet ([tapConfirmButton]).
 *  4. Check that the profile now shows "Unblock" ([verifyBlocked]).
 *
 * Every element is looked up in this order, so the flow survives small Instagram changes:
 *  1. its view id ([ViewIds]): works in every language, but can change between versions;
 *  2. its English or French label ([Labels]);
 *  3. its position, such as "the rightmost button in the top bar";
 *  4. a coordinate derived from the screen size ([dev.instablock.input.Geometry]).
 *
 * Taps that could hit the wrong item (the menu, the confirmation sheet) wait until the target
 * has stopped moving, then check what's under the finger just before it lands.
 */
class BlockFlow(private val d: Driver) {
    private val screen get() = d.screen
    private val touch get() = d.touch

    private enum class ProfileState { READY, BLOCKED, MISSING }

    /** What the block confirmation sheet looks like right now. */
    private sealed interface SheetState {
        data class Ready(val button: Rect) : SheetState
        data object WrongSheet : SheetState
        data object RateLimited : SheetState
    }

    /** Result of opening the menu and tapping "Block". */
    private sealed interface MenuResult {
        data object SheetOpen : MenuResult
        data class Finished(val outcome: Outcome) : MenuResult
    }

    private fun log(msg: Text) = d.log(msg)

    suspend fun block(user: String): Outcome {
        when (loadProfile(user)) {
            null -> return Outcome.FAILED
            ProfileState.MISSING -> {
                log(
                    tx(
                        "   profil introuvable (inexistant, désactivé ou masqué pour vous, par ex. après un blocage)",
                        "   profile unavailable (doesn't exist, deactivated, or hidden from you, e.g. after a block)",
                    ),
                )
                return Outcome.UNAVAILABLE
            }
            ProfileState.BLOCKED -> {
                log(tx("   déjà bloqué : oui (le profil affiche Débloquer)", "   already blocked: yes (profile shows Unblock)"))
                return Outcome.ALREADY_BLOCKED
            }
            ProfileState.READY -> Unit
        }
        touch.idle(100, 300) // glance at the profile like a person would

        val menu = openBlockSheet(user)
        if (menu is MenuResult.Finished) return menu.outcome

        if (!tapConfirmButton()) {
            log(
                tx(
                    "   la confirmation bougeait encore ; impossible de la toucher sans risque",
                    "   Block confirmation kept moving; could not tap it safely",
                ),
            )
            d.dismissOverlays()
            return Outcome.FAILED
        }
        return verifyBlocked(user)
    }

    // --- 1. Profile -------------------------------------------------------------------------

    /**
     * Opens [user]'s profile and waits until it's showing. Returns null if it never loaded.
     *
     * The page sometimes stays blank, with nothing in its view tree. Sending the link again
     * often fixes it; if not, leaving Instagram and coming back does.
     */
    private suspend fun loadProfile(user: String): ProfileState? {
        // A "user not found" page left by the previous account would make this one look missing.
        if (pageMissing(screen.nodes())) d.goBack()
        d.openProfile(user)

        var state = d.timedWait(Step.PROFILE, FIRST_LOAD_WAIT_MS) { profileState(user) }
        if (state == null) {
            log(
                tx(
                    "   rien de reconnaissable après ${FIRST_LOAD_WAIT_MS / 1000} s ; réouverture du lien",
                    "   nothing recognisable after ${FIRST_LOAD_WAIT_MS / 1000}s; opening the link again",
                ),
            )
            d.openProfile(user)
            state = d.timedWait(Step.PROFILE, RELOAD_WAIT_MS) { profileState(user) }
        }
        if (state == null) {
            log(tx("   toujours vide ; sortie d'Instagram puis retour", "   still blank; leaving Instagram and coming back"))
            d.leaveAndReturn()
            state = d.timedWait(Step.PROFILE, RELOAD_WAIT_MS) { profileState(user) }
        }
        if (state == null) {
            ScreenDump.save(d.svc)
            val title = screen.nodes().firstOrNull { it.idName() == ViewIds.PROFILE_TITLE }?.text
            log(
                if (title != null) {
                    tx("   affiche toujours @$title, pas @$user", "   still showing @$title, not @$user")
                } else {
                    tx("   le profil ne s'est pas chargé", "   profile did not load")
                },
            )
        }
        return state
    }

    /** The state of [user]'s profile, or null while it isn't showing yet. */
    private fun profileState(user: String): ProfileState? {
        val nodes = screen.nodes()
        if (pageMissing(nodes)) return ProfileState.MISSING

        // Right after the link is sent, the previous profile is still showing.
        val title = nodes.firstOrNull { it.idName() == ViewIds.PROFILE_TITLE }
        if (title != null && Labels.normalize(title.text) != user) return null

        // The follow button appears after the top bar, so the profile counts as loaded once
        // it's there. It reads "Unblock" on a blocked profile.
        val followButton = nodes.firstOrNull { it.idName() == ViewIds.FOLLOW_BUTTON }
        if (followButton != null) {
            return if (followButton.hasLabel(Label.UNBLOCK)) ProfileState.BLOCKED else ProfileState.READY
        }

        // Fallback if Instagram renames the ids. The bottom tab bar is left out because its
        // labels ("Message"...) look like profile buttons.
        val content = nodes.filter { !d.geo.inBottomBar(it.bounds().centerY()) }
        if (content.any { it.hasLabel(Label.UNBLOCK) }) return ProfileState.BLOCKED
        return if (content.any { it.hasLabel(Label.FOLLOW) }) ProfileState.READY else null
    }

    /**
     * True on Instagram's "user not found" page, shown for accounts that don't exist, are
     * deactivated, or are hidden from you (some blocked ones included). There's nothing to do
     * on that page: no title, no follow button, no menu.
     */
    private fun pageMissing(nodes: List<AccessibilityNodeInfo>): Boolean =
        nodes.any { it.idName() == ViewIds.NOT_FOUND_TEXT || Labels.matches(Label.NOT_AVAILABLE, it.text) }

    // --- 2. Menu ----------------------------------------------------------------------------

    /**
     * Opens the ⋯ menu and taps "Block", until the block confirmation sheet is open. Tried
     * twice: on big profiles the menu can shift while it opens, and a tap meant for "Block"
     * could land on "Restrict" just above it, which is then undone.
     */
    private suspend fun openBlockSheet(user: String): MenuResult {
        // Whether the account was restricted before we started, to tell an accidental
        // Restrict apart from one the user set on purpose.
        var wasRestricted: Boolean? = null

        for (attempt in 1..2) {
            if (attempt > 1 && d.waitFor(4_000) { profileState(user) } != ProfileState.READY) {
                log(tx("   profil perdu pendant le nouvel essai", "   lost the profile while retrying"))
                return MenuResult.Finished(Outcome.FAILED)
            }

            tapOverflow()
            val entry = settledMenuEntry(Label.BLOCK, Label.UNBLOCK, timed = true)
            if (entry == null) {
                log(tx("   pas d'option Bloquer dans le menu", "   no Block entry in the menu"))
                ScreenDump.save(d.svc)
                d.dismissOverlays()
                return MenuResult.Finished(Outcome.FAILED)
            }
            if (entry.hasLabel(Label.UNBLOCK)) {
                log(tx("   déjà bloqué : oui (le menu affiche Débloquer)", "   already blocked: yes (menu shows Unblock)"))
                d.dismissOverlays()
                return MenuResult.Finished(Outcome.ALREADY_BLOCKED)
            }
            if (wasRestricted == null) wasRestricted = logMenuOptions()

            if (!tapMenuEntry(Label.BLOCK)) {
                log(
                    tx(
                        "   le menu bougeait encore ; impossible de toucher Bloquer sans risque",
                        "   menu kept moving; could not tap Block safely",
                    ),
                )
                d.dismissOverlays()
                return MenuResult.Finished(Outcome.FAILED)
            }

            when (settledConfirmButton(timed = true)) {
                SheetState.RateLimited -> return MenuResult.Finished(Outcome.RATE_LIMITED)
                is SheetState.Ready -> return MenuResult.SheetOpen
                SheetState.WrongSheet, null -> Unit
            }

            // Something other than the block sheet opened, or nothing did.
            val seen = screen.nodes()
                .filter { it.idName() in ViewIds.SHEET_TEXTS }
                .mapNotNull { (it.text ?: it.contentDescription)?.toString() }
                .distinct()
                .joinToString(" | ")
            log(
                tx(
                    "   la confirmation de blocage n'est pas apparue ; à l'écran : ${seen.ifEmpty { "aucune feuille" }}",
                    "   Block confirmation did not appear; screen shows: ${seen.ifEmpty { "no sheet" }}",
                ),
            )
            ScreenDump.save(d.svc)
            d.dismissOverlays()
            undoAccidentalRestrict(wasRestricted == true)
        }
        return MenuResult.Finished(Outcome.FAILED)
    }

    /** Logs whether the menu offers Restrict. Returns true if the account is already restricted. */
    private fun logMenuOptions(): Boolean {
        val entries = menuEntries()
        val restricted = entries.any { it.hasLabel(Label.UNRESTRICT) }
        log(tx("   déjà bloqué : non", "   already blocked: no"))
        log(
            tx("   option Restreindre : ", "   Restrict option: ") + when {
                restricted -> tx("déjà restreint (le menu affiche Ne plus restreindre)", "already restricted (menu shows Unrestrict)")
                entries.any { it.hasLabel(Label.RESTRICT) } -> tx("présente", "shown")
                else -> tx("absente", "not shown")
            },
        )
        return restricted
    }

    /** Taps the ⋯ button at the top right of the profile. */
    private suspend fun tapOverflow() {
        val nodes = screen.nodes()
        val geo = d.geo
        val byId = nodes.filter { it.isClickable && it.parent?.idName() == ViewIds.OVERFLOW_PARENT }
            .maxByOrNull { it.bounds().right }
        val byLabel = nodes.firstOrNull {
            geo.inTopBar(it.bounds().centerY()) && Labels.matches(Label.OPTIONS, it.contentDescription)
        }
        // On someone else's profile, ⋯ is the rightmost small button in the top bar.
        val byPosition = nodes.filter {
            val b = it.bounds()
            it.isClickable && geo.inTopBar(b.centerY()) && b.centerX() > geo.width * 0.7 && b.width() < geo.width / 4
        }.maxByOrNull { it.bounds().right }

        val (node, how) = when {
            byId != null -> byId to tx("id", "id")
            byLabel != null -> byLabel to tx("libellé", "label")
            byPosition != null -> byPosition to tx("position", "position")
            else -> null to tx("coordonnées de secours", "screen-size fallback")
        }
        log(tx("   bouton menu : ", "   menu button: ") + how)
        if (node != null) {
            touch.tap(screen.tapTarget(node, geo.height))
        } else {
            touch.tapNear(geo.profileOverflow.x, geo.profileOverflow.y, geo.dp(3))
        }
    }

    /** Entries of the open menu, preferring Instagram's own menu item views over loose labels. */
    private fun menuEntries(): List<AccessibilityNodeInfo> {
        val nodes = screen.nodes()
        val items = nodes.filter { it.idName() == ViewIds.MENU_ITEM }
        return items.ifEmpty { nodes.filter { !it.isEditable && (it.text != null || it.contentDescription != null) } }
    }

    /** The menu entry under a screen point, or null if there's none. */
    private fun menuEntryAt(p: PointF): AccessibilityNodeInfo? =
        menuEntries()
            .map { it to screen.tapTarget(it, d.geo.height) }
            .filter { (_, area) -> area.contains(p.x.toInt(), p.y.toInt()) }
            .minByOrNull { (_, area) -> area.width() * area.height() }
            ?.first

    /**
     * Waits for a menu entry with one of [labels], and returns it once it has stayed in the
     * same place for [SETTLE_MS]: menus can grow or slide while they open.
     *
     * With [timed], this is the [Step.MENU] step: the time until the entry first appears is
     * profiled, and the entry isn't returned before the step's minimum wait.
     */
    private suspend fun settledMenuEntry(vararg labels: Label, timed: Boolean = false): AccessibilityNodeInfo? {
        val start = System.currentTimeMillis()
        var recorded = false
        var lastBounds: Rect? = null
        var stillSince = 0L
        return d.waitFor(6_000) {
            val entry = menuEntries().firstOrNull { e -> labels.any { e.hasLabel(it) } }
            val bounds = entry?.let { screen.tapTarget(it, d.geo.height) }
            val now = System.currentTimeMillis()
            if (timed && entry != null && !recorded) {
                recorded = true
                d.pacing.record(Step.MENU, now - start)
            }
            when {
                entry == null -> { lastBounds = null; null }
                bounds != lastBounds -> { lastBounds = bounds; stillSince = now; null }
                now - stillSince < SETTLE_MS -> null
                timed && now - start < d.pacing.minWait(Step.MENU) -> null
                else -> entry
            }
        }
    }

    /**
     * Taps the menu entry with [label], checking just before the finger lands that the entry
     * under that point is still the right one. Returns false if the menu kept moving.
     */
    private suspend fun tapMenuEntry(label: Label): Boolean {
        repeat(3) {
            val entry = settledMenuEntry(label) ?: return false
            touch.idle(100, 300)
            val p = touch.pointIn(screen.tapTarget(entry, d.geo.height))
            touch.react()
            val under = menuEntryAt(p)
            val underText = under?.text ?: under?.contentDescription
            val at = "${p.x.toInt()},${p.y.toInt()}"
            if (under?.hasLabel(label) == true) {
                touch.tapAt(p.x, p.y, react = false)
                log(tx("   touché « $underText » en $at", "   tapped \"$underText\" at $at"))
                return true
            }
            log(
                tx(
                    "   le menu a bougé sous le doigt (trouvé « $underText ») ; nouvelle lecture",
                    "   menu moved under the tap point (found \"$underText\"); reading it again",
                ),
            )
        }
        return false
    }

    /**
     * If the menu now offers "Unrestrict" when it didn't before, a tap meant for "Block" hit
     * "Restrict": undo it. Leaves the profile with no menu or sheet open.
     */
    private suspend fun undoAccidentalRestrict(wasRestricted: Boolean) {
        if (wasRestricted) return
        tapOverflow()
        val entry = settledMenuEntry(Label.BLOCK, Label.UNBLOCK, Label.UNRESTRICT)
        if (entry == null || screen.nodes().none { it.hasLabel(Label.UNRESTRICT) }) {
            d.dismissOverlays()
            return
        }
        log(tx("   Restreindre activé par erreur ; annulation", "   Restrict was applied by mistake; undoing it"))
        if (!tapMenuEntry(Label.UNRESTRICT)) {
            log(
                tx(
                    "   impossible d'annuler Restreindre ; à faire à la main pour ce compte",
                    "   could not undo Restrict; unrestrict this account by hand",
                ),
            )
            d.dismissOverlays()
            return
        }
        // Some versions ask for confirmation with a second "Unrestrict" button.
        delay(500)
        d.waitFor(3_000) { screen.nodes().firstOrNull { it.isClickable && it.hasLabel(Label.UNRESTRICT) } }
            ?.let { touch.idle(300, 700); touch.tap(it.bounds()) }
        delay(500)
        d.dismissOverlays()
    }

    // --- 3. Confirmation sheet --------------------------------------------------------------

    /** The block confirmation sheet's "Block" button, another sheet, a rate limit, or null. */
    private fun confirmButton(): SheetState? {
        val nodes = screen.nodes()
        if (nodes.any { it.hasLabel(Label.TRY_AGAIN_LATER) }) return SheetState.RateLimited
        val primary = nodes.firstOrNull { it.idName() == ViewIds.CONFIRM_BUTTON }
        return when {
            primary != null && primary.hasLabel(Label.BLOCK) -> SheetState.Ready(primary.bounds())
            primary != null -> SheetState.WrongSheet
            // Fallback if the id changes: once the menu window has closed, the lowest "Block"
            // button on screen is the sheet's main one.
            screen.instagramWindowCount() == 1 ->
                nodes.filter { it.idName() != ViewIds.MENU_ITEM && it.hasLabel(Label.BLOCK) }
                    .map { screen.tapTarget(it, d.geo.height) }
                    .maxByOrNull { it.bottom }
                    ?.let { SheetState.Ready(it) }
            else -> null
        }
    }

    /**
     * Waits for the confirmation sheet's "Block" button, and returns it once it has stayed in
     * the same place for [SETTLE_MS]. The sheet slides up from the bottom, so a position read
     * mid-slide is too low and lands on "Block and report" underneath.
     *
     * With [timed], this is the [Step.SHEET] step, measured when the sheet has stopped moving.
     */
    private suspend fun settledConfirmButton(timed: Boolean = false): SheetState? {
        val start = System.currentTimeMillis()
        var recorded = false
        var lastBounds: Rect? = null
        var stillSince = 0L
        return d.waitFor(6_000) {
            val state = confirmButton()
            val now = System.currentTimeMillis()
            when (state) {
                null -> { lastBounds = null; null }
                SheetState.RateLimited, SheetState.WrongSheet -> state
                is SheetState.Ready -> when {
                    state.button != lastBounds -> { lastBounds = state.button; stillSince = now; null }
                    now - stillSince < SETTLE_MS -> null
                    else -> {
                        if (timed && !recorded) {
                            recorded = true
                            d.pacing.record(Step.SHEET, stillSince - start)
                        }
                        if (timed && now - start < d.pacing.minWait(Step.SHEET)) null else state
                    }
                }
            }
        }
    }

    /**
     * Taps "Block" on the confirmation sheet, checking just before the finger lands that the
     * point is still inside it and not inside "Block and report". Returns false if the sheet
     * kept moving.
     */
    private suspend fun tapConfirmButton(): Boolean {
        repeat(3) {
            val ready = settledConfirmButton() as? SheetState.Ready ?: return false
            touch.idle(100, 300)
            val p = touch.pointIn(ready.button)
            touch.react()
            val x = p.x.toInt()
            val y = p.y.toInt()
            val button = (confirmButton() as? SheetState.Ready)?.button
            val onSecondary = screen.nodes().any { it.idName() == ViewIds.SECONDARY_BUTTON && it.bounds().contains(x, y) }
            if (button != null && button.contains(x, y) && !onSecondary) {
                touch.tapAt(p.x, p.y, react = false)
                log(tx("   confirmation touchée en $x,$y", "   tapped confirm at $x,$y"))
                return true
            }
            log(
                tx(
                    "   la confirmation a bougé sous le doigt ; nouvelle lecture",
                    "   Block confirmation moved under the tap point; reading it again",
                ),
            )
        }
        return false
    }

    // --- 4. Result --------------------------------------------------------------------------

    /**
     * After confirming, Instagram may show a "blocked" sheet with Dismiss, then the profile
     * shows "Unblock". Returns [Outcome.BLOCKED] once "Unblock" is seen.
     */
    private suspend fun verifyBlocked(user: String): Outcome {
        val result = d.timedWait(Step.RESULT, 8_000) {
            val nodes = screen.nodes()
            when {
                nodes.any { it.hasLabel(Label.TRY_AGAIN_LATER) } -> Label.TRY_AGAIN_LATER
                nodes.any { it.hasLabel(Label.UNBLOCK) } -> Label.UNBLOCK
                nodes.any { it.hasLabel(Label.DISMISS) } -> Label.DISMISS
                else -> null
            }
        }
        val verified = when (result) {
            Label.TRY_AGAIN_LATER -> return Outcome.RATE_LIMITED
            Label.UNBLOCK -> true
            Label.DISMISS -> {
                touch.idle(300, 800)
                screen.nodes().firstOrNull { it.hasLabel(Label.DISMISS) }
                    ?.let { touch.tap(screen.tapTarget(it, d.geo.height)) }
                d.waitFor(5_000) { screen.nodes().firstOrNull { it.hasLabel(Label.UNBLOCK) } } != null
            }
            else -> recoverAfterConfirm(user)
        }
        return if (verified) Outcome.BLOCKED else Outcome.BLOCKED_UNVERIFIED
    }

    /**
     * Neither "Unblock" nor "Dismiss" appeared after confirming: something else opened, such
     * as the report flow. Saves a capture and logs what's on screen, then presses Back while a
     * menu or sheet covers the profile. Returns true if the profile then shows "Unblock".
     */
    private suspend fun recoverAfterConfirm(user: String): Boolean {
        val texts = screen.nodes()
            .mapNotNull { (it.text ?: it.contentDescription)?.toString()?.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(8)
            .joinToString(" | ")
        log(
            tx(
                "   pas de Débloquer après confirmation ; à l'écran : ${texts.ifEmpty { "rien" }}",
                "   no Unblock after confirming; screen shows: ${texts.ifEmpty { "nothing" }}",
            ),
        )
        ScreenDump.save(d.svc)
        repeat(3) {
            if (profileState(user) == ProfileState.BLOCKED) {
                log(tx("   le profil affiche Débloquer", "   profile shows Unblock"))
                return true
            }
            if (!d.overlayOpen()) {
                log(tx("   aucun menu ni feuille ouvert ; pas de retour arrière", "   no menu or sheet open; not pressing Back"))
                return false
            }
            d.goBack()
        }
        return d.waitFor(2_000) { if (profileState(user) == ProfileState.BLOCKED) true else null } ?: false
    }

    companion object {
        /** How long a menu entry or button must stay still before it's tapped. */
        private const val SETTLE_MS = 400L

        /** Wait for a profile before sending its link again, then after each later attempt. */
        private const val FIRST_LOAD_WAIT_MS = 4_000L
        private const val RELOAD_WAIT_MS = 10_000L
    }
}
