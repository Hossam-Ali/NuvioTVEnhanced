package com.nuvio.tv.core.poster

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/**
 * Home rows sorted newest first (by release date) instead of in their add-on's order, set per row
 * in the home editor and keyed like the catalog order. Device-local, like [HomeRowStylePreferences].
 */
object NewestFirstRowsPreferences {
    private const val PREFS_NAME = "nuvio_newest_first_rows"
    private const val KEY = "rows"

    @Volatile private var appContext: Context? = null
    private val _rowKeys = MutableStateFlow<Set<String>>(emptySet())
    val rowKeys: StateFlow<Set<String>> = _rowKeys.asStateFlow()

    fun attach(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY, null)
            ?.let { _rowKeys.value = RecentlyAddedPreferences.decode(it) }
    }

    fun toggle(rowKey: String) {
        val current = _rowKeys.value
        replaceAll(if (rowKey in current) current - rowKey else current + rowKey)
    }

    fun replaceAll(rowKeys: Set<String>) {
        _rowKeys.value = rowKeys
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)?.edit()
            ?.putString(KEY, RecentlyAddedPreferences.encode(rowKeys))?.apply()
    }
}
