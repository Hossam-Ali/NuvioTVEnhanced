package com.nuvio.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import com.nuvio.tv.core.collections.CollectionCoverEntryPoint
import com.nuvio.tv.ui.theme.NuvioTheme
import dagger.hilt.android.EntryPointAccessors

/**
 * A cover made from the first posters of a collection folder, for folders without a picture:
 * one poster fills the tile, two or three sit side by side, four make a 2×2 grid. Shows
 * [fallback] until posters are known, or when the folder has none.
 */
@Composable
fun AutoCollectionCover(
    collectionId: String,
    folderId: String,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val resolver = remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, CollectionCoverEntryPoint::class.java).collectionCoverResolver()
    }
    val posters by produceState(resolver.cached(collectionId, folderId), collectionId, folderId) {
        value = resolver.posters(collectionId, folderId)
    }
    if (posters.isEmpty()) {
        fallback()
        return
    }
    Box(modifier = modifier.fillMaxSize().background(NuvioTheme.colors.BackgroundCard)) {
        when (posters.size) {
            1 -> CoverImage(posters[0], Modifier.fillMaxSize())
            2, 3 -> Row(Modifier.fillMaxSize()) {
                posters.forEach { CoverImage(it, Modifier.weight(1f).fillMaxSize()) }
            }
            else -> Column(Modifier.fillMaxSize()) {
                posters.chunked(2).take(2).forEach { pair ->
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        pair.forEach { CoverImage(it, Modifier.weight(1f).fillMaxSize()) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverImage(url: String, modifier: Modifier) {
    AsyncImage(model = url, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
}
