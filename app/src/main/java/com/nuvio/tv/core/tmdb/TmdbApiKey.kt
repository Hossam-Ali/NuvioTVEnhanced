package com.nuvio.tv.core.tmdb

import android.content.Context
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.core.subtitles.SubtitleTranslationSecretStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The TMDB key requests use: the one entered on the TV, or else the build's (TMDB_API_KEY).
 * Public releases ship without a key, so their users add their own free key in Settings. It is
 * encrypted with an Android Keystore key and never synced.
 */
object TmdbApiKey {
    private const val PREFS_NAME = "nuvio_tv_tmdb_key"
    private const val KEY_USER_KEY = "user_key_encrypted"

    @Volatile private var appContext: Context? = null
    private val _userKey = MutableStateFlow<String?>(null)

    /** The key entered on the TV, or "" when none was. */
    val userKey: StateFlow<String?> = _userKey.asStateFlow()

    val hasBuiltInKey: Boolean get() = BuildConfig.TMDB_API_KEY.isNotBlank()

    fun attach(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    /** The key to send with TMDB requests, "" when there is none. */
    fun current(): String = loadedUserKey().ifBlank { BuildConfig.TMDB_API_KEY }

    fun isAvailable(): Boolean = current().isNotBlank()

    private fun loadedUserKey(): String {
        _userKey.value?.let { return it }
        val context = appContext ?: return ""
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_USER_KEY, null)
        return SubtitleTranslationSecretStore.decrypt(stored).also { _userKey.value = it }
    }

    /** Saves [key] encrypted, or forgets it when blank. Returns false if it could not be encrypted. */
    fun setUserKey(context: Context, key: String): Boolean {
        attach(context)
        val trimmed = key.filterNot(Char::isWhitespace)
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (trimmed.isEmpty()) {
            prefs.edit().remove(KEY_USER_KEY).apply()
        } else {
            val encrypted = SubtitleTranslationSecretStore.encrypt(trimmed) ?: return false
            prefs.edit().putString(KEY_USER_KEY, encrypted).apply()
        }
        _userKey.value = trimmed
        return true
    }
}
