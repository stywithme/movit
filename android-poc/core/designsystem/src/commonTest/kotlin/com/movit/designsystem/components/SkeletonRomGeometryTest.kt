package com.movit.designsystem.components

import kotlin.test.Test
import kotlin.test.assertEquals

class SkeletonRomGeometryTest {
    @Test
    fun jointAngleToCanvas_mapsStraightDownToSouth() {
        assertEquals(90f, SkeletonRomGeometry.jointAngleToCanvasDegrees(0.0))
    }

    @Test
    fun jointAngleToCanvas_mapsFoldedToNorth() {
        assertEquals(-90f, SkeletonRomGeometry.jointAngleToCanvasDegrees(180.0))
    }

    @Test
    fun arcSweep_mapsJointRangeToCanvasSweep() {
        val (start, sweep) = SkeletonRomGeometry.arcSweepForJointRange(60.0, 120.0)
        assertEquals(-30f, start, absoluteTolerance = 0.01f)
        assertEquals(-60f, sweep, absoluteTolerance = 0.01f)
    }

    @Test
    fun lineProgress_clampsToUnitInterval() {
        assertEquals(10f / 180f, SkeletonRomGeometry.lineProgress(10f, 50f, 100f, 0f, 180f), 0.001f)
        assertEquals(1f, SkeletonRomGeometry.lineProgress(200f, 50f, 100f, 0f, 180f))
        assertEquals(0.5f, SkeletonRomGeometry.lineProgress(90f, 50f, 100f, 0f, 180f), 0.001f)
    }
}
