package com.movit.feature.trainingdebug.lab

import com.movit.core.training.model.Landmark
import com.movit.core.training.model.PoseLandmarkIndices
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Validates the M-E harness itself on synthetic captures, so that the day real recordings
 * arrive the only unknown is the answer — not whether the tool measures correctly.
 */
class ElbowMethodComparatorTest {

    @Test
    fun crossCameraSpread_isZero_whenAMethodIsCameraIndependent() {
        // Same true angle, four camera positions, and the *projection* is kept identical:
        // any method reading the normalized landmarks must report one stable answer.
        val recordings = CAMERA_POSITIONS.map { position ->
            recording(id = "stable-$position", cameraPosition = position, trueAngleDeg = 90.0, depth = 0f)
        }

        val report = ElbowMethodComparator.compare(recordings)
        val raw2D = report.metrics[ElbowLabMethod.RAW_2D]

        assertNotNull(raw2D)
        assertTrue(raw2D.crossCameraSpread < 0.5, "spread was ${raw2D.crossCameraSpread}")
    }

    @Test
    fun crossCameraSpread_growsWhenTheAnswerDependsOnCameraPosition() {
        // Depth grows with each camera position — exactly the failure the track exists to fix.
        val recordings = CAMERA_POSITIONS.mapIndexed { index, position ->
            recording(
                id = "swinging-$position",
                cameraPosition = position,
                trueAngleDeg = 90.0,
                depth = 0.15f * index,
            )
        }

        val report = ElbowMethodComparator.compare(recordings)
        val raw3D = report.metrics[ElbowLabMethod.RAW_3D]

        assertNotNull(raw3D)
        assertTrue(raw3D.crossCameraSpread > 1.0, "spread was ${raw3D.crossCameraSpread}")
    }

    @Test
    fun report_coversEveryMethodAndCountsFrames() {
        val recordings = listOf(recording("one", "front", 90.0, depth = 0f))

        val report = ElbowMethodComparator.compare(recordings)

        assertEquals(1, report.recordingCount)
        assertEquals(FRAMES_PER_RECORDING, report.frameCount)
        for (method in ElbowLabMethod.entries) {
            val metrics = report.metrics[method]
            assertNotNull(metrics, "missing metrics for $method")
            assertTrue(metrics.samples > 0, "$method produced no samples")
        }
        assertTrue(report.formatTable().contains("ARBITER"))
    }

    @Test
    fun arbiterMethods_reportConfidence() {
        val recordings = listOf(recording("one", "front", 90.0, depth = 0.5f))

        val report = ElbowMethodComparator.compare(recordings)

        assertNotNull(report.metrics[ElbowLabMethod.ARBITER]?.meanConfidence)
        assertEquals(null, report.metrics[ElbowLabMethod.RAW_2D]?.meanConfidence)
    }

    @Test
    fun codec_roundTripsARecording() {
        val original = listOf(recording("round-trip", "side", 63.5, depth = 0.2f))

        val decoded = ElbowLabRecordingCodec.decode(ElbowLabRecordingCodec.encode(original))

        assertEquals(1, decoded.size)
        assertEquals(original.first().id, decoded.first().id)
        assertEquals(original.first().trueAngleDeg, decoded.first().trueAngleDeg)
        assertEquals(original.first().frames.size, decoded.first().frames.size)
        assertEquals(
            original.first().frames.first().normalized[PoseLandmarkIndices.LEFT_WRIST].x,
            decoded.first().frames.first().normalized[PoseLandmarkIndices.LEFT_WRIST].x,
        )
    }

    private companion object {
        val CAMERA_POSITIONS = listOf("front", "front_side", "side", "front_behind")
        const val FRAMES_PER_RECORDING = 40

        /**
         * Builds a still capture: the projected elbow angle is fixed at [trueAngleDeg], while
         * [depth] pushes the world landmarks out of the image plane to simulate a camera that
         * sees the same pose from a more oblique position.
         */
        fun recording(
            id: String,
            cameraPosition: String,
            trueAngleDeg: Double,
            depth: Float,
        ): ElbowLabRecording {
            val theta = trueAngleDeg * PI / 180.0
            val frames = (0 until FRAMES_PER_RECORDING).map { index ->
                val normalized = MutableList(33) { Landmark(0.5f, 0.5f, 0f, 1f, 1f) }
                normalized[PoseLandmarkIndices.LEFT_ELBOW] = Landmark(0.50f, 0.50f, 0f, 1f, 1f)
                normalized[PoseLandmarkIndices.LEFT_SHOULDER] = Landmark(0.50f, 0.30f, 0f, 1f, 1f)
                normalized[PoseLandmarkIndices.LEFT_WRIST] = Landmark(
                    (0.50 + 0.20 * sin(theta)).toFloat(),
                    (0.50 - 0.20 * cos(theta)).toFloat(),
                    0f,
                    1f,
                    1f,
                )
                normalized[PoseLandmarkIndices.RIGHT_SHOULDER] = Landmark(0.65f, 0.30f, 0f, 1f, 1f)

                val world = MutableList(33) { Landmark(0.5f, 0.5f, 0f, 1f, 1f) }
                world[PoseLandmarkIndices.LEFT_ELBOW] = Landmark(0f, 0f, 0f, 1f, 1f)
                world[PoseLandmarkIndices.LEFT_SHOULDER] = Landmark(0f, 1f, -depth, 1f, 1f)
                world[PoseLandmarkIndices.LEFT_WRIST] = Landmark(
                    sin(theta).toFloat(),
                    cos(theta).toFloat(),
                    depth,
                    1f,
                    1f,
                )
                world[PoseLandmarkIndices.RIGHT_SHOULDER] = Landmark(0.4f, 1f, -depth, 1f, 1f)
                world[PoseLandmarkIndices.LEFT_HIP] = Landmark(0f, -0.5f, 0f, 1f, 1f)
                world[PoseLandmarkIndices.RIGHT_HIP] = Landmark(0.4f, -0.5f, 0f, 1f, 1f)

                ElbowLabFrame(
                    timestampMs = 1_000L + index * 33L,
                    normalized = normalized,
                    world = world,
                )
            }
            return ElbowLabRecording(
                id = id,
                trueAngleDeg = trueAngleDeg,
                cameraPosition = cameraPosition,
                side = ElbowLabSide.LEFT,
                analysisImageWidth = 480,
                analysisImageHeight = 640,
                frames = frames,
            )
        }
    }
}
