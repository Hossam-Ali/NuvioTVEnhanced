package com.nuvio.tv.core.subtitles

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

// Translates subtitle cue text with the user's own OpenRouter or OpenAI key. Only text is sent;
// cue timing never leaves the player, so translated cues keep the sync of the source track.

/** The OpenRouter model used unless the user enters another one in settings. */
const val OPENROUTER_DEFAULT_MODEL = "openai/gpt-6-luna"

/** Models offered by earlier builds that were dropped; a saved one falls back to the default. */
internal val RETIRED_OPENROUTER_MODELS = setOf("inclusionai/ling-3.0-flash")

enum class SubtitleTranslationProvider(val label: String, val defaultModel: String) {
    OpenRouter("OpenRouter", OPENROUTER_DEFAULT_MODEL),
    OpenAi("OpenAI (ChatGPT)", "gpt-4.1-mini"),
}

internal data class SubtitleTranslationHttpResponse(
    val status: Int,
    val body: String,
    /** The provider's Retry-After header, in seconds, when it sent one. */
    val retryAfterSeconds: Long? = null,
)

internal fun interface SubtitleTranslationHttp {
    suspend fun postJson(url: String, headers: Map<String, String>, body: String): SubtitleTranslationHttpResponse
}

internal class SubtitleTranslationException(
    val code: Code,
    message: String,
    /** How long the provider asked us to wait, when it said. */
    val retryAfterMs: Long? = null,
) : Exception(message) {
    enum class Code { Auth, Model, Rate, Network, Format, Retry, Config }

    val isFatal: Boolean get() = code == Code.Auth || code == Code.Model
}

private const val OPENROUTER_ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
private const val OPENAI_ENDPOINT = "https://api.openai.com/v1/chat/completions"
private const val OPENROUTER_APP_URL = "https://nuvio.tv"
private const val OPENROUTER_APP_TITLE = "Nuvio TV"
// Smaller batches come back sooner (reply time grows with the number of lines) and run side by side.
private const val BATCH_SIZE = 20
private const val MAX_CONCURRENT_REQUESTS = 3
private const val CACHE_LIMIT = 6_000
private const val RETRY_AFTER_FAILURE_MS = 5_000L
private const val MAX_RETRY_AFTER_FAILURE_MS = 60_000L

private const val RETRY_SKIPPED_LINE_MS = 30_000L
private const val MAX_PROVIDER_MESSAGE_LENGTH = 160

private val languageNames = mapOf(
    "ar" to "Arabic", "de" to "German", "en" to "English", "es" to "Spanish", "fa" to "Persian",
    "fr" to "French", "he" to "Hebrew", "hi" to "Hindi", "id" to "Indonesian", "it" to "Italian",
    "ja" to "Japanese", "ko" to "Korean", "nl" to "Dutch", "pl" to "Polish", "pt" to "Portuguese",
    "ru" to "Russian", "sv" to "Swedish", "th" to "Thai", "tr" to "Turkish", "uk" to "Ukrainian",
    "ur" to "Urdu", "vi" to "Vietnamese", "zh" to "Chinese",
)

private val json = Json { ignoreUnknownKeys = true }

internal fun languageNameForPrompt(code: String): String {
    val normalized = code.trim().replace('_', '-').lowercase()
    return languageNames[normalized.substringBefore('-')] ?: normalized
}

internal fun normalizeCueTextForTranslation(text: String): String =
    text.lineSequence()
        .map { line -> line.replace(Regex("\\s+"), " ").trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")

internal data class SubtitleTranslationPrompt(val system: String, val user: String)

internal fun buildSubtitleTranslationPrompt(lines: List<String>, targetLanguage: String): SubtitleTranslationPrompt {
    val languageName = languageNameForPrompt(targetLanguage)
    val styleHint = if (targetLanguage.lowercase().startsWith("ar")) {
        " Use Modern Standard Arabic as used in professional subtitles."
    } else {
        ""
    }
    val system = "You translate film and TV subtitles into $languageName.$styleHint " +
        "Reply with JSON only, in the form {\"t\":[{\"i\":<id>,\"x\":\"<translation>\"}]}, with exactly one entry for every input id. " +
        "Each entry is one subtitle shown on screen: never merge, split, skip or reorder entries. " +
        "Keep translations short enough to read quickly, keep the line breaks of the source as \\n, " +
        "keep names and numbers, and translate sound descriptions such as [music] naturally. " +
        "If an entry is already in $languageName, return it unchanged."
    val user = buildJsonObject {
        put("target", languageName)
        put(
            "lines",
            buildJsonArray {
                lines.forEachIndexed { index, text ->
                    add(buildJsonObject { put("i", index); put("x", text) })
                }
            },
        )
    }.toString()
    return SubtitleTranslationPrompt(system, user)
}

/** Parses a provider reply into id → translated text. */
internal fun parseSubtitleTranslationResponse(text: String): Map<Int, String> {
    val trimmed = text.trim()
    val unfenced = Regex("^```(?:json)?\\s*([\\s\\S]*?)\\s*```$", RegexOption.IGNORE_CASE)
        .find(trimmed)?.groupValues?.get(1) ?: trimmed
    val root = runCatching { json.parseToJsonElement(unfenced) }.getOrNull() ?: return emptyMap()
    val entries: JsonArray = when (root) {
        is JsonArray -> root
        is JsonObject -> (root["t"] ?: root["translations"]) as? JsonArray ?: return emptyMap()
        else -> return emptyMap()
    }
    val result = linkedMapOf<Int, String>()
    entries.forEach { element ->
        val entry = element as? JsonObject ?: return@forEach
        val id = ((entry["i"] ?: entry["id"]) as? JsonPrimitive)?.intOrNull ?: return@forEach
        val value = ((entry["x"] ?: entry["text"] ?: entry["translation"]) as? JsonPrimitive)
            ?.takeIf { it.isString }?.content ?: return@forEach
        val translated = normalizeCueTextForTranslation(value.replace("\\n", "\n"))
        if (translated.isNotEmpty()) result[id] = translated
    }
    return result
}

private fun providerMessage(body: String): String {
    val parsed = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
    val message = when (val error = parsed?.get("error")) {
        is JsonObject -> (error["message"] ?: error["status"])?.jsonPrimitive?.content
        is JsonPrimitive -> error.content
        else -> parsed?.get("message")?.jsonPrimitive?.content
    }
    return message.orEmpty().take(MAX_PROVIDER_MESSAGE_LENGTH)
}

internal fun classifySubtitleTranslationFailure(
    status: Int,
    body: String,
    retryAfterSeconds: Long? = null,
): SubtitleTranslationException {
    val message = providerMessage(body)
    return when {
        status == 401 || status == 403 || (status == 400 && Regex("api[ _-]?key", RegexOption.IGNORE_CASE).containsMatchIn(message)) ->
            SubtitleTranslationException(SubtitleTranslationException.Code.Auth, message.ifBlank { "The API key was rejected" })
        status == 404 ->
            SubtitleTranslationException(SubtitleTranslationException.Code.Model, message.ifBlank { "The model was not found" })
        status == 429 ->
            SubtitleTranslationException(
                SubtitleTranslationException.Code.Rate,
                message.ifBlank { "Rate limit reached" },
                retryAfterSeconds?.takeIf { it > 0 }?.times(1_000L),
            )
        else ->
            SubtitleTranslationException(SubtitleTranslationException.Code.Network, message.ifBlank { "Request failed with HTTP $status" })
    }
}

private fun openAiSupportsTemperature(model: String): Boolean =
    // Reasoning models only accept the default temperature.
    !Regex("^(o\\d|gpt-5)", RegexOption.IGNORE_CASE).containsMatchIn(model)

/**
 * A translator with a text-keyed cache, so repeated lines and overlapping buffers are only sent
 * once per session. Safe to call from any thread.
 */
/** What one translation request used, as the provider reported it. */
internal data class SubtitleTranslationUsage(val lines: Int, val tokens: Int, val costUsd: Double?)

/** Reads the usage block of a chat completion; OpenRouter includes the request's cost in it. */
internal fun parseSubtitleTranslationUsage(response: String, lines: Int): SubtitleTranslationUsage? = runCatching {
    val usage = json.parseToJsonElement(response).jsonObject["usage"]?.jsonObject ?: return null
    SubtitleTranslationUsage(
        lines = lines,
        tokens = usage["total_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
        costUsd = usage["cost"]?.jsonPrimitive?.content?.toDoubleOrNull(),
    )
}.getOrNull()

internal class SubtitleTranslator(
    val provider: SubtitleTranslationProvider,
    private val apiKey: String,
    val model: String,
    val targetLanguage: String,
    private val http: SubtitleTranslationHttp,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L },
    private val onUsage: (SubtitleTranslationUsage) -> Unit = {},
) {
    private val lock = Any()
    private val cache = LinkedHashMap<String, String>()
    private val inFlight = HashMap<String, Deferred<Unit>>()
    private val lastAttemptMs = HashMap<String, Long>()
    private val requestSlots = Semaphore(MAX_CONCURRENT_REQUESTS)
    private var retryAtMs = 0L
    private var consecutiveFailures = 0

    @Volatile
    var fatalError: SubtitleTranslationException? = null
        private set

    fun cached(text: String): String? {
        val key = normalizeCueTextForTranslation(text)
        return synchronized(lock) { cache[key] }
    }

    /** Adds translations saved earlier; lines translated since then are kept. */
    fun restore(saved: Map<String, String>) {
        synchronized(lock) {
            saved.forEach { (source, translated) -> if (!cache.containsKey(source)) remember(source, translated) }
        }
    }

    /** The cached translations, oldest first, for saving on the device. */
    fun snapshot(): Map<String, String> = synchronized(lock) { LinkedHashMap(cache) }

    /** Translates every uncached text; throws [SubtitleTranslationException] on provider failures. */
    suspend fun translateTexts(texts: Collection<String>) {
        if (apiKey.isBlank() || targetLanguage.isBlank()) {
            throw SubtitleTranslationException(SubtitleTranslationException.Code.Config, "Subtitle translation is not configured")
        }
        fatalError?.let { throw it }
        val batches = mutableListOf<Pair<List<String>, CompletableDeferred<Unit>>>()
        val waitFor = mutableListOf<Deferred<Unit>>()
        synchronized(lock) {
            val now = nowMs()
            val pending = mutableListOf<String>()
            texts.asSequence().map(::normalizeCueTextForTranslation).distinct().forEach { source ->
                if (source.isEmpty() || cache.containsKey(source)) return@forEach
                val existing = inFlight[source]
                when {
                    existing != null -> waitFor += existing
                    // A line the model skipped is not resent on every redraw.
                    lastAttemptMs[source]?.let { now - it < RETRY_SKIPPED_LINE_MS } == true -> Unit
                    else -> pending += source
                }
            }
            if (pending.isNotEmpty() && now < retryAtMs) {
                throw SubtitleTranslationException(SubtitleTranslationException.Code.Retry, "Waiting before retrying the translation")
            }
            pending.chunked(BATCH_SIZE).forEach { sources ->
                val job = CompletableDeferred<Unit>()
                sources.forEach {
                    inFlight[it] = job
                    lastAttemptMs[it] = now
                }
                batches += sources to job
            }
            if (lastAttemptMs.size > CACHE_LIMIT) {
                lastAttemptMs.keys
                    .filter { cache.containsKey(it) || now - lastAttemptMs.getValue(it) >= RETRY_SKIPPED_LINE_MS }
                    .forEach(lastAttemptMs::remove)
            }
        }
        val failure: Throwable? = coroutineScope {
            val own = batches.map { (sources, job) ->
                async {
                    val outcome = runCatching { translateBatch(sources) }
                    synchronized(lock) {
                        sources.forEach { source -> if (inFlight[source] === job) inFlight.remove(source) }
                    }
                    outcome.exceptionOrNull()?.let(job::completeExceptionally) ?: job.complete(Unit)
                    outcome.exceptionOrNull()
                }
            }
            val ownFailure = own.awaitAll().firstOrNull { it != null }
            val sharedFailure = waitFor.firstNotNullOfOrNull { deferred ->
                runCatching { deferred.await() }.exceptionOrNull()
            }
            ownFailure ?: sharedFailure
        }
        if (failure != null) {
            if (failure is CancellationException) throw failure
            val error = failure as? SubtitleTranslationException
                ?: SubtitleTranslationException(SubtitleTranslationException.Code.Network, failure.message ?: "Translation failed")
            synchronized(lock) {
                if (error.isFatal) {
                    fatalError = error
                } else {
                    // Waits 5 s, then 10, 20, 40 and at most 60 s while failures repeat, or as
                    // long as the provider asked.
                    consecutiveFailures++
                    val backoffMs = (RETRY_AFTER_FAILURE_MS shl (consecutiveFailures - 1).coerceAtMost(4))
                        .coerceAtMost(MAX_RETRY_AFTER_FAILURE_MS)
                    retryAtMs = nowMs() + maxOf(backoffMs, error.retryAfterMs?.coerceAtMost(MAX_RETRY_AFTER_FAILURE_MS) ?: 0L)
                }
                // Lines from a failed request may be retried once the wait is over.
                batches.forEach { (sources, _) -> sources.forEach(lastAttemptMs::remove) }
            }
            throw error
        }
        if (batches.isNotEmpty()) synchronized(lock) { consecutiveFailures = 0 }
    }

    /** Milliseconds until requests are allowed again after a failure (0 when they are). */
    fun retryDelayMs(): Long = synchronized(lock) { (retryAtMs - nowMs()).coerceAtLeast(0L) }

    private suspend fun translateBatch(sources: List<String>) {
        val prompt = buildSubtitleTranslationPrompt(sources, targetLanguage)
        val response = requestSlots.withPermit { request(prompt) }
        val reply = runCatching {
            json.parseToJsonElement(response).jsonObject["choices"]!!.jsonArray[0].jsonObject["message"]!!
                .jsonObject["content"]!!.jsonPrimitive.content
        }.getOrDefault("")
        val translations = parseSubtitleTranslationResponse(reply)
        parseSubtitleTranslationUsage(response, translations.size)?.let(onUsage)
        if (translations.isEmpty()) {
            throw SubtitleTranslationException(SubtitleTranslationException.Code.Format, "The model reply could not be read")
        }
        synchronized(lock) {
            sources.forEachIndexed { index, source ->
                translations[index]?.let { translated -> remember(source, translated) }
            }
        }
    }

    private fun remember(source: String, translated: String) {
        cache.remove(source)
        cache[source] = translated
        if (cache.size > CACHE_LIMIT) {
            cache.keys.firstOrNull()?.let(cache::remove)
        }
    }

    private suspend fun request(prompt: SubtitleTranslationPrompt): String {
        val isOpenRouter = provider == SubtitleTranslationProvider.OpenRouter
        val body = buildJsonObject {
            put("model", model)
            // With a model of the user's choosing, OpenRouter falls back to the default one while
            // it is rate-limited or down.
            if (isOpenRouter && model != OPENROUTER_DEFAULT_MODEL) {
                put("models", buildJsonArray {
                    add(JsonPrimitive(model))
                    add(JsonPrimitive(OPENROUTER_DEFAULT_MODEL))
                })
            }
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", prompt.system) })
                add(buildJsonObject { put("role", "user"); put("content", prompt.user) })
            })
            // Both offered OpenRouter models support JSON mode, so replies always parse.
            put("response_format", buildJsonObject { put("type", "json_object") })
            if (isOpenRouter) {
                put("reasoning", buildJsonObject { put("enabled", true) })
            } else if (openAiSupportsTemperature(model)) {
                put("temperature", 0.2)
            }
        }
        val headers = buildMap {
            // The key goes in a header so it never appears in a URL or proxy log.
            put("Authorization", "Bearer $apiKey")
            if (isOpenRouter) {
                put("HTTP-Referer", OPENROUTER_APP_URL)
                put("X-Title", OPENROUTER_APP_TITLE)
            }
        }
        val response = post(if (isOpenRouter) OPENROUTER_ENDPOINT else OPENAI_ENDPOINT, headers, body.toString())
        return response
    }

    private suspend fun post(url: String, headers: Map<String, String>, body: String): String {
        val response = try {
            http.postJson(url, headers, body)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            throw SubtitleTranslationException(SubtitleTranslationException.Code.Network, error.message ?: "Network request failed")
        }
        if (response.status !in 200..299) {
            throw classifySubtitleTranslationFailure(response.status, response.body, response.retryAfterSeconds)
        }
        return response.body
    }
}
