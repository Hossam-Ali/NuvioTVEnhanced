package com.nuvio.tv.ui.screens.search

import com.nuvio.tv.data.remote.api.TmdbMultiSearchResult
import org.junit.Assert.assertEquals
import org.junit.Test

class TmdbSearchLanguageTest {
    private fun result(title: String, original: String, language: String, overview: String = "عربي") =
        TmdbMultiSearchResult(id = 1, mediaType = "movie", title = title, originalTitle = original, originalLanguage = language, overview = overview)

    @Test
    fun `an english film keeps its english title and overview in an arabic app`() {
        val arabic = result("بين النجوم", "Interstellar", "en")
        val english = result("Interstellar", "Interstellar", "en", overview = "A team of explorers")
        val shown = arabic.inOwnLanguage("ar", english)
        assertEquals("Interstellar", shown.title)
        assertEquals("A team of explorers", shown.overview)
    }

    @Test
    fun `titles in the app language stay localized and others use their original title`() {
        assertEquals("حد أقصى", result("حد أقصى", "حد أقصى", "ar").inOwnLanguage("ar", null).title)
        assertEquals("기생충", result("الطفيلي", "기생충", "ko").inOwnLanguage("ar", null).title)
        assertEquals("Interstellar", result("بين النجوم", "Interstellar", "en").inOwnLanguage("ar", null).title)
    }
}

class SearchResultScriptTest {
    @Test
    fun `only results named in a non-latin script are renamed`() {
        assertEquals(true, isNonLatinName("بين النجوم"))
        assertEquals(true, isNonLatinName("Интерстеллар"))
        assertEquals(false, isNonLatinName("Interstellar"))
        assertEquals(false, isNonLatinName("2012"))
        assertEquals(false, isNonLatinName("Amélie"))
    }
}
