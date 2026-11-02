package com.nuvio.tv.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nuvio.tv.R
import com.nuvio.tv.core.poster.POSTER_RATING_PROVIDERS
import com.nuvio.tv.core.poster.PosterRatingPosition
import com.nuvio.tv.core.poster.PosterRatingsPreferences
import kotlinx.coroutines.launch

private enum class PosterRatingsDialog { Providers, Count, Position, IconPack }

/** Rating badges on posters: which ratings, how many, where, and an optional icon pack. */
@Composable
internal fun PosterRatingsSettingsItems() {
    val settings by PosterRatingsPreferences.settings.collectAsState()
    var dialog by remember { mutableStateOf<PosterRatingsDialog?>(null) }
    var iconPackStatus by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val providerNames = posterRatingProviderNames()

    SettingsToggleRow(
        title = stringResource(R.string.poster_ratings_title),
        subtitle = stringResource(R.string.poster_ratings_subtitle),
        checked = settings.enabled,
        onToggle = { PosterRatingsPreferences.update { it.copy(enabled = !it.enabled) } }
    )
    if (!settings.enabled) return
    SettingsActionRow(
        title = stringResource(R.string.poster_ratings_providers),
        subtitle = stringResource(R.string.poster_ratings_providers_subtitle),
        value = settings.providers.joinToString(", ") { providerNames[it] ?: it },
        onClick = { dialog = PosterRatingsDialog.Providers }
    )
    SettingsActionRow(
        title = stringResource(R.string.poster_ratings_count),
        subtitle = null,
        value = settings.maxBadges.toString(),
        onClick = { dialog = PosterRatingsDialog.Count }
    )
    SettingsActionRow(
        title = stringResource(R.string.poster_ratings_position),
        subtitle = null,
        value = posterRatingPositionLabel(settings.position),
        onClick = { dialog = PosterRatingsDialog.Position }
    )
    SettingsActionRow(
        title = stringResource(R.string.poster_ratings_icon_pack),
        subtitle = iconPackStatus ?: stringResource(R.string.poster_ratings_icon_pack_subtitle),
        value = if (settings.iconPackUrl.isBlank()) stringResource(R.string.poster_ratings_icon_pack_builtin) else settings.iconPackUrl.substringAfter("://").take(32),
        onClick = { dialog = PosterRatingsDialog.IconPack }
    )

    when (dialog) {
        PosterRatingsDialog.Providers -> PosterRatingProvidersDialog(
            selected = settings.providers,
            names = providerNames,
            onChange = { providers -> PosterRatingsPreferences.update { it.copy(providers = providers) } },
            onDismiss = { dialog = null }
        )
        PosterRatingsDialog.Count -> SettingsSingleChoiceDialog(
            title = stringResource(R.string.poster_ratings_count),
            options = (1..3).map { SettingsPickerOption(it, it.toString()) },
            selectedValue = settings.maxBadges,
            onOptionSelected = { count ->
                PosterRatingsPreferences.update { it.copy(maxBadges = count) }
                dialog = null
            },
            onDismiss = { dialog = null }
        )
        PosterRatingsDialog.Position -> SettingsSingleChoiceDialog(
            title = stringResource(R.string.poster_ratings_position),
            options = PosterRatingPosition.entries.map { SettingsPickerOption(it, posterRatingPositionLabel(it)) },
            selectedValue = settings.position,
            onOptionSelected = { position ->
                PosterRatingsPreferences.update { it.copy(position = position) }
                dialog = null
            },
            onDismiss = { dialog = null }
        )
        PosterRatingsDialog.IconPack -> {
            val loadingText = stringResource(R.string.poster_ratings_icon_pack_loading)
            val failedText = stringResource(R.string.poster_ratings_icon_pack_failed)
            val loadedTemplate = stringResource(R.string.poster_ratings_icon_pack_loaded)
            TranslationTextDialog(
                title = stringResource(R.string.poster_ratings_icon_pack),
                subtitle = stringResource(R.string.poster_ratings_icon_pack_dialog_subtitle),
                initialValue = settings.iconPackUrl,
                placeholder = "https://example.com/icons.json",
                onClear = if (settings.iconPackUrl.isNotBlank()) {
                    { scope.launch { PosterRatingsPreferences.loadIconPack(""); iconPackStatus = null } }
                } else {
                    null
                },
                onSave = { url ->
                    iconPackStatus = loadingText
                    scope.launch {
                        iconPackStatus = PosterRatingsPreferences.loadIconPack(url).fold(
                            onSuccess = { count -> if (url.isBlank()) null else loadedTemplate.format(count) },
                            onFailure = { failedText }
                        )
                    }
                },
                onDismiss = { dialog = null }
            )
        }
        null -> Unit
    }
}

@Composable
private fun PosterRatingProvidersDialog(
    selected: List<String>,
    names: Map<String, String>,
    onChange: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    // Ticking a provider adds it after the ones already chosen, so the order is the order of choice.
    SettingsSingleChoiceDialog(
        title = stringResource(R.string.poster_ratings_providers),
        subtitle = stringResource(R.string.poster_ratings_providers_dialog_subtitle),
        options = POSTER_RATING_PROVIDERS.map { provider ->
            val index = selected.indexOf(provider)
            SettingsPickerOption(
                value = provider,
                title = names[provider] ?: provider,
                trailing = if (index >= 0) "✓ ${index + 1}" else null
            )
        },
        selectedValue = selected.firstOrNull() ?: "",
        onOptionSelected = { provider ->
            val next = if (provider in selected) selected - provider else selected + provider
            if (next.isNotEmpty()) onChange(next)
        },
        onDismiss = onDismiss,
        width = 460.dp,
        maxHeight = 520.dp
    )
}

@Composable
private fun posterRatingProviderNames(): Map<String, String> = mapOf(
    "imdb" to "IMDb",
    "tomatoes" to "Rotten Tomatoes",
    "audience" to stringResource(R.string.poster_ratings_provider_audience),
    "metacritic" to "Metacritic",
    "tmdb" to "TMDB",
    "letterboxd" to "Letterboxd",
    "trakt" to "Trakt",
    "mal" to "MyAnimeList",
)

@Composable
private fun posterRatingPositionLabel(position: PosterRatingPosition): String = stringResource(
    when (position) {
        PosterRatingPosition.TOP_START -> R.string.poster_ratings_position_top_start
        PosterRatingPosition.TOP_END -> R.string.poster_ratings_position_top_end
        PosterRatingPosition.BOTTOM_START -> R.string.poster_ratings_position_bottom_start
        PosterRatingPosition.BOTTOM_END -> R.string.poster_ratings_position_bottom_end
    }
)
