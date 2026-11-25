package com.nuvio.tv.core.player

import com.nuvio.tv.core.player.dvmkv.DolbyVisionCompatibility
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DolbyVision7BaseLayerTest {

    /** A length-delimited HEVC NAL unit of [type] in layer 0. */
    private fun nal(type: Int, vararg payload: Int): ByteArray {
        val body = byteArrayOf((type shl 1).toByte(), 1) + payload.map { it.toByte() }.toByteArray()
        return byteArrayOf(0, 0, 0, body.size.toByte()) + body
    }

    private val picture = nal(19, 1, 2, 3)
    private val enhancementLayer = nal(63, 4, 5)
    private val rpu = nal(62, 6)

    /** A DOVIDecoderConfigurationRecord of [profile], level 6. */
    private fun config(profile: Int) = byteArrayOf(1, 0, (profile shl 1).toByte(), (6 shl 3).toByte(), 0)

    private fun transformer(stripProfile7: Boolean = true) =
        DolbyVisionMatroskaTransformer(DolbyVisionConversionConfig(active = false), stripProfile7 = stripProfile7)

    @After
    fun reset() {
        DolbyVisionCompatibility.setProfile7BaseLayerActive(false)
        DolbyVisionCompatibility.setHdr10BaseLayerModeActive(false)
    }

    @Test
    fun `a profile 7 sample keeps its base layer only`() {
        val sample = picture + enhancementLayer + rpu
        val transformer = transformer()
        assertTrue(transformer.shouldTransform("dvhe.07.06", config(7)))
        val out = transformer.transformHevcSample(sample, sample.size, 4, null, config(7))!!
        assertArrayEquals(picture, out.copyOf(transformer.lastTransformedSampleLength()))
        assertEquals(0, transformer.onDolbyVisionBlockAdditionalData(rpu, 4, config(7))!!.size)
    }

    @Test
    fun `other profiles keep their Dolby Vision data`() {
        val transformer = transformer()
        assertFalse(transformer.shouldTransform("dvhe.08.06", config(8)))
        assertFalse(transformer.shouldTransform("dvhe.05.06", config(5)))
        assertNull(transformer.onDolbyVisionBlockAdditionalData(rpu, 4, config(8)))
    }

    @Test
    fun `profile 7 is untouched unless asked`() {
        assertFalse(transformer(stripProfile7 = false).shouldTransform("dvhe.07.06", config(7)))
    }

    @Test
    fun `only profile 7 tracks are exposed as HEVC`() {
        assertFalse(DolbyVisionCompatibility.playsBaseLayerOnly("dvhe.07.06"))
        DolbyVisionCompatibility.setProfile7BaseLayerActive(true)
        assertTrue(DolbyVisionCompatibility.playsBaseLayerOnly("dvhe.07.06"))
        assertFalse(DolbyVisionCompatibility.playsBaseLayerOnly("dvhe.08.06"))
        assertFalse(DolbyVisionCompatibility.playsBaseLayerOnly("dvhe.05.06"))
        DolbyVisionCompatibility.setHdr10BaseLayerModeActive(true)
        assertTrue(DolbyVisionCompatibility.playsBaseLayerOnly("dvhe.08.06"))
    }
}
