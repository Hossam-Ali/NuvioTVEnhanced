package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.core.subtitles.SubtitleTranslationService
import com.nuvio.tv.core.subtitles.SubtitleTranslator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MPV_SUBTITLE_POLL_MS = 100L
private const val MPV_IDLE_POLL_MS = 500L
private const val MPV_LATE_TRANSLATION_HOLD_MS = 1_500L
private const val MPV_HIDE_REAPPLY_TICKS = 10

/** The translator for the subtitle mpv is showing, or null when it should be shown as it is. */
private fun PlayerRuntimeController.mpvSubtitleTranslator(): SubtitleTranslator? {
    if (!isUsingMpvEngine() || !SubtitleTranslationService.isConfigured()) return null
    val state = _uiState.value
    val addon = state.selectedAddonSubtitle
    val track = state.subtitleTracks.firstOrNull { it.isSelected }
    val language = when {
        addon != null -> addon.lang
        track != null && track.isTranslatableTextSubtitle() -> track.language
        else -> return null
    }
    return SubtitleTranslationService.activeTranslator(trackLanguage = language)
}

/**
 * mpv draws its own subtitles, so for AI translation its drawing is hidden and the translated
 * line is shown by the player screen instead. mpv only exposes the line on screen, so embedded
 * subtitles are translated line by line; an add-on subtitle is downloaded and translated ahead.
 */
internal fun PlayerRuntimeController.startMpvSubtitleTranslation() {
    mpvSubtitleTranslationJob?.cancel()
    mpvSubtitleTranslationJob = scope.launch {
        var lastText: String? = null
        var textSinceMs = 0L
        var hidden = false
        var ticks = 0
        var prefetchedAddonUrl: String? = null
        while (isActive) {
            val view = mpvView
            val translator = if (view != null) mpvSubtitleTranslator() else null
            if (view == null || translator == null) {
                if (hidden) view?.setSubtitleTextHidden(false)
                hidden = false
                lastText = null
                if (_uiState.value.mpvTranslatedSubtitle != null) _uiState.update { it.copy(mpvTranslatedSubtitle = null) }
                delay(MPV_IDLE_POLL_MS)
                continue
            }
            // Track changes turn mpv's drawing back on, so hiding is reapplied now and then.
            if (!hidden || ticks++ % MPV_HIDE_REAPPLY_TICKS == 0) {
                view.setSubtitleTextHidden(true)
                hidden = true
            }
            _uiState.value.selectedAddonSubtitle?.takeIf { it.url != prefetchedAddonUrl }?.let { addon ->
                prefetchedAddonUrl = addon.url
                launch { prefetchMpvAddonSubtitle(translator, addon) }
            }
            val now = android.os.SystemClock.uptimeMillis()
            val text = view.currentSubtitleText()?.takeIf { it.isNotBlank() }
            if (text != lastText) {
                lastText = text
                textSinceMs = now
                if (text != null && translator.cached(text) == null) {
                    SubtitleTranslationService.translate(translator, listOf(text))
                }
            }
            // A late line is held back briefly, like on ExoPlayer, before the original is shown.
            val shown = text?.let { translator.cached(it) ?: it.takeIf { now - textSinceMs >= MPV_LATE_TRANSLATION_HOLD_MS } }
            if (shown != _uiState.value.mpvTranslatedSubtitle) _uiState.update { it.copy(mpvTranslatedSubtitle = shown) }
            delay(MPV_SUBTITLE_POLL_MS)
        }
    }
}

private suspend fun PlayerRuntimeController.prefetchMpvAddonSubtitle(
    translator: SubtitleTranslator,
    addon: com.nuvio.tv.domain.model.Subtitle,
) {
    val texts = runCatching {
        val body = downloadSubtitleBody(addon.url, addon.lang, addon.headers)
        withContext(Dispatchers.Default) {
            parseSidecarTimedCuesRobust(body, addon.url).cues
                .sortedBy { it.startTimeUs }
                .flatMap { entry -> entry.cues.mapNotNull { cue -> cue.text?.toString()?.takeIf(String::isNotBlank) } }
                .distinct()
        }
    }.getOrNull() ?: return
    texts.filter { translator.cached(it) == null }
        .chunked(40)
        .forEach { group -> SubtitleTranslationService.translate(translator, group) }
}
