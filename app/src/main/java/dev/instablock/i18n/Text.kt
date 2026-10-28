package dev.instablock.i18n

import android.content.Context
import dev.instablock.data.PREFS_NAME

/** The two languages of the interface. */
enum class Lang { FR, EN }

/** The interface language: French by default, switched with the flag button and remembered. */
object L10n {
    private const val KEY = "lang"

    @Volatile
    var lang = Lang.FR
        private set

    fun load(ctx: Context) {
        val saved = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY, "fr")
        lang = if (saved == "en") Lang.EN else Lang.FR
    }

    fun set(ctx: Context, newLang: Lang) {
        lang = newLang
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY, if (newLang == Lang.EN) "en" else "fr")
            .apply()
    }
}

/**
 * A message in both languages. [toString] returns the one for the current language, so a
 * [Text] can be stored (for example in the log) and shown again after the language changes.
 * The log file always uses [en].
 */
class Text(val fr: String, val en: String) {
    override fun toString(): String = if (L10n.lang == Lang.FR) fr else en

    operator fun plus(other: Text) = Text(fr + other.fr, en + other.en)
    operator fun plus(other: String) = Text(fr + other, en + other)
}

/** Builds a [Text]. French comes first because it is the default language. */
fun tx(fr: String, en: String) = Text(fr, en)

/** A [Text] that reads the same in both languages, such as a username. */
fun raw(s: String) = Text(s, s)

/** Joins texts with [separator], keeping both languages. */
fun List<Text>.joinText(separator: String) =
    Text(joinToString(separator) { it.fr }, joinToString(separator) { it.en })
