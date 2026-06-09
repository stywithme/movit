package com.movit.feature.train.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitButton
import com.movit.designsystem.components.MovitButtonVariant
import com.movit.designsystem.components.MovitMediaCard
import com.movit.feature.train.TrainFeaturedProgramUi
import com.movit.resources.movitText

@Composable
fun TrainNoPlanSection(
    programs: List<TrainFeaturedProgramUi>,
    onExplorePrograms: () -> Unit,
    onStartProgram: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MovitSpacing.md),
    ) {
        if (programs.isEmpty()) {
            MovitMediaCard(
                title = movitText("train_browse_programs"),
                subtitle = movitText("train_browse_card_sub"),
                badge = movitText("train_featured"),
                metadata = movitText("train_browse_metadata").split(" · "),
                onClick = onExplorePrograms,
            )
            MovitButton(
                text = movitText("train_start_program"),
                onClick = onExplorePrograms,
                variant = MovitButtonVariant.Filled,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            programs.forEach { program ->
                TrainFeaturedProgramCard(
                    program = program,
                    onStartProgram = { onStartProgram(program.id) },
                )
            }
        }
    }
}
