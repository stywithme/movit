package com.movit.feature.library.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitFilterChip
import com.movit.designsystem.components.MovitFilterRow
import com.movit.designsystem.components.MovitSearchBar
import com.movit.designsystem.movitColors
import com.movit.feature.library.LibraryFilterChip

@Composable
fun LibraryToolbar(
    query: String,
    onQueryChange: (String) -> Unit,
    filters: List<LibraryFilterChip>,
    selectedFilter: LibraryFilterChip,
    onFilterSelected: (LibraryFilterChip) -> Unit,
    resultSummary: String,
    searchPlaceholder: String,
    filterContentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onFilterClick: (() -> Unit)? = null,
    filterSheetVisible: Boolean = false,
    onDismissFilterSheet: () -> Unit = {},
    filterSheetTitle: String = "",
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MovitSearchBar(
                query = query,
                onQueryChange = onQueryChange,
                placeholder = searchPlaceholder,
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
            if (onFilterClick != null) {
                IconButton(
                    onClick = onFilterClick,
                    enabled = enabled,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FilterList,
                        contentDescription = filterContentDescription,
                    )
                }
            }
        }
        if (filters.isNotEmpty()) {
            LibraryFilterStrip(
                filters = filters,
                selectedFilter = selectedFilter,
                onFilterSelected = onFilterSelected,
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

    if (filterSheetVisible) {
        LibraryFilterSheet(
            title = filterSheetTitle,
            filters = filters,
            selectedFilter = selectedFilter,
            onFilterSelected = onFilterSelected,
            onDismiss = onDismissFilterSheet,
        )
    }
}

@Composable
private fun LibraryFilterStrip(
    filters: List<LibraryFilterChip>,
    selectedFilter: LibraryFilterChip,
    onFilterSelected: (LibraryFilterChip) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryFilterSheet(
    title: String,
    filters: List<LibraryFilterChip>,
    selectedFilter: LibraryFilterChip,
    onFilterSelected: (LibraryFilterChip) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MovitSpacing.lg)
                .padding(bottom = MovitSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            filters.forEach { filter ->
                MovitFilterChip(
                    label = filter.label(),
                    selected = filter == selectedFilter,
                    onClick = { onFilterSelected(filter) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
