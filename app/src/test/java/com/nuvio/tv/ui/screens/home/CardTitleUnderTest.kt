package com.nuvio.tv.ui.screens.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardTitleUnderTest {

    @Test
    fun `wide cards always name the title under the picture`() {
        assertTrue(titleUnderCard(showLabels = false, wideCard = true, expanded = false, title = "VISA"))
        assertTrue(titleUnderCard(showLabels = true, wideCard = true, expanded = false, title = "VISA"))
    }

    @Test
    fun `posters follow the poster labels setting`() {
        assertTrue(titleUnderCard(showLabels = true, wideCard = false, expanded = false, title = "Suits"))
        assertFalse(titleUnderCard(showLabels = false, wideCard = false, expanded = false, title = "Suits"))
    }

    @Test
    fun `an expanded card or a blank title has nothing under it`() {
        assertFalse(titleUnderCard(showLabels = true, wideCard = true, expanded = true, title = "VISA"))
        assertFalse(titleUnderCard(showLabels = true, wideCard = true, expanded = false, title = " "))
    }
}
