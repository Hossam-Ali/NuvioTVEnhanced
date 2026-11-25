package com.nuvio.tv.ui.screens.search

import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchResultMergingTest {
    private fun item(id: String, name: String, year: String? = null, type: ContentType = ContentType.MOVIE) = MetaPreview(
        id = id, type = type, name = name, poster = "p", posterShape = PosterShape.POSTER, background = null,
        logo = null, description = null, releaseInfo = year, imdbRating = null, genres = emptyList()
    )

    private fun row(baseUrl: String, vararg items: MetaPreview) = CatalogRow(
        addonId = baseUrl, addonName = baseUrl, addonBaseUrl = baseUrl, catalogId = "search", catalogName = "Search",
        type = ContentType.MOVIE, rawType = "movie", items = items.toList(), isLoading = false, hasMore = false,
        currentPage = 0, supportsSkip = false, skipStep = 0, extraArgs = emptyMap()
    )

    @Test
    fun `the same title from several addons appears once, with the first addon as its source`() {
        val top = buildTopResults(
            "dune",
            listOf(
                row("a", item("tt1", "Dune", "2021"), item("tt2", "Dune: Part Two", "2024")),
                row("b", item("tt1", "Dune", "2021")),
            ),
            extra = listOf(item("tmdb:438631", "Dune", "2021")),
        )
        assertEquals(listOf("tt1", "tt2"), top.items.map { it.id })
        assertEquals("a", top.sources[topResultKey("tt1", "movie")])
    }

    @Test
    fun `exact and prefix matches come before loose ones`() {
        val top = buildTopResults(
            "black love",
            listOf(row("a", item("tt9", "The Black Love Story"), item("tt8", "Black Love"), item("tt7", "Black Lovers Club"))),
            extra = emptyList(),
        )
        assertEquals(listOf("tt8", "tt7", "tt9"), top.items.map { it.id })
    }

    @Test
    fun `titles only TMDB knows, such as arabic names, open without an addon`() {
        val top = buildTopResults("حب اسود", listOf(row("a", item("tt5", "Something Else"))), listOf(item("tmdb:1", "حب أسود", "2026", ContentType.SERIES)))
        assertEquals("tmdb:1", top.items.first().id)
        assertEquals("", top.sources[topResultKey("tmdb:1", "series")])
    }

    @Test
    fun `a movie and a show with the same name stay separate`() {
        val top = buildTopResults("fargo", listOf(row("a", item("tt1", "Fargo", "1996"), item("tt2", "Fargo", "2014", ContentType.SERIES))), emptyList())
        assertEquals(2, top.items.size)
    }

    @Test
    fun `match rank ignores case, accents and arabic letter forms`() {
        assertEquals(0, searchMatchRank("Amélie", "amelie"))
        assertEquals(0, searchMatchRank("حب أسود", "حب اسود"))
        assertEquals(1, searchMatchRank("Dune: Part Two", "dune"))
        assertEquals(4, searchMatchRank("Arrival", "dune"))
    }

    @Test
    fun `top results show only when there are two sources to merge`() {
        assertFalse(shouldShowTopResults(rowsWithResults = 1, extraResults = 0))
        assertTrue(shouldShowTopResults(rowsWithResults = 1, extraResults = 3))
        assertTrue(shouldShowTopResults(rowsWithResults = 2, extraResults = 0))
    }

    private fun looked(vararg ids: Pair<String, String?>) = ids.associate { (id, tmdbId) -> topResultKey(id, "movie") to tmdbId }

    @Test
    fun `a film under its IMDb id and its TMDB id appears once, whatever each calls it`() {
        val top = buildTopResults(
            "هيبتا",
            listOf(row("aradeb", item("tt5240732", "Hepta: The Last Lecture", "2016"))),
            extra = listOf(item("tmdb:395815", "هيبتا: المحاضرة الأخيرة", "2016")),
            ids = SearchTitleIds(looked = looked("tt5240732" to "395815")),
        )
        assertEquals(listOf("tt5240732"), top.items.map { it.id })
        assertEquals("aradeb", top.sources[topResultKey("tt5240732", "movie")])
        assertEquals(setOf("tt5240732|movie", "tmdb:395815|movie"), top.copies)
    }

    @Test
    fun `the copy TMDB recognises is opened rather than an add-on's unknown IMDb id`() {
        // elCinema lists Hepta 2 under an id no source knows.
        val elCinema = row("elcinema", item("tt37272026", "هيبتا ٢: المناظرة الأخيرة", "2025"))
        val aradeb = row("aradeb", item("tt38628047", "Hepta 2: The Last Debate", "2025"))
        val tmdb = listOf(item("tmdb:1330337", "هيبتا ٢: المناظرة الأخيرة", "2025"))
        val ids = SearchTitleIds(looked = looked("tt37272026" to null, "tt38628047" to "1330337"))
        listOf(listOf(elCinema, aradeb), listOf(aradeb, elCinema)).forEach { rows ->
            val top = buildTopResults("هيبتا", rows, tmdb, ids = ids)
            assertEquals(listOf("tt38628047"), top.items.map { it.id })
            assertEquals("aradeb", top.sources[topResultKey("tt38628047", "movie")])
            assertEquals(3, top.copies.size)
        }
    }

    @Test
    fun `without another add-on, TMDB's copy is opened when it has an IMDb id`() {
        val rows = listOf(row("elcinema", item("tt37272026", "هيبتا ٢: المناظرة الأخيرة", "2025")))
        val tmdb = listOf(item("tmdb:1330337", "هيبتا ٢: المناظرة الأخيرة", "2025"))
        val unknown = looked("tt37272026" to null)
        assertEquals("tt37272026", buildTopResults("هيبتا", rows, tmdb, ids = SearchTitleIds(unknown)).items.single().id)
        val ids = SearchTitleIds(unknown, withImdbId = setOf(topResultKey("tmdb:1330337", "movie")))
        assertEquals("tmdb:1330337", buildTopResults("هيبتا", rows, tmdb, ids = ids).items.single().id)
    }

    @Test
    fun `two films TMDB tells apart stay separate with the same name and year`() {
        val top = buildTopResults(
            "hero",
            listOf(row("a", item("tt1", "Hero", "2019")), row("b", item("tt2", "Hero", "2019"))),
            extra = emptyList(),
            ids = SearchTitleIds(looked = looked("tt1" to "10", "tt2" to "20")),
        )
        assertEquals(listOf("tt1", "tt2"), top.items.map { it.id })
    }

    @Test
    fun `an add-on copy further down a row wins over TMDB's first result`() {
        val top = buildTopResults(
            "dune",
            listOf(row("a", item("tt2", "Dune: Part Two", "2024"), item("tt1", "Dune", "2021"))),
            extra = listOf(item("tmdb:438631", "Dune", "2021")),
        )
        assertEquals(listOf("tt1", "tt2"), top.items.map { it.id })
    }

    @Test
    fun `a merged title ranks by its best matching name`() {
        val top = buildTopResults(
            "هيبتا",
            listOf(row("a", item("tt9", "Something Else", "2016"), item("tt1", "Hepta", "2016"))),
            extra = listOf(item("tmdb:1", "هيبتا", "2016")),
            ids = SearchTitleIds(looked = looked("tt1" to "1")),
        )
        assertEquals(listOf("tt1", "tt9"), top.items.map { it.id })
    }

    @Test
    fun `TMDB ids are read from ids written as such only`() {
        assertEquals("395815", tmdbIdIn("tmdb:395815"))
        assertEquals("90", tmdbIdIn("tmdb:series:90"))
        assertEquals(null, tmdbIdIn("395815"))
        assertEquals(null, tmdbIdIn("tt5240732"))
    }
}
