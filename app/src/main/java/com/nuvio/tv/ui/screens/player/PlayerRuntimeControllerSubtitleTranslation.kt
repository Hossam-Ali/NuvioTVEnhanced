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
 * The subtitle to translate, English first wherever it is: the video's English track, then an
 * English add-on, then the video's other tracks, then other add-ons. The video's own tracks win
 * among the rest because they are always in sync.
 */
internal fun subtitleSourceForTranslation(
    subtitleTracks: List<TrackInfo>,
    addonSubtitles: List<com.nuvio.tv.domain.model.Subtitle>,
    targetLanguage: String,
    audioLanguage: String? = null,
): TranslationSource? {
    val embedded = embeddedSubtitleForTranslation(subtitleTracks, targetLanguage, audioLanguage)
        ?.let(TranslationSource::Embedded)
    if (embedded?.isEnglish() == true) return embedded
    val addon = addonSubtitleForTranslation(addonSubtitles, targetLanguage, audioLanguage)
        ?.let(TranslationSource::Addon)
    if (addon?.isEnglish() == true) return addon
    return embedded ?: addon
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
 * Whether a subtitle remembered from an earlier episode or movie gives way to the AI translation's
 * own pick (see [subtitleSourceForTranslation]): one already in the target language always does,
 * and one in another language does when an English source exists, since English translates best.
 * Until the video's tracks (and, without a built-in English track, the add-ons) are known, the
 * restore waits, so a subtitle is not shown and then swapped.
 */
internal fun PlayerRuntimeController.rememberedSubtitleDecision(
    remembered: PlayerRuntimeController.RememberedSubtitleSelection,
    subtitleTracks: List<TrackInfo>,
): RememberedSubtitleDecision {
    val target = preferredEmbeddedTranslationTarget() ?: return RememberedSubtitleDecision.Keep
    val language = when (remembered) {
        is PlayerRuntimeController.RememberedSubtitleSelection.Addon -> remembered.language
        is PlayerRuntimeController.RememberedSubtitleSelection.Internal -> remembered.track.language
        PlayerRuntimeController.RememberedSubtitleSelection.Disabled -> return RememberedSubtitleDecision.Keep
    }
    val inTarget = PlayerSubtitleUtils.matchesLanguageCode(language, target)
    // An English pick is already the best source; it is translated as it is.
    if (!inTarget && PlayerSubtitleUtils.matchesLanguageCode(language, "en")) return RememberedSubtitleDecision.Keep
    if (!hasScannedTextTracksOnce) return RememberedSubtitleDecision.Wait
    val state = _uiState.value
    val audio = currentAudioLanguageForTranslation()
    val embedded = embeddedSubtitleForTranslation(subtitleTracks, target, audio)
    if (embedded == null || !PlayerSubtitleUtils.matchesLanguageCode(embedded.language, "en")) {
        if (state.isLoadingAddonSubtitles) return RememberedSubtitleDecision.Wait
    }
    val source = subtitleSourceForTranslation(subtitleTracks, state.addonSubtitles, target, audio)
    val replace = if (inTarget) source != null else source?.isEnglish() == true
    return if (replace) RememberedSubtitleDecision.Replace else RememberedSubtitleDecision.Keep
}

/**
 * With "always translate with AI" on, picks the subtitle to translate (English first, see
 * [subtitleSourceForTranslation]) even when an Arabic track or add-on subtitle exists. Only when
 * every subtitle is already in the target language (or there is none) does the normal selection
 * apply; then a one-time notice says why.
 * Returns true when it handled the selection, which includes waiting for the add-on list while it
 * may still bring an English subtitle.
 */
internal fun PlayerRuntimeController.tryAutoSelectEmbeddedSubtitleForTranslation(
    subtitleTracks: List<TrackInfo>,
    targetLanguage: String?
): Boolean {
    if (targetLanguage == null || !hasScannedTextTracksOnce) return false
    if (!SubtitleTranslationService.shouldPreferEmbeddedSubtitles()) return false
    val audio = currentAudioLanguageForTranslation()
    val state = _uiState.value
    val embedded = embeddedSubtitleForTranslation(subtitleTracks, targetLanguage, audio)
    if (state.isLoadingAddonSubtitles && (embedded == null || !PlayerSubtitleUtils.matchesLanguageCode(embedded.language, "en"))) {
        return true
    }
    val firstNotice = subtitleTranslationNoSourceNoticeStreamUrl != currentStreamUrl
    when (val source = subtitleSourceForTranslation(subtitleTracks, state.addonSubtitles, targetLanguage, audio)) {
        null -> {
            if (firstNotice) {
                subtitleTranslationNoSourceNoticeStreamUrl = currentStreamUrl
                showSubtitleTranslationNotice(context.getString(R.string.subtitle_ai_translation_no_source))
            }
            return false
        }
        is TranslationSource.Addon -> {
            val addonSource = source.subtitle
            autoSubtitleSelected = true
            if (state.selectedAddonSubtitle?.url != addonSource.url) {
                Log.d(PlayerRuntimeController.TAG, "AUTO_SUB pick addon ${addonSource.lang} from ${addonSource.addonName} for AI translation")
                selectAddonSubtitle(addonSource)
            }
            // The notice explains add-on subtitles only when the video has no text track to use.
            if (firstNotice && embedded == null) {
                subtitleTranslationNoSourceNoticeStreamUrl = currentStreamUrl
                showSubtitleTranslationNotice(context.getString(R.string.subtitle_ai_translation_addon_source, addonSource.addonName))
            }
            return true
        }
        is TranslationSource.Embedded -> {
            val track = source.track
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
