package com.movit.feature.train.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitMediaCard
import com.movit.designsystem.components.MovitSectionHeader
import com.movit.resources.movitText

@Composable
fun TrainNoPlanSection(
    programs: List<com.movit.feature.train.TrainFeaturedProgramUi>,
    onExplorePrograms: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MovitSpacing.md),
    ) {
        MovitSectionHeader(
            title = movitText("train_featured_programs"),
            subtitle = movitText("train_featured_subtitle"),
        )
        if (programs.isEmpty()) {
            MovitMediaCard(
                title = movitText("train_browse_programs"),
                subtitle = movitText("train_browse_card_sub"),
                badge = movitText("train_featured"),
                metadata = movitText("train_browse_metadata").split(" · "),
                onClick = onExplorePrograms,
            )
        } else {
            programs.forEach { program ->
                MovitMediaCard(
                    title = program.title,
                    subtitle = program.subtitle,
                    badge = program.badge,
                    metadata = program.metadata,
                    onClick = onExplorePrograms,
                )
            }
        }
    }
}
