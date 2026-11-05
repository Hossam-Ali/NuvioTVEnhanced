package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.data.repository.SkipInterval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmoothSkipIntervalsTest {
    private fun seg(start: Double, end: Double, type: String = "intro") = SkipInterval(start, end, type, "chapters")

    @Test fun `a one-second segment is dropped`() {
        assertTrue(smoothSkipIntervals(listOf(seg(10.0, 11.0))).isEmpty())
    }

    @Test fun `segments a few seconds apart become one, so the button stays up`() {
        val result = smoothSkipIntervals(listOf(seg(60.0, 70.0, "recap"), seg(73.0, 130.0, "intro")))
        assertEquals(listOf(seg(60.0, 130.0, "intro")), result)
        // Between the two, the button no longer disappears.
        assertNotNull(findActiveSkipInterval(result, 71_500L))
    }

    @Test fun `a short flash before the real intro no longer shows then hides`() {
        val result = smoothSkipIntervals(listOf(seg(5.0, 6.0), seg(9.0, 80.0)))
        assertEquals(listOf(seg(9.0, 80.0)), result)
    }

    @Test fun `segments far apart stay separate and post-credits scenes are kept`() {
        val postCredits = seg(2500.0, 2600.0, "post-credits")
        val result = smoothSkipIntervals(listOf(seg(60.0, 120.0), seg(2400.0, 2490.0, "outro"), postCredits))
        assertEquals(listOf(seg(60.0, 120.0), seg(2400.0, 2490.0, "outro"), postCredits), result)
    }

    @Test fun `overlapping segments from two sources become one`() {
        val result = smoothSkipIntervals(listOf(seg(60.0, 120.0), seg(62.0, 118.0)))
        assertEquals(listOf(seg(60.0, 120.0)), result)
    }

    @Test fun `the button stays up when chapters move the intro start a few seconds later`() {
        // Seen on a real episode: the online intro (0:00-3:00) was showing at 0:01 when the
        // video's chapters replaced it with 0:05-3:14.
        val online = SkipInterval(0.0, 180.0, "intro", "introdb")
        val chapters = listOf(SkipInterval(5.0, 194.0, "intro", "chapters"))
        assertEquals(chapters.first(), upcomingSameKindSkipInterval(chapters, online, 1_245L))
    }

    @Test fun `a segment of another kind or too far ahead doesn't keep the button`() {
        val intro = seg(0.0, 60.0)
        assertEquals(null, upcomingSameKindSkipInterval(listOf(seg(70.0, 90.0, "recap")), intro, 61_000L))
        assertEquals(null, upcomingSameKindSkipInterval(listOf(seg(80.0, 140.0)), intro, 61_000L))
    }
}
