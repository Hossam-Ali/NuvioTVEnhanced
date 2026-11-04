package com.nuvio.tv.core.player

import android.content.Context

/** Where a show's intro sits in each episode, marked once by the viewer. */
data class LearnedIntro(val startMs: Long, val endMs: Long)

/** Intros the viewer marked, one per show, kept on the device. */
object LearnedIntroStore {
    private const val PREFS_NAME = "nuvio_learned_intros"

    fun get(context: Context, showId: String): LearnedIntro? {
        val value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(showId, null) ?: return null
        val parts = value.split(',')
        val start = parts.getOrNull(0)?.toLongOrNull() ?: return null
        val end = parts.getOrNull(1)?.toLongOrNull() ?: return null
        return LearnedIntro(start, end).takeIf { end > start }
    }

    fun save(context: Context, showId: String, intro: LearnedIntro) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(showId, "${intro.startMs},${intro.endMs}")
            .apply()
    }
}
