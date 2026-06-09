package com.movit.feature.explore.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.movit.designsystem.components.MovitFilterRow
import com.movit.feature.explore.ExploreCategoryChip
import com.movit.resources.movitText

@Composable
fun ExploreExerciseChips(
    chips: List<ExploreCategoryChip>,
    selectedCategoryCode: String?,
    onCategorySelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (chips.isEmpty()) return
    val allLabel = movitText("explore_filter_all")
    val labels = chips.map { chip ->
        if (chip.code == null) allLabel else chip.label.ifBlank { chip.code.orEmpty() }
    }
    val selectedLabel = chips.firstOrNull { it.code == selectedCategoryCode }?.let { chip ->
        if (chip.code == null) allLabel else chip.label.ifBlank { chip.code.orEmpty() }
    } ?: allLabel
    MovitFilterRow(
        filters = labels,
        selectedFilter = selectedLabel,
        onFilterSelected = { label ->
            val index = labels.indexOf(label)
            if (index >= 0) {
                onCategorySelected(chips[index].code)
            }
        },
        modifier = modifier,
        enabled = enabled,
    )
}
