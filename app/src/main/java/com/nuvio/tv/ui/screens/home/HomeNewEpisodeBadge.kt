package com.nuvio.tv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.MetaPreview

/**
 * Ids of shows in Continue Watching with an episode out in the last two days (see
 * [isFreshRelease]): their cards in the home rows wear a "New episode" badge.
 */
internal val LocalFreshEpisodeIds = compositionLocalOf<Set<String>> { emptySet() }

/** The shows in [items] whose next episode has just come out, by every id they go by. */
internal fun freshEpisodeIds(items: List<ContinueWatchingItem>, nowMs: Long = System.currentTimeMillis()): Set<String> =
    items.asSequence()
        .filterIsInstance<ContinueWatchingItem.NextUp>()
        .filter { it.isFreshRelease(nowMs) }
        .map { it.info.contentId }
        .filter { it.isNotBlank() }
        .toSet()

/** Whether [meta] is one of [freshIds], by its own id or its IMDb id. */
internal fun hasFreshEpisode(meta: MetaPreview?, freshIds: Set<String>): Boolean {
    if (meta == null || freshIds.isEmpty()) return false
    return meta.id in freshIds || meta.imdbId?.let { it in freshIds } == true
}

private val NewEpisodeGreen = Color(0xFF3DDC97)

/** The small "New episode" label on a card. */
@Composable
internal fun NewEpisodeBadge(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.home_new_episode_badge),
        color = Color(0xFF0A0C10),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = modifier
            .background(NewEpisodeGreen, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
