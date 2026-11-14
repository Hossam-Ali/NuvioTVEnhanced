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
