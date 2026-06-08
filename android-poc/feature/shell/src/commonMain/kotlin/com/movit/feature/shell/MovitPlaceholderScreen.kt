package com.movit.feature.shell

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.movit.designsystem.components.MovitPlaceholderState
import com.movit.designsystem.components.MovitScaffold

@Composable
fun MovitPlaceholderScreen(
    destination: MovitAppDestination,
    modifier: Modifier = Modifier,
) {
    MovitScaffold(
        modifier = modifier,
        title = destination.placeholderTitle,
        subtitle = destination.placeholderSubtitle,
    ) { padding ->
        MovitPlaceholderState(
            title = destination.placeholderTitle,
            subtitle = destination.placeholderSubtitle,
            statusLabel = "Coming soon",
            actionLabel = "Notify me",
            onActionClick = null,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        )
    }
}
