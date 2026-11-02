package com.nuvio.tv.core.player

import android.content.Context
import androidx.annotation.StringRes
import androidx.media3.common.C
import com.nuvio.tv.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Formats that can be sent untouched to the TV or receiver over HDMI. */
enum class PassthroughFormat(@param:StringRes val titleRes: Int, val encodings: IntArray) {
    DOLBY_DIGITAL(R.string.audio_passthrough_dd, intArrayOf(C.ENCODING_AC3)),
    DOLBY_DIGITAL_PLUS(R.string.audio_passthrough_ddp, intArrayOf(C.ENCODING_E_AC3, C.ENCODING_E_AC3_JOC)),
    TRUEHD(R.string.audio_passthrough_truehd, intArrayOf(C.ENCODING_DOLBY_TRUEHD)),
    DTS(R.string.audio_passthrough_dts, intArrayOf(C.ENCODING_DTS)),
    DTS_HD(R.string.audio_passthrough_dts_hd, intArrayOf(C.ENCODING_DTS_HD, C.ENCODING_DTS_UHD_P2)),
}

/**
 * Formats the user doesn't want passed through, for TVs that claim a format they can't play.
 * Those are decoded by the app instead (FFmpeg for DTS). Device-local, like the HDMI setup.
 */
object AudioPassthroughPreferences {
    private const val PREFS_NAME = "nuvio_audio_passthrough"
    private const val KEY_DISABLED = "disabled_formats"

    @Volatile private var appContext: Context? = null
    private val _disabled = MutableStateFlow<Set<PassthroughFormat>>(emptySet())
    val disabled: StateFlow<Set<PassthroughFormat>> = _disabled.asStateFlow()

    fun attach(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        _disabled.value = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_DISABLED, emptySet()).orEmpty()
            .mapNotNull { name -> runCatching { PassthroughFormat.valueOf(name) }.getOrNull() }
            .toSet()
    }

    fun setDisabled(formats: Set<PassthroughFormat>) {
        _disabled.value = formats
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)?.edit()
            ?.putStringSet(KEY_DISABLED, formats.map { it.name }.toSet())?.apply()
    }

    fun toggle(format: PassthroughFormat) {
        val current = _disabled.value
        setDisabled(if (format in current) current - format else current + format)
    }

    /** The encodings left out of what the TV reports. */
    fun blockedEncodings(formats: Set<PassthroughFormat> = _disabled.value): Set<Int> =
        formats.flatMap { it.encodings.toList() }.toSet()
}
