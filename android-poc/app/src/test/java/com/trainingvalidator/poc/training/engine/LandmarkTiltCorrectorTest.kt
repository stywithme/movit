package com.trainingvalidator.poc.training.engine

import com.trainingvalidator.poc.analysis.SmoothedLandmark
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI

class LandmarkTiltCorrectorTest {

    @Test
    fun `zero rotation returns same coordinates`() {
        val landmarks = listOf(point(0.25f, 0.75f))

        val corrected = LandmarkTiltCorrector.correct(landmarks, 0f)

        assertEquals(0.25f, corrected[0].x, 0.0001f)
        assertEquals(0.75f, corrected[0].y, 0.0001f)
    }

    @Test
    fun `ninety degree rotation around image center is stable`() {
        val landmarks = listOf(point(0.75f, 0.50f))

        val corrected = LandmarkTiltCorrector.correct(landmarks, (PI / 2.0).toFloat())

        assertEquals(0.50f, corrected[0].x, 0.0001f)
        assertEquals(0.75f, corrected[0].y, 0.0001f)
    }

    @Test
    fun `full rotation returns original coordinates`() {
        val landmarks = listOf(point(0.20f, 0.80f))

        val corrected = LandmarkTiltCorrector.correct(landmarks, (2.0 * PI).toFloat())

        assertEquals(0.20f, corrected[0].x, 0.0001f)
        assertEquals(0.80f, corrected[0].y, 0.0001f)
    }

    private fun point(x: Float, y: Float) = SmoothedLandmark(
        x = x,
        y = y,
        z = 0f,
        visibility = 1f,
        presence = 1f
    )
}
