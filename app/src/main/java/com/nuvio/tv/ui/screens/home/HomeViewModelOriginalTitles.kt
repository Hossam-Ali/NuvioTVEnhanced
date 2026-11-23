package com.nuvio.tv.ui.screens.home

import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.tmdb.DescriptionLanguage
import com.nuvio.tv.core.tmdb.OriginalTitle
import com.nuvio.tv.core.tmdb.firstLetterScript
import com.nuvio.tv.domain.model.HomeLayout
import com.nuvio.tv.ui.screens.search.isNonLatinName
import com.nuvio.tv.ui.screens.search.renamedToOriginalTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

/**
 * The name the Modern home gives a title that its add-on calls [name], as search does (see
 * [renamedToOriginalTitle]); null keeps [name]. An add-on's "كروس ج2" becomes "Cross" and an Arabic
 * show keeps its Arabic name. A name in Latin letters changes only for a title made in the
 * [favourite] language, written in another script: with Arabic subtitles, ArabCinemeta's
 * "What is the matter?" becomes "الكلام على إيه", while "Squid Game" stays as it is.
 */
internal fun originalCatalogName(
    name: String,
    original: OriginalTitle?,
    favourite: String? = DescriptionLanguage.favourite
): String? {
    original ?: return null
    renamedToOriginalTitle(name, original.title)?.let { return it }
    val title = original.title.trim()
    if (favourite.isNullOrBlank() || !original.language.equals(favourite, ignoreCase = true)) return null
    if (title.isBlank() || title == name) return null
    return title.takeIf { firstLetterScript(it) != firstLetterScript(name) }
}

/**
 * Whether a title named [name] needs its original title looked up: names in another script always
 * (they may be a translation), Latin ones only with a favourite language (they may be a title made in it).
 */
internal fun needsOriginalTitleLookup(name: String, favourite: String? = DescriptionLanguage.favourite): Boolean =
    isNonLatinName(name) || !favourite.isNullOrBlank()

/** The Modern home names titles like its hero, which takes TMDB's original title with its basic info. */
private fun HomeViewModel.namesTitlesInOriginalLanguage(): Boolean {
    val settings = currentTmdbSettings
    return _uiState.value.homeLayout == HomeLayout.MODERN &&
        settings.enabled && settings.modernHomeEnabled && settings.useBasicInfo
}

/**
 * Names the titles of the row [rowKey] by their original title where [originalCatalogName] says so.
 * Titles looked up before are renamed at once, before the row is shown; the others are asked of
 * TMDB in the background and renamed as the answers come.
 */
internal fun HomeViewModel.nameCatalogRowInOriginalLanguage(rowKey: String) {
    if (!namesTitlesInOriginalLanguage()) return
    renameCatalogRow(rowKey)

    val toLookUp = readCatalogRow(rowKey)?.items.orEmpty()
        .filter { needsOriginalTitleLookup(it.name) && !originalTitleStore.isLookedUp(it.id) }
        .distinctBy { it.id }
    if (toLookUp.isEmpty()) return
    viewModelScope.launch(Dispatchers.IO) {
        val found = toLookUp.map { item ->
            async { originalTitleStore.lookUp(item.id, item.apiType, item.type) }
        }.awaitAll()
        if (found.any { it != null } && namesTitlesInOriginalLanguage() &&
            renameCatalogRow(rowKey)
        ) {
            scheduleUpdateCatalogRows()
        }
    }
}

/** Names every loaded row by original titles, for when the TMDB settings change. */
internal fun HomeViewModel.nameCatalogRowsInOriginalLanguage() {
    snapshotCatalogKeys().forEach(::nameCatalogRowInOriginalLanguage)
}

/** Renames the row's titles that already have a saved original title; true when any changed. */
private fun HomeViewModel.renameCatalogRow(rowKey: String): Boolean {
    var changed = false
    updateCatalogRow(rowKey) { row ->
        val items = row.items.map { item ->
            originalCatalogName(item.name, originalTitleStore.known(item.id))
                ?.let { item.copy(name = it) } ?: item
        }
        if (items == row.items) {
            row
        } else {
            changed = true
            truncatedRowCache.remove(rowKey)
            row.copy(items = items)
        }
    }
    return changed
}
