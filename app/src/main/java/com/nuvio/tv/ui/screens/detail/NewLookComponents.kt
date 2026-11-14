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

// Shared pieces of the title page: buttons, pills, labels, thumbnails and progress bars.

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

@Composable
internal fun formatNewLookRuntime(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return if (hours > 0) stringResource(R.string.new_look_runtime_hm, hours, rest) else stringResource(R.string.new_look_runtime_m, rest)
}

@Composable
internal fun NewLookThumbnail(
    url: String?,
    width: Dp,
    height: Dp,
    alignment: androidx.compose.ui.Alignment = androidx.compose.ui.Alignment.Center
) {
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
        alignment = alignment,
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
