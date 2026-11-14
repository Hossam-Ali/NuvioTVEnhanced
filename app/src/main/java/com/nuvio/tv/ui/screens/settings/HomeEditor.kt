@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import android.view.KeyEvent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.core.poster.HomeRowStyle
import com.nuvio.tv.core.poster.HomeRowStylePreferences
import com.nuvio.tv.core.poster.landscapeFor
import com.nuvio.tv.domain.model.HomeLayout
import com.nuvio.tv.ui.screens.addon.CatalogOrderItem
import com.nuvio.tv.ui.screens.addon.CatalogOrderViewModel
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.launch

/** Opens the visual home editor from the layout settings. */
@Composable
internal fun HomeEditorEntry(uiState: LayoutSettingsUiState, onEvent: (LayoutSettingsEvent) -> Unit) {
    var open by remember { mutableStateOf(false) }
    SettingsActionRow(
        title = stringResource(R.string.home_editor_title),
        subtitle = stringResource(R.string.home_editor_entry_subtitle),
        onClick = { open = true },
        leadingIcon = Icons.Default.DashboardCustomize
    )
    if (open) {
        Dialog(
            onDismissRequest = { open = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            HomeEditor(uiState = uiState, onEvent = onEvent)
        }
    }
}

private enum class HomeEditorAction { Move, ToggleVisible, ToggleRecentlyAdded, ToggleNewestFirst, StyleDefault, StylePoster, StyleLandscape }

private fun HomeEditorAction.style(): HomeRowStyle? = when (this) {
    HomeEditorAction.StylePoster -> HomeRowStyle.POSTER
    HomeEditorAction.StyleLandscape -> HomeRowStyle.LANDSCAPE
    else -> null
}

private val CatalogOrderItem.isCollection: Boolean get() = typeLabel == "collection"

/**
 * The home screen's sections in order, with a live picture of the result beside them. OK on a
 * row offers moving it (then Up/Down, OK to drop), hiding it, and its card style.
 */
@Composable
private fun HomeEditor(uiState: LayoutSettingsUiState, onEvent: (LayoutSettingsEvent) -> Unit) {
    val orderViewModel: CatalogOrderViewModel = hiltViewModel()
    val order by orderViewModel.uiState.collectAsState()
    val styles by HomeRowStylePreferences.styles.collectAsState()
    val recentlyAddedKeys by com.nuvio.tv.core.poster.RecentlyAddedPreferences.rowKeys.collectAsState()
    val hideDuplicates by com.nuvio.tv.core.poster.HideDuplicateTitlesPreference.enabled.collectAsState()
    val newestFirstKeys by com.nuvio.tv.core.poster.NewestFirstRowsPreferences.rowKeys.collectAsState()
    val recentlyAddedPeriod by com.nuvio.tv.core.poster.RecentlyAddedPreferences.periodDays.collectAsState()
    var showPeriodPicker by remember { mutableStateOf(false) }
    var focusedKey by remember { mutableStateOf<String?>(null) }
    var movingKey by remember { mutableStateOf<String?>(null) }
    var actionItem by remember { mutableStateOf<CatalogOrderItem?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val firstFocus = remember { FocusRequester() }
    val isModern = uiState.selectedLayout == HomeLayout.MODERN
    // "Recently added" is a row on the Modern and Classic homes; Grid has no rows.
    val hasRecentlyAdded = uiState.selectedLayout != HomeLayout.GRID

    LaunchedEffect(order.isLoading) {
        if (!order.isLoading) runCatching { firstFocus.requestFocus() }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioTheme.colors.Background)
            .padding(horizontal = NuvioTheme.spacing.xxxl, vertical = NuvioTheme.spacing.xl),
        horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.xxl)
    ) {
        Column(modifier = Modifier.weight(0.56f).fillMaxHeight()) {
            Text(stringResource(R.string.home_editor_title), style = MaterialTheme.typography.headlineMedium, color = NuvioTheme.colors.TextPrimary)
            Text(
                text = stringResource(if (movingKey != null) R.string.home_editor_moving_hint else R.string.home_editor_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = if (movingKey != null) NuvioTheme.colors.Primary else NuvioTheme.colors.TextSecondary
            )
            Spacer(Modifier.height(NuvioTheme.spacing.md))
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = NuvioTheme.spacing.xl),
                verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.xs)
            ) {
                item(key = "hero") {
                    SettingsToggleRow(
                        title = stringResource(R.string.layout_show_hero),
                        subtitle = stringResource(R.string.layout_show_hero_sub),
                        checked = uiState.heroSectionEnabled,
                        onToggle = { onEvent(LayoutSettingsEvent.SetHeroSectionEnabled(!uiState.heroSectionEnabled)) },
                        modifier = Modifier.focusRequester(firstFocus)
                    )
                }
                item(key = "cw") {
                    SettingsToggleRow(
                        title = stringResource(R.string.continue_watching),
                        subtitle = null,
                        checked = uiState.continueWatchingEnabled,
                        onToggle = { onEvent(LayoutSettingsEvent.SetContinueWatchingEnabled(!uiState.continueWatchingEnabled)) }
                    )
                }
                item(key = "duplicates") {
                    SettingsToggleRow(
                        title = stringResource(R.string.home_hide_duplicates),
                        subtitle = stringResource(R.string.home_hide_duplicates_sub),
                        checked = hideDuplicates,
                        onToggle = { com.nuvio.tv.core.poster.HideDuplicateTitlesPreference.set(!hideDuplicates) }
                    )
                }
                if (hasRecentlyAdded) {
                    item(key = "recently_added") {
                        val picked = order.items.count { it.key in recentlyAddedKeys }
                        // The row's period: OK opens the choice of 1, 7, 14 or 30 days.
                        SettingsActionRow(
                            title = stringResource(R.string.home_recently_added) +
                                if (picked > 0) " · " + stringResource(R.string.home_editor_recently_added_count, picked) else "",
                            subtitle = stringResource(R.string.home_editor_recently_added_sub),
                            value = recentlyAddedPeriodLabel(recentlyAddedPeriod),
                            onClick = { showPeriodPicker = true }
                        )
                    }
                }
                if (order.followAddonsOrder) {
                    item(key = "follow") {
                        Text(
                            text = stringResource(R.string.home_editor_follow_addons_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = NuvioTheme.colors.TextTertiary,
                            modifier = Modifier.padding(vertical = NuvioTheme.spacing.xs)
                        )
                    }
                }
                itemsIndexed(order.items, key = { _, item -> item.key }) { index, item ->
                    HomeEditorRow(
                        position = index + 1,
                        item = item,
                        style = styles[item.key],
                        isModern = isModern,
                        inRecentlyAdded = hasRecentlyAdded && item.key in recentlyAddedKeys,
                        newestFirst = item.key in newestFirstKeys,
                        isMoving = movingKey == item.key,
                        onFocused = { focusedKey = item.key },
                        onClick = {
                            if (movingKey == item.key) movingKey = null else actionItem = item
                        },
                        onMove = { up ->
                            if (up && item.canMoveUp) orderViewModel.moveUp(item.key)
                            if (!up && item.canMoveDown) orderViewModel.moveDown(item.key)
                            // Keeps the moved row in view, with a couple of rows above it.
                            val newIndex = (index + if (up) -1 else 1).coerceIn(0, order.items.lastIndex)
                            scope.launch { listState.animateScrollToItem(newIndex) }
                        },
                        onStopMoving = { movingKey = null }
                    )
                }
            }
        }
        HomeEditorPreview(
            modifier = Modifier.weight(0.44f).fillMaxHeight(),
            items = order.items,
            styles = styles,
            globalLandscape = uiState.modernLandscapePostersEnabled,
            showHero = uiState.heroSectionEnabled || isModern,
            showContinueWatching = uiState.continueWatchingEnabled,
            showRecentlyAdded = hasRecentlyAdded && order.items.any { it.key in recentlyAddedKeys },
            focusedKey = focusedKey,
            isModern = isModern
        )
    }

    if (showPeriodPicker) {
        RecentlyAddedPeriodDialog(current = recentlyAddedPeriod, onDismiss = { showPeriodPicker = false })
    }

    actionItem?.let { item ->
        val currentStyle = styles[item.key]
        val options = buildList {
            add(SettingsPickerOption(HomeEditorAction.Move, stringResource(R.string.home_editor_action_move)))
            add(
                SettingsPickerOption(
                    HomeEditorAction.ToggleVisible,
                    stringResource(if (item.isDisabled) R.string.home_editor_action_show else R.string.home_editor_action_hide)
                )
            )
            // A collection counts with the add-on catalogs its folders show.
            if (hasRecentlyAdded) {
                add(
                    SettingsPickerOption(
                        HomeEditorAction.ToggleRecentlyAdded,
                        stringResource(
                            if (item.key in recentlyAddedKeys) R.string.home_editor_action_recently_added_remove
                            else R.string.home_editor_action_recently_added_add
                        ),
                        description = stringResource(R.string.home_editor_action_recently_added_desc)
                    )
                )
            }
            if (!item.isCollection) {
                add(
                    SettingsPickerOption(
                        HomeEditorAction.ToggleNewestFirst,
                        stringResource(
                            if (item.key in newestFirstKeys) R.string.home_editor_action_addon_order
                            else R.string.home_editor_action_newest_first
                        )
                    )
                )
                add(SettingsPickerOption(HomeEditorAction.StyleDefault, stringResource(R.string.home_editor_style_default), description = stringResource(R.string.home_editor_style_default_desc)))
                add(SettingsPickerOption(HomeEditorAction.StylePoster, stringResource(R.string.home_editor_style_poster)))
                add(SettingsPickerOption(HomeEditorAction.StyleLandscape, stringResource(R.string.home_editor_style_landscape)))
            }
        }
        SettingsSingleChoiceDialog(
            title = item.catalogName,
            subtitle = if (isModern) null else stringResource(R.string.home_editor_style_modern_only),
            options = options,
            selectedValue = when (currentStyle) {
                HomeRowStyle.POSTER -> HomeEditorAction.StylePoster
                HomeRowStyle.LANDSCAPE -> HomeEditorAction.StyleLandscape
                null -> if (item.isCollection) HomeEditorAction.Move else HomeEditorAction.StyleDefault
            },
            onOptionSelected = { action ->
                when (action) {
                    HomeEditorAction.Move -> movingKey = item.key
                    HomeEditorAction.ToggleVisible -> orderViewModel.toggleCatalogEnabled(item.disableKey)
                    HomeEditorAction.ToggleRecentlyAdded -> com.nuvio.tv.core.poster.RecentlyAddedPreferences.toggle(item.key)
                    HomeEditorAction.ToggleNewestFirst -> com.nuvio.tv.core.poster.NewestFirstRowsPreferences.toggle(item.key)
                    else -> HomeRowStylePreferences.setStyle(item.key, action.style())
                }
                actionItem = null
            },
            onDismiss = { actionItem = null },
            width = 460.dp,
            maxHeight = 460.dp
        )
    }
}

@Composable
private fun HomeEditorRow(
    position: Int,
    item: CatalogOrderItem,
    style: HomeRowStyle?,
    isModern: Boolean,
    inRecentlyAdded: Boolean,
    newestFirst: Boolean,
    isMoving: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onMove: (up: Boolean) -> Unit,
    onStopMoving: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { if (it.isFocused) onFocused() }
            .onPreviewKeyEvent { event ->
                if (!isMoving || event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> { onMove(true); true }
                    KeyEvent.KEYCODE_DPAD_DOWN -> { onMove(false); true }
                    KeyEvent.KEYCODE_BACK -> { onStopMoving(); true }
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> true
                    else -> false
                }
            },
        shape = CardDefaults.shape(shape),
        colors = CardDefaults.colors(
            containerColor = if (isMoving) NuvioTheme.colors.Primary.copy(alpha = 0.18f) else NuvioTheme.colors.BackgroundCard,
            focusedContainerColor = if (isMoving) NuvioTheme.colors.Primary.copy(alpha = 0.28f) else NuvioTheme.colors.BackgroundElevated
        ),
        border = CardDefaults.border(
            focusedBorder = Border(border = NuvioTheme.focusRing.border(2.dp), shape = shape)
        ),
        scale = CardDefaults.scale(focusedScale = 1f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = position.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = NuvioTheme.colors.TextTertiary,
                modifier = Modifier.width(24.dp)
            )
            Column(modifier = Modifier.weight(1f).alpha(if (item.isDisabled) 0.5f else 1f)) {
                Text(item.catalogName, style = MaterialTheme.typography.titleSmall, color = NuvioTheme.colors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = if (item.isCollection) stringResource(R.string.home_editor_collection) else "${item.addonName} · ${item.typeLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = NuvioTheme.colors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (inRecentlyAdded) {
                EditorChip(stringResource(R.string.home_recently_added))
            }
            if (newestFirst) {
                EditorChip(stringResource(R.string.home_editor_newest_first))
            }
            if (!item.isCollection && style != null && isModern) {
                EditorChip(stringResource(if (style == HomeRowStyle.LANDSCAPE) R.string.home_editor_style_landscape else R.string.home_editor_style_poster))
            }
            if (item.isDisabled) {
                Icon(Icons.Default.VisibilityOff, contentDescription = stringResource(R.string.home_editor_hidden), tint = NuvioTheme.colors.TextTertiary, modifier = Modifier.size(18.dp))
            }
            if (isMoving) {
                Icon(Icons.Default.SwapVert, contentDescription = null, tint = NuvioTheme.colors.Primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun EditorChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = NuvioTheme.colors.TextSecondary,
        modifier = Modifier
            .background(NuvioTheme.colors.Background, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

/** A small picture of the home screen as the editor would leave it. */
@Composable
private fun HomeEditorPreview(
    modifier: Modifier,
    items: List<CatalogOrderItem>,
    styles: Map<String, HomeRowStyle>,
    globalLandscape: Boolean,
    showHero: Boolean,
    showContinueWatching: Boolean,
    showRecentlyAdded: Boolean,
    focusedKey: String?,
    isModern: Boolean,
) {
    val visible = items.filterNot { it.isDisabled }
    val shown = visible.take(7)
    Column(
        modifier = modifier
            .background(NuvioTheme.colors.BackgroundCard, RoundedCornerShape(18.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(stringResource(R.string.home_editor_preview), style = MaterialTheme.typography.labelLarge, color = NuvioTheme.colors.TextSecondary)
        if (showHero) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(78.dp)
                    .background(
                        Brush.horizontalGradient(listOf(NuvioTheme.colors.Primary.copy(alpha = 0.45f), NuvioTheme.colors.BackgroundElevated)),
                        RoundedCornerShape(10.dp)
                    )
                    .padding(10.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Text(stringResource(R.string.home_editor_preview_hero), style = MaterialTheme.typography.labelMedium, color = Color.White)
            }
        }
        if (showContinueWatching) {
            PreviewRow(title = stringResource(R.string.continue_watching), landscape = true, highlighted = false, accent = true)
        }
        if (showRecentlyAdded) {
            PreviewRow(
                title = stringResource(R.string.home_recently_added),
                landscape = styles.landscapeFor(com.nuvio.tv.ui.screens.home.MODERN_RECENTLY_ADDED_ROW_KEY, globalLandscape),
                highlighted = false,
                accent = true
            )
        }
        shown.forEach { item ->
            val landscape = !item.isCollection && isModern && styles.landscapeFor(item.key, globalLandscape)
            PreviewRow(
                title = item.catalogName,
                landscape = landscape || (!isModern && globalLandscape && !item.isCollection),
                highlighted = item.key == focusedKey,
                square = item.isCollection
            )
        }
        if (visible.size > shown.size) {
            Text(
                text = stringResource(R.string.home_editor_preview_more, visible.size - shown.size),
                style = MaterialTheme.typography.bodySmall,
                color = NuvioTheme.colors.TextTertiary
            )
        }
    }
}

@Composable
private fun PreviewRow(title: String, landscape: Boolean, highlighted: Boolean, square: Boolean = false, accent: Boolean = false) {
    val cardWidth: Dp = when {
        square -> 30.dp
        landscape -> 52.dp
        else -> 24.dp
    }
    val cardHeight: Dp = when {
        square -> 30.dp
        landscape -> 29.dp
        else -> 36.dp
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (highlighted) Modifier.border(2.dp, NuvioTheme.colors.Primary, RoundedCornerShape(8.dp)) else Modifier
            )
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = NuvioTheme.colors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(if (landscape) 4 else 7) { index ->
                Box(
                    modifier = Modifier
                        .size(cardWidth, cardHeight)
                        .background(
                            (if (accent) NuvioTheme.colors.Primary else NuvioTheme.colors.TextTertiary).copy(alpha = if (index == 0) 0.55f else 0.25f),
                            RoundedCornerShape(4.dp)
                        )
                )
            }
        }
    }
}

/** The choice of 1, 7, 14 or 30 days for "Recently added". */
@Composable
internal fun RecentlyAddedPeriodDialog(current: Int, onDismiss: () -> Unit) {
    SettingsSingleChoiceDialog(
        title = stringResource(R.string.home_recently_added_period),
        options = com.nuvio.tv.core.poster.RecentlyAddedPreferences.PERIOD_CHOICES.map { days ->
            SettingsPickerOption(days, recentlyAddedPeriodLabel(days))
        },
        selectedValue = current,
        onOptionSelected = { days ->
            com.nuvio.tv.core.poster.RecentlyAddedPreferences.setPeriodDays(days)
            onDismiss()
        },
        onDismiss = onDismiss
    )
}

/** "Last 7 days" and the like, for a "Recently added" period. */
@Composable
internal fun recentlyAddedPeriodLabel(days: Int): String = stringResource(
    when (days) {
        1 -> R.string.home_recently_added_period_1
        14 -> R.string.home_recently_added_period_14
        30 -> R.string.home_recently_added_period_30
        else -> R.string.home_recently_added_period_7
    }
)
