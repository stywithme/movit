package com.movit.core.training.geometry

import com.movit.core.training.model.JointAngles
import com.movit.core.training.model.Landmark

/** Front-camera landmark index swap table (legacy BodyLandmarks.SWAP_MAP). */
object PoseLandmarkMirroring {
    private val swapMap = mapOf(
        1 to 4, 4 to 1, 2 to 5, 5 to 2, 3 to 6, 6 to 3,
        7 to 8, 8 to 7, 9 to 10, 10 to 9,
        11 to 12, 12 to 11, 13 to 14, 14 to 13, 15 to 16, 16 to 15,
        17 to 18, 18 to 17, 19 to 20, 20 to 19, 21 to 22, 22 to 21,
        23 to 24, 24 to 23, 25 to 26, 26 to 25, 27 to 28, 28 to 27,
        29 to 30, 30 to 29, 31 to 32, 32 to 31,
    )

    fun mirroredIndex(index: Int): Int = swapMap[index] ?: index

    fun mirrorLandmarks(landmarks: List<Landmark>): List<Landmark> {
        if (landmarks.isEmpty()) return landmarks
        val out = landmarks.toMutableList()
        for ((left, right) in swapMap) {
            if (left >= out.size || right >= out.size) continue
            val l = out[left]
            val r = out[right]
            out[left] = r
            out[right] = l
        }
        return out
    }

    fun mirrorAngles(angles: JointAngles): JointAngles = angles.copy(
        leftElbow = angles.rightElbow,
        rightElbow = angles.leftElbow,
        leftShoulder = angles.rightShoulder,
        rightShoulder = angles.leftShoulder,
        leftShoulderCross = angles.rightShoulderCross,
        rightShoulderCross = angles.leftShoulderCross,
        leftWrist = angles.rightWrist,
        rightWrist = angles.leftWrist,
        leftHip = angles.rightHip,
        rightHip = angles.leftHip,
        leftHipCross = angles.rightHipCross,
        rightHipCross = angles.leftHipCross,
        leftKnee = angles.rightKnee,
        rightKnee = angles.leftKnee,
        leftAnkle = angles.rightAnkle,
        rightAnkle = angles.leftAnkle,
        neckLeft = angles.neckRight,
        neckRight = angles.neckLeft,
    )
}
