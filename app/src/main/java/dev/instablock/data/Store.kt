package dev.instablock.data

import android.content.Context

/** Name of the app's SharedPreferences file. */
const val PREFS_NAME = "instablock"

/**
 * Persistent state: the list of accounts to block and the outcome of each one.
 *
 * Both live in SharedPreferences as plain text: the list one username per line, the results
 * one "username<TAB>OUTCOME" per line.
 */
object Store {
    private const val KEY_QUEUE = "queue"
    private const val KEY_RESULTS = "results"
    private const val KEY_DEFAULT_LIST_VERSION = "defaultListVersion"

    /** File in assets/ holding the list that ships with the app. */
    private const val DEFAULT_LIST_ASSET = "default_accounts.txt"

    /**
     * Version of [DEFAULT_LIST_ASSET]. Bump it whenever that file changes: the app then replaces
     * the user's list with the new one, once, on its next start.
     */
    private const val DEFAULT_LIST_VERSION = 2

    private val usernameRegex = Regex("[a-z0-9._]{1,30}")
    private val profileUrlRegex = Regex("instagram\\.com/([A-Za-z0-9._]+)")

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Accepts "name", "@name" or a profile URL. Returns the lowercase username, or null if invalid. */
    fun parseUsername(input: String): String? {
        var s = input.trim().removePrefix("@")
        profileUrlRegex.find(s)?.let { s = it.groupValues[1] }
        s = s.trim('/').lowercase()
        return s.takeIf { usernameRegex.matches(it) }
    }

    // --- Account list -----------------------------------------------------------------------

    /** The list that ships with the app. */
    fun defaultAccounts(ctx: Context): List<String> =
        ctx.assets.open(DEFAULT_LIST_ASSET).bufferedReader().readLines().mapNotNull(::parseUsername)

    /** Loads the shipped list if this version of it hasn't been loaded before. */
    fun loadDefaultsIfNew(ctx: Context) {
        val p = prefs(ctx)
        if (p.getInt(KEY_DEFAULT_LIST_VERSION, 0) >= DEFAULT_LIST_VERSION) return
        setQueue(ctx, defaultAccounts(ctx))
        p.edit().putInt(KEY_DEFAULT_LIST_VERSION, DEFAULT_LIST_VERSION).apply()
    }

    fun queue(ctx: Context): List<String> =
        prefs(ctx).getString(KEY_QUEUE, "").orEmpty().lines().filter { it.isNotBlank() }

    fun setQueue(ctx: Context, users: List<String>) {
        prefs(ctx).edit().putString(KEY_QUEUE, users.distinct().joinToString("\n")).apply()
    }

    // --- Results ----------------------------------------------------------------------------

    fun results(ctx: Context): Map<String, Outcome> =
        prefs(ctx).getString(KEY_RESULTS, "").orEmpty().lines().mapNotNull { line ->
            val parts = line.split('\t')
            // NOT_FOUND is the old name of UNAVAILABLE, found in results saved by early versions.
            val name = parts.getOrNull(1)?.let { if (it == "NOT_FOUND") "UNAVAILABLE" else it }
            val outcome = name?.let { runCatching { Outcome.valueOf(it) }.getOrNull() }
            outcome?.let { parts[0] to it }
        }.toMap()

    /**
     * Saves the outcome for [user]. Written synchronously, so an interrupted run (Stop, a crash,
     * the phone turning off) never loses an account it already handled.
     */
    @Synchronized
    fun record(ctx: Context, user: String, outcome: Outcome) {
        val all = results(ctx) + (user to outcome)
        prefs(ctx).edit()
            .putString(KEY_RESULTS, all.entries.joinToString("\n") { "${it.key}\t${it.value}" })
            .commit()
    }

    /** Forgets every outcome, so every account in the list is pending again. */
    fun clearResults(ctx: Context) {
        prefs(ctx).edit().remove(KEY_RESULTS).commit()
    }

    /**
     * Accounts still to do, in the order a run takes them: never tried first, then those whose
     * last attempt didn't settle (failed, unconfirmed, rate-limited). A resumed run therefore
     * continues where the previous one stopped.
     */
    fun pending(ctx: Context): List<String> {
        val done = results(ctx)
        val (retries, untried) = queue(ctx).filter { done[it]?.final != true }.partition { it in done }
        return untried + retries
    }
}
