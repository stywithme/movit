package com.movit.core.training.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * WP-23 acceptance: `PoseFrame.mirrored()` swaps L/R **angles**, so every joint-keyed
 * side-channel must make the same hop. If confidence stayed put, the front camera would
 * silently describe the opposite arm — and it would look exactly like working code.
 */
class PoseFrameConfidenceMirrorTest {

    @Test
    fun mirrored_swapsConfidenceKeysWithTheAnglesTheyDescribe() {
        val frame = PoseFrame(
            angles = JointAngles(leftElbow = 40.0, rightElbow = 150.0),
            landmarks = List(33) { Landmark(0.5f, 0.5f, 0f, 1f, 1f) },
            isFrontCamera = true,
            timestampMs = 1L,
            angleConfidence = mapOf("left_elbow" to 0.2f, "right_elbow" to 0.9f),
            angleObservability = mapOf("left_elbow" to 0.1f, "right_elbow" to 0.8f),
        )

        val mirrored = frame.mirrored()

        // The angle that is now `leftElbow` came from the right side — so must its confidence.
        assertEquals(150.0, mirrored.angles.leftElbow)
        assertEquals(0.9f, mirrored.angleConfidence["left_elbow"])
        assertEquals(0.2f, mirrored.angleConfidence["right_elbow"])
        assertEquals(0.8f, mirrored.angleObservability["left_elbow"])
        assertEquals(0.1f, mirrored.angleObservability["right_elbow"])
    }

    @Test
    fun mirrored_isNoOpForBackCamera() {
        val frame = PoseFrame(
            angles = JointAngles(leftElbow = 40.0),
            landmarks = List(33) { Landmark(0.5f, 0.5f, 0f, 1f, 1f) },
            isFrontCamera = false,
            timestampMs = 1L,
            angleConfidence = mapOf("left_elbow" to 0.2f),
        )

        assertEquals(frame, frame.mirrored())
    }
}
