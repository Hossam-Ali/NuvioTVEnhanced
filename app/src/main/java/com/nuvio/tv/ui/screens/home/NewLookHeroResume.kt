package com.nuvio.tv.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nuvio.tv.R
import com.nuvio.tv.ui.screens.detail.NewLookPrimaryButton
import com.nuvio.tv.ui.theme.NuvioTheme
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * New look: Resume (or Play next) under the hero for the Continue Watching card in focus.
 * Up from the row reaches it; Down goes back to the card it came from.
 */
@Composable
internal fun NewLookHeroResumeRow(
    item: ContinueWatchingItem,
    progressFraction: Float?,
    downFocusRequester: FocusRequester,
    onClick: () -> Unit
) {
    val label = when (item) {
        is ContinueWatchingItem.InProgress -> {
            val season = item.progress.season
            val episode = item.progress.episode
            if (season != null && episode != null) {
                stringResource(R.string.new_look_resume_episode, season, episode)
            } else {
                stringResource(R.string.new_look_resume)
            }
        }
        is ContinueWatchingItem.NextUp -> {
            // An episode that hasn't aired can't be played yet.
            if (!item.info.hasAired) return
            stringResource(R.string.new_look_play_episode, item.info.season, item.info.episode)
        }
    }
    Row(
        modifier = Modifier.padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        NewLookPrimaryButton(
            text = label,
            onClick = onClick,
            modifier = Modifier.focusProperties { down = downFocusRequester }
        )
        val remainingMs = (item as? ContinueWatchingItem.InProgress)?.progress?.takeIf { it.duration > 0 }?.remainingTime
        if (progressFraction != null || remainingMs != null) {
            // Time left in small text over the bar; together they stay within the button's height.
            Column(
                modifier = Modifier.width(150.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (remainingMs != null) {
                    Text(
                        text = com.nuvio.tv.ui.components.formatRemainingTime(
                            remainingMs = remainingMs,
                            strHoursMinLeft = stringResource(R.string.cw_hours_min_left),
                            strMinLeft = stringResource(R.string.cw_min_left),
                            strAlmostDone = stringResource(R.string.cw_almost_done)
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = NuvioTheme.colors.TextSecondary,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                progressFraction?.let { HeroResumeProgress(fraction = it, modifier = Modifier.fillMaxWidth()) }
            }
        }
    }
}
