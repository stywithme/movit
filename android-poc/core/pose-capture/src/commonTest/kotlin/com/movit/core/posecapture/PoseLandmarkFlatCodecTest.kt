package com.movit.core.posecapture

import com.movit.core.training.model.Landmark
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PoseLandmarkFlatCodecTest {
    @Test
    fun roundTrip_preservesLandmarks() {
        val landmarks = List(PoseLandmarkFlatCodec.LANDMARK_COUNT) { index ->
            Landmark(
                x = index * 0.01f,
                y = index * 0.02f,
                z = index * 0.03f,
                visibility = 0.9f,
                presence = 0.8f,
            )
        }
        val flat = PoseLandmarkFlatCodec.encode(landmarks)
        assertEquals(PoseLandmarkFlatCodec.FLAT_SIZE, flat.size)
        val decoded = PoseLandmarkFlatCodec.decode(flat)
        assertEquals(landmarks, decoded)
    }

    @Test
    fun decodeOptional_returnsNullForShortArray() {
        assertNull(PoseLandmarkFlatCodec.decodeOptional(FloatArray(10)))
    }
}
