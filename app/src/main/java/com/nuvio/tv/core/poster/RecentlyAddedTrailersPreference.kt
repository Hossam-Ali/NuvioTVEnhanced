package com.nuvio.tv.core.poster

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * With Autoplay Trailer on, whether trailers play only for titles in the "Recently added" row
 * rather than in every row. Device-local, like [HomeRowStylePreferences]; off by default.
 */
object RecentlyAddedTrailersPreference {
    private const val PREFS_NAME = "nuvio_recently_added_trailers"
    private const val KEY = "only"

    @Volatile private var appContext: Context? = null
    private val _onlyRecentlyAdded = MutableStateFlow(false)
    val onlyRecentlyAdded: StateFlow<Boolean> = _onlyRecentlyAdded.asStateFlow()

    fun attach(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        _onlyRecentlyAdded.value = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY, false)
    }

    fun set(onlyRecentlyAdded: Boolean) {
        _onlyRecentlyAdded.value = onlyRecentlyAdded
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)?.edit()?.putBoolean(KEY, onlyRecentlyAdded)?.apply()
    }
}
