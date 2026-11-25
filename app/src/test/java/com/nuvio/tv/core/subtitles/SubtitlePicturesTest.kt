package com.nuvio.tv.core.subtitles

import java.util.Base64
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitlePicturesTest {

    private fun key(number: Int) = subtitlePictureKey(ByteArray(20) { number.toByte() })
        .also { SubtitlePictures.put(it, SubtitlePicture(byteArrayOf(number.toByte()))) }

    private fun reply(vararg lines: String) = SubtitleTranslationHttpResponse(
        200,
        buildJsonObject {
            put("choices", buildJsonArray {
                add(buildJsonObject {
                    put("message", buildJsonObject {
                        put("content", buildJsonObject {
                            put("t", buildJsonArray {
                                lines.forEachIndexed { i, x -> add(buildJsonObject { put("i", i); put("x", x) }) }
                            })
                        }.toString())
                    })
                })
            })
        }.toString(),
    )

    private fun translator(
        provider: SubtitleTranslationProvider = SubtitleTranslationProvider.OpenRouter,
        model: String = "someone/text-only",
        http: SubtitleTranslationHttp,
    ) = SubtitleTranslator(
        provider, "test-key", model, "ar", http,
        encodeBase64 = { Base64.getEncoder().encodeToString(it) },
    )

    @Test
    fun `pictures are sent in order after their ids and kept under their keys`() = runTest {
        val bodies = mutableListOf<JsonObject>()
        val first = key(1)
        val second = key(2)
        val translator = translator { _, _, body ->
            bodies += Json.parseToJsonElement(body).jsonObject
            reply("واحد", "اثنان")
        }
        translator.translateTexts(listOf(first, second))
        assertEquals("واحد", translator.cached(first))
        assertEquals("اثنان", translator.cached(second))

        val content = bodies.single()["messages"]!!.jsonArray[1].jsonObject["content"]!!.jsonArray
        assertEquals(listOf("text", "image_url", "text", "image_url"), content.map { it.jsonObject["type"]!!.jsonPrimitive.content })
        assertEquals("id 1", content[2].jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals(
            "data:image/jpeg;base64,AQ==",
            content[1].jsonObject["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `pictures use a model that reads pictures, whatever the user's model`() = runTest {
        val bodies = mutableListOf<JsonObject>()
        val picture = key(3)
        translator { _, _, body ->
            bodies += Json.parseToJsonElement(body).jsonObject
            reply("نص")
        }.translateTexts(listOf(picture))
        assertEquals(OPENROUTER_DEFAULT_MODEL, bodies.single()["model"]!!.jsonPrimitive.content)
        assertFalse(bodies.single()["models"].toString().contains("someone/text-only"))
    }

    @Test
    fun `text and pictures go in separate requests`() = runTest {
        val bodies = mutableListOf<String>()
        val picture = key(4)
        translator { _, _, body ->
            bodies += body
            reply("ترجمة")
        }.translateTexts(listOf("Hello", picture))
        assertEquals(2, bodies.size)
        assertEquals(1, bodies.count { "image_url" in it })
        assertTrue(bodies.first { "image_url" !in it }.contains("someone/text-only"))
    }

    @Test
    fun `a picture that is no longer kept isn't sent`() = runTest {
        var requests = 0
        val translator = translator { _, _, _ -> requests++; reply("نص") }
        val missing = subtitlePictureKey(ByteArray(20) { 99 })
        translator.translateTexts(listOf(missing))
        assertEquals(0, requests)
        assertNull(translator.cached(missing))
    }

    @Test
    fun `picture keys are told from subtitle lines`() {
        assertTrue(isSubtitlePictureKey(subtitlePictureKey(ByteArray(20))))
        assertFalse(isSubtitlePictureKey("A picture of you"))
        val key = subtitlePictureKey(ByteArray(20) { 7 })
        assertEquals(key, normalizeCueTextForTranslation(key))
    }
}
