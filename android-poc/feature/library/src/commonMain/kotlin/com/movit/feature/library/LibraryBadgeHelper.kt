package com.movit.feature.library

import com.movit.designsystem.components.MovitTagVariant
import com.movit.feature.explore.ExploreItemUi

data class LibraryBadge(
    val text: String,
    val variant: MovitTagVariant,
)

fun ExploreItemUi.resolveLibraryBadge(featured: Boolean = false): LibraryBadge? {
    badge?.let { return LibraryBadge(it, variantForBadge(it)) }
    if (featured) {
        return LibraryBadge(text = "Featured", variant = MovitTagVariant.Lime)
    }
    metadata.firstOrNull()?.let { meta ->
        return LibraryBadge(meta, variantForBadge(meta))
    }
    return null
}

private fun variantForBadge(text: String): MovitTagVariant {
    val normalized = text.lowercase()
    return when {
        normalized.contains("beginner") || normalized.contains("safe") || normalized.contains("popular") ->
            MovitTagVariant.Lime
        normalized.contains("equipment") || normalized.contains("weight") ->
            MovitTagVariant.Coral
        normalized.contains("short") || normalized.contains("easy") ->
            MovitTagVariant.Blue
        else -> MovitTagVariant.Blue
    }
}
