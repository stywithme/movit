package com.movit.designsystem.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.movit.designsystem.MovitSpacing

@Composable
fun MovitFilterRow(
    filters: List<String>,
    selectedFilter: String,
    onFilterSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
    ) {
        filters.forEach { filter ->
            MovitFilterChip(
                label = filter,
                selected = filter == selectedFilter,
                onClick = { onFilterSelected(filter) },
                enabled = enabled,
            )
        }
    }
}
