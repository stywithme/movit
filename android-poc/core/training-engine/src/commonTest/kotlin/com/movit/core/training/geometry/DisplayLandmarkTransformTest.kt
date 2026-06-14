package com.movit.core.training.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DisplayLandmarkTransformTest {

    @Test
    fun fillCenter_mapsAnalysisCenterToViewCenter() {
        val transform = DisplayLandmarkTransform.fromLayout(
            layout = CameraFrameLayout(
                analysisWidth = 640,
                analysisHeight = 480,
                previewWidth = 1080,
                previewHeight = 2400,
            ),
            isFrontCamera = false,
            scaleMode = DisplayScaleMode.FILL_CENTER,
        )
        val (px, py) = transform.mapNormalized(0.5f, 0.5f)
        assertEquals(540f, px, 0.01f)
        assertEquals(1200f, py, 0.01f)
    }

    @Test
    fun fillCenter_cropsHorizontalBarsOnTallView() {
        val transform = DisplayLandmarkTransform.fromLayout(
            layout = CameraFrameLayout(640, 480, 1080, 2400),
            isFrontCamera = false,
        )
        assertTrue(transform.offsetX < 0f)
        assertEquals(0f, transform.offsetY, 0.01f)
        assertEquals(5f, transform.scale, 0.01f)
    }

    @Test
    fun fitCenter_letterboxesWithoutNegativeOffset() {
        val transform = DisplayLandmarkTransform.fromLayout(
            layout = CameraFrameLayout(640, 480, 1080, 2400),
            isFrontCamera = false,
            scaleMode = DisplayScaleMode.FIT_CENTER,
        )
        assertTrue(transform.offsetX >= 0f)
        assertTrue(transform.offsetY >= 0f)
        val (px, py) = transform.mapNormalized(0.5f, 0.5f)
        assertEquals(540f, px, 0.01f)
        assertEquals(1200f, py, 0.01f)
    }

    @Test
    fun mirrorX_flipsNormalizedXForFrontCameraPreview() {
        val transform = DisplayLandmarkTransform.fromLayout(
            layout = CameraFrameLayout(100, 100, 100, 100),
            isFrontCamera = true,
        )
        val (left, _) = transform.mapNormalized(0.2f, 0.5f)
        val (right, _) = transform.mapNormalized(0.8f, 0.5f)
        assertEquals(80f, left, 0.01f)
        assertEquals(20f, right, 0.01f)
    }

    @Test
    fun mapNormalized_topLeftCornerAtCropEdge() {
        val transform = DisplayLandmarkTransform.fromLayout(
            layout = CameraFrameLayout(640, 480, 1080, 2400),
            isFrontCamera = false,
        )
        val (px, py) = transform.mapNormalized(0f, 0f)
        assertEquals(transform.offsetX, px, 0.01f)
        assertEquals(transform.offsetY, py, 0.01f)
    }
}
