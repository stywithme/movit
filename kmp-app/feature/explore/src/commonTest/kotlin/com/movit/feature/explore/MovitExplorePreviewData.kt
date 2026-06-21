package com.movit.feature.explore

import com.movit.core.model.ExploreContent
import com.movit.core.model.ExploreItemType
import com.movit.core.model.ExploreItemUi
import com.movit.designsystem.components.MovitTagVariant

object MovitExplorePreviewData {

    private const val IMG_SQUAT =
        "https://images.unsplash.com/photo-1518611012118-696072aa579a?auto=format&fit=crop&w=400&q=70"
    private const val IMG_LEGS =
        "https://images.unsplash.com/photo-1574680096145-d05b474e2155?auto=format&fit=crop&w=320&q=70"
    private const val IMG_CORE =
        "https://images.unsplash.com/photo-1598971639058-fab3c3109a00?auto=format&fit=crop&w=320&q=70"
    private const val IMG_FULL =
        "https://images.unsplash.com/photo-1534438327276-14e5300c3a48?auto=format&fit=crop&w=320&q=70"
    private const val IMG_LUNGE =
        "https://images.unsplash.com/photo-1538805060514-97d9cc17730c?auto=format&fit=crop&w=400&q=70"
    private const val IMG_MOBILITY =
        "https://images.unsplash.com/photo-1544367567-0f2fcb009e0b?auto=format&fit=crop&w=400&q=70"

    val featured: List<ExploreItemUi> = listOf(
        ExploreItemUi(
            id = "workout-lower-body",
            title = "Lower Body Strength",
            subtitle = "A balanced workout built from Squat, Lunges and mobility drills.",
            type = ExploreItemType.Workout,
            imageUrl = IMG_SQUAT,
            badge = "Smart pick",
            badgeVariant = MovitTagVariant.Lime,
            focusLabel = "Legs focus",
            metadata = listOf("6 exercises", "~24 min", "Legs focus"),
            levelNumber = 1,
            durationMinutes = 24,
        ),
    )

    val exercises: List<ExploreItemUi> = listOf(
        ExploreItemUi(
            id = "ex-squat",
            title = "Bodyweight Squat",
            subtitle = "Quads, glutes and core control with clear depth cues.",
            type = ExploreItemType.Exercise,
            imageUrl = IMG_SQUAT,
            badge = "Beginner safe",
            badgeVariant = MovitTagVariant.Lime,
            metadata = listOf("Lower body", "3 muscle groups"),
            tags = listOf("Quads", "Glutes", "Core"),
            categoryCode = "lower_body",
        ),
        ExploreItemUi(
            id = "workout-core",
            title = "Core Crusher Express",
            subtitle = "Fast control session for abs, shoulders and trunk stability.",
            type = ExploreItemType.Workout,
            imageUrl = IMG_CORE,
            badge = "Short",
            badgeVariant = MovitTagVariant.Blue,
            focusLabel = "Core focus",
            metadata = listOf("5 exercises", "~18 min", "Core"),
            levelNumber = 2,
            durationMinutes = 18,
        ),
        ExploreItemUi(
            id = "program-starter",
            title = "Starter Strength Plan",
            subtitle = "Four-week progression for full-body confidence.",
            type = ExploreItemType.Program,
            imageUrl = IMG_MOBILITY,
            metadata = listOf("4 weeks", "3 days/week"),
        ),
        ExploreItemUi(
            id = "ex-lunge",
            title = "Reverse Lunge",
            subtitle = "Single-leg strength with balance and knee tracking focus.",
            type = ExploreItemType.Exercise,
            imageUrl = IMG_LUNGE,
            badge = "Equipment",
            badgeVariant = MovitTagVariant.Coral,
            metadata = listOf("Legs", "Balance"),
            tags = listOf("Legs", "Balance", "Strength"),
            categoryCode = "lower_body",
        ),
        ExploreItemUi(
            id = "workout-full-body",
            title = "Full Body Strength",
            subtitle = "A complete session that mixes legs, push and stability work.",
            type = ExploreItemType.Workout,
            imageUrl = IMG_FULL,
            badge = "Popular",
            badgeVariant = MovitTagVariant.Lime,
            focusLabel = "Full body",
            metadata = listOf("8 exercises", "~35 min", "Strength"),
            levelNumber = 3,
            durationMinutes = 35,
        ),
        ExploreItemUi(
            id = "ex-plank",
            title = "Plank Hold",
            subtitle = "Core and shoulder stability with clear alignment cues.",
            type = ExploreItemType.Exercise,
            imageUrl = IMG_CORE,
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
            imageUrl = IMG_MOBILITY,
            badge = "Mobility",
            badgeVariant = MovitTagVariant.Blue,
            metadata = listOf("Mobility", "Warm-up"),
            tags = listOf("Hips", "Warm-up"),
            categoryCode = "mobility",
        ),
    )

    val workouts: List<ExploreItemUi> = listOf(
        ExploreItemUi(
            id = "workout-lower-body",
            title = "Lower Body Strength",
            subtitle = "Quads, glutes and hamstrings in one clear guided session.",
            type = ExploreItemType.Workout,
            imageUrl = IMG_LEGS,
            badge = "Beginner",
            badgeVariant = MovitTagVariant.Lime,
            focusLabel = "Best for legs",
            metadata = listOf("6 exercises", "~24 min", "Legs focus"),
            levelNumber = 1,
            durationMinutes = 24,
        ),
        exercises[1],
        exercises[4],
    )

    val exerciseOnly: List<ExploreItemUi> = exercises.filter { it.type == ExploreItemType.Exercise }

    val programs: List<ExploreItemUi> = exercises.filter { it.type == ExploreItemType.Program }

    val content: ExploreContent = ExploreContent(
        featured = featured,
        workouts = workouts,
        exercises = exerciseOnly,
        programs = programs,
    )
}
