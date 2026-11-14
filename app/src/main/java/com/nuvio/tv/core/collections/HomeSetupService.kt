package com.nuvio.tv.core.collections

import com.nuvio.tv.core.poster.HomeRowStylePreferences
import com.nuvio.tv.core.poster.PosterRatingsPreferences
import com.nuvio.tv.core.sync.HomeCatalogSettingsSyncService
import com.nuvio.tv.data.local.CollectionsDataStore
import com.nuvio.tv.data.local.LayoutPreferenceDataStore
import com.nuvio.tv.domain.model.HomeLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

internal const val HOME_SETUP_FORMAT = "nuvio-home-setup"
private const val HOME_SETUP_VERSION = 1

/** What an import changed, for the message shown afterwards. */
data class HomeSetupImportResult(val collections: Int, val rows: Int)

/**
 * A whole home setup as one JSON file, to share or move between devices: layout and its
 * switches, row order, hidden and renamed rows, hero catalogs, collections, row card styles and
 * poster rating badges. Rows refer to add-ons by id, so they apply where the same add-ons are
 * installed; others are kept for when they are.
 */
@Singleton
class HomeSetupService @Inject constructor(
    private val layout: LayoutPreferenceDataStore,
    private val collections: CollectionsDataStore,
    private val homeCatalogSync: HomeCatalogSettingsSyncService,
) {
    private val http by lazy { OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build() }

    suspend fun export(): String {
        val layoutJson = JSONObject()
            .put("layout", layout.selectedLayout.first().name)
            .put("heroSection", layout.heroSectionEnabled.first())
            .put("continueWatching", layout.continueWatchingEnabled.first())
            .put("landscapePosters", layout.modernLandscapePostersEnabled.first())
            .put("fullScreenHeroBackdrop", layout.modernHeroFullScreenBackdropEnabled.first())
            .put("posterLabels", layout.posterLabelsEnabled.first())
            .put("catalogAddonName", layout.catalogAddonNameEnabled.first())
            .put("catalogTypeSuffix", layout.catalogTypeSuffixEnabled.first())
            .put("followAddonsOrder", layout.followAddonsOrder.first())
            .put("posterCardWidthDp", layout.posterCardWidthDp.first())
            .put("posterCardHeightDp", layout.posterCardHeightDp.first())
            .put("posterCardCornerRadiusDp", layout.posterCardCornerRadiusDp.first())
            .put("heroCatalogs", JSONArray(layout.heroCatalogSelections.first()))
            .put("rowOrder", JSONArray(layout.homeCatalogOrderKeys.first()))
            .put("hiddenRows", JSONArray(layout.disabledHomeCatalogKeys.first()))
            .put("rowTitles", JSONObject(layout.customCatalogTitles.first()))
        return JSONObject()
            .put("format", HOME_SETUP_FORMAT)
            .put("version", HOME_SETUP_VERSION)
            .put("home", layoutJson)
            .put("rowStyles", JSONObject(HomeRowStylePreferences.encode(HomeRowStylePreferences.styles.value)))
            .put("recentlyAddedRows", JSONArray(com.nuvio.tv.core.poster.RecentlyAddedPreferences.encode(com.nuvio.tv.core.poster.RecentlyAddedPreferences.rowKeys.value)))
            .put("newestFirstRows", JSONArray(com.nuvio.tv.core.poster.RecentlyAddedPreferences.encode(com.nuvio.tv.core.poster.NewestFirstRowsPreferences.rowKeys.value)))
            .put("posterRatings", PosterRatingsPreferences.encode(PosterRatingsPreferences.settings.value))
            .put("collections", JSONArray(collections.exportToJson(collections.getCurrentCollections())))
            .toString(2)
    }

    suspend fun importFromUrl(url: String): HomeSetupImportResult {
        val body = withContext(Dispatchers.IO) {
            http.newCall(Request.Builder().url(url.trim()).build()).execute().use { response ->
                check(response.isSuccessful) { "HTTP ${response.code}" }
                response.body.string()
            }
        }
        return import(body)
    }

    /** Applies a setup file; parts it doesn't have are left as they are. Throws on a file that isn't one. */
    suspend fun import(json: String): HomeSetupImportResult {
        val root = JSONObject(json.trim())
        require(root.optString("format") == HOME_SETUP_FORMAT) { "Not a Nuvio home setup" }
        var rows = 0
        root.optJSONObject("home")?.let { home ->
            home.optString("layout").takeIf { it.isNotBlank() }?.let { name ->
                runCatching { HomeLayout.valueOf(name) }.getOrNull()?.let { layout.setLayout(it) }
            }
            home.optBooleanOrNull("heroSection")?.let { layout.setHeroSectionEnabled(it) }
            home.optBooleanOrNull("continueWatching")?.let { layout.setContinueWatchingEnabled(it) }
            home.optBooleanOrNull("landscapePosters")?.let { layout.setModernLandscapePostersEnabled(it) }
            home.optBooleanOrNull("fullScreenHeroBackdrop")?.let { layout.setModernHeroFullScreenBackdropEnabled(it) }
            home.optBooleanOrNull("posterLabels")?.let { layout.setPosterLabelsEnabled(it) }
            home.optBooleanOrNull("catalogAddonName")?.let { layout.setCatalogAddonNameEnabled(it) }
            home.optBooleanOrNull("catalogTypeSuffix")?.let { layout.setCatalogTypeSuffixEnabled(it) }
            home.optBooleanOrNull("followAddonsOrder")?.let { layout.setFollowAddonsOrder(it) }
            home.optIntOrNull("posterCardWidthDp")?.let { layout.setPosterCardWidthDp(it) }
            home.optIntOrNull("posterCardHeightDp")?.let { layout.setPosterCardHeightDp(it) }
            home.optIntOrNull("posterCardCornerRadiusDp")?.let { layout.setPosterCardCornerRadiusDp(it) }
            home.optStringList("heroCatalogs")?.let { layout.setHeroCatalogKeys(it) }
            home.optStringList("rowOrder")?.let { layout.setHomeCatalogOrderKeys(it); rows = it.size }
            home.optStringList("hiddenRows")?.let { layout.setDisabledHomeCatalogKeys(it) }
            home.optJSONObject("rowTitles")?.let { titles ->
                layout.setCustomCatalogTitles(titles.keys().asSequence().associateWith { titles.getString(it) })
            }
        }
        root.optJSONObject("rowStyles")?.let { HomeRowStylePreferences.replaceAll(HomeRowStylePreferences.decode(it.toString())) }
        root.optJSONArray("recentlyAddedRows")?.let {
            com.nuvio.tv.core.poster.RecentlyAddedPreferences.replaceAll(com.nuvio.tv.core.poster.RecentlyAddedPreferences.decode(it.toString()))
        }
        root.optJSONArray("newestFirstRows")?.let {
            com.nuvio.tv.core.poster.NewestFirstRowsPreferences.replaceAll(com.nuvio.tv.core.poster.RecentlyAddedPreferences.decode(it.toString()))
        }
        root.optJSONObject("posterRatings")?.let { saved ->
            val decoded = PosterRatingsPreferences.decode(saved)
            PosterRatingsPreferences.update { decoded }
        }
        var importedCollections = 0
        root.optJSONArray("collections")?.let { array ->
            val incoming = collections.importFromJson(array.toString())
            if (incoming.isNotEmpty()) {
                // Collections with the same id are replaced; the others stay.
                val incomingIds = incoming.map { it.id }.toSet()
                val kept = collections.getCurrentCollections().filterNot { it.id in incomingIds }
                collections.setCollections(kept + incoming)
                importedCollections = incoming.size
            }
        }
        homeCatalogSync.triggerPush()
        return HomeSetupImportResult(collections = importedCollections, rows = rows)
    }
}

private fun JSONObject.optBooleanOrNull(key: String): Boolean? = if (has(key) && !isNull(key)) optBoolean(key) else null
private fun JSONObject.optIntOrNull(key: String): Int? = if (has(key) && !isNull(key)) optInt(key) else null
private fun JSONObject.optStringList(key: String): List<String>? =
    optJSONArray(key)?.let { array -> (0 until array.length()).map { array.getString(it) } }
