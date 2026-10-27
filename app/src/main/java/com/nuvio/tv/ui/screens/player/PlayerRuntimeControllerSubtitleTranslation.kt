@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.nuvio.tv.ui.screens.player

import android.util.Log
import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.media3.extractor.ExtractorsFactory
import com.nuvio.tv.R
import com.nuvio.tv.core.subtitles.SubtitleTranslationEvent
import com.nuvio.tv.core.subtitles.SubtitleTranslationException
import com.nuvio.tv.core.subtitles.SubtitleTranslationService
import com.nuvio.tv.core.subtitles.languageNameForPrompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Starts listening for translation results (redraw) and problems (notices) for this player. */
internal fun PlayerRuntimeController.observeSubtitleTranslation() {
    SubtitleTranslationService.attach(context)
    startMpvSubtitleTranslation()
    scope.launch {
        SubtitleTranslationService.events.collect { event ->
            withContext(Dispatchers.Main.immediate) {
                when (event) {
                    SubtitleTranslationEvent.Translated -> {
                        subtitleTranslationBridge.refresh()
                        // The add-on path redraws only when its cues change; new translations count.
                        lastSidecarCueSignature = null
                    }
                    is SubtitleTranslationEvent.Started -> showSubtitleTranslationNotice(
                        context.getString(
                            R.string.subtitle_ai_translation_active,
                            languageNameForPrompt(event.targetLanguage),
                            event.provider.label
                        )
                    )
                    is SubtitleTranslationEvent.Failed -> showSubtitleTranslationNotice(
                        when (event.code) {
                            SubtitleTranslationException.Code.Auth ->
                                context.getString(R.string.subtitle_ai_translation_error_key, event.provider.label)
                            SubtitleTranslationException.Code.Model ->
                                context.getString(R.string.subtitle_ai_translation_error_model, event.model)
                            SubtitleTranslationException.Code.Rate ->
                                context.getString(R.string.subtitle_ai_translation_error_rate, event.provider.label)
                            else -> context.getString(R.string.subtitle_ai_translation_error_generic)
                        }
                    )
                }
            }
        }
    }
}

private fun PlayerRuntimeController.showSubtitleTranslationNotice(message: String) {
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}

/** Refreshes the player's preferred language for the "preferred" target, then checks the setup. */
internal fun PlayerRuntimeController.isSubtitleTranslationConfigured(): Boolean {
    SubtitleTranslationService.attach(context)
    SubtitleTranslationService.preferredSubtitleLanguage = subtitleLanguageTargets().firstOrNull()
    return SubtitleTranslationService.isConfigured()
}

internal fun PlayerRuntimeController.subtitleTranslationExtractorsFactory(delegate: ExtractorsFactory): ExtractorsFactory =
    if (isSubtitleTranslationConfigured()) SubtitleTranslationExtractorsFactory(delegate) else delegate

/** Tracks the language of the subtitle being shown, so only that track is translated. */
internal fun PlayerRuntimeController.onSubtitleTranslationTracksChanged(tracks: Tracks) {
    if (!SubtitleTranslationService.isConfigured()) return
    val selectedLanguage = tracks.groups
        .firstOrNull { it.type == C.TRACK_TYPE_TEXT && it.isSelected }
        ?.let { group -> (0 until group.length).firstOrNull(group::isTrackSelected)?.let { group.getTrackFormat(it).language } }
    SubtitleTranslationService.preferredSubtitleLanguage = subtitleLanguageTargets().firstOrNull()
    if (SubtitleTranslationService.selectedTrackLanguage == selectedLanguage) return
    SubtitleTranslationService.selectedTrackLanguage = selectedLanguage
    EmbeddedSubtitleTextTap.replaySelection()
    subtitleTranslationBridge.refresh()
}

private val bitmapSubtitleCodecHints = listOf("pgs", "vobsub", "dvd", "dvb", "bitmap")

/** Text (not image) subtitle tracks that AI translation can read. */
internal fun TrackInfo.isTranslatableTextSubtitle(): Boolean {
    val codec = codec.orEmpty().lowercase()
    return bitmapSubtitleCodecHints.none { codec.contains(it) }
}

/**
 * The one embedded track that "always translate with AI" shows in [targetLanguage]: the subtitle
 * in the film's own spoken language ([audioLanguage], so a Turkish film uses its Turkish
 * subtitle), then English, then the first other full (non-forced) text track.
 */
internal fun embeddedSubtitleForTranslation(
    subtitleTracks: List<TrackInfo>,
    targetLanguage: String,
    audioLanguage: String? = null,
): TrackInfo? {
    val sourceTracks = subtitleTracks.filter { track ->
        track.isTranslatableTextSubtitle() && !track.isForced &&
            !PlayerSubtitleUtils.matchesLanguageCode(track.language, targetLanguage)
    }
    val spoken = audioLanguage?.takeIf { it.isNotBlank() && !it.equals("und", ignoreCase = true) }
    return spoken?.let { language -> sourceTracks.firstOrNull { PlayerSubtitleUtils.matchesLanguageCode(it.language, language) } }
        ?: sourceTracks.firstOrNull { PlayerSubtitleUtils.matchesLanguageCode(it.language, "en") }
        ?: sourceTracks.firstOrNull()
}

/**
 * The add-on subtitle to translate when a video has no embedded text subtitle: one in the film's
 * spoken language, then English. Add-on subtitles in other languages are not picked on their own.
 */
internal fun addonSubtitleForTranslation(
    subtitles: List<com.nuvio.tv.domain.model.Subtitle>,
    targetLanguage: String,
    audioLanguage: String? = null,
): com.nuvio.tv.domain.model.Subtitle? {
    val candidates = subtitles.filter { subtitle ->
        !addonSubtitleIsForced(subtitle) && !PlayerSubtitleUtils.matchesLanguageCode(subtitle.lang, targetLanguage)
    }
    val spoken = audioLanguage?.takeIf { it.isNotBlank() && !it.equals("und", ignoreCase = true) }
    return spoken?.let { language -> candidates.firstOrNull { PlayerSubtitleUtils.matchesLanguageCode(it.lang, language) } }
        ?: candidates.firstOrNull { PlayerSubtitleUtils.matchesLanguageCode(it.lang, "en") }
}

/** Spoken language of the audio track being played, for choosing the subtitle to translate. */
internal fun PlayerRuntimeController.currentAudioLanguageForTranslation(): String? =
    selectedAudioTrackForSubtitleMatching(_uiState.value)?.language

/** The language "always translate with AI" translates into, or null when it does not apply. */
internal fun PlayerRuntimeController.preferredEmbeddedTranslationTarget(): String? {
    if (!SubtitleTranslationService.shouldPreferEmbeddedSubtitles()) return null
    return SubtitleTranslationService.translationTargetLanguage() ?: subtitleLanguageTargets().firstOrNull()
}

/**
 * True when a subtitle remembered from an earlier episode or movie (an add-on, or a track in the
 * target language) should give way to the AI-translated embedded track. Before the embedded
 * tracks are known the restore waits, so an add-on is not picked first.
 */
internal fun PlayerRuntimeController.shouldReplaceRememberedSubtitleWithTranslation(
    remembered: PlayerRuntimeController.RememberedSubtitleSelection,
    subtitleTracks: List<TrackInfo>,
): Boolean {
    val target = preferredEmbeddedTranslationTarget() ?: return false
    // Only a subtitle already in the target language is swapped for the AI one: anything else
    // the user picked (an English add-on, say) is translated as it is.
    val replaceable = when (remembered) {
        is PlayerRuntimeController.RememberedSubtitleSelection.Addon ->
            PlayerSubtitleUtils.matchesLanguageCode(remembered.language, target)
        is PlayerRuntimeController.RememberedSubtitleSelection.Internal ->
            PlayerSubtitleUtils.matchesLanguageCode(remembered.track.language, target)
        PlayerRuntimeController.RememberedSubtitleSelection.Disabled -> false
    }
    if (!replaceable) return false
    val audio = currentAudioLanguageForTranslation()
    return !hasScannedTextTracksOnce ||
        embeddedSubtitleForTranslation(subtitleTracks, target, audio) != null ||
        addonSubtitleForTranslation(_uiState.value.addonSubtitles, target, audio) != null
}

/**
 * With "always translate with AI" on, picks the video's own text subtitle track and translates
 * it, even when an Arabic track or add-on subtitle exists. Only a video whose embedded text
 * tracks are all in the target language (or that has none) keeps the normal selection; then a
 * one-time notice says why add-on subtitles are shown.
 * Returns true when it handled the selection.
 */
internal fun PlayerRuntimeController.tryAutoSelectEmbeddedSubtitleForTranslation(
    subtitleTracks: List<TrackInfo>,
    targetLanguage: String?
): Boolean {
    if (targetLanguage == null || !hasScannedTextTracksOnce) return false
    if (!SubtitleTranslationService.shouldPreferEmbeddedSubtitles()) return false
    val audio = currentAudioLanguageForTranslation()
    val source = embeddedSubtitleForTranslation(subtitleTracks, targetLanguage, audio)
    if (source == null) {
        val state = _uiState.value
        // Without an embedded text subtitle, an add-on subtitle in the spoken language (or
        // English) is translated instead. Wait for the add-on list before deciding.
        if (state.isLoadingAddonSubtitles) return false
        val addonSource = addonSubtitleForTranslation(state.addonSubtitles, targetLanguage, audio)
        val firstNotice = subtitleTranslationNoSourceNoticeStreamUrl != currentStreamUrl
        if (firstNotice) subtitleTranslationNoSourceNoticeStreamUrl = currentStreamUrl
        if (addonSource == null) {
            if (firstNotice) showSubtitleTranslationNotice(context.getString(R.string.subtitle_ai_translation_no_source))
            return false
        }
        autoSubtitleSelected = true
        if (state.selectedAddonSubtitle?.url != addonSource.url) {
            Log.d(PlayerRuntimeController.TAG, "AUTO_SUB pick addon ${addonSource.lang} from ${addonSource.addonName} for AI translation")
            selectAddonSubtitle(addonSource)
        }
        if (firstNotice) {
            showSubtitleTranslationNotice(context.getString(R.string.subtitle_ai_translation_addon_source, addonSource.addonName))
        }
        return true
    }
    autoSubtitleSelected = true
    val state = _uiState.value
    if (state.selectedSubtitleTrackIndex != source.index || state.selectedAddonSubtitle != null) {
        Log.d(PlayerRuntimeController.TAG, "AUTO_SUB pick embedded index=${source.index} lang=${source.language} for AI translation")
        selectSubtitleTrack(source.index)
        _uiState.update { it.copy(selectedSubtitleTrackIndex = source.index, selectedAddonSubtitle = null) }
    }
    return true
}
