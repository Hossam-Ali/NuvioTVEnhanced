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
    private const val KEY_PERIOD = "period_days"

    /** The periods the home editor offers, in days. */
    val PERIOD_CHOICES = listOf(1, 7, 14, 30)
    const val DEFAULT_PERIOD_DAYS = 7

    @Volatile private var appContext: Context? = null
    private val _rowKeys = MutableStateFlow<Set<String>>(emptySet())
    val rowKeys: StateFlow<Set<String>> = _rowKeys.asStateFlow()

    private val _periodDays = MutableStateFlow(DEFAULT_PERIOD_DAYS)
    /** How many days back "Recently added" looks: titles out within this many days count. */
    val periodDays: StateFlow<Int> = _periodDays.asStateFlow()

    fun attach(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getString(KEY, null)?.let { _rowKeys.value = decode(it) }
        _periodDays.value = normalizePeriod(prefs.getInt(KEY_PERIOD, DEFAULT_PERIOD_DAYS))
    }

    fun toggle(rowKey: String) {
        val current = _rowKeys.value
        replaceAll(if (rowKey in current) current - rowKey else current + rowKey)
    }

    fun replaceAll(rowKeys: Set<String>) {
        _rowKeys.value = rowKeys
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)?.edit()?.putString(KEY, encode(rowKeys))?.apply()
    }

    fun setPeriodDays(days: Int) {
        val period = normalizePeriod(days)
        _periodDays.value = period
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)?.edit()?.putInt(KEY_PERIOD, period)?.apply()
    }

    /** One of [PERIOD_CHOICES]; anything else falls back to the default. */
    fun normalizePeriod(days: Int): Int = if (days in PERIOD_CHOICES) days else DEFAULT_PERIOD_DAYS

    fun encode(rowKeys: Set<String>): String = JSONArray(rowKeys.toList()).toString()

    fun decode(raw: String): Set<String> = runCatching {
        val json = JSONArray(raw)
        (0 until json.length()).mapNotNull { index -> json.optString(index).takeIf { it.isNotBlank() } }.toSet()
    }.getOrDefault(emptySet())
}
