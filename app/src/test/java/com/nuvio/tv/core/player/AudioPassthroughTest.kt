package com.nuvio.tv.core.player

import androidx.media3.common.C
import com.nuvio.tv.ui.screens.player.keptEncodings
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioPassthroughTest {
    @Test
    fun `switched off formats are removed from what the TV reports`() {
        val tv = setOf(C.ENCODING_PCM_16BIT, C.ENCODING_AC3, C.ENCODING_E_AC3, C.ENCODING_DOLBY_TRUEHD, C.ENCODING_DTS, C.ENCODING_DTS_HD)
        val blocked = AudioPassthroughPreferences.blockedEncodings(setOf(PassthroughFormat.DTS, PassthroughFormat.DTS_HD))
        assertEquals(
            listOf(C.ENCODING_PCM_16BIT, C.ENCODING_AC3, C.ENCODING_E_AC3, C.ENCODING_DOLBY_TRUEHD),
            keptEncodings({ it in tv }, blocked).toList()
        )
    }

    @Test
    fun `dolby digital plus covers atmos too`() {
        assertEquals(
            setOf(C.ENCODING_E_AC3, C.ENCODING_E_AC3_JOC),
            AudioPassthroughPreferences.blockedEncodings(setOf(PassthroughFormat.DOLBY_DIGITAL_PLUS))
        )
    }
}
