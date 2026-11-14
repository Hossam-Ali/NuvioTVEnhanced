package com.nuvio.tv.core.tmdb

import android.content.Context
import com.nuvio.tv.domain.model.ContentType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** A title's date for "Recently added", and when it was looked up. [date] is null when TMDB has none. */
data class KnownReleaseDate(val date: LocalDate?, val fetchedAtMs: Long)

/**
 * When titles last came out (see [TmdbMetadataService.fetchLatestReleaseDate]), kept on the
 * device. Add-ons rarely give full release dates, so "Recently added" asks TMDB once per title.
 * A series' date moves with each new episode, so it is asked again after half a day; a movie's
 * after a week; a title TMDB had nothing for, after a day.
 */
@Singleton
class ReleaseDateStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tmdbService: TmdbService,
    private val tmdbMetadataService: TmdbMetadataService
) {
    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private val saved: ConcurrentHashMap<String, KnownReleaseDate> by lazy { load() }
    private val lookups = Semaphore(MAX_PARALLEL_LOOKUPS)

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { saved.size }
    }

    /** The saved date for [id], or null when it was never looked up or TMDB had none. */
    fun known(id: String): LocalDate? = saved[id]?.date

    /** Whether [id] needs (another) lookup at [nowMs]. */
    fun needsLookUp(id: String, type: ContentType, nowMs: Long = System.currentTimeMillis()): Boolean {
        val entry = saved[id] ?: return true
        return nowMs - entry.fetchedAtMs > maxAgeMs(entry, type)
    }

    /** Asks TMDB for [id]'s date and keeps the answer; false when TMDB couldn't be asked. */
    suspend fun lookUp(id: String, apiType: String, type: ContentType): Boolean {
        val answer = lookups.withPermit {
            try {
                val tmdbId = tmdbService.ensureTmdbId(id, apiType) ?: return@withPermit Result.success<LocalDate?>(null)
                Result.success(tmdbMetadataService.fetchLatestReleaseDate(tmdbId, type))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
        val date = answer.getOrElse { return false }
        save(id, KnownReleaseDate(date, System.currentTimeMillis()))
        return true
    }

    private fun maxAgeMs(entry: KnownReleaseDate, type: ContentType): Long = when {
        entry.date == null -> DAY_MS
        type == ContentType.SERIES || type == ContentType.TV -> DAY_MS / 2
        else -> 7 * DAY_MS
    }

    private fun save(id: String, entry: KnownReleaseDate) {
        saved[id] = entry
        val editor = prefs.edit()
        if (saved.size > MAX_SAVED) editor.clear()
        editor.putString(id, "${entry.date ?: NONE}$SEPARATOR${entry.fetchedAtMs}").apply()
    }

    private fun load(): ConcurrentHashMap<String, KnownReleaseDate> {
        val map = ConcurrentHashMap<String, KnownReleaseDate>()
        prefs.all.forEach { (id, value) ->
            val text = value as? String ?: return@forEach
            val date = text.substringBefore(SEPARATOR)
            val fetchedAt = text.substringAfter(SEPARATOR, "").toLongOrNull() ?: return@forEach
            map[id] = KnownReleaseDate(if (date == NONE) null else runCatching { LocalDate.parse(date) }.getOrNull(), fetchedAt)
        }
        return map
    }

    private companion object {
        const val PREFS_NAME = "nuvio_release_dates"
        const val SEPARATOR = '|'
        const val NONE = "-"
        const val MAX_SAVED = 5_000
        const val MAX_PARALLEL_LOOKUPS = 4
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
