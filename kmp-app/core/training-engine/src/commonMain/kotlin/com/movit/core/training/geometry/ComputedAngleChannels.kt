package com.movit.core.training.geometry

/**
 * Single source of truth for joint codes that [PoseFrameAssembler.calculateAngles] actually fills.
 * Used by [com.movit.core.training.config.ExerciseConfig.validationIssues] to reject ghost channels.
 */
object ComputedAngleChannels {
    val CODES: Set<String> = setOf(
        "left_elbow",
        "right_elbow",
        "left_shoulder",
        "right_shoulder",
        "left_shoulder_cross",
        "right_shoulder_cross",
        "left_wrist",
        "right_wrist",
        "left_hip",
        "right_hip",
        "left_hip_cross",
        "right_hip_cross",
        "left_knee",
        "right_knee",
        "left_ankle",
        "right_ankle",
        "neck",
        "neck_left",
        "neck_right",
        "neck_spine",
        "spine",
    )
}
