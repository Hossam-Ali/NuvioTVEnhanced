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

internal val NewLookFocusedText = Color(0xFF0A0C10)
internal val NewLookChip = Color.White.copy(alpha = 0.12f)
internal val NewLookImdbYellow = Color(0xFFF5C518)
private const val CAST_LIMIT_SERIES = 6
private const val CAST_LIMIT_MOVIE = 12

/**
 * New-look title page: everything on one screen. Info, buttons and cast on the start side;
 * seasons and episodes (or, for a film, cast and similar titles) on the end side.
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun NewLookDetailsContent(
    meta: Meta,
    seasons: List<Int>,
    selectedSeason: Int,
    episodesForSeason: List<Video>,
    episodeProgressMap: Map<Pair<Int, Int>, WatchProgress>,
    watchedEpisodes: Set<Pair<Int, Int>>,
    episodeImdbRatings: Map<Pair<Int, Int>, Double>,
    mdbListRatings: MDBListRatings?,
    mdbListRatingOrder: List<String>,
    isInLibrary: Boolean,
    playText: String?,
    isPlayEnabled: Boolean,
    moreLikeThis: List<MetaPreview>,
    cast: List<MetaCastMember>,
    onPlay: () -> Unit,
    onPlayLongPress: () -> Unit,
    onTrailer: (() -> Unit)?,
    onToggleLibrary: () -> Unit,
    onLibraryLongPress: () -> Unit,
    onSeasonSelected: (Int) -> Unit,
    onEpisodeClick: (Video) -> Unit,
    onEpisodeLongPress: (Video) -> Unit,
    onCastClick: (MetaCastMember) -> Unit,
    onMoreLikeThisClick: (MetaPreview) -> Unit
) {
    val context = LocalContext.current
    val isSeries = seasons.isNotEmpty() || meta.videos.isNotEmpty()
    val playFocusRequester = remember { FocusRequester() }
    var focusedEpisodeId by rememberSaveable(meta.id) { mutableStateOf<String?>(null) }
    val episodeFocusRequesters = remember(meta.id) { HashMap<String, FocusRequester>() }
    val episodesListState = rememberLazyListState()

    // First visit: start on Play. Coming back from the player: the episode that was opened.
    LaunchedEffect(meta.id) {
        withFrameNanos { }
        val returnId = focusedEpisodeId
        val returnIndex = returnId?.let { id -> episodesForSeason.indexOfFirst { it.id == id } } ?: -1
        if (returnIndex >= 0) {
            episodesListState.scrollToItem(returnIndex)
            withFrameNanos { }
            val requester = episodeFocusRequesters[returnId]
            if (requester != null && runCatching { requester.requestFocus() }.isSuccess) return@LaunchedEffect
        }
        runCatching { playFocusRequester.requestFocus() }
    }
    LaunchedEffect(selectedSeason) {
        if (focusedEpisodeId == null || episodesForSeason.none { it.id == focusedEpisodeId }) {
            episodesListState.scrollToItem(0)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(NuvioTheme.colors.Background)) {
        NewLookBackdrop(url = meta.backdropUrl)

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 48.dp, end = 40.dp, top = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(36.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(0.46f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NewLookTitle(name = meta.name, logo = meta.logo)
                NewLookMetaChips(
                    meta = meta,
                    seasonCount = seasons.count { it > 0 },
                    mdbListRatings = mdbListRatings,
                    mdbListRatingOrder = mdbListRatingOrder,
                    genreLabel = { localizedGenreLabel(context, it) }
                )
                meta.description?.takeIf { it.isNotBlank() }?.let { description ->
                    Text(
                        text = description,
                        style = androidx.tv.material3.MaterialTheme.typography.bodyMedium.copy(
                            textDirection = description.contentTextDirection()
                        ),
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        color = NuvioTheme.colors.TextPrimary.copy(alpha = 0.88f),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NewLookPrimaryButton(
                        text = if (isPlayEnabled) playText ?: stringResource(R.string.new_look_play) else stringResource(R.string.playback_unavailable),
                        enabled = isPlayEnabled,
                        onClick = onPlay,
                        onLongClick = onPlayLongPress,
                        modifier = Modifier.focusRequester(playFocusRequester)
                    )
                    if (onTrailer != null) {
                        NewLookSecondaryButton(
                            text = stringResource(R.string.detail_tab_trailer),
                            icon = { Icon(Icons.Outlined.Movie, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = onTrailer
                        )
                    }
                    NewLookSecondaryButton(
                        text = null,
                        contentDescription = stringResource(
                            if (isInLibrary) R.string.new_look_in_list else R.string.new_look_add_to_list
                        ),
                        icon = {
                            Icon(
                                if (isInLibrary) Icons.Default.Check else Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = onToggleLibrary,
                        onLongClick = onLibraryLongPress
                    )
                }
                if (isSeries && cast.isNotEmpty()) {
                    NewLookSectionTitle(stringResource(R.string.new_look_cast), modifier = Modifier.padding(top = 10.dp))
                    NewLookCastGrid(cast = cast.take(CAST_LIMIT_SERIES), columns = 2, onCastClick = onCastClick)
                }
            }

            Column(
                modifier = Modifier
                    .weight(0.54f)
                    .fillMaxHeight()
            ) {
                if (isSeries) {
                    if (seasons.size > 1) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            modifier = Modifier.padding(bottom = 10.dp)
                        ) {
                            items(seasons, key = { it }) { season ->
                                NewLookPill(
                                    text = if (season == 0) stringResource(R.string.episodes_specials) else stringResource(R.string.episodes_season, season),
                                    selected = season == selectedSeason,
                                    onClick = { onSeasonSelected(season) }
                                )
                            }
                        }
                    }
                    LazyColumn(
                        state = episodesListState,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 40.dp, end = 6.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(episodesForSeason, key = { it.id }) { video ->
                            val episodeKey = (video.season ?: 0) to (video.episode ?: 0)
                            val requester = episodeFocusRequesters.getOrPut(video.id) { FocusRequester() }
                            NewLookEpisodeRow(
                                video = video,
                                progress = episodeProgressMap[episodeKey],
                                watched = episodeKey in watchedEpisodes,
                                imdbRating = episodeImdbRatings[episodeKey] ?: video.rating,
                                onClick = {
                                    focusedEpisodeId = video.id
                                    onEpisodeClick(video)
                                },
                                onLongClick = { onEpisodeLongPress(video) },
                                modifier = Modifier
                                    .focusRequester(requester)
                                    .onFocusChanged { if (it.isFocused) focusedEpisodeId = video.id }
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (cast.isNotEmpty()) {
                            NewLookSectionTitle(stringResource(R.string.new_look_cast))
                            NewLookCastGrid(cast = cast.take(CAST_LIMIT_MOVIE), columns = 3, onCastClick = onCastClick)
                        }
                        if (moreLikeThis.isNotEmpty()) {
                            NewLookSectionTitle(stringResource(R.string.detail_tab_more_like_this), modifier = Modifier.padding(top = 8.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)
                            ) {
                                items(moreLikeThis, key = { it.id }) { item ->
                                    NewLookPosterCard(item = item, onClick = { onMoreLikeThisClick(item) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun NewLookBackdrop(url: String?, alpha: Float = 0.38f) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // A small decode is plenty for a dimmed background and keeps memory and decode time low.
    val request = remember(context, url, density) {
        url?.let {
            ImageRequest.Builder(context)
                .data(it)
                .size(with(density) { 960.dp.roundToPx() }, with(density) { 540.dp.roundToPx() })
                .crossfade(false)
                .build()
        }
    }
    if (request != null) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = alpha,
            modifier = Modifier.fillMaxSize()
        )
    }
    val background = NuvioTheme.colors.Background
    val fade = remember(background, isRtl) {
        val stops = arrayOf(0f to background, 0.45f to background.copy(alpha = 0.9f), 1f to background.copy(alpha = 0.75f))
        val ordered = if (isRtl) stops.map { (1f - it.first) to it.second }.reversed().toTypedArray() else stops
        Brush.horizontalGradient(*ordered)
    }
    Box(modifier = Modifier.fillMaxSize().background(fade))
}

@Composable
private fun NewLookTitle(name: String, logo: String?) {
    var logoFailed by remember(logo) { mutableStateOf(false) }
    if (!logo.isNullOrBlank() && !logoFailed) {
        AsyncImage(
            model = logo,
            contentDescription = name,
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            onError = { logoFailed = true },
            modifier = Modifier
                .height(76.dp)
                .fillMaxWidth(0.75f)
        )
    } else {
        Text(
            text = name,
            style = androidx.tv.material3.MaterialTheme.typography.headlineMedium.copy(textDirection = name.contentTextDirection()),
            fontWeight = FontWeight.Bold,
            color = NuvioTheme.colors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewLookMetaChips(
    meta: Meta,
    seasonCount: Int,
    mdbListRatings: MDBListRatings?,
    mdbListRatingOrder: List<String>,
    genreLabel: (String) -> String
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (mdbListRatings != null) {
            MDBListRatingsRow(ratings = mdbListRatings, maxItems = 3, order = mdbListRatingOrder, iconSize = 18.dp)
        } else {
            meta.imdbRating?.takeIf { it > 0f }?.let { rating ->
                NewLookChipText(text = "IMDb ${"%.1f".format(rating)}", container = NewLookImdbYellow, content = Color(0xFF111111), bold = true)
            }
        }
        meta.genres.take(2).forEach { NewLookChipText(text = genreLabel(it)) }
        formatDetailYear(meta.releaseInfo)?.let { NewLookChipText(text = it) }
        if (seasonCount > 1) {
            NewLookChipText(text = stringResource(R.string.new_look_season_count, seasonCount))
        }
        meta.ageRating?.takeIf { it.isNotBlank() }?.let { NewLookChipText(text = it) }
    }
}

private fun formatDetailYear(releaseInfo: String?): String? =
    releaseInfo?.trim()?.takeIf { it.isNotBlank() }?.let { info ->
        Regex("\\d{4}(\\s*[-–]\\s*(\\d{4})?)?").find(info)?.value?.replace(" ", "") ?: info
    }

@Composable
internal fun NewLookChipText(
    text: String,
    container: Color = NewLookChip,
    content: Color = NuvioTheme.colors.TextPrimary.copy(alpha = 0.85f),
    bold: Boolean = false
) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
        color = content,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 9.dp, vertical = 3.dp)
    )
}

@Composable
internal fun NewLookSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = NuvioTheme.colors.TextSecondary,
        modifier = modifier
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun NewLookPrimaryButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        enabled = enabled,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.9f),
            contentColor = NewLookFocusedText,
            focusedContainerColor = Color.White,
            focusedContentColor = NewLookFocusedText,
            disabledContainerColor = NewLookChip,
            disabledContentColor = NuvioTheme.colors.TextSecondary
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(3.dp, Color.White.copy(alpha = 0.4f)),
                inset = (-3).dp,
                shape = RoundedCornerShape(14.dp)
            )
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        modifier = modifier.height(42.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxHeight().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun NewLookSecondaryButton(
    text: String?,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    contentDescription: String? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.14f),
            contentColor = NuvioTheme.colors.TextPrimary,
            focusedContainerColor = Color.White,
            focusedContentColor = NewLookFocusedText
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        modifier = modifier
            .height(42.dp)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = if (text == null) 11.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            icon()
            if (text != null) {
                Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun NewLookPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White.copy(alpha = 0.85f) else NewLookChip,
            contentColor = if (selected) NewLookFocusedText else NuvioTheme.colors.TextPrimary,
            focusedContainerColor = Color.White,
            focusedContentColor = NewLookFocusedText
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        modifier = modifier.semantics { this.selected = selected }
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NewLookEpisodeRow(
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

@Composable
internal fun formatNewLookRuntime(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return if (hours > 0) stringResource(R.string.new_look_runtime_hm, hours, rest) else stringResource(R.string.new_look_runtime_m, rest)
}

@Composable
internal fun NewLookThumbnail(url: String?, width: Dp, height: Dp) {
    if (url.isNullOrBlank()) return
    val context = LocalContext.current
    val density = LocalDensity.current
    val request = remember(context, url, density) {
        ImageRequest.Builder(context)
            .data(url)
            .size(with(density) { width.roundToPx() }, with(density) { height.roundToPx() })
            .crossfade(false)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
internal fun NewLookProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.3f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .background(Color.White)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NewLookCastGrid(
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
private fun NewLookCastItem(member: MetaCastMember, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
                    NewLookThumbnail(url = member.photo, width = 40.dp, height = 40.dp)
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NewLookPosterCard(item: MetaPreview, onClick: () -> Unit) {
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
        modifier = Modifier.size(width = 96.dp, height = 144.dp)
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
            NewLookThumbnail(url = item.poster, width = 96.dp, height = 144.dp)
        }
    }
}

/** The cast list on the title page: actors only, each once. */
internal fun newLookCastOrder(members: List<MetaCastMember>): List<MetaCastMember> =
    members.actorsOnly().distinctPeople()

@Composable
private fun newLookRoleLabel(character: String?): String? {
    val role = character?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return when {
        role.equals("Creator", ignoreCase = true) -> stringResource(R.string.cast_role_creator)
        role.equals("Director", ignoreCase = true) -> stringResource(R.string.cast_role_director)
        role.equals("Writer", ignoreCase = true) -> stringResource(R.string.cast_role_writer)
        else -> role
    }
}
