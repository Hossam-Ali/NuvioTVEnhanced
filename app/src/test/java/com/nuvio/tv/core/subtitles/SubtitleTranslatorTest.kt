package com.nuvio.tv.core.subtitles

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SubtitleTranslatorTest {
    private data class Call(val url: String, val headers: Map<String, String>, val body: String)

    private fun echoReply(userPrompt: String, skipIds: Set<Int> = emptySet()): String {
        val lines = Json.parseToJsonElement(userPrompt).jsonObject["lines"]!!.jsonArray
        return buildJsonObject {
            put("t", buildJsonArray {
                lines.forEach { line ->
                    val id = line.jsonObject["i"]!!.jsonPrimitive.int
                    if (id in skipIds) return@forEach
                    add(buildJsonObject {
                        put("i", id)
                        put("x", "AR:" + line.jsonObject["x"]!!.jsonPrimitive.content)
                    })
                }
            })
        }.toString()
    }

    private fun openRouterHttp(
        calls: MutableList<Call>,
        status: Int = 200,
        errorBody: String = "",
        delayMs: Long = 0,
        skipIds: Set<Int> = emptySet(),
    ) = SubtitleTranslationHttp { url, headers, body ->
        calls += Call(url, headers, body)
        if (delayMs > 0) delay(delayMs)
        if (status != 200) return@SubtitleTranslationHttp SubtitleTranslationHttpResponse(status, errorBody)
        val userPrompt = Json.parseToJsonElement(body).jsonObject["messages"]!!.jsonArray[1].jsonObject["content"]!!
            .jsonPrimitive.content
        val reply = buildJsonObject {
            put("choices", buildJsonArray {
                add(buildJsonObject {
                    put("message", buildJsonObject {
                        put("reasoning", "thinking about it")
                        put("content", echoReply(userPrompt, skipIds))
                    })
                })
            })
        }
        SubtitleTranslationHttpResponse(200, reply.toString())
    }

    private fun translator(http: SubtitleTranslationHttp, nowMs: () -> Long = { 0L }) = SubtitleTranslator(
        provider = SubtitleTranslationProvider.OpenRouter,
        apiKey = "sk-or-secret",
        model = "openai/gpt-6-luna",
        targetLanguage = "ar",
        http = http,
        nowMs = nowMs,
    )

    private suspend fun expectFailure(block: suspend () -> Unit): SubtitleTranslationException {
        try {
            block()
        } catch (error: SubtitleTranslationException) {
            return error
        }
        fail("expected a SubtitleTranslationException")
        throw IllegalStateException()
    }

    @Test
    fun promptNamesTheLanguageKeepsIdsAndAddsArabicStyle() {
        val prompt = buildSubtitleTranslationPrompt(listOf("Hello", "Run!\nNow."), "ar")
        assertTrue("into Arabic" in prompt.system)
        assertTrue("Modern Standard Arabic" in prompt.system)
        val lines = Json.parseToJsonElement(prompt.user).jsonObject["lines"]!!.jsonArray
        assertEquals("Run!\nNow.", lines[1].jsonObject["x"]!!.jsonPrimitive.content)
        assertEquals(1, lines[1].jsonObject["i"]!!.jsonPrimitive.int)
        assertFalse("Arabic" in buildSubtitleTranslationPrompt(listOf("Hi"), "fr").system)
    }

    @Test
    fun repliesAreParsedFromPlainFencedAndAlternativeShapes() {
        assertEquals(
            mapOf(0 to "مرحبا", 1 to "اركض\nالآن"),
            parseSubtitleTranslationResponse("{\"t\":[{\"i\":0,\"x\":\"مرحبا\"},{\"i\":1,\"x\":\"اركض\\nالآن\"}]}"),
        )
        assertEquals(mapOf(2 to "نعم"), parseSubtitleTranslationResponse("```json\n[{\"id\":2,\"text\":\"نعم\"}]\n```"))
        assertTrue(parseSubtitleTranslationResponse("not json").isEmpty())
    }

    @Test
    fun openRouterSendsTheKeyAsBearerWithReasoningAndCachesTranslations() = runTest {
        val calls = mutableListOf<Call>()
        val subject = translator(openRouterHttp(calls))
        subject.translateTexts(listOf("Hello", "Hello", "  Bye  "))
        assertEquals(1, calls.size)
        assertEquals("https://openrouter.ai/api/v1/chat/completions", calls[0].url)
        assertFalse("sk-or-secret" in calls[0].url)
        assertFalse("sk-or-secret" in calls[0].body)
        assertEquals("Bearer sk-or-secret", calls[0].headers["Authorization"])
        assertEquals("Nuvio TV", calls[0].headers["X-Title"])
        val request = Json.parseToJsonElement(calls[0].body).jsonObject
        assertEquals("openai/gpt-6-luna", request["model"]!!.jsonPrimitive.contentOrNull)
        assertEquals("true", request["reasoning"]!!.jsonObject["enabled"]!!.jsonPrimitive.content)
        assertEquals("json_object", request["response_format"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(
            "the other offered model takes over when this one is rate-limited",
            listOf("openai/gpt-6-luna", "inclusionai/ling-3.0-flash"),
            request["models"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertNull(request["temperature"])
        assertEquals("AR:Hello", subject.cached("Hello"))
        assertEquals("AR:Bye", subject.cached("Bye"))

        subject.translateTexts(listOf("Hello", "Bye"))
        assertEquals("cached lines are not sent again", 1, calls.size)
    }

    @Test
    fun openAiUsesBearerTokenJsonModeAndSkipsTemperatureOnReasoningModels() = runTest {
        val bodies = mutableListOf<Call>()
        val http = SubtitleTranslationHttp { url, headers, body ->
            bodies += Call(url, headers, body)
            val user = Json.parseToJsonElement(body).jsonObject["messages"]!!.jsonArray[1].jsonObject["content"]!!.jsonPrimitive.content
            val reply = buildJsonObject {
                put("choices", buildJsonArray {
                    add(buildJsonObject { put("message", buildJsonObject { put("content", echoReply(user)) }) })
                })
            }
            SubtitleTranslationHttpResponse(200, reply.toString())
        }
        SubtitleTranslator(SubtitleTranslationProvider.OpenAi, "sk-test", "gpt-4.1-mini", "ar", http).translateTexts(listOf("Hi"))
        SubtitleTranslator(SubtitleTranslationProvider.OpenAi, "sk-test", "gpt-5-mini", "ar", http).translateTexts(listOf("Hi"))
        assertEquals("https://api.openai.com/v1/chat/completions", bodies[0].url)
        assertEquals("Bearer sk-test", bodies[0].headers["Authorization"])
        val first = Json.parseToJsonElement(bodies[0].body).jsonObject
        assertEquals("json_object", first["response_format"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(0.2, first["temperature"]!!.jsonPrimitive.doubleOrNull!!, 0.0001)
        assertNull(Json.parseToJsonElement(bodies[1].body).jsonObject["temperature"])
    }

    @Test
    fun longBuffersAreBatched() = runTest {
        val calls = mutableListOf<Call>()
        val subject = translator(openRouterHttp(calls))
        subject.translateTexts((0 until 90).map { "Line $it" })
        assertEquals(5, calls.size)
        assertEquals("AR:Line 89", subject.cached("Line 89"))
    }

    @Test
    fun overlappingCallsShareOneRequest() = runTest {
        val calls = mutableListOf<Call>()
        val subject = translator(openRouterHttp(calls, delayMs = 50))
        listOf(
            async { subject.translateTexts(listOf("A", "B")) },
            async { subject.translateTexts(listOf("B", "A")) },
        ).awaitAll()
        assertEquals(1, calls.size)
        assertEquals("AR:B", subject.cached("B"))
    }

    @Test
    fun skippedLinesStayUntranslatedAndAreRetriedLater() = runTest {
        var clock = 0L
        val calls = mutableListOf<Call>()
        val subject = translator(openRouterHttp(calls, skipIds = setOf(1))) { clock }
        subject.translateTexts(listOf("First", "Second"))
        assertEquals("AR:First", subject.cached("First"))
        assertNull(subject.cached("Second"))
        subject.translateTexts(listOf("Second"))
        assertEquals("a skipped line is not resent on every redraw", 1, calls.size)
        clock += 31_000L
        subject.translateTexts(listOf("Second"))
        assertEquals(2, calls.size)
    }

    @Test
    fun aRejectedKeyStopsFurtherRequests() = runTest {
        val calls = mutableListOf<Call>()
        val subject = translator(
            openRouterHttp(calls, status = 400, errorBody = "{\"error\":{\"message\":\"API key not valid. Please pass a valid API key.\"}}"),
        )
        assertEquals(SubtitleTranslationException.Code.Auth, expectFailure { subject.translateTexts(listOf("Hello")) }.code)
        assertEquals(SubtitleTranslationException.Code.Auth, expectFailure { subject.translateTexts(listOf("Other")) }.code)
        assertEquals(1, calls.size)
    }

    @Test
    fun rateLimitsBackOffLongerEachTimeAndHonourRetryAfter() = runTest {
        var clock = 0L
        val http = SubtitleTranslationHttp { _, _, _ -> SubtitleTranslationHttpResponse(429, "", retryAfterSeconds = 12) }
        val subject = translator(http) { clock }
        assertEquals(SubtitleTranslationException.Code.Rate, expectFailure { subject.translateTexts(listOf("A")) }.code)
        assertEquals("the provider's Retry-After wins over the first 5 s wait", 12_000L, subject.retryDelayMs())
        clock += 12_000L
        expectFailure { subject.translateTexts(listOf("A")) }
        assertEquals(12_000L, subject.retryDelayMs())
        val noHeader = translator(SubtitleTranslationHttp { _, _, _ -> SubtitleTranslationHttpResponse(429, "") }) { clock }
        expectFailure { noHeader.translateTexts(listOf("B")) }
        assertEquals(5_000L, noHeader.retryDelayMs())
        clock += 5_000L
        expectFailure { noHeader.translateTexts(listOf("B")) }
        assertEquals("waits double after a second failure", 10_000L, noHeader.retryDelayMs())
    }

    @Test
    fun savedTranslationsAreRestoredWithoutOverwritingNewOnes() = runTest {
        val subject = translator(openRouterHttp(mutableListOf()))
        subject.translateTexts(listOf("Hello"))
        subject.restore(mapOf("Hello" to "old", "Bye" to "وداعا"))
        assertEquals("AR:Hello", subject.cached("Hello"))
        assertEquals("وداعا", subject.cached("Bye"))
        assertEquals(setOf("Hello", "Bye"), subject.snapshot().keys)
    }

    @Test
    fun temporaryFailuresWaitBeforeRetrying() = runTest {
        var clock = 1_000L
        var status = 429
        val calls = mutableListOf<Call>()
        val http = SubtitleTranslationHttp { url, headers, body ->
            if (status != 200) {
                calls += Call(url, headers, body)
                SubtitleTranslationHttpResponse(status, "{\"error\":{\"message\":\"Resource exhausted\"}}")
            } else {
                openRouterHttp(calls).postJson(url, headers, body)
            }
        }
        val subject = translator(http) { clock }
        assertEquals(SubtitleTranslationException.Code.Rate, expectFailure { subject.translateTexts(listOf("Hello")) }.code)
        assertEquals(SubtitleTranslationException.Code.Retry, expectFailure { subject.translateTexts(listOf("Hello")) }.code)
        assertEquals(1, calls.size)
        clock += 20_000L
        status = 200
        subject.translateTexts(listOf("Hello"))
        assertEquals("AR:Hello", subject.cached("Hello"))
    }

    @Test
    fun eachRequestReportsItsLinesAndTheCostOpenRouterSent() = runTest {
        val usages = mutableListOf<SubtitleTranslationUsage>()
        val http = SubtitleTranslationHttp { _, _, body ->
            val user = Json.parseToJsonElement(body).jsonObject["messages"]!!.jsonArray[1].jsonObject["content"]!!.jsonPrimitive.content
            val reply = buildJsonObject {
                put("choices", buildJsonArray {
                    add(buildJsonObject { put("message", buildJsonObject { put("content", echoReply(user)) }) })
                })
                put("usage", buildJsonObject { put("total_tokens", 120); put("cost", 0.00042) })
            }
            SubtitleTranslationHttpResponse(200, reply.toString())
        }
        SubtitleTranslator(SubtitleTranslationProvider.OpenRouter, "k", "m", "ar", http, onUsage = { usages += it })
            .translateTexts(listOf("One", "Two"))
        assertEquals(listOf(SubtitleTranslationUsage(lines = 2, tokens = 120, costUsd = 0.00042)), usages)
        assertNull(parseSubtitleTranslationUsage("{\"choices\":[]}", 1))
    }

    @Test
    fun providerFailuresAreClassified() {
        assertEquals(SubtitleTranslationException.Code.Auth, classifySubtitleTranslationFailure(401, "").code)
        assertEquals(SubtitleTranslationException.Code.Model, classifySubtitleTranslationFailure(404, "").code)
        assertEquals(SubtitleTranslationException.Code.Rate, classifySubtitleTranslationFailure(429, "").code)
        assertEquals(SubtitleTranslationException.Code.Network, classifySubtitleTranslationFailure(500, "").code)
        assertTrue(classifySubtitleTranslationFailure(400, "{\"error\":{\"message\":\"API key expired\"}}").isFatal)
    }
}
