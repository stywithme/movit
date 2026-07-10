package com.movit.feature.training

import com.movit.core.training.session.TrainingFlowItem
import com.movit.core.training.session.TrainingSessionFlowCoordinator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrainingFlowEngineOverridesTest {
    @Test
    fun durationAndWeight_reachOverrideHelpers() {
        val item = TrainingFlowItem.Exercise(
            slug = "plank",
            displayName = "Plank",
            targetReps = 0,
            targetDurationSeconds = 40,
            weightPerSetKg = listOf(10f, 12.5f, 15f),
        )
        assertEquals(40, TrainingFlowEngineOverrides.targetDurationSeconds(item))
        assertEquals(10f, TrainingFlowEngineOverrides.weightKgForSet(item, setNumber = 1))
        assertEquals(12.5f, TrainingFlowEngineOverrides.weightKgForSet(item, setNumber = 2))
        assertEquals(15f, TrainingFlowEngineOverrides.weightKgForSet(item, setNumber = 3))
        // Beyond list → last weight
        assertEquals(15f, TrainingFlowEngineOverrides.weightKgForSet(item, setNumber = 9))
    }

    @Test
    fun blankOverrides_areNull() {
        val item = TrainingFlowItem.Exercise(
            slug = "squat",
            displayName = "Squat",
            targetReps = 10,
            targetDurationSeconds = 0,
            weightPerSetKg = emptyList(),
        )
        assertNull(TrainingFlowEngineOverrides.targetDurationSeconds(item))
        assertNull(TrainingFlowEngineOverrides.weightKgForSet(item, setNumber = 1))
        assertNull(TrainingFlowEngineOverrides.targetDurationSeconds(null))
        assertNull(TrainingFlowEngineOverrides.weightKgForSet(null, setNumber = 1))
    }

    @Test
    fun restPreview_usesUpcomingOneBasedSetNumber() {
        val item = TrainingFlowItem.Exercise(
            slug = "squat",
            displayName = "Squat",
            targetReps = 10,
            weightPerSetKg = listOf(20f, 25f, 30f),
        )
        assertEquals(
            25f,
            TrainingFlowEngineOverrides.weightKgForRestPreview(
                exercise = item,
                upcomingSetNumber = 2,
                restContext = TrainingSessionFlowCoordinator.RestContext.BETWEEN_SETS,
            ),
        )
        assertEquals(
            20f,
            TrainingFlowEngineOverrides.weightKgForRestPreview(
                exercise = item,
                upcomingSetNumber = 3,
                restContext = TrainingSessionFlowCoordinator.RestContext.BETWEEN_EXERCISES,
            ),
        )
    }
}
