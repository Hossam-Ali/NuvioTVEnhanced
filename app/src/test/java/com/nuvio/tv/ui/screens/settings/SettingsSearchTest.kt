package com.nuvio.tv.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {
    private fun row(title: String, subtitle: String? = null, page: SettingsCategory = SettingsCategory.PLAYBACK) =
        SettingsSearchResult(SettingsSearchEntry(title.hashCode(), null, page, null, null), title, subtitle)

    private val rows = listOf(
        row("Preferred audio language", "Language used when a video has several"),
        row("Subtitle size", page = SettingsCategory.SUBTITLES),
        row("Audio passthrough", "Send surround sound to your receiver"),
        row("حجم الترجمة", page = SettingsCategory.SUBTITLES),
        row("Show ratings on posters", page = SettingsCategory.LAYOUT),
    )
    private val allPages = SettingsCategory.entries.toSet()

    @Test
    fun `titles starting with the query come before other matches`() {
        val results = searchSettings("audio", rows, allPages).map { it.title }
        assertEquals(listOf("Audio passthrough", "Preferred audio language"), results)
    }

    @Test
    fun `every word must match the title or description`() {
        assertEquals(listOf("Audio passthrough"), searchSettings("surround audio", rows, allPages).map { it.title })
        assertTrue(searchSettings("audio size", rows, allPages).isEmpty())
    }

    @Test
    fun `arabic matches ignore diacritics and letter variants`() {
        assertEquals("حَجْم الترجمة".let { normalizeForSettingsSearch(it) }, normalizeForSettingsSearch("حجم الترجمة"))
        assertEquals(listOf("حجم الترجمة"), searchSettings("الترجمه", rows, allPages).map { it.title })
        assertEquals("اعدادات", normalizeForSettingsSearch("إعدادات"))
    }

    @Test
    fun `hidden pages and empty queries return nothing`() {
        assertTrue(searchSettings("   ", rows, allPages).isEmpty())
        val withoutLayout = allPages - SettingsCategory.LAYOUT
        assertTrue(searchSettings("ratings", rows, withoutLayout).isEmpty())
    }

    @Test
    fun `every indexed setting points at a page the rail can open`() {
        assertTrue(settingsSearchIndex.size > 200)
        settingsSearchIndex.forEach { entry ->
            assertTrue(entry.page != SettingsCategory.SEARCH && entry.page != SettingsCategory.EXPERIENCE)
            if (entry.page == SettingsCategory.PLAYBACK) assertTrue(entry.playbackSection != null)
        }
    }
}
