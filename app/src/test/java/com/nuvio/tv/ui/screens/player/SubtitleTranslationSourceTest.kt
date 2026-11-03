package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.domain.model.Subtitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleTranslationSourceTest {
    private fun sub(id: String, lang: String, addon: String = "OpenSubtitles") =
        Subtitle(id = id, url = "https://subs/$id.srt", lang = lang, addonName = addon, addonLogo = null)

    private fun track(index: Int, lang: String, codec: String = "application/x-subrip") =
        TrackInfo(index = index, name = lang, language = lang, codec = codec)

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

    @Test
    fun `an english add-on comes before the video's own track in another language`() {
        val source = subtitleSourceForTranslation(listOf(track(7, "fre")), listOf(sub("en", "eng")), "ar")
        assertEquals("en", (source as TranslationSource.Addon).subtitle.id)
    }

    @Test
    fun `the video's english track comes before an english add-on`() {
        val source = subtitleSourceForTranslation(listOf(track(2, "fre"), track(3, "eng")), listOf(sub("en", "eng")), "ar")
        assertEquals(3, (source as TranslationSource.Embedded).track.index)
    }

    @Test
    fun `without english anywhere, the video's own track comes before an add-on`() {
        val source = subtitleSourceForTranslation(listOf(track(7, "fre")), listOf(sub("de", "ger")), "ar")
        assertEquals(7, (source as TranslationSource.Embedded).track.index)
    }

    @Test
    fun `image subtitles and target language tracks leave the add-on as the source`() {
        val tracks = listOf(track(1, "eng", codec = "hdmv/pgs"), track(2, "ara"))
        val source = subtitleSourceForTranslation(tracks, listOf(sub("tr", "tur")), "ar")
        assertTrue(source is TranslationSource.Addon)
        assertNull(subtitleSourceForTranslation(tracks, listOf(sub("ar", "ara")), "ar"))
    }
}
