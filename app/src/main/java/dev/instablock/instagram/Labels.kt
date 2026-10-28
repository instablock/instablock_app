package dev.instablock.instagram

import java.util.Locale

/** Texts the app looks for on Instagram's screens. */
enum class Label {
    BLOCK,
    UNBLOCK,
    RESTRICT,
    UNRESTRICT,
    DISMISS,
    OPTIONS,
    FOLLOW,
    NOT_AVAILABLE,
    TRY_AGAIN_LATER,
}

/**
 * What each [Label] reads in Instagram's English and French interfaces.
 *
 * Most labels must match a view's whole text. [NOT_AVAILABLE][Label.NOT_AVAILABLE] and
 * [TRY_AGAIN_LATER][Label.TRY_AGAIN_LATER] are messages inside longer sentences, so they match
 * anywhere in the text.
 */
object Labels {
    private val texts: Map<Label, List<String>> = mapOf(
        Label.BLOCK to listOf("Block", "Bloquer"),
        Label.UNBLOCK to listOf("Unblock", "Débloquer"),
        Label.RESTRICT to listOf("Restrict", "Restreindre"),
        // Replaces "Restrict" in the profile menu once the account is restricted.
        Label.UNRESTRICT to listOf("Unrestrict", "Ne plus restreindre"),
        Label.DISMISS to listOf("Dismiss", "OK", "Ignorer", "Fermer"),
        Label.OPTIONS to listOf("Options", "More options", "Plus d'options"),
        Label.FOLLOW to listOf(
            "Follow", "Follow back", "Following", "Requested",
            "S'abonner", "Suivre", "Suivre en retour", "Abonné(e)", "Abonné", "Suivi(e)", "Demandé",
        ),
        Label.NOT_AVAILABLE to listOf(
            // The French page reads "Utilisateur(ice) introuvable", hence the bare "introuvable".
            "isn't available", "user not found", "n'est pas disponible", "introuvable",
        ),
        Label.TRY_AGAIN_LATER to listOf("try again later", "réessayer plus tard", "réessayez plus tard"),
    )

    /**
     * Invisible formatting characters Instagram puts around some texts: zero-width spaces and
     * joiners, and text direction marks. Declared before [normalized], which needs it: Kotlin
     * initialises an object's properties in order.
     */
    private val invisibleChars = Regex("[\\u200B-\\u200F\\u202A-\\u202E\\u2066-\\u2069]")

    private val matchAnywhere = setOf(Label.NOT_AVAILABLE, Label.TRY_AGAIN_LATER)

    private val normalized: Map<Label, Set<String>> = texts.mapValues { (_, v) -> v.map(::normalize).toSet() }

    /** True if [text] is what Instagram shows for [label]. */
    fun matches(label: Label, text: CharSequence?): Boolean {
        val n = normalize(text)
        if (n.isEmpty()) return false
        val candidates = normalized[label] ?: return false
        return if (label in matchAnywhere) candidates.any { n.contains(it) } else n in candidates
    }

    /**
     * Lowercases [text] and removes what varies between screens without changing the meaning:
     * non-breaking spaces, curly apostrophes, invisible formatting characters, outer spaces.
     */
    fun normalize(text: CharSequence?): String =
        text?.toString()
            ?.replace('\u00A0', ' ') // non-breaking space
            ?.replace('\u2019', '\'') // curly apostrophe
            ?.replace(invisibleChars, "")
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?: ""
}
