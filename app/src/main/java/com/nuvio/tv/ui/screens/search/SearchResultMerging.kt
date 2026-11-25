package com.nuvio.tv.ui.screens.search

import com.nuvio.tv.core.search.normalizeSearchText
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.MetaPreview

/** Rows the search screen builds itself, above the addon rows. Never sent to an addon. */
internal const val TOP_RESULTS_ROW_ID = "__nuvio_search_top_results__"
internal const val PEOPLE_ROW_ID = "__nuvio_search_people__"
internal val SEARCH_SCREEN_ROW_IDS = setOf(TOP_RESULTS_ROW_ID, PEOPLE_ROW_ID)

/** Id prefix of people from TMDB search; the rest is the TMDB person id. */
internal const val SEARCH_PERSON_ID_PREFIX = "tmdbperson:"

internal data class TopSearchResults(
    val items: List<MetaPreview>,
    /** Addon base URL to open each item with, by [topResultKey]; blank lets details pick one. */
    val sources: Map<String, String>,
    /** [topResultKey] of every copy the items stand for, including the ones merged away. */
    val copies: Set<String> = emptySet(),
)

/**
 * What search has learnt about its results' TMDB ids. An add-on's "tt5240732" and TMDB's
 * "tmdb:395815" are one film, whatever each is called.
 */
internal data class SearchTitleIds(
    /** TMDB id by [topResultKey]; null once TMDB was asked and doesn't know the title. */
    val looked: Map<String, String?> = emptyMap(),
    /** TMDB search results, by [topResultKey], that TMDB has an IMDb id for. */
    val withImdbId: Set<String> = emptySet(),
) {
    fun tmdbIdOf(item: MetaPreview): String? = tmdbIdIn(item.id) ?: looked[topResultKey(item.id, item.rawType)]

    /** An IMDb id TMDB doesn't know, such as elCinema's own for a film IMDb lists under another. */
    fun isUnrecognised(item: MetaPreview): Boolean {
        val key = topResultKey(item.id, item.rawType)
        return item.id.startsWith("tt") && key in looked && looked[key] == null
    }
}

private val TMDB_ID = Regex("tmdb:(?:(?:movie|series|tv):)?(\\d+)")

/** The TMDB id written in an add-on's or TMDB's own [id], such as "tmdb:395815". */
internal fun tmdbIdIn(id: String): String? = TMDB_ID.matchEntire(id)?.groupValues?.get(1)

internal fun topResultKey(id: String, type: String): String = "$id|$type"

/** How well [title] matches [query], lower being better. */
internal fun searchMatchRank(title: String, query: String): Int {
    val t = normalizeSearchText(title)
    val q = normalizeSearchText(query)
    if (q.isEmpty()) return 4
    return when {
        t == q -> 0
        t.startsWith(q) -> 1
        t.split(' ').any { it.startsWith(q) } || " $t".contains(" $q") -> 2
        t.contains(q) -> 3
        else -> 4
    }
}

private fun MetaPreview.year(): String = releaseInfo?.let { Regex("(19|20)\\d{2}").find(it)?.value }.orEmpty()

internal fun MetaPreview.sameTitleKey(): String = "${rawType.lowercase()}|${normalizeSearchText(name)}|${year()}"

/** One title in the top row, and every copy of it the sources returned. */
private class TopCandidate(var item: MetaPreview, var source: String, var score: Int, val order: Int, var tmdbId: String?) {
    val nameKeys = mutableSetOf<String>()
    val copies = mutableSetOf<String>()
    var hits = 0
    var rank = Int.MAX_VALUE
}

/**
 * One row from every addon row plus [extra] titles (TMDB): each title once, best match first.
 * Copies are one title when they share an id, a TMDB id (see [ids]), or a name and year without
 * being known to TMDB as two titles. Ties keep the order results arrived in, which is each
 * source's own relevance, and a title found by more sources ranks above one found by fewer. Addon
 * copies win over [extra] ones, so a title opens with the addon that returned it, unless TMDB
 * doesn't know the addon's id and has another for the title: sources are listed under that one.
 */
internal fun buildTopResults(
    query: String,
    rows: List<CatalogRow>,
    extra: List<MetaPreview>,
    limit: Int = 30,
    ids: SearchTitleIds = SearchTitleIds(),
): TopSearchResults {
    val found = LinkedHashSet<TopCandidate>()
    var order = 0
    val sources = rows.map { row -> row.items.map { it to row.addonBaseUrl } } + listOf(extra.map { it to "" })
    // Interleave sources by position, so one addon's long tail does not crowd out the others.
    val longest = sources.maxOfOrNull { it.size } ?: 0
    for (position in 0 until longest) {
        sources.forEachIndexed { sourceIndex, source ->
            val (item, baseUrl) = source.getOrNull(position) ?: return@forEachIndexed
            if (item.name.isBlank() || item.id.startsWith("__placeholder_")) return@forEachIndexed
            val copyKey = topResultKey(item.id, item.rawType)
            val nameKey = item.sameTitleKey()
            val tmdbId = ids.tmdbIdOf(item)?.let { "${item.apiType}:$it" }
            val score = when {
                sourceIndex == rows.size -> if (copyKey in ids.withImdbId) 2 else 0
                ids.isUnrecognised(item) -> 1
                else -> 3
            }
            val sameId = found.filter { copyKey in it.copies || (tmdbId != null && it.tmdbId == tmdbId) }
            val sameName = found.filter { nameKey in it.nameKeys && (tmdbId == null || it.tmdbId == null) }
            val candidate = sameId.firstOrNull() ?: sameName.firstOrNull()
                ?: TopCandidate(item, baseUrl, score, order++, tmdbId).also { found += it }
            if (score > candidate.score) {
                candidate.item = item
                candidate.source = baseUrl
                candidate.score = score
            }
            candidate.tmdbId = candidate.tmdbId ?: tmdbId
            candidate.nameKeys += nameKey
            candidate.copies += copyKey
            candidate.hits++
            candidate.rank = minOf(candidate.rank, searchMatchRank(item.name, query))
            // This copy can show that two titles found so far are one.
            for (other in sameId + sameName) {
                val differ = other.tmdbId != null && candidate.tmdbId != null && other.tmdbId != candidate.tmdbId
                if (other === candidate || differ || !found.remove(other)) continue
                if (other.score > candidate.score) {
                    candidate.item = other.item
                    candidate.source = other.source
                    candidate.score = other.score
                }
                candidate.tmdbId = candidate.tmdbId ?: other.tmdbId
                candidate.nameKeys += other.nameKeys
                candidate.copies += other.copies
                candidate.hits += other.hits
                candidate.rank = minOf(candidate.rank, other.rank)
            }
        }
    }
    val ranked = found
        .sortedWith(compareBy<TopCandidate>({ it.rank }, { -it.hits }, { it.order }))
        .take(limit)
    return TopSearchResults(
        items = ranked.map { it.item },
        sources = ranked.associate { topResultKey(it.item.id, it.item.rawType) to it.source },
        copies = ranked.flatMapTo(mutableSetOf()) { it.copies },
    )
}

/** Whether a top results row adds anything: results from at least two sources. */
internal fun shouldShowTopResults(rowsWithResults: Int, extraResults: Int): Boolean =
    rowsWithResults + (if (extraResults > 0) 1 else 0) >= 2
