package com.nuvio.tv.core.collections

import android.content.Context
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.core.tmdb.TmdbCollectionSourceResolver
import com.nuvio.tv.core.trakt.TraktPublicListSourceResolver
import com.nuvio.tv.data.local.CollectionsDataStore
import com.nuvio.tv.domain.model.AddonCatalogCollectionSource
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.CollectionFolder
import com.nuvio.tv.domain.model.TmdbCollectionSource
import com.nuvio.tv.domain.model.TraktCollectionSource
import com.nuvio.tv.domain.model.findCollectionCatalog
import com.nuvio.tv.domain.model.enabledAddons
import com.nuvio.tv.domain.repository.AddonRepository
import com.nuvio.tv.domain.repository.CatalogRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Posters a cover is made from. */
internal const val COLLECTION_COVER_POSTERS = 4
private const val COVER_TTL_MS = 24L * 60 * 60 * 1000
private const val PREFS_NAME = "nuvio_collection_covers"

/**
 * Covers for collection folders that have no picture: the first posters of the folder's first
 * source, kept for a day (in memory and on disk) so tiles don't wait for the network.
 */
@Singleton
class CollectionCoverResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val collectionsDataStore: CollectionsDataStore,
    private val addonRepository: AddonRepository,
    private val catalogRepository: CatalogRepository,
    private val tmdbResolver: TmdbCollectionSourceResolver,
    private val traktResolver: TraktPublicListSourceResolver,
) {
    private val memory = ConcurrentHashMap<String, List<String>>()
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    /** Saved posters for a folder, without loading anything. */
    fun cached(collectionId: String, folderId: String): List<String> {
        val key = key(collectionId, folderId)
        memory[key]?.let { return it }
        return readSaved(key, allowStale = true).orEmpty().also { if (it.isNotEmpty()) memory[key] = it }
    }

    suspend fun posters(collectionId: String, folderId: String): List<String> {
        val key = key(collectionId, folderId)
        readSaved(key, allowStale = false)?.let { return it.also { memory[key] = it } }
        return locks.getOrPut(key) { Mutex() }.withLock {
            readSaved(key, allowStale = false)?.let { return@withLock it }
            val folder = collectionsDataStore.collections.first()
                .firstOrNull { it.id == collectionId }?.folders?.firstOrNull { it.id == folderId }
                ?: return@withLock emptyList()
            val loaded = withTimeoutOrNull(20_000) { load(folder) }.orEmpty()
            if (loaded.isNotEmpty()) {
                memory[key] = loaded
                prefs.edit().putString(key, JSONObject().put("at", System.currentTimeMillis()).put("urls", JSONArray(loaded)).toString()).apply()
            }
            loaded.ifEmpty { cached(collectionId, folderId) }
        }
    }

    private suspend fun load(folder: CollectionFolder): List<String> {
        // Sources are tried in order until one gives posters.
        for (source in folder.sources) {
            val row = runCatching {
                when (source) {
                    is AddonCatalogCollectionSource -> addonRow(source)
                    is TmdbCollectionSource -> tmdbResolver.resolve(source, 1).firstSuccess()
                    is TraktCollectionSource -> traktResolver.resolve(source, 1).firstSuccess()
                }
            }.getOrNull()
            val posters = row?.items.orEmpty().mapNotNull { it.poster?.takeIf(String::isNotBlank) }.distinct().take(COLLECTION_COVER_POSTERS)
            if (posters.isNotEmpty()) return posters
        }
        return emptyList()
    }

    private suspend fun addonRow(source: AddonCatalogCollectionSource): CatalogRow? {
        val addons = addonRepository.getInstalledAddons().first().enabledAddons()
        val (addon, catalog) = addons.firstNotNullOfOrNull { addon ->
            addon.catalogs.findCollectionCatalog(source.type, source.catalogId)?.let { addon to it }
        } ?: return null
        val genre = source.genre?.takeIf { it.isNotBlank() && !it.equals("None", ignoreCase = true) }
        return catalogRepository.getCatalog(
            addonBaseUrl = addon.baseUrl,
            addonId = addon.id,
            addonName = addon.displayName,
            catalogId = source.catalogId,
            catalogName = catalog.name,
            type = catalog.apiType,
            skip = 0,
            skipStep = 100,
            extraArgs = genre?.let { mapOf("genre" to it) }.orEmpty(),
            supportsSkip = false,
            posterScreen = com.nuvio.tv.core.poster.CustomPosterScreen.COLLECTIONS
        ).firstSuccess()
    }

    private suspend fun Flow<NetworkResult<CatalogRow>>.firstSuccess(): CatalogRow? =
        first { it !is NetworkResult.Loading }.let { (it as? NetworkResult.Success)?.data }

    private fun readSaved(key: String, allowStale: Boolean): List<String>? {
        val raw = prefs.getString(key, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            if (!allowStale && System.currentTimeMillis() - json.getLong("at") > COVER_TTL_MS) return null
            val urls = json.getJSONArray("urls")
            (0 until urls.length()).map { urls.getString(it) }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    private fun key(collectionId: String, folderId: String) = "$collectionId::$folderId"
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CollectionCoverEntryPoint {
    fun collectionCoverResolver(): CollectionCoverResolver
}
