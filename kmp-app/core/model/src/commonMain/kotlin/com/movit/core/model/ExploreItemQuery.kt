package com.movit.core.model

fun ExploreItemUi.matchesExploreQuery(query: String): Boolean {
    if (query.isBlank()) return true
    val needle = query.trim().lowercase()
    return title.lowercase().contains(needle) ||
        subtitle.lowercase().contains(needle) ||
        metadata.any { it.lowercase().contains(needle) } ||
        tags.any { it.lowercase().contains(needle) }
}
