package com.nuvio.tv.ui.screens.detail

import com.nuvio.tv.domain.model.PosterShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewLookPosterCardSizeTest {

    @Test
    fun `wide pictures get a wide card so nothing is cropped away`() {
        val (width, height) = newLookPosterCardSize(PosterShape.LANDSCAPE)
        assertEquals(16f / 9f, width.value / height.value, 0.02f)
    }

    @Test
    fun `posters keep a poster-shaped card`() {
        val (width, height) = newLookPosterCardSize(PosterShape.POSTER)
        assertEquals(2f / 3f, width.value / height.value, 0.01f)
    }

    @Test
    fun `square pictures get a square card`() {
        val (width, height) = newLookPosterCardSize(PosterShape.SQUARE)
        assertTrue(width == height)
    }
}
