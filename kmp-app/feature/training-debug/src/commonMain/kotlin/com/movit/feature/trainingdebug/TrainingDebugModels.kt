package com.movit.feature.trainingdebug

import com.movit.core.training.model.PoseFrame
import com.movit.core.training.config.PositionCheckType
import com.movit.core.training.config.PositionOperator
import com.movit.core.training.position.AxisMatchResult
import com.movit.core.training.position.BodyPosture
import com.movit.core.training.position.ExpectedDirection
import com.movit.core.training.position.PoseSceneExpectation
import com.movit.core.training.position.PoseSceneResult
import com.movit.core.training.position.PositionCheckDebug
import com.movit.core.training.position.VisibleRegion
import com.movit.core.training.session.SetupPhase
import com.movit.core.training.session.SetupReadinessResult
import com.movit.core.training.geometry.JointLandmarkMapping
import com.movit.designsystem.components.SkeletonDebugOverlayState

enum class TrainingDebugInputMode {
    CAMERA,
    VIDEO,
    IMAGE,
}

enum class TrainingDebugTab {
    ANGLE_DIAGNOSTICS,
    POSITION_CHECK,
    CAMERA_SCENE,
    SETUP_GATE,
}

enum class DebugPoseModelType {
    FULL,
    HEAVY,
}

data class DebugPositionCheckConfig(
    val checkType: PositionCheckType = PositionCheckType.VERTICAL_COMPARISON,
    val primaryLandmark: String = "left_knee",
    val secondaryLandmark: String = "left_ankle",
    val operator: PositionOperator = PositionOperator.SHOULD_NOT_EXCEED,
    val threshold: Double = 0.05,
)

data class DebugSceneExpectationConfig(
    val postures: List<BodyPosture> = listOf(BodyPosture.STANDING),
    val directions: List<ExpectedDirection> = listOf(ExpectedDirection.FRONT),
    val regions: List<VisibleRegion> = listOf(VisibleRegion.FULL_BODY),
) {
    fun toPoseSceneExpectation(): PoseSceneExpectation =
        PoseSceneExpectation(postures, directions, regions)
}

data class TrainingDebugConfig(
    val inputMode: TrainingDebugInputMode = TrainingDebugInputMode.CAMERA,
    val selectedJoints: Set<String> = setOf("left_knee"),
    val positionCheck: DebugPositionCheckConfig = DebugPositionCheckConfig(),
    val sceneExpectation: DebugSceneExpectationConfig = DebugSceneExpectationConfig(),
    val modelType: DebugPoseModelType = DebugPoseModelType.FULL,
    val tiltCorrectionEnabled: Boolean = false,
    val infoPanelVisible: Boolean = true,
    val activeTab: TrainingDebugTab = TrainingDebugTab.ANGLE_DIAGNOSTICS,
)

data class TrainingDebugFrameInput(
    val poseFrame: PoseFrame,
    val rawLandmarks: List<com.movit.core.training.model.Landmark>,
    val smoothedLandmarks: List<com.movit.core.training.model.Landmark>,
    val rawWorldLandmarks: List<com.movit.core.training.model.Landmark>?,
    val smoothedWorldLandmarks: List<com.movit.core.training.model.Landmark>?,
    val inferenceTimeMs: Long = 0L,
    val elbowDiagnosticsPort: ElbowDiagnosticsPort = ElbowDiagnosticsPort.NoOp,
) {
    val timestampMs: Long get() = poseFrame.timestampMs
    val isFrontCamera: Boolean get() = poseFrame.isFrontCamera
    val analysisImageWidth: Int get() = poseFrame.analysisImageWidth
    val analysisImageHeight: Int get() = poseFrame.analysisImageHeight
}

data class AngleDebugPoint(
    val index: Int,
    val name: String,
    val x: Float,
    val y: Float,
    val z: Float,
    val visibility: Float,
    val presence: Float,
)

data class AngleSegmentMetrics(
    val dx: Double,
    val dy: Double,
    val dz: Double,
    val length2D: Double,
    val length3D: Double,
) {
    val depthShare: Double
        get() = if (length3D > 0.0) kotlin.math.abs(dz) / length3D else 0.0

    val planarRatio: Double
        get() = if (length3D > 0.0) length2D / length3D else 0.0
}

data class AngleDebugFrame(
    val pointA: AngleDebugPoint,
    val pointB: AngleDebugPoint,
    val pointC: AngleDebugPoint,
    val xyAngle: Double?,
    val xzAngle: Double?,
    val yzAngle: Double?,
    val xyzAngle: Double?,
    val segmentBA: AngleSegmentMetrics,
    val segmentBC: AngleSegmentMetrics,
) {
    val minVisibility: Float
        get() = minOf(pointA.visibility, pointB.visibility, pointC.visibility)

    val minPresence: Float
        get() = minOf(pointA.presence, pointB.presence, pointC.presence)

    val smoothingDrift: Double?
        get() {
            val raw = xyzAngle ?: return null
            return null
        }
}

data class AngleDiagnosticsData(
    val displayJointCode: String,
    val sourceJointCode: String,
    val effectiveIndices: List<Int>,
    val displayedAngle: Double?,
    val pipelineSourceLabel: String,
    val normalizedRaw: AngleDebugFrame?,
    val normalizedSmoothed: AngleDebugFrame?,
    val worldRaw: AngleDebugFrame?,
    val worldSmoothed: AngleDebugFrame?,
    val elbowDiagnostics: ElbowDiagnosticsSnapshot? = null,
)

/** Hook for Agent 1 [ElbowCorrectionDiagnostics]; populated when training-engine exposes them. */
data class ElbowDiagnosticsSnapshot(
    val facingRatio: Float? = null,
    val screenAngle: Double? = null,
    val worldAngle: Double? = null,
    val maxDzShare: Float? = null,
    val correctionPct: Float? = null,
    val outputAngle: Double? = null,
    val isHolding: Boolean = false,
    val strategy: String? = null,
)

data class TrainingDebugFpsCounters(
    val sourceFps: Int = 0,
    val inferenceFps: Int = 0,
    val analysisFps: Int = 0,
    val skippedBusyFrames: Int = 0,
)

data class TrainingDebugAnalysisResult(
    val hasPose: Boolean,
    val liveValueText: String,
    val statusText: String,
    val infoPanelText: String,
    val angleDiagnostics: List<AngleDiagnosticsData> = emptyList(),
    val positionDebug: PositionCheckDebug? = null,
    val sceneResult: PoseSceneResult? = null,
    val axisMatch: AxisMatchResult? = null,
    val setupProbe: SetupReadinessResult? = null,
    val setupExerciseLabel: String = "Probe (squat default)",
    val overlayState: SkeletonDebugOverlayState = SkeletonDebugOverlayState(),
    val jsonSnapshot: String = "",
)

data class TrainingDebugUiState(
    val config: TrainingDebugConfig = TrainingDebugConfig(),
    val analysis: TrainingDebugAnalysisResult = TrainingDebugAnalysisResult(
        hasPose = false,
        liveValueText = "—",
        statusText = "No pose",
        infoPanelText = "Waiting for pose input…",
    ),
    val fps: TrainingDebugFpsCounters = TrainingDebugFpsCounters(),
    val modelLabel: String = "full",
    val isFrontCamera: Boolean = true,
    val permissionGranted: Boolean = false,
    val errorMessage: String? = null,
    val videoPlaying: Boolean = false,
    val videoCurrentMs: Long = 0L,
    val videoDurationMs: Long = 0L,
    val hasMediaLoaded: Boolean = false,
)

object TrainingDebugJointCatalog {
    val trackedJointCodes: Set<String> = JointLandmarkMapping.trackedJointCodes

    val landmarkNames: List<String> = listOf(
        "nose",
        "left_shoulder", "right_shoulder",
        "left_elbow", "right_elbow",
        "left_wrist", "right_wrist",
        "left_hip", "right_hip",
        "left_knee", "right_knee",
        "left_ankle", "right_ankle",
        "left_heel", "right_heel",
        "left_foot_index", "right_foot_index",
        "neck", "spine",
    )
}
