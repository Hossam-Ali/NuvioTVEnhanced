package com.nuvio.tv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.nuvio.tv.R
import com.nuvio.tv.core.poster.PosterRatingPosition
import com.nuvio.tv.core.poster.PosterRatingsEntryPoint
import com.nuvio.tv.core.poster.PosterRatingsPreferences
import com.nuvio.tv.core.poster.badgesFor
import com.nuvio.tv.domain.model.MDBListRatings
import dagger.hilt.android.EntryPointAccessors

/**
 * Rating badges over a poster, as set in Settings › Appearance › Layout: IMDb from the catalog,
 * other providers from MDBList (batched and cached). Draws nothing while switched off.
 */
@Composable
fun BoxScope.PosterRatingBadges(
    itemId: String?,
    itemType: String?,
    imdbRating: Float?,
    tmdbRating: Float? = null,
    modifier: Modifier = Modifier,
) {
    val settings by PosterRatingsPreferences.settings.collectAsState()
    if (!settings.enabled || itemId.isNullOrBlank()) return
    val context = LocalContext.current
    val repository = remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, PosterRatingsEntryPoint::class.java).posterRatingsRepository()
    }
    val fromCatalog = imdbRating?.takeIf { it > 0f }?.toDouble()
    val fromTmdb = tmdbRating?.takeIf { it > 0f }?.toDouble()
    // MDBList is only asked when a provider other than IMDb could be shown.
    val needsMdbList = settings.providers.take(settings.maxBadges + 2).any { it != "imdb" } || fromCatalog == null
    val type = itemType.orEmpty().ifBlank { "movie" }
    val ratings by produceState(
        initialValue = (repository.cached(itemId, type) ?: MDBListRatings()).withKnown(fromCatalog, fromTmdb),
        itemId, type, needsMdbList, fromCatalog, fromTmdb
    ) {
        value = value.withKnown(fromCatalog, fromTmdb)
        if (needsMdbList) {
            repository.ratings(itemId, type)?.let { loaded -> value = loaded.withKnown(fromCatalog, fromTmdb) }
        }
    }
    val badges = remember(ratings, settings) { settings.badgesFor(ratings) }
    if (badges.isEmpty()) return
    val alignment = when (settings.position) {
        PosterRatingPosition.TOP_START -> Alignment.TopStart
        PosterRatingPosition.TOP_END -> Alignment.TopEnd
        PosterRatingPosition.BOTTOM_START -> Alignment.BottomStart
        PosterRatingPosition.BOTTOM_END -> Alignment.BottomEnd
    }
    Column(
        modifier = modifier.align(alignment).padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = if (alignment == Alignment.TopStart || alignment == Alignment.BottomStart) Alignment.Start else Alignment.End
    ) {
        badges.forEach { (provider, value) ->
            PosterRatingBadge(provider = provider, value = value, iconUrl = settings.iconPack[provider], ratings = ratings)
        }
    }
}

/** Fills IMDb and TMDB from the catalog and TMDB enrichment where MDBList has none. */
private fun MDBListRatings.withKnown(imdb: Double?, tmdb: Double?): MDBListRatings =
    copy(imdb = this.imdb ?: imdb, tmdb = this.tmdb ?: tmdb)

@Composable
private fun PosterRatingBadge(provider: String, value: Double, iconUrl: String?, ratings: MDBListRatings) {
    Row(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(6.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(modifier = Modifier.height(13.dp).widthIn(max = 28.dp), contentAlignment = Alignment.Center) {
            RatingIcon(provider = provider, iconUrl = iconUrl, ratings = ratings)
        }
        Text(
            text = formatPosterRating(provider, value),
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun RatingIcon(provider: String, iconUrl: String?, ratings: MDBListRatings) {
    val context = LocalContext.current
    if (iconUrl != null) {
        AsyncImage(model = iconUrl, contentDescription = provider, modifier = Modifier.height(13.dp), contentScale = ContentScale.Fit)
        return
    }
    when (provider) {
        "audience" -> Image(
            painter = painterResource(
                when (ratings.audienceStatus) {
                    com.nuvio.tv.domain.model.RottenTomatoesStatus.VERIFIED_HOT -> R.drawable.mdblist_audience_verified_hot
                    com.nuvio.tv.domain.model.RottenTomatoesStatus.STALE -> R.drawable.mdblist_audience_stale
                    else -> R.drawable.mdblist_audience
                }
            ),
            contentDescription = provider,
            modifier = Modifier.size(13.dp)
        )
        "metacritic" -> Image(painter = painterResource(R.drawable.mdblist_metacritic), contentDescription = provider, modifier = Modifier.size(13.dp))
        else -> {
            val raw = when (provider) {
                "imdb" -> R.raw.imdb_logo_2016
                "tmdb" -> R.raw.mdblist_tmdb
                "letterboxd" -> R.raw.mdblist_letterboxd
                "trakt" -> R.raw.mdblist_trakt
                "mal" -> R.raw.mdblist_mal
                "tomatoes" -> when (ratings.tomatoesStatus) {
                    com.nuvio.tv.domain.model.RottenTomatoesStatus.CERTIFIED_FRESH -> R.raw.mdblist_tomatoes_certified
                    com.nuvio.tv.domain.model.RottenTomatoesStatus.ROTTEN -> R.raw.mdblist_tomatoes_rotten
                    else -> R.raw.mdblist_tomatoes
                }
                else -> return
            }
            val model = remember(context, raw) { ImageRequest.Builder(context).data(raw).build() }
            AsyncImage(
                model = model,
                contentDescription = provider,
                modifier = Modifier.height(13.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}

internal fun formatPosterRating(provider: String, value: Double): String = when (provider) {
    "imdb", "tmdb", "letterboxd", "mal" -> String.format(java.util.Locale.US, "%.1f", value)
    "tomatoes", "audience", "trakt" -> String.format(java.util.Locale.US, "%.0f%%", value)
    else -> String.format(java.util.Locale.US, "%.0f", value)
}
