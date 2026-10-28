package dev.instablock

import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import dev.instablock.data.Store
import dev.instablock.i18n.L10n
import dev.instablock.i18n.Lang
import dev.instablock.instagram.INSTAGRAM_PACKAGE
import dev.instablock.ui.Guide
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The app's only screen: the list of accounts, Start/Stop, and the log.
 *
 * Every text is set in code, in the current language, by [applyTexts]. Runs happen in
 * [BlockerService]; this screen follows them through the service's state flows.
 */
class MainActivity : Activity() {
    private val scope = MainScope()

    private lateinit var serviceNotice: TextView
    private lateinit var settingsButton: ImageButton
    private lateinit var usernames: EditText
    private lateinit var summary: TextView
    private lateinit var startStop: Button
    private lateinit var logsPanel: View
    private lateinit var logsChevron: ImageView
    private lateinit var logView: TextView

    /** Last log lines received, kept to re-render them when the language changes. */
    private var logLines: List<LogLine> = emptyList()

    /** Gently pulses the settings button while the accessibility service is off. */
    private val settingsPulse = ValueAnimator.ofFloat(1f, 1.18f).apply {
        duration = 900
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
    }

    // --- Lifecycle --------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        L10n.load(this)
        setContentView(R.layout.activity_main)
        applyInsets(findViewById(R.id.root))

        serviceNotice = findViewById(R.id.serviceNotice)
        settingsButton = findViewById(R.id.openSettings)
        usernames = findViewById(R.id.usernames)
        summary = findViewById(R.id.summary)
        startStop = findViewById(R.id.startStop)
        logsPanel = findViewById(R.id.logsPanel)
        logsChevron = findViewById(R.id.logsChevron)
        logView = findViewById(R.id.log)

        Store.loadDefaultsIfNew(this)
        usernames.setText(Store.queue(this).joinToString("\n"))

        setUpListeners()
        applyTexts()
        observeService()
    }

    override fun onResume() {
        super.onResume()
        refreshServiceNotice()
        refreshSummary()
    }

    override fun onPause() {
        super.onPause()
        if (!BlockerService.running.value) saveList()
    }

    override fun onDestroy() {
        settingsPulse.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun setUpListeners() {
        val openSettings = View.OnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        settingsButton.setOnClickListener(openSettings)
        serviceNotice.setOnClickListener(openSettings)
        settingsPulse.addUpdateListener {
            val scale = it.animatedValue as Float
            settingsButton.scaleX = scale
            settingsButton.scaleY = scale
        }

        findViewById<ImageButton>(R.id.help).setOnClickListener { showGuide() }
        findViewById<TextView>(R.id.language).setOnClickListener {
            L10n.set(this, if (L10n.lang == Lang.FR) Lang.EN else Lang.FR)
            applyTexts()
        }

        startStop.setOnClickListener {
            if (BlockerService.running.value) BlockerService.instance?.stop() else start()
        }
        findViewById<TextView>(R.id.loadDefaults).setOnClickListener { confirmLoadDefaults() }
        findViewById<TextView>(R.id.resetResults).setOnClickListener { confirmClearResults() }
        findViewById<View>(R.id.logsHeader).setOnClickListener { setLogsOpen(logsPanel.visibility != View.VISIBLE) }
    }

    /** Follows the service: connection, running state, log, end of run, resume countdown. */
    private fun observeService() {
        scope.launch { BlockerService.connected.collect { refreshServiceNotice() } }
        scope.launch {
            BlockerService.running.collect { running ->
                renderStartStop()
                usernames.isEnabled = !running
                refreshSummary()
            }
        }
        scope.launch {
            BlockerService.logLines.collect {
                logLines = it
                renderLog()
            }
        }
        scope.launch {
            BlockerService.finished.collect { message ->
                if (message == null) return@collect
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(R.string.app_name)
                    .setMessage(message.toString())
                    .setPositiveButton("OK", null)
                    .setOnDismissListener { BlockerService.finishedShown() }
                    .show()
            }
        }
        scope.launch {
            var dialog: AlertDialog? = null
            BlockerService.resumeCountdown.collect { secondsLeft ->
                if (secondsLeft == null) {
                    dialog?.dismiss()
                    dialog = null
                    return@collect
                }
                val message = tr(
                    "Instagram a quitté l'écran (plantage, touche Accueil ou écran verrouillé).\n\n" +
                        "Reprise dans $secondsLeft s : Instagram va se rouvrir et le compte en cours sera réessayé.",
                    "Instagram left the screen (crash, Home, or locked screen).\n\n" +
                        "The run resumes in $secondsLeft s: Instagram reopens and the current account is tried again.",
                )
                dialog?.setMessage(message) ?: run {
                    dialog = AlertDialog.Builder(this@MainActivity)
                        .setTitle(R.string.app_name)
                        .setMessage(message)
                        .setCancelable(false)
                        .setNegativeButton(tr("Arrêter", "Stop the run")) { _, _ -> BlockerService.answerResume(false) }
                        .setPositiveButton(tr("Reprendre", "Resume now")) { _, _ -> BlockerService.answerResume(true) }
                        .show()
                }
            }
        }
    }

    // --- Texts and rendering ----------------------------------------------------------------

    private fun tr(fr: String, en: String) = if (L10n.lang == Lang.FR) fr else en

    /** Sets every text on screen in the current language. */
    private fun applyTexts() {
        findViewById<TextView>(R.id.language).apply {
            text = if (L10n.lang == Lang.FR) FLAG_FR else FLAG_GB
            contentDescription = tr("Switch to English", "Passer en français")
        }
        findViewById<ImageButton>(R.id.help).contentDescription = tr("Guide", "Guide")
        settingsButton.contentDescription = tr("Paramètres d'accessibilité", "Accessibility settings")
        findViewById<TextView>(R.id.listLabel).text = tr("Comptes à bloquer", "Accounts to block")
        findViewById<TextView>(R.id.loadDefaults).text = tr("Liste par défaut", "Default list")
        usernames.hint = tr("Un compte par ligne : nom, @nom ou lien du profil", "One account per line: name, @name or profile link")
        findViewById<TextView>(R.id.logsLabel).text = tr("Journal", "Logs")
        findViewById<TextView>(R.id.resetResults).text = tr("Effacer les résultats", "Clear results")
        renderStartStop()
        refreshServiceNotice()
        refreshSummary()
        renderLog()
    }

    /** Filled "Start" button when idle, outlined "Stop" button during a run. */
    private fun renderStartStop() {
        val running = BlockerService.running.value
        startStop.text = if (running) tr("Arrêter", "Stop") else tr("Démarrer", "Start")
        startStop.setBackgroundResource(if (running) R.drawable.bg_button_outline else R.drawable.bg_button_primary)
        startStop.setTextColor(getColor(if (running) R.color.text else R.color.on_accent))
    }

    /** Shows the banner and pulses the settings button while the accessibility service is off. */
    private fun refreshServiceNotice() {
        val on = BlockerService.connected.value
        serviceNotice.visibility = if (on) View.GONE else View.VISIBLE
        serviceNotice.text = tr(
            "Le service d'accessibilité InstaBlock est désactivé. Touchez ici pour l'activer.",
            "The InstaBlock accessibility service is off. Tap here to turn it on.",
        )
        if (on) {
            settingsPulse.cancel()
            settingsButton.scaleX = 1f
            settingsButton.scaleY = 1f
        } else if (!settingsPulse.isStarted) {
            settingsPulse.start()
        }
    }

    /** "619 comptes · 599 en attente · 17 bloqués" */
    private fun refreshSummary() {
        val results = Store.results(this)
        val queue = Store.queue(this)
        val counts = queue.mapNotNull { results[it] }.groupingBy { it }.eachCount().toSortedMap()
        val pending = Store.pending(this).size
        summary.text = buildString {
            append(tr("${queue.size} comptes · $pending en attente", "${queue.size} accounts · $pending pending"))
            counts.forEach { (outcome, n) -> append(" · ").append(n).append(' ').append(outcome.short) }
        }
    }

    /** Newest line first, so the latest step is visible without scrolling. */
    private fun renderLog() {
        logView.text = logLines.asReversed().joinToString("\n") { "${it.time}  ${it.text}" }
            .ifEmpty { tr("Rien pour l'instant.", "Nothing yet.") }
    }

    private fun setLogsOpen(open: Boolean) {
        logsPanel.visibility = if (open) View.VISIBLE else View.GONE
        logsChevron.rotation = if (open) 180f else 0f
    }

    // --- Actions ----------------------------------------------------------------------------

    private fun start() {
        val service = BlockerService.instance
            ?: return toast(tr("Activez d'abord le service d'accessibilité InstaBlock.", "Turn on the InstaBlock accessibility service first."))
        packageManager.getLaunchIntentForPackage(INSTAGRAM_PACKAGE)
            ?: return toast(tr("Instagram n'est pas installé.", "Instagram is not installed."))
        saveList()
        if (Store.pending(this).isEmpty()) return toast(tr("Plus aucun compte à bloquer.", "No accounts left to block."))
        service.start()
    }

    private fun confirmLoadDefaults() {
        if (BlockerService.running.value) return toast(tr("Arrêtez d'abord le lancement en cours.", "Stop the run first."))
        AlertDialog.Builder(this)
            .setMessage(
                tr(
                    "Remplacer la liste par la liste par défaut ? Vos modifications seront perdues.",
                    "Replace the list with the default one? Your edits will be lost.",
                ),
            )
            .setNegativeButton(tr("Annuler", "Cancel"), null)
            .setPositiveButton(tr("Remplacer", "Replace")) { _, _ ->
                val accounts = Store.defaultAccounts(this)
                Store.setQueue(this, accounts)
                usernames.setText(accounts.joinToString("\n"))
                refreshSummary()
            }
            .show()
    }

    private fun confirmClearResults() {
        if (BlockerService.running.value) return toast(tr("Arrêtez d'abord le lancement en cours.", "Stop the run first."))
        AlertDialog.Builder(this)
            .setMessage(
                tr(
                    "Effacer tous les résultats ? Tous les comptes de la liste repasseront en attente.",
                    "Clear all results? Every account in the list will be pending again.",
                ),
            )
            .setNegativeButton(tr("Annuler", "Cancel"), null)
            .setPositiveButton(tr("Effacer", "Clear")) { _, _ ->
                Store.clearResults(this)
                BlockerService.clearLog()
                refreshSummary()
            }
            .show()
    }

    private fun showGuide() {
        AlertDialog.Builder(this)
            .setTitle(Guide.title.toString())
            .setMessage(Guide.body.toString())
            .setPositiveButton("OK", null)
            .show()
    }

    /** Saves the list as typed, skipping lines that aren't valid usernames. */
    private fun saveList() {
        val lines = usernames.text.lines().filter { it.isNotBlank() }
        val parsed = lines.mapNotNull(Store::parseUsername)
        val skipped = lines.size - parsed.size
        if (skipped > 0) {
            toast(tr("$skipped ligne(s) ignorée(s) : noms de compte non valides.", "$skipped line(s) skipped: not valid usernames."))
        }
        Store.setQueue(this, parsed)
    }

    // --- Helpers ----------------------------------------------------------------------------

    /** The app draws behind the system bars (Android 15+), so pad for them and the keyboard. */
    private fun applyInsets(root: View) {
        root.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.ime() or WindowInsets.Type.displayCutout(),
                )
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(
                    insets.systemWindowInsetLeft,
                    insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight,
                    insets.systemWindowInsetBottom,
                )
            }
            insets
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private companion object {
        const val FLAG_FR = "🇫🇷"
        const val FLAG_GB = "🇬🇧"
    }
}
