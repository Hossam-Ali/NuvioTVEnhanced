package com.nuvio.tv.core.tmdb

import com.nuvio.tv.data.remote.api.TmdbAggregateCreditsResponse
import com.nuvio.tv.data.remote.api.TmdbApi
import com.nuvio.tv.data.remote.api.TmdbCreditsResponse
import com.nuvio.tv.data.remote.api.TmdbDetailsResponse
import com.nuvio.tv.data.remote.api.TmdbImagesResponse
import com.nuvio.tv.data.remote.api.TmdbMovieReleaseDatesResponse
import com.nuvio.tv.data.remote.api.TmdbTvContentRatingsResponse
import com.nuvio.tv.data.remote.api.TmdbVideosResponse
import com.nuvio.tv.domain.model.ContentType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Response

class DescriptionLanguageTest {

    @Test
    fun `a title made in the favourite language is described in it, any other in English`() {
        assertEquals("ar", descriptionLanguageFor(original = "ar", favourite = "ar"))
        assertEquals("en", descriptionLanguageFor(original = "en", favourite = "ar"))
        assertEquals("en", descriptionLanguageFor(original = "ko", favourite = "ar"))
        assertEquals("en", descriptionLanguageFor(original = null, favourite = "ar"))
        assertNull(descriptionLanguageFor(original = "ko", favourite = null))
    }

    @Test
    fun `the favourite comes from the subtitle language setting`() {
        assertEquals("ar", DescriptionLanguage.normalizeFavourite("ar"))
        assertEquals("pt", DescriptionLanguage.normalizeFavourite("pt-BR"))
        assertNull(DescriptionLanguage.normalizeFavourite("none"))
        assertNull(DescriptionLanguage.normalizeFavourite(""))
        assertNull(DescriptionLanguage.normalizeFavourite(null))
    }

    private fun tvApi(id: Int, original: String, overviews: Map<String, String>): TmdbApi {
        val api = mockk<TmdbApi>()
        overviews.forEach { (language, overview) ->
            coEvery { api.getTvDetails(id, any(), language) } returns Response.success(
                TmdbDetailsResponse(id = id, name = "Show", originalName = "Original", originalLanguage = original, overview = overview)
            )
        }
        coEvery { api.getTvAggregateCredits(any(), any(), any()) } returns Response.success(TmdbAggregateCreditsResponse())
        coEvery { api.getTvImages(any(), any(), any()) } returns Response.success(TmdbImagesResponse())
        coEvery { api.getTvContentRatings(any(), any()) } returns Response.success(TmdbTvContentRatingsResponse())
        coEvery { api.getTvVideos(any(), any(), any()) } returns Response.success(TmdbVideosResponse(id = id))
        return api
    }

    @Test
    fun `an Arabic show reads in Arabic for an Arabic favourite in an English app`() = runTest {
        val api = tvApi(30, "ar", mapOf("en" to "Sabah's life is turned upside down", "ar" to "حياة صباح تنقلب"))
        val service = TmdbMetadataService(api).apply { favouriteDescriptionLanguage = { "ar" } }

        val enrichment = service.fetchEnrichment(tmdbId = "30", contentType = ContentType.SERIES, language = "en")!!

        assertEquals("حياة صباح تنقلب", enrichment.description)
        assertEquals("حياة صباح تنقلب", enrichment.descriptionForOriginalTitle(null, "ar"))
    }

    @Test
    fun `a Korean show reads in English for an Arabic favourite, without asking for Korean`() = runTest {
        val api = tvApi(40, "ko", mapOf("en" to "A deadly game", "ko" to "목숨을 건 게임"))
        val service = TmdbMetadataService(api).apply { favouriteDescriptionLanguage = { "ar" } }

        val enrichment = service.fetchEnrichment(tmdbId = "40", contentType = ContentType.SERIES, language = "en")!!

        assertEquals("A deadly game", enrichment.description)
        assertEquals("A deadly game", enrichment.descriptionForOriginalTitle("목숨을 건 게임", "ar"))
        coVerify(exactly = 0) { api.getTvDetails(40, any(), "ko") }
    }

    @Test
    fun `an English film reads in English even when TMDB is set to Arabic`() = runTest {
        val api = mockk<TmdbApi>()
        coEvery { api.getMovieDetails(50, any(), "ar") } returns Response.success(
            TmdbDetailsResponse(id = 50, title = "أم أخرى", originalTitle = "Other Mommy", originalLanguage = "en", overview = "وصف بالعربية")
        )
        coEvery { api.getMovieDetails(50, any(), "en") } returns Response.success(
            TmdbDetailsResponse(id = 50, title = "Other Mommy", originalTitle = "Other Mommy", originalLanguage = "en", overview = "An English overview")
        )
        coEvery { api.getMovieCredits(any(), any(), any()) } returns Response.success(TmdbCreditsResponse())
        coEvery { api.getMovieImages(any(), any(), any()) } returns Response.success(TmdbImagesResponse())
        coEvery { api.getMovieReleaseDates(any(), any()) } returns Response.success(TmdbMovieReleaseDatesResponse())
        coEvery { api.getMovieVideos(any(), any(), any()) } returns Response.success(TmdbVideosResponse(id = 50))
        val service = TmdbMetadataService(api).apply { favouriteDescriptionLanguage = { "ar" } }

        val enrichment = service.fetchEnrichment(tmdbId = "50", contentType = ContentType.MOVIE, language = "ar")!!

        assertEquals("An English overview", enrichment.description)
    }

    @Test
    fun `without a favourite, descriptions stay as before`() = runTest {
        val api = tvApi(60, "ko", mapOf("en" to "A deadly game", "ko" to "목숨을 건 게임"))
        val service = TmdbMetadataService(api).apply { favouriteDescriptionLanguage = { null } }

        val enrichment = service.fetchEnrichment(tmdbId = "60", contentType = ContentType.SERIES, language = "en")!!

        assertEquals("A deadly game", enrichment.description)
        assertEquals("목숨을 건 게임", enrichment.descriptionForOriginalTitle(null, null))
    }

    @Test
    fun `titles follow the favourite too - a Korean show is named in English, an Arabic show in Arabic`() = runTest {
        val korean = tvApi(70, "ko", mapOf("en" to "A deadly game"))
        coEvery { korean.getTvDetails(70, any(), "en") } returns Response.success(
            TmdbDetailsResponse(id = 70, name = "Squid Game", originalName = "오징어 게임", originalLanguage = "ko", overview = "A deadly game")
        )
        val koreanShow = TmdbMetadataService(korean).apply { favouriteDescriptionLanguage = { "ar" } }
            .fetchEnrichment(tmdbId = "70", contentType = ContentType.SERIES, language = "en")!!
        assertEquals("Squid Game", koreanShow.originalTitleOr("x"))
        assertNull(koreanShow.originalLanguageDifferentFrom("en"))

        val arabic = tvApi(71, "ar", mapOf("en" to "English overview", "ar" to "وصف عربي"))
        coEvery { arabic.getTvDetails(71, any(), "en") } returns Response.success(
            TmdbDetailsResponse(id = 71, name = "Up and Down", originalName = "طالع نازل", originalLanguage = "ar", overview = "English overview")
        )
        val arabicShow = TmdbMetadataService(arabic).apply { favouriteDescriptionLanguage = { "ar" } }
            .fetchEnrichment(tmdbId = "71", contentType = ContentType.SERIES, language = "en")!!
        assertEquals("طالع نازل", arabicShow.originalTitleOr("x"))
        assertEquals("ar", arabicShow.originalLanguageDifferentFrom("en"))
    }

    @Test
    fun `with TMDB in Arabic, an English film is still named in English`() = runTest {
        val api = mockk<TmdbApi>()
        coEvery { api.getMovieDetails(80, any(), "ar") } returns Response.success(
            TmdbDetailsResponse(id = 80, title = "أم أخرى", originalTitle = "Other Mommy", originalLanguage = "en", overview = "وصف")
        )
        coEvery { api.getMovieDetails(80, any(), "en") } returns Response.success(
            TmdbDetailsResponse(id = 80, title = "Other Mommy", originalTitle = "Other Mommy", originalLanguage = "en", overview = "English")
        )
        coEvery { api.getMovieCredits(any(), any(), any()) } returns Response.success(TmdbCreditsResponse())
        coEvery { api.getMovieImages(any(), any(), any()) } returns Response.success(TmdbImagesResponse())
        coEvery { api.getMovieReleaseDates(any(), any()) } returns Response.success(TmdbMovieReleaseDatesResponse())
        coEvery { api.getMovieVideos(any(), any(), any()) } returns Response.success(TmdbVideosResponse(id = 80))
        val film = TmdbMetadataService(api).apply { favouriteDescriptionLanguage = { "ar" } }
            .fetchEnrichment(tmdbId = "80", contentType = ContentType.MOVIE, language = "ar")!!
        assertEquals("Other Mommy", film.localizedTitle)
        assertEquals("Other Mommy", film.originalTitleOr("x"))
    }

    @Test
    fun `an add-on's Arabic name for a Korean show becomes its English name`() = runTest {
        val api = mockk<TmdbApi>()
        coEvery { api.getTvDetails(90, any(), "en-US") } returns Response.success(
            TmdbDetailsResponse(id = 90, name = "Squid Game", originalName = "오징어 게임", originalLanguage = "ko")
        )
        val service = TmdbMetadataService(api).apply { favouriteDescriptionLanguage = { "ar" } }
        assertEquals("ko" to "Squid Game", service.fetchOriginalTitle("90", ContentType.SERIES))
    }

    @Test
    fun `AI translation's own target counts as the favourite language`() {
        assertEquals("ar", favouriteSubtitleLanguage("en", translationEnabled = true, translationTarget = "ar"))
        assertEquals("en", favouriteSubtitleLanguage("en", translationEnabled = true, translationTarget = "preferred"))
        assertEquals("en", favouriteSubtitleLanguage("en", translationEnabled = false, translationTarget = "ar"))
        assertEquals("ar", favouriteSubtitleLanguage("ar", translationEnabled = true, translationTarget = ""))
    }
}
