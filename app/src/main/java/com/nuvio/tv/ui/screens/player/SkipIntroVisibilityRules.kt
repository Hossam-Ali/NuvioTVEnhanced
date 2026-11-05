package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.data.repository.SkipInterval
import com.nuvio.tv.data.repository.segmentCategory

internal const val SKIP_INTRO_AUTO_HIDE_TIMEOUT_MS = 10_000

internal fun findActiveSkipInterval(
    intervals: List<SkipInterval>,
    positionMs: Long,
): SkipInterval? {
    if (intervals.isEmpty()) return null
    val positionSec = positionMs / 1000.0
    return intervals.find { interval ->
        interval.type != "post-credits" &&
            positionSec >= interval.startTime && positionSec < interval.endTime
    }
}

internal fun nextActiveSkipInterval(
    intervals: List<SkipInterval>,
    positionMs: Long,
): SkipInterval? = findActiveSkipInterval(intervals, positionMs)

internal fun SkipInterval.followingPostCreditsScene(
    intervals: List<SkipInterval>,
    durationMs: Long,
): SkipInterval? {
    if (type != "movie-credits" && type !in PlayerNextEpisodeRules.OUTRO_SEGMENT_TYPES) return null
    // Explicit post-credits scene from IntroDb
    val explicit = intervals.filter {
        it.type == "post-credits" && it.startTime.isFinite() && it.endTime.isFinite() &&
            it.startTime >= endTime && it.endTime > it.startTime &&
            // A different release can end before the submitted scene does; its start is still playable.
            (durationMs <= 0L || it.startTime * 1000.0 < durationMs)
    }.minByOrNull { it.startTime }
    if (explicit != null) return explicit
    // Heuristic: significant content after credits/outro suggests a post-credits scene.
    if (durationMs > 0L) {
        val creditsEndMs = (endTime * 1000.0).toLong()
        val gapMs = durationMs - creditsEndMs
        if (gapMs > PlayerNextEpisodeRules.POST_OUTRO_AUTOPLAY_GAP_MS) {
            return SkipInterval(
                startTime = endTime,
                endTime = durationMs / 1000.0,
                type = "post-credits",
                provider = "heuristic"
            )
        }
    }
    return null
}

internal fun isSkipIntroButtonVisible(
    hasActiveInterval: Boolean,
    dismissed: Boolean,
    controlsVisible: Boolean,
    autoHidden: Boolean,
): Boolean {
    val shouldShow = hasActiveInterval && (!dismissed || controlsVisible)
    return shouldShow && (!autoHidden || controlsVisible)
}

/**
 * Whether the Skip Intro button may accept D-pad focus.
 *
 * While the subtitle selection overlay is open, Skip Intro stays on screen but
 * must not be focusable — otherwise DPAD_DOWN past the last language/track card
 * escapes the overlay onto Skip Intro (#2874).
 */
internal fun isSkipIntroCanFocus(
    subtitleOverlayVisible: Boolean,
): Boolean = !subtitleOverlayVisible

internal fun skipIntroAutoHideRemainingMs(
    progress: Float,
    totalTimeoutMs: Int = SKIP_INTRO_AUTO_HIDE_TIMEOUT_MS,
): Int = ((1f - progress.coerceIn(0f, 1f)) * totalTimeoutMs).toInt().coerceAtLeast(1)

/** Skip segments shorter than this are dropped: they only flash the button. */
internal const val MIN_SKIP_SEGMENT_SEC = 3.0

/** Segments this close together are joined, so the button doesn't vanish between them. */
internal const val MAX_SKIP_SEGMENT_GAP_SEC = 5.0

/**
 * Skip segments as the Skip button should see them. A segment of a second or two (a stray chapter
 * mark) is dropped, and segments a few seconds apart, such as a recap right before the intro, are
 * joined into one that skips both, keeping the longer part's label. Post-credits scenes are left
 * as they are.
 */
internal fun smoothSkipIntervals(intervals: List<SkipInterval>): List<SkipInterval> {
    val (postCredits, segments) = intervals.partition { it.type == "post-credits" }
    val joined = mutableListOf<SkipInterval>()
    segments
        .filter { it.startTime.isFinite() && it.endTime.isFinite() && it.endTime - it.startTime >= MIN_SKIP_SEGMENT_SEC }
        .sortedBy { it.startTime }
        .forEach { segment ->
            val previous = joined.lastOrNull()
            if (previous != null && segment.startTime - previous.endTime <= MAX_SKIP_SEGMENT_GAP_SEC) {
                val longer = if (segment.endTime - segment.startTime > previous.endTime - previous.startTime) segment else previous
                joined[joined.lastIndex] = longer.copy(
                    startTime = previous.startTime,
                    endTime = maxOf(previous.endTime, segment.endTime)
                )
            } else {
                joined += segment
            }
        }
    return joined + postCredits
}

/** How far ahead a segment of the same kind may start and still keep the Skip button up. */
internal const val SKIP_REPLACEMENT_LOOKAHEAD_SEC = 10.0

/**
 * The segment of the same kind as [current] that starts within [SKIP_REPLACEMENT_LOOKAHEAD_SEC]
 * after [positionMs]. The Skip button switches to it instead of hiding until it starts.
 */
internal fun upcomingSameKindSkipInterval(
    intervals: List<SkipInterval>,
    current: SkipInterval,
    positionMs: Long,
): SkipInterval? {
    val positionSec = positionMs / 1000.0
    val kind = segmentCategory(current.type)
    return intervals.firstOrNull {
        segmentCategory(it.type) == kind &&
            positionSec < it.startTime &&
            it.startTime - positionSec <= SKIP_REPLACEMENT_LOOKAHEAD_SEC
    }
}
