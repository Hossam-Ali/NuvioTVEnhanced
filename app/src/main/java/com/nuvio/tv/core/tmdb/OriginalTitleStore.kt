package com.nuvio.tv.core.tmdb

import android.content.Context
import com.nuvio.tv.domain.model.ContentType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** A title's original language and its name in that language, as TMDB has them. */
data class OriginalTitle(val language: String, val title: String)

/**
 * Titles' original names from TMDB, kept on the device. Some add-ons name every title in their
 * own language ("كروس" for Cross), and the Modern home names such titles by their original title
 * like its hero does; keeping the answers means a row shows the right names from its first frame
 * on later visits instead of switching after a lookup.
 */
@Singleton
class OriginalTitleStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tmdbService: TmdbService,
    private val tmdbMetadataService: TmdbMetadataService
) {
    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private val saved: ConcurrentHashMap<String, OriginalTitle> by lazy { load() }

    /** Titles TMDB couldn't name this session; not saved, so a failed request is tried again later. */
    private val unknown = ConcurrentHashMap.newKeySet<String>()
    private val lookups = Semaphore(MAX_PARALLEL_LOOKUPS)

    init {
        // Read what's saved before the first row asks, off the main thread.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { saved.size }
    }

    /** The saved answer for [id], without asking TMDB. */
    fun known(id: String): OriginalTitle? = saved[keyFor(id)]

    /** Whether [id] was already looked up, whatever the answer. */
    fun isLookedUp(id: String): Boolean = saved.containsKey(keyFor(id)) || keyFor(id) in unknown

    /** Asks TMDB for [id]'s original title (one details request), and keeps the answer. */
    suspend fun lookUp(id: String, apiType: String, type: ContentType): OriginalTitle? {
        val key = keyFor(id)
        saved[key]?.let { return it }
        if (key in unknown) return null
        val found = lookups.withPermit {
            try {
                val tmdbId = tmdbService.ensureTmdbId(id, apiType) ?: return@withPermit null
                tmdbMetadataService.fetchOriginalTitle(tmdbId, type)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
        if (found == null) {
            unknown += key
            return null
        }
        return OriginalTitle(language = found.first, title = found.second).also { save(key, it) }
    }

    private fun save(id: String, title: OriginalTitle) {
        saved[id] = title
        val editor = prefs.edit()
        if (saved.size > MAX_SAVED) editor.clear()
        editor.putString(id, title.language + SEPARATOR + title.title).apply()
    }

    private fun load(): ConcurrentHashMap<String, OriginalTitle> {
        val map = ConcurrentHashMap<String, OriginalTitle>()
        prefs.all.forEach { (id, value) ->
            val text = value as? String ?: return@forEach
            val language = text.substringBefore(SEPARATOR, missingDelimiterValue = "")
            val title = text.substringAfter(SEPARATOR, missingDelimiterValue = "")
            if (language.isNotBlank() && title.isNotBlank()) map[id] = OriginalTitle(language, title)
        }
        return map
    }

    /** Answers depend on the favourite subtitle language (see [DescriptionLanguage]), so they are kept per language. */
    private fun keyFor(id: String): String = "${DescriptionLanguage.favourite.orEmpty()}|$id"

    private companion object {
        const val PREFS_NAME = "nuvio_original_titles_v2"
        const val SEPARATOR = '\t'
        const val MAX_SAVED = 5_000
        const val MAX_PARALLEL_LOOKUPS = 4
    }
}
