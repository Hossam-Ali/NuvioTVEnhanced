package com.nuvio.tv.core.subtitles

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SubtitleTranslationSettingsTest {
    private val settings = SubtitleTranslationSettings(enabled = true)
        .withApiKey("sk-or-v1-secret-value")

    @Test
    fun apiKeysAreNeverSerializedWithTheOtherSettings() {
        val encoded = Json.encodeToString(SubtitleTranslationSettings.serializer(), settings)
        assertFalse("sk-or-v1-secret-value" in encoded)
        assertFalse("ApiKey" in encoded)
    }

    @Test
    fun toStringNeverPrintsTheKey() {
        assertFalse("sk-or-v1-secret-value" in settings.toString())
    }

    @Test
    fun removedGeminiProviderFallsBackToOpenRouter() {
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val decoded = json.decodeFromString(
            SubtitleTranslationSettings.serializer(),
            """{"enabled":true,"provider":"Gemini","geminiApiKey":"old","geminiModel":"gemini-flash-latest"}""",
        )
        assertEquals(SubtitleTranslationProvider.OpenRouter, decoded.provider)
        assertEquals("inclusionai/ling-3.0-flash", decoded.model)
        assertEquals("", decoded.apiKey)
    }
}
