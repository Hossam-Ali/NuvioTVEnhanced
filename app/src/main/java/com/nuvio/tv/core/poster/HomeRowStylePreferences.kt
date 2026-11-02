package com.nuvio.tv.core.poster

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** How one home row draws its cards; rows without a style follow the layout's setting. */
enum class HomeRowStyle { POSTER, LANDSCAPE }

/**
 * Card style per home row, set in the home editor, keyed like the catalog order
 * (`addon_type_catalog`). Device-local; applies to the Modern home.
 */
object HomeRowStylePreferences {
    private const val PREFS_NAME = "nuvio_home_row_styles"
    private const val KEY = "styles"

    @Volatile private var appContext: Context? = null
    private val _styles = MutableStateFlow<Map<String, HomeRowStyle>>(emptyMap())
    val styles: StateFlow<Map<String, HomeRowStyle>> = _styles.asStateFlow()

    fun attach(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        val raw = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY, null)
        raw?.let { _styles.value = decode(it) }
    }

    fun setStyle(rowKey: String, style: HomeRowStyle?) {
        replaceAll(if (style == null) _styles.value - rowKey else _styles.value + (rowKey to style))
    }

    fun replaceAll(styles: Map<String, HomeRowStyle>) {
        _styles.value = styles
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)?.edit()?.putString(KEY, encode(styles))?.apply()
    }

    fun encode(styles: Map<String, HomeRowStyle>): String = JSONObject(styles.mapValues { it.value.name }).toString()

    fun decode(raw: String): Map<String, HomeRowStyle> = runCatching {
        val json = JSONObject(raw)
        json.keys().asSequence().mapNotNull { key ->
            runCatching { HomeRowStyle.valueOf(json.getString(key)) }.getOrNull()?.let { key to it }
        }.toMap()
    }.getOrDefault(emptyMap())
}

/** Whether a row draws landscape cards: its own style, else the layout's [globalLandscape]. */
fun Map<String, HomeRowStyle>.landscapeFor(rowKey: String, globalLandscape: Boolean): Boolean = when (this[rowKey]) {
    HomeRowStyle.LANDSCAPE -> true
    HomeRowStyle.POSTER -> false
    null -> globalLandscape
}
