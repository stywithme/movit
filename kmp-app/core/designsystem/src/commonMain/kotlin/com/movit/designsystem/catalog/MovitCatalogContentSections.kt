package com.movit.designsystem.catalog

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitButton
import com.movit.designsystem.components.MovitCoachCard
import com.movit.designsystem.components.MovitDifficultyDots
import com.movit.designsystem.components.MovitExerciseCard
import com.movit.designsystem.components.MovitMacroCaloriesCard
import com.movit.designsystem.components.MovitMacroNutrientUi
import com.movit.designsystem.components.MovitProgramCard
import com.movit.designsystem.components.MovitWorkoutScrollCard
import com.movit.resources.movitText

@Composable
fun MovitCatalogMacroSection() {
    CatalogSection(
        eyebrow = movitText("catalog_eyebrow_progress"),
        title = movitText("catalog_macro_title"),
        subtitle = movitText("catalog_macro_subtitle"),
    ) {
        MovitMacroCaloriesCard(
            title = movitText("catalog_macro_calories_title"),
            subtitle = movitText("catalog_macro_calories_subtitle"),
            currentCalories = "2,040",
            targetCalories = "2,350",
            barHeights = listOf(0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f),
            nutrients = listOf(
                MovitMacroNutrientUi("269g", movitText("catalog_macro_carbs")),
                MovitMacroNutrientUi("164g", movitText("catalog_macro_proteins")),
                MovitMacroNutrientUi("110g", movitText("catalog_macro_fats")),
            ),
        )
    }
}

@Composable
fun MovitCatalogCoachSection() {
    CatalogSection(
        eyebrow = movitText("catalog_eyebrow_lists"),
        title = movitText("catalog_coach_title"),
    ) {
        MovitCoachCard(
            name = movitText("catalog_coach_name"),
            subtitle = movitText("catalog_coach_subtitle"),
            onMessage = {},
            onCall = {},
        )
    }
}

@Composable
fun MovitCatalogProgramCardSection() {
    CatalogSection(
        eyebrow = movitText("catalog_eyebrow_content"),
        title = movitText("catalog_program_card_title"),
    ) {
        MovitProgramCard(
            title = movitText("catalog_program_card_name"),
            description = movitText("catalog_program_card_desc"),
            badge = movitText("catalog_program_card_badge"),
            durationLabel = movitText("catalog_program_card_weeks"),
            levelLabel = movitText("catalog_program_card_level"),
            metadata = listOf(
                movitText("catalog_program_card_sessions"),
                movitText("catalog_program_card_frequency"),
            ),
            onClick = {},
        )
        MovitButton(
            text = movitText("catalog_program_card_cta"),
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MovitSpacing.md),
        )
    }
}

@Composable
fun MovitCatalogWorkoutCardsSection() {
    CatalogSection(
        eyebrow = movitText("catalog_eyebrow_content"),
        title = movitText("catalog_workout_cards_title"),
        actionLabel = movitText("catalog_see_all"),
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(MovitSpacing.md),
        ) {
            MovitWorkoutScrollCard(
                title = movitText("catalog_workout_ai_title"),
                metadata = movitText("catalog_workout_ai_meta"),
                badge = movitText("catalog_workout_ai_badge"),
                durationLabel = movitText("catalog_workout_ai_duration"),
                actionLabel = movitText("catalog_workout_start"),
                onAction = {},
            )
            MovitWorkoutScrollCard(
                title = movitText("catalog_workout_hiit_title"),
                metadata = movitText("catalog_workout_hiit_meta"),
                durationLabel = movitText("catalog_workout_hiit_duration"),
                levelLabel = movitText("catalog_workout_hiit_level"),
                actionLabel = movitText("catalog_workout_start"),
                onAction = {},
            )
        }
    }
}

@Composable
fun MovitCatalogDifficultyDotsSection() {
    CatalogSection(
        eyebrow = movitText("catalog_eyebrow_content"),
        title = movitText("catalog_exercise_cards_title"),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(MovitSpacing.md)) {
            Column(modifier = Modifier.weight(1f)) {
                MovitExerciseCard(
                    title = movitText("catalog_exercise_squats"),
                    subtitle = movitText("catalog_exercise_squats_meta"),
                    onClick = {},
                )
                MovitDifficultyDots(
                    filledDots = 2,
                    label = movitText("catalog_difficulty_medium"),
                    modifier = Modifier.padding(top = MovitSpacing.sm),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                MovitExerciseCard(
                    title = movitText("catalog_exercise_pushup"),
                    subtitle = movitText("catalog_exercise_pushup_meta"),
                    onClick = {},
                )
                MovitDifficultyDots(
                    filledDots = 1,
                    label = movitText("catalog_difficulty_easy"),
                    modifier = Modifier.padding(top = MovitSpacing.sm),
                )
            }
        }
    }
}
