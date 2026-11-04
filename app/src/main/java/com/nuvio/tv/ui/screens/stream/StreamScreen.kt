@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.stream

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import android.view.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.nuvio.tv.core.party.PartySource
import com.nuvio.tv.core.party.matches
import com.nuvio.tv.ui.util.contentTextDirection
import com.nuvio.tv.ui.util.toAbsoluteAlignment
import com.nuvio.tv.ui.util.localizeEpisodeTitle
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.core.streams.StreamBadgePlacement
import com.nuvio.tv.core.streams.StreamBadgeSettings
import com.nuvio.tv.data.local.PlayerPreference
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.ui.components.SourceChipItem
import com.nuvio.tv.ui.components.SourceChipStatus
import com.nuvio.tv.ui.components.P2pConsentDialog
import com.nuvio.tv.ui.components.StreamBadgeChips
import com.nuvio.tv.ui.components.StreamsSkeletonList
import com.nuvio.tv.ui.screens.player.LoadingOverlay
import com.nuvio.tv.ui.screens.player.AddonFilterChips
import com.nuvio.tv.ui.screens.player.focusChip
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.navigation.sourceSelectionRestoreTarget
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay as coroutineDelay
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch as coroutineLaunch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.nuvio.tv.R
import android.util.Log


@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun StreamScreen(
    viewModel: StreamScreenViewModel = hiltViewModel(),
    startFromBeginning: Boolean = false,
    restoreSourceSelection: Boolean = false,
    onSourceSelectionRestoreHandled: () -> Unit = {},
    onBackPress: () -> Unit,
    onStreamSelected: (StreamPlaybackInfo) -> Unit,
    onAutoPlayResolved: (StreamPlaybackInfo) -> Unit,
    onNavigateToCastDetail: (personId: Int, personName: String) -> Unit = { _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val watchProgress by viewModel.watchProgress.collectAsStateWithLifecycle()
    val resumeProgress = streamResumeProgress(watchProgress, startFromBeginning)
    val resumePositionMs = resumeProgress?.position?.takeIf { it > 0L }
    val resumePercent = resumeProgress?.progressPercentage?.takeIf { it > 0f }
    val resumeText = when {
        resumePositionMs != null -> stringResource(
            R.string.stream_resume_from_time,
            com.nuvio.tv.ui.screens.detail.formatPlaybackTime(resumePositionMs)
        )
        resumePercent != null -> stringResource(R.string.stream_resume_from_percent, (resumePercent * 100).toInt())
        else -> null
    }
    val playerPreference by viewModel.playerPreference.collectAsStateWithLifecycle(
        initialValue = null
    )
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    var restoreFocusedStream by rememberSaveable { mutableStateOf(false) }
    var pendingRestoreOnResume by rememberSaveable { mutableStateOf(false) }
    var showPlayerChoiceDialog by remember { mutableStateOf(false) }
    var pendingPlaybackInfo by remember { mutableStateOf<StreamPlaybackInfo?>(null) }
    var showP2pConsentDialog by remember { mutableStateOf(false) }
    var pendingTorrentPlaybackInfo by remember { mutableStateOf<StreamPlaybackInfo?>(null) }
    val p2pEnabled by viewModel.p2pEnabled.collectAsStateWithLifecycle(initialValue = false)
    val scope = rememberCoroutineScope()

    LaunchedEffect(restoreSourceSelection) {
        if (restoreSourceSelection) {
            pendingRestoreOnResume = false
            restoreFocusedStream = true
        }
    }

    fun launchExternalPlayer(playbackInfo: StreamPlaybackInfo) {
        val url = playbackInfo.url ?: if (playbackInfo.isTorrent) "torrent://${playbackInfo.infoHash}" else return
        scope.coroutineLaunch {
            viewModel.launchExternalPlayer(
                playbackInfo = playbackInfo,
                url = url,
                startFromBeginning = startFromBeginning,
                context = context
            )
        }
    }

    fun openExternalInBrowser(playbackInfo: StreamPlaybackInfo): Boolean {
        if (!playbackInfo.isExternal) return false
        val url = playbackInfo.url?.takeIf { it.isNotBlank() } ?: return false
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        val openedInBrowser = runCatching {
            context.startActivity(browserIntent)
        }.isSuccess
        if (!openedInBrowser) {
            // The external player records the provider after its own launch succeeds.
            launchExternalPlayer(playbackInfo)
            return true
        }
        viewModel.rememberPlayedPlugin(playbackInfo.bingeGroup)
        return true
    }

    fun launchInternalPlayer(playbackInfo: StreamPlaybackInfo) {
        viewModel.onInternalPlayerLaunching()
        onStreamSelected(playbackInfo)
    }

    fun routePlayback(playbackInfo: StreamPlaybackInfo) {
        if (openExternalInBrowser(playbackInfo)) {
            return
        }
        val preference = if (playbackInfo.isServerStream) PlayerPreference.INTERNAL else playerPreference ?: return
        if (playbackInfo.isTorrent && !p2pEnabled) {
            pendingTorrentPlaybackInfo = playbackInfo
            showP2pConsentDialog = true
            return
        }
        when (preference) {
            PlayerPreference.INTERNAL -> {
                launchInternalPlayer(playbackInfo)
            }
            PlayerPreference.EXTERNAL -> {
                if (playbackInfo.url != null || playbackInfo.isTorrent) {
                    launchExternalPlayer(playbackInfo)
                }
            }
            PlayerPreference.ASK_EVERY_TIME -> {
                pendingPlaybackInfo = playbackInfo
                showPlayerChoiceDialog = true
            }
        }
    }

    fun routeAutoPlay(playbackInfo: StreamPlaybackInfo) {
        if (openExternalInBrowser(playbackInfo)) {
            viewModel.onEvent(StreamScreenEvent.OnAutoPlayConsumed)
            return
        }
        // Always check P2P consent for torrents, even in direct auto-play flow
        if (playbackInfo.isTorrent && !p2pEnabled) {
            pendingTorrentPlaybackInfo = playbackInfo
            showP2pConsentDialog = true
            return
        }
        val preference = if (playbackInfo.isServerStream) PlayerPreference.INTERNAL else playerPreference ?: return
        if (uiState.isDirectAutoPlayFlow) {
            // Respect player preference even in direct autoplay flow
            when (preference) {
                PlayerPreference.EXTERNAL -> {
                    val url = playbackInfo.url ?: if (playbackInfo.isTorrent) "torrent://${playbackInfo.infoHash}" else null
                    url?.let { urlString ->
                        scope.coroutineLaunch {
                            viewModel.launchExternalPlayer(
                                playbackInfo = playbackInfo,
                                url = urlString,
                                startFromBeginning = startFromBeginning,
                                autoLaunch = true,
                                context = context
                            )
                            // Delay pop so external player appears on top
                            coroutineDelay(1000)
                            viewModel.onEvent(StreamScreenEvent.OnAutoPlayConsumed)
                            onBackPress()
                        }
                    }
                }
                PlayerPreference.ASK_EVERY_TIME -> {
                    pendingPlaybackInfo = playbackInfo
                    showPlayerChoiceDialog = true
                    viewModel.onEvent(StreamScreenEvent.OnAutoPlayConsumed)
                }
                else -> {
                    viewModel.onInternalPlayerLaunching()
                    onAutoPlayResolved(playbackInfo)
                }
            }
            return
        } else {
            pendingRestoreOnResume = true
            routePlayback(playbackInfo)
            viewModel.onEvent(StreamScreenEvent.OnAutoPlayConsumed)
        }
    }

    BackHandler {
        onBackPress()
    }

    LaunchedEffect(uiState.autoPlayStream) {
        val stream = uiState.autoPlayStream ?: return@LaunchedEffect
        // User aborted the auto-next chain that navigated here — don't auto-launch; show the list.
        if (viewModel.isAutoNextContinuationAborted()) {
            viewModel.consumeAbortedAutoNextContinuation()
            viewModel.onEvent(StreamScreenEvent.OnAutoPlayConsumed)
            return@LaunchedEffect
        }
        val playbackInfo = viewModel.resolveStreamForPlayback(stream)
        if (playbackInfo == null) {
            viewModel.onEvent(StreamScreenEvent.OnAutoPlayConsumed)
            return@LaunchedEffect
        }
        // Torrent streams have url == null but carry an infoHash; navigation
        // builds a torrent:// sentinel URL downstream.
        if (playbackInfo.url != null || (playbackInfo.isTorrent && playbackInfo.infoHash != null)) {
            viewModel.awaitStreamLinkCacheSave()
            routeAutoPlay(playbackInfo)
        }
    }

    LaunchedEffect(uiState.playbackErrorMessage) {
        val message = uiState.playbackErrorMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.onPlaybackErrorShown()
    }

    // Once streams are resolved, release the MainActivity auto-next loader so it doesn't
    // mask this screen (whether it auto-launches a player or shows the manual list).
    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading) {
            viewModel.dismissExternalAutoNextOverlay(
                forceRelease = !uiState.isDirectAutoPlayFlow ||
                    playerPreference != PlayerPreference.EXTERNAL
            )
        }
    }

    LaunchedEffect(uiState.autoPlayPlaybackInfo) {
        val playbackInfo = uiState.autoPlayPlaybackInfo ?: return@LaunchedEffect
        // User aborted the auto-next chain that navigated here — don't auto-launch; show the list.
        if (viewModel.isAutoNextContinuationAborted()) {
            viewModel.consumeAbortedAutoNextContinuation()
            viewModel.onEvent(StreamScreenEvent.OnAutoPlayConsumed)
            return@LaunchedEffect
        }
        if (playbackInfo.url != null || (playbackInfo.isTorrent && playbackInfo.infoHash != null)) {
            // Torrent cached links still need P2P consent
            if (playbackInfo.isTorrent && !p2pEnabled) {
                pendingTorrentPlaybackInfo = playbackInfo
                showP2pConsentDialog = true
                return@LaunchedEffect
            }
            // Respect player preference for cached links too
            when (playerPreference ?: return@LaunchedEffect) {
                PlayerPreference.EXTERNAL -> {
                    val url = playbackInfo.url ?: if (playbackInfo.isTorrent) "torrent://${playbackInfo.infoHash}" else null
                    url?.let { urlString ->
                        Log.d("StreamScreen", "autoPlayPlaybackInfo EXTERNAL: launching player, will pop after 800ms")
                        viewModel.launchExternalPlayer(
                            playbackInfo = playbackInfo,
                            url = urlString,
                            startFromBeginning = startFromBeginning,
                            autoLaunch = true,
                            context = context
                        )
                    }
                    // Delay pop so external player appears on top, keep overlay visible
                    coroutineDelay(1000)
                    Log.d("StreamScreen", "autoPlayPlaybackInfo EXTERNAL: popping now")
                    viewModel.onEvent(StreamScreenEvent.OnAutoPlayConsumed)
                    onBackPress()
                }
                PlayerPreference.ASK_EVERY_TIME -> {
                    pendingPlaybackInfo = playbackInfo
                    showPlayerChoiceDialog = true
                    viewModel.onEvent(StreamScreenEvent.OnAutoPlayConsumed)
                }
                else -> {
                    viewModel.onInternalPlayerLaunching()
                    onAutoPlayResolved(playbackInfo)
                    viewModel.onEvent(StreamScreenEvent.OnAutoPlayConsumed)
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Always dismiss overlay and stop tracking on resume
                // covers both ActivityResult path and fire-and-forget path.
                viewModel.stopExternalPlayerTracking()
                viewModel.onEvent(StreamScreenEvent.OnResume)
                if (pendingRestoreOnResume) {
                    restoreFocusedStream = true
                    pendingRestoreOnResume = false
                }
            } else if (event == Lifecycle.Event.ON_STOP) {
                // Backgrounded by the external player: playback behind it is healthy,
                // so the stuck-loader timeout must not treat it as stuck.
                viewModel.onHostStopped()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val showOverlay = uiState.showDirectAutoPlayOverlay || uiState.externalPlayerOverlayVisible

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Full screen backdrop
        StreamBackdrop(
            backdrop = uiState.backdrop ?: uiState.poster,
            isLoading = uiState.isLoading
        )

        if (!uiState.autoPlayDecided) {
            // Don't render overlay or stream list until ViewModel decides
            // whether direct autoplay is active — prevents single-frame flash.
        } else if (showOverlay) {
            LoadingOverlay(
                visible = true,
                backdropUrl = uiState.backdrop ?: uiState.poster,
                logoUrl = uiState.logo,
                title = uiState.title,
                message = if (uiState.directAutoPlayMessage != null) {
                    uiState.directAutoPlayMessage
                } else {
                    null
                },
                progress = uiState.directAutoPlayProgress,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            NewLookStreamContent(
                uiState = uiState,
                resumeText = resumeText,
                shouldRestoreFocus = restoreFocusedStream,
                onRestoreFocusHandled = {
                    restoreFocusedStream = false
                    if (restoreSourceSelection) {
                        onSourceSelectionRestoreHandled()
                    }
                },
                onStreamSelected = { stream ->
                    scope.coroutineLaunch {
                        val playbackInfo = viewModel.resolveStreamForPlayback(stream)
                        if (playbackInfo != null) {
                            viewModel.persistBingeGroupForStream(stream)
                            pendingRestoreOnResume = true
                            routePlayback(playbackInfo)
                            viewModel.onEvent(StreamScreenEvent.OnAutoPlayConsumed)
                        }
                    }
                },
                onRetry = { viewModel.onEvent(StreamScreenEvent.OnRetry) },
                onExpandStreams = { viewModel.expandFilteredStreamsIfNeeded() }
            )
        }

        // Player choice dialog for "Ask every time" preference
        if (showPlayerChoiceDialog && pendingPlaybackInfo != null) {
            PlayerChoiceDialog(
                onInternalSelected = {
                    showPlayerChoiceDialog = false
                    pendingPlaybackInfo?.let { launchInternalPlayer(it) }
                    pendingPlaybackInfo = null
                },
                onExternalSelected = {
                    showPlayerChoiceDialog = false
                    pendingPlaybackInfo?.let { info ->
                        if (info.url != null || info.isTorrent) {
                            launchExternalPlayer(info)
                        }
                    }
                    pendingPlaybackInfo = null
                },
                onDismiss = {
                    showPlayerChoiceDialog = false
                    pendingPlaybackInfo = null
                }
            )
        }

        if (showP2pConsentDialog && pendingTorrentPlaybackInfo != null) {
            P2pConsentDialog(
                onEnableP2p = {
                    viewModel.enableP2p()
                    showP2pConsentDialog = false
                    val info = pendingTorrentPlaybackInfo!!
                    pendingTorrentPlaybackInfo = null
                    routePlayback(info)
                },
                onDismiss = {
                    showP2pConsentDialog = false
                    pendingTorrentPlaybackInfo = null
                    // Cancelled P2P consent — fall back to manual stream selection
                    viewModel.onEvent(StreamScreenEvent.OnAutoPlayConsumed)
                }
            )
        }

    }
}

@Composable
internal fun StreamBackdrop(
    backdrop: String?,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val backgroundColor = NuvioTheme.colors.Background
    val backdropModel = remember(context, backdrop) {
        backdrop?.let { image ->
            ImageRequest.Builder(context)
                .data(image)
                .crossfade(false)
                .build()
        }
    }
    val imageAlpha by animateFloatAsState(
        targetValue = if (isLoading) 0.7f else 0.5f,
        animationSpec = tween(500),
        label = "backdrop_image_alpha"
    )

    Box(modifier = modifier
        .fillMaxSize()
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    ) {
        // Backdrop image
        if (backdropModel != null) {
            AsyncImage(
                model = backdropModel,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = imageAlpha },
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopEnd
            )
        }

        StreamGradientLayer(
            bgColor = backgroundColor,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun StreamGradientLayer(
    bgColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .drawWithCache {
                val combinedGradient = Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0.0f to bgColor,
                        0.15f to bgColor.copy(alpha = 0.85f),
                        0.30f to bgColor.copy(alpha = 0.40f),
                        0.50f to bgColor.copy(alpha = 0.15f),
                        0.70f to bgColor.copy(alpha = 0.40f),
                        0.85f to bgColor.copy(alpha = 0.85f),
                        1.0f to bgColor
                    ),
                    startX = 0f,
                    endX = size.width
                )
                onDrawBehind {
                    drawRect(brush = combinedGradient)
                }
            }
    )
}

@Composable
internal fun PlayerChoiceDialog(
    onInternalSelected: () -> Unit,
    onExternalSelected: () -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(NuvioTheme.radii.xl))
                .background(NuvioTheme.colors.BackgroundCard)
        ) {
            Column(
                modifier = Modifier
                    .width(400.dp)
                    .padding(NuvioTheme.spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.stream_player_picker_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = NuvioTheme.colors.TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(NuvioTheme.spacing.xl))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.lg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    var internalFocused by remember { mutableStateOf(false) }
                    Card(
                        onClick = onInternalSelected,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                            .onFocusChanged { internalFocused = it.isFocused },
                        colors = CardDefaults.colors(
                            containerColor = NuvioTheme.colors.BackgroundElevated,
                            focusedContainerColor = NuvioTheme.colors.Secondary
                        ),
                        border = CardDefaults.border(
                            focusedBorder = Border(
                                border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
                                shape = RoundedCornerShape(NuvioTheme.radii.md)
                            )
                        ),
                        shape = CardDefaults.shape(shape = RoundedCornerShape(NuvioTheme.radii.md)),
                        scale = CardDefaults.scale(focusedScale = 1.05f)
                    ) {
                        Text(
                            text = stringResource(R.string.stream_player_internal),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (internalFocused) NuvioTheme.colors.OnSecondary else NuvioTheme.colors.TextPrimary,
                            modifier = Modifier
                                .padding(horizontal = NuvioTheme.spacing.lg, vertical = 14.dp)
                                .fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }

                    var externalFocused by remember { mutableStateOf(false) }
                    Card(
                        onClick = onExternalSelected,
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { externalFocused = it.isFocused },
                        colors = CardDefaults.colors(
                            containerColor = NuvioTheme.colors.BackgroundElevated,
                            focusedContainerColor = NuvioTheme.colors.Secondary
                        ),
                        border = CardDefaults.border(
                            focusedBorder = Border(
                                border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
                                shape = RoundedCornerShape(NuvioTheme.radii.md)
                            )
                        ),
                        shape = CardDefaults.shape(shape = RoundedCornerShape(NuvioTheme.radii.md)),
                        scale = CardDefaults.scale(focusedScale = 1.05f)
                    ) {
                        Text(
                            text = stringResource(R.string.stream_player_external),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (externalFocused) NuvioTheme.colors.OnSecondary else NuvioTheme.colors.TextPrimary,
                            modifier = Modifier
                                .padding(horizontal = NuvioTheme.spacing.lg, vertical = 14.dp)
                                .fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
