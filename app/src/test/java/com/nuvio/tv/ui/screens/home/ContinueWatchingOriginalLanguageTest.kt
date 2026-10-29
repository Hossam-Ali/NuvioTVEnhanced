package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.core.tmdb.TmdbEnrichment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContinueWatchingOriginalLanguageTest {
    private fun enrichment(language: String?, originalTitle: String?) = TmdbEnrichment(
        localizedTitle = "Half the People Are Named Mohamed", description = null, genres = emptyList(), backdrop = null,
        logo = null, poster = null, directorMembers = emptyList(), writerMembers = emptyList(), castMembers = emptyList(),
        releaseInfo = null, rating = null, runtimeMinutes = null, director = emptyList(), writer = emptyList(),
        productionCompanies = emptyList(), networks = emptyList(), ageRating = null, status = null, countries = null,
        language = language, collectionId = null, collectionName = null, originalTitle = originalTitle
    )

    @Test
    fun `an arabic show in an english app uses arabic on the modern home`() {
        assertEquals("ar", continueWatchingOriginalLanguage(enrichment("ar", "نص الشعب اسمه محمد"), "en", isModernLayout = true))
        assertEquals("ar", continueWatchingOriginalLanguage(enrichment("ar", "نص الشعب اسمه محمد"), "en-US", isModernLayout = true))
    }

    @Test
    fun `titles in the app language, without an original title, or on other layouts stay as they are`() {
        assertNull(continueWatchingOriginalLanguage(enrichment("en", "Game of Thrones"), "en", isModernLayout = true))
        assertNull(continueWatchingOriginalLanguage(enrichment("ar", "نص الشعب اسمه محمد"), "ar-SA", isModernLayout = true))
        assertNull(continueWatchingOriginalLanguage(enrichment("ar", null), "en", isModernLayout = true))
        assertNull(continueWatchingOriginalLanguage(enrichment("ar", "نص الشعب اسمه محمد"), "en", isModernLayout = false))
    }
}
