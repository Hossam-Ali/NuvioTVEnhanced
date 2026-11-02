package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.core.subtitles.TranscribedLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioTranscriptionSessionTest {
    @Test
    fun `coverage follows done and pending clips from the playback position`() {
        val session = AudioTranscriptionSession(file = null)
        assertEquals(5_000L, session.coveredEnd(5_000L))
        val first = session.markPending(5_000L, 15_000L)
        session.markPending(15_000L, 45_000L)
        assertEquals(45_000L, session.coveredEnd(5_000L))
        session.complete(first, listOf(TranscribedLine(6_000L, 8_000L, "أهلاً")))
        assertEquals(45_000L, session.coveredEnd(10_000L))
        assertEquals(90_000L, session.coveredEnd(90_000L))
    }

    @Test
    fun `a failed clip is no longer covered`() {
        val session = AudioTranscriptionSession(file = null)
        val range = session.markPending(0L, 10_000L)
        session.fail(range)
        assertEquals(0L, session.coveredEnd(0L))
    }

    @Test
    fun `the line on screen is found by position`() {
        val lines = listOf(TranscribedLine(1_000L, 2_000L, "a"), TranscribedLine(3_000L, 4_000L, "b"))
        assertEquals("a", transcribedLineAt(lines, 1_500L))
        assertNull(transcribedLineAt(lines, 2_500L))
        assertEquals("b", transcribedLineAt(lines, 3_000L))
    }

    @Test
    fun `a seek into audio only a slow clip covers asks for a near clip once`() {
        val session = AudioTranscriptionSession(file = null)
        session.markPending(30_000L, 60_000L)
        assertEquals(true, session.needsNearClip(40_000L))
        session.markPending(40_000L, 48_000L, near = true)
        assertEquals(false, session.needsNearClip(40_000L))
        // Nothing pending at all is the normal path, not a near re-request.
        assertEquals(false, session.needsNearClip(90_000L))
    }
}
