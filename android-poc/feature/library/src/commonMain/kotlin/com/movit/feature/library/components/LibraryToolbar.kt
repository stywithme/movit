package com.movit.feature.library.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitFilterRow
import com.movit.designsystem.components.MovitSearchBar
import com.movit.designsystem.movitColors

@Composable
fun LibraryToolbar(
    query: String,
    onQueryChange: (String) -> Unit,
    chips: List<String>,
    selectedChip: String,
    onChipSelected: (String) -> Unit,
    resultSummary: String,
    searchPlaceholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
    ) {
        MovitSearchBar(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = searchPlaceholder,
            enabled = enabled,
        )
        if (chips.isNotEmpty()) {
            MovitFilterRow(
                filters = chips,
                selectedFilter = selectedChip,
                onFilterSelected = onChipSelected,
                enabled = enabled,
            )
        }
        Text(
            text = resultSummary,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.movitColors.textSecondary,
            modifier = Modifier.padding(top = MovitSpacing.xs),
        )
    }
}
