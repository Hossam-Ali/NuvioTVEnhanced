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
)

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

private fun MetaPreview.sameTitleKey(): String = "${rawType.lowercase()}|${normalizeSearchText(name)}|${year()}"

/**
 * One row from every addon row plus [extra] titles (TMDB): each title once, best match first.
 * Ties keep the order results arrived in, which is each source's own relevance, and a title found
 * by more sources ranks above one found by fewer. Addon copies win over [extra] ones, so a title
 * opens with the addon that returned it.
 */
internal fun buildTopResults(
    query: String,
    rows: List<CatalogRow>,
    extra: List<MetaPreview>,
    limit: Int = 30,
): TopSearchResults {
    data class Candidate(val item: MetaPreview, val source: String, val order: Int, var hits: Int)

    val byTitle = LinkedHashMap<String, Candidate>()
    var order = 0
    val sources = rows.map { row -> row.items.map { it to row.addonBaseUrl } } + listOf(extra.map { it to "" })
    // Interleave sources by position, so one addon's long tail does not crowd out the others.
    val longest = sources.maxOfOrNull { it.size } ?: 0
    for (position in 0 until longest) {
        for (source in sources) {
            val (item, baseUrl) = source.getOrNull(position) ?: continue
            if (item.name.isBlank() || item.id.startsWith("__placeholder_")) continue
            val key = item.sameTitleKey()
            val existing = byTitle[key] ?: byTitle.values.firstOrNull { it.item.id == item.id && it.item.rawType == item.rawType }
            if (existing != null) {
                existing.hits++
            } else {
                byTitle[key] = Candidate(item, baseUrl, order++, hits = 1)
            }
        }
    }
    val ranked = byTitle.values
        .sortedWith(compareBy<Candidate>({ searchMatchRank(it.item.name, query) }, { -it.hits }, { it.order }))
        .take(limit)
    return TopSearchResults(
        items = ranked.map { it.item },
        sources = ranked.associate { topResultKey(it.item.id, it.item.rawType) to it.source },
    )
}

/** Whether a top results row adds anything: results from at least two sources. */
internal fun shouldShowTopResults(rowsWithResults: Int, extraResults: Int): Boolean =
    rowsWithResults + (if (extraResults > 0) 1 else 0) >= 2
