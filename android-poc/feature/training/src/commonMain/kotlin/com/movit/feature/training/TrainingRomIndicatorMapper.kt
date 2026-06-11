package com.movit.feature.training

import com.movit.designsystem.components.SkeletonJointQuality
import com.movit.designsystem.components.SkeletonJointVisual
import com.movit.designsystem.components.SkeletonLandmarkPoint
import com.movit.designsystem.components.SkeletonRomIndicator
import com.movit.designsystem.components.SkeletonRomIndicatorStyle

private const val LEFT_HIP = 23
private const val RIGHT_HIP = 24
private const val LEFT_KNEE = 25
private const val RIGHT_KNEE = 26
private const val LEFT_ANKLE = 27
private const val RIGHT_ANKLE = 28
private const val LEFT_SHOULDER = 11
private const val RIGHT_SHOULDER = 12
private const val LEFT_ELBOW = 13
private const val RIGHT_ELBOW = 14
private const val LEFT_WRIST = 15
private const val RIGHT_WRIST = 16

/**
 * Builds partial ROM overlays from landmarks + joint visuals until engine feeds full ranges (I-8).
 */
fun buildSkeletonRomIndicators(
    landmarks: List<SkeletonLandmarkPoint>?,
    jointVisuals: Map<String, SkeletonJointVisual>,
): List<SkeletonRomIndicator> {
    if (landmarks == null || landmarks.size < 33 || jointVisuals.isEmpty()) return emptyList()
    return jointVisuals.values.mapNotNull { visual ->
        if (visual.quality == SkeletonJointQuality.NORMAL && !visual.dimmed) return@mapNotNull null
        val anchor = jointAnchor(visual.jointCode, landmarks) ?: return@mapNotNull null
        val (rangeMin, rangeMax, angle, color) = qualityBand(visual.quality)
        val style = if (visual.jointCode.contains("knee", ignoreCase = true)) {
            SkeletonRomIndicatorStyle.ARC
        } else {
            SkeletonRomIndicatorStyle.LINE
        }
        SkeletonRomIndicator(
            jointCode = visual.jointCode,
            style = style,
            centerX = anchor.center.x,
            centerY = anchor.center.y,
            limbEndX = anchor.limbEnd.x,
            limbEndY = anchor.limbEnd.y,
            currentAngleDeg = angle,
            rangeMinDeg = rangeMin,
            rangeMaxDeg = rangeMax,
            markerColorArgb = color,
        )
    }
}

private data class NormPoint(val x: Float, val y: Float)

private data class JointAnchor(val center: NormPoint, val limbEnd: NormPoint)

private fun jointAnchor(jointCode: String, landmarks: List<SkeletonLandmarkPoint>): JointAnchor? {
    return when {
        jointCode.contains("left_knee", ignoreCase = true) -> anchorTriple(LEFT_HIP, LEFT_KNEE, LEFT_ANKLE, landmarks)
        jointCode.contains("right_knee", ignoreCase = true) -> anchorTriple(RIGHT_HIP, RIGHT_KNEE, RIGHT_ANKLE, landmarks)
        jointCode.contains("left_elbow", ignoreCase = true) -> anchorTriple(LEFT_SHOULDER, LEFT_ELBOW, LEFT_WRIST, landmarks)
        jointCode.contains("right_elbow", ignoreCase = true) -> anchorTriple(RIGHT_SHOULDER, RIGHT_ELBOW, RIGHT_WRIST, landmarks)
        else -> null
    }
}

private fun anchorTriple(
    proximal: Int,
    joint: Int,
    distal: Int,
    landmarks: List<SkeletonLandmarkPoint>,
): JointAnchor? {
    val j = landmarks.getOrNull(joint)?.takeIf { it.visible } ?: return null
    val d = landmarks.getOrNull(distal)?.takeIf { it.visible } ?: return null
    return JointAnchor(
        center = NormPoint(j.x, j.y),
        limbEnd = NormPoint(d.x, d.y),
    )
}

private fun qualityBand(quality: SkeletonJointQuality): Quadruple {
    return when (quality) {
        SkeletonJointQuality.PERFECT -> Quadruple(85f, 110f, 95f, 0xFF7CFF6B)
        SkeletonJointQuality.WARNING -> Quadruple(60f, 85f, 72f, 0xFFFFB020)
        SkeletonJointQuality.DANGER -> Quadruple(30f, 60f, 45f, 0xFFFF5A5A)
        SkeletonJointQuality.NORMAL -> Quadruple(70f, 100f, 85f, 0xFF4FC3F7)
    }
}

private data class Quadruple(
    val rangeMin: Float,
    val rangeMax: Float,
    val angle: Float,
    val color: Long,
)
