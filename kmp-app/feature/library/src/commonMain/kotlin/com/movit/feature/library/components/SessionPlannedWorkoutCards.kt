package com.movit.feature.library.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitCard
import com.movit.designsystem.components.MovitCardVariant
import com.movit.designsystem.components.MovitSectionHeader
import com.movit.designsystem.components.MovitTag
import com.movit.designsystem.components.MovitTagVariant
import com.movit.designsystem.movitColors
import com.movit.feature.library.PlannedWorkoutCardUi
import com.movit.resources.movitText

@Composable
fun SessionPlannedWorkoutCards(
    cards: List<PlannedWorkoutCardUi>,
    expandedWorkoutId: String?,
    onToggleExpand: (String) -> Unit,
    onSelectWorkout: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (cards.size <= 1) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
    ) {
        MovitSectionHeader(title = movitText("session_planned_workouts"))
        cards.forEach { card ->
            val expanded = expandedWorkoutId == card.id
            MovitCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
                    .clickable {
                        if (card.isSelected) {
                            onToggleExpand(card.id)
                        } else {
                            onSelectWorkout(card.id)
                        }
                    },
                variant = if (card.isSelected) MovitCardVariant.Elevated else MovitCardVariant.Outlined,
                contentPadding = MovitSpacing.md,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(MovitSpacing.xs),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = card.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.W800,
                            )
                            if (card.isSelected) {
                                MovitTag(text = movitText("workout_flow_now"), variant = MovitTagVariant.Lime)
                            }
                        }
                        Text(
                            text = movitText(
                                "session_planned_workout_meta",
                                card.exerciseCount,
                                card.durationLabel,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.movitColors.textSecondary,
                        )
                    }
                    if (card.isSelected) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.movitColors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}
