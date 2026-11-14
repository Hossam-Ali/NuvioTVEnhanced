package com.nuvio.tv.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.MDBListRatings
import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.domain.model.MetaCastMember
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.Video
import com.nuvio.tv.domain.model.WatchProgress
import com.nuvio.tv.ui.components.MDBListRatingsRow
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.util.contentTextDirection
import com.nuvio.tv.ui.util.actorsOnly
import com.nuvio.tv.ui.util.distinctPeople
import com.nuvio.tv.ui.util.localizedGenreLabel

// The title page's cards: episodes, cast and "More like this".

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun NewLookEpisodeRow(
    video: Video,
    progress: WatchProgress?,
    watched: Boolean,
    imdbRating: Double?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = video.title.takeIf { it.isNotBlank() }
    val number = video.episode
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = NuvioTheme.colors.TextPrimary,
            focusedContainerColor = Color.White.copy(alpha = 0.10f),
            focusedContentColor = NuvioTheme.colors.TextPrimary
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
                shape = RoundedCornerShape(14.dp)
            )
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 150.dp, height = 84.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(NewLookChip)
            ) {
                NewLookThumbnail(url = video.thumbnail, width = 150.dp, height = 84.dp)
                val fraction = progress?.progressPercentage?.takeIf { !watched && it > 0.01f }
                if (fraction != null) {
                    NewLookProgressBar(
                        fraction = fraction,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                    )
                }
                if (watched) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(5.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.65f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.new_look_watched), modifier = Modifier.size(14.dp), tint = Color.White)
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                val heading = when {
                    number != null && title != null -> stringResource(R.string.new_look_episode_heading, number, title)
                    title != null -> title
                    number != null -> stringResource(R.string.new_look_episode_number, number)
                    else -> ""
                }
                Text(
                    text = heading,
                    style = androidx.tv.material3.MaterialTheme.typography.titleSmall.copy(textDirection = heading.contentTextDirection()),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val facts = buildList {
                    video.runtime?.takeIf { it > 0 }?.let { add(formatNewLookRuntime(it)) }
                    imdbRating?.takeIf { it > 0 }?.let { add("IMDb ${"%.1f".format(it)}") }
                    video.released?.takeIf { it.isNotBlank() }?.let { iso ->
                        runCatching { formatReleaseDate(iso) }.getOrNull()?.takeIf { it.isNotBlank() }?.let { add(it) }
                    }
                }
                if (facts.isNotEmpty()) {
                    Text(
                        text = facts.joinToString("  ·  "),
                        fontSize = 12.sp,
                        color = NuvioTheme.colors.TextSecondary,
                        maxLines = 1
                    )
                }
                video.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                    Text(
                        text = overview,
                        style = androidx.tv.material3.MaterialTheme.typography.bodySmall.copy(textDirection = overview.contentTextDirection()),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = NuvioTheme.colors.TextPrimary.copy(alpha = 0.78f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun NewLookCastGrid(
    cast: List<MetaCastMember>,
    columns: Int,
    onCastClick: (MetaCastMember) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        cast.chunked(columns).forEach { rowMembers ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                rowMembers.forEach { member ->
                    NewLookCastItem(member = member, onClick = { onCastClick(member) }, modifier = Modifier.weight(1f))
                }
                repeat(columns - rowMembers.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun NewLookCastItem(member: MetaCastMember, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = NuvioTheme.colors.TextPrimary,
            focusedContainerColor = Color.White,
            focusedContentColor = NewLookFocusedText
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(NewLookChip),
                contentAlignment = Alignment.Center
            ) {
                if (member.photo.isNullOrBlank()) {
                    Text(text = member.name.take(1), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                } else {
                    NewLookThumbnail(
                        url = member.photo,
                        width = 40.dp,
                        height = 40.dp,
                        // Headshots are tall: keep the face rather than the middle of the photo.
                        alignment = com.nuvio.tv.ui.screens.stream.PortraitFaceAlignment
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = member.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                newLookRoleLabel(member.character)?.let {
                    Text(
                        text = it,
                        fontSize = 11.sp,
                        color = androidx.tv.material3.LocalContentColor.current.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** The size of a "More like this" card, following its picture so nothing is cropped away. */
internal fun newLookPosterCardSize(shape: com.nuvio.tv.domain.model.PosterShape): Pair<Dp, Dp> = when (shape) {
    com.nuvio.tv.domain.model.PosterShape.LANDSCAPE -> 176.dp to 99.dp
    com.nuvio.tv.domain.model.PosterShape.SQUARE -> 120.dp to 120.dp
    com.nuvio.tv.domain.model.PosterShape.POSTER -> 96.dp to 144.dp
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun NewLookPosterCard(item: MetaPreview, onClick: () -> Unit) {
    // TMDB's suggestions come with wide backdrops: a wide card shows all of the picture instead of
    // a poster-shaped slice of its middle. The title's name sits under every picture.
    val (width, height) = newLookPosterCardSize(item.posterShape)
    Column(modifier = Modifier.width(width)) {
        Surface(
            onClick = onClick,
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = NewLookChip, focusedContainerColor = NewLookChip),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = androidx.tv.material3.Border(
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
                    shape = RoundedCornerShape(10.dp)
                )
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            modifier = Modifier.size(width = width, height = height)
        ) {
            if (item.poster.isNullOrBlank()) {
                Text(
                    text = item.name,
                    fontSize = 12.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(8.dp)
                )
            } else {
                NewLookThumbnail(url = item.poster, width = width, height = height)
            }
        }
        if (!item.poster.isNullOrBlank()) {
            Text(
                text = item.name,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = androidx.compose.ui.text.TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Content),
                modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp)
            )
        }
    }
}

/** The cast list on the title page: actors only, each once. */
internal fun newLookCastOrder(members: List<MetaCastMember>): List<MetaCastMember> =
    members.actorsOnly().distinctPeople()

@Composable
internal fun newLookRoleLabel(character: String?): String? {
    val role = character?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return when {
        role.equals("Creator", ignoreCase = true) -> stringResource(R.string.cast_role_creator)
        role.equals("Director", ignoreCase = true) -> stringResource(R.string.cast_role_director)
        role.equals("Writer", ignoreCase = true) -> stringResource(R.string.cast_role_writer)
        else -> role
    }
}
