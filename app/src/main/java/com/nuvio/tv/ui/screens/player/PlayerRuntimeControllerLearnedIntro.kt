package com.nuvio.tv.ui.screens.player

import android.widget.Toast
import com.nuvio.tv.R
import com.nuvio.tv.core.player.LearnedIntro
import com.nuvio.tv.core.player.LearnedIntroStore
import com.nuvio.tv.data.repository.SkipInterval
import com.nuvio.tv.ui.screens.detail.formatPlaybackTime

private const val MIN_INTRO_MS = 5_000L
private const val MAX_INTRO_MS = 6 * 60_000L

/** The show this episode belongs to, when it's a series episode; intros are learned per show. */
private fun PlayerRuntimeController.introShowId(): String? {
    val type = contentType?.lowercase() ?: return null
    if (type != "series" && type != "tv") return null
    if (currentSeason == null || currentEpisode == null) return null
    return contentId?.takeIf { it.isNotBlank() }
}

/**
 * "Mark intro": the first press marks where the intro starts, the second where it ends. The intro
 * is saved for the show, and every episode of it then offers Skip intro at that time.
 */
internal fun PlayerRuntimeController.markIntro() {
    val showId = introShowId()
    if (showId == null) {
        Toast.makeText(context, R.string.intro_mark_series_only, Toast.LENGTH_SHORT).show()
        return
    }
    val position = currentPlaybackPositionMs() ?: return
    val start = pendingIntroStartMs
    if (start == null || position <= start) {
        pendingIntroStartMs = position
        Toast.makeText(context, context.getString(R.string.intro_mark_start, formatPlaybackTime(position)), Toast.LENGTH_LONG).show()
        return
    }
    pendingIntroStartMs = null
    val length = position - start
    if (length < MIN_INTRO_MS || length > MAX_INTRO_MS) {
        Toast.makeText(context, R.string.intro_mark_invalid, Toast.LENGTH_LONG).show()
        return
    }
    LearnedIntroStore.save(context, showId, LearnedIntro(start, position))
    recomputeMergedSkipIntervals()
    Toast.makeText(
        context,
        context.getString(R.string.intro_mark_saved, formatPlaybackTime(start), formatPlaybackTime(position)),
        Toast.LENGTH_LONG
    ).show()
}

/** The intro the viewer marked for this show, as a skip interval. */
internal fun PlayerRuntimeController.learnedIntroInterval(): SkipInterval? {
    val showId = introShowId() ?: return null
    val intro = LearnedIntroStore.get(context, showId) ?: return null
    return SkipInterval(
        startTime = intro.startMs / 1000.0,
        endTime = intro.endMs / 1000.0,
        type = "intro",
        provider = "learned"
    )
}
