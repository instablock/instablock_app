package dev.instablock.run

import dev.instablock.BlockerService
import dev.instablock.data.Outcome
import dev.instablock.data.Store
import dev.instablock.i18n.Text
import dev.instablock.i18n.joinText
import dev.instablock.i18n.raw
import dev.instablock.i18n.tx
import dev.instablock.input.randomBetween
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * One run over the pending accounts, one at a time, with [BlockFlow] doing the work for each.
 *
 * Around that, the run:
 *  - saves each outcome as soon as it's known, so an interrupted run can be resumed;
 *  - takes a short break every [BREAK_EVERY] accounts;
 *  - after [FAILURE_STREAK] failures in a row, slows down and retries those accounts once;
 *  - if Instagram leaves the screen, asks the user, then relaunches it and carries on;
 *  - stops as soon as Instagram says "Try again later".
 */
class Runner(private val svc: BlockerService) {
    private val d = Driver(svc)
    private val flow = BlockFlow(d)

    /** How many accounts reached each outcome in this run. */
    private val counts = sortedMapOf<Outcome, Int>()

    /** "Time: 1m 20s (4.0 s per account)", once the run has handled an account. */
    private var timing: Text? = null

    private fun log(msg: Text) = d.log(msg)

    /** One line per outcome reached in this run (such as "Blocked: 5"), then the timing. */
    fun summary(): Text =
        (counts.entries.map { (o, n) -> o.label + tx(" : $n", ": $n") } + listOfNotNull(timing)).joinText("\n")

    /** Handles the pending accounts. Returns the headline of the end-of-run message. */
    suspend fun run(): Text {
        d.measureScreen()
        log(tx("Écran : ${d.geo}", "Screen: ${d.geo}"))

        val pending = Store.pending(svc)
        if (pending.isEmpty()) {
            val nothing = tx("Plus aucun compte à bloquer.", "Nothing left to block.")
            log(nothing)
            return nothing
        }
        log(tx("${pending.size} compte(s) en attente", "${pending.size} account(s) pending"))
        d.launchInstagram()
        log(
            tx(
                "Calibrage sur les ${Pacing.PROFILE_ACCOUNTS} premiers comptes : action dès que chaque écran est prêt",
                "Profiling the first ${Pacing.PROFILE_ACCOUNTS} accounts: acting as soon as each screen is ready",
            ),
        )

        val started = System.currentTimeMillis()
        val queue = ArrayDeque(pending)
        val handled = mutableSetOf<String>()
        val failureStreak = mutableListOf<String>() // consecutive failures on a first attempt
        val retried = mutableSetOf<String>()
        var attempts = 0
        var resumes = 0

        while (queue.isNotEmpty()) {
            val user = queue.removeFirst()
            log(if (user in retried) tx("→ @$user (nouvel essai)", "→ @$user (retry)") else raw("→ @$user"))

            val outcome = try {
                flow.block(user)
            } catch (e: CancellationException) {
                throw e // the user pressed Stop
            } catch (e: Abort) {
                throw e
            } catch (e: LeftInstagram) {
                // Not this account's fault: ask the user, then try it again in a fresh Instagram.
                resumeAfterLeaving(resumes)
                resumes++
                queue.addFirst(user)
                continue
            } catch (e: Exception) {
                log(tx("   erreur : ${e.message}", "   error: ${e.message}"))
                Outcome.FAILED
            }

            Store.record(svc, user, outcome)
            counts[outcome] = (counts[outcome] ?: 0) + 1
            log(raw("   ") + outcome.label)
            if (outcome == Outcome.RATE_LIMITED) {
                val limited = tx(
                    "Arrêt : Instagram limite les actions. Attendez quelques heures ou plus avant le prochain lancement.",
                    "Stopped: Instagram is limiting actions. Wait a few hours or more before the next run.",
                )
                log(limited)
                return limited
            }

            handled += user
            attempts++
            timing = timingText(System.currentTimeMillis() - started, handled.size)
            d.pacing.accountDone()?.let(::log)

            // A retried account that fails again stays failed; it doesn't start a new streak.
            when {
                outcome != Outcome.FAILED -> failureStreak.clear()
                user !in retried -> failureStreak += user
            }
            if (failureStreak.size >= FAILURE_STREAK && d.pacing.slowdowns < Pacing.MAX_SLOWDOWNS) {
                slowDownAndRetry(failureStreak, queue, retried)
            }

            if (attempts % BREAK_EVERY == 0) takeBreak()
        }

        log(tx("Terminé (${handled.size} traité(s)). ", "Run finished (${handled.size} processed). ") + (timing ?: raw("")))
        return tx("Terminé.", "All done.")
    }

    /**
     * Instagram left the screen. Shows the user a countdown, then relaunches Instagram.
     * Throws [Abort] if the user cancels, or after [MAX_RESUMES] resumes in one run.
     */
    private suspend fun resumeAfterLeaving(resumesSoFar: Int) {
        if (resumesSoFar >= MAX_RESUMES) {
            throw Abort(
                tx(
                    "Instagram a quitté l'écran $MAX_RESUMES fois ; arrêt.",
                    "Instagram left the foreground $MAX_RESUMES times; stopping.",
                ),
            )
        }
        log(
            tx(
                "   Instagram a quitté l'écran ; reprise dans $RESUME_COUNTDOWN_S s sauf annulation",
                "   Instagram left the foreground; resuming in ${RESUME_COUNTDOWN_S}s unless cancelled",
            ),
        )
        if (!svc.askToResume(RESUME_COUNTDOWN_S)) {
            throw Abort(tx("annulé après qu'Instagram a quitté l'écran", "cancelled after Instagram left the foreground"))
        }
        log(tx("   reprise : relance d'Instagram", "   resuming: relaunching Instagram"))
        d.launchInstagram()
    }

    /**
     * Gives Instagram more time on every step and puts the accounts of the failure streak back
     * at the front of the queue, so each is retried once. They no longer count as failed.
     */
    private fun slowDownAndRetry(streak: MutableList<String>, queue: ArrayDeque<String>, retried: MutableSet<String>) {
        log(tx("${streak.size} échecs d'affilée", "${streak.size} failures in a row"))
        d.pacing.slowDown().forEach(::log)
        val failed = (counts[Outcome.FAILED] ?: 0) - streak.size
        if (failed > 0) counts[Outcome.FAILED] = failed else counts.remove(Outcome.FAILED)
        streak.asReversed().forEach { queue.addFirst(it) }
        retried += streak
        streak.clear()
    }

    private suspend fun takeBreak() {
        val ms = randomBetween(BREAK_MIN_MS, BREAK_MAX_MS)
        log(tx("Pause : ${ms / 1000} s", "Break: ${ms / 1000}s"))
        delay(ms)
    }

    private fun timingText(ms: Long, accounts: Int): Text {
        val s = ms / 1000
        val perAccount = "%.1f".format(Locale.US, ms / 1000.0 / accounts)
        return tx(
            "Durée : ${s / 60} min ${s % 60} s ($perAccount s par compte)",
            "Time: ${s / 60}m ${s % 60}s ($perAccount s per account)",
        )
    }

    companion object {
        /** A break of [BREAK_MIN_MS] to [BREAK_MAX_MS] after every [BREAK_EVERY] accounts. */
        private const val BREAK_EVERY = 10
        private const val BREAK_MIN_MS = 5_000L
        private const val BREAK_MAX_MS = 15_000L

        /** Failures in a row that make the run slow down and retry them. */
        private const val FAILURE_STREAK = 5

        /** Seconds the user has to cancel before the run resumes after Instagram left the screen. */
        private const val RESUME_COUNTDOWN_S = 10

        /** Resumes allowed in one run before it stops, so a crash loop can't go on unattended. */
        private const val MAX_RESUMES = 5
    }
}
