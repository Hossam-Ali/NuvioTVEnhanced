package com.nuvio.tv.ui.screens.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptionReadTurnTest {

    private fun mayRead(aheadMs: Long, wasReading: Boolean, ready: Boolean = true, loading: Boolean = true) =
        mayReadTranscriptionAudio(ready, loading, aheadMs, wasReading)

    @Test
    fun `reading waits while the video is buffering`() {
        assertFalse(mayRead(30_000, wasReading = true, ready = false))
        assertFalse(mayRead(30_000, wasReading = true, ready = false, loading = false))
    }

    @Test
    fun `reading starts with a good buffer and stops with a low one`() {
        assertFalse(mayRead(8_000, wasReading = false))
        assertTrue(mayRead(12_000, wasReading = false))
        assertTrue(mayRead(8_000, wasReading = true))
        assertFalse(mayRead(7_999, wasReading = true))
    }

    @Test
    fun `a video that has loaded all it wants leaves the connection free`() {
        assertTrue(mayRead(3_000, wasReading = false, loading = false))
    }
}
