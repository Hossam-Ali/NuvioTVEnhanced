package com.nuvio.tv.core.network

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How long add-on requests (catalogs, metadata, streams, subtitles) may take. Read on every
 * request by the add-on HTTP client, so a change applies at once. Device-local, not synced.
 */
object AddonNetworkPreferences {
    const val DEFAULT_TIMEOUT_SECONDS = 60
    val TIMEOUT_CHOICES = listOf(10, 15, 20, 30, 45, 60, 90, 120)

    private const val PREFS_NAME = "nuvio_addon_network"
    private const val KEY_TIMEOUT = "timeout_seconds"

    @Volatile private var appContext: Context? = null
    private val _timeoutSeconds = MutableStateFlow(DEFAULT_TIMEOUT_SECONDS)
    val timeoutSeconds: StateFlow<Int> = _timeoutSeconds.asStateFlow()

    fun attach(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        val saved = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_TIMEOUT, DEFAULT_TIMEOUT_SECONDS)
        _timeoutSeconds.value = saved.coerceIn(TIMEOUT_CHOICES.first(), TIMEOUT_CHOICES.last())
    }

    fun setTimeoutSeconds(seconds: Int) {
        val value = seconds.coerceIn(TIMEOUT_CHOICES.first(), TIMEOUT_CHOICES.last())
        _timeoutSeconds.value = value
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)?.edit()?.putInt(KEY_TIMEOUT, value)?.apply()
    }

    /** Connecting never needs more than half a minute, however long the add-on may take to reply. */
    fun connectTimeoutSeconds(timeout: Int = timeoutSeconds.value): Int = minOf(timeout, 30)
}
