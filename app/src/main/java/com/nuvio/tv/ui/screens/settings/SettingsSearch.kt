package com.nuvio.tv.ui.screens.settings

import android.view.KeyEvent
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.ui.theme.NuvioTheme
import java.text.Normalizer

/** The integrations sub-page a setting lives on. */
internal enum class SettingsSearchIntegration { Debrid, Tmdb, MdbList, AnimeSkip }

/** A searchable settings row: its title and description, and the page (and section) it is on. */
internal data class SettingsSearchEntry(
    @param:StringRes val titleRes: Int,
    @param:StringRes val subtitleRes: Int?,
    val page: SettingsCategory,
    val playbackSection: PlaybackSection?,
    val integration: SettingsSearchIntegration?,
)

internal data class SettingsSearchResult(val entry: SettingsSearchEntry, val title: String, val subtitle: String?)

private val combiningMarks = Regex("\\p{Mn}+")
private val arabicMarks = Regex("[\\u064B-\\u0652\\u0670\\u0640]")

/** Lower case, without accents or Arabic diacritics, with Arabic letter variants unified. */
internal fun normalizeForSettingsSearch(text: String): String =
    Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        .replace(combiningMarks, "")
        .replace(arabicMarks, "")
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ى', 'ي').replace('ة', 'ه')
        .trim()

/**
 * Settings whose title or description contains every word of [query]. Title matches come first,
 * then titles starting with the query; only pages in [visiblePages] are returned.
 */
internal fun searchSettings(
    query: String,
    rows: List<SettingsSearchResult>,
    visiblePages: Set<SettingsCategory>,
    limit: Int = 50,
): List<SettingsSearchResult> {
    val words = normalizeForSettingsSearch(query).split(' ').filter { it.isNotBlank() }
    if (words.isEmpty()) return emptyList()
    return rows.asSequence()
        .filter { it.entry.page in visiblePages }
        .mapNotNull { row ->
            val title = normalizeForSettingsSearch(row.title)
            val haystack = title + " " + normalizeForSettingsSearch(row.subtitle.orEmpty())
            if (words.any { it !in haystack }) return@mapNotNull null
            val rank = when {
                title.startsWith(words.first()) -> 0
                words.all { it in title } -> 1
                else -> 2
            }
            rank to row
        }
        .sortedBy { it.first }
        .map { it.second }
        .distinctBy { it.title to it.entry.page }
        .take(limit)
        .toList()
}

/** The search page: a text field and the matching settings; choosing one opens its page. */
@Composable
internal fun SettingsSearchContent(
    visiblePages: Set<SettingsCategory>,
    onOpen: (SettingsSearchEntry) -> Unit,
    initialFocusRequester: FocusRequester?,
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    val rows = remember(context) {
        settingsSearchIndex.map { entry ->
            SettingsSearchResult(entry, context.getString(entry.titleRes), entry.subtitleRes?.let(context::getString))
        }
    }
    val results = remember(query, rows, visiblePages) { searchSettings(query, rows, visiblePages) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)
    ) {
        SettingsDetailHeader(
            title = stringResource(R.string.settings_search_title),
            subtitle = stringResource(R.string.settings_search_subtitle)
        )
        SettingsSearchField(
            value = query,
            onValueChange = { query = it },
            focusRequester = initialFocusRequester
        )
        when {
            query.isBlank() -> SettingsSearchMessage(stringResource(R.string.settings_search_hint))
            results.isEmpty() -> SettingsSearchMessage(stringResource(R.string.settings_search_no_results, query.trim()))
            else -> SettingsGroupCard(modifier = Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = NuvioTheme.spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.xs)
                ) {
                    items(results, key = { "${it.entry.page}:${it.entry.titleRes}" }) { result ->
                        SettingsActionRow(
                            title = result.title,
                            subtitle = settingsSearchLocation(result.entry),
                            onClick = { onOpen(result.entry) }
                        )
                    }
                }
            }
        }
    }
}

/** Where a setting lives, for example "Playback › Audio" or "Appearance › Layout". */
@Composable
private fun settingsSearchLocation(entry: SettingsSearchEntry): String {
    val page = when (entry.page) {
        SettingsCategory.APPEARANCE -> stringResource(R.string.settings_page_theme)
        SettingsCategory.LAYOUT -> stringResource(R.string.settings_layout)
        SettingsCategory.PLAYBACK -> stringResource(R.string.settings_playback)
        SettingsCategory.SUBTITLES -> stringResource(R.string.playback_section_subtitles)
        SettingsCategory.CONTENT_DISCOVERY -> stringResource(R.string.settings_content_discovery)
        SettingsCategory.INTEGRATION -> stringResource(R.string.settings_integration)
        SettingsCategory.TRACKING -> stringResource(R.string.settings_tracking_title)
        SettingsCategory.ADVANCED -> stringResource(R.string.settings_advanced)
        SettingsCategory.ABOUT -> stringResource(R.string.about_title)
        SettingsCategory.PROFILES -> stringResource(R.string.settings_profiles)
        SettingsCategory.WATCH_PARTY -> stringResource(R.string.party_title)
        SettingsCategory.ACCOUNT -> stringResource(R.string.settings_account)
        else -> ""
    }
    val section = entry.playbackSection
        ?.takeIf { it != PlaybackSection.SUBTITLES }
        ?.let { stringResource(it.title) }
    return listOfNotNull(page.takeIf { it.isNotBlank() }, section).joinToString(" › ")
}

@Composable
private fun SettingsSearchMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = NuvioTheme.colors.TextSecondary,
        modifier = Modifier.padding(horizontal = NuvioTheme.spacing.sm)
    )
}

@Composable
private fun SettingsSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester?,
) {
    var isFocused by remember { mutableStateOf(false) }
    val inputFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val shape = RoundedCornerShape(10.dp)
    Card(
        // The card takes focus from the remote; OK moves into the field, which opens the keyboard.
        onClick = { inputFocusRequester.requestFocus() },
        modifier = Modifier
            .fillMaxWidth()
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
            .onFocusChanged { isFocused = it.isFocused || it.hasFocus },
        colors = CardDefaults.colors(
            containerColor = NuvioTheme.colors.BackgroundElevated,
            focusedContainerColor = NuvioTheme.colors.BackgroundElevated
        ),
        border = CardDefaults.border(
            border = Border(
                border = androidx.compose.foundation.BorderStroke(NuvioTheme.spacing.hairline, NuvioTheme.colors.Border),
                shape = shape
            ),
            focusedBorder = Border(border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs), shape = shape)
        ),
        shape = CardDefaults.shape(shape),
        scale = CardDefaults.scale(focusedScale = 1f)
    ) {
        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = NuvioTheme.spacing.md)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(inputFocusRequester)
                    .onKeyEvent { event ->
                        event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER &&
                            event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN
                    },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = NuvioTheme.colors.TextPrimary,
                    textDirection = TextDirection.Content
                ),
                cursorBrush = SolidColor(if (isFocused) NuvioTheme.colors.Primary else Color.Transparent),
                decorationBox = { innerTextField ->
                    if (value.isEmpty()) {
                        Text(
                            text = stringResource(R.string.settings_search_placeholder),
                            style = MaterialTheme.typography.bodyLarge,
                            color = NuvioTheme.colors.TextTertiary
                        )
                    }
                    innerTextField()
                }
            )
        }
    }
}
