package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.core.tmdb.OriginalTitle
import com.nuvio.tv.ui.screens.search.renamedToOriginalTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        assertNull(originalCatalogName("Squid Game", OriginalTitle("ko", "오징어 게임"), favourite = null))
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

    @Test
    fun `an Arabic film named in English takes its Arabic title when Arabic is the favourite`() {
        // ArabCinemeta names الكلام على إيه "What is the matter?".
        assertEquals(
            "الكلام على إيه",
            originalCatalogName("What is the matter?", OriginalTitle("ar", "الكلام على إيه"), favourite = "ar")
        )
    }

    @Test
    fun `without that favourite a Latin name stays`() {
        assertNull(originalCatalogName("What is the matter?", OriginalTitle("ar", "الكلام على إيه"), favourite = null))
        assertNull(originalCatalogName("What is the matter?", OriginalTitle("ar", "الكلام على إيه"), favourite = "en"))
    }

    @Test
    fun `a title from another language keeps its Latin name even with a favourite`() {
        assertNull(originalCatalogName("Squid Game", OriginalTitle("ko", "Squid Game"), favourite = "ar"))
        assertNull(originalCatalogName("Squid Game", OriginalTitle("ko", "오징어 게임"), favourite = "ar"))
    }

    @Test
    fun `Latin names are looked up only with a favourite language`() {
        assertTrue(needsOriginalTitleLookup("كروس", favourite = null))
        assertFalse(needsOriginalTitleLookup("Squid Game", favourite = null))
        assertTrue(needsOriginalTitleLookup("What is the matter?", favourite = "ar"))
    }
}
