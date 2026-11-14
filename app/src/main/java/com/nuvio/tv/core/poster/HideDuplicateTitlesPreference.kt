package com.nuvio.tv.core.poster

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the home screen shows each title once, in the first row that has it (from the top).
 * Device-local, like [HomeRowStylePreferences]; off by default.
 */
object HideDuplicateTitlesPreference {
    private const val PREFS_NAME = "nuvio_home_duplicates"
    private const val KEY = "hide"

    @Volatile private var appContext: Context? = null
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun attach(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        _enabled.value = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY, false)
    }

    fun set(enabled: Boolean) {
        _enabled.value = enabled
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)?.edit()?.putBoolean(KEY, enabled)?.apply()
    }
}
