package com.movit.feature.training

import androidx.compose.ui.geometry.Offset
import com.movit.core.training.bilateral.BilateralSide
import com.movit.core.training.config.CheckSeverity
import com.movit.core.training.engine.JointAngleTracker
import com.movit.core.training.engine.JointState
import com.movit.core.training.engine.JointStateInfo
import com.movit.core.training.geometry.CameraFrameLayout
import com.movit.core.training.geometry.DisplayLandmarkTransform
import com.movit.core.training.geometry.DisplayScaleMode
import com.movit.core.training.geometry.JointLandmarkMapping
import com.movit.core.training.position.PositionError
import com.movit.core.training.session.SessionRunState
import com.movit.core.training.session.SetupPhase
import com.movit.designsystem.components.SkeletonBilateralSideHint
import com.movit.designsystem.components.SkeletonJointQuality
import com.movit.designsystem.components.SkeletonJointVisual
import com.movit.designsystem.components.SkeletonOverlayMode
import com.movit.designsystem.components.SkeletonOverlayParityState
import com.movit.designsystem.components.SkeletonPositionErrorMark
import com.movit.designsystem.components.SkeletonPositionSeverity
import com.movit.designsystem.components.SkeletonSetupDirection
import com.movit.designsystem.components.SkeletonSetupJointHighlight

/**
 * Cached FILL_CENTER projector — matches [com.movit.feature.training.TrainingCameraSurface] / PreviewView.
 */
class SkeletonLandmarkProjector(
    private val analysisWidth: Int,
    private val analysisHeight: Int,
    private val mirrorPreview: Boolean,
) {
    private var cachedViewW = 0
    private var cachedViewH = 0
    private var transform: DisplayLandmarkTransform? = null

    fun project(normalizedX: Float, normalizedY: Float, canvasWidth: Float, canvasHeight: Float): Offset {
        val viewW = canvasWidth.toInt().coerceAtLeast(1)
        val viewH = canvasHeight.toInt().coerceAtLeast(1)
        if (transform == null || viewW != cachedViewW || viewH != cachedViewH) {
            cachedViewW = viewW
            cachedViewH = viewH
            transform = DisplayLandmarkTransform.fromLayout(
                layout = CameraFrameLayout(
                    analysisWidth = analysisWidth,
                    analysisHeight = analysisHeight,
                    previewWidth = viewW,
                    previewHeight = viewH,
                ),
                isFrontCamera = mirrorPreview,
                scaleMode = DisplayScaleMode.FILL_CENTER,
            )
        }
        val (px, py) = transform!!.mapNormalized(normalizedX, normalizedY)
        return Offset(px, py)
    }
}

/**
 * Projects normalized analysis landmarks onto the Compose overlay canvas using the same
 * FILL_CENTER math as [TrainingCameraSurface] / PreviewView.
 */
fun skeletonLandmarkProjector(
    analysisWidth: Int,
    analysisHeight: Int,
    mirrorPreview: Boolean,
): ((Float, Float, Float, Float) -> Offset)? {
    if (analysisWidth <= 0 || analysisHeight <= 0) return null
    val projector = SkeletonLandmarkProjector(analysisWidth, analysisHeight, mirrorPreview)
    return { x, y, w, h -> projector.project(x, y, w, h) }
}

fun buildSkeletonOverlayParityState(
    runState: SessionRunState,
    setupPhase: String,
    jointStateInfos: Map<String, JointStateInfo>,
    anySideDimmedJointCodes: Set<String>,
    positionErrors: List<PositionError>,
    isBilateralExercise: Boolean,
    isBilateralFlipped: Boolean,
    bilateralSide: BilateralSide?,
    setupJointRows: List<SetupJointGuidanceUi>,
    language: String,
): SkeletonOverlayParityState {
    val mode = resolveSkeletonOverlayMode(runState, setupPhase)
    return SkeletonOverlayParityState(
        mode = mode,
        jointVisuals = mapJointVisuals(jointStateInfos, anySideDimmedJointCodes),
        positionErrors = if (mode == SkeletonOverlayMode.TRAINING) {
            mapPositionErrorMarks(positionErrors)
        } else {
            emptyList()
        },
        setupHighlights = if (mode == SkeletonOverlayMode.SETUP_ANGLES) {
            mapSetupHighlights(setupJointRows)
        } else {
            emptyList()
        },
        bilateralSideHint = if (mode == SkeletonOverlayMode.TRAINING && isBilateralExercise && bilateralSide != null) {
            bilateralSideHint(bilateralSide, language)
        } else {
            null
        },
        isBilateralFlipped = isBilateralFlipped,
    )
}

fun resolveSkeletonOverlayMode(runState: SessionRunState, setupPhase: String): SkeletonOverlayMode =
    when (runState) {
        SessionRunState.SETUP_POSE, SessionRunState.RESUME_SETUP -> {
            if (setupPhase == SetupPhase.ANGLES.name) {
                SkeletonOverlayMode.SETUP_ANGLES
            } else {
                SkeletonOverlayMode.SCENE_CHECK
            }
        }
        SessionRunState.TRAINING -> SkeletonOverlayMode.TRAINING
        SessionRunState.COUNTDOWN, SessionRunState.RESUME_COUNTDOWN -> SkeletonOverlayMode.PREVIEW
        else -> SkeletonOverlayMode.PREVIEW
    }

fun mapJointVisuals(
    jointStateInfos: Map<String, JointStateInfo>,
    anySideDimmedJointCodes: Set<String>,
): Map<String, SkeletonJointVisual> =
    jointStateInfos.mapValues { (_, info) ->
        SkeletonJointVisual(
            jointCode = info.jointCode,
            quality = when (info.state) {
                JointState.PERFECT -> SkeletonJointQuality.PERFECT
                JointState.PAD -> SkeletonJointQuality.PAD
                JointState.DANGER -> SkeletonJointQuality.DANGER
                JointState.WARNING -> SkeletonJointQuality.WARNING
                else -> SkeletonJointQuality.NORMAL
            },
            dimmed = info.jointCode in anySideDimmedJointCodes,
            isPrimary = info.isPrimary,
        )
    }

fun mapPositionErrorMarks(errors: List<PositionError>): List<SkeletonPositionErrorMark> =
    errors
        .sortedBy { severityRank(it.severity) }
        .take(2)
        .mapNotNull { error ->
            val i1 = JointLandmarkMapping.jointToLandmark(error.landmark1) ?: return@mapNotNull null
            val i2 = JointLandmarkMapping.jointToLandmark(error.landmark2) ?: return@mapNotNull null
            SkeletonPositionErrorMark(
                landmark1Index = i1,
                landmark2Index = i2,
                severity = when (error.severity) {
                    CheckSeverity.ERROR -> SkeletonPositionSeverity.ERROR
                    CheckSeverity.WARNING -> SkeletonPositionSeverity.WARNING
                    CheckSeverity.TIP -> SkeletonPositionSeverity.TIP
                },
            )
        }

fun mapSetupHighlights(rows: List<SetupJointGuidanceUi>): List<SkeletonSetupJointHighlight> =
    rows.map { row ->
        SkeletonSetupJointHighlight(
            jointCode = row.jointCode,
            level = row.level,
            currentAngleDeg = row.currentAngle.toFloat(),
            direction = row.direction?.let { dir ->
                when (dir.uppercase()) {
                    "RAISE" -> SkeletonSetupDirection.RAISE
                    "LOWER" -> SkeletonSetupDirection.LOWER
                    else -> null
                }
            } ?: if (row.level.equals("GREEN", ignoreCase = true)) {
                SkeletonSetupDirection.HOLD
            } else {
                null
            },
            isPrimary = row.isPrimary,
        )
    }

fun effectiveLandmarkJointCode(jointCode: String, isBilateralFlipped: Boolean): String =
    if (isBilateralFlipped) JointAngleTracker.mirrorJointCode(jointCode) else jointCode

private fun bilateralSideHint(side: BilateralSide, language: String): SkeletonBilateralSideHint {
    val code = when (side) {
        BilateralSide.LEFT -> "left"
        BilateralSide.RIGHT -> "right"
    }
    val label = when (side) {
        BilateralSide.LEFT -> if (language == "ar") "الجانب الأيسر" else "Left side"
        BilateralSide.RIGHT -> if (language == "ar") "الجانب الأيمن" else "Right side"
    }
    return SkeletonBilateralSideHint(sideCode = code, label = label)
}

private fun severityRank(severity: CheckSeverity): Int = when (severity) {
    CheckSeverity.ERROR -> 0
    CheckSeverity.WARNING -> 1
    CheckSeverity.TIP -> 2
}
