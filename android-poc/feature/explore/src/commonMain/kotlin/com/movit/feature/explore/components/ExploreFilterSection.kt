package com.movit.feature.explore.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.movit.designsystem.components.MovitFilterRow
import com.movit.feature.explore.ExploreFilter

@Composable
fun ExploreFilterSection(
    filters: List<ExploreFilter>,
    selectedFilter: ExploreFilter,
    onFilterSelected: (ExploreFilter) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    MovitFilterRow(
        filters = filters.map { it.label },
        selectedFilter = selectedFilter.label,
        onFilterSelected = { label ->
            filters.firstOrNull { it.label == label }?.let(onFilterSelected)
        },
        modifier = modifier,
        enabled = enabled,
    )
}
