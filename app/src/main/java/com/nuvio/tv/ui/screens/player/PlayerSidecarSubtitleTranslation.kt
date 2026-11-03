@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.nuvio.tv.ui.screens.player

import androidx.media3.common.text.Cue
import androidx.media3.extractor.text.CuesWithTiming
import com.nuvio.tv.core.subtitles.SubtitleTranslationService
import com.nuvio.tv.core.subtitles.SubtitleTranslator

/** Lines sent per translation request while an add-on subtitle is translated ahead. */
private const val SIDECAR_PREFETCH_GROUP = 40
/** The first request is small, so the lines on screen come back quickly after a switch. */
private const val SIDECAR_FIRST_GROUP = 8

/** The translator for the add-on subtitle being shown, or null when it is shown as it is. */
private fun PlayerRuntimeController.sidecarTranslator(): SubtitleTranslator? {
    if (isUsingMpvEngine() || activeSidecarSubtitleKey == null) return null
    return SubtitleTranslationService.activeTranslator(trackLanguage = activeSidecarSubtitleLanguage)
}

/** The text of [cues] as it is shown, which is also the key its translation is cached under. */
private fun PlayerRuntimeController.displayedTexts(cues: List<Cue>): List<String> {
    val stripSdh = currentPlayerSettingsForReport.subtitleStyle.stripSdh
    val sanitized = cues.map { SubtitleMojibakeSanitizer.sanitizeCue(it) }
    val filtered = if (stripSdh) SubtitleSdhFilter.filterCues(sanitized) else sanitized
    return PlayerSubtitleUtils.mergeOverlappingCues(filtered)
        .mapNotNull { cue -> cue.text?.toString()?.takeIf { cue.bitmap == null && it.isNotBlank() } }
}

/**
 * An add-on subtitle arrives whole, so it is translated ahead from the current position onwards,
 * then the part already played. Lines are usually translated long before they are shown.
 */
internal fun PlayerRuntimeController.prefetchSidecarTranslations(cues: List<CuesWithTiming>) {
    val translator = sidecarTranslator() ?: return
    val positionUs = (_exoPlayer?.currentPosition ?: 0L).coerceAtLeast(0L) * 1_000L
    val ordered = cues.sortedBy { it.startTimeUs }
    val upcoming = ordered.indexOfFirst { it.endTimeUs >= positionUs }.let { if (it < 0) ordered.size else it }
    val texts = (ordered.drop(upcoming) + ordered.take(upcoming))
        .flatMap { displayedTexts(it.cues) }
        .distinct()
        .filter { translator.cached(it) == null }
    if (texts.isEmpty()) return
    SubtitleTranslationService.translate(translator, texts.take(SIDECAR_FIRST_GROUP))
    texts.drop(SIDECAR_FIRST_GROUP)
        .chunked(SIDECAR_PREFETCH_GROUP)
        .forEach { group -> SubtitleTranslationService.translate(translator, group) }
}

/** Shows translations of [cues] that are ready; missing lines are asked for and shown as they are. */
internal fun PlayerRuntimeController.translateSidecarCues(cues: List<Cue>): List<Cue> {
    val translator = sidecarTranslator() ?: return cues
    val missing = mutableListOf<String>()
    val translated = cues.map { cue ->
        val text = cue.text?.toString()?.takeIf { cue.bitmap == null && it.isNotBlank() } ?: return@map cue
        val translation = translator.cached(text)
        if (translation == null) {
            missing += text
            cue
        } else {
            PlayerSubtitleRtlFix.fixCueText(cue.buildUpon().setText(translation).build(), isBuiltInSubtitle = false)
        }
    }
    if (missing.isNotEmpty()) SubtitleTranslationService.translate(translator, missing)
    return translated
}
