package com.movit.core.training.report

import com.movit.core.training.engine.currentTimeMillis

/**
 * KMP v1 frame-evidence registry — mirrors Legacy [FrameCaptureManager] limits without bitmap I/O.
 * Platform code persists JPEGs and registers [MovitPeakFrameCapture] references here.
 */
class MovitPeakFrameCaptureManager(
    private val timeProvider: () -> Long = { currentTimeMillis() },
    private val idGenerator: () -> String = { "cap-${currentTimeMillis()}" },
) {
    data class RegisterRequest(
        val repNumber: Int,
        val setNumber: Int = 1,
        val phaseCode: Byte,
        val captureType: MovitPeakCaptureType,
        val localPath: String,
        val thumbnailPath: String? = null,
        val errorKey: String? = null,
        val angles: Map<String, Double> = emptyMap(),
        val capturedAtMs: Long? = null,
        val id: String? = null,
    )

    private data class RepCaptureKey(val setNumber: Int, val repNumber: Int)

    private val captures = mutableListOf<MovitPeakFrameCapture>()
    private val peakByRep = mutableMapOf<RepCaptureKey, MovitPeakFrameCapture>()
    private val errorsByRep = mutableMapOf<RepCaptureKey, MutableSet<String>>()
    private val lastErrorCaptureAt = mutableMapOf<String, Long>()

    private var dangerCount = 0
    private var lastDangerCaptureAt = 0L
    private var bestRepCount = 0
    private var holdSampleCount = 0

    fun canCapture(
        captureType: MovitPeakCaptureType,
        repNumber: Int,
        setNumber: Int = 1,
        errorKey: String? = null,
        nowMs: Long = timeProvider(),
    ): Boolean = when (captureType) {
        MovitPeakCaptureType.DANGER_FRAME -> {
            dangerCount < MAX_DANGER_FRAMES && (
                lastDangerCaptureAt == 0L ||
                    nowMs - lastDangerCaptureAt >= DANGER_CAPTURE_COOLDOWN_MS
                )
        }
        MovitPeakCaptureType.PEAK_FRAME -> !peakByRep.containsKey(RepCaptureKey(setNumber, repNumber))
        MovitPeakCaptureType.ERROR_FRAME -> {
            val key = errorKey ?: return false
            val repKey = RepCaptureKey(setNumber, repNumber)
            val perRep = errorsByRep.getOrPut(repKey) { mutableSetOf() }
            if (key in perRep) return false
            val cooldownKey = "$setNumber:$repNumber:$key"
            val lastCapture = lastErrorCaptureAt[cooldownKey]
            lastCapture == null || nowMs - lastCapture >= ERROR_CAPTURE_COOLDOWN_MS
        }
        MovitPeakCaptureType.HOLD_SAMPLE -> holdSampleCount < MAX_HOLD_SAMPLES
        MovitPeakCaptureType.BEST_REP -> {
            bestRepCount < MAX_BEST_REPS && peakByRep.containsKey(RepCaptureKey(setNumber, repNumber))
        }
    }

    fun tryRegister(request: RegisterRequest): MovitPeakFrameCapture? {
        val capturedAtMs: Long = request.capturedAtMs ?: timeProvider()
        if (!canCapture(request.captureType, request.repNumber, request.setNumber, request.errorKey, capturedAtMs)) {
            return null
        }
        val captureId: String = request.id ?: idGenerator()
        val repKey = RepCaptureKey(request.setNumber, request.repNumber)
        val hasError = request.captureType == MovitPeakCaptureType.DANGER_FRAME ||
            request.captureType == MovitPeakCaptureType.ERROR_FRAME
        val capture = MovitPeakFrameCapture(
            id = captureId,
            repNumber = request.repNumber,
            setNumber = request.setNumber,
            phaseCode = request.phaseCode,
            capturedAtMs = capturedAtMs,
            captureType = request.captureType,
            localPath = request.localPath,
            thumbnailPath = request.thumbnailPath,
            errorType = request.errorKey,
            metadata = MovitFrameCaptureMetadata(
                angles = request.angles,
                hasError = hasError,
                errorDetails = if (hasError) request.errorKey else null,
            ),
        )
        when (request.captureType) {
            MovitPeakCaptureType.DANGER_FRAME -> {
                dangerCount++
                lastDangerCaptureAt = capturedAtMs
            }
            MovitPeakCaptureType.PEAK_FRAME -> peakByRep[repKey] = capture
            MovitPeakCaptureType.ERROR_FRAME -> {
                val key = request.errorKey ?: return null
                errorsByRep.getOrPut(repKey) { mutableSetOf() }.add(key)
                lastErrorCaptureAt["${request.setNumber}:${request.repNumber}:$key"] = capturedAtMs
            }
            MovitPeakCaptureType.HOLD_SAMPLE -> holdSampleCount++
            MovitPeakCaptureType.BEST_REP -> {
                bestRepCount++
            }
        }
        captures += capture
        return capture
    }

    fun markBestRep(repNumber: Int, setNumber: Int = 1): Boolean {
        val repKey = RepCaptureKey(setNumber, repNumber)
        val peak = peakByRep[repKey] ?: return false
        if (peak.captureType == MovitPeakCaptureType.BEST_REP) return false
        if (bestRepCount >= MAX_BEST_REPS) return false
        val index = captures.indexOfFirst { it.id == peak.id }
        if (index < 0) return false
        val updated = peak.copy(captureType = MovitPeakCaptureType.BEST_REP)
        captures[index] = updated
        peakByRep[repKey] = updated
        bestRepCount++
        return true
    }

    fun captures(): List<MovitPeakFrameCapture> = captures.toList()

    fun clear() {
        captures.clear()
        peakByRep.clear()
        errorsByRep.clear()
        lastErrorCaptureAt.clear()
        dangerCount = 0
        lastDangerCaptureAt = 0L
        bestRepCount = 0
        holdSampleCount = 0
    }

    private companion object {
        const val MAX_BEST_REPS = 3
        const val MAX_DANGER_FRAMES = 6
        const val MAX_HOLD_SAMPLES = 3
        const val ERROR_CAPTURE_COOLDOWN_MS = 2_000L
        const val DANGER_CAPTURE_COOLDOWN_MS = 1_000L
    }
}
