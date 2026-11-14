package com.nuvio.tv.ui.screens.detail

import com.nuvio.tv.ui.components.LocalPlaybackAvailability
import com.nuvio.tv.ui.navigation.LocalDetailChildClosedEpoch
import com.nuvio.tv.ui.navigation.LocalDetailChildOverlayVisible
import android.widget.Toast

import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.theme.NuvioMotion

import android.view.KeyEvent
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.runtime.State
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.nuvio.tv.R
import com.nuvio.tv.ui.components.posteroptions.PosterListPickerDialog
import com.nuvio.tv.ui.util.dpadVerticalFastScroll
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.nuvio.tv.ui.util.localizedGenreLabel
import com.nuvio.tv.ui.util.recompositionHighlighter
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListPrefetchStrategy
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.clip
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.DetailImdbRatingsVisibility
import com.nuvio.tv.domain.model.EpisodeOptionsOverlayStyle
import com.nuvio.tv.domain.model.HomeImdbRatingsVisibility
import com.nuvio.tv.domain.model.LibraryListTab
import com.nuvio.tv.domain.model.LibrarySourceMode
import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.domain.model.MetaCastMember
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.MetaTrailer
import com.nuvio.tv.domain.model.resolveContentLanguage
import com.nuvio.tv.domain.model.MDBListRatings
import com.nuvio.tv.domain.model.NextToWatch
import com.nuvio.tv.domain.model.TraktCommentReview
import com.nuvio.tv.domain.model.Video
import com.nuvio.tv.domain.model.WatchProgress
import com.nuvio.tv.ui.components.ErrorState
import com.nuvio.tv.ui.components.MetaDetailsSkeleton
import com.nuvio.tv.ui.components.PlayManualOverrideDialog
import com.nuvio.tv.ui.components.SynopsisOverlay
import com.nuvio.tv.ui.components.TrailerPlayer
import com.nuvio.tv.ui.components.posteroptions.TrackingRemovalConfirmationDialog
import com.nuvio.tv.core.tracking.LOCAL_LIBRARY_LIST_KEY
import com.nuvio.tv.core.tracking.supportsMembershipFor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

private const val USER_INTERACTION_DISPATCH_DEBOUNCE_MS = 120L


private fun formatDetailYearRange(releaseInfo: String?): String? {
    if (releaseInfo.isNullOrBlank()) return null
    return releaseInfo.trim()
}

@Composable
fun MetaDetailsScreen(
    viewModel: MetaDetailsViewModel = hiltViewModel(),
    returnFocusSeason: Int? = null,
    returnFocusEpisode: Int? = null,
    heroRestoreToken: Int = 0,
    heroBackdropUrl: String? = null,
    playOnLoad: Boolean = false,
    playOnLoadManually: Boolean = false,
    onBackPress: () -> Unit,
    onReturnFocusConsumed: () -> Unit = {},
    onNavigateToCastDetail: (personId: Int, personName: String, preferCrew: Boolean) -> Unit = { _, _, _ -> },
    onNavigateToTmdbEntityBrowse: (entityKind: String, entityId: Int, entityName: String, sourceType: String) -> Unit = { _, _, _, _ -> },
    onNavigateToDetail: (itemId: String, itemType: String, addonBaseUrl: String?) -> Unit = { _, _, _ -> },
    onPlayClick: (
        videoId: String,
        contentType: String,
        contentId: String,
        title: String,
        poster: String?,
        backdrop: String?,
        logo: String?,
        season: Int?,
        episode: Int?,
        episodeName: String?,
        genres: String?,
        year: String?,
        runtime: Int?,
        contentLanguage: String?
    ) -> Unit = { _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
    onPlayManuallyClick: (
        videoId: String,
        contentType: String,
        contentId: String,
        title: String,
        poster: String?,
        backdrop: String?,
        logo: String?,
        season: Int?,
        episode: Int?,
        episodeName: String?,
        genres: String?,
        year: String?,
        runtime: Int?,
        contentLanguage: String?
    ) -> Unit = { _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
    onPlayStartFromBeginningClick: (
        videoId: String,
        contentType: String,
        contentId: String,
        title: String,
        poster: String?,
        backdrop: String?,
        logo: String?,
        season: Int?,
        episode: Int?,
        episodeName: String?,
        genres: String?,
        year: String?,
        runtime: Int?,
        contentLanguage: String?
    ) -> Unit = { _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> }
,
    /** The page's main Play/Resume button: plays straight away with the source last used. */
    onResumePlayClick: (
        videoId: String,
        contentType: String,
        contentId: String,
        title: String,
        poster: String?,
        backdrop: String?,
        logo: String?,
        season: Int?,
        episode: Int?,
        episodeName: String?,
        genres: String?,
        year: String?,
        runtime: Int?,
        contentLanguage: String?
    ) -> Unit = onPlayClick
) {
    val playbackAvailability = LocalPlaybackAvailability.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val posterCardCornerRadiusDp by viewModel.posterCardCornerRadiusDp.collectAsStateWithLifecycle()
    val effectiveAutoplayEnabled by viewModel.effectiveAutoplayEnabled.collectAsStateWithLifecycle(
        initialValue = false
    )
    val selectedComment = uiState.selectedComment
    var commentOverlayDirection by remember { mutableIntStateOf(0) }
    var restorePlayFocusAfterTrailerBackToken by rememberSaveable { mutableIntStateOf(0) }
    var restoreSharedTrailerFocusToken by rememberSaveable { mutableIntStateOf(0) }
    var isTrailerPaused by remember { mutableStateOf(false) }
    val playOnLoadConsumed = rememberSaveable { mutableStateOf(false) }
    val playOnLoadHandoffDispatched = rememberSaveable { mutableStateOf(false) }
    val playOnLoadReturnObserved = rememberSaveable { mutableStateOf(false) }
    val suppressInitialPlayOnLoadContent = playOnLoad && !playOnLoadReturnObserved.value
    val playOnLoadReturnContentReady = !uiState.isLoading && uiState.meta != null
    var playOnLoadReturnRevealRequested by remember(playOnLoad) { mutableStateOf(!playOnLoad) }
    var playOnLoadReturnContentRevealed by remember(playOnLoad) { mutableStateOf(!playOnLoad) }

    LaunchedEffect(playOnLoad, playOnLoadReturnObserved.value, playOnLoadReturnContentReady) {
        if (!playOnLoad || (playOnLoadReturnObserved.value && playOnLoadReturnContentReady)) {
            playOnLoadReturnRevealRequested = true
        }
    }
    val playOnLoadReturnContentAlpha by animateFloatAsState(
        targetValue = if (playOnLoadReturnRevealRequested) 1f else 0f,
        animationSpec = NuvioMotion.mediumTween(),
        label = "playOnLoadReturnContentAlpha",
        finishedListener = { playOnLoadReturnContentRevealed = it == 1f }
    )

    val childOverlayVisible = LocalDetailChildOverlayVisible.current
    BackHandler(enabled = !childOverlayVisible) {
        if (selectedComment != null) {
            commentOverlayDirection = 0
            viewModel.onEvent(MetaDetailsEvent.OnDismissCommentOverlay)
        } else if (uiState.isSharedTrailerOverlayVisible) {
            restoreSharedTrailerFocusToken += 1
            viewModel.onEvent(MetaDetailsEvent.OnDismissSharedTrailer)
        } else if (uiState.isTrailerPlaying) {
            restorePlayFocusAfterTrailerBackToken += 1
            isTrailerPaused = false
            viewModel.onEvent(MetaDetailsEvent.OnTrailerBackPress)
        } else {
            onBackPress()
        }
    }

    val currentIsTrailerPlaying by rememberUpdatedState(uiState.isTrailerPlaying)
    val currentShowTrailerControls by rememberUpdatedState(uiState.showTrailerControls)
    val trailerControllerFocusRequester = remember { FocusRequester() }
    var trailerSeekOverlayVisible by remember { mutableStateOf(false) }
    val trailerSeekOverlayState = remember { TrailerSeekOverlayState() }
    var trailerSeekToken by remember { mutableIntStateOf(0) }
    var trailerSeekDeltaMs by remember { mutableLongStateOf(0L) }
    var lastUserInteractionDispatchMs by remember { mutableLongStateOf(0L) }
    val onTrailerProgressChanged = remember(trailerSeekOverlayState) {
        { position: Long, duration: Long ->
            trailerSeekOverlayState.positionMs = position
            trailerSeekOverlayState.durationMs = duration
        }
    }

    LaunchedEffect(uiState.isTrailerPlaying, uiState.showTrailerControls) {
        if (uiState.isTrailerPlaying && uiState.showTrailerControls) {
            trailerControllerFocusRequester.requestFocusAfterFrames()
        }
    }

    LaunchedEffect(uiState.userMessage) {
        if (uiState.userMessage != null) {
            delay(2500)
            viewModel.onEvent(MetaDetailsEvent.OnClearMessage)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        if (
            playOnLoad &&
            playOnLoadHandoffDispatched.value &&
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        ) {
            playOnLoadReturnObserved.value = true
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    viewModel.onEvent(MetaDetailsEvent.OnLifecyclePause)
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (playOnLoad && playOnLoadHandoffDispatched.value) {
                        playOnLoadReturnObserved.value = true
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioTheme.colors.Background)
            .onPreviewKeyEvent { keyEvent ->
                if (currentIsTrailerPlaying) {
                    if (currentShowTrailerControls) {
                        if (keyEvent.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) {
                            return@onPreviewKeyEvent false
                        }
                        // The keys the trailer controls use are handled; any other goes on to the page.
                        return@onPreviewKeyEvent when (keyEvent.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_DPAD_CENTER,
                            KeyEvent.KEYCODE_ENTER,
                            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                isTrailerPaused = !isTrailerPaused
                                trailerSeekOverlayVisible = true
                                true
                            }
                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                isTrailerPaused = !isTrailerPaused
                                true
                            }
                            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                isTrailerPaused = true
                                true
                            }
                            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                                isTrailerPaused = false
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_UP -> {
                                trailerSeekOverlayVisible = true
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                trailerSeekOverlayVisible = false
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                val repeatCount = keyEvent.nativeKeyEvent.repeatCount
                                val delta = when {
                                    repeatCount >= 12 -> -12_000L
                                    repeatCount >= 6 -> -8_000L
                                    repeatCount >= 2 -> -5_000L
                                    else -> -3_000L
                                }
                                trailerSeekDeltaMs = delta
                                trailerSeekToken += 1
                                trailerSeekOverlayVisible = true
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                val repeatCount = keyEvent.nativeKeyEvent.repeatCount
                                val delta = when {
                                    repeatCount >= 12 -> 12_000L
                                    repeatCount >= 6 -> 8_000L
                                    repeatCount >= 2 -> 5_000L
                                    else -> 3_000L
                                }
                                trailerSeekDeltaMs = delta
                                trailerSeekToken += 1
                                trailerSeekOverlayVisible = true
                                true
                            }
                            else -> false
                        }
                    }
                    // During auto trailer preview, consume all keys except back/ESC so content doesn't scroll.
                    val keyCode = keyEvent.nativeKeyEvent.keyCode
                    return@onPreviewKeyEvent keyCode != KeyEvent.KEYCODE_BACK &&
                            keyCode != KeyEvent.KEYCODE_ESCAPE
                }
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    val nativeEvent = keyEvent.nativeKeyEvent
                    val shouldDispatch =
                        nativeEvent.repeatCount == 0 &&
                            (nativeEvent.eventTime - lastUserInteractionDispatchMs) >=
                            USER_INTERACTION_DISPATCH_DEBOUNCE_MS
                    if (shouldDispatch) {
                        lastUserInteractionDispatchMs = nativeEvent.eventTime
                        viewModel.onEvent(MetaDetailsEvent.OnUserInteraction)
                    }
                }
                false
            }
    ) {
        when {
            uiState.isLoading -> {
                if (playOnLoad && !playOnLoadReturnContentRevealed) {
                    PlaybackHandoffBackdrop(backdropUrl = heroBackdropUrl)
                } else {
                    if (!heroBackdropUrl.isNullOrBlank()) {
                        val localContext = LocalContext.current
                        val localDensity = LocalDensity.current
                        val configuration = LocalConfiguration.current
                        val loadingBackdropWidthPx = remember(configuration, localDensity) {
                            with(localDensity) { configuration.screenWidthDp.dp.roundToPx() }
                        }
                        val loadingBackdropHeightPx = remember(configuration, localDensity) {
                            with(localDensity) { configuration.screenHeightDp.dp.roundToPx() }
                        }
                        val loadingBackdropRequest = remember(localContext, heroBackdropUrl, loadingBackdropWidthPx, loadingBackdropHeightPx) {
                            ImageRequest.Builder(localContext)
                                .data(heroBackdropUrl)
                                .crossfade(false)
                                .size(width = loadingBackdropWidthPx, height = loadingBackdropHeightPx)
                                .build()
                        }
                        AsyncImage(
                            model = loadingBackdropRequest,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.TopEnd
                        )
                    }
                    MetaDetailsSkeleton(backdropAware = !heroBackdropUrl.isNullOrBlank())
                }
            }
            uiState.error != null -> {
                ErrorState(
                    message = uiState.error ?: stringResource(R.string.error_generic),
                    onRetry = { viewModel.onEvent(MetaDetailsEvent.OnRetry) }
                )
            }
            uiState.meta != null -> {
                val meta = uiState.meta!!
                val context = LocalContext.current
                val genresString = remember(meta.genres) {
                    meta.genres.takeIf { it.isNotEmpty() }
                        ?.joinToString(" • ") { localizedGenreLabel(context, it) }
                }
                val yearString = remember(meta.releaseInfo) {
                    formatDetailYearRange(meta.releaseInfo)
                }
                val playEpisode: (Video) -> Unit = playEpisode@{ video ->
                    if (!playbackAvailability.canStream(meta.apiType, video.id, meta.id, video)) {
                        Toast.makeText(context, R.string.playback_unavailable_message, Toast.LENGTH_SHORT).show()
                        return@playEpisode
                    }
                    onPlayClick(
                        video.id,
                        meta.apiType,
                        meta.id,
                        meta.name,
                        video.thumbnail ?: meta.poster,
                        meta.backdropUrl,
                        meta.logo,
                        video.season,
                        video.episode,
                        video.title,
                        null,
                        null,
                        video.runtime,
                        meta.resolveContentLanguage()
                    )
                }
                val playEpisodeManually: (Video) -> Unit = playEpisodeManually@{ video ->
                    if (!playbackAvailability.canStream(meta.apiType, video.id, meta.id, video)) {
                        Toast.makeText(context, R.string.playback_unavailable_message, Toast.LENGTH_SHORT).show()
                        return@playEpisodeManually
                    }
                    onPlayManuallyClick(
                        video.id,
                        meta.apiType,
                        meta.id,
                        meta.name,
                        video.thumbnail ?: meta.poster,
                        meta.backdropUrl,
                        meta.logo,
                        video.season,
                        video.episode,
                        video.title,
                        null,
                        null,
                        video.runtime,
                        meta.resolveContentLanguage()
                    )
                }
                val playTitle: (String) -> Unit = playTitle@{ videoId ->
                    if (!playbackAvailability.canStream(meta.apiType, videoId, meta.id)) {
                        Toast.makeText(context, R.string.playback_unavailable_message, Toast.LENGTH_SHORT).show()
                        return@playTitle
                    }
                    onPlayClick(
                        videoId,
                        meta.apiType,
                        meta.id,
                        meta.name,
                        meta.poster,
                        meta.backdropUrl,
                        meta.logo,
                        null,
                        null,
                        null,
                        genresString,
                        yearString,
                        null,
                        meta.resolveContentLanguage()
                    )
                }
                val playTitleManually: (String) -> Unit = playTitleManually@{ videoId ->
                    if (!playbackAvailability.canStream(meta.apiType, videoId, meta.id)) {
                        Toast.makeText(context, R.string.playback_unavailable_message, Toast.LENGTH_SHORT).show()
                        return@playTitleManually
                    }
                    onPlayManuallyClick(
                        videoId,
                        meta.apiType,
                        meta.id,
                        meta.name,
                        meta.poster,
                        meta.backdropUrl,
                        meta.logo,
                        null,
                        null,
                        null,
                        genresString,
                        yearString,
                        null,
                        meta.resolveContentLanguage()
                    )
                }
                val isSeries = remember(meta.type, meta.videos) {
                    meta.type == ContentType.SERIES || meta.videos.isNotEmpty()
                }
                val playOnLoadVideo = remember(meta, uiState.nextToWatch, uiState.episodesForSeason) {
                    resolveHeroPlaybackVideo(
                        meta = meta,
                        nextToWatch = uiState.nextToWatch,
                        episodesForSeason = uiState.episodesForSeason
                    )
                }

                LaunchedEffect(
                    playOnLoad,
                    playOnLoadManually,
                    playOnLoadConsumed.value,
                    isSeries,
                    uiState.nextToWatch,
                    playOnLoadVideo?.id,
                    playbackAvailability
                ) {
                    if (!playOnLoad || playOnLoadConsumed.value || (isSeries && uiState.nextToWatch == null)) {
                        return@LaunchedEffect
                    }
                    if (!playbackAvailability.isLoaded) return@LaunchedEffect
                    playOnLoadConsumed.value = true
                    if (uiState.shufflePoolEmpty) {
                        playOnLoadReturnObserved.value = true
                        return@LaunchedEffect
                    }
                    if (!playbackAvailability.canStream(meta.apiType, playOnLoadVideo?.id ?: meta.id, meta.id, playOnLoadVideo)) {
                        playOnLoadReturnObserved.value = true
                        Toast.makeText(context, R.string.playback_unavailable_message, Toast.LENGTH_SHORT).show()
                        return@LaunchedEffect
                    }
                    playOnLoadHandoffDispatched.value = true
                    if (playOnLoadVideo != null) {
                        if (playOnLoadManually) {
                            playEpisodeManually(playOnLoadVideo)
                        } else {
                            playEpisode(playOnLoadVideo)
                        }
                    } else if (playOnLoadManually) {
                        playTitleManually(meta.id)
                    } else {
                        playTitle(meta.id)
                    }
                }

                if (suppressInitialPlayOnLoadContent) {
                    PlaybackHandoffBackdrop(backdropUrl = heroBackdropUrl ?: meta.backdropUrl)
                    return@Box
                }
                if (playOnLoad && !playOnLoadReturnContentRevealed) {
                    PlaybackHandoffBackdrop(backdropUrl = heroBackdropUrl ?: meta.backdropUrl)
                }
                run {
                    val heroPlayable = playbackAvailability.canStream(
                        meta.apiType,
                        playOnLoadVideo?.id ?: meta.id,
                        meta.id,
                        playOnLoadVideo
                    )
                    val newLookCast = remember(meta.castMembers, meta.cast) {
                        newLookCastOrder(
                            meta.castMembers.ifEmpty { meta.cast.map { name -> MetaCastMember(name = name) } }
                        )
                    }
                    Box(modifier = Modifier.graphicsLayer { alpha = playOnLoadReturnContentAlpha }) {
                        NewLookDetailsContent(
                            meta = meta,
                            seasons = uiState.seasons,
                            selectedSeason = uiState.selectedSeason,
                            episodesForSeason = uiState.episodesForSeason,
                            episodeProgressMap = uiState.episodeProgressMap,
                            watchedEpisodes = uiState.watchedEpisodes,
                            episodeImdbRatings = uiState.episodeImdbRatings,
                            mdbListRatings = uiState.mdbListRatings?.takeIf { uiState.isMdbListRatingsActive },
                            mdbListRatingOrder = uiState.mdbListRatingOrder,
                            isInLibrary = uiState.isInLibrary,
                            playText = uiState.nextToWatch?.displayText,
                            isPlayEnabled = heroPlayable,
                            moreLikeThis = uiState.moreLikeThis,
                            cast = newLookCast,
                            onPlay = {
                                val video = if (isSeries) playOnLoadVideo else null
                                if (video != null) {
                                    onResumePlayClick(
                                        video.id, meta.apiType, meta.id, meta.name,
                                        video.thumbnail ?: meta.poster, meta.backdropUrl, meta.logo,
                                        video.season, video.episode, video.title, null, null,
                                        video.runtime, meta.resolveContentLanguage()
                                    )
                                } else {
                                    onResumePlayClick(
                                        meta.id, meta.apiType, meta.id, meta.name,
                                        meta.poster, meta.backdropUrl, meta.logo,
                                        null, null, null, genresString, yearString,
                                        null, meta.resolveContentLanguage()
                                    )
                                }
                            },
                            onPlayLongPress = {
                                val video = if (isSeries) playOnLoadVideo else null
                                if (video != null) playEpisodeManually(video) else playTitleManually(meta.id)
                            },
                            onTrailer = meta.trailers.firstOrNull()?.let { trailer ->
                                { viewModel.onEvent(MetaDetailsEvent.OnSharedTrailerSelected(trailer)) }
                            },
                            onToggleLibrary = { viewModel.onEvent(MetaDetailsEvent.OnToggleLibrary) },
                            onLibraryLongPress = { viewModel.onEvent(MetaDetailsEvent.OnLibraryLongPress) },
                            onSeasonSelected = { viewModel.onEvent(MetaDetailsEvent.OnSeasonSelected(it)) },
                            onEpisodeClick = playEpisode,
                            onEpisodeLongPress = { video ->
                                viewModel.onEvent(MetaDetailsEvent.OnToggleEpisodeWatched(video))
                            },
                            onCastClick = { member ->
                                member.tmdbId?.let { id ->
                                    val preferCrew = listOf("Creator", "Director", "Writer")
                                        .any { member.character.equals(it, ignoreCase = true) }
                                    onNavigateToCastDetail(id, member.name, preferCrew)
                                }
                            },
                            onMoreLikeThisClick = { item ->
                                onNavigateToDetail(item.id, item.apiType, null)
                            }
                        )
                        if (uiState.isSharedTrailerOverlayVisible) {
                            SharedTrailerOverlay(
                                title = uiState.selectedSharedTrailer?.name?.takeIf { it.isNotBlank() }
                                    ?: stringResource(R.string.detail_tab_trailer),
                                trailerUrl = uiState.sharedTrailerUrl,
                                trailerAudioUrl = uiState.sharedTrailerAudioUrl,
                                isLoading = uiState.isSharedTrailerLoading,
                                errorMessage = uiState.sharedTrailerErrorMessage,
                                onDismiss = { viewModel.onEvent(MetaDetailsEvent.OnDismissSharedTrailer) },
                                onRetry = { viewModel.onEvent(MetaDetailsEvent.OnRetrySharedTrailer) }
                            )
                        }
                    }
                }
            }
        }

        if (uiState.showListPicker) {
            val nuvioListTab = LibraryListTab(
                key = LOCAL_LIBRARY_LIST_KEY,
                title = stringResource(R.string.trakt_library_source_nuvio),
                type = LibraryListTab.Type.WATCHLIST
            )
            val contentType = uiState.meta?.apiType?.lowercase().orEmpty()
            val providerTabs = uiState.libraryListTabs.filter { tab ->
                tab.supportsMembershipFor(contentType)
            }
            val combinedTabs = listOf(nuvioListTab) + providerTabs
            PosterListPickerDialog(
                title = uiState.meta?.name ?: stringResource(R.string.detail_lists_fallback),
                tabs = combinedTabs,
                membership = uiState.pickerMembership,
                isPending = uiState.pickerPending,
                error = uiState.pickerError,
                onToggle = { key ->
                    viewModel.onEvent(MetaDetailsEvent.OnPickerMembershipToggled(key))
                },
                onSave = { viewModel.onEvent(MetaDetailsEvent.OnPickerSave) },
                onDismiss = { viewModel.onEvent(MetaDetailsEvent.OnPickerDismiss) }
            )
        }

        if (uiState.removalConfirmations.isNotEmpty()) {
            TrackingRemovalConfirmationDialog(
                itemTitle = uiState.meta?.name.orEmpty(),
                confirmations = uiState.removalConfirmations,
                isPending = uiState.pickerPending || uiState.defaultLibraryTogglePending,
                onConfirm = { viewModel.onEvent(MetaDetailsEvent.OnRemovalConfirmed) },
                onDismiss = { viewModel.onEvent(MetaDetailsEvent.OnRemovalCancelled) }
            )
        }

        val message = uiState.userMessage
        if (!message.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = NuvioTheme.spacing.xl)
                    .background(
                        color = if (uiState.userMessageIsError) {
                            Color(0xFF5A1C1C)
                        } else {
                            NuvioTheme.colors.BackgroundElevated
                        },
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NuvioTheme.colors.TextPrimary
                )
            }
        }

        if (uiState.isTrailerPlaying && uiState.showTrailerControls) {
            // This surface exists only to capture remote keys (back/play/pause/seek) while the
            // trailer is playing, so it has no visible label of its own to announce.
            val trailerControlsDescription = stringResource(R.string.cd_trailer_controls)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(trailerControllerFocusRequester)
                    .focusable()
                    .semantics { contentDescription = trailerControlsDescription }
            )
        }

        TrailerSeekOverlayHost(
            visible = uiState.isTrailerPlaying && uiState.showTrailerControls && trailerSeekOverlayVisible,
            overlayState = trailerSeekOverlayState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    LaunchedEffect(trailerSeekOverlayVisible, uiState.isTrailerPlaying, uiState.showTrailerControls, trailerSeekToken) {
        if (trailerSeekOverlayVisible && uiState.isTrailerPlaying && uiState.showTrailerControls) {
            delay(3000)
            trailerSeekOverlayVisible = false
        }
    }

    LaunchedEffect(uiState.isTrailerPlaying, uiState.showTrailerControls) {
        if (!uiState.isTrailerPlaying || !uiState.showTrailerControls) {
            trailerSeekOverlayVisible = false
        }
    }

    val posterOptionsState by viewModel.posterOptions.state.collectAsStateWithLifecycle()
    com.nuvio.tv.ui.components.posteroptions.PosterOptionsHost(
        state = posterOptionsState,
        controller = viewModel.posterOptions,
        onNavigateToDetail = { id, type, addonBaseUrl ->
            onNavigateToDetail(id, type, addonBaseUrl.takeIf { it.isNotBlank() })
        }
    )
}

@Composable
private fun PlaybackHandoffBackdrop(backdropUrl: String?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (!backdropUrl.isNullOrBlank()) {
            val context = LocalContext.current
            val density = LocalDensity.current
            val configuration = LocalConfiguration.current
            val widthPx = remember(configuration, density) {
                with(density) { configuration.screenWidthDp.dp.roundToPx() }
            }
            val heightPx = remember(configuration, density) {
                with(density) { configuration.screenHeightDp.dp.roundToPx() }
            }
            val request = remember(context, backdropUrl, widthPx, heightPx) {
                ImageRequest.Builder(context)
                    .data(backdropUrl)
                    .crossfade(false)
                    .size(width = widthPx, height = heightPx)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopEnd
            )
        }
    }
}
