package com.nuvio.tv.ui.screens.search

import com.nuvio.tv.core.tmdb.TmdbApiKey
import com.nuvio.tv.data.local.TmdbSettingsDataStore
import com.nuvio.tv.data.remote.api.TmdbApi
import com.nuvio.tv.data.remote.api.TmdbMultiSearchResult
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

data class TmdbSearchResults(val titles: List<MetaPreview>, val people: List<MetaPreview>) {
    companion object {
        val EMPTY = TmdbSearchResults(emptyList(), emptyList())
    }
}

/**
 * Searches TMDB, which knows titles in many languages and their original names, so a search in
 * Arabic or a show's own language finds it even when the addons only know English titles. Also
 * returns people for the actors row. Needs a TMDB key (entered on the TV or built in); without one it finds nothing.
 */
@Singleton
class TmdbSearchSource @Inject constructor(
    private val tmdbApi: TmdbApi,
    private val tmdbSettingsDataStore: TmdbSettingsDataStore,
) {
    suspend fun search(query: String): TmdbSearchResults = coroutineScope {
        val apiKey = TmdbApiKey.current()
        if (apiKey.isBlank()) return@coroutineScope TmdbSearchResults.EMPTY
        val language = runCatching { tmdbSettingsDataStore.settings.first().language }.getOrNull()
        val appLanguage = language.orEmpty().lowercase().substringBefore('-').ifBlank { "en" }
        // English titles keep their English name and overview, so a second search in English runs
        // alongside when the app language is another one.
        val englishDeferred = if (appLanguage != "en") {
            async { runCatching { tmdbApi.searchMulti(apiKey = apiKey, query = query, language = "en-US").body()?.results }.getOrNull().orEmpty() }
        } else {
            null
        }
        val results = runCatching {
            tmdbApi.searchMulti(apiKey = apiKey, query = query, language = language)
                .body()?.results
        }.getOrNull().orEmpty()
        val english = englishDeferred?.await().orEmpty().associateBy { "${it.mediaType}:${it.id}" }
        TmdbSearchResults(
            titles = results.mapNotNull { it.inOwnLanguage(appLanguage, english["${it.mediaType}:${it.id}"]).toTitle() }.take(MAX_TITLES),
            people = results.mapNotNull { it.toPerson() }.take(MAX_PEOPLE),
        )
    }

    private fun TmdbMultiSearchResult.toTitle(): MetaPreview? {
        val type = when (mediaType) {
            "movie" -> ContentType.MOVIE
            "tv" -> ContentType.SERIES
            else -> return null
        }
        val poster = posterPath ?: return null
        val displayName = (title ?: name)?.takeIf { it.isNotBlank() } ?: (originalTitle ?: originalName) ?: return null
        return MetaPreview(
            id = "tmdb:$id",
            type = type,
            name = displayName,
            poster = "$IMAGE_BASE/w342$poster",
            posterShape = PosterShape.POSTER,
            background = backdropPath?.let { "$IMAGE_BASE/w1280$it" },
            logo = null,
            description = overview?.takeIf { it.isNotBlank() },
            releaseInfo = (releaseDate ?: firstAirDate)?.take(4)?.takeIf { it.length == 4 },
            imdbRating = voteAverage?.toFloat()?.takeIf { it > 0f },
            genres = emptyList(),
        )
    }

    private fun TmdbMultiSearchResult.toPerson(): MetaPreview? {
        if (mediaType != "person") return null
        val photo = profilePath ?: return null
        val displayName = name?.takeIf { it.isNotBlank() } ?: return null
        return MetaPreview(
            id = "$SEARCH_PERSON_ID_PREFIX$id",
            type = ContentType.UNKNOWN,
            rawType = "person",
            name = displayName,
            poster = "$IMAGE_BASE/w342$photo",
            posterShape = PosterShape.POSTER,
            background = null,
            logo = null,
            description = null,
            releaseInfo = knownForDepartment,
            imdbRating = null,
            genres = emptyList(),
        )
    }

    private companion object {
        const val IMAGE_BASE = "https://image.tmdb.org/t/p"
        const val MAX_TITLES = 20
        const val MAX_PEOPLE = 15
    }
}

/**
 * A result named in its own language, as on the home screen: an English film keeps its English
 * title and overview ([english], from the English search), a title in the app language keeps the
 * localized ones, and any other is named by its original title. With a [favourite] subtitle
 * language, only a title made in it keeps its original name; any other reads in English.
 */
internal fun TmdbMultiSearchResult.inOwnLanguage(
    appLanguage: String,
    english: TmdbMultiSearchResult?,
    favourite: String? = com.nuvio.tv.core.tmdb.DescriptionLanguage.favourite,
): TmdbMultiSearchResult {
    val original = originalLanguage?.lowercase()?.takeIf { it.isNotBlank() } ?: return this
    if (favourite != null) {
        return when {
            original == favourite -> copy(
                title = originalTitle?.takeIf { it.isNotBlank() } ?: title,
                name = originalName?.takeIf { it.isNotBlank() } ?: name
            )
            english != null -> copy(
                title = english.title ?: title,
                name = english.name ?: name,
                overview = english.overview?.takeIf { it.isNotBlank() } ?: overview
            )
            // An English search runs beside any other language; without its result, an English
            // title's original name is its English one.
            appLanguage.startsWith("en") -> this
            original == "en" -> copy(
                title = originalTitle?.takeIf { it.isNotBlank() } ?: title,
                name = originalName?.takeIf { it.isNotBlank() } ?: name
            )
            else -> this
        }
    }
    return when {
        original == appLanguage -> this
        original == "en" && english != null -> copy(
            title = english.title ?: title,
            name = english.name ?: name,
            overview = english.overview?.takeIf { it.isNotBlank() } ?: overview
        )
        else -> copy(
            title = originalTitle?.takeIf { it.isNotBlank() } ?: title,
            name = originalName?.takeIf { it.isNotBlank() } ?: name
        )
    }
}

