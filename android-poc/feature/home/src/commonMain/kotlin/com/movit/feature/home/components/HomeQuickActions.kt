package com.movit.feature.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitActionTile
import com.movit.designsystem.components.MovitSectionHeader
import com.movit.feature.home.HomeQuickActionUi

@Composable
fun HomeQuickActions(
    actions: List<HomeQuickActionUi>,
    onActionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (actions.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
    ) {
        MovitSectionHeader(
            title = "Quick actions",
            subtitle = "Shortcuts",
        )
        Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm)) {
            actions.forEach { action ->
                MovitActionTile(
                    label = action.label,
                    description = action.description,
                    onClick = { onActionClick(action.id) },
                )
            }
        }
    }
}
