package com.nuvio.tv.core.subtitles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioTranscriptionTest {
    @Test
    fun `replies become lines inside the clip, in order, without overlaps`() {
        val reply = """```json
            {"s":[{"a":4.1,"b":6.2,"x":"كان الزحام كابوساً،\\nأقسم لك."},
                  {"a":0.0,"b":1.3,"x":"مهلاً يا سارة، انتظري."},
                  {"a":3.0,"b":4.5,"x":"لقد تأخرتِ مرة أخرى."},
                  {"a":7.0,"b":6.0,"x":"backwards"},
                  {"a":8.0,"b":9.0,"x":"  "},
                  {"a":9.5,"b":40.0,"x":"past the end"}]}
            ```"""
        val lines = parseTranscriptionResponse(reply, clipMs = 10_000)
        assertEquals(listOf(0L, 3_000L, 4_100L, 9_500L), lines.map { it.startMs })
        assertEquals(4_100L, lines[1].endMs)
        assertEquals("كان الزحام كابوساً،\nأقسم لك.", lines[2].text)
        assertEquals(10_000L, lines[3].endMs)
    }

    @Test
    fun `unreadable replies give no lines`() {
        assertTrue(parseTranscriptionResponse("sorry, I can't", 10_000).isEmpty())
        assertTrue(parseTranscriptionResponse("{\"s\":[]}", 10_000).isEmpty())
    }

    @Test
    fun `wav files have a valid 16 kHz mono header`() {
        val wav = pcm16ToWav(shortArrayOf(1, -2, 3))
        val header = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(wav, 0, 4))
        assertEquals("WAVE", String(wav, 8, 4))
        assertEquals(1, header.getShort(22).toInt())
        assertEquals(16_000, header.getInt(24))
        assertEquals(6, header.getInt(40))
        assertEquals(50, wav.size)
        assertEquals(-2, header.getShort(46).toInt())
    }

    @Test
    fun `a clip is cut in the quietest moment`() {
        val samples = ShortArray(1_000) { if (it in 600..700) 0 else 10_000 }
        val cut = quietestCutSample(samples, 400, 1_000, window = 50)
        assertTrue(cut in 600..700)
    }

    @Test
    fun `stereo 48 kHz becomes mono 16 kHz`() {
        val sink = ShortSink()
        val frames = 4_800
        val input = FloatArray(frames * 2) { if (it % 2 == 0) 0.5f else 0.0f }
        MonoDownsampler(48_000).add(input, frames, channels = 2, out = sink)
        assertEquals(1_600, sink.size)
        assertEquals((0.25 * Short.MAX_VALUE).toInt().toShort(), sink.data[10])
    }

    @Test
    fun `requests send the audio with the listening model and its fallback`() {
        val body = buildTranscriptionRequestBody(pcm16ToWav(ShortArray(16)), clipMs = 1_000, targetLanguage = "ar")
        assertTrue(body.contains("\"input_audio\""))
        assertTrue(body.contains(TRANSCRIPTION_MODEL) && body.contains(TRANSCRIPTION_FALLBACK_MODEL))
        assertTrue(body.contains("Arabic"))
    }

    @Test
    fun `lines written in the spoken language instead of the target are spotted`() {
        assertTrue(isWrittenInTargetScript("مرحبا يا سارة", "ar"))
        assertTrue(isWrittenInTargetScript("خسرنا حساب Henderson", "ar"))
        assertTrue(!isWrittenInTargetScript("Привет, как дела?", "ar"))
        assertTrue(!isWrittenInTargetScript("Hello there", "ar"))
        // Latin-script targets can't be told apart by script, so nothing is dropped.
        assertTrue(isWrittenInTargetScript("Привет", "en"))
        assertTrue(isWrittenInTargetScript("...", "ar"))
    }
}
