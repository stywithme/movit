package com.movit.feature.explore.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.movit.designsystem.components.MovitFilterRow
import com.movit.feature.explore.ExploreFilter
import com.movit.resources.movitText

@Composable
fun ExploreFilterSection(
    filters: List<ExploreFilter>,
    selectedFilter: ExploreFilter,
    onFilterSelected: (ExploreFilter) -> Unit,
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

@Composable
fun ExploreFilter.label(): String = when (this) {
    ExploreFilter.All -> movitText("explore_filter_all")
    ExploreFilter.Exercises -> movitText("explore_filter_exercises")
    ExploreFilter.Workouts -> movitText("explore_filter_workouts")
    ExploreFilter.Programs -> movitText("explore_filter_programs")
}
