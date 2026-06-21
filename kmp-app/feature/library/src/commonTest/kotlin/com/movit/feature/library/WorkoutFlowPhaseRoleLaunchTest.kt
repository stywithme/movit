package com.movit.feature.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkoutFlowPhaseRoleLaunchTest {
    @Test
    fun toTrainingFlowItems_preservesPhaseRoleFromSessionBlocks() {
        val config = WorkoutFlowMapper.fromSession(WorkoutSessionPreviewData.preview)
        val items = config.toTrainingFlowItems()
        val exercises = items.filterIsInstance<com.movit.core.training.session.TrainingFlowItem.Exercise>()
        assertEquals(6, exercises.size)
        assertTrue(exercises.take(2).all { it.phaseRole == "WARMUP" })
        assertTrue(exercises.drop(2).take(3).all { it.phaseRole == "MAIN" })
        assertEquals("COOLDOWN", exercises.last().phaseRole)
    }
}
