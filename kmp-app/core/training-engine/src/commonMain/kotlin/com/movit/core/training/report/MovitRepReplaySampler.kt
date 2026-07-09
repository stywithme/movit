package com.movit.core.training.report

import com.movit.core.training.engine.currentTimeMillis

/**
 * Common replay burst registry — mirrors Legacy [FrameCaptureManager] replay limits without bitmap I/O.
 * Platform code persists JPEGs and registers frame paths here.
 */
class MovitRepReplaySampler(
    private val timeProvider: () -> Long = { currentTimeMillis() },
) {
    private data class RepCaptureKey(val setNumber: Int, val repNumber: Int)

    private val replayFramesByRep = linkedMapOf<RepCaptureKey, MutableList<MovitReplayFrameRef>>()
    private val replayStartTimesByRep = mutableMapOf<RepCaptureKey, Long>()

    fun canSample(repNumber: Int, setNumber: Int = 1): Boolean {
        if (repNumber < 1) return false
        val list = replayFramesByRep[RepCaptureKey(setNumber, repNumber)]
        return list == null || list.size < MAX_FRAMES_PER_REP
    }

    fun tryRegisterFrame(
        repNumber: Int,
        frameUri: String,
        setNumber: Int = 1,
        capturedAtMs: Long = timeProvider(),
    ): Boolean {
        if (repNumber < 1) return false
        val repKey = RepCaptureKey(setNumber, repNumber)
        val list = replayFramesByRep.getOrPut(repKey) { mutableListOf() }
        if (list.size >= MAX_FRAMES_PER_REP) return false
        val startedAt = replayStartTimesByRep.getOrPut(repKey) { capturedAtMs }
        list += MovitReplayFrameRef(
            frameUri = frameUri,
            offsetMs = capturedAtMs - startedAt,
        )
        enforceRollingWindow(keepRep = repKey)
        return true
    }

    fun clipForRep(repNumber: Int, setNumber: Int = 1): MovitRepReplayClip? {
        val frames = replayFramesByRep[RepCaptureKey(setNumber, repNumber)] ?: return null
        if (frames.size < MIN_FRAMES_FOR_CLIP) return null
        return MovitRepReplayClip(repNumber = repNumber, setNumber = setNumber, frames = frames.toList())
    }

    fun clips(setNumber: Int? = null): List<MovitRepReplayClip> =
        replayFramesByRep.keys
            .filter { setNumber == null || it.setNumber == setNumber }
            .mapNotNull { clipForRep(it.repNumber, it.setNumber) }

    fun clear() {
        replayFramesByRep.clear()
        replayStartTimesByRep.clear()
    }

    private fun enforceRollingWindow(keepRep: RepCaptureKey) {
        while (replayFramesByRep.size > MAX_TRACKED_REPS) {
            val candidates = replayFramesByRep.keys.filter { it != keepRep }
            if (candidates.isEmpty()) break
            val evictRep = candidates.maxWithOrNull(compareBy<RepCaptureKey> { it.setNumber }.thenBy { it.repNumber })
                ?: break
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
