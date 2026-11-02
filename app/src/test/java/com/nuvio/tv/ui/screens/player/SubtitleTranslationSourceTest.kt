package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.domain.model.Subtitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
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
    fun `the video's own track in another language comes before an english add-on`() {
        val source = subtitleSourceForTranslation(listOf(track(7, "fre")), listOf(sub("en", "eng")), "ar")
        assertEquals(7, (source as TranslationSource.Embedded).track.index)
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

    private fun choice(tracks: List<TrackInfo>, subs: List<Subtitle>, canTranscribe: Boolean = true) =
        autoSubtitleChoice(tracks, subs, "ar", canTranscribe = canTranscribe)

    @Test
    fun `the video's own arabic track is shown as it is, before anything translated`() {
        val result = choice(listOf(track(1, "eng"), track(2, "ara", codec = "hdmv/pgs")), listOf(sub("ar", "ara")))
        assertEquals(2, (result as AutoSubtitleChoice.Embedded).track.index)
    }

    @Test
    fun `without an arabic track, the video's english then other tracks are translated`() {
        assertEquals(3, (choice(listOf(track(2, "fre"), track(3, "eng")), listOf(sub("ar", "ara"))) as AutoSubtitleChoice.Embedded).track.index)
        assertEquals(2, (choice(listOf(track(2, "fre")), listOf(sub("ar", "ara"), sub("en", "eng"))) as AutoSubtitleChoice.Embedded).track.index)
    }

    @Test
    fun `without video tracks, an arabic add-on comes first, then english, then any other`() {
        assertEquals("ar", (choice(emptyList(), listOf(sub("en", "eng"), sub("ar", "ara"))) as AutoSubtitleChoice.Addon).subtitle.id)
        assertEquals("en", (choice(emptyList(), listOf(sub("fr", "fre"), sub("en", "eng"))) as AutoSubtitleChoice.Addon).subtitle.id)
        assertEquals("fr", (choice(listOf(track(1, "eng", codec = "hdmv/pgs")), listOf(sub("fr", "fre"))) as AutoSubtitleChoice.Addon).subtitle.id)
    }

    @Test
    fun `with no subtitle at all, the audio is transcribed when possible`() {
        assertEquals(AutoSubtitleChoice.Transcribe, choice(emptyList(), emptyList()))
        assertNull(choice(emptyList(), emptyList(), canTranscribe = false))
        assertEquals(AutoSubtitleChoice.Transcribe, choice(listOf(track(1, "eng", codec = "hdmv/pgs")), listOf(sub("en-forced", "eng"))))
    }

    @Test
    fun `the add-on list only matters when the video has no track to use`() {
        assertTrue(autoSubtitleChoiceNeedsAddons(emptyList(), "ar"))
        assertTrue(autoSubtitleChoiceNeedsAddons(listOf(track(1, "eng", codec = "hdmv/pgs")), "ar"))
        assertFalse(autoSubtitleChoiceNeedsAddons(listOf(track(1, "eng")), "ar"))
        assertFalse(autoSubtitleChoiceNeedsAddons(listOf(track(1, "ara", codec = "hdmv/pgs")), "ar"))
    }

    @Test
    fun `a remembered subtitle is kept only when it matches the automatic choice`() {
        val internalFrench = PlayerRuntimeController.RememberedSubtitleSelection.Internal(
            PlayerRuntimeController.RememberedTrackSelection(language = "fre", name = "French")
        )
        val addonEnglish = PlayerRuntimeController.RememberedSubtitleSelection.Addon("x", "https://subs/x.srt", "eng", "OpenSubtitles")
        val embeddedEnglish = AutoSubtitleChoice.Embedded(track(3, "eng"))
        assertFalse(rememberedSubtitleMatchesChoice(internalFrench, embeddedEnglish))
        assertTrue(rememberedSubtitleMatchesChoice(internalFrench, AutoSubtitleChoice.Embedded(track(4, "fre"))))
        assertFalse(rememberedSubtitleMatchesChoice(addonEnglish, embeddedEnglish))
        assertTrue(rememberedSubtitleMatchesChoice(addonEnglish, AutoSubtitleChoice.Addon(sub("en2", "eng"))))
        assertTrue(rememberedSubtitleMatchesChoice(PlayerRuntimeController.RememberedSubtitleSelection.Disabled, embeddedEnglish))
    }
}
