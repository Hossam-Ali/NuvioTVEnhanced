package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.core.tmdb.OriginalTitle
import com.nuvio.tv.ui.screens.search.renamedToOriginalTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OriginalCatalogNameTest {

    @Test
    fun `an English show named in Arabic by its add-on takes its original title`() {
        assertEquals("Cross", originalCatalogName("كروس ج2", OriginalTitle("en", "Cross")))
    }

    @Test
    fun `an English film named in Arabic takes its original title in search too`() {
        // ArabCinemeta (elCinema) names Other Mommy "أم أخرى", whatever the app language.
        assertEquals("Other Mommy", renamedToOriginalTitle("أم أخرى", "Other Mommy"))
    }

    @Test
    fun `a title from a third language is named in its own script`() {
        assertEquals("오징어 게임", originalCatalogName("لعبة الحبار", OriginalTitle("ko", "오징어 게임")))
    }

    @Test
    fun `an Arabic show keeps its add-on name when that is already Arabic`() {
        assertNull(originalCatalogName("طالع نازل", OriginalTitle("ar", "طالع نازل 2")))
    }

    @Test
    fun `a name in Latin letters is never changed`() {
        assertNull(originalCatalogName("Squid Game", OriginalTitle("ko", "오징어 게임")))
    }

    @Test
    fun `a title TMDB doesn't know keeps the add-on name`() {
        assertNull(originalCatalogName("كروس", null))
    }

    @Test
    fun `a name already equal to the original title is left alone`() {
        assertNull(originalCatalogName("Друзья", OriginalTitle("ru", "Друзья")))
    }

    @Test
    fun `a blank original title never replaces the add-on name`() {
        assertNull(renamedToOriginalTitle("أم أخرى", " "))
    }
}
