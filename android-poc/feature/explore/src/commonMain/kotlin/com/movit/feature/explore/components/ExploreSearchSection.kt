package com.movit.feature.explore.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.movit.designsystem.components.MovitSearchBar
import com.movit.resources.movitText

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
        placeholder = movitText("explore_search_placeholder"),
        enabled = enabled,
    )
}
