package com.trainingvalidator.poc.training.engine.testing

import com.trainingvalidator.poc.analysis.JointAngles
import com.google.gson.annotations.SerializedName

/** Default angles for harness frames when a row omits angles (upright neutral pose). */
fun defaultJointAngles(): JointAngles = JointAngles(
    leftElbow = 180.0, rightElbow = 180.0,
    leftShoulder = 90.0, rightShoulder = 90.0,
    leftShoulderCross = 90.0, rightShoulderCross = 90.0,
    leftWrist = 180.0, rightWrist = 180.0,
    leftHip = 180.0, rightHip = 180.0,
    neckLeft = 160.0, neckRight = 160.0, neckSpine = 160.0,
    spine = 0.0,
    leftKnee = 180.0, rightKnee = 180.0,
    leftAnkle = 90.0, rightAnkle = 90.0
)

/**
 * Replays a list of frame inputs for parity harness. Landmarks are optional; many tests use angles only.
 */
data class FrameFixture(
    val name: String,
    val isFrontCamera: Boolean = true,
    val frames: List<FrameFixtureFrame>
) {
    companion object {
        fun singleAngles(
            name: String,
            isFrontCamera: Boolean,
            vararg suppliers: (() -> JointAngles)
        ): FrameFixture = FrameFixture(
            name = name,
            isFrontCamera = isFrontCamera,
            frames = suppliers.mapIndexed { i, s ->
                FrameFixtureFrame(
                    index = i,
                    timestampMs = 1000L * (i + 1),
                    angles = s()
                )
            }
        )
    }
}

data class FrameFixtureFrame(
    val index: Int,
    val timestampMs: Long,
    /** Serialized as nested object if loaded from JSON — Gson fills JointAngles fields via reflection. */
    val angles: JointAngles? = null,
    val isFrontCamera: Boolean? = null
)

/**
 * JSON-friendly frame row when angles are given as a flat map (for hand-written fixtures).
 */
data class JsonFrameFixture(
    @SerializedName("name") val name: String = "",
    @SerializedName("isFrontCamera") val isFrontCamera: Boolean = true,
    @SerializedName("frames") val frames: List<JsonFrameData> = emptyList()
)

data class JsonFrameData(
    @SerializedName("timestampMs") val timestampMs: Long = 0L,
    @SerializedName("angles") val angles: Map<String, Double> = emptyMap()
)
