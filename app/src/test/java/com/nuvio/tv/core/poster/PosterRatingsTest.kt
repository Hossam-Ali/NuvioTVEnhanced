package com.nuvio.tv.core.poster

import com.nuvio.tv.domain.model.MDBListRatings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PosterRatingsTest {
    @Test
    fun `badges follow the chosen order and skip missing ratings`() {
        val settings = PosterRatingsSettings(providers = listOf("tomatoes", "imdb", "tmdb"), maxBadges = 2)
        val ratings = MDBListRatings(imdb = 8.1, tmdb = 7.9)
        assertEquals(listOf("imdb" to 8.1, "tmdb" to 7.9), settings.badgesFor(ratings))
        assertEquals(listOf("tomatoes" to 91.0), settings.copy(maxBadges = 1).badgesFor(ratings.copy(tomatoes = 91.0)))
    }

    @Test
    fun `icon packs accept nested or flat maps and common provider names`() {
        val nested = parseIconPack("""{"name":"Pack","icons":{"IMDb":"https://x/imdb.png","rottentomatoes":"https://x/rt.png","bogus":"https://x/b.png","tmdb":"not a url"}}""")
        assertEquals(mapOf("imdb" to "https://x/imdb.png", "tomatoes" to "https://x/rt.png"), nested)
        assertEquals(mapOf("mal" to "https://x/mal.png"), parseIconPack("""{"myanimelist":"https://x/mal.png"}"""))
    }

    @Test
    fun `settings survive saving and loading`() {
        val settings = PosterRatingsSettings(
            enabled = true,
            providers = listOf("letterboxd", "imdb"),
            maxBadges = 3,
            position = PosterRatingPosition.BOTTOM_START,
            iconPackUrl = "https://x/pack.json",
            iconPack = mapOf("imdb" to "https://x/imdb.png")
        )
        assertEquals(settings, PosterRatingsPreferences.decode(PosterRatingsPreferences.encode(settings)))
    }

    @Test
    fun `row styles override the layout's card shape`() {
        val styles = mapOf("a" to HomeRowStyle.LANDSCAPE, "b" to HomeRowStyle.POSTER)
        assertTrue(styles.landscapeFor("a", globalLandscape = false))
        assertFalse(styles.landscapeFor("b", globalLandscape = true))
        assertTrue(styles.landscapeFor("c", globalLandscape = true))
        assertEquals(styles, HomeRowStylePreferences.decode(HomeRowStylePreferences.encode(styles)))
        assertEquals(emptyMap<String, HomeRowStyle>(), HomeRowStylePreferences.decode("""{"x":"WIDE"}"""))
    }
}

class SubtitleStylePresetTest {
    @Test
    fun `a style matches the preset it was set from`() {
        val preset = com.nuvio.tv.data.local.SubtitleStylePreset.BOXED
        val style = com.nuvio.tv.data.local.SubtitleStyleSettings(
            size = preset.size, verticalOffset = preset.verticalOffset, bold = preset.bold,
            textColor = preset.textColor, backgroundColor = preset.backgroundColor,
            outlineEnabled = preset.outlineEnabled, outlineColor = 123, outlineWidth = 4
        )
        // Without an outline, its colour and width don't matter.
        assertEquals(preset, com.nuvio.tv.data.local.SubtitleStylePreset.matching(style))
        assertEquals(null, com.nuvio.tv.data.local.SubtitleStylePreset.matching(style.copy(size = 77)))
    }

    @Test
    fun `connecting to an add-on never waits more than half a minute`() {
        assertEquals(30, com.nuvio.tv.core.network.AddonNetworkPreferences.connectTimeoutSeconds(120))
        assertEquals(15, com.nuvio.tv.core.network.AddonNetworkPreferences.connectTimeoutSeconds(15))
    }
}
