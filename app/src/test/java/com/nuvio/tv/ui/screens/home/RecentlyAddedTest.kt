package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.domain.model.legacyKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecentlyAddedTest {

    private val today = LocalDate.of(2026, 11, 13)

    private fun title(id: String, released: String? = null, releaseInfo: String? = null, type: ContentType = ContentType.MOVIE) =
        MetaPreview(
            id = id, type = type, name = id, poster = null, posterShape = PosterShape.POSTER,
            background = null, logo = null, description = null, releaseInfo = releaseInfo,
            imdbRating = null, genres = emptyList(), released = released
        )

    private fun row(catalogId: String, type: ContentType, vararg items: MetaPreview) = CatalogRow(
        addonId = "addon", addonName = "Addon", addonBaseUrl = "https://addon", catalogId = catalogId,
        catalogName = catalogId, type = type, items = items.toList()
    )

    private val arabicMovies = row(
        "arabic", ContentType.MOVIE,
        title("tt1", released = "2026-10-01T00:00:00.000Z"),
        title("tt2", released = "2024-05-01"),
        title("tt3", releaseInfo = "2026")
    )
    private val englishSeries = row(
        "english", ContentType.SERIES,
        title("tt10", released = "2026-11-01", type = ContentType.SERIES),
        title("tt11", releaseInfo = "2019-2023", type = ContentType.SERIES)
    )
    private val other = row("other", ContentType.MOVIE, title("tt99", released = "2026-11-12"))

    @Test
    fun `newest first across the picked rows only`() {
        val entries = recentlyAddedEntries(
            listOf(arabicMovies, englishSeries, other),
            setOf(arabicMovies.legacyKey(), englishSeries.legacyKey()),
            today,
            maxAgeDays = null
        )
        assertEquals(listOf("tt10", "tt1", "tt3", "tt2", "tt11"), entries.map { it.item.id })
        assertEquals(englishSeries, entries.first().source)
    }

    @Test
    fun `titles not out yet and titles without a date are left out`() {
        val rows = listOf(
            row(
                "upcoming", ContentType.MOVIE,
                title("future", released = "2026-12-25"),
                title("next-year", releaseInfo = "2027"),
                title("no-date"),
                title("out", released = "2026-11-13")
            )
        )
        val entries = recentlyAddedEntries(rows, setOf(rows[0].legacyKey()), today, maxAgeDays = null)
        assertEquals(listOf("out"), entries.map { it.item.id })
    }

    @Test
    fun `a title in two picked rows shows once`() {
        val first = row("a", ContentType.MOVIE, title("tt1", released = "2026-01-01"))
        val second = row("b", ContentType.MOVIE, title("tt1", released = "2026-01-01"), title("tt2", released = "2025-01-01"))
        val entries = recentlyAddedEntries(listOf(first, second), setOf(first.legacyKey(), second.legacyKey()), today, maxAgeDays = null)
        assertEquals(listOf("tt1", "tt2"), entries.map { it.item.id })
    }

    @Test
    fun `titles of the same date take turns between rows`() {
        val a = row("a", ContentType.MOVIE, title("a1", releaseInfo = "2026"), title("a2", releaseInfo = "2026"))
        val b = row("b", ContentType.MOVIE, title("b1", releaseInfo = "2026"), title("b2", releaseInfo = "2026"))
        val entries = recentlyAddedEntries(listOf(a, b), setOf(a.legacyKey(), b.legacyKey()), today, maxAgeDays = null)
        assertEquals(listOf("a1", "b1", "a2", "b2"), entries.map { it.item.id })
    }

    @Test
    fun `no picked rows means no row`() {
        assertTrue(recentlyAddedEntries(listOf(arabicMovies), emptySet(), today).isEmpty())
    }

    @Test
    fun `the row stops at its limit`() {
        val many = row("many", ContentType.MOVIE, *Array(60) { title("t$it", releaseInfo = "2020") })
        assertEquals(RECENTLY_ADDED_MAX_ITEMS, recentlyAddedEntries(listOf(many), setOf(many.legacyKey()), today, maxAgeDays = null).size)
    }

    @Test
    fun `release dates read from full dates, then years`() {
        assertEquals(LocalDate.of(2026, 10, 1), title("x", released = "2026-10-01T00:00:00.000Z").releaseSortDate())
        assertEquals(LocalDate.of(2019, 1, 1), title("x", releaseInfo = "2019-2023").releaseSortDate())
        assertEquals(LocalDate.of(2024, 1, 1), title("x", releaseInfo = "2024-").releaseSortDate())
        assertNull(title("x").releaseSortDate())
    }

    @Test
    fun `the Classic row names no add-on and offers no See all`() {
        val entries = recentlyAddedEntries(listOf(arabicMovies), setOf(arabicMovies.legacyKey()), today, maxAgeDays = null)
        val row = recentlyAddedCatalogRow(entries, "Recently added")!!
        assertEquals("", row.addonBaseUrl)
        assertEquals(entries.map { it.item.id }, row.items.map { it.id })
        assertTrue(row.isRecentlyAddedRow())
        assertTrue(!arabicMovies.isRecentlyAddedRow())
        assertNull(recentlyAddedCatalogRow(emptyList(), "Recently added"))
    }

    @Test
    fun `only titles out within the last week make the row`() {
        val row = row(
            "fresh", ContentType.MOVIE,
            title("today", released = "2026-11-13T00:00:00.000Z"),
            title("six-days", released = "2026-11-07"),
            title("eight-days", released = "2026-11-05"),
            title("year-only", releaseInfo = "2026"),
            title("tomorrow", released = "2026-11-14")
        )
        val entries = recentlyAddedEntries(listOf(row), setOf(row.legacyKey()), today)
        assertEquals(listOf("today", "six-days"), entries.map { it.item.id })
    }

    @Test
    fun `TMDB dates count for titles the add-on gives no date`() {
        // Aradeb's "latest" rows carry no dates at all.
        val row = row("aradeb", ContentType.SERIES, title("tt1"), title("tt2"), title("tt3"))
        val tmdb = mapOf("tt1" to LocalDate.of(2026, 11, 12), "tt2" to LocalDate.of(2026, 9, 1))
        val entries = recentlyAddedEntries(listOf(row), setOf(row.legacyKey()), today, dateOf = { tmdb[it.id] })
        assertEquals(listOf("tt1"), entries.map { it.item.id })
    }
}
