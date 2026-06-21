package com.movit.feature.trainingdebug

import com.movit.core.data.MovitData
import com.movit.core.training.boundary.DeviceTiltPort
import com.movit.core.training.config.ExerciseConfig
import com.movit.core.training.engine.Phase
import com.movit.core.training.geometry.PoseFrameAssembler
import com.movit.core.training.position.PoseSceneDetector
import com.movit.core.training.position.PositionCheckDebugStatus
import com.movit.core.training.position.PositionValidator
import com.movit.core.training.session.SetupProbeDefaults
import com.movit.core.training.session.SetupReadinessGate
import com.movit.designsystem.components.SkeletonDebugOverlayState
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.serialization.json.putJsonArray

class TrainingDebugAnalyzer {
    private val sceneDetector = PoseSceneDetector()
    private var setupGate: SetupReadinessGate? = null
    private var setupGateTiltEnabled: Boolean? = null
    private var positionValidator: PositionValidator? = null

    fun resetAnalysisState(reason: String) {
        PoseFrameAssembler.resetElbowEstimator()
        sceneDetector.reset()
        setupGate?.reset()
        setupGate = null
        setupGateTiltEnabled = null
        positionValidator = null
    }

    fun analyze(
        frame: TrainingDebugFrameInput,
        config: TrainingDebugConfig,
        tiltSource: com.movit.core.training.boundary.DeviceTiltPort? = null,
        exerciseSlug: String? = null,
    ): TrainingDebugAnalysisResult {
        val poseFrame = frame.poseFrame
        val landmarks = poseFrame.landmarks
        if (landmarks == null || landmarks.size < 33) {
            return noPoseResult("Insufficient landmarks (${landmarks?.size ?: 0})")
        }

        val angles = poseFrame.angles

        val angleDiagnostics = AngleDiagnosticsBuilder.buildAll(
            selectedJoints = config.selectedJoints,
            angles = angles,
            rawNorm = frame.rawLandmarks,
            smoothedNorm = frame.smoothedLandmarks,
            rawWorld = frame.rawWorldLandmarks,
            smoothedWorld = frame.smoothedWorldLandmarks,
            isFrontCamera = poseFrame.isFrontCamera,
            elbowDiagnosticsPort = PoseFrameAssemblerElbowDiagnostics,
        )

        val sceneExpectation = config.sceneExpectation.toPoseSceneExpectation()
        val sceneResult = sceneDetector.detect(landmarks, poseFrame.isFrontCamera)
        val axisMatch = sceneExpectation.matchesScene(sceneResult)

        val validator = positionValidator ?: DebugPositionCheckFactory.buildValidator(
            config = config.positionCheck,
            sceneExpectation = sceneExpectation,
            tiltSource = tiltSource.takeIf { config.tiltCorrectionEnabled },
        ).also { positionValidator = it }
        val positionResult = validator.validate(
            landmarks = landmarks,
            currentPhase = Phase.START,
            isFrontCamera = poseFrame.isFrontCamera,
        )
        val positionDebug = positionResult.debugChecks.firstOrNull()

        val (setupExerciseConfig, setupExerciseLabel) = resolveSetupExercise(exerciseSlug)
        val gate = resolveSetupGate(
            tiltSource = tiltSource,
            tiltEnabled = config.tiltCorrectionEnabled,
        )
        val setupProbe = gate.validate(
            angles = angles,
            landmarks = landmarks,
            exerciseConfig = setupExerciseConfig,
            poseVariantIndex = 0,
            isFrontCamera = poseFrame.isFrontCamera,
        )

        val liveValue = primaryLiveValue(config, angles, positionDebug)
        val status = buildStatus(config, positionDebug?.status, axisMatch.allMatch, setupProbe.phase.name, setupExerciseLabel)
        val infoText = buildInfoPanel(
            config = config,
            angles = angles,
            angleDiagnostics = angleDiagnostics,
            positionDebug = positionDebug,
            sceneResult = sceneResult,
            axisMatch = axisMatch,
            setupProbe = setupProbe,
            setupExerciseLabel = setupExerciseLabel,
            inferenceMs = frame.inferenceTimeMs,
        )
        val jsonSnapshot = buildJsonSnapshot(
            config = config,
            angleDiagnostics = angleDiagnostics,
            positionDebug = positionDebug,
            sceneResult = sceneResult,
            axisMatch = axisMatch,
            setupPhase = setupProbe.phase.name,
            setupExerciseLabel = setupExerciseLabel,
        )
        val baseOverlay = SkeletonDebugOverlayState()

        return TrainingDebugAnalysisResult(
            hasPose = true,
            liveValueText = liveValue,
            statusText = status,
            infoPanelText = infoText,
            angleDiagnostics = angleDiagnostics,
            positionDebug = positionDebug,
            sceneResult = sceneResult,
            axisMatch = axisMatch,
            setupProbe = setupProbe,
            setupExerciseLabel = setupExerciseLabel,
            overlayState = baseOverlay,
            jsonSnapshot = jsonSnapshot,
        ).let { result ->
            result.copy(
                overlayState = TrainingDebugOverlayMapper.map(
                    config = config,
                    analysis = result,
                    isFrontCamera = poseFrame.isFrontCamera,
                ),
            )
        }
    }

    private fun primaryLiveValue(
        config: TrainingDebugConfig,
        angles: com.movit.core.training.model.JointAngles,
        positionDebug: com.movit.core.training.position.PositionCheckDebug?,
    ): String = when (config.activeTab) {
        TrainingDebugTab.ANGLE_DIAGNOSTICS -> {
            val parts = config.selectedJoints.mapNotNull { joint ->
                angles.getAngle(joint)?.let { "${it.roundToInt()}°" }
            }
            when {
                parts.isEmpty() -> "—"
                parts.size == 1 -> parts.first()
                else -> parts.joinToString(" · ")
            }
        }
        TrainingDebugTab.POSITION_CHECK ->
            positionDebug?.actualValue?.let { formatFixedDecimals(it, 3) } ?: "—"
        TrainingDebugTab.CAMERA_SCENE -> "scene"
        TrainingDebugTab.SETUP_GATE -> "setup"
    }

    private fun buildStatus(
        config: TrainingDebugConfig,
        positionStatus: PositionCheckDebugStatus?,
        sceneMatch: Boolean,
        setupPhase: String,
        setupExerciseLabel: String,
    ): String = when (config.activeTab) {
        TrainingDebugTab.ANGLE_DIAGNOSTICS -> "Angle diagnostics"
        TrainingDebugTab.POSITION_CHECK -> positionStatus?.name ?: "No check"
        TrainingDebugTab.CAMERA_SCENE -> if (sceneMatch) "Scene match" else "Scene mismatch"
        TrainingDebugTab.SETUP_GATE -> "Setup: $setupPhase ($setupExerciseLabel)"
    }

    private fun buildInfoPanel(
        config: TrainingDebugConfig,
        angles: com.movit.core.training.model.JointAngles,
        angleDiagnostics: List<AngleDiagnosticsData>,
        positionDebug: com.movit.core.training.position.PositionCheckDebug?,
        sceneResult: com.movit.core.training.position.PoseSceneResult,
        axisMatch: com.movit.core.training.position.AxisMatchResult?,
        setupProbe: com.movit.core.training.session.SetupReadinessResult,
        setupExerciseLabel: String,
        inferenceMs: Long,
    ): String = buildString {
        appendLine("inferenceMs=$inferenceMs")
        when (config.activeTab) {
            TrainingDebugTab.ANGLE_DIAGNOSTICS -> {
                angleDiagnostics.forEach { item ->
                    appendLine("joint=${item.displayJointCode} angle=${item.displayedAngle} source=${item.pipelineSourceLabel}")
                    item.elbowDiagnostics?.strategy?.let { appendLine("  elbow strategy=$it holding=${item.elbowDiagnostics.isHolding}") }
                }
            }
            TrainingDebugTab.POSITION_CHECK -> {
                positionDebug?.let {
                    appendLine("status=${it.status} actual=${it.actualValue} threshold=${it.threshold}")
                }
            }
            TrainingDebugTab.CAMERA_SCENE -> {
                appendLine("direction=${sceneResult.direction} posture=${sceneResult.posture} region=${sceneResult.region}")
                axisMatch?.let {
                    appendLine("match posture=${it.postureMatch} direction=${it.directionMatch} region=${it.regionMatch}")
                }
            }
            TrainingDebugTab.SETUP_GATE -> {
                appendLine("exercise=$setupExerciseLabel")
                appendLine("phase=${setupProbe.phase} progress=${setupProbe.progressPercent}% confirmed=${setupProbe.isConfirmed}")
                setupProbe.axisStatuses.let { axes ->
                    appendLine("  region=${axes.region} posture=${axes.posture} direction=${axes.direction}")
                }
            }
        }
    }

    private fun buildJsonSnapshot(
        config: TrainingDebugConfig,
        angleDiagnostics: List<AngleDiagnosticsData>,
        positionDebug: com.movit.core.training.position.PositionCheckDebug?,
        sceneResult: com.movit.core.training.position.PoseSceneResult,
        axisMatch: com.movit.core.training.position.AxisMatchResult?,
        setupPhase: String,
        setupExerciseLabel: String,
    ): String = runCatching {
        buildJsonObject {
            put("tab", config.activeTab.name)
            putJsonArray("joints") {
                angleDiagnostics.forEach { item ->
                    add(
                        buildJsonObject {
                            put("joint", item.displayJointCode)
                            item.displayedAngle?.let { put("angle", it) }
                            put("pipeline", item.pipelineSourceLabel)
                            item.elbowDiagnostics?.strategy?.let { put("elbowStrategy", it) }
                        },
                    )
                }
            }
            positionDebug?.let {
                put("positionStatus", it.status.name)
                it.actualValue?.let { actual -> put("positionActual", actual) }
            }
            put("sceneDirection", sceneResult.direction.name)
            put("scenePosture", sceneResult.posture.name)
            axisMatch?.let { put("sceneAllMatch", it.allMatch) }
            put("setupPhase", setupPhase)
            put("setupExercise", setupExerciseLabel)
        }.toString()
    }.getOrDefault("")

    private fun resolveSetupGate(
        tiltSource: DeviceTiltPort?,
        tiltEnabled: Boolean,
    ): SetupReadinessGate {
        if (setupGate == null || setupGateTiltEnabled != tiltEnabled) {
            setupGate = SetupReadinessGate(
                tiltSource = tiltSource.takeIf { tiltEnabled },
            )
            setupGateTiltEnabled = tiltEnabled
        }
        return setupGate!!
    }

    private fun resolveSetupExercise(slug: String?): Pair<ExerciseConfig, String> {
        if (!slug.isNullOrBlank() && MovitData.isInstalled) {
            MovitData.trainingConfig.getExercise(slug)?.let { config ->
                return config to slug
            }
        }
        return SetupProbeDefaults.exerciseConfig to "Probe (squat default)"
    }

    private fun noPoseResult(reason: String) = TrainingDebugAnalysisResult(
        hasPose = false,
        liveValueText = "—",
        statusText = "No pose",
        infoPanelText = reason,
    )
}

private fun formatFixedDecimals(value: Double, fractionDigits: Int): String {
    val multiplier = 10.0.pow(fractionDigits)
    val rounded = kotlin.math.round(value * multiplier) / multiplier
    val text = rounded.toString()
    if (fractionDigits == 0) return text.substringBefore('.')
    val dot = text.indexOf('.')
    return if (dot < 0) {
        "$text.${"0".repeat(fractionDigits)}"
    } else {
        val fraction = text.substring(dot + 1).padEnd(fractionDigits, '0').take(fractionDigits)
        "${text.substring(0, dot)}.$fraction"
    }
}
