package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.domain.model.Subtitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleTranslationSourceTest {
    private fun sub(id: String, lang: String, addon: String = "OpenSubtitles") =
        Subtitle(id = id, url = "https://subs/$id.srt", lang = lang, addonName = addon, addonLogo = null)

    @Test
    fun `english comes first, even over the spoken language`() {
        val subtitles = listOf(sub("fr", "fre"), sub("en", "eng"), sub("tr", "tur"))
        assertEquals("en", addonSubtitleForTranslation(subtitles, "ar", audioLanguage = "tr")?.id)
        assertEquals("en", addonSubtitleForTranslation(subtitles, "ar")?.id)
    }

    @Test
    fun `without english, the spoken language and then any other language is used`() {
        val subtitles = listOf(sub("fr", "fre"), sub("tr", "tur"))
        assertEquals("tr", addonSubtitleForTranslation(subtitles, "ar", audioLanguage = "tr")?.id)
        assertEquals("fr", addonSubtitleForTranslation(subtitles, "ar", audioLanguage = "ja")?.id)
        assertEquals("de", addonSubtitleForTranslation(listOf(sub("de", "ger")), "ar")?.id)
    }

    @Test
    fun `subtitles already in the target language and forced ones are not picked`() {
        assertNull(addonSubtitleForTranslation(listOf(sub("ar", "ara")), "ar"))
        assertNull(addonSubtitleForTranslation(listOf(sub("en-forced", "eng")), "ar"))
    }
}
