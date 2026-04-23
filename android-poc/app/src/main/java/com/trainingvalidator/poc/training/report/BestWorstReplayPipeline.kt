package com.trainingvalidator.poc.training.report

import android.util.Log
import java.io.File

/**
 * Single pipeline for Best vs Worst **motion replay** data.
 *
 * Logcat filter (one tag for the whole feature):
 * `POSE_BEST_WORST_REPLAY`
 *
 * Resolution order per rep:
 * 1. **burst** — live-sampled replay from [FrameCaptureManager.captureReplayFrame] (>= 2 frames).
 * 2. **synthetic** — built from existing [FrameCapture] stills for that rep (>= 2 distinct files),
 *    sorted by capture time (coarse motion from key moments, not a video).
 * 3. **none** — UI falls back to still image only.
 */
object BestWorstReplayPipeline {

    const val LOG_TAG = "POSE_BEST_WORST_REPLAY"

    /**
     * Resolve the best playable clip for [repNumber] for the Best/Worst comparison screen.
     */
    fun resolveClipForRep(
        repNumber: Int,
        replayClips: List<RepReplayClip>,
        frameCaptures: List<FrameCapture>
    ): RepReplayClip? {
        val burst = replayClips.firstOrNull { it.repNumber == repNumber }
        if (burst != null && burst.frames.size >= 2) {
            Log.d(
                LOG_TAG,
                "resolve rep=$repNumber source=burst frames=${burst.frames.size} " +
                    "spanMs=${burst.frames.lastOrNull()?.offsetMs ?: 0L}"
            )
            return burst
        }

        val synthetic = buildSyntheticClip(repNumber, frameCaptures)
        if (synthetic != null && synthetic.frames.size >= 2) {
            Log.d(
                LOG_TAG,
                "resolve rep=$repNumber source=synthetic frames=${synthetic.frames.size} " +
                    "(burstFrames=${burst?.frames?.size ?: 0})"
            )
            return synthetic
        }

        Log.d(
            LOG_TAG,
            "resolve rep=$repNumber source=none burstFrames=${burst?.frames?.size ?: 0} " +
                "syntheticFrames=${synthetic?.frames?.size ?: 0} capturesForRep=" +
                frameCaptures.count { it.repNumber == repNumber }
        )
        return null
    }

    /**
     * Build a minimal clip from saved [FrameCapture]s for this rep (distinct files, time order).
     */
    private fun buildSyntheticClip(repNumber: Int, frameCaptures: List<FrameCapture>): RepReplayClip? {
        val sorted = frameCaptures
            .filter { it.repNumber == repNumber }
            .sortedBy { it.timestamp }

        val pathsWithTime = sorted.mapNotNull { fc ->
            val path = when {
                fc.frameUri.isNotBlank() && File(fc.frameUri).exists() -> fc.frameUri
                fc.thumbnailUri.isNotBlank() && File(fc.thumbnailUri).exists() -> fc.thumbnailUri
                else -> null
            }
            path?.let { it to fc.timestamp }
        }

        // Drop consecutive duplicates (same file saved twice)
        val deduped = mutableListOf<Pair<String, Long>>()
        for ((path, ts) in pathsWithTime) {
            if (deduped.lastOrNull()?.first == path) continue
            deduped.add(path to ts)
        }

        if (deduped.size < 2) return null

        val t0 = deduped.first().second
        val frames = deduped.map { (path, ts) ->
            ReplayFrameRef(frameUri = path, offsetMs = (ts - t0).coerceAtLeast(0L))
        }
        return RepReplayClip(repNumber = repNumber, frames = frames)
    }
}
