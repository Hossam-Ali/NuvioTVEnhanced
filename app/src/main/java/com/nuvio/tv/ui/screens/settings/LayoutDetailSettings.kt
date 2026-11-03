package com.nuvio.tv.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nuvio.tv.R
import com.nuvio.tv.core.build.AppFeaturePolicy
import com.nuvio.tv.core.streams.STREAM_BADGE_IMPORT_LIMIT
import com.nuvio.tv.core.streams.StreamBadgePlacement
import com.nuvio.tv.domain.model.DetailImdbRatingsVisibility
import com.nuvio.tv.domain.model.EpisodeOptionsOverlayStyle

@Composable
internal fun LayoutStreamsSection(
    streamBadgeUiState: StreamBadgeSettingsUiState,
    onShowFileSizeBadgesChange: (Boolean) -> Unit,
    onShowAddonLogoChange: (Boolean) -> Unit,
    onBadgePlacementSelected: (StreamBadgePlacement) -> Unit,
    onConfigureBadges: () -> Unit,
    onShowInPlayerChange: (Boolean) -> Unit,
    onPlayerBadgeSourceSelected: (String) -> Unit,
    configureBadgesFocusRequester: FocusRequester
) {
    var showPositionDialog by rememberSaveable { mutableStateOf(false) }
    var showPlayerBadgesDialog by rememberSaveable { mutableStateOf(false) }

    SettingsSectionLabel(text = stringResource(R.string.settings_stream_badges_section))
    SettingsToggleRow(
        title = stringResource(R.string.settings_stream_size_badges_title),
        subtitle = stringResource(R.string.settings_stream_size_badges_description),
        checked = streamBadgeUiState.showFileSizeBadges,
        onToggle = { onShowFileSizeBadgesChange(!streamBadgeUiState.showFileSizeBadges) }
    )
    SettingsActionRow(
        title = stringResource(R.string.settings_stream_badge_position_title),
        subtitle = null,
        value = streamBadgePlacementLabel(streamBadgeUiState.badgePlacement),
        onClick = { showPositionDialog = true }
    )
    SettingsActionRow(
        title = stringResource(R.string.settings_stream_badge_urls_title),
        subtitle = streamBadgeRulesPreview(streamBadgeUiState),
        onClick = onConfigureBadges,
        modifier = Modifier.focusRequester(configureBadgesFocusRequester)
    )
    SettingsToggleRow(
        title = stringResource(R.string.settings_stream_badges_in_player_title),
        subtitle = stringResource(R.string.settings_stream_badges_in_player_description),
        checked = streamBadgeUiState.showInPlayer,
        enabled = streamBadgeUiState.rules.hasImport,
        onToggle = { onShowInPlayerChange(!streamBadgeUiState.showInPlayer) }
    )
    if (streamBadgeUiState.showInPlayer && streamBadgeUiState.rules.hasImport) {
        SettingsActionRow(
            title = stringResource(R.string.settings_stream_player_badges_title),
            subtitle = playerBadgesLabel(streamBadgeUiState),
            onClick = { showPlayerBadgesDialog = true }
        )
    }

    SettingsSectionLabel(text = stringResource(R.string.settings_stream_display_section))
    SettingsToggleRow(
        title = stringResource(R.string.settings_stream_addon_logo_title),
        subtitle = stringResource(R.string.settings_stream_addon_logo_description),
        checked = streamBadgeUiState.showAddonLogo,
        onToggle = { onShowAddonLogoChange(!streamBadgeUiState.showAddonLogo) }
    )

    if (showPlayerBadgesDialog) {
        PlayerBadgesDialog(
            streamBadgeUiState = streamBadgeUiState,
            onSourceSelected = { source ->
                onPlayerBadgeSourceSelected(source)
                showPlayerBadgesDialog = false
            },
            onDismiss = { showPlayerBadgesDialog = false }
        )
    }

    if (showPositionDialog) {
        StreamBadgePositionDialog(
            currentPlacement = streamBadgeUiState.badgePlacement,
            onPlacementSelected = { placement ->
                onBadgePlacementSelected(placement)
                showPositionDialog = false
            },
            onDismiss = { showPositionDialog = false }
        )
    }
}

@Composable
private fun streamBadgePlacementLabel(placement: StreamBadgePlacement): String =
    when (placement) {
        StreamBadgePlacement.TOP -> stringResource(R.string.settings_stream_badge_position_top)
        StreamBadgePlacement.BOTTOM -> stringResource(R.string.settings_stream_badge_position_bottom)
    }

@Composable
private fun playerBadgesLabel(uiState: StreamBadgeSettingsUiState): String =
    uiState.rules.imports.firstOrNull { it.sourceUrl.equals(uiState.playerBadgeSource, ignoreCase = true) }?.sourceUrl
        ?: stringResource(R.string.settings_stream_player_badges_same)

@Composable
private fun PlayerBadgesDialog(
    streamBadgeUiState: StreamBadgeSettingsUiState,
    onSourceSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val imports = streamBadgeUiState.rules.normalized().imports
    val selected = imports.firstOrNull {
        it.sourceUrl.equals(streamBadgeUiState.playerBadgeSource, ignoreCase = true)
    }?.sourceUrl.orEmpty()
    val options = listOf(
        SettingsPickerOption("", stringResource(R.string.settings_stream_player_badges_same))
    ) + imports.map { SettingsPickerOption(it.sourceUrl, it.sourceUrl) }

    SettingsSingleChoiceDialog(
        title = stringResource(R.string.settings_stream_player_badges_title),
        subtitle = stringResource(R.string.settings_stream_player_badges_description),
        options = options,
        selectedValue = selected,
        onOptionSelected = onSourceSelected,
        onDismiss = onDismiss,
        width = 640.dp
    )
}

@Composable
private fun streamBadgeRulesPreview(uiState: StreamBadgeSettingsUiState): String {
    val rules = uiState.rules.normalized()
    return if (rules.hasImport) {
        stringResource(
            R.string.settings_fusion_badges_summary,
            rules.imports.size,
            STREAM_BADGE_IMPORT_LIMIT,
            rules.enabledFilterCount
        )
    } else {
        stringResource(R.string.settings_fusion_badges_empty)
    }
}

@Composable
private fun StreamBadgePositionDialog(
    currentPlacement: StreamBadgePlacement,
    onPlacementSelected: (StreamBadgePlacement) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        SettingsPickerOption(
            StreamBadgePlacement.BOTTOM,
            stringResource(R.string.settings_stream_badge_position_bottom)
        ),
        SettingsPickerOption(
            StreamBadgePlacement.TOP,
            stringResource(R.string.settings_stream_badge_position_top)
        )
    )

    SettingsSingleChoiceDialog(
        title = stringResource(R.string.settings_stream_badge_position_dialog_title),
        subtitle = stringResource(R.string.settings_stream_badge_position_dialog_description),
        options = options,
        selectedValue = currentPlacement,
        onOptionSelected = onPlacementSelected,
        onDismiss = onDismiss,
        width = 420.dp,
        maxHeight = 260.dp
    )
}
