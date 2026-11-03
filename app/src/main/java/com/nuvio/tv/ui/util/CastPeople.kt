package com.nuvio.tv.ui.util

import com.nuvio.tv.domain.model.MetaCastMember

/**
 * Each person once, in the order given. Credits list the same person again for each role
 * (creator, writer, actor), so they're matched by TMDB id, or by name when there's no id.
 */
fun List<MetaCastMember>.distinctPeople(): List<MetaCastMember> =
    distinctBy { member -> member.tmdbId?.let { "id:$it" } ?: "name:${member.name.trim().lowercase()}" }

private val CREW_ROLES = setOf("creator", "director", "writer", "producer", "executive producer", "screenplay")

/** Only the actors: crew credits (creator, director, writer...) are left out. */
fun List<MetaCastMember>.actorsOnly(): List<MetaCastMember> =
    filterNot { member -> member.character?.trim()?.lowercase() in CREW_ROLES }
