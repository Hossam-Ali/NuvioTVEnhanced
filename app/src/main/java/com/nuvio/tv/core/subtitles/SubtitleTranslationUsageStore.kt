package com.nuvio.tv.core.subtitles

import android.content.Context
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Translation spending: this month on this TV, and the video playing now. */
data class SubtitleTranslationSpending(
    val monthCostUsd: Double = 0.0,
    val monthLines: Int = 0,
    /** False when a provider did not report costs (OpenAI), so the month total is incomplete. */
    val monthCostComplete: Boolean = true,
    val videoCostUsd: Double = 0.0,
    val videoLines: Int = 0,
)

/**
 * Adds up what AI subtitle translation costs, from the cost OpenRouter reports with each request.
 * The month total is kept on the device and starts again each calendar month.
 */
object SubtitleTranslationUsageStore {
    private const val PREFS_NAME = "nuvio_tv_subtitle_translation_usage"
    private const val KEY_MONTH = "month"
    private const val KEY_COST = "cost_usd"
    private const val KEY_LINES = "lines"
    private const val KEY_COMPLETE = "cost_complete"

    private val lock = Any()
    @Volatile private var appContext: Context? = null
    private val _spending = MutableStateFlow(SubtitleTranslationSpending())
    val spending: StateFlow<SubtitleTranslationSpending> = _spending.asStateFlow()

    fun attach(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        synchronized(lock) {
            val prefs = prefs() ?: return
            val month = YearMonth.now().toString()
            _spending.value = if (prefs.getString(KEY_MONTH, null) == month) {
                SubtitleTranslationSpending(
                    monthCostUsd = prefs.getString(KEY_COST, null)?.toDoubleOrNull() ?: 0.0,
                    monthLines = prefs.getInt(KEY_LINES, 0),
                    monthCostComplete = prefs.getBoolean(KEY_COMPLETE, true),
                )
            } else {
                SubtitleTranslationSpending()
            }
        }
    }

    /** Starts the per-video totals again; called when a new stream starts. */
    fun startVideo() {
        synchronized(lock) { _spending.value = _spending.value.copy(videoCostUsd = 0.0, videoLines = 0) }
    }

    internal fun record(usage: SubtitleTranslationUsage) {
        synchronized(lock) {
            val month = YearMonth.now().toString()
            val prefs = prefs()
            val current = _spending.value.let {
                // A new calendar month starts the month total again.
                if (prefs != null && prefs.getString(KEY_MONTH, month) != month) {
                    it.copy(monthCostUsd = 0.0, monthLines = 0, monthCostComplete = true)
                } else {
                    it
                }
            }
            val cost = usage.costUsd ?: 0.0
            val next = current.copy(
                monthCostUsd = current.monthCostUsd + cost,
                monthLines = current.monthLines + usage.lines,
                monthCostComplete = current.monthCostComplete && usage.costUsd != null,
                videoCostUsd = current.videoCostUsd + cost,
                videoLines = current.videoLines + usage.lines,
            )
            _spending.value = next
            prefs?.edit()
                ?.putString(KEY_MONTH, month)
                ?.putString(KEY_COST, next.monthCostUsd.toString())
                ?.putInt(KEY_LINES, next.monthLines)
                ?.putBoolean(KEY_COMPLETE, next.monthCostComplete)
                ?.apply()
        }
    }

    private fun prefs() = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
