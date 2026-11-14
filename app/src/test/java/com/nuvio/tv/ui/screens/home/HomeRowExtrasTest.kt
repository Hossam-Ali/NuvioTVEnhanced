package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.AddonCatalogCollectionSource
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.Collection
import com.nuvio.tv.domain.model.CollectionFolder
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.domain.model.catalogRowLegacyKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRowExtrasTest {

    private fun title(id: String, released: String? = null, releaseInfo: String? = null, imdbId: String? = null) = MetaPreview(
        id = id, type = ContentType.MOVIE, name = id, poster = null, posterShape = PosterShape.POSTER,
        background = null, logo = null, description = null, releaseInfo = releaseInfo,
        imdbRating = null, genres = emptyList(), released = released, imdbId = imdbId
    )

    private fun row(vararg items: MetaPreview) = CatalogRow(
        addonId = "addon", addonName = "Addon", addonBaseUrl = "https://addon", catalogId = "c",
        catalogName = "c", type = ContentType.MOVIE, items = items.toList()
    )

    @Test
    fun `a picked collection stands for the add-on catalogs its folders show`() {
        val collection = Collection(
            id = "arabic",
            title = "Arabic",
            folders = listOf(
                CollectionFolder(id = "f1", title = "Movies", sources = listOf(AddonCatalogCollectionSource("cinema", "movie", "latest"))),
                CollectionFolder(id = "f2", title = "Drama", sources = listOf(AddonCatalogCollectionSource("cinema", "series", "top", genre = "Drama")))
            )
        )
        val keys = recentlyAddedCatalogKeys(setOf("collection_arabic", "other_movie_new"), listOf(collection))
        assertEquals(setOf("other_movie_new", catalogRowLegacyKey("cinema", "movie", "latest")), keys)
    }

    @Test
    fun `rows sort newest first, undated titles last in their own order`() {
        val sorted = row(
            title("old", released = "2020-01-01"),
            title("undated-a"),
            title("new", released = "2026-11-10"),
            title("year", releaseInfo = "2024"),
            title("undated-b")
        ).sortedNewestFirst()
        assertEquals(listOf("new", "year", "old", "undated-a", "undated-b"), sorted.items.map { it.id })
    }

    @Test
    fun `a row already newest first is kept as it is`() {
        val original = row(title("new", released = "2026-11-10"), title("old", released = "2020-01-01"))
        assertSame(original, original.sortedNewestFirst())
    }

    @Test
    fun `cards of a show with a fresh episode are matched by id or IMDb id`() {
        val fresh = setOf("tt100")
        assertTrue(hasFreshEpisode(title("tt100"), fresh))
        assertTrue(hasFreshEpisode(title("tmdb:5", imdbId = "tt100"), fresh))
        assertFalse(hasFreshEpisode(title("tt200"), fresh))
        assertFalse(hasFreshEpisode(null, fresh))
    }

    @Test
    fun `trailers limited to Recently added play only on its cards`() {
        assertTrue(trailerAllowedFor("modern_recently_added::tt1", onlyRecentlyAdded = true))
        assertFalse(trailerAllowedFor("addon_movie_top::tt1", onlyRecentlyAdded = true))
        assertTrue(trailerAllowedFor("addon_movie_top::tt1", onlyRecentlyAdded = false))
    }
}
