package com.nuvio.tv.ui.navigation

import android.content.Context
import com.nuvio.tv.data.local.PlayerSettingsDataStore
import com.nuvio.tv.data.local.StreamLinkCacheDataStore
import com.nuvio.tv.data.local.PlayerPreference
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DirectResumeEntryPoint {
    fun streamLinkCacheDataStore(): StreamLinkCacheDataStore
    fun playerSettingsDataStore(): PlayerSettingsDataStore
}

/** How old a saved source may be for Resume to play it without the source screen. */
private const val DIRECT_RESUME_MAX_AGE_MS = 24L * 60 * 60 * 1000

/** What Resume needs to know about the episode or film being resumed. */
internal data class ResumeTarget(
    val videoId: String,
    val contentType: String,
    val title: String,
    val poster: String?,
    val backdrop: String?,
    val logo: String?,
    val season: Int?,
    val episode: Int?,
    val episodeName: String?,
    val contentId: String?,
    val contentName: String?,
    val contentLanguage: String?,
    val returnToDetailOnBack: Boolean,
    val returnToHomeOnBack: Boolean
)

/**
 * The player route for Resume when the source last played for this episode or film is still
 * saved: playback starts at once, without the source screen asking every add-on again. Null when
 * there is no fresh saved source, it's a torrent (which needs the source screen's P2P checks), or
 * the user plays in an external player; Resume then opens the source screen as before.
 */
internal suspend fun directResumeRoute(context: Context, target: ResumeTarget): String? {
    val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, DirectResumeEntryPoint::class.java)
    val settings = entryPoint.playerSettingsDataStore().playerSettings.first()
    if (settings.playerPreference != PlayerPreference.INTERNAL) return null
    val maxAgeMs = maxOf(settings.streamReuseLastLinkCacheHours * 60L * 60L * 1000L, DIRECT_RESUME_MAX_AGE_MS)
    val cached = entryPoint.streamLinkCacheDataStore().getValid(
        contentKey = "${target.contentType.lowercase()}|${target.videoId}",
        maxAgeMs = maxAgeMs
    ) ?: return null
    if (cached.url.isBlank() || !cached.url.startsWith("http", ignoreCase = true)) return null
    return Screen.Player.createRoute(
        streamUrl = cached.url,
        title = target.title,
        streamName = cached.streamName,
        year = cached.year,
        headers = cached.headers,
        contentId = target.contentId ?: target.videoId.substringBefore(":"),
        contentType = target.contentType,
        contentName = target.contentName ?: target.title,
        poster = target.poster,
        backdrop = target.backdrop,
        logo = target.logo,
        videoId = target.videoId,
        season = target.season,
        episode = target.episode,
        episodeTitle = target.episodeName,
        bingeGroup = cached.bingeGroup,
        autoPlayNav = true,
        returnToDetailOnBack = target.returnToDetailOnBack,
        returnToHomeOnBack = target.returnToHomeOnBack,
        filename = cached.filename,
        videoHash = cached.videoHash,
        videoSize = cached.videoSize,
        fileIdx = cached.fileIdx,
        sources = cached.sources,
        contentLanguage = cached.contentLanguage ?: target.contentLanguage,
        audioDelayMs = cached.audioDelayMs,
        audioDelayLanguage = cached.audioDelayLanguage
    )
}
