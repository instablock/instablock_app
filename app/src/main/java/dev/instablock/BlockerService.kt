package dev.instablock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import dev.instablock.i18n.Text
import dev.instablock.i18n.joinText
import dev.instablock.i18n.tx
import dev.instablock.run.Abort
import dev.instablock.run.Runner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One line of the on-screen log. [text] is shown in the current interface language. */
class LogLine(val time: String, val text: Text)

/**
 * The accessibility service: the only part of the app allowed to read Instagram's screen and
 * tap inside it. It runs the [Runner] and keeps the log.
 *
 * [MainActivity] talks to it through the state flows in the companion object, which stay
 * valid while the service is off.
 */
class BlockerService : AccessibilityService() {

    companion object {
        private const val TAG = "InstaBlock"

        /** Lines kept in the on-screen log. The log file keeps everything. */
        private const val MAX_LOG_LINES = 300

        /** The running service, or null while the accessibility service is off. */
        @Volatile
        var instance: BlockerService? = null
            private set

        private val _connected = MutableStateFlow(false)

        /** Whether the accessibility service is on. It can connect after the app has opened. */
        val connected: StateFlow<Boolean> = _connected

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running

        private val _log = MutableStateFlow<List<LogLine>>(emptyList())
        val logLines: StateFlow<List<LogLine>> = _log

        private val _finished = MutableStateFlow<Text?>(null)

        /** Message for the end of the last run, until the app has shown it. */
        val finished: StateFlow<Text?> = _finished

        private val _resumeCountdown = MutableStateFlow<Int?>(null)

        /** Seconds left before the run resumes after Instagram left the screen, or null. */
        val resumeCountdown: StateFlow<Int?> = _resumeCountdown

        private var resumeAnswer: CompletableDeferred<Boolean>? = null

        fun finishedShown() {
            _finished.value = null
        }

        /** The user's answer to the resume countdown: true to resume now, false to stop. */
        fun answerResume(resume: Boolean) {
            resumeAnswer?.complete(resume)
        }

        /** Clears the on-screen log. The log file is kept. */
        fun clearLog() {
            _log.value = emptyList()
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val dayFormat = SimpleDateFormat("yyyyMMdd", Locale.US)

    // --- Service lifecycle ------------------------------------------------------------------

    override fun onServiceConnected() {
        instance = this
        _connected.value = true
        log(tx("Service d'accessibilité connecté", "Accessibility service connected"))
    }

    // Nothing to do with the events themselves. The service still subscribes to all of them
    // (see accessibility_config.xml), because Android refreshes its cached copy of the screen
    // only from events delivered to the service.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        instance = null
        _connected.value = false
        scope.cancel()
        releaseWakeLock()
        _running.value = false
        super.onDestroy()
    }

    // --- Runs -------------------------------------------------------------------------------

    /** Starts a run over the pending accounts, unless one is already going. */
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            _running.value = true
            acquireWakeLock()
            val runner = Runner(this@BlockerService)
            val headline = try {
                runner.run()
            } catch (e: Abort) {
                val stopped = tx("Arrêt : ", "Stopped: ") + e.text
                log(stopped)
                stopped
            } catch (e: CancellationException) {
                throw e // the user pressed Stop
            } catch (e: Throwable) {
                // A bug in the app. Uncaught, it would crash the app, and Android turns off the
                // accessibility service of an app that keeps crashing. Stop the run instead.
                Log.e(TAG, "Run failed", e)
                val failed = tx("Arrêt : erreur inattendue ($e)", "Stopped: unexpected error ($e)")
                log(failed)
                failed
            } finally {
                releaseWakeLock()
                _running.value = false
            }
            // Not reached when the user pressed Stop: they're already in the app.
            showFinished(listOf(headline, runner.summary()).filter { it.en.isNotEmpty() }.joinText("\n\n"))
        }
    }

    fun stop() {
        job?.cancel()
        log(tx("Arrêté par l'utilisateur", "Stopped by user"))
    }

    /**
     * Brings InstaBlock to the front with a [seconds] countdown. Returns true to resume (the
     * countdown ran out, or the user chose to resume now), false if the user stopped the run.
     */
    suspend fun askToResume(seconds: Int): Boolean {
        val answer = CompletableDeferred<Boolean>()
        resumeAnswer = answer
        bringAppToFront()
        try {
            for (left in seconds downTo 1) {
                _resumeCountdown.value = left
                withTimeoutOrNull(1_000) { answer.await() }?.let { return it }
            }
            return true
        } finally {
            _resumeCountdown.value = null
            resumeAnswer = null
        }
    }

    /** Brings InstaBlock back to the front with the end-of-run message. */
    private fun showFinished(message: Text) {
        _finished.value = message
        bringAppToFront()
    }

    private fun bringAppToFront() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
    }

    // --- Log --------------------------------------------------------------------------------

    /**
     * Adds a line to the on-screen log (shown in the interface language), and in English to
     * logcat and to Android/data/dev.instablock/files/logs/log-<day>.txt.
     */
    fun log(msg: Text) {
        Log.i(TAG, msg.en)
        val now = Date()
        val time = timeFormat.format(now)
        _log.value = (_log.value + LogLine(time, msg)).takeLast(MAX_LOG_LINES)
        runCatching {
            File(getExternalFilesDir("logs"), "log-${dayFormat.format(now)}.txt").appendText("$time  ${msg.en}\n")
        }
    }

    // --- Wake lock --------------------------------------------------------------------------

    /** Keeps the screen on for the whole run, breaks included. */
    private fun acquireWakeLock() {
        val pm = getSystemService(PowerManager::class.java)
        @Suppress("DEPRECATION") // the screen must stay on, which the newer APIs can't do from a service
        wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "instablock:run").apply {
            acquire(6 * 60 * 60 * 1000L) // released at the end of the run; 6 h is only a safety limit
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }
}
