@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.nuvio.tv.ui.screens.player

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import androidx.media3.exoplayer.MediaExtractorCompat
import androidx.media3.extractor.DefaultExtractorsFactory
import com.nuvio.tv.core.subtitles.MonoDownsampler
import com.nuvio.tv.core.subtitles.ShortSink
import com.nuvio.tv.core.subtitles.TRANSCRIPTION_SAMPLE_RATE
import com.nuvio.tv.core.subtitles.quietestCutSample
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A piece of the video's audio as 16 kHz mono, with where it sits in the video. */
internal class AudioClip(val startMs: Long, val samples: ShortArray) {
    val endMs: Long get() = startMs + samples.size * 1000L / TRANSCRIPTION_SAMPLE_RATE
}

/**
 * Reads the audio of a stream on its own connection, ahead of playback, and decodes it to
 * 16 kHz mono for transcription. Uses Media3's extractors (the same containers the player reads)
 * and the device's decoders. Not thread-safe: one reader belongs to one transcription job.
 */
internal class AudioClipReader(
    private val context: Context,
    private val url: String,
    private val headers: Map<String, String>,
    private val preferredLanguage: String?,
    /** The playing track's place among the audio tracks, used when languages don't tell them apart. */
    private val preferredPosition: Int = -1,
) : Closeable {
    private var extractor: MediaExtractorCompat? = null
    private var codec: MediaCodec? = null
    private var downsampler: MonoDownsampler? = null
    private val buffered = ShortSink()
    private var bufferedStartMs = 0L
    private var discardBeforeUs = 0L
    private var inputDone = false
    private var outputDone = false
    private var outputChannels = 2
    private var outputFloat = false
    private val info = MediaCodec.BufferInfo()
    private var floatScratch = FloatArray(0)

    /** Opens the stream and its audio decoder; false when either is not possible. */
    fun open(): Boolean = runCatching {
        val source = MediaExtractorCompat(
            DefaultExtractorsFactory(),
            PlayerPlaybackNetworking.createDataSourceFactory(context, headers),
        )
        source.setDataSource(Uri.parse(url), 0L)
        val tracks = (0 until source.trackCount).filter {
            source.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")
        }
        val track = tracks.firstOrNull { index ->
            preferredLanguage != null &&
                PlayerSubtitleUtils.matchesLanguageCode(source.getTrackFormat(index).getString(MediaFormat.KEY_LANGUAGE), preferredLanguage)
        } ?: tracks.getOrNull(preferredPosition) ?: tracks.firstOrNull() ?: error("no audio track")
        source.selectTrack(track)
        val format = source.getTrackFormat(track)
        val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        decoder.configure(format, null, null, 0)
        decoder.start()
        extractor = source
        codec = decoder
        outputChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
        downsampler = MonoDownsampler(format.getInteger(MediaFormat.KEY_SAMPLE_RATE))
        true
    }.getOrElse {
        Log.w(TAG, "Audio for transcription unavailable: ${it.message}")
        close()
        false
    }

    /** Moves reading to [positionMs]; audio before it is skipped. */
    fun seekTo(positionMs: Long) {
        val source = extractor ?: return
        source.seekTo(positionMs * 1000L, MediaExtractorCompat.SEEK_TO_PREVIOUS_SYNC)
        codec?.flush()
        downsampler?.reset()
        buffered.clear()
        bufferedStartMs = positionMs
        discardBeforeUs = positionMs * 1000L
        inputDone = false
        outputDone = false
    }

    /**
     * The next clip of about [maxMs], ending at the quietest moment of its last [searchMs] so
     * words are not cut. The rest stays buffered for the next clip. Null at the end of the stream.
     */
    fun nextClip(maxMs: Long, searchMs: Long): AudioClip? {
        val wanted = (maxMs * TRANSCRIPTION_SAMPLE_RATE / 1000).toInt()
        while (buffered.size < wanted && !outputDone) {
            if (!pump()) break
        }
        if (buffered.size == 0) return null
        val cut = if (buffered.size < wanted) {
            buffered.size
        } else {
            val searchFrom = wanted - (searchMs * TRANSCRIPTION_SAMPLE_RATE / 1000).toInt()
            quietestCutSample(buffered.data, searchFrom, wanted, TRANSCRIPTION_SAMPLE_RATE / 5)
        }
        val clip = AudioClip(bufferedStartMs, buffered.take(cut))
        bufferedStartMs = clip.endMs
        return clip
    }

    /** Feeds the decoder and collects its output once; false when nothing more can come. */
    private fun pump(): Boolean {
        val source = extractor ?: return false
        val decoder = codec ?: return false
        if (!inputDone) {
            val inputIndex = decoder.dequeueInputBuffer(10_000)
            if (inputIndex >= 0) {
                val input = decoder.getInputBuffer(inputIndex)!!
                val size = source.readSampleData(input, 0)
                if (size < 0) {
                    decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    inputDone = true
                } else {
                    decoder.queueInputBuffer(inputIndex, 0, size, source.sampleTime, 0)
                    source.advance()
                }
            }
        }
        val outputIndex = decoder.dequeueOutputBuffer(info, 10_000)
        when {
            outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> onOutputFormat(decoder.outputFormat)
            outputIndex >= 0 -> {
                if (info.size > 0) collect(decoder.getOutputBuffer(outputIndex)!!, info)
                decoder.releaseOutputBuffer(outputIndex, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
            }
        }
        return !outputDone
    }

    private fun onOutputFormat(format: MediaFormat) {
        outputChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
        outputFloat = format.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
            format.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
        downsampler = MonoDownsampler(format.getInteger(MediaFormat.KEY_SAMPLE_RATE))
    }

    private fun collect(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        val sampler = downsampler ?: return
        buffer.position(info.offset)
        buffer.limit(info.offset + info.size)
        val ordered = buffer.slice().order(ByteOrder.nativeOrder())
        val bytesPerSample = if (outputFloat) 4 else 2
        val totalSamples = info.size / bytesPerSample
        var frames = totalSamples / outputChannels
        var skipFrames = 0
        // After a seek, decoding starts at the key frame before the target; that part is skipped.
        if (info.presentationTimeUs < discardBeforeUs) {
            val inputRate = sampler.inputRate
            skipFrames = (((discardBeforeUs - info.presentationTimeUs) * inputRate) / 1_000_000L).toInt()
            if (skipFrames >= frames) return
        }
        if (floatScratch.size < totalSamples) floatScratch = FloatArray(totalSamples)
        if (outputFloat) {
            ordered.asFloatBuffer().get(floatScratch, 0, totalSamples)
        } else {
            val shorts = ordered.asShortBuffer()
            for (i in 0 until totalSamples) floatScratch[i] = shorts.get(i) / 32768f
        }
        if (skipFrames > 0) {
            System.arraycopy(floatScratch, skipFrames * outputChannels, floatScratch, 0, (frames - skipFrames) * outputChannels)
            frames -= skipFrames
        }
        discardBeforeUs = 0L
        sampler.add(floatScratch, frames, outputChannels, buffered)
    }

    override fun close() {
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        runCatching { extractor?.release() }
        codec = null
        extractor = null
    }

    private companion object {
        const val TAG = "AudioClipReader"
    }
}
