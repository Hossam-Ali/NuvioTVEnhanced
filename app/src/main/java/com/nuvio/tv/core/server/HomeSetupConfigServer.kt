package com.nuvio.tv.core.server

import com.nuvio.tv.core.collections.HomeSetupService
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.nio.charset.StandardCharsets

/**
 * Lets a phone on the same network download the TV's home setup file, or send one to it
 * (pasted or uploaded), from a small page. Runs only while its QR code is on screen.
 */
class HomeSetupConfigServer(
    private val service: HomeSetupService,
    private val onImported: () -> Unit,
    port: Int = 8095
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response = when {
        session.method == Method.GET && session.uri == "/" ->
            newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", HomeSetupWebPage.HTML)
        session.method == Method.GET && session.uri == "/api/setup" -> serveSetup(download = session.parameters.containsKey("download"))
        session.method == Method.POST && session.uri == "/api/setup" -> importSetup(session)
        else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
    }

    private fun serveSetup(download: Boolean): Response {
        val json = runBlocking { service.export() }
        return newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", json).apply {
            if (download) addHeader("Content-Disposition", "attachment; filename=\"nuvio-home-setup.json\"")
        }
    }

    private fun importSetup(session: IHTTPSession): Response {
        val body = readUtf8Body(session)
        return runCatching { runBlocking { service.import(body) } }.fold(
            onSuccess = { result ->
                onImported()
                json(Response.Status.OK, JSONObject().put("status", "imported").put("collections", result.collections).put("rows", result.rows))
            },
            onFailure = { error -> json(Response.Status.BAD_REQUEST, JSONObject().put("error", error.message ?: "Invalid setup file")) }
        )
    }

    private fun json(status: Response.Status, body: JSONObject): Response =
        newFixedLengthResponse(status, "application/json; charset=utf-8", body.toString())

    private fun readUtf8Body(session: IHTTPSession): String {
        val length = session.headers["content-length"]?.toIntOrNull() ?: return ""
        if (length <= 0 || length > MAX_BODY_BYTES) return ""
        val buffer = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = session.inputStream.read(buffer, offset, length - offset)
            if (read <= 0) break
            offset += read
        }
        return String(buffer, 0, offset, StandardCharsets.UTF_8)
    }

    companion object {
        private const val MAX_BODY_BYTES = 5 * 1024 * 1024

        fun startOnAvailablePort(service: HomeSetupService, onImported: () -> Unit, startPort: Int = 8095, maxAttempts: Int = 10): HomeSetupConfigServer? {
            for (port in startPort until startPort + maxAttempts) {
                try {
                    return HomeSetupConfigServer(service, onImported, port).also { it.start(SOCKET_READ_TIMEOUT, false) }
                } catch (_: Exception) {
                }
            }
            return null
        }
    }
}
