package com.nuvio.tv.core.poster

import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.core.tmdb.TmdbMetadataService
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.data.local.TmdbSettingsDataStore
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.repository.MetaRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Wide background art for titles whose add-on only gives a poster, so landscape cards can show a
 * real picture instead of a cropped poster. Looked up on TMDB once per title and kept in memory.
 */
@Singleton
class CardBackdropRepository @Inject constructor(
    private val tmdbService: TmdbService,
    private val tmdbMetadataService: TmdbMetadataService,
    private val tmdbSettings: TmdbSettingsDataStore,
    private val metaRepository: MetaRepository
) {
    private val found = ConcurrentHashMap<String, String>()
    private val missing = ConcurrentHashMap.newKeySet<String>()

    fun cached(id: String): String? = found[id]

    suspend fun backdrop(id: String, apiType: String): String? {
        found[id]?.let { return it }
        if (id in missing) return null
        val url = fromTmdb(id, apiType) ?: fromAddons(id, apiType)
        if (url != null) found[id] = url else missing.add(id)
        return url
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
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CardBackdropEntryPoint {
    fun cardBackdropRepository(): CardBackdropRepository
}
