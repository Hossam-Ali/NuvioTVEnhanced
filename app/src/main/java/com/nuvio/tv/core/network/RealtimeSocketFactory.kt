package com.nuvio.tv.core.network

import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.realtime.RealtimeMessage
import io.github.jan.supabase.realtime.RealtimeProtocolVersion
import io.github.jan.supabase.realtime.websocket.RealtimeWebsocket
import io.github.jan.supabase.realtime.websocket.RealtimeWebsocketFactory
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@OptIn(SupabaseInternal::class)
internal class RealtimeSocketFactory(
    private val connect: suspend (String) -> RealtimeWebsocket,
) : RealtimeWebsocketFactory {
    @Volatile
    private var unansweredHeartbeat: String? = null

    /** The protocol the client last spoke, so a repeated heartbeat is encoded the same way. */
    @Volatile
    private var lastVersion: RealtimeProtocolVersion = RealtimeProtocolVersion.V1

    override suspend fun create(url: String): RealtimeWebsocket {
        val socket = Socket(connect(url))
        unansweredHeartbeat?.let { ref ->
            socket.send(RealtimeMessage(PHOENIX, HEARTBEAT, JsonObject(emptyMap()), ref), lastVersion)
        }
        return socket
    }

    private inner class Socket(private val socket: RealtimeWebsocket) : RealtimeWebsocket by socket {
        override suspend fun send(message: RealtimeMessage, vsn: RealtimeProtocolVersion) {
            lastVersion = vsn
            if (message.topic == PHOENIX && message.event == HEARTBEAT) unansweredHeartbeat = message.ref
            socket.send(message, vsn)
        }

        override suspend fun receive(): Frame {
            val frame = socket.receive()
            val pending = unansweredHeartbeat
            if (pending != null && frame is Frame.Text) {
                val (topic, ref) = phoenixTopicAndRef(frame.readText()) ?: return frame
                if (topic == PHOENIX && ref == pending) unansweredHeartbeat = null
            }
            return frame
        }
    }

    companion object {
        private const val PHOENIX = "phoenix"
        private const val HEARTBEAT = "heartbeat"

        /**
         * The topic and ref of a text frame in either Realtime protocol: a JSON object in 1.0.0,
         * a `[joinRef, ref, topic, event, payload]` array in 2.0.0. Null when it is neither.
         */
        internal fun phoenixTopicAndRef(text: String): Pair<String?, String?>? = runCatching {
            fun JsonPrimitive?.text(): String? = this?.takeIf { it.isString }?.content
            when (val element = Json.parseToJsonElement(text)) {
                is JsonObject -> (element["topic"] as? JsonPrimitive).text() to (element["ref"] as? JsonPrimitive).text()
                is JsonArray -> (element.getOrNull(2) as? JsonPrimitive).text() to (element.getOrNull(1) as? JsonPrimitive).text()
                else -> null
            }
        }.getOrNull()
    }
}
