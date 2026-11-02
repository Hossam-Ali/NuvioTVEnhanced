package com.nuvio.tv.ui.util

/** The writing system of the first letter, e.g. ARABIC or LATIN; null for text without letters. */
fun textScript(text: String?): Character.UnicodeScript? {
    val letter = text?.firstOrNull { Character.isLetter(it) } ?: return null
    return Character.UnicodeScript.of(letter.code)
}

/**
 * Whether [text] reads in the same writing system as [title]. A Latin-script or unknown title
 * accepts anything, so only shows titled in another script (Arabic, say) are strict.
 */
fun writtenLikeTitle(title: String?, text: String?): Boolean {
    val titleScript = textScript(title) ?: return true
    if (titleScript == Character.UnicodeScript.LATIN) return true
    return textScript(text) == titleScript
}
