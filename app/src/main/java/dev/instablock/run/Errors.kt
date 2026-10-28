package dev.instablock.run

import dev.instablock.i18n.Text

/** Stops the whole run, as opposed to failing one account. [text] says why. */
class Abort(val text: Text) : Exception(text.en)

/** Instagram stayed out of the foreground: it crashed, or the user pressed Home or locked the screen. */
class LeftInstagram : Exception("Instagram left the foreground")
