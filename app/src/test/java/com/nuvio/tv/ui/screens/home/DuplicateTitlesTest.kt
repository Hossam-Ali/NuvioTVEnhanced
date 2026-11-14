package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DuplicateTitlesTest {

    private fun title(id: String, imdbId: String? = null) = MetaPreview(
        id = id, type = ContentType.MOVIE, name = id, poster = null, posterShape = PosterShape.POSTER,
        background = null, logo = null, description = null, releaseInfo = null,
        imdbRating = null, genres = emptyList(), imdbId = imdbId
    )

    private fun row(catalogId: String, vararg items: MetaPreview) = CatalogRow(
        addonId = "addon", addonName = "Addon", addonBaseUrl = "https://addon", catalogId = catalogId,
        catalogName = catalogId, type = ContentType.MOVIE, items = items.toList()
    )

    @Test
    fun `each title stays only in the first row that has it`() {
        val rows = withoutDuplicateTitles(
            listOf(
                row("prime", title("tt1"), title("tt2")),
                row("netflix", title("tt2"), title("tt3")),
                row("popular", title("tt1"), title("tt3"), title("tt4"))
            )
        )
        assertEquals(listOf(listOf("tt1", "tt2"), listOf("tt3"), listOf("tt4")), rows.map { row -> row.items.map { it.id } })
    }

    @Test
    fun `titles in Recently added are taken out of the rows below`() {
        val rows = withoutDuplicateTitles(listOf(row("prime", title("tt1"), title("tt2"))), alreadyShown = listOf(title("tt1")))
        assertEquals(listOf("tt2"), rows.single().items.map { it.id })
    }

    @Test
    fun `the same film with another add-on id is matched by its IMDb id`() {
        val rows = withoutDuplicateTitles(
            listOf(row("a", title("tt5")), row("b", title("tmdb:99", imdbId = "tt5"), title("tmdb:100")))
        )
        assertEquals(listOf("tmdb:100"), rows[1].items.map { it.id })
    }

    @Test
    fun `a row whose titles are all shown higher up ends up empty`() {
        val rows = withoutDuplicateTitles(listOf(row("a", title("tt1")), row("b", title("tt1"))))
        assertEquals(0, rows[1].items.size)
    }

    @Test
    fun `rows without duplicates are kept as they are`() {
        val original = row("a", title("tt1"), title("tt2"))
        assertSame(original, withoutDuplicateTitles(listOf(original)).single())
    }

    @Test
    fun `loading placeholders are never removed`() {
        val rows = withoutDuplicateTitles(
            listOf(row("a", title("__placeholder_x_0")), row("b", title("__placeholder_x_0")))
        )
        assertEquals(1, rows[1].items.size)
    }
}
