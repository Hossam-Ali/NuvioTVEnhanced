package com.nuvio.tv.ui.screens.settings

internal enum class SettingsRailGroup {
    SEARCH,
    YOU,
    LOOK,
    WATCH,
    SERVICES,
    SYSTEM
}

/**
 * The entries of the settings rail: search, then seven sections. A section with more than one
 * page shows its pages as tabs above the options, so related settings live together without a
 * longer rail.
 */
internal enum class SettingsSection(val group: SettingsRailGroup) {
    SEARCH(SettingsRailGroup.SEARCH),
    ACCOUNT(SettingsRailGroup.YOU),
    APPEARANCE(SettingsRailGroup.LOOK),
    PLAYBACK(SettingsRailGroup.WATCH),
    SUBTITLES(SettingsRailGroup.WATCH),
    SOURCES(SettingsRailGroup.WATCH),
    TRACKING(SettingsRailGroup.SERVICES),
    SYSTEM(SettingsRailGroup.SYSTEM)
}

/** A page of options. Declared in rail order, pages of one section next to each other. */
internal enum class SettingsCategory(val section: SettingsSection) {
    SEARCH(SettingsSection.SEARCH),
    ACCOUNT(SettingsSection.ACCOUNT),
    PROFILES(SettingsSection.ACCOUNT),
    WATCH_PARTY(SettingsSection.ACCOUNT),
    APPEARANCE(SettingsSection.APPEARANCE),
    LAYOUT(SettingsSection.APPEARANCE),
    PLAYBACK(SettingsSection.PLAYBACK),
    SUBTITLES(SettingsSection.SUBTITLES),
    CONTENT_DISCOVERY(SettingsSection.SOURCES),
    INTEGRATION(SettingsSection.SOURCES),
    TRACKING(SettingsSection.TRACKING),
    ADVANCED(SettingsSection.SYSTEM),
    ABOUT(SettingsSection.SYSTEM),
    DEBUG(SettingsSection.SYSTEM),
    EXPERIENCE(SettingsSection.SYSTEM);

    val group: SettingsRailGroup get() = section.group
}

internal fun visibleSettingsCategories(
    isPrimaryProfile: Boolean,
    isEssentialMode: Boolean,
    isDebugBuild: Boolean,
    isWatchPartyAvailable: Boolean = false
): List<SettingsCategory> = SettingsCategory.entries.filter { category ->
    when (category) {
        SettingsCategory.ACCOUNT,
        SettingsCategory.PROFILES -> isPrimaryProfile
        SettingsCategory.WATCH_PARTY -> isWatchPartyAvailable
        // Essential mode's simplified playback page already includes the subtitle basics.
        SettingsCategory.SUBTITLES -> !isEssentialMode
        SettingsCategory.DEBUG -> isDebugBuild && !isEssentialMode
        SettingsCategory.EXPERIENCE -> false
        else -> true
    }
}

/** One rail entry per section: the section's first visible page stands for it. */
internal fun List<SettingsCategory>.railAnchors(): List<SettingsCategory> = distinctBy { it.section }

/** The visible pages of [section], in order. */
internal fun List<SettingsCategory>.pagesOf(section: SettingsSection): List<SettingsCategory> =
    filter { it.section == section }

internal fun List<SettingsCategory>.startsNewGroup(index: Int): Boolean =
    index > 0 && this[index - 1].group != this[index].group
