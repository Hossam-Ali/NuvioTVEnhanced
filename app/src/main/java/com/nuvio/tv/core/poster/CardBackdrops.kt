package com.nuvio.tv.core.poster

import android.content.Context
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.core.tmdb.TmdbMetadataService
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.data.local.TmdbSettingsDataStore
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.repository.MetaRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/** What a landscape card should show for a title whose add-on art isn't wide. */
sealed interface CardArt {
    data class Wide(val url: String) : CardArt
    /** Nothing wide exists: the card shows the poster. */
    data object None : CardArt
}

/**
 * Wide background art for titles whose add-on only gives a poster, so landscape cards can show a
 * real picture instead of a cropped poster. Looked up on TMDB (or the add-on) once per title and
 * kept on the device, so a card shows the right picture from its first frame on later visits.
 */
@Singleton
class CardBackdropRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tmdbService: TmdbService,
    private val tmdbMetadataService: TmdbMetadataService,
    private val tmdbSettings: TmdbSettingsDataStore,
    private val metaRepository: MetaRepository
) {
    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private val known: ConcurrentHashMap<String, CardArt> by lazy { loadKnown() }
    private val inFlight = ConcurrentHashMap<String, Deferred<String?>>()
    private val lookups = Semaphore(MAX_PARALLEL_LOOKUPS)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Read what's saved before the first card asks, off the main thread.
        scope.launch { known.size }
    }

    /** The answer from an earlier lookup, or null when this title hasn't been looked up yet. */
    fun known(id: String): CardArt? = known[id]

    suspend fun backdrop(id: String, apiType: String): String? {
        when (val art = known[id]) {
            is CardArt.Wide -> return art.url
            CardArt.None -> return null
            null -> Unit
        }
        // One lookup per title, however many cards and prefetches ask for it at once.
        val request = inFlight.computeIfAbsent(id) {
            scope.async {
                lookups.withPermit { fromTmdb(id, apiType) ?: fromAddons(id, apiType) }
                    .also { url -> remember(id, url) }
            }
        }
        return try {
            request.await()
        } finally {
            inFlight.remove(id, request)
        }
    }

    /**
     * Looks titles up ahead of time, so their cards don't change picture as they scroll in.
     * [onFound] gets each wide picture found, to download it ahead too.
     */
    fun prefetch(titles: List<Pair<String, String>>, onFound: (String) -> Unit = {}) {
        titles.forEach { (id, apiType) ->
            if (known[id] == null) scope.launch { backdrop(id, apiType)?.let(onFound) }
        }
    }

    private fun remember(id: String, url: String?) {
        known[id] = url?.let { CardArt.Wide(it) } ?: CardArt.None
        val editor = prefs.edit()
        if (known.size > MAX_REMEMBERED) editor.clear()
        editor.putString(id, url ?: NONE_MARKER).apply()
    }

    private fun loadKnown(): ConcurrentHashMap<String, CardArt> {
        val map = ConcurrentHashMap<String, CardArt>()
        prefs.all.forEach { (id, value) ->
            val url = value as? String ?: return@forEach
            map[id] = if (url == NONE_MARKER) CardArt.None else CardArt.Wide(url)
        }
        return map
    }

    private suspend fun fromTmdb(id: String, apiType: String): String? = runCatching {
        val tmdbId = tmdbService.ensureTmdbId(id, apiType) ?: return@runCatching null
        val type = if (apiType.equals("movie", ignoreCase = true)) ContentType.MOVIE else ContentType.SERIES
        tmdbMetadataService.fetchEnrichment(tmdbId, type, tmdbSettings.settings.value.language)?.backdrop
    }.getOrNull()?.takeIf { it.isNotBlank() }

    /** Titles TMDB doesn't know (add-on ids) take the background their add-on gives. */
    private suspend fun fromAddons(id: String, apiType: String): String? {
        metaRepository.getCachedMeta(apiType, id)?.backdropUrl?.takeIf { it.isNotBlank() }?.let { return it }
        return withTimeoutOrNull(ADDON_LOOKUP_TIMEOUT_MS) {
            metaRepository.getMetaFromAllAddons(apiType, id)
                .firstOrNull { it is NetworkResult.Success || it is NetworkResult.Error }
                ?.let { it as? NetworkResult.Success }
                ?.data?.backdropUrl?.takeIf { it.isNotBlank() }
        }
    }

    private companion object {
        const val ADDON_LOOKUP_TIMEOUT_MS = 8_000L
        const val PREFS_NAME = "nuvio_card_backdrops"
        const val NONE_MARKER = "-"
        const val MAX_REMEMBERED = 5_000
        const val MAX_PARALLEL_LOOKUPS = 4
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CardBackdropEntryPoint {
    fun cardBackdropRepository(): CardBackdropRepository
}
