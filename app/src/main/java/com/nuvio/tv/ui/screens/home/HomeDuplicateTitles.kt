package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.MetaPreview

/**
 * The ways a title can be recognised across rows: its id and, when an add-on gives one, its IMDb
 * id, so the same film listed as "tt…" by one add-on and by id plus IMDb id by another still
 * counts once.
 */
internal fun MetaPreview.identityKeys(): List<String> = listOfNotNull(
    id.takeIf { it.isNotBlank() },
    imdbId?.trim()?.takeIf { it.startsWith("tt") }
)

/**
 * [rows] with each title kept only in the first row that has it, top to bottom. Titles in
 * [alreadyShown] (the "Recently added" row above them) count as shown. Placeholders are kept.
 */
internal fun withoutDuplicateTitles(rows: List<CatalogRow>, alreadyShown: List<MetaPreview> = emptyList()): List<CatalogRow> {
    val seen = HashSet<String>()
    alreadyShown.forEach { seen += it.identityKeys() }
    return rows.map { row ->
        val kept = row.items.filter { item ->
            if (item.id.startsWith("__placeholder_")) return@filter true
            val keys = item.identityKeys()
            if (keys.any { it in seen }) {
                false
            } else {
                seen += keys
                true
            }
        }
        if (kept.size == row.items.size) row else row.copy(items = kept)
    }
}
