package dev.instablock.run

import dev.instablock.i18n.Text
import dev.instablock.i18n.joinText
import dev.instablock.i18n.raw
import dev.instablock.i18n.tx
import dev.instablock.input.randomBetween

/** The steps where the app waits for Instagram, with the wait used if profiling never saw one. */
enum class Step(val label: Text, val fallbackMs: Long) {
    PROFILE(tx("profil", "profile"), 1_500),
    MENU(tx("menu", "menu"), 600),
    SHEET(tx("feuille de blocage", "block sheet"), 600),
    RESULT(tx("résultat", "block result"), 1_500),
}

/**
 * How long each [Step] gets before the app acts, learned at the start of each run.
 *
 * Profiling: during the first [PROFILE_ACCOUNTS] accounts, the app checks the screen every
 * [PROFILING_POLL_MS] and acts as soon as each screen is ready, recording how long Instagram
 * took. After that, each step waits at least the slowest time seen, plus a margin. A slow
 * phone or connection therefore gets more time.
 *
 * Slowing down: after a streak of failures, [slowDown] lengthens every wait, at most
 * [MAX_SLOWDOWNS] times per run.
 */
class Pacing {
    var profiling = true
        private set
    var slowdowns = 0
        private set

    private val slowest = mutableMapOf<Step, Long>()
    private val minWait = mutableMapOf<Step, Long>()
    private var profiledAccounts = 0

    /** Records how long a step took. Only counts while profiling. */
    fun record(step: Step, ms: Long) {
        if (profiling) slowest[step] = maxOf(slowest[step] ?: 0, ms)
    }

    /** Minimum time between the action that starts [step] and the app acting on its result. */
    fun minWait(step: Step): Long = minWait[step] ?: 0

    /** Time between two looks at the screen. */
    fun pollMs(): Long = if (profiling) PROFILING_POLL_MS else randomBetween(60, 120)

    /** Counts a handled account. Returns a log line when profiling has just ended. */
    fun accountDone(): Text? {
        if (!profiling) return null
        profiledAccounts++
        return if (profiledAccounts >= PROFILE_ACCOUNTS) finishProfiling() else null
    }

    /** Gives every step more time. Returns the log lines describing the change. */
    fun slowDown(): List<Text> {
        val ended = if (profiling) listOf(finishProfiling()) else emptyList()
        slowdowns++
        for (s in Step.entries) minWait[s] = (minWait(s) * SLOWDOWN_FACTOR).toLong() + SLOWDOWN_MS
        return ended + (tx("Ralentissement. Attentes désormais : ", "Slowing down. Waits from now on: ") + describe(minWait))
    }

    private fun finishProfiling(): Text {
        profiling = false
        for (s in Step.entries) {
            minWait[s] = slowest[s]?.let { (it * MARGIN_FACTOR).toLong() + MARGIN_MS } ?: s.fallbackMs
        }
        return tx("Calibrage terminé. Plus lents : ", "Profiling done. Slowest: ") + describe(slowest) +
            tx(". Attentes désormais : ", ". Waits from now on: ") + describe(minWait)
    }

    /** "profile 587 ms, menu 602 ms, ..." */
    private fun describe(times: Map<Step, Long>): Text =
        Step.entries.map { s -> s.label + " " + (times[s]?.let { raw("$it ms") } ?: tx("non vu", "not seen")) }
            .joinText(", ")

    companion object {
        const val PROFILE_ACCOUNTS = 10
        const val MAX_SLOWDOWNS = 3
        private const val PROFILING_POLL_MS = 40L

        /** Wait after profiling = slowest time x MARGIN_FACTOR + MARGIN_MS. */
        private const val MARGIN_FACTOR = 1.25
        private const val MARGIN_MS = 150L

        /** Wait after a slowdown = previous wait x SLOWDOWN_FACTOR + SLOWDOWN_MS. */
        private const val SLOWDOWN_FACTOR = 1.5
        private const val SLOWDOWN_MS = 200L
    }
}
