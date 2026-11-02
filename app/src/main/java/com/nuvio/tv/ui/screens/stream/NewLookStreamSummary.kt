package com.nuvio.tv.ui.screens.stream

import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamDebridCacheState
import java.util.Locale

/** Quality groups for the new-look source filters, best first. */
enum class StreamQualityGroup { UHD, FULL_HD, HD, SD, UNKNOWN }

/** What a source is, in plain words, read from its name, title, description and file name. */
data class StreamSummary(
    val quality: StreamQualityGroup,
    val qualityLabel: String?,
    val source: String?,
    val features: List<String>,
    val sizeBytes: Long?,
    val cached: Boolean,
    val fileName: String?
)

private val RESOLUTIONS = listOf(
    Regex("(?i)\\b(2160p|4k|uhd)\\b") to (StreamQualityGroup.UHD to "4K"),
    Regex("(?i)\\b1440p\\b") to (StreamQualityGroup.FULL_HD to "1440p"),
    Regex("(?i)\\b1080[pi]\\b") to (StreamQualityGroup.FULL_HD to "1080p"),
    Regex("(?i)\\b720p\\b") to (StreamQualityGroup.HD to "720p"),
    Regex("(?i)\\b(576p|480p|360p|sd)\\b") to (StreamQualityGroup.SD to "SD")
)

private val SOURCES = listOf(
    Regex("(?i)\\bremux\\b") to "REMUX",
    Regex("(?i)\\b(blu-?ray|bdrip|brrip|bd)\\b") to "BluRay",
    Regex("(?i)\\bweb-?dl\\b") to "WEB-DL",
    Regex("(?i)\\bweb-?rip\\b") to "WEBRip",
    Regex("(?i)\\bweb\\b") to "WEB",
    Regex("(?i)\\bhdtv\\b") to "HDTV",
    Regex("(?i)\\b(cam|hdcam|telesync|ts)\\b") to "CAM"
)

private val HDR = listOf(
    Regex("(?i)\\b(dolby[ .]?vision|dovi|dv)\\b") to "Dolby Vision",
    Regex("(?i)\\bhdr10(\\+|plus)") to "HDR10+",
    Regex("(?i)\\bhdr10\\b") to "HDR10",
    Regex("(?i)\\bhdr\\b") to "HDR"
)

private val AUDIO = listOf(
    Regex("(?i)\\batmos\\b") to "Atmos",
    Regex("(?i)\\btrue-?hd\\b") to "TrueHD",
    Regex("(?i)\\bdts-?(hd|x|ma)\\b") to "DTS-HD",
    Regex("(?i)\\bdts\\b") to "DTS",
    Regex("(?i)\\b(ddp|dd\\+|e-?ac-?3|eac3)") to "Dolby Digital+",
    Regex("(?i)\\b(dd|ac-?3)(\\b|\\d)") to "Dolby Digital",
    Regex("(?i)\\baac\\b") to "AAC"
)

private val CHANNELS = Regex("(?<![\\d.])(7\\.1|5\\.1|2\\.0)(?![\\d])")
private val CODECS = listOf(
    Regex("(?i)\\bav1\\b") to "AV1",
    Regex("(?i)\\b(hevc|x265|h\\.?265)\\b") to "HEVC"
)
private val SIZE = Regex("(?i)(\\d+(?:[.,]\\d+)?)\\s?(TB|GB|GiB|MB|MiB)\\b")

fun summarizeStream(stream: Stream): StreamSummary {
    val fileName = stream.behaviorHints?.filename?.takeIf { it.isNotBlank() }
    val text = listOfNotNull(stream.name, stream.title, stream.description, fileName, stream.quality)
        .joinToString("\n")

    val (quality, qualityLabel) = RESOLUTIONS.firstOrNull { (regex, _) -> regex.containsMatchIn(text) }?.second
        ?: qualityFromValue(stream.qualityValue)

    val hdr = HDR.firstOrNull { (regex, _) -> regex.containsMatchIn(text) }?.second
    // Dolby Digital+ already says Dolby Digital; DTS-HD already says DTS.
    val audio = AUDIO.filter { (regex, _) -> regex.containsMatchIn(text) }.map { it.second }
        .let { found ->
            found.filterNot { label ->
                (label == "Dolby Digital" && "Dolby Digital+" in found) || (label == "DTS" && "DTS-HD" in found)
            }
        }
        .take(2)
    val channels = CHANNELS.find(text)?.value
    val codec = CODECS.firstOrNull { (regex, _) -> regex.containsMatchIn(text) }?.second

    val features = buildList {
        hdr?.let(::add)
        audio.forEachIndexed { index, label -> add(if (index == 0 && channels != null) "$label $channels" else label) }
        if (audio.isEmpty() && channels != null) add(channels)
        codec?.let(::add)
    }

    return StreamSummary(
        quality = quality,
        qualityLabel = qualityLabel,
        source = SOURCES.firstOrNull { (regex, _) -> regex.containsMatchIn(text) }?.second,
        features = features,
        sizeBytes = stream.behaviorHints?.videoSize?.takeIf { it > 0 }
            ?: stream.debridCacheStatus?.cachedSize?.takeIf { it > 0 }
            ?: parseSize(text),
        cached = stream.debridCacheStatus?.state == StreamDebridCacheState.CACHED,
        fileName = fileName ?: stream.title?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()
    )
}

private fun qualityFromValue(value: Int): Pair<StreamQualityGroup, String?> = when {
    value >= 2160 -> StreamQualityGroup.UHD to "4K"
    value >= 1080 -> StreamQualityGroup.FULL_HD to "1080p"
    value >= 720 -> StreamQualityGroup.HD to "720p"
    value > 0 -> StreamQualityGroup.SD to "SD"
    else -> StreamQualityGroup.UNKNOWN to null
}

internal fun parseSize(text: String): Long? {
    val match = SIZE.find(text) ?: return null
    val number = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
    val unit = when (match.groupValues[2].uppercase(Locale.ROOT)) {
        "TB" -> 1_000_000_000_000.0
        "GB", "GIB" -> 1_000_000_000.0
        else -> 1_000_000.0
    }
    return (number * unit).toLong().takeIf { it > 0 }
}

fun formatStreamSize(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> String.format(Locale.US, "%.1f GB", bytes / 1_000_000_000.0)
    else -> String.format(Locale.US, "%d MB", bytes / 1_000_000L)
}
