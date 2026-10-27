package com.nuvio.tv.core.subtitles

import android.content.Context
import android.os.SystemClock
import com.nuvio.tv.ui.screens.player.PlayerSubtitleUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal sealed interface SubtitleTranslationEvent {
    /** New translations are cached; subtitles on screen can be redrawn. */
    data object Translated : SubtitleTranslationEvent

    data class Started(val provider: SubtitleTranslationProvider, val targetLanguage: String) : SubtitleTranslationEvent

    data class Failed(val provider: SubtitleTranslationProvider, val model: String, val code: SubtitleTranslationException.Code) :
        SubtitleTranslationEvent
}

internal class OkHttpSubtitleTranslationHttp(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build(),
) : SubtitleTranslationHttp {
    override suspend fun postJson(url: String, headers: Map<String, String>, body: String): SubtitleTranslationHttpResponse {
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Accept", "application/json")
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .build()
        val call = client.newCall(request)
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val text = runCatching { it.body?.string().orEmpty() }.getOrDefault("")
                        val retryAfter = it.header("Retry-After")?.trim()?.toLongOrNull()
                        if (continuation.isActive) continuation.resume(SubtitleTranslationHttpResponse(it.code, text, retryAfter))
                    }
                }
            })
        }
    }
}

/**
 * Process-wide coordinator: keeps one translator per configuration so its cache survives player
 * rebuilds and episodes, batches lines queued while ExoPlayer buffers ahead, and reports results.
 */
internal object SubtitleTranslationService {
    // ExoPlayer reads 15-45 s ahead in bursts. The first lines after a start or seek go out at
    // once; later ones are gathered briefly, so each burst becomes one request instead of a
    // request per line (which is what ran into the provider's rate limit).
    private const val QUEUE_FIRST_FLUSH_DELAY_MS = 300L
    private const val QUEUE_FLUSH_DELAY_MS = 2_500L
    private const val QUEUE_IDLE_MS = 20_000L
    private const val QUEUE_FLUSH_SIZE = 20
    private const val MAX_AUTOMATIC_RETRIES = 3
    private const val DISK_CACHE_DIRECTORY = "subtitle-translations"
    private const val DISK_CACHE_SAVE_DELAY_MS = 5_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val http: SubtitleTranslationHttp by lazy { OkHttpSubtitleTranslationHttp() }
    private val _events = MutableSharedFlow<SubtitleTranslationEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<SubtitleTranslationEvent> = _events.asSharedFlow()

    private var translator: SubtitleTranslator? = null
    private var translatorSignature: List<String> = emptyList()
    private val queued = LinkedHashSet<String>()
    private var queuedTranslator: SubtitleTranslator? = null
    private var flushScheduled = false
    private var lastFlushAtMs = 0L
    private var saveScheduled = false
    private var reportedFailure: Pair<SubtitleTranslationProvider, SubtitleTranslationException.Code>? = null
    private var announcedKey: String? = null

    @Volatile private var appContext: Context? = null

    /** The player's resolved preferred subtitle language, used when the target is "preferred". */
    @Volatile
    var preferredSubtitleLanguage: String? = null

    /** Language of the subtitle track the player currently shows, or null when unknown. */
    @Volatile
    var selectedTrackLanguage: String? = null

    fun attach(context: Context) {
        if (appContext == null) appContext = context.applicationContext
        SubtitleTranslationPreferences.ensureLoaded(context)
    }

    private fun currentSettings(): SubtitleTranslationSettings? {
        val context = appContext ?: return null
        SubtitleTranslationPreferences.ensureLoaded(context)
        return SubtitleTranslationPreferences.settings.value
    }

    /** True when translation is switched on with a key and a target language. */
    fun isConfigured(): Boolean {
        val settings = currentSettings() ?: return false
        return settings.enabled && settings.apiKey.isNotBlank() && resolveTargetLanguage(settings) != null
    }

    /** The language subtitles are translated into, or null when translation is off. */
    fun translationTargetLanguage(): String? {
        val settings = currentSettings() ?: return null
        if (!settings.enabled || settings.apiKey.isBlank()) return null
        return resolveTargetLanguage(settings)
    }

    fun shouldPreferEmbeddedSubtitles(): Boolean =
        isConfigured() && currentSettings()?.preferEmbeddedSubtitles == true

    fun resolveTargetLanguage(settings: SubtitleTranslationSettings, preferred: String? = preferredSubtitleLanguage): String? {
        val configured = if (settings.targetLanguage == SUBTITLE_TRANSLATION_TARGET_PREFERRED) preferred else settings.targetLanguage
        val resolved = when (configured?.trim()?.lowercase()) {
            null, "", "none", "off", "forced" -> null
            "device", "system" -> Locale.getDefault().language
            else -> configured
        }
        return resolved?.let(PlayerSubtitleUtils::normalizeLanguageCode)?.takeIf { it.isNotBlank() }
    }

    /** Returns the translator for the selected track, or null when it should be shown as it is. */
    fun activeTranslator(trackLanguage: String? = selectedTrackLanguage): SubtitleTranslator? {
        val settings = currentSettings() ?: return null
        if (!settings.enabled || settings.apiKey.isBlank()) return null
        val target = resolveTargetLanguage(settings) ?: return null
        if (!trackLanguage.isNullOrBlank() && PlayerSubtitleUtils.matchesLanguageCode(trackLanguage, target)) return null
        val signature = listOf(settings.provider.name, settings.apiKey, settings.model, target)
        val current = synchronized(lock) {
            translator?.takeIf { translatorSignature == signature }
                ?: SubtitleTranslator(settings.provider, settings.apiKey, settings.model, target, http).also {
                    translator = it
                    translatorSignature = signature
                    reportedFailure = null
                    restoreSaved(it)
                }
        }
        // A rejected key or unknown model stays unusable until the settings change.
        return current.takeIf { it.fatalError == null }
    }

    /** Queues a line seen while buffering; lines are sent in small batches. */
    fun prefetch(text: String) {
        val active = activeTranslator() ?: return
        if (active.cached(text) != null) return
        synchronized(lock) {
            if (queuedTranslator !== active) {
                queued.clear()
                queuedTranslator = active
            }
            if (!queued.add(text)) return
            if (queued.size >= QUEUE_FLUSH_SIZE) {
                flushLocked(active)
                return
            }
            if (flushScheduled) return
            flushScheduled = true
        }
        val idle = SystemClock.elapsedRealtime() - lastFlushAtMs > QUEUE_IDLE_MS
        scope.launch {
            delay(if (idle) QUEUE_FIRST_FLUSH_DELAY_MS else QUEUE_FLUSH_DELAY_MS)
            synchronized(lock) {
                flushScheduled = false
                flushLocked(active)
            }
        }
    }

    private fun diskCache(active: SubtitleTranslator): SubtitleTranslationDiskCache? {
        val directory = appContext?.cacheDir?.let { java.io.File(it, DISK_CACHE_DIRECTORY) } ?: return null
        return SubtitleTranslationDiskCache.forTranslator(directory, active.provider, active.model, active.targetLanguage)
    }

    /** Loads translations saved on the device off the main thread, then redraws. */
    private fun restoreSaved(active: SubtitleTranslator) {
        val cache = diskCache(active) ?: return
        scope.launch {
            val saved = cache.load()
            if (saved.isEmpty()) return@launch
            active.restore(saved)
            _events.tryEmit(SubtitleTranslationEvent.Translated)
        }
    }

    /** Saves new translations a few seconds after they arrive, once per burst. */
    private fun scheduleSave(active: SubtitleTranslator) {
        val shouldSchedule = synchronized(lock) { (!saveScheduled).also { saveScheduled = true } }
        if (!shouldSchedule) return
        scope.launch {
            delay(DISK_CACHE_SAVE_DELAY_MS)
            synchronized(lock) { saveScheduled = false }
            diskCache(active)?.save(active.snapshot())
        }
    }

    private fun flushLocked(active: SubtitleTranslator) {
        if (queuedTranslator !== active || queued.isEmpty()) return
        val batch = queued.toList()
        queued.clear()
        lastFlushAtMs = SystemClock.elapsedRealtime()
        translate(active, batch)
    }

    /** Translates lines that are about to be shown; the screen is redrawn when they arrive. */
    fun translate(active: SubtitleTranslator, texts: Collection<String>, attempt: Int = 0) {
        if (texts.isEmpty()) return
        announce(active)
        scope.launch {
            try {
                active.translateTexts(texts)
                _events.tryEmit(SubtitleTranslationEvent.Translated)
                scheduleSave(active)
            } catch (error: SubtitleTranslationException) {
                // No redraw here: a redraw asks for the missing lines again, which would loop.
                report(active, error)
                // Lines buffered ahead are sent again once the wait is over, so they are ready
                // before they are shown instead of being asked for one by one on screen.
                val retryable = error.code == SubtitleTranslationException.Code.Rate ||
                    error.code == SubtitleTranslationException.Code.Network ||
                    error.code == SubtitleTranslationException.Code.Format
                if (retryable && attempt < MAX_AUTOMATIC_RETRIES) {
                    delay(active.retryDelayMs() + 250L)
                    if (activeTranslator() === active) translate(active, texts, attempt + 1)
                }
            }
        }
    }

    private fun announce(active: SubtitleTranslator) {
        val key = "${active.provider}|${active.targetLanguage}|${active.model}"
        val shouldAnnounce = synchronized(lock) {
            (announcedKey != key).also { if (it) announcedKey = key }
        }
        if (shouldAnnounce) _events.tryEmit(SubtitleTranslationEvent.Started(active.provider, active.targetLanguage))
    }

    private fun report(active: SubtitleTranslator, error: SubtitleTranslationException) {
        if (error.code == SubtitleTranslationException.Code.Retry || error.code == SubtitleTranslationException.Code.Config) return
        val key = active.provider to error.code
        val shouldReport = synchronized(lock) {
            (reportedFailure != key).also { if (it) reportedFailure = key }
        }
        if (shouldReport) _events.tryEmit(SubtitleTranslationEvent.Failed(active.provider, active.model, error.code))
    }

    /** One-off request for the settings screen; never reuses a translator that saw a rejected key. */
    suspend fun testTranslation(settings: SubtitleTranslationSettings, preferredLanguage: String?, sample: String): Result<String> {
        val target = resolveTargetLanguage(settings, preferredLanguage) ?: "ar"
        val tester = SubtitleTranslator(settings.provider, settings.apiKey, settings.model, target, http)
        return runCatching {
            tester.translateTexts(listOf(sample))
            tester.cached(sample).orEmpty()
        }
    }
}
