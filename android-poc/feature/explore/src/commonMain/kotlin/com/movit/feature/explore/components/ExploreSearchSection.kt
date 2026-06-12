package com.movit.feature.explore.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitSearchBar
import com.movit.resources.movitText

@Composable
fun ExploreSearchSection(
    query: String,
    onQueryChange: (String) -> Unit,
    onFilterClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filtersActive: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MovitSearchBar(
            query = query,
            onQueryChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = movitText("explore_search_placeholder"),
            enabled = enabled,
        )
        IconButton(
            onClick = onFilterClick,
            enabled = enabled,
            modifier = Modifier.padding(start = MovitSpacing.xs),
        ) {
            Icon(
                imageVector = Icons.Default.FilterList,
                contentDescription = movitText("explore_a11y_filter"),
                tint = if (filtersActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
