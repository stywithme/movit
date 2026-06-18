package com.movit.feature.training

import com.movit.core.training.config.AngleRange
import com.movit.core.training.config.StateRanges
import com.movit.core.training.engine.JointState
import com.movit.core.training.engine.JointStateInfo
import com.movit.designsystem.components.SkeletonLandmarkPoint
import com.movit.designsystem.components.SkeletonRomAngleRange
import com.movit.designsystem.components.SkeletonRomGeometry
import com.movit.designsystem.components.SkeletonRomIndicator
import com.movit.designsystem.components.SkeletonRomIndicatorStyle
import com.movit.designsystem.components.SkeletonRomState
import com.movit.designsystem.components.SkeletonRomStateRanges

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

fun buildSkeletonRomIndicators(
    landmarks: List<SkeletonLandmarkPoint>?,
    jointStateInfos: Map<String, JointStateInfo>,
    anySideDimmedJointCodes: Set<String> = emptySet(),
    isBilateralFlipped: Boolean = false,
    indicatorType: String = "arc",
): List<SkeletonRomIndicator> {
    if (landmarks == null || landmarks.size < 33 || jointStateInfos.isEmpty()) return emptyList()
    val style = if (indicatorType.equals("line", ignoreCase = true)) {
        SkeletonRomIndicatorStyle.LINE
    } else {
        SkeletonRomIndicatorStyle.ARC
    }

    return jointStateInfos.values.mapNotNull { info ->
        if (!info.isPrimary) return@mapNotNull null
        if (info.jointCode in anySideDimmedJointCodes) return@mapNotNull null
        val upRanges = info.upStateRanges?.toSkeletonRomStateRanges()
        val downRanges = info.downStateRanges?.toSkeletonRomStateRanges()
        if (upRanges == null && downRanges == null) return@mapNotNull null

        val lookupCode = effectiveLandmarkJointCode(info.jointCode, isBilateralFlipped)
        val anchor = jointAnchor(lookupCode, landmarks) ?: return@mapNotNull null
        val activeRange = info.stateRanges ?: info.upStateRanges ?: info.downStateRanges
        val visualState = info.state.toSkeletonRomState()

        SkeletonRomIndicator(
            jointCode = info.jointCode,
            style = style,
            centerX = anchor.center.x,
            centerY = anchor.center.y,
            upperEndX = anchor.upper.x,
            upperEndY = anchor.upper.y,
            lowerEndX = anchor.lower.x,
            lowerEndY = anchor.lower.y,
            limbEndX = anchor.lower.x,
            limbEndY = anchor.lower.y,
            currentAngleDeg = info.currentAngle.toFloat().coerceIn(0f, 180f),
            rangeMinDeg = activeRange?.effectiveMin?.toFloat() ?: 0f,
            rangeMaxDeg = activeRange?.effectiveMax?.toFloat() ?: 180f,
            markerColorArgb = SkeletonRomGeometry.stateColorArgb(visualState),
            currentState = visualState,
            upStateRanges = upRanges,
            downStateRanges = downRanges,
            invertAngles = info.invertIndicator,
            isHoldRange = upRanges != null && upRanges == downRanges,
            isPrimary = info.isPrimary,
            dimmed = false,
        )
    }
}

private data class NormPoint(val x: Float, val y: Float)

private data class JointAnchor(
    val upper: NormPoint,
    val center: NormPoint,
    val lower: NormPoint,
)

private fun jointAnchor(jointCode: String, landmarks: List<SkeletonLandmarkPoint>): JointAnchor? =
    when {
        jointCode.contains("left_knee", ignoreCase = true) -> anchorTriple(LEFT_HIP, LEFT_KNEE, LEFT_ANKLE, landmarks)
        jointCode.contains("right_knee", ignoreCase = true) -> anchorTriple(RIGHT_HIP, RIGHT_KNEE, RIGHT_ANKLE, landmarks)
        jointCode.contains("left_elbow", ignoreCase = true) -> anchorTriple(LEFT_SHOULDER, LEFT_ELBOW, LEFT_WRIST, landmarks)
        jointCode.contains("right_elbow", ignoreCase = true) -> anchorTriple(RIGHT_SHOULDER, RIGHT_ELBOW, RIGHT_WRIST, landmarks)
        else -> null
    }

private fun anchorTriple(
    proximal: Int,
    joint: Int,
    distal: Int,
    landmarks: List<SkeletonLandmarkPoint>,
): JointAnchor? {
    val p = landmarks.getOrNull(proximal)?.takeIf { it.visible } ?: return null
    val j = landmarks.getOrNull(joint)?.takeIf { it.visible } ?: return null
    val d = landmarks.getOrNull(distal)?.takeIf { it.visible } ?: return null
    return JointAnchor(
        upper = NormPoint(p.x, p.y),
        center = NormPoint(j.x, j.y),
        lower = NormPoint(d.x, d.y),
    )
}

private fun StateRanges.toSkeletonRomStateRanges(): SkeletonRomStateRanges =
    SkeletonRomStateRanges(
        perfect = perfect.toSkeletonRomAngleRange(),
        normal = normal?.toSkeletonRomAngleRange(),
        pad = pad?.toSkeletonRomAngleRange(),
        warning = warning?.toSkeletonRomAngleRange(),
        danger = danger?.toSkeletonRomAngleRange(),
    )

private fun AngleRange.toSkeletonRomAngleRange(): SkeletonRomAngleRange =
    SkeletonRomAngleRange(minDeg = min.toFloat(), maxDeg = max.toFloat())

private fun JointState.toSkeletonRomState(): SkeletonRomState =
    when (this) {
        JointState.PERFECT -> SkeletonRomState.PERFECT
        JointState.NORMAL -> SkeletonRomState.NORMAL
        JointState.PAD -> SkeletonRomState.PAD
        JointState.WARNING -> SkeletonRomState.WARNING
        JointState.DANGER -> SkeletonRomState.DANGER
        JointState.TRANSITION -> SkeletonRomState.TRANSITION
    }
