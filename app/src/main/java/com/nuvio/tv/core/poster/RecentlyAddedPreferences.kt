package com.nuvio.tv.core.poster

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/**
 * The home rows that feed the "Recently added" row, picked in the home editor and keyed like the
 * catalog order (`addon_type_catalog`). Device-local, like [HomeRowStylePreferences]; with none
 * picked there is no "Recently added" row.
 */
object RecentlyAddedPreferences {
    private const val PREFS_NAME = "nuvio_recently_added"
    private const val KEY = "rows"

    @Volatile private var appContext: Context? = null
    private val _rowKeys = MutableStateFlow<Set<String>>(emptySet())
    val rowKeys: StateFlow<Set<String>> = _rowKeys.asStateFlow()

    fun attach(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        val raw = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY, null)
        raw?.let { _rowKeys.value = decode(it) }
    }

    fun toggle(rowKey: String) {
        val current = _rowKeys.value
        replaceAll(if (rowKey in current) current - rowKey else current + rowKey)
    }

    fun replaceAll(rowKeys: Set<String>) {
        _rowKeys.value = rowKeys
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)?.edit()?.putString(KEY, encode(rowKeys))?.apply()
    }

    fun encode(rowKeys: Set<String>): String = JSONArray(rowKeys.toList()).toString()

    fun decode(raw: String): Set<String> = runCatching {
        val json = JSONArray(raw)
        (0 until json.length()).mapNotNull { index -> json.optString(index).takeIf { it.isNotBlank() } }.toSet()
    }.getOrDefault(emptySet())
}
