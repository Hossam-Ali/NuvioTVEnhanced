package com.nuvio.tv.ui.screens.player

import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import android.os.Build
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer

private val HARDWARE_FIRST_VIDEO_TYPES = setOf(
    MimeTypes.VIDEO_H264,
    MimeTypes.VIDEO_H265,
    MimeTypes.VIDEO_VP9,
    MimeTypes.VIDEO_AV1,
    MimeTypes.VIDEO_DOLBY_VISION,
)

private val SOFTWARE_DECODER_PREFIXES = listOf("omx.google.", "c2.android.", "c2.google.", "omx.ffmpeg.")

/**
 * Android's software decoders are left out only when a hardware decoder supports this exact format
 * (profile, level, size), so a stream beyond the hardware still plays in software.
 */
internal fun <T> hardwareFirstVideoDecoders(
    mimeType: String?,
    decoders: List<T>,
    isSoftware: (T) -> Boolean,
    supportsFormat: (T) -> Boolean,
): List<T> {
    if (mimeType !in HARDWARE_FIRST_VIDEO_TYPES) return decoders
    val hardware = decoders.filterNot(isSoftware)
    return if (hardware.any(supportsFormat)) hardware else decoders
}

internal fun hardwareFirstVideoDecoders(format: Format, decoders: List<MediaCodecInfo>): List<MediaCodecInfo> =
    hardwareFirstVideoDecoders(format.sampleMimeType, decoders, ::isSoftwareDecoder) {
        runCatching { it.isFormatSupported(format) }.getOrDefault(false)
    }

/** Dolby Vision profiles carried in HEVC. The others are H.264 (dvav) and AV1 (dav1). */
private val HEVC_DOLBY_VISION_PROFILES = setOf(
    CodecProfileLevel.DolbyVisionProfileDvheDer,
    CodecProfileLevel.DolbyVisionProfileDvheDen,
    CodecProfileLevel.DolbyVisionProfileDvheDtr,
    CodecProfileLevel.DolbyVisionProfileDvheStn,
    CodecProfileLevel.DolbyVisionProfileDvheDth,
    CodecProfileLevel.DolbyVisionProfileDvheDtb,
    CodecProfileLevel.DolbyVisionProfileDvheSt,
)

internal fun isDolbyVisionProfile7(format: Format): Boolean =
    format.sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION &&
        Regex("dv(he|h1)\\.07(\\..*)?").matches(format.codecs.orEmpty())

/** Whether a Dolby Vision decoder with these [profiles] decodes HEVC; one that lists none may. */
internal fun canDecodeHevcDolbyVision(profiles: Collection<Int>): Boolean =
    profiles.isEmpty() || profiles.any { it in HEVC_DOLBY_VISION_PROFILES }

/**
 * Decoders for a Dolby Vision profile 7 video, which no decoder of the Google TV Streamer takes. On
 * a Dolby Vision TV Media3 then keeps the platform's order, where the Streamer's first one is for
 * H.264: it starts, never shows a frame and fails. [hevc] decoders play the video's HDR10 base
 * layer instead, when [playBaseLayer] asks for it or the first decoder can't decode HEVC at all.
 */
internal fun <T> dolbyVision7Decoders(
    decoders: List<T>,
    hevc: () -> List<T>,
    playBaseLayer: Boolean,
    supportsFormat: (T) -> Boolean,
    canDecodeHevc: (T) -> Boolean,
): List<T> {
    if (!playBaseLayer) {
        if (decoders.any(supportsFormat)) return decoders
        val first = decoders.firstOrNull()
        if (first != null && canDecodeHevc(first)) return decoders
    }
    return (hevc() + decoders).distinct()
}

/**
 * Whether this box's Dolby Vision decoders take profile 7, or may: one lists the profile, or the
 * first one lists other HEVC profiles only, as some boxes that do decode it do.
 */
internal fun canDecodeDolbyVision7(): Boolean = runCatching {
    canDecodeDolbyVision7(
        MediaCodecSelector.DEFAULT.getDecoderInfos(MimeTypes.VIDEO_DOLBY_VISION, false, false)
            .map { info -> info.profileLevels.map { it.profile } }
    )
}.getOrDefault(true)

/** [decoders]: the profiles each Dolby Vision decoder lists, in the platform's order. */
internal fun canDecodeDolbyVision7(decoders: List<List<Int>>): Boolean =
    decoders.any { CodecProfileLevel.DolbyVisionProfileDvheDtb in it } ||
        decoders.firstOrNull()?.let(::canDecodeHevcDolbyVision) == true

/**
 * The platform video renderer with [hardwareFirstVideoDecoders] and [dolbyVision7Decoders] applied
 * to its decoder list. [mapDv7ToHevc] is repeated here because the platform's own setting has no
 * effect on a Dolby Vision TV.
 */
internal class HardwareFirstVideoRenderer(
    builder: MediaCodecVideoRenderer.Builder,
    private val mapDv7ToHevc: Boolean = false,
) : MediaCodecVideoRenderer(builder) {
    override fun getDecoderInfos(
        mediaCodecSelector: MediaCodecSelector,
        format: Format,
        requiresSecureDecoder: Boolean,
    ): List<MediaCodecInfo> {
        val decoders = hardwareFirstVideoDecoders(format, super.getDecoderInfos(mediaCodecSelector, format, requiresSecureDecoder))
        if (!isDolbyVisionProfile7(format)) return decoders
        return dolbyVision7Decoders(
            decoders = decoders,
            hevc = {
                val all = mediaCodecSelector.getDecoderInfos(MimeTypes.VIDEO_H265, requiresSecureDecoder, false)
                all.filterNot(::isSoftwareDecoder).ifEmpty { all }
            },
            playBaseLayer = mapDv7ToHevc,
            supportsFormat = { runCatching { it.isFormatSupported(format) }.getOrDefault(false) },
            canDecodeHevc = { info ->
                info.codecMimeType != MimeTypes.VIDEO_DOLBY_VISION ||
                    canDecodeHevcDolbyVision(info.profileLevels.map { it.profile })
            },
        )
    }
}

internal fun isSoftwareDecoder(info: MediaCodecInfo): Boolean =
    info.softwareOnly || hasSoftwareDecoderName(info.name)

internal fun hasSoftwareDecoderName(name: String): Boolean {
    val lower = name.lowercase()
    return SOFTWARE_DECODER_PREFIXES.any { lower.startsWith(it) }
}

/** Extension decoders (libdav1d, libgav1, ffmpeg) are always software. */
internal fun isSoftwareVideoDecoderName(name: String, softwareOnlyNames: Set<String>): Boolean {
    val lower = name.lowercase()
    return lower in softwareOnlyNames ||
        hasSoftwareDecoderName(lower) ||
        lower.startsWith("lib") ||
        lower.startsWith("ffmpeg")
}

internal val softwareOnlyDecoderNames: Set<String> by lazy {
    if (Build.VERSION.SDK_INT < 29) return@lazy emptySet()
    runCatching {
        MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
            .filter { !it.isEncoder && it.isSoftwareOnly }
            .map { it.name.lowercase() }
            .toSet()
    }.getOrDefault(emptySet())
}
