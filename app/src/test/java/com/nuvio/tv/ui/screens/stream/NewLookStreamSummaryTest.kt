package com.nuvio.tv.ui.screens.stream

import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamBehaviorHints
import com.nuvio.tv.domain.model.StreamDebridCacheState
import com.nuvio.tv.domain.model.StreamDebridCacheStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewLookStreamSummaryTest {
    private fun stream(
        name: String? = null,
        title: String? = null,
        description: String? = null,
        hints: StreamBehaviorHints? = null,
        cache: StreamDebridCacheStatus? = null,
        qualityValue: Int = -1
    ) = Stream(
        name = name, title = title, description = description, url = "https://example.com/v.mkv",
        ytId = null, infoHash = null, fileIdx = null, externalUrl = null, behaviorHints = hints,
        addonName = "Addon", addonLogo = null, qualityValue = qualityValue, debridCacheStatus = cache
    )

    @Test fun `typical torrent title becomes plain labels`() {
        val summary = summarizeStream(
            stream(
                name = "Torrentio\n4k DV | HDR",
                title = "Show.S01E01.2160p.WEB-DL.DDP5.1.Atmos.DV.HDR.H.265-GRP\n👤 120 💾 8.9 GB ⚙️ ThePirateBay"
            )
        )
        assertEquals(StreamQualityGroup.UHD, summary.quality)
        assertEquals("4K", summary.qualityLabel)
        assertEquals("WEB-DL", summary.source)
        assertEquals(listOf("Dolby Vision", "Atmos 5.1", "Dolby Digital+", "HEVC"), summary.features)
        assertEquals(8_900_000_000L, summary.sizeBytes)
        assertEquals("Show.S01E01.2160p.WEB-DL.DDP5.1.Atmos.DV.HDR.H.265-GRP", summary.fileName)
    }

    @Test fun `file name and size from behavior hints win`() {
        val summary = summarizeStream(
            stream(
                name = "Addon 1080p",
                title = "Some title 💾 1.0 GB",
                hints = StreamBehaviorHints(
                    notWebReady = null, bingeGroup = null, countryWhitelist = null, proxyHeaders = null,
                    videoSize = 2_400_000_000L, filename = "Movie.2024.1080p.BluRay.x264.AAC.mkv"
                )
            )
        )
        assertEquals(StreamQualityGroup.FULL_HD, summary.quality)
        assertEquals("BluRay", summary.source)
        assertEquals(listOf("AAC"), summary.features)
        assertEquals(2_400_000_000L, summary.sizeBytes)
        assertEquals("Movie.2024.1080p.BluRay.x264.AAC.mkv", summary.fileName)
    }

    @Test fun `dts-hd is not also listed as dts`() {
        val summary = summarizeStream(stream(title = "Film 1080p REMUX DTS-HD MA 7.1"))
        assertEquals("REMUX", summary.source)
        assertEquals(listOf("DTS-HD 7.1"), summary.features)
    }

    @Test fun `quality falls back to the add-on value`() {
        val summary = summarizeStream(stream(name = "Server", title = "Episode 3", qualityValue = 720))
        assertEquals(StreamQualityGroup.HD, summary.quality)
        assertEquals("720p", summary.qualityLabel)
        assertNull(summary.source)
        assertTrue(summary.features.isEmpty())
    }

    @Test fun `unknown quality stays unknown`() {
        val summary = summarizeStream(stream(name = "Direct link"))
        assertEquals(StreamQualityGroup.UNKNOWN, summary.quality)
        assertNull(summary.qualityLabel)
        assertNull(summary.sizeBytes)
    }

    @Test fun `debrid cache state marks cached sources`() {
        val cached = StreamDebridCacheStatus("rd", "Real-Debrid", StreamDebridCacheState.CACHED, cachedSize = 3_000_000_000L)
        val summary = summarizeStream(stream(title = "Film 1080p", cache = cached))
        assertTrue(summary.cached)
        assertEquals(3_000_000_000L, summary.sizeBytes)
        assertFalse(summarizeStream(stream(title = "Film 1080p")).cached)
    }

    @Test fun `sizes are written short`() {
        assertEquals("2.4 GB", formatStreamSize(2_400_000_000L))
        assertEquals("780 MB", formatStreamSize(780_000_000L))
        assertEquals(1_500_000_000L, parseSize("size 1,5 GiB"))
    }
}
