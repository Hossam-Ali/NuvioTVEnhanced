package com.nuvio.tv.ui.util

import com.nuvio.tv.domain.model.MetaCastMember
import org.junit.Assert.assertEquals
import org.junit.Test

class CastPeopleTest {
    @Test fun `a person credited for several roles appears once`() {
        val cast = listOf(
            MetaCastMember("Ahmed Gabr", "Hamdy", tmdbId = 1),
            MetaCastMember("Khaled Diab", "Creator", tmdbId = 2),
            MetaCastMember("Khaled Diab", "Writer", tmdbId = 2),
            MetaCastMember("Sherine Diab", "Writer", tmdbId = 3),
            MetaCastMember("Khaled Diab", "Director", tmdbId = 2),
            MetaCastMember("Sherine Diab", "Creator", tmdbId = 3)
        )
        assertEquals(listOf("Ahmed Gabr", "Khaled Diab", "Sherine Diab"), cast.distinctPeople().map { it.name })
        assertEquals("Creator", cast.distinctPeople()[1].character)
    }

    @Test fun `people without an id are matched by name`() {
        val cast = listOf(MetaCastMember("Hend Sabry"), MetaCastMember(" hend sabry "), MetaCastMember("Other"))
        assertEquals(2, cast.distinctPeople().size)
    }

    @Test fun `only actors are kept`() {
        val cast = listOf(
            MetaCastMember("Ahmed Gabr", "Hamdy", tmdbId = 1),
            MetaCastMember("Khaled Diab", "Creator", tmdbId = 2),
            MetaCastMember("Sherine Diab", "writer", tmdbId = 3),
            MetaCastMember("Hend", null, tmdbId = 4)
        )
        assertEquals(listOf("Ahmed Gabr", "Hend"), cast.actorsOnly().map { it.name })
    }
}
