package com.movit.core.training.journal

import com.movit.core.training.engine.JointState
import com.movit.core.training.engine.Phase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MetricsCalculatorTest {
    @Test
    fun calculateROM_returnsAngleDeltaTimesTen() {
        val frames = listOf(
            frame(t = 0, knee = 900),
            frame(t = 500, knee = 1200),
            frame(t = 1000, knee = 600),
        )
        assertEquals(600, MetricsCalculator.calculateROM(frames, jointIndex = 0))
    }

    @Test
    fun calculateFormConsistencyFromScores_lowVarianceScoresHigh() {
        val scores = listOf(85f, 86f, 84f, 85f, 87f, 86f)
        val result = MetricsCalculator.calculateFormConsistencyFromScores(scores)
        assertNotNull(result)
        assertTrue(result!! > 800)
    }

    @Test
    fun motionRecorder_finalize_producesUploadMatchingDtoScale() {
        val recorder = MotionRecorder(
            trackedJoints = listOf("left_knee"),
            exerciseId = "bodyweight-squat",
            timeProvider = { 1_000L },
            idGenerator = { "exec-test-1" },
        )
        recorder.start(1_000L)
        recorder.record(
            timestamp = 1_020L,
            phase = Phase.DOWN,
            angles = mapOf("left_knee" to 90.0),
        )
        recorder.record(
            timestamp = 1_200L,
            phase = Phase.UP,
            angles = mapOf("left_knee" to 150.0),
        )
        recorder.finalizeRep(
            repNumber = 1,
            phaseTimings = mapOf("down" to 800L, "up" to 700L),
            worstState = JointState.NORMAL,
            score = 85f,
        )
        val upload = recorder.finalize(endTimestampMs = 2_000L)
        assertEquals(1, upload.totalReps)
        assertEquals("exec-test-1", upload.id)
        assertEquals(850, upload.executionMetrics.avgFormScore)
    }

    @Test
    fun calculateBilateralRomSymmetry_zeroRom_returnsNull() {
        val result = MetricsCalculator.calculateBilateralRomSymmetry(
            leftRepsRom = listOf(0, 0),
            rightRepsRom = listOf(0, 0),
        )
        assertEquals(null, result)
    }

    @Test
    fun calculateTrunkStability_linearTrend_scoresHigherThanRawSpread() {
        val frames = (0 until 10).map { index ->
            frame(t = index * 100, knee = 900 + index * 50)
        }
        val spineIndex = 0
        val detrended = MetricsCalculator.calculateTrunkStability(frames, spineIndex)
        assertNotNull(detrended)
        assertTrue(detrended!! > 800)
    }

    @Test
    fun calculateTrunkStability_linearTrendWithNoise_scoresLowerThanSmoothTrend() {
        val smooth = (0 until 12).map { index ->
            frame(t = index * 100, knee = 900 + index * 40)
        }
        val noisy = smooth.mapIndexed { index, sample ->
            val jitter = if (index % 3 == 0) 120 else 0
            frame(t = sample.t, knee = sample.angles[0].toInt() + jitter)
        }
        val smoothScore = MetricsCalculator.calculateTrunkStability(smooth, 0)!!
        val noisyScore = MetricsCalculator.calculateTrunkStability(noisy, 0)!!
        assertTrue(smoothScore > noisyScore)
    }

    @Test
    fun calculateAlignmentAccuracy_expandsCompressedRepeatedStates() {
        val frames = (0 until 10).map { index ->
            frame(
                t = index * 100,
                knee = 900,
                states = when (index) {
                    0 -> byteArrayOf(StateCode.NORMAL)
                    9 -> byteArrayOf(StateCode.DANGER)
                    else -> null
                },
            )
        }

        assertEquals(900, MetricsCalculator.calculateAlignmentAccuracy(frames))
    }

    private fun frame(t: Int, knee: Int, states: ByteArray? = null): FrameSample = FrameSample(
        t = t,
        phase = PhaseCode.ECCENTRIC,
        angles = shortArrayOf(knee.toShort()),
        states = states,
    )
}
