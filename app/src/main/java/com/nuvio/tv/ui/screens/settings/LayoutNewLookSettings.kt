package com.nuvio.tv.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.nuvio.tv.R
import com.nuvio.tv.core.ui.NewLookNavigation
import com.nuvio.tv.core.ui.NewLookPreferences

/** Where the main menu sits, and whether the side icons stay on screen. */
@Composable
internal fun NewLookSettingsItems() {
    val settings = NewLookPreferences.current.value
    var choosingNavigation by remember { mutableStateOf(false) }

    SettingsActionRow(
        title = stringResource(R.string.new_look_navigation),
        subtitle = stringResource(R.string.new_look_navigation_subtitle),
        value = newLookNavigationLabel(settings.navigation),
        onClick = { choosingNavigation = true }
    )
    if (settings.navigation == NewLookNavigation.SIDE_ICONS) {
        SettingsToggleRow(
            title = stringResource(R.string.new_look_hide_side_bar),
            subtitle = stringResource(R.string.new_look_hide_side_bar_subtitle),
            checked = settings.hideSideBar,
            onToggle = { NewLookPreferences.update { it.copy(hideSideBar = !it.hideSideBar) } }
        )
    }

    if (choosingNavigation) {
        SettingsSingleChoiceDialog(
            title = stringResource(R.string.new_look_navigation),
            options = NewLookNavigation.entries.map { SettingsPickerOption(it, newLookNavigationLabel(it)) },
            selectedValue = settings.navigation,
            onOptionSelected = { navigation ->
                NewLookPreferences.update { it.copy(navigation = navigation) }
                choosingNavigation = false
            },
            onDismiss = { choosingNavigation = false }
        )
    }
}

@Composable
private fun newLookNavigationLabel(navigation: NewLookNavigation): String = stringResource(
    when (navigation) {
        NewLookNavigation.SIDE_ICONS -> R.string.new_look_navigation_side
        NewLookNavigation.TOP_TABS -> R.string.new_look_navigation_top
    }
)
