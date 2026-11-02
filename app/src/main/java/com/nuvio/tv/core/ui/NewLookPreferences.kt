package com.nuvio.tv.core.ui

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/** Where the main menu sits while the new look is on. */
enum class NewLookNavigation { SIDE_ICONS, TOP_TABS }

data class NewLookSettings(
    val navigation: NewLookNavigation = NewLookNavigation.SIDE_ICONS,
    /** Side icons only appear when asked for (left at the edge, or Back). */
    val hideSideBar: Boolean = false
)

/**
 * How the app's menu is shown. Device-local, read as Compose state so it changes at once.
 */
object NewLookPreferences {
    private const val PREFS_NAME = "nuvio_new_look"
    private const val KEY_NAVIGATION = "navigation"
    private const val KEY_HIDE_SIDE_BAR = "hide_side_bar"

    @Volatile private var appContext: Context? = null
    val current = mutableStateOf(NewLookSettings())

    fun attach(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        current.value = NewLookSettings(
            navigation = prefs.getString(KEY_NAVIGATION, null)
                ?.let { name -> runCatching { NewLookNavigation.valueOf(name) }.getOrNull() }
                ?: NewLookNavigation.SIDE_ICONS,
            hideSideBar = prefs.getBoolean(KEY_HIDE_SIDE_BAR, false)
        )
    }

    fun update(transform: (NewLookSettings) -> NewLookSettings) {
        val next = transform(current.value)
        current.value = next
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)?.edit()
            ?.putString(KEY_NAVIGATION, next.navigation.name)
            ?.putBoolean(KEY_HIDE_SIDE_BAR, next.hideSideBar)
            ?.apply()
    }
}
