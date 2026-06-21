package com.movit.feature.train.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitIconBoxVariant
import com.movit.designsystem.components.MovitListGroup
import com.movit.designsystem.components.MovitListRow
import com.movit.designsystem.components.MovitSectionHeader
import com.movit.feature.train.TrainQuickActionUi
import com.movit.resources.movitText

@Composable
fun TrainQuickActions(
    actions: List<TrainQuickActionUi>,
    onActionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MovitSpacing.md),
    ) {
        MovitSectionHeader(
            title = movitText("home_quick_actions"),
            subtitle = movitText("train_quick_actions_sub"),
        )
        MovitListGroup(
            rows = actions.map { action ->
                {
                    TrainQuickActionRow(
                        action = action,
                        onClick = { onActionClick(action.id) },
                    )
                }
            },
        )
    }
}

private fun actionIcon(id: String): ImageVector = when (id) {
    "reports" -> Icons.Default.Assessment
    "preferences" -> Icons.Default.Tune
    else -> Icons.Default.Explore
}

private fun actionIconVariant(id: String): MovitIconBoxVariant = when (id) {
    "reports" -> MovitIconBoxVariant.Lime
    "preferences" -> MovitIconBoxVariant.Coral
    else -> MovitIconBoxVariant.Primary
}

@Composable
private fun TrainQuickActionRow(
    action: TrainQuickActionUi,
    onClick: () -> Unit,
) {
    val a11yLabel = when (action.id) {
        "reports" -> movitText("home_a11y_reports_action")
        "preferences" -> movitText("train_a11y_preferences")
        else -> movitText("home_a11y_explore_action")
    }
    MovitListRow(
        title = action.label,
        subtitle = action.description,
        icon = actionIcon(action.id),
        iconVariant = actionIconVariant(action.id),
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = a11yLabel },
    )
}
