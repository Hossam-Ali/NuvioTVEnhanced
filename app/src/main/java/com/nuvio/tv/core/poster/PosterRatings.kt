package com.nuvio.tv.core.poster

import android.content.Context
import com.nuvio.tv.data.repository.MDBListRepository
import com.nuvio.tv.domain.model.MDBListRatings
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Where rating badges sit on a poster. */
enum class PosterRatingPosition { TOP_START, TOP_END, BOTTOM_START, BOTTOM_END }

/** Rating providers a badge can show, in MDBList's naming. */
val POSTER_RATING_PROVIDERS = listOf("imdb", "tomatoes", "audience", "metacritic", "tmdb", "letterboxd", "trakt", "mal")

data class PosterRatingsSettings(
    val enabled: Boolean = false,
    /** Providers in the order they are tried; the first [maxBadges] with a rating are shown. */
    val providers: List<String> = listOf("imdb", "tomatoes", "tmdb"),
    val maxBadges: Int = 1,
    val position: PosterRatingPosition = PosterRatingPosition.TOP_END,
    /** Where the icon pack was loaded from, blank for the built-in icons. */
    val iconPackUrl: String = "",
    /** Provider to icon image URL, from the icon pack. */
    val iconPack: Map<String, String> = emptyMap(),
)

/**
 * Settings for rating badges on posters, device-local. An icon pack is a JSON file at a URL:
 * `{"name": "...", "icons": {"imdb": "https://…/imdb.png", "tomatoes": "…"}}` (a flat map works too).
 */
object PosterRatingsPreferences {
    private const val PREFS_NAME = "nuvio_poster_ratings"
    private const val KEY = "settings"

    @Volatile private var appContext: Context? = null
    private val _settings = MutableStateFlow(PosterRatingsSettings())
    val settings: StateFlow<PosterRatingsSettings> = _settings.asStateFlow()

    /**
     * The same settings as Compose state, for the cards: reading it from a card costs nothing,
     * where collecting the flow would start a coroutine for every card created while scrolling.
     */
    val current = androidx.compose.runtime.mutableStateOf(PosterRatingsSettings())
    private val http by lazy { OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build() }

    fun attach(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        val raw = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY, null)
        raw?.let { runCatching { _settings.value = decode(JSONObject(it)) } }
        current.value = _settings.value
    }

    fun update(transform: (PosterRatingsSettings) -> PosterRatingsSettings) {
        val next = transform(_settings.value).let { it.copy(maxBadges = it.maxBadges.coerceIn(1, 3)) }
        _settings.value = next
        current.value = next
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)?.edit()?.putString(KEY, encode(next).toString())?.apply()
    }

    /** Loads the icon pack at [url]; a blank URL goes back to the built-in icons. */
    suspend fun loadIconPack(url: String): Result<Int> = withContext(Dispatchers.IO) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) {
            update { it.copy(iconPackUrl = "", iconPack = emptyMap()) }
            return@withContext Result.success(0)
        }
        runCatching {
            val body = http.newCall(Request.Builder().url(trimmed).build()).execute().use { response ->
                check(response.isSuccessful) { "HTTP ${response.code}" }
                response.body.string()
            }
            val icons = parseIconPack(body)
            check(icons.isNotEmpty()) { "No icons in the pack" }
            update { it.copy(iconPackUrl = trimmed, iconPack = icons) }
            icons.size
        }
    }

    internal fun encode(settings: PosterRatingsSettings): JSONObject = JSONObject()
        .put("enabled", settings.enabled)
        .put("providers", JSONArray(settings.providers))
        .put("maxBadges", settings.maxBadges)
        .put("position", settings.position.name)
        .put("iconPackUrl", settings.iconPackUrl)
        .put("iconPack", JSONObject(settings.iconPack))

    internal fun decode(json: JSONObject): PosterRatingsSettings {
        val defaults = PosterRatingsSettings()
        val providers = json.optJSONArray("providers")?.let { array ->
            (0 until array.length()).map { array.getString(it) }.filter { it in POSTER_RATING_PROVIDERS }.distinct()
        }?.takeIf { it.isNotEmpty() } ?: defaults.providers
        val pack = json.optJSONObject("iconPack")?.let { obj -> obj.keys().asSequence().associateWith { obj.getString(it) } }.orEmpty()
        return PosterRatingsSettings(
            enabled = json.optBoolean("enabled", defaults.enabled),
            providers = providers,
            maxBadges = json.optInt("maxBadges", defaults.maxBadges).coerceIn(1, 3),
            position = runCatching { PosterRatingPosition.valueOf(json.optString("position")) }.getOrDefault(defaults.position),
            iconPackUrl = json.optString("iconPackUrl", ""),
            iconPack = pack,
        )
    }
}

/** Reads an icon pack: `{"icons": {...}}` or a flat provider-to-URL map; unknown providers are dropped. */
internal fun parseIconPack(body: String): Map<String, String> {
    val root = JSONObject(body)
    val icons = root.optJSONObject("icons") ?: root
    return icons.keys().asSequence()
        .map { it to it.lowercase().replace("rottentomatoes", "tomatoes").replace("rotten_tomatoes", "tomatoes").replace("myanimelist", "mal") }
        .filter { (_, provider) -> provider in POSTER_RATING_PROVIDERS }
        .mapNotNull { (key, provider) -> icons.optString(key).takeIf { it.startsWith("http") }?.let { provider to it } }
        .toMap()
}

/** The value of [provider] in [ratings]. */
fun MDBListRatings.valueOf(provider: String): Double? = when (provider) {
    "imdb" -> imdb
    "tomatoes" -> tomatoes
    "audience" -> audience
    "metacritic" -> metacritic
    "tmdb" -> tmdb
    "letterboxd" -> letterboxd
    "trakt" -> trakt
    "mal" -> mal
    else -> null
}

/** The badges to show: the first [PosterRatingsSettings.maxBadges] providers that have a rating. */
fun PosterRatingsSettings.badgesFor(ratings: MDBListRatings): List<Pair<String, Double>> =
    providers.mapNotNull { provider -> ratings.valueOf(provider)?.let { provider to it } }.take(maxBadges)

/** Ratings for posters: MDBList (batched and cached by its loader) when it is set up. */
@Singleton
class PosterRatingsRepository @Inject constructor(private val mdbList: MDBListRepository) {
    private val memory = ConcurrentHashMap<String, MDBListRatings>()
    private val missing = ConcurrentHashMap.newKeySet<String>()

    fun cached(id: String, type: String): MDBListRatings? = memory["$type:$id"]

    /** For the home hero: only the providers switched on in the MDBList settings. Not cached, so a change shows at once. */
    suspend fun enabledRatings(id: String, type: String): MDBListRatings? =
        runCatching { mdbList.getEnabledRatingsForItem(id, type) }.getOrNull()

    suspend fun ratings(id: String, type: String): MDBListRatings? {
        val key = "$type:$id"
        memory[key]?.let { return it }
        if (key in missing) return null
        val loaded = runCatching { mdbList.getAllRatingsForItem(id, type) }.getOrNull()
        if (loaded == null) missing += key else memory[key] = loaded
        return loaded
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PosterRatingsEntryPoint {
    fun posterRatingsRepository(): PosterRatingsRepository
    fun metaRepository(): com.nuvio.tv.domain.repository.MetaRepository
}
