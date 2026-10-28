package dev.instablock.instagram

/** Instagram's Android package name. */
const val INSTAGRAM_PACKAGE = "com.instagram.android"

/** The link that opens [user]'s profile in the Instagram app. */
fun profileUrl(user: String) = "https://www.instagram.com/$user/"

/**
 * Instagram view ids (the part after ":id/"), confirmed on Instagram 446.0.0.49.77 in English
 * and French. They don't depend on the language but can change between Instagram versions, so
 * every lookup that uses them also has a fallback based on labels or position.
 */
object ViewIds {
    /** Username in the profile's top bar. */
    const val PROFILE_TITLE = "action_bar_title"

    /** Follow button on a profile. Reads "Unblock" when the account is blocked. */
    const val FOLLOW_BUTTON = "profile_header_follow_button"

    /** Message on the "user not found" page. */
    const val NOT_FOUND_TEXT = "no_found_text"

    /** Container of the profile's top-right buttons. The ⋯ button has no id of its own. */
    const val OVERFLOW_PARENT = "right_action_bar_buttons"

    /** One entry of the ⋯ menu (Restrict, Block, Report...). */
    const val MENU_ITEM = "context_menu_item"

    /** Main button of a bottom sheet: "Block" on the block confirmation sheet. */
    const val CONFIRM_BUTTON = "bb_primary_action_container"

    /** Second button of a bottom sheet: "Block and report", right under the confirm button. */
    const val SECONDARY_BUTTON = "bb_secondary_action"

    /** Title of a bottom sheet, such as "Block username?". */
    const val SHEET_HEADLINE = "igds_headline_headline"

    /** Container of any bottom sheet. */
    const val BOTTOM_SHEET = "layout_container_bottom_sheet"

    /** Views that exist only while a menu or bottom sheet covers the profile. */
    val OVERLAYS = setOf(MENU_ITEM, CONFIRM_BUTTON, BOTTOM_SHEET)

    /** Views whose text is worth logging when an unexpected sheet opens. */
    val SHEET_TEXTS = setOf(SHEET_HEADLINE, CONFIRM_BUTTON, SECONDARY_BUTTON)
}
