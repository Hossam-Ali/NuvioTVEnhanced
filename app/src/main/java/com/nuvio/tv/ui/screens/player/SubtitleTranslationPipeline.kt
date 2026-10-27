@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.nuvio.tv.ui.screens.player

import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.SniffFailure
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.text.CueDecoder
import com.nuvio.tv.core.subtitles.SubtitleTranslationService
import java.io.EOFException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.max

/**
 * Swaps AI translations into cues before the rest of the subtitle pipeline (position
 * normalization, RTL fixes, SDH stripping), keeping each cue's timing and layout. Lines without a
 * translation yet are shown as they are and requested right away; the screen is redrawn through
 * [SubtitleCueTranslationBridge] once they arrive.
 */
internal class TranslatingTextOutput(
    private val delegate: TextOutput,
    bridge: SubtitleCueTranslationBridge,
    /** False while these cues are not shown (for example a sidecar add-on subtitle is active). */
    private val shouldTranslateProvider: () -> Boolean = { true },
    /** The looper Media3 delivers cues on. */
    outputLooper: Looper = Looper.getMainLooper(),
) : TextOutput {
    private var lastCueGroup: CueGroup? = null
    /** True once the original text of [lastCueGroup] was shown because its translation was late. */
    private var showingOriginal = false
    private val handler = Handler(outputLooper)
    private val showOriginalWhenLate = Runnable {
        showingOriginal = true
        lastCueGroup?.let(::deliver)
    }

    init {
        // Runs on the output looper, where Media3 delivers cues.
        bridge.register { lastCueGroup?.let(::deliver) }
    }

    override fun onCues(cueGroup: CueGroup) {
        lastCueGroup = cueGroup
        showingOriginal = false
        handler.removeCallbacks(showOriginalWhenLate)
        deliver(cueGroup)
    }

    @Deprecated("Uses the deprecated Media3 callback for text outputs.")
    override fun onCues(cues: List<Cue>) {
        @Suppress("DEPRECATION")
        delegate.onCues(translateCues(cues, holdUntranslated = false))
    }

    private fun deliver(cueGroup: CueGroup) {
        delegate.onCues(CueGroup(translateCues(cueGroup.cues, holdUntranslated = !showingOriginal), cueGroup.presentationTimeUs))
    }

    /**
     * Swaps in cached translations. A line whose translation is still on its way is held back
     * for a moment (the redraw shows it in the target language) instead of flashing the original;
     * only if it is still missing after [LATE_TRANSLATION_HOLD_MS] is the original shown.
     */
    private fun translateCues(cues: List<Cue>, holdUntranslated: Boolean): List<Cue> {
        if (!shouldTranslateProvider()) return cues
        val translator = SubtitleTranslationService.activeTranslator() ?: return cues
        val missing = mutableListOf<String>()
        val translated = cues.mapNotNull { cue ->
            val source = cue.text?.toString()?.takeIf { cue.bitmap == null && it.isNotBlank() } ?: return@mapNotNull cue
            val translation = translator.cached(source)
            if (translation == null) {
                missing += source
                cue.takeUnless { holdUntranslated }
            } else {
                cue.buildUpon().setText(translation).build()
            }
        }
        handler.removeCallbacks(showOriginalWhenLate)
        if (missing.isNotEmpty() && holdUntranslated) {
            handler.postDelayed(showOriginalWhenLate, LATE_TRANSLATION_HOLD_MS)
        }
        SubtitleTranslationService.translate(translator, missing)
        return translated
    }

    private companion object {
        const val LATE_TRANSLATION_HOLD_MS = 1_500L
    }
}

/** Lets the player ask its text outputs to redraw once new translations are cached. */
internal class SubtitleCueTranslationBridge {
    private val refreshers = CopyOnWriteArrayList<() -> Unit>()

    fun register(refresh: () -> Unit) {
        refreshers += refresh
    }

    /** Call on the main looper. */
    fun refresh() = refreshers.forEach { it() }
}

/**
 * Reads embedded subtitle text while ExoPlayer buffers ahead and queues it for translation, so
 * lines are usually translated before they are shown. Output is forwarded to Media3 unchanged.
 */
internal class SubtitleTranslationExtractorsFactory(
    private val delegate: ExtractorsFactory,
) : ExtractorsFactory {
    init {
        EmbeddedSubtitleTextTap.reset()
    }

    override fun createExtractors(): Array<Extractor> = wrap(delegate.createExtractors())

    override fun createExtractors(uri: Uri, responseHeaders: Map<String, List<String>>): Array<Extractor> =
        wrap(delegate.createExtractors(uri, responseHeaders))

    private fun wrap(extractors: Array<Extractor>): Array<Extractor> =
        Array(extractors.size) { index -> TranslationTapExtractor(extractors[index]) }
}

/** Recent embedded subtitle lines per track, replayed when the selected track changes. */
internal object EmbeddedSubtitleTextTap {
    private const val MAX_RECENT_LINES_PER_TRACK = 400

    private class Track(var language: String?) {
        val recent = ArrayDeque<String>()
    }

    private val lock = Any()
    private val tracks = HashMap<Int, Track>()

    fun reset() = synchronized(lock) { tracks.clear() }

    fun record(trackId: Int, language: String?, text: String) {
        val normalizedLanguage = normalizedLanguage(language)
        synchronized(lock) {
            val track = tracks.getOrPut(trackId) { Track(normalizedLanguage) }
            track.language = normalizedLanguage
            track.recent.addLast(text)
            if (track.recent.size > MAX_RECENT_LINES_PER_TRACK) track.recent.removeFirst()
        }
        if (matchesSelection(normalizedLanguage)) SubtitleTranslationService.prefetch(text)
    }

    /** Queues lines already buffered for the newly selected track. */
    fun replaySelection() {
        val lines = synchronized(lock) {
            tracks.values.filter { matchesSelection(it.language) }.flatMap { it.recent.toList() }
        }
        lines.forEach(SubtitleTranslationService::prefetch)
    }

    private fun normalizedLanguage(language: String?): String? =
        language?.takeIf { it.isNotBlank() }?.let(PlayerSubtitleUtils::normalizeLanguageCode)

    private fun matchesSelection(trackLanguage: String?): Boolean =
        normalizedLanguage(SubtitleTranslationService.selectedTrackLanguage) == trackLanguage
}

private class TranslationTapExtractor(private val delegate: Extractor) : RewrappableExtractor {
    override val wrappedExtractor: Extractor get() = delegate
    override fun rewrap(inner: Extractor): Extractor = TranslationTapExtractor(inner)
    override fun sniff(input: ExtractorInput) = delegate.sniff(input)
    override fun getSniffFailureDetails(): List<SniffFailure> = delegate.getSniffFailureDetails()
    override fun init(output: ExtractorOutput) = delegate.init(TranslationTapExtractorOutput(output))
    override fun read(input: ExtractorInput, seekPosition: PositionHolder) = delegate.read(input, seekPosition)
    override fun seek(position: Long, timeUs: Long) = delegate.seek(position, timeUs)
    override fun release() = delegate.release()
    override fun getUnderlyingImplementation(): Extractor = delegate.getUnderlyingImplementation()
}

private class TranslationTapExtractorOutput(private val delegate: ExtractorOutput) : ExtractorOutput {
    private val textTracks = mutableMapOf<Int, TrackOutput>()

    override fun track(id: Int, type: Int): TrackOutput {
        val output = delegate.track(id, type)
        return if (type == C.TRACK_TYPE_TEXT) {
            textTracks.getOrPut(id) { TranslationTapTextTrackOutput(output, id) }
        } else {
            output
        }
    }

    override fun endTracks() = delegate.endTracks()
    override fun seekMap(seekMap: SeekMap) = delegate.seekMap(seekMap)
}

/** Mirrors the AutoSync text tap: buffers sample bytes and decodes Media3 cue samples. */
private class TranslationTapTextTrackOutput(
    private val delegate: TrackOutput,
    private val trackId: Int,
) : TrackOutput {
    private val cueDecoder = CueDecoder()
    private var format: Format? = null
    private var buffered = ByteArray(0)
    private var start = 0
    private var end = 0

    override fun durationUs(durationUs: Long) = delegate.durationUs(durationUs)

    override fun format(format: Format) {
        this.format = format
        delegate.format(format)
    }

    override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean, sampleDataPart: Int): Int {
        if (sampleDataPart != TrackOutput.SAMPLE_DATA_PART_MAIN) {
            return delegate.sampleData(input, length, allowEndOfInput, sampleDataPart)
        }
        val bytes = ByteArray(length)
        val read = input.read(bytes, 0, length)
        if (read == C.RESULT_END_OF_INPUT) {
            return if (allowEndOfInput) C.RESULT_END_OF_INPUT else throw EOFException()
        }
        if (read > 0) {
            append(bytes, 0, read)
            delegate.sampleData(ParsableByteArray(bytes, read), read, sampleDataPart)
        }
        return read
    }

    override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
        if (sampleDataPart == TrackOutput.SAMPLE_DATA_PART_MAIN) append(data.data, data.position, length)
        delegate.sampleData(data, length, sampleDataPart)
    }

    override fun sampleMetadata(timeUs: Long, flags: Int, size: Int, offset: Int, cryptoData: TrackOutput.CryptoData?) {
        observe(timeUs, size, offset)
        delegate.sampleMetadata(timeUs, flags, size, offset, cryptoData)
        start = (end - offset).coerceIn(start, end)
        if (start == end) {
            start = 0
            end = 0
        }
    }

    private fun observe(timeUs: Long, size: Int, offset: Int) {
        val currentFormat = format ?: return
        if (size <= 0 || currentFormat.sampleMimeType != MimeTypes.APPLICATION_MEDIA3_CUES) return
        val sampleStart = end - offset - size
        if (sampleStart < start || sampleStart + size > end) return
        val decoded = runCatching { cueDecoder.decode(timeUs, buffered, sampleStart, size) }.getOrNull() ?: return
        // Each cue is shown separately, so each one is its own translation key.
        decoded.cues.forEach { cue ->
            val text = cue.text?.toString()?.takeIf { it.isNotBlank() } ?: return@forEach
            EmbeddedSubtitleTextTap.record(trackId, currentFormat.language, text)
        }
    }

    private fun append(data: ByteArray, offset: Int, length: Int) {
        if (length <= 0) return
        val retained = end - start
        val required = retained + length
        if (buffered.size - end < length) {
            buffered.copyInto(buffered, 0, start, end)
            start = 0
            end = retained
            if (buffered.size < required) buffered = buffered.copyOf(max(required, max(buffered.size * 2, 256)))
        }
        data.copyInto(buffered, end, offset, offset + length)
        end += length
    }
}
