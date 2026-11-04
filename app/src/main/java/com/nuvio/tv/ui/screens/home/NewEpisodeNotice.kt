package com.nuvio.tv.ui.screens.home

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.nuvio.tv.R
import kotlinx.coroutines.delay

private const val PREFS_NAME = "nuvio_new_episode_alerts"
private const val KEY_ANNOUNCED = "announced"
private const val MAX_REMEMBERED = 200
private const val NOTICE_VISIBLE_MS = 7_000L

private val NoticeGreen = Color(0xFF3DDC97)

/**
 * A short notice on the home screen when an episode of something in Continue Watching has just
 * come out. Each episode is announced once.
 */
@Composable
internal fun NewEpisodeNotice(items: List<ContinueWatchingItem>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var notice by remember { mutableStateOf<String?>(null) }
    val freshKeys = remember(items) {
        items.mapNotNull { item ->
            (item as? ContinueWatchingItem.NextUp)?.takeIf { it.isFreshRelease() }
        }
    }

    LaunchedEffect(freshKeys) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val announced = prefs.getStringSet(KEY_ANNOUNCED, emptySet()).orEmpty()
        val unannounced = freshKeys.filter { it.alertKey() !in announced }
        if (unannounced.isEmpty()) return@LaunchedEffect
        prefs.edit()
            .putStringSet(KEY_ANNOUNCED, (announced + unannounced.map { it.alertKey() }).toList().takeLast(MAX_REMEMBERED).toSet())
            .apply()
        notice = if (unannounced.size == 1) {
            val info = unannounced.first().info
            context.getString(R.string.new_episode_notice_one, info.name, info.season, info.episode)
        } else {
            context.getString(R.string.new_episode_notice_many, unannounced.size)
        }
        delay(NOTICE_VISIBLE_MS)
        notice = null
    }

    AnimatedVisibility(
        visible = notice != null,
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut() + slideOutVertically { -it / 2 },
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xE6111318))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.new_episode_notice_badge),
                color = Color(0xFF06281A),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(NoticeGreen)
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            )
            Text(
                text = notice.orEmpty(),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun ContinueWatchingItem.NextUp.alertKey(): String = "${info.contentId}:s${info.season}e${info.episode}"
