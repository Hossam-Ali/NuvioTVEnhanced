package com.nuvio.tv.ui.screens.player

import android.graphics.Bitmap
import androidx.media3.common.text.Cue
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PictureSubtitleCueTest {

    private fun picture(line: Float, height: Float = 0.08f): Cue = Cue.Builder()
        .setBitmap(mockk<Bitmap>(relaxed = true))
        .setLine(line, Cue.LINE_TYPE_FRACTION)
        .setLineAnchor(Cue.ANCHOR_TYPE_START)
        .setBitmapHeight(height)
        .build()

    @Test
    fun `two pictures at the bottom become one cue, top to bottom`() {
        val cues = pictureTranslationCues(listOf(picture(0.90f) to "الثاني", picture(0.80f) to "الأول"))
        assertEquals(listOf("الأول\nالثاني"), cues.map { it.text.toString() })
        assertNull(cues.single().bitmap)
        assertEquals(Cue.DIMEN_UNSET, cues.single().line)
    }

    @Test
    fun `a picture at the top stays at the top`() {
        val cues = pictureTranslationCues(listOf(picture(0.05f) to "فوق", picture(0.85f) to "تحت"))
        assertEquals(listOf("فوق", "تحت"), cues.map { it.text.toString() })
        assertEquals(0.05f, cues.first().line)
    }

    @Test
    fun `nothing read gives no cues`() {
        assertTrue(pictureTranslationCues(emptyList()).isEmpty())
    }

    @Test
    fun `only the selected track's pictures are read`() {
        assertTrue(isSelectedSubtitleTrack("5", "5"))
        assertTrue(isSelectedSubtitleTrack("5", "1:5"))
        assertFalse(isSelectedSubtitleTrack("5", "15"))
        assertFalse(isSelectedSubtitleTrack("5", "6"))
        assertTrue(isSelectedSubtitleTrack("5", null))
        assertTrue(isSelectedSubtitleTrack(null, "5"))
    }

    @Test
    fun `picture tracks are told by their original type`() {
        assertTrue(isPictureSubtitleMime("application/pgs"))
        assertTrue(isPictureSubtitleMime("application/vobsub"))
        assertFalse(isPictureSubtitleMime("application/x-subrip"))
        assertFalse(isPictureSubtitleMime(null))
    }
}
