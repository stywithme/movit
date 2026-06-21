package com.movit.core.training.session

import com.movit.core.training.config.ExerciseConfig
import com.movit.core.training.engine.Phase
import com.movit.core.training.model.PoseFrame

/**
 * Live exercise facade — delegates to [MovitTrainingEngine] (Phase 07 WS-2).
 */
class LiveExerciseRunner(
    private val exerciseConfig: ExerciseConfig,
    targetReps: Int = exerciseConfig.defaultTargetReps(),
    poseVariantIndex: Int = 0,
    wallClock: () -> Long = { com.movit.core.training.engine.currentTimeMillis() },
) {
    data class Metrics(
        val repCount: Int,
        val targetReps: Int,
        val liveFormScore: Float,
        val averageFormScore: Float,
        val phase: Phase,
        val isTargetReached: Boolean,
    )

    private val engine = MovitTrainingEngine(
        exerciseConfig = exerciseConfig,
        poseVariantIndex = poseVariantIndex,
        targetRepsOverride = targetReps,
        wallClock = wallClock,
    )

    var onMetrics: ((Metrics) -> Unit)? = null
    var onTargetReached: (() -> Unit)? = null

    init {
        engine.onRepCountChanged = { _, _, _ -> emitMetrics() }
        engine.onPhaseChanged = { emitMetrics() }
        engine.onTargetReached = {
            onTargetReached?.invoke()
            emitMetrics()
        }
    }

    fun start() {
        engine.start()
        emitMetrics()
    }

    fun stop(): Long = engine.stop().durationMs

    fun processFrame(frame: PoseFrame) {
        engine.processFrame(frame)
        emitMetrics()
    }

    private fun emitMetrics() {
        val snapshot = engine.metricsSnapshot()
        onMetrics?.invoke(
            Metrics(
                repCount = snapshot.repCount,
                targetReps = snapshot.targetReps,
                liveFormScore = snapshot.liveFormScore,
                averageFormScore = snapshot.averageFormScore,
                phase = snapshot.phase,
                isTargetReached = snapshot.isTargetReached,
            ),
        )
    }
}