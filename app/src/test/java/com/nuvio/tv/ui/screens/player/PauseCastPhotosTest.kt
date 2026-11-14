package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.domain.model.MetaCastMember
import org.junit.Assert.assertEquals
import org.junit.Test

class PauseCastPhotosTest {

    private val tmdb = listOf(
        MetaCastMember(name = "Aldis Hodge", character = "Alex Cross", photo = "https://img/aldis.jpg", tmdbId = 1),
        MetaCastMember(name = "Isaiah Mustafa", character = "John Sampson", photo = "https://img/isaiah.jpg", tmdbId = 2)
    )

    @Test
    fun `an add-on cast of names only takes TMDB's cast with photos`() {
        val addon = listOf(MetaCastMember(name = "Aldis Hodge"), MetaCastMember(name = "Isaiah Mustafa"))
        assertEquals(tmdb, castWithTmdbPhotos(addon, tmdb))
    }

    @Test
    fun `a partial add-on cast keeps its order and gets the missing photos`() {
        val addon = listOf(
            MetaCastMember(name = "Isaiah Mustafa", photo = "https://addon/isaiah.jpg"),
            MetaCastMember(name = "aldis hodge"),
            MetaCastMember(name = "Someone Else")
        )
        val merged = castWithTmdbPhotos(addon, tmdb)
        assertEquals(listOf("https://addon/isaiah.jpg", "https://img/aldis.jpg", null), merged.map { it.photo })
        assertEquals("Alex Cross", merged[1].character)
        assertEquals("Someone Else", merged[2].name)
    }

    @Test
    fun `no TMDB cast leaves the add-on's as it is`() {
        val addon = listOf(MetaCastMember(name = "Aldis Hodge"))
        assertEquals(addon, castWithTmdbPhotos(addon, emptyList()))
    }

    @Test
    fun `no add-on cast takes TMDB's`() {
        assertEquals(tmdb, castWithTmdbPhotos(emptyList(), tmdb))
    }
}
