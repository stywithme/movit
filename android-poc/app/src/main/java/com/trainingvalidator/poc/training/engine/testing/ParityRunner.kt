package com.trainingvalidator.poc.training.engine.testing

import com.trainingvalidator.poc.analysis.SmoothedLandmark
import com.trainingvalidator.poc.training.TrainingEngine

/**
 * Drives [TrainingEngine.processFrame] from a [FrameFixture] and records [TrainingEngineTrace] per frame.
 */
object ParityRunner {

    data class ParityResult(
        val traces: List<TrainingEngineTrace>,
        val allEventKeys: List<String>
    )

    fun run(
        engine: TrainingEngine,
        fixture: FrameFixture,
        landmarks: List<SmoothedLandmark>? = null
    ): ParityResult {
        val events = mutableListOf<String>()
        // Collect shared-flow events in a best-effort way (tryEmit in engine).
        // For harness we snapshot empty events per frame; extend if needed.
        val traces = mutableListOf<TrainingEngineTrace>()

        engine.start()

        for ((idx, f) in fixture.frames.withIndex()) {
            val angles = f.angles ?: defaultJointAngles()
            val ts = if (f.timestampMs > 0) f.timestampMs else (1000L * (idx + 1))
            val front = f.isFrontCamera ?: fixture.isFrontCamera
            engine.processFrame(angles, landmarks, front, ts)

            val jointMap = engine.jointStateInfos.value
            val summary = jointMap.mapValues { it.value.state }

            traces.add(
                TrainingEngineTrace(
                    frameIndex = idx,
                    timestampMs = ts,
                    phase = engine.currentPhase.value,
                    repCount = engine.getCurrentRep(),
                    isInStartPosition = engine.isInStartPosition.value,
                    isCountingSuspended = engine.isCountingSuspended.value,
                    isVisibilityPaused = engine.isVisibilityPaused.value,
                    jointStateSummary = summary,
                    eventKeys = events.toList()
                )
            )
        }
        return ParityResult(traces, events)
    }

    /**
     * Returns true if two trace lists are equal (same comparable strings).
     */
    fun compare(a: List<TrainingEngineTrace>, b: List<TrainingEngineTrace>): Boolean {
        if (a.size != b.size) return false
        return a.zip(b).all { p -> p.first.toComparableString() == p.second.toComparableString() }
    }

    /** Self check: two runs of the same engine+fixture (fresh engine each time) should match. */
    fun assertSelfConsistent(
        createEngine: () -> TrainingEngine,
        fixture: FrameFixture
    ) {
        val t1 = run(createEngine(), fixture)
        val t2 = run(createEngine(), fixture)
        require(compare(t1.traces, t2.traces)) {
            "Parity self-check failed: ${t1.traces} vs ${t2.traces}"
        }
    }
}
