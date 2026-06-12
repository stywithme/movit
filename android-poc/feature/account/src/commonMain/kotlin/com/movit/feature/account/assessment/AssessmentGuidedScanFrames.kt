package com.movit.feature.account.assessment

import com.movit.core.training.model.JointAngles
import com.movit.core.training.model.Landmark
import com.movit.core.training.model.PoseFrame

/** Synthetic pose frame for iOS guided scan until MediaPipe bridge ships (Phase 07). */
fun assessmentGuidedPoseFrame(timestampMs: Long): PoseFrame = PoseFrame(
    angles = JointAngles(
        leftShoulder = 150.0,
        rightShoulder = 148.0,
        leftHip = 105.0,
        rightHip = 103.0,
        leftKnee = 132.0,
        rightKnee = 130.0,
        leftAnkle = 92.0,
        rightAnkle = 91.0,
    ),
    landmarks = List(33) {
        Landmark(x = 0.5f, y = 0.5f, z = 0f, visibility = 0.92f, presence = 0.95f)
    },
    isFrontCamera = true,
    timestampMs = timestampMs,
)
