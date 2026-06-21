package com.movit.core.training.report

import com.movit.core.training.engine.currentTimeMillis

/**
 * Common replay burst registry — mirrors Legacy [FrameCaptureManager] replay limits without bitmap I/O.
 * Platform code persists JPEGs and registers frame paths here.
 */
class MovitRepReplaySampler(
    private val timeProvider: () -> Long = { currentTimeMillis() },
) {
    private val replayFramesByRep = linkedMapOf<Int, MutableList<MovitReplayFrameRef>>()
    private val replayStartTimesByRep = mutableMapOf<Int, Long>()

    fun canSample(repNumber: Int): Boolean {
        if (repNumber < 1) return false
        val list = replayFramesByRep[repNumber]
        return list == null || list.size < MAX_FRAMES_PER_REP
    }

    fun tryRegisterFrame(
        repNumber: Int,
        frameUri: String,
        capturedAtMs: Long = timeProvider(),
    ): Boolean {
        if (repNumber < 1) return false
        val list = replayFramesByRep.getOrPut(repNumber) { mutableListOf() }
        if (list.size >= MAX_FRAMES_PER_REP) return false
        val startedAt = replayStartTimesByRep.getOrPut(repNumber) { capturedAtMs }
        list += MovitReplayFrameRef(
            frameUri = frameUri,
            offsetMs = capturedAtMs - startedAt,
        )
        enforceRollingWindow(keepRep = repNumber)
        return true
    }

    fun clipForRep(repNumber: Int): MovitRepReplayClip? {
        val frames = replayFramesByRep[repNumber] ?: return null
        if (frames.size < MIN_FRAMES_FOR_CLIP) return null
        return MovitRepReplayClip(repNumber = repNumber, frames = frames.toList())
    }

    fun clips(): List<MovitRepReplayClip> =
        replayFramesByRep.keys.mapNotNull(::clipForRep)

    fun clear() {
        replayFramesByRep.clear()
        replayStartTimesByRep.clear()
    }

    private fun enforceRollingWindow(keepRep: Int) {
        while (replayFramesByRep.size > MAX_TRACKED_REPS) {
            val candidates = replayFramesByRep.keys.filter { it != keepRep }
            if (candidates.isEmpty()) break
            val evictRep = candidates.maxOrNull() ?: break
            replayFramesByRep.remove(evictRep)
            replayStartTimesByRep.remove(evictRep)
        }
    }

    companion object {
        const val SAMPLE_INTERVAL_MS = 180L
        const val MAX_FRAMES_PER_REP = 16
        const val MAX_TRACKED_REPS = 10
        const val MIN_FRAMES_FOR_CLIP = 2
    }
}
