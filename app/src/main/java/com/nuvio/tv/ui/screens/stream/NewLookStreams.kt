package com.nuvio.tv.ui.screens.stream

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.ui.screens.detail.NewLookBackdrop
import com.nuvio.tv.ui.screens.detail.NewLookChip
import com.nuvio.tv.ui.screens.detail.NewLookChipText
import com.nuvio.tv.ui.screens.detail.NewLookFocusedText
import com.nuvio.tv.ui.screens.detail.NewLookPill
import com.nuvio.tv.ui.screens.detail.NewLookPrimaryButton
import com.nuvio.tv.ui.screens.detail.NewLookThumbnail
import com.nuvio.tv.ui.screens.detail.formatNewLookRuntime
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.util.contentTextDirection

private enum class SourceFilter { ALL, UHD, FULL_HD, HD_AND_LOWER, SMALLEST }

private val CachedGreen = Color(0xFF3DDC97)
private const val EXPAND_WHEN_WITHIN = 5

private fun streamKey(stream: Stream): String =
    listOf(stream.addonName, stream.url, stream.infoHash, stream.fileIdx?.toString(), stream.ytId, stream.serverTarget?.toString())
        .joinToString("|")

/**
 * New-look source picker: the top source as a big card with Play, quick quality filters,
 * and every source written as quality, features, size and add-on instead of a raw file name.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun NewLookStreamContent(
    uiState: StreamScreenUiState,
    resumeText: String?,
    shouldRestoreFocus: Boolean,
    onRestoreFocusHandled: () -> Unit,
    onStreamSelected: (Stream) -> Unit,
    onRetry: () -> Unit,
    onExpandStreams: () -> Unit
) {
    var filter by rememberSaveable { mutableStateOf(SourceFilter.ALL) }
    var focusedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val streams = uiState.filteredStreams
    val summaries = remember(streams) { streams.associateBy(::streamKey).mapValues { summarizeStream(it.value) } }
    val counts = remember(summaries) {
        summaries.values.groupingBy { it.quality }.eachCount()
    }
    val shown = remember(streams, summaries, filter) {
        when (filter) {
            SourceFilter.ALL -> streams
            SourceFilter.UHD -> streams.filter { summaries[streamKey(it)]?.quality == StreamQualityGroup.UHD }
            SourceFilter.FULL_HD -> streams.filter { summaries[streamKey(it)]?.quality == StreamQualityGroup.FULL_HD }
            SourceFilter.HD_AND_LOWER -> streams.filter {
                summaries[streamKey(it)]?.quality in setOf(StreamQualityGroup.HD, StreamQualityGroup.SD)
            }
            SourceFilter.SMALLEST -> streams.sortedBy { summaries[streamKey(it)]?.sizeBytes ?: Long.MAX_VALUE }
        }
    }
    val listState = rememberLazyListState()
    val requesters = remember { HashMap<String, FocusRequester>() }

    // Filters look at every source, not just the first page.
    LaunchedEffect(filter, streams.size) {
        if (filter != SourceFilter.ALL) onExpandStreams()
    }
    LaunchedEffect(filter) { listState.scrollToItem(0) }

    // Land on the top source once it arrives, or back on the one that was played.
    var initialFocusDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(shown.isNotEmpty(), shouldRestoreFocus) {
        if (shown.isEmpty()) return@LaunchedEffect
        if (initialFocusDone && !shouldRestoreFocus) return@LaunchedEffect
        val index = focusedKey?.let { key -> shown.indexOfFirst { streamKey(it) == key } }?.takeIf { it >= 0 } ?: 0
        listState.scrollToItem(index)
        withFrameNanos { }
        requesters[streamKey(shown[index])]?.let { runCatching { it.requestFocus() } }
        initialFocusDone = true
        if (shouldRestoreFocus) onRestoreFocusHandled()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // The screen's own backdrop is already drawn underneath; this only darkens it.
        NewLookBackdrop(url = null)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 48.dp, end = 40.dp, top = 36.dp),
            horizontalArrangement = Arrangement.spacedBy(36.dp)
        ) {
            NewLookStreamInfo(
                uiState = uiState,
                resumeText = resumeText,
                modifier = Modifier.weight(0.34f).fillMaxHeight()
            )
            Column(modifier = Modifier.weight(0.66f).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.new_look_choose_source),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = NuvioTheme.colors.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    val status = when {
                        uiState.isLoading && streams.isEmpty() -> stringResource(R.string.new_look_finding_sources)
                        uiState.isLoading -> stringResource(R.string.new_look_sources_found_more, streams.size)
                        else -> androidx.compose.ui.res.pluralStringResource(R.plurals.new_look_sources_count, streams.size, streams.size)
                    }
                    Text(text = status, fontSize = 13.sp, color = NuvioTheme.colors.TextSecondary)
                }
                if (streams.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        val filters = buildList {
                            add(SourceFilter.ALL)
                            if ((counts[StreamQualityGroup.UHD] ?: 0) > 0) add(SourceFilter.UHD)
                            if ((counts[StreamQualityGroup.FULL_HD] ?: 0) > 0) add(SourceFilter.FULL_HD)
                            if ((counts[StreamQualityGroup.HD] ?: 0) + (counts[StreamQualityGroup.SD] ?: 0) > 0) add(SourceFilter.HD_AND_LOWER)
                            if (summaries.values.count { it.sizeBytes != null } > 1) add(SourceFilter.SMALLEST)
                        }
                        items(filters, key = { it.name }) { option ->
                            NewLookPill(
                                text = sourceFilterLabel(option),
                                selected = option == filter,
                                onClick = { filter = option }
                            )
                        }
                    }
                }
                when {
                    streams.isEmpty() && uiState.isLoading -> NewLookSourcesMessage(text = stringResource(R.string.new_look_finding_sources))
                    streams.isEmpty() && uiState.error != null -> NewLookSourcesMessage(
                        text = uiState.error,
                        actionText = stringResource(R.string.new_look_try_again),
                        onAction = onRetry
                    )
                    streams.isEmpty() -> NewLookSourcesMessage(
                        text = stringResource(R.string.new_look_no_sources),
                        actionText = stringResource(R.string.new_look_try_again),
                        onAction = onRetry
                    )
                    else -> LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 40.dp, start = 4.dp, end = 6.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(shown, key = { _, stream -> streamKey(stream) }) { index, stream ->
                            val key = streamKey(stream)
                            val summary = summaries[key] ?: summarizeStream(stream)
                            val requester = requesters.getOrPut(key) { FocusRequester() }
                            NewLookSourceRow(
                                stream = stream,
                                summary = summary,
                                highlighted = index == 0,
                                topPick = index == 0 && filter == SourceFilter.ALL,
                                onClick = {
                                    focusedKey = key
                                    onStreamSelected(stream)
                                },
                                modifier = Modifier
                                    .focusRequester(requester)
                                    .onFocusChanged { state ->
                                        if (state.isFocused) {
                                            focusedKey = key
                                            if (filter == SourceFilter.ALL && index >= shown.size - EXPAND_WHEN_WITHIN) onExpandStreams()
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun sourceFilterLabel(filter: SourceFilter): String = when (filter) {
    SourceFilter.ALL -> stringResource(R.string.new_look_filter_all)
    SourceFilter.UHD -> "4K"
    SourceFilter.FULL_HD -> "1080p"
    SourceFilter.HD_AND_LOWER -> stringResource(R.string.new_look_filter_720_lower)
    SourceFilter.SMALLEST -> stringResource(R.string.new_look_filter_smallest)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewLookStreamInfo(uiState: StreamScreenUiState, resumeText: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        var logoFailed by remember(uiState.logo) { mutableStateOf(false) }
        if (!uiState.logo.isNullOrBlank() && !logoFailed) {
            AsyncImage(
                model = uiState.logo,
                contentDescription = uiState.title,
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
                onError = { logoFailed = true },
                modifier = Modifier.height(64.dp).fillMaxWidth(0.85f)
            )
        } else {
            Text(
                text = uiState.title,
                style = androidx.tv.material3.MaterialTheme.typography.headlineSmall.copy(textDirection = uiState.title.contentTextDirection()),
                fontWeight = FontWeight.Bold,
                color = NuvioTheme.colors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (uiState.isEpisode) {
            val code = stringResource(R.string.season_episode_format, uiState.season ?: 0, uiState.episode ?: 0)
            val heading = uiState.episodeName?.takeIf { it.isNotBlank() }?.let { "$code · $it" } ?: code
            Text(
                text = heading,
                style = androidx.tv.material3.MaterialTheme.typography.titleMedium.copy(textDirection = heading.contentTextDirection()),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = NuvioTheme.colors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            uiState.runtime?.takeIf { it > 0 }?.let { NewLookChipText(text = formatNewLookRuntime(it)) }
            uiState.year?.takeIf { it.isNotBlank() }?.let { NewLookChipText(text = it) }
            uiState.genres?.split(" • ", ",")?.map { it.trim() }?.filter { it.isNotBlank() }?.take(2)?.forEach { NewLookChipText(text = it) }
        }
        if (uiState.isEpisode && !uiState.poster.isNullOrBlank()) {
            // The episode still when there is one; a tall poster in its place gives way to the
            // show's wide backdrop, so the frame is filled with a whole picture.
            var posterIsTall by remember(uiState.poster) { mutableStateOf(false) }
            val imageUrl = if (posterIsTall) uiState.backdrop ?: uiState.poster else uiState.poster
            val context = androidx.compose.ui.platform.LocalContext.current
            val density = androidx.compose.ui.platform.LocalDensity.current
            val request = remember(context, imageUrl, density) {
                coil3.request.ImageRequest.Builder(context)
                    .data(imageUrl)
                    .size(with(density) { 320.dp.roundToPx() }, with(density) { 180.dp.roundToPx() })
                    .scale(coil3.size.Scale.FILL)
                    .build()
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(NewLookChip)
            ) {
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onSuccess = { state ->
                        val size = state.painter.intrinsicSize
                        if (!posterIsTall && size.height > size.width && !uiState.backdrop.isNullOrBlank()) posterIsTall = true
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        resumeText?.let {
            Text(text = it, fontSize = 13.sp, color = NuvioTheme.colors.TextSecondary)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun NewLookSourceRow(
    stream: Stream,
    summary: StreamSummary,
    highlighted: Boolean,
    topPick: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val heading = listOfNotNull(summary.qualityLabel, summary.source).joinToString(" · ")
        .ifBlank { stream.name?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim() ?: stream.addonName }
    val shape = RoundedCornerShape(if (highlighted) 16.dp else 12.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = if (highlighted) 0.10f else 0.05f),
            contentColor = NuvioTheme.colors.TextPrimary,
            focusedContainerColor = Color.White.copy(alpha = 0.16f),
            focusedContentColor = NuvioTheme.colors.TextPrimary
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
                shape = shape
            )
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = if (highlighted) 14.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (topPick) {
                        NewLookChipText(
                            text = stringResource(R.string.new_look_top_pick),
                            container = CachedGreen,
                            content = Color(0xFF06281A),
                            bold = true
                        )
                    }
                    Text(
                        text = heading,
                        fontSize = if (highlighted) 16.sp else 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (summary.features.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        summary.features.forEach { NewLookChipText(text = it) }
                    }
                }
                val facts = buildList {
                    summary.sizeBytes?.let { add(formatStreamSize(it)) }
                    add(stream.addonName)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (summary.cached) {
                        Text(text = stringResource(R.string.new_look_cached), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = CachedGreen)
                    }
                    Text(
                        text = facts.joinToString("  ·  "),
                        fontSize = 12.sp,
                        color = NuvioTheme.colors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // The raw file name only for the source in focus, for anyone who wants the detail.
                if (focused) {
                    summary.fileName?.let {
                        Text(
                            text = it,
                            fontSize = 11.sp,
                            color = NuvioTheme.colors.TextTertiary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (highlighted) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (focused) Color.White else Color.White.copy(alpha = 0.85f))
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = NewLookFocusedText, modifier = Modifier.size(18.dp))
                    Text(text = stringResource(R.string.new_look_play), color = NewLookFocusedText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun NewLookSourcesMessage(text: String, actionText: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = text, fontSize = 15.sp, color = NuvioTheme.colors.TextSecondary)
        if (actionText != null && onAction != null) {
            val requester = remember { FocusRequester() }
            NewLookPrimaryButton(text = actionText, onClick = onAction, modifier = Modifier.focusRequester(requester))
            LaunchedEffect(Unit) {
                withFrameNanos { }
                runCatching { requester.requestFocus() }
            }
        }
    }
}
