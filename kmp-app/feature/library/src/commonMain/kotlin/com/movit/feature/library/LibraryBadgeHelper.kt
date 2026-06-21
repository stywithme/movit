package com.movit.feature.library

import com.movit.designsystem.components.MovitTagVariant
import com.movit.core.model.ExploreItemUi

data class LibraryBadge(
    val text: String,
    val variant: MovitTagVariant,
)

fun ExploreItemUi.resolveLibraryBadge(
    featured: Boolean = false,
    featuredLabel: String? = null,
): LibraryBadge? {
    if (featured && featuredLabel != null) {
        return LibraryBadge(featuredLabel, MovitTagVariant.Lime)
    }
    badge?.let { return LibraryBadge(it, badgeVariant ?: MovitTagVariant.Blue) }
    metadata.firstOrNull()?.let { meta ->
        return LibraryBadge(meta, badgeVariant ?: MovitTagVariant.Blue)
    }
    return null
}
