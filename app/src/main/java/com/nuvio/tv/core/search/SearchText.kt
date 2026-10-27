package com.nuvio.tv.core.search

import java.text.Normalizer

private val combiningMarks = Regex("\\p{Mn}+")
private val arabicMarks = Regex("[\\u064B-\\u0652\\u0670\\u0640]")
private val punctuation = Regex("[\\p{P}\\p{S}]+")
private val spaces = Regex("\\s+")

/**
 * Text prepared for matching: lower case, without accents, punctuation or Arabic diacritics, and
 * with Arabic letter variants unified, so "Amélie", "amelie" and "إعدادات"/"اعدادات" compare equal.
 */
fun normalizeSearchText(text: String): String =
    Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        .replace(combiningMarks, "")
        .replace(arabicMarks, "")
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ى', 'ي').replace('ة', 'ه')
        .replace(punctuation, " ")
        .replace(spaces, " ")
        .trim()
