package com.movit.designsystem.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DebugOverlayLandmarkProjectorTest {

    @Test
    fun fillCenter_mapsAnalysisCenterToViewCenter() {
        val projector = DebugOverlayLandmarkProjector(
            imageWidth = 640,
            imageHeight = 480,
            mirrorX = false,
            scaleMode = DebugOverlayScaleMode.FILL_CENTER,
        )
        val offset = projector.project(0.5f, 0.5f, canvasWidth = 1080f, canvasHeight = 2400f)
        assertEquals(540f, offset.x, 0.01f)
        assertEquals(1200f, offset.y, 0.01f)
    }

    @Test
    fun fillCenter_cropsHorizontalBarsOnTallView() {
        val projector = DebugOverlayLandmarkProjector(
            imageWidth = 640,
            imageHeight = 480,
            mirrorX = false,
            scaleMode = DebugOverlayScaleMode.FILL_CENTER,
        )
        val topLeft = projector.project(0f, 0f, canvasWidth = 1080f, canvasHeight = 2400f)
        val scale = 2400f / 480f
        val offsetX = (1080f - 640f * scale) / 2f
        assertEquals(offsetX, topLeft.x, 0.01f)
        assertTrue(offsetX < 0f)
        assertEquals(0f, topLeft.y, 0.01f)
    }

    @Test
    fun fitCenter_letterboxesWithoutNegativeOffset() {
        val projector = DebugOverlayLandmarkProjector(
            imageWidth = 640,
            imageHeight = 480,
            mirrorX = false,
            scaleMode = DebugOverlayScaleMode.FIT_CENTER,
        )
        val center = projector.project(0.5f, 0.5f, canvasWidth = 1080f, canvasHeight = 2400f)
        assertEquals(540f, center.x, 0.01f)
        assertEquals(1200f, center.y, 0.01f)
        val topLeft = projector.project(0f, 0f, canvasWidth = 1080f, canvasHeight = 2400f)
        assertTrue(topLeft.x >= 0f)
        assertTrue(topLeft.y >= 0f)
    }

    @Test
    fun mirrorX_flipsNormalizedXForFrontCameraPreview() {
        val projector = DebugOverlayLandmarkProjector(
            imageWidth = 100,
            imageHeight = 100,
            mirrorX = true,
            scaleMode = DebugOverlayScaleMode.FILL_CENTER,
        )
        val left = projector.project(0.2f, 0.5f, canvasWidth = 100f, canvasHeight = 100f)
        val right = projector.project(0.8f, 0.5f, canvasWidth = 100f, canvasHeight = 100f)
        assertEquals(80f, left.x, 0.01f)
        assertEquals(20f, right.x, 0.01f)
    }

    @Test
    fun stretchFallback_mirrorsXWhenImageDimensionsUnknown() {
        val projector = DebugOverlayLandmarkProjector(
            imageWidth = 0,
            imageHeight = 0,
            mirrorX = true,
            scaleMode = DebugOverlayScaleMode.FILL_CENTER,
        )
        val point = projector.project(0.25f, 0.5f, canvasWidth = 400f, canvasHeight = 800f)
        assertEquals(300f, point.x, 0.01f)
        assertEquals(400f, point.y, 0.01f)
    }

    @Test
    fun sweepAngleDegrees_usesSmallerArc() {
        assertEquals(90f, sweepAngleDegrees(0f, 90f), 0.01f)
        assertEquals(-90f, sweepAngleDegrees(90f, 0f), 0.01f)
        assertEquals(20f, sweepAngleDegrees(350f, 10f), 0.01f)
    }
}
