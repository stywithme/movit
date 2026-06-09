package com.movit.feature.explore

object MovitExplorePreviewData {

    val featured: List<ExploreItemUi> = listOf(
        ExploreItemUi(
            id = "workout-lower-body",
            title = "Lower Body Strength",
            subtitle = "A balanced workout built from Squat, Lunges and mobility drills.",
            type = ExploreItemType.Workout,
            badge = "Smart pick",
            metadata = listOf("6 exercises", "~24 min", "Legs focus"),
        ),
    )

    val exercises: List<ExploreItemUi> = listOf(
        ExploreItemUi(
            id = "ex-squat",
            title = "Bodyweight Squat",
            subtitle = "Quads, glutes and core control with clear depth cues.",
            type = ExploreItemType.Exercise,
            badge = "Popular",
            metadata = listOf("Legs", "Beginner"),
        ),
        ExploreItemUi(
            id = "workout-core",
            title = "Core Crusher Express",
            subtitle = "Fast control session for abs, shoulders and trunk stability.",
            type = ExploreItemType.Workout,
            badge = "Short",
            metadata = listOf("5 exercises", "~18 min", "Core"),
        ),
        ExploreItemUi(
            id = "program-starter",
            title = "Starter Strength Plan",
            subtitle = "Four-week progression for full-body confidence.",
            type = ExploreItemType.Program,
            metadata = listOf("4 weeks", "3 days/week"),
        ),
        ExploreItemUi(
            id = "ex-lunge",
            title = "Reverse Lunge",
            subtitle = "Single-leg strength with balance and knee tracking focus.",
            type = ExploreItemType.Exercise,
            metadata = listOf("Legs", "Intermediate"),
        ),
        ExploreItemUi(
            id = "workout-full-body",
            title = "Full Body Strength",
            subtitle = "A complete session that mixes legs, push and core work.",
            type = ExploreItemType.Workout,
            badge = "Popular",
            metadata = listOf("8 exercises", "~35 min", "Strength"),
        ),
    )

    val workouts: List<ExploreItemUi> = exercises.filter { it.type == ExploreItemType.Workout }

    val exerciseOnly: List<ExploreItemUi> = exercises.filter { it.type == ExploreItemType.Exercise }

    val programs: List<ExploreItemUi> = exercises.filter { it.type == ExploreItemType.Program }

    val content: ExploreContent = ExploreContent(
        featured = featured,
        workouts = workouts,
        exercises = exerciseOnly,
        programs = programs,
    )

    /** Arabic sample block for RTL/catalog previews */
    val featuredAr: List<ExploreItemUi> = listOf(
        ExploreItemUi(
            id = "workout-ar-1",
            title = "قوة الجزء السفلي",
            subtitle = "جلسة متوازنة تجمع السكوات والاندفاع وتمارين الحركة.",
            type = ExploreItemType.Workout,
            badge = "مقترح",
            metadata = listOf("6 تمارين", "~24 دقيقة", "الأرجل"),
        ),
    )
}
