package com.movit.feature.training

import com.movit.core.training.session.TrainingFlowItem
import kotlin.test.Test
import kotlin.test.assertEquals

class TrainingPoseVariantResolverTest {
    @Test
    fun resolve_usesRouteIndexWhenNoFlowItem() {
        assertEquals(
            2,
            TrainingPoseVariantResolver.resolve(
                routePoseVariantIndex = 2,
                flowExercise = null,
                variantCount = 4,
            ),
        )
    }

    @Test
    fun resolve_prefersFlowExerciseIndex() {
        val exercise = TrainingFlowItem.Exercise(
            slug = "curl",
            displayName = "Curl",
            poseVariantIndex = 1,
        )
        assertEquals(
            1,
            TrainingPoseVariantResolver.resolve(
                routePoseVariantIndex = 0,
                flowExercise = exercise,
                variantCount = 3,
            ),
        )
    }

    @Test
    fun resolve_clampsToLastVariant() {
        assertEquals(
            2,
            TrainingPoseVariantResolver.resolve(
                routePoseVariantIndex = 9,
                flowExercise = null,
                variantCount = 3,
            ),
        )
    }

    @Test
    fun resolve_returnsZeroWhenNoVariants() {
        assertEquals(
            0,
            TrainingPoseVariantResolver.resolve(
                routePoseVariantIndex = 2,
                flowExercise = null,
                variantCount = 0,
            ),
        )
    }
}
