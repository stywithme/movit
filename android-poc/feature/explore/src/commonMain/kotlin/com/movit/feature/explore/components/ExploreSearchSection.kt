package com.movit.feature.explore.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.movit.designsystem.components.MovitSearchBar

@Composable
fun ExploreSearchSection(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    MovitSearchBar(
        query = query,
        onQueryChange = onQueryChange,
        modifier = modifier,
        placeholder = "Search workouts or exercises…",
        enabled = enabled,
    )
}
