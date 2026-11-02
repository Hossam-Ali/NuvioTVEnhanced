package com.nuvio.tv.core.subtitles

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

// Makes subtitles from a video's audio when it has none: short clips of the audio are sent to an
// OpenRouter model that listens, transcribes and translates in one step, and returns timed lines.

/** Answers in a few seconds with close timings, so subtitles keep up with playback and seeking. */
internal const val TRANSCRIPTION_MODEL = "google/gemini-3.1-flash-lite"
/** Used while the first model is rate-limited or down. */
internal const val TRANSCRIPTION_FALLBACK_MODEL = "google/gemini-2.5-flash-lite"
/** Speech needs no more than this; it keeps uploads small. */
internal const val TRANSCRIPTION_SAMPLE_RATE = 16_000

internal data class AudioTranscriptionSetup(
    val apiKey: String,
    val targetLanguage: String,
    val http: SubtitleTranslationHttp,
)

/** One subtitle made from the audio, in milliseconds of the video. */
internal data class TranscribedLine(val startMs: Long, val endMs: Long, val text: String)

internal fun buildTranscriptionPrompt(targetLanguage: String): String {
    val languageName = languageNameForPrompt(targetLanguage)
    val styleHint = if (targetLanguage.lowercase().startsWith("ar")) {
        " Use Modern Standard Arabic as used in professional subtitles."
    } else {
        ""
    }
    return "You make film and TV subtitles from audio. Transcribe the speech in the clip and translate it into $languageName.$styleHint " +
        "Reply with JSON only: {\"s\":[{\"a\":<start seconds>,\"b\":<end seconds>,\"x\":\"<subtitle>\"}]}. " +
        "Times are seconds from the start of the clip, with one decimal, and must follow the speech closely. " +
        "Write every subtitle in $languageName only, whatever language is spoken: translate the speech, never copy " +
        "the original words or mix languages in a subtitle. " +
        "Start a new entry for every sentence and every change of speaker; an entry lasts at most 6 seconds and has " +
        "at most two lines of about 40 characters (use \\n). Keep names and numbers. " +
        "Skip silence, music and background noise; never invent speech. If there is no speech, reply {\"s\":[]}."
}

internal fun buildTranscriptionRequestBody(wav: ByteArray, clipMs: Long, targetLanguage: String): String = buildJsonObject {
    put("model", TRANSCRIPTION_MODEL)
    put("models", buildJsonArray {
        add(JsonPrimitive(TRANSCRIPTION_MODEL))
        add(JsonPrimitive(TRANSCRIPTION_FALLBACK_MODEL))
    })
    put("messages", buildJsonArray {
        add(buildJsonObject { put("role", "system"); put("content", buildTranscriptionPrompt(targetLanguage)) })
        add(buildJsonObject {
            put("role", "user")
            put("content", buildJsonArray {
                add(buildJsonObject { put("type", "text"); put("text", "Clip length: %.1f seconds.".format(java.util.Locale.US, clipMs / 1000.0)) })
                add(buildJsonObject {
                    put("type", "input_audio")
                    put("input_audio", buildJsonObject {
                        put("data", java.util.Base64.getEncoder().encodeToString(wav))
                        put("format", "wav")
                    })
                })
            })
        })
    })
    put("response_format", buildJsonObject { put("type", "json_object") })
    put("usage", buildJsonObject { put("include", true) })
}.toString()

/**
 * Reads the model's reply into lines relative to the clip: times are kept inside the clip, empty
 * or backwards entries are dropped, and a line ends where the next one starts.
 */
internal fun parseTranscriptionResponse(content: String, clipMs: Long): List<TranscribedLine> {
    val trimmed = content.trim()
    val unfenced = Regex("^```(?:json)?\\s*([\\s\\S]*?)\\s*```$", RegexOption.IGNORE_CASE)
        .find(trimmed)?.groupValues?.get(1) ?: trimmed
    val root = runCatching { subtitleTranslationJson.parseToJsonElement(unfenced) }.getOrNull()
        ?: Regex("\\{[\\s\\S]*\\}").find(unfenced)?.value?.let { runCatching { subtitleTranslationJson.parseToJsonElement(it) }.getOrNull() }
        ?: return emptyList()
    val entries = when (root) {
        is JsonArray -> root
        is JsonObject -> (root["s"] ?: root["subtitles"] ?: root["t"]) as? JsonArray ?: return emptyList()
        else -> return emptyList()
    }
    val lines = entries.mapNotNull { element ->
        val entry = element as? JsonObject ?: return@mapNotNull null
        val start = (entry["a"] ?: entry["start"])?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
        val end = (entry["b"] ?: entry["end"])?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
        val text = ((entry["x"] ?: entry["text"]) as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?.replace("\\n", "\n")?.lines()?.joinToString("\n") { it.trim() }?.trim()
            ?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val startMs = (start * 1000).toLong().coerceIn(0L, clipMs)
        val endMs = (end * 1000).toLong().coerceIn(0L, clipMs)
        if (endMs <= startMs) null else TranscribedLine(startMs, endMs, text)
    }.sortedBy { it.startMs }
    return lines.mapIndexed { index, line ->
        val nextStart = lines.getOrNull(index + 1)?.startMs
        if (nextStart != null && line.endMs > nextStart && nextStart > line.startMs) line.copy(endMs = nextStart) else line
    }
}

/** The writing system of [language] when it has its own, so a line in another script stands out. */
private fun scriptOf(language: String): Character.UnicodeScript? = when (language.lowercase().substringBefore('-').substringBefore('_')) {
    "ar", "fa", "ur", "ps", "ckb", "sd", "ug" -> Character.UnicodeScript.ARABIC
    "he", "yi" -> Character.UnicodeScript.HEBREW
    "ru", "uk", "bg", "be", "mk", "kk", "ky", "mn", "tg" -> Character.UnicodeScript.CYRILLIC
    "el" -> Character.UnicodeScript.GREEK
    "hi", "mr", "ne" -> Character.UnicodeScript.DEVANAGARI
    "th" -> Character.UnicodeScript.THAI
    "ko" -> Character.UnicodeScript.HANGUL
    "zh" -> Character.UnicodeScript.HAN
    "ka" -> Character.UnicodeScript.GEORGIAN
    "hy" -> Character.UnicodeScript.ARMENIAN
    "bn" -> Character.UnicodeScript.BENGALI
    "ta" -> Character.UnicodeScript.TAMIL
    else -> null
}

/**
 * False when [text] is mostly in another script than [targetLanguage]'s, as when the model wrote
 * down the speech instead of translating it. True whenever it can't tell (Latin-script targets).
 */
internal fun isWrittenInTargetScript(text: String, targetLanguage: String): Boolean {
    val script = scriptOf(targetLanguage) ?: return true
    var letters = 0
    var inScript = 0
    var index = 0
    while (index < text.length) {
        val codePoint = text.codePointAt(index)
        if (Character.isLetter(codePoint)) {
            letters++
            if (Character.UnicodeScript.of(codePoint) == script) inScript++
        }
        index += Character.charCount(codePoint)
    }
    return letters == 0 || inScript * 2 >= letters
}

/** A 16-bit mono WAV file of the first [count] [samples]. */
internal fun pcm16ToWav(samples: ShortArray, count: Int = samples.size, sampleRate: Int = TRANSCRIPTION_SAMPLE_RATE): ByteArray {
    val dataBytes = count * 2
    val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray()); putInt(36 + dataBytes); put("WAVE".toByteArray())
        put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
        putInt(sampleRate); putInt(sampleRate * 2); putShort(2); putShort(16)
        put("data".toByteArray()); putInt(dataBytes)
    }
    val data = ByteBuffer.allocate(dataBytes).order(ByteOrder.LITTLE_ENDIAN)
    for (i in 0 until count) data.putShort(samples[i])
    return ByteArrayOutputStream(44 + dataBytes).apply { write(header.array()); write(data.array()) }.toByteArray()
}

/**
 * The sample in [from, to) where the quietest [window] of audio starts its middle, so a clip ends
 * in a pause instead of in the middle of a word.
 */
internal fun quietestCutSample(samples: ShortArray, from: Int, to: Int, window: Int): Int {
    val start = from.coerceIn(0, samples.size)
    val end = to.coerceIn(start, samples.size)
    if (end - start <= window) return end
    var sum = 0L
    for (i in start until start + window) sum += abs(samples[i].toInt())
    var best = sum
    var bestStart = start
    for (i in start + window until end) {
        sum += abs(samples[i].toInt()) - abs(samples[i - window].toInt())
        if (sum < best) {
            best = sum
            bestStart = i - window + 1
        }
    }
    return bestStart + window / 2
}

/**
 * Turns decoded audio into 16 kHz mono: channels are averaged, then each output sample is the
 * average of the input samples it covers, which also filters what 16 kHz cannot hold.
 */
internal class MonoDownsampler(val inputRate: Int, private val outputRate: Int = TRANSCRIPTION_SAMPLE_RATE) {
    private val step = inputRate.toDouble() / outputRate
    private var position = 0.0
    private var sum = 0.0
    private var count = 0

    /** Adds [frames] frames of interleaved [channels]-channel samples scaled to -1..1. */
    fun add(input: FloatArray, frames: Int, channels: Int, out: ShortSink) {
        for (frame in 0 until frames) {
            var mono = 0f
            val base = frame * channels
            for (c in 0 until channels) mono += input[base + c]
            sum += mono / channels
            count++
            position += 1.0
            if (position >= step) {
                position -= step
                val value = (sum / count).coerceIn(-1.0, 1.0)
                out.add((value * Short.MAX_VALUE).toInt().toShort())
                sum = 0.0
                count = 0
            }
        }
    }

    fun reset() {
        position = 0.0
        sum = 0.0
        count = 0
    }
}

/** A growable buffer of 16-bit samples. */
internal class ShortSink(initialCapacity: Int = TRANSCRIPTION_SAMPLE_RATE * 40) {
    var data = ShortArray(initialCapacity)
        private set
    var size = 0
        private set

    fun add(value: Short) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = value
    }

    /** Removes the first [count] samples and returns them. */
    fun take(count: Int): ShortArray {
        val n = count.coerceIn(0, size)
        val taken = data.copyOfRange(0, n)
        System.arraycopy(data, n, data, 0, size - n)
        size -= n
        return taken
    }

    fun clear() {
        size = 0
    }
}

/** Sends clips to OpenRouter and reports the cost with the translation spending. */
internal class AudioTranscriptionClient(private val setup: AudioTranscriptionSetup) {
    suspend fun transcribe(samples: ShortArray, targetLanguage: String = setup.targetLanguage): List<TranscribedLine> {
        val clipMs = samples.size * 1000L / TRANSCRIPTION_SAMPLE_RATE
        val body = buildTranscriptionRequestBody(pcm16ToWav(samples), clipMs, targetLanguage)
        val headers = mapOf(
            "Authorization" to "Bearer ${setup.apiKey}",
            "HTTP-Referer" to OPENROUTER_APP_URL,
            "X-Title" to OPENROUTER_APP_TITLE,
        )
        val response = try {
            setup.http.postJson(OPENROUTER_ENDPOINT, headers, body)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            throw SubtitleTranslationException(SubtitleTranslationException.Code.Network, error.message ?: "Network request failed")
        }
        if (response.status !in 200..299) {
            throw classifySubtitleTranslationFailure(response.status, response.body, response.retryAfterSeconds)
        }
        val root = runCatching { subtitleTranslationJson.parseToJsonElement(response.body).jsonObject }.getOrNull()
            ?: throw SubtitleTranslationException(SubtitleTranslationException.Code.Network, "Unreadable reply")
        val content = runCatching {
            root["choices"]!!.let { it as JsonArray }[0].jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content
        }.getOrNull().orEmpty()
        val lines = parseTranscriptionResponse(content, clipMs)
        parseSubtitleTranslationUsage(response.body, lines.size)?.let(SubtitleTranslationUsageStore::record)
        return lines
    }
}
