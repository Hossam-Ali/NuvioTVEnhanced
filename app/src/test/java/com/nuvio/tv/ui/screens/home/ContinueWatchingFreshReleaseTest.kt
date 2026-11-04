package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.ContinueWatchingSortMode
import com.nuvio.tv.domain.model.WatchProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinueWatchingFreshReleaseTest {
    private val now = 10_000_000_000L
    private val hour = 60L * 60 * 1000

    private fun nextUp(id: String, releasedAgo: Long?, lastWatched: Long, alert: Boolean = true, aired: Boolean = true) =
        ContinueWatchingItem.NextUp(
            NextUpInfo(
                contentId = id, contentType = "series", name = id, poster = null, backdrop = null, logo = null,
                videoId = "$id:1:2", season = 1, episode = 2, episodeTitle = null, thumbnail = null,
                hasAired = aired, lastWatched = lastWatched,
                sortTimestamp = releasedAgo?.let { now - it } ?: lastWatched,
                releaseTimestamp = releasedAgo?.let { now - it }, isReleaseAlert = alert
            )
        )

    private fun inProgress(id: String, lastWatched: Long) = ContinueWatchingItem.InProgress(
        WatchProgress(
            contentId = id, contentType = "movie", name = id, poster = null, backdrop = null, logo = null,
            videoId = id, season = null, episode = null, episodeTitle = null,
            position = 1_000L, duration = 10_000L, lastWatched = lastWatched
        )
    )

    private fun ContinueWatchingItem.id() = when (this) {
        is ContinueWatchingItem.InProgress -> progress.contentId
        is ContinueWatchingItem.NextUp -> info.contentId
    }

    @Test fun `an episode out in the last two days goes first in every mode`() {
        val items = listOf(
            inProgress("movie", lastWatched = now - hour),
            nextUp("old", releasedAgo = 10 * 24 * hour, lastWatched = now - 30 * 24 * hour),
            nextUp("fresh", releasedAgo = 5 * hour, lastWatched = now - 7 * 24 * hour)
        )
        ContinueWatchingSortMode.entries.forEach { mode ->
            assertEquals("fresh", sortContinueWatchingItems(items, mode, now).first().id())
        }
    }

    @Test fun `only aired release alerts within two days count`() {
        assertTrue(nextUp("a", releasedAgo = 47 * hour, lastWatched = 0).isFreshRelease(now))
        assertFalse(nextUp("b", releasedAgo = 49 * hour, lastWatched = 0).isFreshRelease(now))
        assertFalse(nextUp("c", releasedAgo = hour, lastWatched = 0, alert = false).isFreshRelease(now))
        assertFalse(nextUp("d", releasedAgo = null, lastWatched = 0).isFreshRelease(now))
        assertFalse(nextUp("e", releasedAgo = -hour, lastWatched = 0, aired = false).isFreshRelease(now))
        assertFalse(inProgress("f", lastWatched = now).isFreshRelease(now))
    }
}
