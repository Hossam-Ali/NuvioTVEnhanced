package com.nuvio.tv.ui.screens.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TracksAreKnownTest {

    @Test
    fun `the empty list of a stopped or replaced video says nothing about its subtitles`() {
        assertFalse(tracksAreKnown(Tracks.EMPTY))
    }

    @Test
    fun `a video that opened without subtitles has known tracks`() {
        val video = TrackGroup(Format.Builder().setSampleMimeType("video/avc").build())
        val group = Tracks.Group(video, false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(true))
        assertTrue(tracksAreKnown(Tracks(listOf(group))))
    }
}
