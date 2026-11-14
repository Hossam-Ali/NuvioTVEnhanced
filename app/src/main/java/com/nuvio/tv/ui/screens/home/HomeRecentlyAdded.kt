package com.nuvio.tv.ui.screens.home

import androidx.compose.runtime.Immutable
import com.nuvio.tv.core.util.isUnreleased
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.legacyKey
import java.time.LocalDate

/** Key of the "Recently added" row on the Modern home, also its key for a card style. */
internal const val MODERN_RECENTLY_ADDED_ROW_KEY = "modern_recently_added"

/** How many titles the "Recently added" row shows. */
internal const val RECENTLY_ADDED_MAX_ITEMS = 40

/** A title in the "Recently added" row, with the row it came from (which add-on opens it). */
@Immutable
data class RecentlyAddedEntry(val source: CatalogRow, val item: MetaPreview)

private val ISO_DATE = Regex("""^(\d{4})-(\d{2})-(\d{2})""")
private val YEAR = Regex("""\b(19|20)\d{2}\b""")

/**
 * The date a title came out, for ordering: its full release date when the add-on gives one, else
 * January 1st of the first year in its release info ("2024", "2024-", "2019-2023").
 */
internal fun MetaPreview.releaseSortDate(): LocalDate? {
    released?.trim()?.let { raw ->
        ISO_DATE.find(raw)?.let { match ->
            val (year, month, day) = match.destructured
            runCatching { return LocalDate.of(year.toInt(), month.toInt(), day.toInt()) }
        }
    }
    val year = (YEAR.find(releaseInfo.orEmpty()) ?: YEAR.find(released.orEmpty()))?.value?.toIntOrNull()
        ?: return null
    return LocalDate.of(year, 1, 1)
}

/**
 * The "Recently added" row: the newest titles, by release date, of the rows whose keys are in
 * [selectedRowKeys]. Titles not out yet and titles without any date are left out, each title
 * shows once, and titles of the same date take turns between the rows, so one busy row doesn't
 * fill the whole strip.
 */
internal fun recentlyAddedEntries(
    rows: List<CatalogRow>,
    selectedRowKeys: Set<String>,
    today: LocalDate,
    limit: Int = RECENTLY_ADDED_MAX_ITEMS
): List<RecentlyAddedEntry> {
    if (selectedRowKeys.isEmpty()) return emptyList()
    val sources = rows.filter { it.legacyKey() in selectedRowKeys }
    if (sources.isEmpty()) return emptyList()

    // Taking the rows in turn first means a stable sort keeps them alternating within a date.
    val longest = sources.maxOf { it.items.size }
    val alternating = buildList {
        for (index in 0 until longest) {
            sources.forEach { row -> row.items.getOrNull(index)?.let { add(RecentlyAddedEntry(row, it)) } }
        }
    }
    val seen = HashSet<String>()
    return alternating
        .asSequence()
        .filterNot { it.item.id.startsWith("__placeholder_") }
        .mapNotNull { entry -> entry.item.releaseSortDate()?.let { entry to it } }
        .filter { (entry, date) -> !date.isAfter(today) && !entry.item.isUnreleased(today) }
        .sortedByDescending { (_, date) -> date }
        .map { (entry, _) -> entry }
        .filter { seen.add("${it.item.apiType}:${it.item.id}") }
        .take(limit)
        .toList()
}

/**
 * The "Recently added" carousel row. Cards are built like those of their own rows (opening the
 * add-on that listed them) but keyed apart, so focus never lands on the same title in its own row.
 */
internal fun buildRecentlyAddedRow(
    entries: List<RecentlyAddedEntry>,
    title: String,
    useLandscapePosters: Boolean,
    showFullReleaseDate: Boolean,
    showImdbRatings: Boolean,
    strTypeMovie: String,
    strTypeSeries: String,
    itemCache: MutableMap<String, CachedCarouselItem>
): HeroCarouselRow? {
    if (entries.isEmpty()) return null
    val usedKeys = HashSet<String>()
    val items = entries.map { (source, item) ->
        val cacheKey = "${item.apiType}:${item.id}"
        usedKeys += cacheKey
        val cached = itemCache[cacheKey]
        if (cached != null &&
            cached.source == item &&
            cached.useLandscapePosters == useLandscapePosters &&
            cached.showFullReleaseDate == showFullReleaseDate &&
            cached.showImdbRatings == showImdbRatings
        ) {
            cached.carouselItem
        } else {
            val built = buildCatalogItem(
                item = item,
                row = source,
                useLandscapePosters = useLandscapePosters,
                occurrence = 0,
                strTypeMovie = strTypeMovie,
                strTypeSeries = strTypeSeries,
                showFullReleaseDate = showFullReleaseDate,
                showImdbRatings = showImdbRatings,
                previousCachedItem = cached?.carouselItem
            ).let { card ->
                val payload = card.payload as ModernPayload.Catalog
                card.copy(
                    key = "${MODERN_RECENTLY_ADDED_ROW_KEY}_$cacheKey",
                    payload = payload.copy(focusKey = "$MODERN_RECENTLY_ADDED_ROW_KEY::${item.id}")
                )
            }
            itemCache[cacheKey] = CachedCarouselItem(
                source = item,
                useLandscapePosters = useLandscapePosters,
                showFullReleaseDate = showFullReleaseDate,
                showImdbRatings = showImdbRatings,
                carouselItem = built
            )
            built
        }
    }
    itemCache.keys.retainAll(usedKeys)
    return HeroCarouselRow(
        key = MODERN_RECENTLY_ADDED_ROW_KEY,
        title = title,
        globalRowIndex = -1,
        items = com.nuvio.tv.ui.util.StableList(items)
    )
}

/** Catalog id of the "Recently added" row on the Classic home, which has no add-on of its own. */
internal const val RECENTLY_ADDED_CATALOG_ID = "nuvio_recently_added"

internal fun CatalogRow.isRecentlyAddedRow(): Boolean = catalogId == RECENTLY_ADDED_CATALOG_ID && addonBaseUrl.isEmpty()

/**
 * The "Recently added" row as a catalog row, for the Classic home. Its titles come from several
 * add-ons, so it names none: details then picks the add-on, as for search's top results.
 */
internal fun recentlyAddedCatalogRow(entries: List<RecentlyAddedEntry>, title: String): CatalogRow? {
    if (entries.isEmpty()) return null
    return CatalogRow(
        addonId = RECENTLY_ADDED_CATALOG_ID,
        addonName = "",
        addonBaseUrl = "",
        catalogId = RECENTLY_ADDED_CATALOG_ID,
        catalogName = title,
        type = com.nuvio.tv.domain.model.ContentType.MOVIE,
        rawType = "movie",
        items = entries.map { it.item },
        hasMore = false
    )
}
