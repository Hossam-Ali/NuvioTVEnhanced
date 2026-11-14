package com.nuvio.tv.core.network

import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.realtime.RealtimeMessage
import io.github.jan.supabase.realtime.RealtimeProtocolVersion
import io.github.jan.supabase.realtime.websocket.RealtimeWebsocket
import io.ktor.websocket.Frame
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(SupabaseInternal::class)
class RealtimeSocketFactoryTest {
    private class FakeSocket : RealtimeWebsocket {
        val sent = mutableListOf<Pair<RealtimeMessage, RealtimeProtocolVersion>>()
        val incoming = Channel<Frame>(Channel.UNLIMITED)
        override val hasIncomingMessages = true
        override fun makeRef() = "0"
        override suspend fun receive() = incoming.receive()
        override suspend fun send(message: RealtimeMessage, vsn: RealtimeProtocolVersion) {
            sent += message to vsn
        }
        override suspend fun send(data: ByteArray) = Unit
        override suspend fun blockUntilDisconnect() = Unit
        override fun disconnect() = Unit
    }

    private fun heartbeat(ref: String) = RealtimeMessage("phoenix", "heartbeat", JsonObject(emptyMap()), ref)

    private fun v1Reply(ref: String) = Frame.Text("""{"topic":"phoenix","event":"phx_reply","payload":{},"ref":"$ref"}""")

    private fun v2Reply(ref: String) = Frame.Text("""[null,"$ref","phoenix","phx_reply",{}]""")

    @Test
    fun replaysHeartbeatLostWithPreviousSocket() = runTest {
        val sockets = mutableListOf<FakeSocket>()
        val factory = RealtimeSocketFactory { FakeSocket().also(sockets::add) }

        factory.create("wss://test").send(heartbeat("7"), RealtimeProtocolVersion.V2)
        factory.create("wss://test")

        // Repeated in the protocol the client was speaking.
        assertEquals(listOf(heartbeat("7") to RealtimeProtocolVersion.V2), sockets[1].sent)
    }

    @Test
    fun skipsReplayAfterHeartbeatReply() = runTest {
        val sockets = mutableListOf<FakeSocket>()
        val factory = RealtimeSocketFactory { FakeSocket().also(sockets::add) }

        val first = factory.create("wss://test")
        first.send(heartbeat("7"), RealtimeProtocolVersion.V1)
        sockets[0].incoming.send(v1Reply("7"))
        first.receive()
        factory.create("wss://test")

        assertTrue(sockets[1].sent.isEmpty())
    }

    @Test
    fun recognisesHeartbeatRepliesInBothProtocols() {
        assertEquals("phoenix" to "7", RealtimeSocketFactory.phoenixTopicAndRef(v1Reply("7").let { String(it.data) }))
        assertEquals("phoenix" to "9", RealtimeSocketFactory.phoenixTopicAndRef(v2Reply("9").let { String(it.data) }))
        assertNull(RealtimeSocketFactory.phoenixTopicAndRef("not json"))
    }

    @Test
    fun realtimeUrlOptsIntoTheServerRoute() {
        assertEquals("wss://api.example.com/realtime/v1?nuvio_realtime=1", realtimeUrl("https://api.example.com/"))
        assertEquals("ws://192.168.1.5:8000/realtime/v1?nuvio_realtime=1", realtimeUrl("http://192.168.1.5:8000"))
    }
}
