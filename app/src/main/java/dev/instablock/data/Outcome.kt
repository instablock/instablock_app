package dev.instablock.data

import dev.instablock.i18n.Text
import dev.instablock.i18n.tx

/**
 * What happened to one account.
 *
 * A [final] outcome is never retried. The others are tried again on the next run, after the
 * accounts that were never tried.
 *
 * [label] is used in the log and the end-of-run message, [short] in the counts under the list.
 * The enum names are stored in the results, so renaming one needs a migration in [Store].
 */
enum class Outcome(val final: Boolean, val label: Text, val short: Text) {
    BLOCKED(true, tx("Bloqué", "Blocked"), tx("bloqués", "blocked")),

    /** The profile or its menu already showed "Unblock". */
    ALREADY_BLOCKED(true, tx("Déjà bloqué", "Already blocked"), tx("déjà bloqués", "already blocked")),

    /** Instagram's "user not found" page: the account is missing, deactivated, or hidden from you. */
    UNAVAILABLE(
        true,
        tx("Introuvable (inexistant, désactivé ou masqué)", "Unavailable (not found, deactivated or hidden)"),
        tx("introuvables", "unavailable"),
    ),

    /** Block was confirmed, but the profile never showed "Unblock" afterwards. */
    BLOCKED_UNVERIFIED(
        false,
        tx("Bloqué, non confirmé (revérifié au prochain lancement)", "Blocked, not confirmed (rechecked next run)"),
        tx("non confirmés", "unconfirmed"),
    ),

    FAILED(
        false,
        tx("Échec (réessayé au prochain lancement)", "Failed (retried next run)"),
        tx("échecs", "failed"),
    ),

    /** Instagram showed "Try again later". The run stops when this happens. */
    RATE_LIMITED(
        false,
        tx("Limité par Instagram (réessayé au prochain lancement)", "Rate-limited (retried next run)"),
        tx("limités", "rate-limited"),
    ),
}
