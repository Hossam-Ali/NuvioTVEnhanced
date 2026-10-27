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
}
