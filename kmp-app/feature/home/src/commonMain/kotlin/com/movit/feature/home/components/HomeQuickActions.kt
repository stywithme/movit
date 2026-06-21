package com.movit.feature.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitCard
import com.movit.designsystem.components.MovitCardVariant
import com.movit.designsystem.components.MovitIconBox
import com.movit.designsystem.components.MovitIconBoxVariant
import com.movit.designsystem.components.MovitSectionHeader
import com.movit.feature.home.HomeQuickActionUi
import com.movit.resources.movitText

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
        MovitSectionHeader(title = movitText("home_quick_actions"))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
        ) {
            actions.take(2).forEach { action ->
                HomeQuickActionTile(
                    action = action,
                    onClick = { onActionClick(action.id) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HomeQuickActionTile(
    action: HomeQuickActionUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val a11yLabel = when (action.id) {
        "explore" -> movitText("home_a11y_explore_action")
        "reports" -> movitText("home_a11y_reports_action")
        else -> action.label
    }
    MovitCard(
        modifier = modifier.semantics { contentDescription = a11yLabel },
        variant = MovitCardVariant.Outlined,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.padding(MovitSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
        ) {
            MovitIconBox(
                icon = quickActionIcon(action.id),
                variant = quickActionIconVariant(action.id),
                contentDescription = a11yLabel,
            )
            Text(
                text = action.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.W700,
            )
        }
    }
}

private fun quickActionIcon(id: String): ImageVector = when (id) {
    "reports" -> Icons.Default.Assessment
    else -> Icons.Default.Explore
}

private fun quickActionIconVariant(id: String): MovitIconBoxVariant = when (id) {
    "reports" -> MovitIconBoxVariant.Lime
    else -> MovitIconBoxVariant.Primary
}
