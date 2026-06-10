package com.movit.feature.library

import com.movit.core.model.ExploreContent
import com.movit.core.model.ExploreItemType
import com.movit.core.model.ExploreItemUi
import com.movit.designsystem.components.MovitTagVariant

internal object LibraryTestFixtures {
    val exercises: List<ExploreItemUi> = listOf(
        ExploreItemUi(
            id = "ex-squat",
            title = "Bodyweight Squat",
            subtitle = "Quads, glutes and core control with clear depth cues.",
            type = ExploreItemType.Exercise,
            badge = "Beginner safe",
            badgeVariant = MovitTagVariant.Lime,
            metadata = listOf("Lower body", "3 muscle groups"),
            tags = listOf("Quads", "Glutes", "Core"),
            categoryCode = "lower_body",
        ),
        ExploreItemUi(
            id = "ex-lunge",
            title = "Reverse Lunge",
            subtitle = "Single-leg strength with balance and knee tracking focus.",
            type = ExploreItemType.Exercise,
            badge = "Equipment",
            badgeVariant = MovitTagVariant.Coral,
            metadata = listOf("Legs", "Balance"),
            tags = listOf("Legs", "Balance", "Strength"),
            categoryCode = "lower_body",
        ),
        ExploreItemUi(
            id = "ex-plank",
            title = "Plank Hold",
            subtitle = "Core and shoulder stability with clear alignment cues.",
            type = ExploreItemType.Exercise,
            badge = "Core",
            badgeVariant = MovitTagVariant.Blue,
            metadata = listOf("Core", "2 muscle groups"),
            tags = listOf("Core", "Shoulders"),
            categoryCode = "core",
        ),
        ExploreItemUi(
            id = "ex-swings",
            title = "Leg Swings",
            subtitle = "Hip mobility warm-up for lower body sessions.",
            type = ExploreItemType.Exercise,
            badge = "Mobility",
            badgeVariant = MovitTagVariant.Blue,
            metadata = listOf("Mobility", "Warm-up"),
            tags = listOf("Hips", "Warm-up"),
            categoryCode = "mobility",
        ),
    )

    val workouts: List<ExploreItemUi> = listOf(
        ExploreItemUi(
            id = "workout-core",
            title = "Core Crusher Express",
            subtitle = "Fast control session for abs, shoulders and trunk stability.",
            type = ExploreItemType.Workout,
            badge = "Short",
            badgeVariant = MovitTagVariant.Blue,
            focusLabel = "Core focus",
            metadata = listOf("5 exercises", "~18 min", "Core"),
            levelNumber = 2,
            durationMinutes = 18,
        ),
        ExploreItemUi(
            id = "workout-full-body",
            title = "Full Body Strength",
            subtitle = "A complete session that mixes legs, push and stability work.",
            type = ExploreItemType.Workout,
            badge = "Popular",
            badgeVariant = MovitTagVariant.Lime,
            focusLabel = "Full body",
            metadata = listOf("8 exercises", "~35 min", "Strength"),
            levelNumber = 3,
            durationMinutes = 35,
        ),
    )

    fun sampleLibraryContent(kind: LibraryListKind): ExploreContent =
        when (kind) {
            LibraryListKind.Exercises -> ExploreContent(
                featured = emptyList(),
                workouts = emptyList(),
                exercises = exercises,
                programs = emptyList(),
            )
            LibraryListKind.Workouts -> ExploreContent(
                featured = emptyList(),
                workouts = workouts,
                exercises = emptyList(),
                programs = emptyList(),
            )
        }
}
