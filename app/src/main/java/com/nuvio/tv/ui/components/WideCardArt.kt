package com.nuvio.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.platform.LocalContext
import com.nuvio.tv.core.poster.CardArt
import com.nuvio.tv.core.poster.CardBackdropEntryPoint
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PLACEHOLDER_IMAGE_URL

/** What a wide card shows: [url], and [alignment] to keep faces and titles when [url] is tall art. */
internal data class WideCardArt(val url: String?, val alignment: Alignment)

/** Slightly above centre: a poster or headshot cropped to a wide card keeps the faces and title. */
internal val PortraitInWideCardAlignment: Alignment = BiasAlignment(0f, -0.45f)

/**
 * The picture of a wide card for [item]: its own wide art ([ownLandscapePoster], else a
 * background that isn't just the poster again), else the title's wide picture from TMDB or its
 * add-on (looked up once and kept, as on the Modern home), else the poster or a person's photo,
 * aimed above centre so it isn't cut through the faces.
 */
@Composable
internal fun rememberWideCardArt(item: MetaPreview, ownLandscapePoster: String?): WideCardArt {
    val context = LocalContext.current
    val ownWideArt = ownLandscapePoster
        ?: item.background?.takeIf { it.isNotBlank() && it != item.poster && it != item.rawPosterUrl }
    val isPerson = item.rawType == "person"
    val needsLookUp = ownWideArt == null && !isPerson &&
        item.poster != PLACEHOLDER_IMAGE_URL && !item.id.startsWith("__placeholder_")
    val cardBackdrops = remember(context) {
        dagger.hilt.android.EntryPointAccessors.fromApplication(
            context.applicationContext,
            CardBackdropEntryPoint::class.java
        ).cardBackdropRepository()
    }
    val saved = remember(item.id, needsLookUp) { if (needsLookUp) cardBackdrops.known(item.id) else null }
    val lookedUp by produceState(initialValue = saved, item.id, needsLookUp) {
        if (!needsLookUp || saved != null) return@produceState
        value = cardBackdrops.backdrop(item.id, item.apiType)?.let { CardArt.Wide(it) } ?: CardArt.None
    }
    val found = (lookedUp as? CardArt.Wide)?.url
    return when {
        ownWideArt != null -> WideCardArt(ownWideArt, Alignment.Center)
        found != null -> WideCardArt(found, Alignment.Center)
        else -> WideCardArt(item.background ?: item.poster, PortraitInWideCardAlignment)
    }
}
