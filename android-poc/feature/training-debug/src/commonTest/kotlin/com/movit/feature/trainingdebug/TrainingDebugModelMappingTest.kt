package com.movit.feature.trainingdebug

import com.movit.core.posecapture.boundary.trainingdebug.PoseModelType
import kotlin.test.Test
import kotlin.test.assertEquals

class TrainingDebugModelMappingTest {
    @Test
    fun debugModelType_mapsToPoseModelTypePortValues() {
        assertEquals(PoseModelType.FULL, DebugPoseModelType.FULL.toPoseModelType())
        assertEquals(PoseModelType.HEAVY, DebugPoseModelType.HEAVY.toPoseModelType())
    }
}
