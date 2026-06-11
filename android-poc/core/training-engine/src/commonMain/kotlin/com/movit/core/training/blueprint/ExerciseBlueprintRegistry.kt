package com.movit.core.training.blueprint

import com.movit.core.training.engine.CountingMethod

/**
 * POC exercise configs until training-config JSON is parsed in commonMain.
 * Squat knee ranges approximate legacy bodyweight-squat side view.
 */
object ExerciseBlueprintRegistry {
    private val squatKnee = BlueprintJointConfig(
        jointCode = "left_knee",
        upMin = 155.0,
        upMax = 180.0,
        downMin = 70.0,
        downMax = 105.0,
    )

    private val squatKneeRight = BlueprintJointConfig(
        jointCode = "right_knee",
        upMin = 155.0,
        upMax = 180.0,
        downMin = 70.0,
        downMax = 105.0,
    )

    private val bodyweightSquat = ExerciseBlueprint(
        slug = "bodyweight-squat",
        displayName = "Bodyweight Squat",
        countingMethod = CountingMethod.UP_DOWN,
        primaryJoints = listOf(squatKnee, squatKneeRight),
        defaultTargetReps = 12,
    )

    private val barbellSquat = bodyweightSquat.copy(
        slug = "barbell-squat",
        displayName = "Barbell Squat",
    )

    private val bySlug = mapOf(
        bodyweightSquat.slug to bodyweightSquat,
        barbellSquat.slug to barbellSquat,
    )

    fun resolve(slug: String): ExerciseBlueprint? = bySlug[slug]

    fun supportedSlugs(): Set<String> = bySlug.keys
}
