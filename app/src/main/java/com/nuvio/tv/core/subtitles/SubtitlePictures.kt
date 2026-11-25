package com.nuvio.tv.core.subtitles

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import java.io.ByteArrayOutputStream
import java.lang.ref.WeakReference
import java.security.MessageDigest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Blu-ray subtitles (PGS) are pictures of text. Each one is sent to the AI, which reads and
// translates it; the translation is then kept under the picture's key like any other line.

private const val PICTURE_KEY_PREFIX = "nuvio-picture:"

/** Whether a line to translate is a subtitle picture, see [SubtitlePictures.keyOf]. */
internal fun isSubtitlePictureKey(source: String): Boolean = source.startsWith(PICTURE_KEY_PREFIX)

internal fun subtitlePictureKey(hash: ByteArray): String =
    PICTURE_KEY_PREFIX + hash.take(16).joinToString("") { "%02x".format(it) }

/** One subtitle picture ready to send, as a data URL. */
internal class SubtitlePicture(val jpeg: ByteArray)

/** The pictures waiting to be read, by key; the least recently added ones are dropped first. */
internal object SubtitlePictures {
    private const val MAX_PICTURES = 250
    private const val MAX_WIDTH = 1024
    private const val MAX_HEIGHT = 384
    private const val PADDING = 12
    private const val JPEG_QUALITY = 88

    private val lock = Any()
    private val pictures = LinkedHashMap<String, SubtitlePicture>()
    private var lastKey: Pair<WeakReference<Bitmap>, String>? = null

    /** A key for the picture's content, the same for every copy of it; null for an empty picture. */
    fun keyOf(bitmap: Bitmap): String? {
        synchronized(lock) { lastKey?.takeIf { it.first.get() === bitmap }?.let { return it.second } }
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return null
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update("${bitmap.width}x${bitmap.height}".toByteArray())
        val row = IntArray(bitmap.width)
        val bytes = ByteArray(bitmap.width * 4)
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            for (x in row.indices) {
                val pixel = row[x]
                val at = x * 4
                bytes[at] = (pixel ushr 24).toByte()
                bytes[at + 1] = (pixel ushr 16).toByte()
                bytes[at + 2] = (pixel ushr 8).toByte()
                bytes[at + 3] = pixel.toByte()
            }
            digest.update(bytes)
        }
        val key = subtitlePictureKey(digest.digest())
        synchronized(lock) { lastKey = WeakReference(bitmap) to key }
        return key
    }

    /** Keeps [bitmap] ready to be sent under [key]. Takes a few milliseconds: not for the main thread. */
    fun offer(key: String, bitmap: Bitmap) {
        synchronized(lock) { if (key in pictures) return }
        val picture = runCatching { SubtitlePicture(encode(bitmap)) }.getOrNull() ?: return
        synchronized(lock) {
            pictures[key] = picture
            while (pictures.size > MAX_PICTURES) pictures.remove(pictures.keys.first())
        }
    }

    fun get(key: String): SubtitlePicture? = synchronized(lock) { pictures[key] }

    internal fun put(key: String, picture: SubtitlePicture) = synchronized(lock) { pictures[key] = picture }

    /** The text alone, on black, no larger than it needs to be read. */
    private fun encode(source: Bitmap): ByteArray {
        val bitmap = if (source.config == Bitmap.Config.HARDWARE) source.copy(Bitmap.Config.ARGB_8888, false) else source
        val text = visibleBounds(bitmap)
        val scale = minOf(1f, MAX_WIDTH.toFloat() / text.width(), MAX_HEIGHT.toFloat() / text.height())
        val width = (text.width() * scale).toInt().coerceAtLeast(1)
        val height = (text.height() * scale).toInt().coerceAtLeast(1)
        val page = Bitmap.createBitmap(width + PADDING * 2, height + PADDING * 2, Bitmap.Config.ARGB_8888)
        Canvas(page).apply {
            drawColor(Color.BLACK)
            drawBitmap(
                bitmap, text, Rect(PADDING, PADDING, PADDING + width, PADDING + height),
                android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG),
            )
        }
        return ByteArrayOutputStream().also { page.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }.toByteArray()
            .also { page.recycle() }
    }

    private fun visibleBounds(bitmap: Bitmap): Rect {
        val row = IntArray(bitmap.width)
        val bounds = Rect(bitmap.width, bitmap.height, 0, 0)
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            for (x in row.indices) {
                if (row[x] ushr 24 < 32) continue
                if (x < bounds.left) bounds.left = x
                if (x >= bounds.right) bounds.right = x + 1
                if (y < bounds.top) bounds.top = y
                bounds.bottom = y + 1
            }
        }
        return if (bounds.isEmpty) Rect(0, 0, bitmap.width, bitmap.height) else bounds
    }
}

/** The system prompt for [pictures read][buildSubtitlePictureMessage] into [targetLanguage]. */
internal fun buildSubtitlePicturePrompt(targetLanguage: String): String {
    val languageName = languageNameForPrompt(targetLanguage)
    val styleHint = if (targetLanguage.lowercase().startsWith("ar")) {
        " Use Modern Standard Arabic as used in professional subtitles."
    } else {
        ""
    }
    return "You read film and TV subtitles from pictures and translate them into $languageName.$styleHint " +
        "Each picture is one subtitle shown on screen, in the order shown, and is preceded by its id. " +
        "Read its text exactly, then translate it. " +
        "Reply with JSON only, in the form {\"t\":[{\"i\":<id>,\"x\":\"<translation>\"}]}, with exactly one entry for every id. " +
        "Never merge, split, skip or reorder entries, and never return the original text. " +
        "Keep translations short enough to read quickly, keep the line breaks of the picture as \\n, " +
        "keep names and numbers, and translate sound descriptions such as [music] naturally."
}

/** The user message: every picture after its id. */
internal fun buildSubtitlePictureMessage(pictures: List<SubtitlePicture>, encode: (ByteArray) -> String): JsonArray =
    buildJsonArray {
        pictures.forEachIndexed { index, picture ->
            add(buildJsonObject { put("type", "text"); put("text", "id $index") })
            add(
                buildJsonObject {
                    put("type", "image_url")
                    put("image_url", buildJsonObject { put("url", "data:image/jpeg;base64," + encode(picture.jpeg)) })
                }
            )
        }
    }
