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
 * Picks the subtitle to translate from [candidates]: English first, since that is what models
 * translate best, then the film's spoken language ([audioLanguage]), then the first other one.
 */
private fun <T> pickTranslationSource(candidates: List<T>, audioLanguage: String?, language: (T) -> String?): T? {
    val spoken = audioLanguage?.takeIf { it.isNotBlank() && !it.equals("und", ignoreCase = true) }
    return candidates.firstOrNull { PlayerSubtitleUtils.matchesLanguageCode(language(it), "en") }
        ?: spoken?.let { wanted -> candidates.firstOrNull { PlayerSubtitleUtils.matchesLanguageCode(language(it), wanted) } }
        ?: candidates.firstOrNull()
}

/**
 * The one embedded track that "always translate with AI" shows in [targetLanguage]: a full
 * (non-forced) text track in another language, English first (see [pickTranslationSource]).
 */
internal fun embeddedSubtitleForTranslation(
    subtitleTracks: List<TrackInfo>,
    targetLanguage: String,
    audioLanguage: String? = null,
): TrackInfo? = pickTranslationSource(
    subtitleTracks.filter { track ->
        track.isTranslatableTextSubtitle() && !track.isForced &&
            !PlayerSubtitleUtils.matchesLanguageCode(track.language, targetLanguage)
    },
    audioLanguage,
) { it.language }

/**
 * The add-on subtitle to translate: English first, then any other language that is not already
 * the target (see [pickTranslationSource]).
 */
internal fun addonSubtitleForTranslation(
    subtitles: List<com.nuvio.tv.domain.model.Subtitle>,
    targetLanguage: String,
    audioLanguage: String? = null,
): com.nuvio.tv.domain.model.Subtitle? = pickTranslationSource(
    subtitles.filter { subtitle ->
        !addonSubtitleIsForced(subtitle) && !PlayerSubtitleUtils.matchesLanguageCode(subtitle.lang, targetLanguage)
    },
    audioLanguage,
) { it.lang }

/** The subtitle AI translation reads: one of the video's own tracks, or an add-on subtitle. */
internal sealed interface TranslationSource {
    data class Embedded(val track: TrackInfo) : TranslationSource
    data class Addon(val subtitle: com.nuvio.tv.domain.model.Subtitle) : TranslationSource
}

internal val TranslationSource.language: String?
    get() = when (this) {
        is TranslationSource.Embedded -> track.language
        is TranslationSource.Addon -> subtitle.lang
    }

internal fun TranslationSource.isEnglish(): Boolean = PlayerSubtitleUtils.matchesLanguageCode(language, "en")

/**
 * The subtitle to translate: the video's own tracks first, since they are always in sync (English,
 * then the spoken language, then any other), then add-on subtitles in the same order.
 */
internal fun subtitleSourceForTranslation(
    subtitleTracks: List<TrackInfo>,
    addonSubtitles: List<com.nuvio.tv.domain.model.Subtitle>,
    targetLanguage: String,
    audioLanguage: String? = null,
): TranslationSource? =
    embeddedSubtitleForTranslation(subtitleTracks, targetLanguage, audioLanguage)?.let(TranslationSource::Embedded)
        ?: addonSubtitleForTranslation(addonSubtitles, targetLanguage, audioLanguage)?.let(TranslationSource::Addon)

/** What "always translate with AI" shows. */
internal sealed interface AutoSubtitleChoice {
    /** One of the video's own tracks: shown as it is when in the target language, else translated. */
    data class Embedded(val track: TrackInfo) : AutoSubtitleChoice
    /** An add-on subtitle: shown as it is when in the target language, else translated. */
    data class Addon(val subtitle: com.nuvio.tv.domain.model.Subtitle) : AutoSubtitleChoice
    /** No subtitle at all: subtitles are made from the audio. */
    data object Transcribe : AutoSubtitleChoice
}

/** The video's own full track in [targetLanguage], text or image, which needs no translation. */
internal fun embeddedSubtitleInTarget(subtitleTracks: List<TrackInfo>, targetLanguage: String): TrackInfo? =
    subtitleTracks.firstOrNull { !it.isForced && PlayerSubtitleUtils.matchesLanguageCode(it.language, targetLanguage) }

internal fun addonSubtitleInTarget(
    subtitles: List<com.nuvio.tv.domain.model.Subtitle>,
    targetLanguage: String,
): com.nuvio.tv.domain.model.Subtitle? =
    subtitles.firstOrNull { !addonSubtitleIsForced(it) && PlayerSubtitleUtils.matchesLanguageCode(it.lang, targetLanguage) }

/** True when the choice depends on the add-on list, because the video has no track to use. */
internal fun autoSubtitleChoiceNeedsAddons(subtitleTracks: List<TrackInfo>, targetLanguage: String): Boolean =
    embeddedSubtitleInTarget(subtitleTracks, targetLanguage) == null &&
        embeddedSubtitleForTranslation(subtitleTracks, targetLanguage) == null

/**
 * The order "always translate with AI" follows: the video's own track in the target language, then
 * its English track, then its other tracks (translated), then an add-on in the target language,
 * then an English add-on, then other add-ons (translated). Without any subtitle, the audio is
 * transcribed when [canTranscribe].
 */
internal fun autoSubtitleChoice(
    subtitleTracks: List<TrackInfo>,
    addonSubtitles: List<com.nuvio.tv.domain.model.Subtitle>,
    targetLanguage: String,
    audioLanguage: String? = null,
    canTranscribe: Boolean = true,
): AutoSubtitleChoice? {
    embeddedSubtitleInTarget(subtitleTracks, targetLanguage)?.let { return AutoSubtitleChoice.Embedded(it) }
    embeddedSubtitleForTranslation(subtitleTracks, targetLanguage, audioLanguage)?.let { return AutoSubtitleChoice.Embedded(it) }
    addonSubtitleInTarget(addonSubtitles, targetLanguage)?.let { return AutoSubtitleChoice.Addon(it) }
    addonSubtitleForTranslation(addonSubtitles, targetLanguage, audioLanguage)?.let { return AutoSubtitleChoice.Addon(it) }
    return if (canTranscribe) AutoSubtitleChoice.Transcribe else null
}

/**
 * Whether a subtitle remembered from earlier viewing is kept under [choice]: only when it is the
 * same kind of subtitle in the same language (a particular English add-on file, say), so the
 * order above still applies. Turning subtitles off is always kept.
 */
internal fun rememberedSubtitleMatchesChoice(
    remembered: PlayerRuntimeController.RememberedSubtitleSelection,
    choice: AutoSubtitleChoice?,
): Boolean = when (remembered) {
    PlayerRuntimeController.RememberedSubtitleSelection.Disabled -> true
    is PlayerRuntimeController.RememberedSubtitleSelection.Internal ->
        choice == null || (choice is AutoSubtitleChoice.Embedded &&
            PlayerSubtitleUtils.matchesLanguageCode(choice.track.language, remembered.track.language.orEmpty()))
    is PlayerRuntimeController.RememberedSubtitleSelection.Addon ->
        choice == null || (choice is AutoSubtitleChoice.Addon &&
            PlayerSubtitleUtils.matchesLanguageCode(choice.subtitle.lang, remembered.language))
}

/** Spoken language of the audio track being played, for choosing the subtitle to translate. */
internal fun PlayerRuntimeController.currentAudioLanguageForTranslation(): String? =
    selectedAudioTrackForSubtitleMatching(_uiState.value)?.language

/** The language "always translate with AI" translates into, or null when it does not apply. */
internal fun PlayerRuntimeController.preferredEmbeddedTranslationTarget(): String? {
    if (!SubtitleTranslationService.shouldPreferEmbeddedSubtitles()) return null
    return SubtitleTranslationService.translationTargetLanguage() ?: subtitleLanguageTargets().firstOrNull()
}

/** What to do with a subtitle remembered from earlier viewing while AI translation is on. */
internal enum class RememberedSubtitleDecision { Keep, Wait, Replace }

/**
 * Whether a subtitle remembered from an earlier episode or movie gives way to the automatic choice
 * (see [autoSubtitleChoice] and [rememberedSubtitleMatchesChoice]). Until the video's tracks (and,
 * when it has none to use, the add-ons) are known, the restore waits, so a subtitle is not shown
 * and then swapped.
 */
internal fun PlayerRuntimeController.rememberedSubtitleDecision(
    remembered: PlayerRuntimeController.RememberedSubtitleSelection,
    subtitleTracks: List<TrackInfo>,
): RememberedSubtitleDecision {
    val target = preferredEmbeddedTranslationTarget() ?: return RememberedSubtitleDecision.Keep
    if (remembered == PlayerRuntimeController.RememberedSubtitleSelection.Disabled) return RememberedSubtitleDecision.Keep
    if (!hasScannedTextTracksOnce) return RememberedSubtitleDecision.Wait
    val state = _uiState.value
    if (state.isLoadingAddonSubtitles && autoSubtitleChoiceNeedsAddons(subtitleTracks, target)) {
        return RememberedSubtitleDecision.Wait
    }
    val choice = autoSubtitleChoice(
        subtitleTracks, state.addonSubtitles, target, currentAudioLanguageForTranslation(), canTranscribe = canTranscribeAudio()
    )
    return if (rememberedSubtitleMatchesChoice(remembered, choice)) RememberedSubtitleDecision.Keep else RememberedSubtitleDecision.Replace
}

/**
 * With "always translate with AI" on, shows the subtitle [autoSubtitleChoice] picks: the video's
 * own track in the target language as it is, else a translated one, else add-ons, else subtitles
 * made from the audio.
 * Returns true when it handled the selection, which includes waiting for the add-on list when the
 * video has no track to use.
 */
internal fun PlayerRuntimeController.tryAutoSelectEmbeddedSubtitleForTranslation(
    subtitleTracks: List<TrackInfo>,
    targetLanguage: String?
): Boolean {
    if (targetLanguage == null || !hasScannedTextTracksOnce) return false
    if (!SubtitleTranslationService.shouldPreferEmbeddedSubtitles()) return false
    val state = _uiState.value
    if (state.isLoadingAddonSubtitles && autoSubtitleChoiceNeedsAddons(subtitleTracks, targetLanguage)) return true
    val firstNotice = subtitleTranslationNoSourceNoticeStreamUrl != currentStreamUrl
    val choice = autoSubtitleChoice(
        subtitleTracks, state.addonSubtitles, targetLanguage, currentAudioLanguageForTranslation(), canTranscribe = canTranscribeAudio()
    )
    if (choice !is AutoSubtitleChoice.Transcribe) stopAudioTranscription()
    when (choice) {
        null -> {
            if (firstNotice) {
                subtitleTranslationNoSourceNoticeStreamUrl = currentStreamUrl
                showSubtitleTranslationNotice(context.getString(R.string.subtitle_ai_translation_no_source))
            }
            return false
        }
        AutoSubtitleChoice.Transcribe -> {
            autoSubtitleSelected = true
            if (!isAudioTranscriptionActive()) {
                Log.d(PlayerRuntimeController.TAG, "AUTO_SUB no subtitles: transcribing the audio into $targetLanguage")
                startAudioTranscription(targetLanguage)
                if (firstNotice) {
                    subtitleTranslationNoSourceNoticeStreamUrl = currentStreamUrl
                    showSubtitleTranslationNotice(context.getString(R.string.subtitle_ai_transcription_started))
                }
            }
            return true
        }
        is AutoSubtitleChoice.Addon -> {
            val addonSource = choice.subtitle
            autoSubtitleSelected = true
            if (state.selectedAddonSubtitle?.url != addonSource.url) {
                Log.d(PlayerRuntimeController.TAG, "AUTO_SUB pick addon ${addonSource.lang} from ${addonSource.addonName} for AI translation")
                selectAddonSubtitle(addonSource)
            }
            // The notice explains add-on subtitles, which the video has no track to replace.
            val translated = !PlayerSubtitleUtils.matchesLanguageCode(addonSource.lang, targetLanguage)
            if (firstNotice && translated) {
                subtitleTranslationNoSourceNoticeStreamUrl = currentStreamUrl
                showSubtitleTranslationNotice(context.getString(R.string.subtitle_ai_translation_addon_source, addonSource.addonName))
            }
            return true
        }
        is AutoSubtitleChoice.Embedded -> {
            val track = choice.track
            autoSubtitleSelected = true
            if (state.selectedSubtitleTrackIndex != track.index || state.selectedAddonSubtitle != null) {
                Log.d(PlayerRuntimeController.TAG, "AUTO_SUB pick embedded index=${track.index} lang=${track.language} for AI translation")
                selectSubtitleTrack(track.index)
                _uiState.update { it.copy(selectedSubtitleTrackIndex = track.index, selectedAddonSubtitle = null) }
            }
            return true
        }
    }
}

/**
 * Videos whose subtitle the user picked during this app session. A stream restart reloads the
 * saved choice from storage; for these videos it is kept even when it is in the translation's
 * target language, instead of giving way to the AI translation.
 */
internal object SessionSubtitleChoices {
    private val videos = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun mark(videoKey: String?) {
        videoKey?.takeIf { it.isNotBlank() }?.let(videos::add)
    }

    operator fun contains(videoKey: String?): Boolean = videoKey != null && videoKey in videos
}

internal val PlayerRuntimeController.subtitleChoiceVideoKey: String?
    get() = currentVideoId ?: contentId
