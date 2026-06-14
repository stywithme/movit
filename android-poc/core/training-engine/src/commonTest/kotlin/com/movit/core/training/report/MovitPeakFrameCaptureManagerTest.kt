package com.movit.core.training.report

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MovitPeakFrameCaptureManagerTest {

    private var nowMs = 1_000L
    private var idSeq = 0

    private fun manager() = MovitPeakFrameCaptureManager(
        timeProvider = { nowMs },
        idGenerator = { "cap-${++idSeq}" },
    )

    @Test
    fun peakFrame_allowsOnePerRep() {
        val mgr = manager()
        val first = mgr.tryRegister(sampleRequest(repNumber = 1, type = MovitPeakCaptureType.PEAK_FRAME))
        val second = mgr.tryRegister(sampleRequest(repNumber = 1, type = MovitPeakCaptureType.PEAK_FRAME))
        assertNotNull(first)
        assertNull(second)
        assertEquals(1, mgr.captures().size)
    }

    @Test
    fun dangerFrame_respectsMaxAndCooldown() {
        val mgr = manager()
        repeat(6) { index ->
            nowMs += 2_000L
            assertNotNull(
                mgr.tryRegister(
                    sampleRequest(
                        repNumber = index + 1,
                        type = MovitPeakCaptureType.DANGER_FRAME,
                        errorKey = "knee:DANGER",
                    ),
                ),
            )
        }
        nowMs += 2_000L
        assertNull(
            mgr.tryRegister(
                sampleRequest(
                    repNumber = 99,
                    type = MovitPeakCaptureType.DANGER_FRAME,
                    errorKey = "knee:DANGER",
                ),
            ),
        )
        assertEquals(6, mgr.captures().size)
    }

    @Test
    fun errorFrame_dedupesPerRepAndErrorKey() {
        val mgr = manager()
        assertNotNull(
            mgr.tryRegister(
                sampleRequest(
                    repNumber = 2,
                    type = MovitPeakCaptureType.ERROR_FRAME,
                    errorKey = "left_knee:WARNING",
                ),
            ),
        )
        nowMs += 3_000L
        assertNull(
            mgr.tryRegister(
                sampleRequest(
                    repNumber = 2,
                    type = MovitPeakCaptureType.ERROR_FRAME,
                    errorKey = "left_knee:WARNING",
                ),
            ),
        )
        nowMs += 3_000L
        assertNotNull(
            mgr.tryRegister(
                sampleRequest(
                    repNumber = 3,
                    type = MovitPeakCaptureType.ERROR_FRAME,
                    errorKey = "left_knee:WARNING",
                ),
            ),
        )
    }

    @Test
    fun markBestRep_updatesPeakCaptureType() {
        val mgr = manager()
        mgr.tryRegister(sampleRequest(repNumber = 4, type = MovitPeakCaptureType.PEAK_FRAME))
        assertTrue(mgr.markBestRep(4))
        assertEquals(MovitPeakCaptureType.BEST_REP, mgr.captures().single().captureType)
    }

    @Test
    fun markBestRep_respectsMaxBestReps() {
        val mgr = manager()
        repeat(3) { rep ->
            mgr.tryRegister(sampleRequest(repNumber = rep + 1, type = MovitPeakCaptureType.PEAK_FRAME))
            assertTrue(mgr.markBestRep(rep + 1))
        }
        mgr.tryRegister(sampleRequest(repNumber = 4, type = MovitPeakCaptureType.PEAK_FRAME))
        assertFalse(mgr.markBestRep(4))
    }

    @Test
    fun tryRegister_persistsAnglesAndErrorMetadata() {
        val mgr = manager()
        val capture = mgr.tryRegister(
            sampleRequest(
                repNumber = 2,
                type = MovitPeakCaptureType.ERROR_FRAME,
                errorKey = "left_knee:WARNING",
                angles = mapOf("left_knee" to 118.5, "right_knee" to 120.0),
            ),
        )
        assertNotNull(capture)
        assertEquals("left_knee:WARNING", capture.errorType)
        assertTrue(capture.metadata.hasError)
        assertEquals("left_knee:WARNING", capture.metadata.errorDetails)
        assertEquals(118.5, capture.metadata.angles["left_knee"])
    }

    @Test
    fun tryRegister_peakFrame_hasAnglesWithoutErrorFlag() {
        val mgr = manager()
        val capture = mgr.tryRegister(
            sampleRequest(
                repNumber = 1,
                type = MovitPeakCaptureType.PEAK_FRAME,
                angles = mapOf("left_hip" to 95.0),
            ),
        )
        assertNotNull(capture)
        assertFalse(capture.metadata.hasError)
        assertNull(capture.metadata.errorDetails)
        assertEquals(95.0, capture.metadata.angles["left_hip"])
    }

    private fun sampleRequest(
        repNumber: Int,
        type: MovitPeakCaptureType,
        errorKey: String? = null,
        angles: Map<String, Double> = emptyMap(),
    ): MovitPeakFrameCaptureManager.RegisterRequest = MovitPeakFrameCaptureManager.RegisterRequest(
        repNumber = repNumber,
        phaseCode = 3,
        captureType = type,
        localPath = "/tmp/frame-$repNumber.jpg",
        thumbnailPath = "/tmp/frame-$repNumber-thumb.jpg",
        errorKey = errorKey,
        angles = angles,
        capturedAtMs = nowMs,
        id = "cap-$repNumber-${type.name}",
    )
}
