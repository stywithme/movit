package com.movit.feature.explore.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.movit.designsystem.components.MovitFilterRow
import com.movit.feature.explore.ExploreWorkoutFilter
import com.movit.feature.explore.label

@Composable
fun ExploreMuscleStrip(
    filters: List<ExploreWorkoutFilter>,
    selectedFilter: ExploreWorkoutFilter,
    onFilterSelected: (ExploreWorkoutFilter) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val labels = filters.map { it.label() }
    val selectedLabel = selectedFilter.label()
    MovitFilterRow(
        filters = labels,
        selectedFilter = selectedLabel,
        onFilterSelected = { label ->
            val index = labels.indexOf(label)
            if (index >= 0) {
                onFilterSelected(filters[index])
            }
        },
        modifier = modifier,
        enabled = enabled,
    )
}
