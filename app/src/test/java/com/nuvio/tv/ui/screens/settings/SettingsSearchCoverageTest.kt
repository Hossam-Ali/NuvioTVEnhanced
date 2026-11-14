package com.nuvio.tv.ui.screens.settings

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Settings search lists every setting: each settings row's title has to be in
 * [settingsSearchIndex], so a setting added to a page can't be forgotten there.
 */
class SettingsSearchCoverageTest {

    private val settingsDir = File("src/main/java/com/nuvio/tv/ui/screens/settings")

    /** Rows that aren't settings of their own, or only exist where normal search doesn't look. */
    private val notSettings = setOf(
        // Essential mode's simplified Playback page; normal mode lists the full settings.
        "essential_stream_selection", "essential_autoplay_next_episode", "essential_p2p_streams",
        "essential_subtitle_language", "essential_audio_language", "experience_mode_switch_to_advanced",
        // Values inside the card-depth fine-tune dialog, reached through its own entry.
        "settings_card_depth_edge_value", "settings_card_depth_sheen_value", "settings_card_depth_coverage_value",
        // Descriptions and status text the row scan picks up as titles.
        "servers_add_description", "network_speed_test_subtitle", "stream_test_btn_running",
        // The speed test row's label while a test runs; its own title is network_speed_test_run.
        "network_speed_test_running",
        "settings_tmdb_subtitle", "settings_animeskip_subtitle", "layout_open",
    )

    private val rowCall = Regex(
        """(SettingsToggleRow|SettingsActionRow|SliderSettingsItem|ColorSettingsItem)\("""
    )
    /** A title from a string resource, also when it picks one of two (`if (running) … else …`). */
    private val stringTitle = Regex(
        """title\s*=\s*stringResource\(\s*(?:if\s*\([^)]*\)\s*)?R\.string\.(\w+)""",
        RegexOption.DOT_MATCHES_ALL
    )

    private fun rowTitles(): Map<String, String> {
        val titles = LinkedHashMap<String, String>()
        settingsDir.listFiles { file -> file.extension == "kt" }.orEmpty()
            .filterNot { it.name in setOf("SettingsSearchIndex.kt", "SettingsSearch.kt", "SettingsDesignSystem.kt", "SettingsValueRows.kt") }
            .sortedBy { it.name }
            .forEach { file ->
                val source = file.readText()
                rowCall.findAll(source).forEach { call ->
                    val start = call.range.last + 1
                    val head = source.substring(start, minOf(source.length, start + 400))
                    // Only the title argument itself: rows titled by a variable (a provider's name) are skipped.
                    val title = stringTitle.find(head.take(160))?.groupValues?.get(1)
                    if (title != null) titles.putIfAbsent(title, file.name)
                }
            }
        return titles
    }

    @Test
    fun `every settings row can be found by settings search`() {
        val index = File(settingsDir, "SettingsSearchIndex.kt").readText()
        val indexed = Regex("""SettingsSearchEntry\(R\.string\.(\w+)""").findAll(index).map { it.groupValues[1] }.toSet()
        val rows = rowTitles()
        assertTrue("the scan found no settings rows; is the working directory the app module?", rows.size > 100)
        val missing = rows.filterKeys { it !in indexed && it !in notSettings }
        assertTrue(
            "Settings rows missing from SettingsSearchIndex.kt (add an entry, or list it in notSettings): " +
                missing.entries.joinToString { "${it.key} (${it.value})" },
            missing.isEmpty()
        )
    }
}
