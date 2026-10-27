package com.nuvio.tv.core.subtitles

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** "preferred" follows the preferred subtitle language from the playback settings. */
const val SUBTITLE_TRANSLATION_TARGET_PREFERRED = "preferred"

@Serializable
data class SubtitleTranslationSettings(
    val enabled: Boolean = false,
    val provider: SubtitleTranslationProvider = SubtitleTranslationProvider.OpenRouter,
    // API keys are never serialized with the other settings; they are stored encrypted.
    @Transient val openRouterApiKey: String = "",
    @Transient val openAiApiKey: String = "",
    val openRouterModel: String = "",
    val openAiModel: String = "",
    val targetLanguage: String = SUBTITLE_TRANSLATION_TARGET_PREFERRED,
    /** Pick the video's embedded subtitle track and translate it instead of add-on subtitles. */
    val preferEmbeddedSubtitles: Boolean = true,
) {
    val apiKey: String
        get() = when (provider) {
            SubtitleTranslationProvider.OpenRouter -> openRouterApiKey
            SubtitleTranslationProvider.OpenAi -> openAiApiKey
        }

    val configuredModel: String
        get() = when (provider) {
            SubtitleTranslationProvider.OpenRouter -> openRouterModel
            SubtitleTranslationProvider.OpenAi -> openAiModel
        }

    val model: String
        get() = configuredModel.ifBlank { provider.defaultModel }

    fun withApiKey(key: String): SubtitleTranslationSettings = when (provider) {
        SubtitleTranslationProvider.OpenRouter -> copy(openRouterApiKey = key)
        SubtitleTranslationProvider.OpenAi -> copy(openAiApiKey = key)
    }

    fun withModel(model: String): SubtitleTranslationSettings = when (provider) {
        SubtitleTranslationProvider.OpenRouter -> copy(openRouterModel = model)
        SubtitleTranslationProvider.OpenAi -> copy(openAiModel = model)
    }

    /** Never prints the keys, so the settings can be logged safely. */
    override fun toString(): String =
        "SubtitleTranslationSettings(enabled=$enabled, provider=$provider, model=$model, " +
            "targetLanguage=$targetLanguage, preferEmbeddedSubtitles=$preferEmbeddedSubtitles, " +
            "hasApiKey=${apiKey.isNotEmpty()})"
}

/**
 * Subtitle translation preferences, in their own preferences file outside
 * PlayerSettingsDataStore and account sync. API keys are encrypted with an Android Keystore key
 * ([SubtitleTranslationSecretStore]); only ciphertext is written to disk.
 */
object SubtitleTranslationPreferences {
    private const val PREFS_NAME = "nuvio_tv_subtitle_translation"
    private const val KEY_SETTINGS = "settings"
    private const val KEY_OPENROUTER_SECRET = "openrouter_api_key_encrypted"
    private const val KEY_OPENAI_SECRET = "openai_api_key_encrypted"

    // Unknown enum values (for example the removed Gemini provider) fall back to the defaults.
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val lock = Any()
    @Volatile private var loaded = false

    private val _settings = MutableStateFlow(SubtitleTranslationSettings())
    val settings: StateFlow<SubtitleTranslationSettings> = _settings.asStateFlow()

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val stored = prefs.getString(KEY_SETTINGS, null)
            val base = stored
                ?.let { runCatching { json.decodeFromString<SubtitleTranslationSettings>(it) }.getOrNull() }
                ?: SubtitleTranslationSettings()
            var openAiKey = SubtitleTranslationSecretStore.decrypt(prefs.getString(KEY_OPENAI_SECRET, null))
            // Earlier test builds kept keys in plain JSON: move the OpenAI key into encrypted
            // storage and drop every plaintext key from disk.
            val legacy = stored?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
            if (legacy != null && legacy.keys.any { it.endsWith("ApiKey") }) {
                if (openAiKey.isEmpty()) {
                    openAiKey = legacy["openAiApiKey"]?.jsonPrimitive?.content.orEmpty()
                }
                prefs.edit().putString(KEY_SETTINGS, json.encodeToString(SubtitleTranslationSettings.serializer(), base)).apply()
                if (openAiKey.isNotEmpty()) {
                    SubtitleTranslationSecretStore.encrypt(openAiKey)?.let { prefs.edit().putString(KEY_OPENAI_SECRET, it).apply() }
                }
            }
            _settings.value = base.copy(
                openRouterApiKey = SubtitleTranslationSecretStore.decrypt(prefs.getString(KEY_OPENROUTER_SECRET, null)),
                openAiApiKey = openAiKey,
            )
            loaded = true
        }
    }

    fun update(context: Context, transform: (SubtitleTranslationSettings) -> SubtitleTranslationSettings) {
        ensureLoaded(context)
        val previous = _settings.value
        val next = transform(previous).let { settings ->
            settings.copy(
                openRouterApiKey = settings.openRouterApiKey.filterNot(Char::isWhitespace),
                openAiApiKey = settings.openAiApiKey.filterNot(Char::isWhitespace),
                openRouterModel = settings.openRouterModel.trim(),
                openAiModel = settings.openAiModel.trim(),
            )
        }
        if (next == previous) return
        val editor = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_SETTINGS, json.encodeToString(SubtitleTranslationSettings.serializer(), next))
        val keysToStore = listOf(
            Triple(KEY_OPENROUTER_SECRET, previous.openRouterApiKey, next.openRouterApiKey),
            Triple(KEY_OPENAI_SECRET, previous.openAiApiKey, next.openAiApiKey),
        )
        var stored = next
        keysToStore.forEach { (prefKey, before, after) ->
            if (before == after) return@forEach
            if (after.isEmpty()) {
                editor.remove(prefKey)
            } else {
                val encrypted = SubtitleTranslationSecretStore.encrypt(after)
                if (encrypted != null) {
                    editor.putString(prefKey, encrypted)
                } else {
                    // Never fall back to plaintext: keep the previous key if encryption fails.
                    stored = if (prefKey == KEY_OPENROUTER_SECRET) stored.copy(openRouterApiKey = before)
                    else stored.copy(openAiApiKey = before)
                }
            }
        }
        editor.apply()
        _settings.value = stored
    }
}
