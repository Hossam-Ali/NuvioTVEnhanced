package com.nuvio.tv.core.tmdb

import android.content.Context

/**
 * The language descriptions are shown in, following the favourite subtitle language: a title
 * made in that language (an Arabic film for an Arabic speaker) is described in it, and every
 * other title in English. Kept on the device so the first screens after a start already follow
 * it, before the player settings are read.
 */
object DescriptionLanguage {
    private const val PREFS_NAME = "nuvio_description_language"
    private const val KEY = "favourite"

    @Volatile private var appContext: Context? = null

    /** The favourite subtitle language (ISO 639-1, such as "ar"), or null to leave descriptions as TMDB gives them. */
    @Volatile var favourite: String? = null
        private set

    fun attach(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        favourite = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY, null)
    }

    /** Sets the favourite subtitle language from the player settings ("none" or blank turns the rule off). */
    fun setFavourite(subtitleLanguage: String?) {
        val code = normalizeFavourite(subtitleLanguage)
        if (code == favourite) return
        favourite = code
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)?.edit()?.putString(KEY, code)?.apply()
    }

    internal fun normalizeFavourite(subtitleLanguage: String?): String? =
        subtitleLanguage?.trim()?.lowercase()
            ?.substringBefore('-')?.substringBefore('_')
            ?.takeIf { it.length in 2..3 && it.all(Char::isLetter) && it != "none" }
}

/**
 * The language to describe a title in: [favourite] when the title was made in it ([original]),
 * else English. Null when there is no favourite, which keeps TMDB's description in the app's
 * TMDB language.
 */
internal fun descriptionLanguageFor(original: String?, favourite: String?): String? {
    val fav = favourite?.lowercase() ?: return null
    val made = original?.lowercase()?.substringBefore('-')
    return if (made == fav) fav else "en"
}

/**
 * The favourite subtitle language: AI translation's own target language when translation is on
 * and set to one (a personal build translating into Arabic), else the preferred subtitle
 * language from the playback settings.
 */
internal fun favouriteSubtitleLanguage(
    preferredSubtitleLanguage: String?,
    translationEnabled: Boolean,
    translationTarget: String?,
): String? {
    val target = translationTarget?.trim()?.takeIf {
        it.isNotEmpty() && !it.equals(com.nuvio.tv.core.subtitles.SUBTITLE_TRANSLATION_TARGET_PREFERRED, ignoreCase = true)
    }
    return if (translationEnabled && target != null) target else preferredSubtitleLanguage
}
