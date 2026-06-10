package com.movit.core.training.model

/**
 * All joint angles for one pose frame. Angle calculation lives in
 * [com.movit.core.training.geometry.JointAngleCalculator]; this model is the
 * engine boundary input consumed by phase/rep logic.
 */
data class JointAngles(
    val leftElbow: Double? = null,
    val rightElbow: Double? = null,
    val leftShoulder: Double? = null,
    val rightShoulder: Double? = null,
    val leftShoulderCross: Double? = null,
    val rightShoulderCross: Double? = null,
    val leftWrist: Double? = null,
    val rightWrist: Double? = null,
    val leftHip: Double? = null,
    val rightHip: Double? = null,
    val leftHipCross: Double? = null,
    val rightHipCross: Double? = null,
    val neckLeft: Double? = null,
    val neckRight: Double? = null,
    val neckSpine: Double? = null,
    val spine: Double? = null,
    val leftKnee: Double? = null,
    val rightKnee: Double? = null,
    val leftAnkle: Double? = null,
    val rightAnkle: Double? = null,
) {
    fun getAngle(name: String): Double? = when (name.lowercase()) {
        "left_elbow" -> leftElbow
        "right_elbow" -> rightElbow
        "left_shoulder" -> leftShoulder
        "right_shoulder" -> rightShoulder
        "left_shoulder_cross" -> leftShoulderCross
        "right_shoulder_cross" -> rightShoulderCross
        "left_wrist" -> leftWrist
        "right_wrist" -> rightWrist
        "left_hip" -> leftHip
        "right_hip" -> rightHip
        "left_hip_cross" -> leftHipCross
        "right_hip_cross" -> rightHipCross
        "left_knee" -> leftKnee
        "right_knee" -> rightKnee
        "left_ankle" -> leftAnkle
        "right_ankle" -> rightAnkle
        "neck", "neck_left" -> neckLeft
        "neck_right" -> neckRight
        "neck_spine" -> neckSpine
        "spine" -> spine
        else -> null
    }

    fun toMap(): Map<String, Double> = buildMap {
        leftElbow?.let { put("left_elbow", it) }
        rightElbow?.let { put("right_elbow", it) }
        leftShoulder?.let { put("left_shoulder", it) }
        rightShoulder?.let { put("right_shoulder", it) }
        leftShoulderCross?.let { put("left_shoulder_cross", it) }
        rightShoulderCross?.let { put("right_shoulder_cross", it) }
        leftWrist?.let { put("left_wrist", it) }
        rightWrist?.let { put("right_wrist", it) }
        leftHip?.let { put("left_hip", it) }
        rightHip?.let { put("right_hip", it) }
        leftHipCross?.let { put("left_hip_cross", it) }
        rightHipCross?.let { put("right_hip_cross", it) }
        leftKnee?.let { put("left_knee", it) }
        rightKnee?.let { put("right_knee", it) }
        leftAnkle?.let { put("left_ankle", it) }
        rightAnkle?.let { put("right_ankle", it) }
        neckLeft?.let { put("neck_left", it) }
        neckRight?.let { put("neck_right", it) }
        neckSpine?.let { put("neck_spine", it) }
        spine?.let { put("spine", it) }
    }
}
