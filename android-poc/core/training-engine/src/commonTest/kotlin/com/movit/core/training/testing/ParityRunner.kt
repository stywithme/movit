package com.movit.core.training.testing

import com.movit.core.training.config.ExerciseConfig
import com.movit.core.training.engine.Phase
import com.movit.core.training.geometry.PoseFrameAssembler
import com.movit.core.training.model.Landmark
import com.movit.core.training.model.PoseFrame
import com.movit.core.training.model.PoseLandmarkIndices
import com.movit.core.training.session.LiveExerciseRunner
import com.movit.core.training.session.MovitTrainingEngine

/** Golden replay harness for [LiveExerciseRunner] / [MovitTrainingEngine]. */
object ParityRunner {

    data class FrameTrace(
        val frameIndex: Int,
        val timestampMs: Long,
        val phase: Phase,
        val repCount: Int,
        val holdActive: Boolean = false,
    ) {
        fun comparable(): String = "f=$frameIndex,t=$timestampMs,p=$phase,r=$repCount,h=$holdActive"
    }

    data class ParityResult(
        val traces: List<FrameTrace>,
        val finalRepCount: Int,
        val isHoldExercise: Boolean,
    )

    fun run(
        config: ExerciseConfig,
        fixture: FrameFixture,
        targetReps: Int = 12,
    ): ParityResult = runEngine(
        config = config,
        fixture = fixture,
        targetReps = targetReps,
    ) { engine, frame ->
        engine.processFrame(frame)
    }

    fun runLiveRunner(
        config: ExerciseConfig,
        fixture: FrameFixture,
        targetReps: Int = 12,
    ): ParityResult = runEngine(
        config = config,
        fixture = fixture,
        targetReps = targetReps,
    ) { runner, frame ->
        runner.processFrame(frame)
    }

    fun assertSelfConsistent(
        config: ExerciseConfig,
        fixture: FrameFixture,
        targetReps: Int = 2,
    ) {
        val r1 = run(config, fixture, targetReps)
        val r2 = run(config, fixture, targetReps)
        require(r1.traces.map { it.comparable() } == r2.traces.map { it.comparable() }) {
            "Parity self-check failed: ${r1.traces} vs ${r2.traces}"
        }
    }

    private inline fun runEngine(
        config: ExerciseConfig,
        fixture: FrameFixture,
        targetReps: Int,
        process: (LiveExerciseRunner, PoseFrame) -> Unit,
    ): ParityResult {
        var now = 0L
        val runner = LiveExerciseRunner(
            exerciseConfig = config,
            targetReps = targetReps,
            wallClock = { now },
        )
        val traces = mutableListOf<FrameTrace>()
        var lastReps = 0
        var lastPhase = Phase.IDLE
        var holdActive = false
        runner.onMetrics = { metrics ->
            lastReps = metrics.repCount
            lastPhase = metrics.phase
        }
        runner.start()

        fixture.frames.forEach { frame ->
            now = frame.timestampMs
            val poseFrame = syntheticFrame(
                angles = frame.angles,
                timestamp = frame.timestampMs,
                isFrontCamera = frame.isFrontCamera ?: fixture.isFrontCamera,
            )
            process(runner, poseFrame)
            holdActive = lastPhase == Phase.COUNT
            traces += FrameTrace(
                frameIndex = frame.index,
                timestampMs = frame.timestampMs,
                phase = lastPhase,
                repCount = lastReps,
                holdActive = holdActive,
            )
        }
        return ParityResult(
            traces = traces,
            finalRepCount = lastReps,
            isHoldExercise = config.isHoldExercise(),
        )
    }

    fun syntheticFrame(
        angles: Map<String, Double>,
        timestamp: Long,
        isFrontCamera: Boolean,
    ): PoseFrame {
        val landmarks = List(33) { Landmark(0.5f, 0.5f, 0f, 1f, 1f) }.toMutableList()
        landmarks[PoseLandmarkIndices.LEFT_HIP] = Landmark(0.45f, 0.45f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.LEFT_KNEE] = Landmark(0.45f, 0.60f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.LEFT_ANKLE] = Landmark(0.45f, 0.78f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.LEFT_SHOULDER] = Landmark(0.45f, 0.30f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.RIGHT_HIP] = Landmark(0.55f, 0.45f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.RIGHT_KNEE] = Landmark(0.55f, 0.60f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.RIGHT_ANKLE] = Landmark(0.55f, 0.78f, 0f, 1f, 1f)
        landmarks[PoseLandmarkIndices.RIGHT_SHOULDER] = Landmark(0.55f, 0.30f, 0f, 1f, 1f)
        val assembled = PoseFrameAssembler.assemble(landmarks, timestamp, isFrontCamera)
        return assembled.copy(
            angles = assembled.angles.copy(
                leftKnee = angles["left_knee"] ?: assembled.angles.leftKnee,
                rightKnee = angles["right_knee"] ?: assembled.angles.rightKnee,
                leftHip = angles["left_hip"] ?: assembled.angles.leftHip,
                rightHip = angles["right_hip"] ?: assembled.angles.rightHip,
                leftShoulder = angles["left_shoulder"] ?: assembled.angles.leftShoulder,
                rightShoulder = angles["right_shoulder"] ?: assembled.angles.rightShoulder,
                leftElbow = angles["left_elbow"] ?: assembled.angles.leftElbow,
                rightElbow = angles["right_elbow"] ?: assembled.angles.rightElbow,
            ),
        )
    }
}
