package com.nuvio.tv.ui.screens.home

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

/** How many titles of each picked row get their date looked up. */
private const val RECENTLY_ADDED_LOOKUPS_PER_ROW = 50

/**
 * Looks up on TMDB when the titles of the row [rowKey] came out, when the row feeds "Recently
 * added" (add-ons rarely give full dates), then rebuilds the rows so the new dates count.
 * Saved dates are reused until they need refreshing (see [com.nuvio.tv.core.tmdb.ReleaseDateStore]).
 */
internal fun HomeViewModel.lookUpRecentlyAddedDates(rowKey: String) {
    val picked = recentlyAddedCatalogKeys(com.nuvio.tv.core.poster.RecentlyAddedPreferences.rowKeys.value, collectionsCache)
    if (rowKey !in picked) return
    val toLookUp = readCatalogRow(rowKey)?.items.orEmpty()
        .asSequence()
        .filterNot { it.id.startsWith("__placeholder_") }
        .take(RECENTLY_ADDED_LOOKUPS_PER_ROW)
        .filter { releaseDateStore.needsLookUp(it.id, it.type) }
        .distinctBy { it.id }
        .toList()
    if (toLookUp.isEmpty()) return
    viewModelScope.launch(Dispatchers.IO) {
        val changed = toLookUp.map { item ->
            async { releaseDateStore.lookUp(item.id, item.apiType, item.type) }
        }.awaitAll()
        if (changed.any { it }) scheduleUpdateCatalogRows()
    }
}
