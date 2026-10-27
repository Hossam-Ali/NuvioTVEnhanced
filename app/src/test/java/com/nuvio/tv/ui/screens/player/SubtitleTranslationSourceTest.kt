package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.domain.model.Subtitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleTranslationSourceTest {
    private fun sub(id: String, lang: String, addon: String = "OpenSubtitles") =
        Subtitle(id = id, url = "https://subs/$id.srt", lang = lang, addonName = addon, addonLogo = null)

    @Test
    fun `the spoken language comes first, then english`() {
        val subtitles = listOf(sub("fr", "fre"), sub("en", "eng"), sub("tr", "tur"))
        assertEquals("tr", addonSubtitleForTranslation(subtitles, "ar", audioLanguage = "tr")?.id)
        assertEquals("en", addonSubtitleForTranslation(subtitles, "ar", audioLanguage = "ja")?.id)
        assertEquals("en", addonSubtitleForTranslation(subtitles, "ar")?.id)
    }

    @Test
    fun `subtitles already in the target language, forced ones and other languages are not picked`() {
        assertNull(addonSubtitleForTranslation(listOf(sub("ar", "ara")), "ar"))
        assertNull(addonSubtitleForTranslation(listOf(sub("en-forced", "eng")), "ar"))
        assertNull(addonSubtitleForTranslation(listOf(sub("de", "ger")), "ar"))
    }
}
