package com.movit.feature.train.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitActionTile
import com.movit.designsystem.components.MovitSectionHeader
import com.movit.feature.train.TrainQuickActionUi

@Composable
fun TrainQuickActions(
    actions: List<TrainQuickActionUi>,
    onActionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
    ) {
        MovitSectionHeader(
            title = "Quick actions",
            subtitle = "Move around the training flow without leaving the shell.",
        )
        actions.forEach { action ->
            MovitActionTile(
                label = action.label,
                description = action.description,
                onClick = { onActionClick(action.id) },
            )
        }
    }
}
