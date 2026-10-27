package com.nuvio.tv.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCatalogTest {

    @Test
    fun `primary profile in advanced debug build sees every category in group order`() {
        val categories = visibleSettingsCategories(
            isPrimaryProfile = true,
            isEssentialMode = false,
            isDebugBuild = true
        )

        assertEquals(
            listOf(
                SettingsCategory.SEARCH,
                SettingsCategory.ACCOUNT,
                SettingsCategory.PROFILES,
                SettingsCategory.APPEARANCE,
                SettingsCategory.LAYOUT,
                SettingsCategory.PLAYBACK,
                SettingsCategory.SUBTITLES,
                SettingsCategory.CONTENT_DISCOVERY,
                SettingsCategory.INTEGRATION,
                SettingsCategory.TRACKING,
                SettingsCategory.ADVANCED,
                SettingsCategory.ABOUT,
                SettingsCategory.DEBUG
            ),
            categories
        )
    }

    @Test
    fun `secondary profile does not see account or profiles`() {
        val categories = visibleSettingsCategories(
            isPrimaryProfile = false,
            isEssentialMode = false,
            isDebugBuild = false
        )

        assertFalse(SettingsCategory.ACCOUNT in categories)
        assertFalse(SettingsCategory.PROFILES in categories)
        assertEquals(listOf(SettingsCategory.SEARCH, SettingsCategory.APPEARANCE), categories.take(2))
    }

    @Test
    fun `debug is hidden in release builds and in essential mode`() {
        assertFalse(SettingsCategory.DEBUG in visibleSettingsCategories(true, isEssentialMode = false, isDebugBuild = false))
        assertFalse(SettingsCategory.DEBUG in visibleSettingsCategories(true, isEssentialMode = true, isDebugBuild = true))
    }

    @Test
    fun `experience never appears in the rail`() {
        allCombinations().forEach { categories ->
            assertFalse(SettingsCategory.EXPERIENCE in categories)
        }
    }

    @Test
    fun `about is the last regular category`() {
        allCombinations().forEach { categories ->
            val withoutDebug = categories - SettingsCategory.DEBUG
            assertEquals(SettingsCategory.ABOUT, withoutDebug.last())
        }
    }

    @Test
    fun `each group is contiguous in every configuration`() {
        allCombinations().forEach { categories ->
            val groupRuns = categories.map { it.group }.fold(mutableListOf<SettingsRailGroup>()) { runs, group ->
                if (runs.lastOrNull() != group) runs.add(group)
                runs
            }
            assertEquals(groupRuns.distinct(), groupRuns)
        }
    }

    @Test
    fun `the rail has search and one entry per section, seven in a full configuration`() {
        val rail = visibleSettingsCategories(
            isPrimaryProfile = true,
            isEssentialMode = false,
            isDebugBuild = true
        ).railAnchors()

        assertEquals(
            listOf(
                SettingsCategory.SEARCH,
                SettingsCategory.ACCOUNT,
                SettingsCategory.APPEARANCE,
                SettingsCategory.PLAYBACK,
                SettingsCategory.SUBTITLES,
                SettingsCategory.CONTENT_DISCOVERY,
                SettingsCategory.TRACKING,
                SettingsCategory.ADVANCED
            ),
            rail
        )
        assertEquals(SettingsSection.entries, rail.map { it.section })
    }

    @Test
    fun `a section lists its visible pages as tabs`() {
        val categories = visibleSettingsCategories(true, isEssentialMode = false, isDebugBuild = false)
        assertEquals(listOf(SettingsCategory.APPEARANCE, SettingsCategory.LAYOUT), categories.pagesOf(SettingsSection.APPEARANCE))
        assertEquals(listOf(SettingsCategory.CONTENT_DISCOVERY, SettingsCategory.INTEGRATION), categories.pagesOf(SettingsSection.SOURCES))
        assertEquals(listOf(SettingsCategory.ADVANCED, SettingsCategory.ABOUT), categories.pagesOf(SettingsSection.SYSTEM))
        assertEquals(listOf(SettingsCategory.SUBTITLES), categories.pagesOf(SettingsSection.SUBTITLES))
    }

    @Test
    fun `a secondary profile without watch party has no account section`() {
        val rail = visibleSettingsCategories(false, isEssentialMode = false, isDebugBuild = false).railAnchors()
        assertFalse(rail.any { it.section == SettingsSection.ACCOUNT })
        assertEquals(SettingsCategory.APPEARANCE, rail[1])
    }

    @Test
    fun `subtitles get their own section only outside essential mode`() {
        assertTrue(SettingsCategory.SUBTITLES in visibleSettingsCategories(true, isEssentialMode = false, isDebugBuild = false))
        assertFalse(SettingsCategory.SUBTITLES in visibleSettingsCategories(true, isEssentialMode = true, isDebugBuild = false))
    }

    @Test
    fun `dividers are placed only where the group changes`() {
        val rail = visibleSettingsCategories(
            isPrimaryProfile = true,
            isEssentialMode = false,
            isDebugBuild = true
        ).railAnchors()
        val dividerBefore = rail.indices.filter { rail.startsNewGroup(it) }.map { rail[it] }

        assertEquals(
            listOf(
                SettingsCategory.ACCOUNT,
                SettingsCategory.APPEARANCE,
                SettingsCategory.PLAYBACK,
                SettingsCategory.TRACKING,
                SettingsCategory.ADVANCED
            ),
            dividerBefore
        )
        assertFalse(rail.startsNewGroup(0))
    }

    @Test
    fun `secondary profile rail starts without a divider`() {
        val categories = visibleSettingsCategories(
            isPrimaryProfile = false,
            isEssentialMode = true,
            isDebugBuild = false
        )
        assertFalse(categories.startsNewGroup(0))
        assertTrue(categories.startsNewGroup(categories.indexOf(SettingsCategory.PLAYBACK)))
    }

    @Test
    fun `watch party shows for every profile only while available`() {
        listOf(true, false).forEach { primary ->
            val available = visibleSettingsCategories(primary, isEssentialMode = false, isDebugBuild = false, isWatchPartyAvailable = true)
            val unavailable = visibleSettingsCategories(primary, isEssentialMode = false, isDebugBuild = false, isWatchPartyAvailable = false)
            assertTrue(SettingsCategory.WATCH_PARTY in available)
            assertFalse(SettingsCategory.WATCH_PARTY in unavailable)
        }
        assertEquals(
            listOf(SettingsCategory.ACCOUNT, SettingsCategory.PROFILES, SettingsCategory.WATCH_PARTY),
            visibleSettingsCategories(true, isEssentialMode = false, isDebugBuild = false, isWatchPartyAvailable = true).drop(1).take(3)
        )
    }

    private fun allCombinations(): List<List<SettingsCategory>> =
        listOf(true, false).flatMap { primary ->
            listOf(true, false).flatMap { essential ->
                listOf(true, false).flatMap { debug ->
                    listOf(true, false).map { watchParty ->
                        visibleSettingsCategories(primary, essential, debug, watchParty)
                    }
                }
            }
        }
}
