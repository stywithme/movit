package com.movit.feature.account

import com.movit.core.training.model.JointAngles
import com.movit.core.training.model.Landmark
import com.movit.core.training.model.PoseFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AssessmentBodyScanEngineTest {

    @Test
    fun ingestFrames_completesTemplateAndBuildsUploadPayload() {
        val engine = AssessmentBodyScanEngine(
            template = AssessmentDefaults.initialTemplate,
            samplesPerMovement = 4,
        )

        repeat(AssessmentDefaults.initialTemplate.movements.size * 4) { index ->
            val update = engine.ingest(frame(timestamp = index.toLong()))
            assertTrue(update.progressPercent in 0..100)
        }

        assertTrue(engine.isComplete)
        val result = engine.buildResult(parqPassed = true, parqFlags = emptyList())

        assertTrue(result.uiResults.bodyScore > 50)
        assertEquals(AssessmentDefaults.initialTemplate.movements.size, result.uploadRequest.movementCount)
        assertTrue(result.uploadRequest.regions.isNotEmpty())
    }

    private fun frame(timestamp: Long): PoseFrame = PoseFrame(
        angles = JointAngles(
            leftShoulder = 152.0,
            rightShoulder = 150.0,
            leftHip = 108.0,
            rightHip = 106.0,
            leftKnee = 134.0,
            rightKnee = 132.0,
            leftAnkle = 92.0,
            rightAnkle = 90.0,
        ),
        landmarks = List(33) {
            Landmark(x = 0.5f, y = 0.5f, z = 0f, visibility = 0.94f, presence = 0.96f)
        },
        isFrontCamera = true,
        timestampMs = timestamp,
    )
}
