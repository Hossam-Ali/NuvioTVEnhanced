@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.stream

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.R
import com.nuvio.tv.core.tmdb.TmdbMetadataService
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.data.local.TmdbSettingsDataStore
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaCastMember
import com.nuvio.tv.ui.theme.NuvioTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Faces sit in the upper part of a headshot, so round crops keep the head instead of the chest. */
internal val PortraitFaceAlignment = BiasAlignment(0f, -0.6f)

/**
 * The creators and cast of the title whose sources are listed, from TMDB (usually cached from the
 * details page), so who is in it can be seen before choosing a version.
 */
@HiltViewModel
class StreamCastViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tmdbService: TmdbService,
    private val tmdbMetadataService: TmdbMetadataService,
    private val tmdbSettingsDataStore: TmdbSettingsDataStore,
) : ViewModel() {
    private val _people = MutableStateFlow<List<MetaCastMember>>(emptyList())
    val people: StateFlow<List<MetaCastMember>> = _people.asStateFlow()

    init {
        val contentType = savedStateHandle.get<String>("contentType").orEmpty()
        val contentId = savedStateHandle.get<String>("contentId")?.takeIf { it.isNotBlank() }
            ?: savedStateHandle.get<String>("videoId")?.substringBefore(':')
        if (contentId != null) {
            viewModelScope.launch {
                val settings = tmdbSettingsDataStore.settings.first()
                if (!settings.enabled || !settings.useCredits) return@launch
                val type = if (contentType.equals("movie", ignoreCase = true)) ContentType.MOVIE else ContentType.SERIES
                val tmdbId = runCatching { tmdbService.ensureTmdbId(contentId, type.toApiString()) }.getOrNull() ?: return@launch
                val enrichment = runCatching {
                    tmdbMetadataService.fetchEnrichment(tmdbId = tmdbId, contentType = type, language = settings.language)
                }.getOrNull() ?: return@launch
                _people.value = (enrichment.directorMembers + enrichment.castMembers)
                    .filter { it.tmdbId != null && it.name.isNotBlank() }
                    .distinctBy { it.tmdbId }
                    .take(MAX_PEOPLE)
            }
        }
    }

    private companion object {
        const val MAX_PEOPLE = 15
    }
}

/** A row of round headshots with names; choosing one opens their films and series. */
@Composable
internal fun StreamCastRow(
    people: List<MetaCastMember>,
    onPersonClick: (MetaCastMember) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (people.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.detail_tab_cast),
            style = MaterialTheme.typography.labelLarge,
            color = NuvioTheme.extendedColors.textSecondary
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
        ) {
            items(people, key = { it.tmdbId ?: it.name.hashCode() }) { person ->
                Column(modifier = Modifier.width(76.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Card(
                        onClick = { onPersonClick(person) },
                        modifier = Modifier.size(60.dp),
                        shape = CardDefaults.shape(CircleShape),
                        colors = CardDefaults.colors(containerColor = NuvioTheme.colors.SurfaceVariant),
                        border = CardDefaults.border(focusedBorder = Border(border = NuvioTheme.focusRing.border(2.dp), shape = CircleShape)),
                        scale = CardDefaults.scale(focusedScale = 1.08f)
                    ) {
                        if (person.photo != null) {
                            AsyncImage(
                                model = person.photo,
                                contentDescription = person.name,
                                contentScale = ContentScale.Crop,
                                alignment = PortraitFaceAlignment,
                                modifier = Modifier.size(60.dp)
                            )
                        } else {
                            Text(
                                text = person.name.take(1).uppercase(),
                                style = MaterialTheme.typography.titleLarge,
                                color = NuvioTheme.colors.TextPrimary,
                                modifier = Modifier.padding(top = 14.dp).fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    Text(
                        text = person.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = NuvioTheme.colors.TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }
}
