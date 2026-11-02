@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.nuvio.tv.ui.screens.player

import android.util.Log
import android.widget.Toast
import androidx.media3.common.text.Cue
import androidx.media3.extractor.text.CuesWithTiming
import com.nuvio.tv.R
import com.nuvio.tv.core.subtitles.AudioTranscriptionClient
import com.nuvio.tv.core.subtitles.SubtitleTranslationException
import com.nuvio.tv.core.subtitles.SubtitleTranslationService
import com.nuvio.tv.core.subtitles.TRANSCRIPTION_MODEL
import com.nuvio.tv.core.subtitles.TranscribedLine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

private const val TRANSCRIPTION_KEY_PREFIX = "transcription:"
/**
 * Audio close to the playback position goes in short clips sent at once, so subtitles appear a
 * few seconds after a start or a seek; audio further ahead goes in longer clips, a few at a time.
 */
private const val NEAR_WINDOW_MS = 30_000L
private const val NEAR_CLIP_MS = 8_000L
private const val CLIP_MS = 30_000L
/** A clip ends at the quietest moment of its last few seconds. */
private const val CUT_SEARCH_MS = 5_000L
private const val NEAR_CUT_SEARCH_MS = 2_500L
/** How far ahead of playback subtitles are made. */
private const val LEAD_MS = 150_000L
private const val PARALLEL_REQUESTS = 3
private const val MAX_FAILURES_IN_A_ROW = 4
private const val RENDER_INTERVAL_MS = 100L
private const val DISK_CACHE_DIRECTORY = "subtitle-transcriptions"
private const val CACHE_VERSION = 2

/**
 * What has been made from one video's audio: the lines, and the stretches of the video they
 * cover (including ones still being transcribed), kept for the app session and on disk.
 */
internal class AudioTranscriptionSession(private val file: File?) {
    private val lock = Any()
    private val lines = mutableListOf<TranscribedLine>()
    private val done = mutableListOf<LongRange>()
    private val pending = mutableListOf<LongRange>()
    /** Pending clips near playback, which come back within seconds. */
    private val nearPending = mutableListOf<LongRange>()

    init {
        file?.takeIf { it.exists() }?.let { restore(it) }
    }

    fun lines(): List<TranscribedLine> = synchronized(lock) { lines.toList() }

    /** Where the covered stretch around [positionMs] ends, or [positionMs] when it is not covered. */
    fun coveredEnd(positionMs: Long): Long = synchronized(lock) {
        var end = positionMs
        var grew = true
        val ranges = done + pending
        while (grew) {
            grew = false
            for (range in ranges) {
                if (range.first <= end + 500 && range.last > end) {
                    end = range.last
                    grew = true
                }
            }
        }
        end
    }

    fun markPending(startMs: Long, endMs: Long, near: Boolean = false): LongRange = synchronized(lock) {
        (startMs..endMs).also { pending += it; if (near) nearPending += it }
    }

    /**
     * True when [positionMs] has no subtitles yet and only a slow clip (made far ahead) covers it,
     * as after a seek into it: a short clip from there is made too, so they come in time.
     */
    fun needsNearClip(positionMs: Long): Boolean = synchronized(lock) {
        fun LongRange.covers() = first <= positionMs + 500 && last > positionMs + 1_000
        done.none { it.covers() } && nearPending.none { it.covers() } && pending.any { it.covers() }
    }

    fun complete(range: LongRange, clipLines: List<TranscribedLine>) = synchronized(lock) {
        pending.remove(range)
        nearPending.remove(range)
        done += range
        lines.removeAll { it.startMs >= range.first && it.startMs < range.last }
        lines += clipLines
        lines.sortBy { it.startMs }
        save()
    }

    fun fail(range: LongRange) = synchronized(lock) {
        pending.remove(range)
        nearPending.remove(range)
    }

    private fun save() {
        val target = file ?: return
        runCatching {
            val json = org.json.JSONObject()
                .put("done", org.json.JSONArray(done.map { org.json.JSONArray(listOf(it.first, it.last)) }))
                .put("lines", org.json.JSONArray(lines.map { org.json.JSONArray(listOf(it.startMs, it.endMs, it.text)) }))
            target.parentFile?.mkdirs()
            target.writeText(json.toString())
        }
    }

    private fun restore(source: File) {
        runCatching {
            val json = org.json.JSONObject(source.readText())
            val savedDone = json.getJSONArray("done")
            for (i in 0 until savedDone.length()) {
                val range = savedDone.getJSONArray(i)
                done += range.getLong(0)..range.getLong(1)
            }
            val savedLines = json.getJSONArray("lines")
            for (i in 0 until savedLines.length()) {
                val line = savedLines.getJSONArray(i)
                lines += TranscribedLine(line.getLong(0), line.getLong(1), line.getString(2))
            }
        }
    }

    companion object {
        private val sessions = mutableMapOf<String, AudioTranscriptionSession>()

        fun forVideo(cacheDir: File?, videoKey: String, targetLanguage: String): AudioTranscriptionSession = synchronized(sessions) {
            // The version changes when saved lines from earlier builds must not be reused.
            val key = "$videoKey|$targetLanguage|$TRANSCRIPTION_MODEL|$CACHE_VERSION"
            sessions.getOrPut(key) {
                val name = MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
                AudioTranscriptionSession(cacheDir?.let { File(File(it, DISK_CACHE_DIRECTORY), "$name.json") })
            }
        }
    }
}

/** Lines as the subtitle view shows them: right-to-left text fixed, times in microseconds. */
internal fun transcribedLinesToCues(lines: List<TranscribedLine>): List<CuesWithTiming> = lines.map { line ->
    val cue = PlayerSubtitleRtlFix.fixCueText(Cue.Builder().setText(line.text).build(), isBuiltInSubtitle = false)
    CuesWithTiming(listOf(cue), line.startMs * 1000L, (line.endMs - line.startMs) * 1000L)
}

/** The line on screen at [positionMs], if any. */
internal fun transcribedLineAt(lines: List<TranscribedLine>, positionMs: Long): String? =
    lines.lastOrNull { it.startMs <= positionMs && positionMs < it.endMs }?.text

private val PlayerRuntimeController.transcriptionVideoKey: String
    get() = listOfNotNull(subtitleChoiceVideoKey, currentFilename ?: currentStreamUrl?.substringBefore('?'))
        .joinToString("|")

/** True when subtitles can be made from this video's audio. */
internal fun PlayerRuntimeController.canTranscribeAudio(): Boolean {
    SubtitleTranslationService.transcriptionSetup() ?: return false
    val url = currentStreamUrl ?: return false
    if (!url.startsWith("http", ignoreCase = true)) return false
    // Adaptive streams have no single file to read the audio from.
    val path = url.substringBefore('?').lowercase()
    if (path.endsWith(".m3u8") || path.endsWith(".mpd")) return false
    return audioTranscriptionUnsupportedUrl != url
}

/** True while subtitles made from the audio are the ones shown. */
internal fun PlayerRuntimeController.isAudioTranscriptionActive(): Boolean = isTranscriptionSidecarKey(activeSidecarSubtitleKey)

/**
 * Makes subtitles in [targetLanguage] from the audio, ahead of playback, and shows them like an
 * add-on subtitle (on mpv, in the translated-line overlay). Choosing any subtitle stops it.
 */
internal fun PlayerRuntimeController.startAudioTranscription(targetLanguage: String) {
    stopAudioTranscription()
    val setup = SubtitleTranslationService.transcriptionSetup() ?: return
    val url = currentStreamUrl ?: return
    val key = TRANSCRIPTION_KEY_PREFIX + url
    val session = AudioTranscriptionSession.forVideo(context.cacheDir, transcriptionVideoKey, targetLanguage)
    val generation = startGeneratedSidecarSubtitle(key, targetLanguage)
    updateGeneratedSidecarCues(key, generation, transcribedLinesToCues(session.lines()))
    _uiState.update { it.copy(isAiTranscribing = true) }
    // The audio being played is the one read: by language, else by its place among the audio tracks.
    val audioLanguage = currentAudioLanguageForTranslation()
    val audioPosition = _uiState.value.selectedAudioTrackIndex
    val headers = currentHeaders
    fun isCurrent() = activeSidecarSubtitleKey == key && activeSidecarGeneration == generation

    audioTranscriptionRenderJob = scope.launch {
        while (isActive && isCurrent()) {
            if (isUsingMpvEngine()) {
                val line = transcribedLineAt(session.lines(), currentPlaybackPositionMs() ?: 0L)
                if (line != _uiState.value.mpvTranslatedSubtitle) _uiState.update { it.copy(mpvTranslatedSubtitle = line) }
            }
            delay(RENDER_INTERVAL_MS)
        }
    }
    audioTranscriptionJob = scope.launch(Dispatchers.IO) {
        val client = AudioTranscriptionClient(setup)
        val slots = Semaphore(PARALLEL_REQUESTS)
        val reader = AudioClipReader(context, url, headers, audioLanguage, audioPosition)
        val failures = java.util.concurrent.atomic.AtomicInteger(0)
        try {
            if (!reader.open()) {
                audioTranscriptionUnsupportedUrl = url
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.subtitle_ai_transcription_unsupported, Toast.LENGTH_LONG).show()
                    stopAudioTranscription()
                }
                return@launch
            }
            var readPositionMs = -1L
            while (isActive && isCurrent() && failures.get() < MAX_FAILURES_IN_A_ROW) {
                val positionMs = withContext(Dispatchers.Main) { currentPlaybackPositionMs() } ?: 0L
                val from = if (session.needsNearClip(positionMs)) positionMs else session.coveredEnd(positionMs)
                if (from - positionMs >= LEAD_MS) {
                    delay(500)
                    continue
                }
                val near = from - positionMs < NEAR_WINDOW_MS
                // A decoder or network failure ends transcription for this stream, never the app.
                val clip = try {
                    if (readPositionMs != from) reader.seekTo(from)
                    reader.nextClip(
                        if (near) NEAR_CLIP_MS else CLIP_MS,
                        if (near) NEAR_CUT_SEARCH_MS else CUT_SEARCH_MS,
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w(PlayerRuntimeController.TAG, "Reading audio for transcription failed: ${error.message}")
                    readPositionMs = -1L
                    failures.incrementAndGet()
                    delay(2_000)
                    continue
                } ?: break
                readPositionMs = clip.endMs
                val range = session.markPending(clip.startMs, clip.endMs, near)
                // Near clips never wait behind clips made far ahead.
                if (!near) slots.acquire()
                launch {
                    try {
                        val lines = inTargetLanguage(client.transcribe(clip.samples, targetLanguage), targetLanguage)
                            .map { TranscribedLine(clip.startMs + it.startMs, clip.startMs + it.endMs, it.text) }
                        session.complete(range, lines)
                        failures.set(0)
                        withContext(Dispatchers.Main) {
                            updateGeneratedSidecarCues(key, generation, transcribedLinesToCues(session.lines()))
                        }
                    } catch (cancelled: CancellationException) {
                        session.fail(range)
                        throw cancelled
                    } catch (error: Exception) {
                        session.fail(range)
                        val count = failures.incrementAndGet()
                        Log.w(PlayerRuntimeController.TAG, "Transcription of ${clip.startMs}ms failed: ${error.message}")
                        if (error is SubtitleTranslationException && error.isFatal) failures.set(MAX_FAILURES_IN_A_ROW)
                        delay(2_000L * count)
                    } finally {
                        if (!near) slots.release()
                    }
                }
            }
            if (failures.get() >= MAX_FAILURES_IN_A_ROW) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.subtitle_ai_translation_error_generic, Toast.LENGTH_LONG).show()
                }
            }
        } finally {
            reader.close()
        }
    }
}

/** Stops making subtitles from the audio and clears them from the screen. */
internal fun PlayerRuntimeController.stopAudioTranscription() {
    if (isTranscriptionSidecarKey(activeSidecarSubtitleKey)) stopSidecarAddonSubtitle(clearView = true)
    endAudioTranscriptionJobs()
}

/** Ends the work behind subtitles made from the audio; called whenever the sidecar subtitle stops. */
internal fun PlayerRuntimeController.endAudioTranscriptionJobs() {
    val wasRunning = audioTranscriptionJob != null || audioTranscriptionRenderJob != null
    audioTranscriptionJob?.cancel()
    audioTranscriptionJob = null
    audioTranscriptionRenderJob?.cancel()
    audioTranscriptionRenderJob = null
    if (wasRunning || _uiState.value.isAiTranscribing) {
        _uiState.update { it.copy(isAiTranscribing = false, mpvTranslatedSubtitle = if (isUsingMpvEngine()) null else it.mpvTranslatedSubtitle) }
    }
}

internal fun isTranscriptionSidecarKey(key: String?): Boolean = key?.startsWith(TRANSCRIPTION_KEY_PREFIX) == true

/**
 * Lines the model wrote in the spoken language instead of [targetLanguage] go through the text
 * translator; ones it can't translate are left out rather than shown in the wrong language.
 */
private suspend fun inTargetLanguage(lines: List<TranscribedLine>, targetLanguage: String): List<TranscribedLine> {
    val wrong = lines.filterNot { com.nuvio.tv.core.subtitles.isWrittenInTargetScript(it.text, targetLanguage) }
    if (wrong.isEmpty()) return lines
    val translator = SubtitleTranslationService.activeTranslator(trackLanguage = "und")
    if (translator != null) runCatching { translator.translateTexts(wrong.map { it.text }) }
    val wrongSet = wrong.toSet()
    return lines.mapNotNull { line ->
        if (line !in wrongSet) return@mapNotNull line
        translator?.cached(line.text)
            ?.takeIf { com.nuvio.tv.core.subtitles.isWrittenInTargetScript(it, targetLanguage) }
            ?.let { line.copy(text = it) }
    }
}
