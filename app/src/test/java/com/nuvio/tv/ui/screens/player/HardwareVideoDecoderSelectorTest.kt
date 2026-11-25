package com.nuvio.tv.ui.screens.player

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareVideoDecoderSelectorTest {

    private fun decoder(name: String, mimeType: String, softwareOnly: Boolean): MediaCodecInfo =
        MediaCodecInfo.newInstance(
            name,
            mimeType,
            mimeType,
            null,
            !softwareOnly,
            softwareOnly,
            !softwareOnly,
            false,
            false,
        )

    /** No codec string and no size: every decoder of the type reports the format as supported. */
    private fun format(mimeType: String): Format = Format.Builder().setSampleMimeType(mimeType).build()

    private fun names(format: Format, vararg decoders: MediaCodecInfo): List<String> =
        hardwareFirstVideoDecoders(format, decoders.filter { it.mimeType == format.sampleMimeType }).map { it.name }

    private data class FakeDecoder(val name: String, val software: Boolean, val vp9Profiles: Set<Int>)

    private fun vp9Profile(codecs: String): Int = codecs.split('.')[1].toInt()

    private fun vp9(codecs: String, decoders: List<FakeDecoder>): List<String> =
        hardwareFirstVideoDecoders(MimeTypes.VIDEO_VP9, decoders, { it.software }) {
            vp9Profile(codecs) in it.vp9Profiles
        }.map { it.name }

    @Test
    fun `VP9 profile 2 on profile-0-only hardware keeps the software decoder`() {
        val box = listOf(
            FakeDecoder("c2.vendor.vp9.decoder", software = false, vp9Profiles = setOf(0)),
            FakeDecoder("c2.android.vp9.decoder", software = true, vp9Profiles = setOf(0, 2)),
        )
        assertEquals(listOf("c2.vendor.vp9.decoder", "c2.android.vp9.decoder"), vp9("vp09.02.40.10", box))
        assertEquals(listOf("c2.vendor.vp9.decoder"), vp9("vp09.00.40.08", box))
    }

    @Test
    fun `software is dropped as soon as one hardware decoder supports the format`() {
        val box = listOf(
            FakeDecoder("OMX.vendor.vp9.decoder", software = false, vp9Profiles = setOf(0)),
            FakeDecoder("c2.vendor.vp9.decoder", software = false, vp9Profiles = setOf(0, 2)),
            FakeDecoder("c2.android.vp9.decoder", software = true, vp9Profiles = setOf(0, 2)),
        )
        assertEquals(listOf("OMX.vendor.vp9.decoder", "c2.vendor.vp9.decoder"), vp9("vp09.02.40.10", box))
    }

    @Test
    fun `a capability check that fails counts as not supported and keeps the whole list`() {
        // The JVM stubs make media3's size check throw here.
        val sized = Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H265).setWidth(7680).setHeight(4320).build()
        val decoders = listOf(
            decoder("c2.amlogic.hevc.decoder", MimeTypes.VIDEO_H265, softwareOnly = false),
            decoder("c2.android.hevc.decoder", MimeTypes.VIDEO_H265, softwareOnly = true),
        )
        assertEquals(decoders.map { it.name }, hardwareFirstVideoDecoders(sized, decoders).map { it.name })
    }

    @Test
    fun `software HEVC and H264 are dropped when the hardware supports the format`() {
        val decoders = arrayOf(
            decoder("c2.amlogic.hevc.decoder", MimeTypes.VIDEO_H265, softwareOnly = false),
            decoder("c2.android.hevc.decoder", MimeTypes.VIDEO_H265, softwareOnly = true),
            decoder("OMX.amlogic.avc.decoder.awesome2", MimeTypes.VIDEO_H264, softwareOnly = false),
            decoder("OMX.google.h264.decoder", MimeTypes.VIDEO_H264, softwareOnly = true),
            decoder("c2.android.avc.decoder", MimeTypes.VIDEO_H264, softwareOnly = true),
        )
        assertEquals(listOf("c2.amlogic.hevc.decoder"), names(format(MimeTypes.VIDEO_H265), *decoders))
        assertEquals(listOf("OMX.amlogic.avc.decoder.awesome2"), names(format(MimeTypes.VIDEO_H264), *decoders))
    }

    @Test
    fun `software stays when it is the only decoder for the type`() {
        val decoders = arrayOf(
            decoder("c2.android.vp9.decoder", MimeTypes.VIDEO_VP9, softwareOnly = true),
            decoder("c2.android.av1.decoder", MimeTypes.VIDEO_AV1, softwareOnly = true),
        )
        assertEquals(listOf("c2.android.vp9.decoder"), names(format(MimeTypes.VIDEO_VP9), *decoders))
        assertEquals(listOf("c2.android.av1.decoder"), names(format(MimeTypes.VIDEO_AV1), *decoders))
    }

    @Test
    fun `Android's software AV1 stays when the hardware AV1 decoder cannot take the stream`() {
        val decoders = listOf("c2.amlogic.av1.decoder" to false, "c2.android.av1.decoder" to true)
        val kept = hardwareFirstVideoDecoders(MimeTypes.VIDEO_AV1, decoders, { it.second }) { it.second }
        assertEquals(decoders, kept)
        val dropped = hardwareFirstVideoDecoders(MimeTypes.VIDEO_AV1, decoders, { it.second }) { true }
        assertEquals(listOf(decoders[0]), dropped)
    }

    @Test
    fun `name prefix catches a software decoder the flag misses`() {
        val decoders = arrayOf(
            decoder("c2.mtk.hevc.decoder", MimeTypes.VIDEO_H265, softwareOnly = false),
            decoder("c2.android.hevc.decoder", MimeTypes.VIDEO_H265, softwareOnly = false),
        )
        assertEquals(listOf("c2.mtk.hevc.decoder"), names(format(MimeTypes.VIDEO_H265), *decoders))
    }

    @Test
    fun `hardware order is kept`() {
        val decoders = arrayOf(
            decoder("c2.amlogic.dolby-vision.dvhe.decoder", MimeTypes.VIDEO_DOLBY_VISION, softwareOnly = false),
            decoder("c2.android.dv.decoder", MimeTypes.VIDEO_DOLBY_VISION, softwareOnly = true),
            decoder("c2.amlogic.dolby-vision.dvav.decoder", MimeTypes.VIDEO_DOLBY_VISION, softwareOnly = false),
        )
        assertEquals(
            listOf("c2.amlogic.dolby-vision.dvhe.decoder", "c2.amlogic.dolby-vision.dvav.decoder"),
            names(format(MimeTypes.VIDEO_DOLBY_VISION), *decoders),
        )
    }

    @Test
    fun `other video types are untouched`() {
        val decoders = arrayOf(
            decoder("c2.amlogic.vc1.decoder", "video/vc1", softwareOnly = false),
            decoder("c2.amlogic.vc1.decoder.sw", "video/vc1", softwareOnly = true),
            decoder("c2.amlogic.mpeg2.decoder", MimeTypes.VIDEO_MPEG2, softwareOnly = false),
            decoder("c2.android.mpeg2.decoder", MimeTypes.VIDEO_MPEG2, softwareOnly = true),
        )
        assertEquals(listOf("c2.amlogic.vc1.decoder", "c2.amlogic.vc1.decoder.sw"), names(format("video/vc1"), *decoders))
        assertEquals(
            listOf("c2.amlogic.mpeg2.decoder", "c2.android.mpeg2.decoder"),
            names(format(MimeTypes.VIDEO_MPEG2), *decoders),
        )
    }

    @Test
    fun `empty list stays empty`() {
        assertTrue(hardwareFirstVideoDecoders(format(MimeTypes.VIDEO_H265), emptyList()).isEmpty())
    }

    @Test
    fun `decoder names in the log line`() {
        val softwareOnly = setOf("c2.amlogic.vc1.decoder.sw")
        assertTrue(isSoftwareVideoDecoderName("c2.android.hevc.decoder", emptySet()))
        assertTrue(isSoftwareVideoDecoderName("OMX.google.h264.decoder", emptySet()))
        assertTrue(isSoftwareVideoDecoderName("libdav1d", emptySet()))
        assertTrue(isSoftwareVideoDecoderName("libgav1", emptySet()))
        assertTrue(isSoftwareVideoDecoderName("C2.AMLOGIC.VC1.DECODER.SW", softwareOnly))
        assertFalse(isSoftwareVideoDecoderName("c2.amlogic.hevc.decoder", softwareOnly))
        assertFalse(isSoftwareVideoDecoderName("c2.amlogic.dolby-vision.dvhe.decoder", softwareOnly))
        assertFalse(isSoftwareVideoDecoderName("amstream_dves_hevc.sideband", softwareOnly))
        assertFalse(isSoftwareVideoDecoderName("OMX.MTK.VIDEO.DECODER.HEVC", softwareOnly))
    }

    private fun dolbyVision(codecs: String): Format =
        Format.Builder().setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION).setCodecs(codecs).build()

    /** The Google TV Streamer's, in its order, with the Dolby Vision profiles each lists. */
    private val streamer = listOf(
        "c2.mtk.dvav.ser.decoder" to listOf(512),
        "c2.mtk.dvav1.decoder" to listOf(1024),
        "c2.mtk.dvhe.dtr.decoder" to listOf(16),
        "c2.mtk.dvhe.sth.decoder" to listOf(256),
        "c2.mtk.dvhe.stn.decoder" to listOf(32),
    )
    private val hevc = listOf("c2.mtk.hevc.decoder" to emptyList<Int>())

    private fun dv7(
        decoders: List<Pair<String, List<Int>>>,
        playBaseLayer: Boolean = false,
    ): List<String> = dolbyVision7Decoders(
        decoders = decoders,
        hevc = { hevc },
        playBaseLayer = playBaseLayer,
        supportsFormat = { 128 in it.second },
        canDecodeHevc = { canDecodeHevcDolbyVision(it.second) },
    ).map { it.first }

    @Test
    fun `Dolby Vision profile 7 plays its base layer where the first decoder is for H264`() {
        assertEquals("c2.mtk.hevc.decoder", dv7(streamer).first())
        assertEquals(6, dv7(streamer).size)
    }

    @Test
    fun `a decoder that takes profile 7, or may, keeps its place`() {
        val shield = listOf("OMX.Nvidia.DOVI.decode" to listOf(32, 128))
        val unlisted = listOf("c2.amlogic.dolby-vision.dvhe.decoder" to listOf(256))
        assertEquals(shield.map { it.first }, dv7(shield))
        assertEquals(unlisted.map { it.first }, dv7(unlisted))
        assertEquals(listOf("no.profiles"), dv7(listOf("no.profiles" to emptyList())))
    }

    @Test
    fun `asking for the base layer plays it whatever Dolby Vision decoders there are`() {
        assertEquals("c2.mtk.hevc.decoder", dv7(streamer, playBaseLayer = true).first())
        assertEquals("c2.mtk.hevc.decoder", dv7(listOf("OMX.Nvidia.DOVI.decode" to listOf(128)), playBaseLayer = true).first())
    }

    @Test
    fun `without a Dolby Vision decoder the base layer is played`() {
        assertEquals(listOf("c2.mtk.hevc.decoder"), dv7(emptyList()))
    }

    @Test
    fun `only profile 7 is recognised as such`() {
        assertTrue(isDolbyVisionProfile7(dolbyVision("dvhe.07.06")))
        assertTrue(isDolbyVisionProfile7(dolbyVision("dvh1.07.06")))
        assertFalse(isDolbyVisionProfile7(dolbyVision("dvhe.05.06")))
        assertFalse(isDolbyVisionProfile7(dolbyVision("dvhe.08.06")))
        assertFalse(isDolbyVisionProfile7(format(MimeTypes.VIDEO_H265)))
        assertFalse(isDolbyVisionProfile7(format(MimeTypes.VIDEO_DOLBY_VISION)))
    }

    @Test
    fun `only a box that may take profile 7 gets its Dolby Vision data`() {
        assertFalse(canDecodeDolbyVision7(streamer.map { it.second }))
        assertTrue(canDecodeDolbyVision7(listOf(listOf(32, 128))))
        assertTrue(canDecodeDolbyVision7(listOf(listOf(512), listOf(128))))
        assertTrue(canDecodeDolbyVision7(listOf(listOf(256))))
        assertFalse(canDecodeDolbyVision7(emptyList<List<Int>>()))
    }
}
