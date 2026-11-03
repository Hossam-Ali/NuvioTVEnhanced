package com.nuvio.tv.core.subtitles

import android.util.Log
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * Keeps translated lines on the device, one file per provider, model and target language, so
 * resuming or rewatching shows them at once instead of asking the provider again. Only subtitle
 * text is stored, never the API key.
 */
internal class SubtitleTranslationDiskCache(private val file: File) {
    fun load(): Map<String, String> = runCatching {
        if (!file.isFile) return emptyMap()
        Json.parseToJsonElement(file.readText()).let { it as? JsonObject }
            ?.mapValues { (_, value) -> value.jsonPrimitive.content }
            .orEmpty()
    }.onFailure { Log.w(TAG, "Could not read saved translations: ${it.javaClass.simpleName}") }.getOrDefault(emptyMap())

    fun save(entries: Map<String, String>) {
        runCatching {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(JsonObject(entries.mapValues { JsonPrimitive(it.value) }).toString())
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
        }.onFailure { Log.w(TAG, "Could not save translations: ${it.javaClass.simpleName}") }
    }

    companion object {
        private const val TAG = "SubtitleTranslationCache"

        fun forTranslator(directory: File, provider: SubtitleTranslationProvider, model: String, target: String): SubtitleTranslationDiskCache {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest("${provider.name}|$model|$target".toByteArray(Charsets.UTF_8))
                .take(12)
                .joinToString("") { "%02x".format(it) }
            return SubtitleTranslationDiskCache(File(directory, "$digest.json"))
        }
    }
}
