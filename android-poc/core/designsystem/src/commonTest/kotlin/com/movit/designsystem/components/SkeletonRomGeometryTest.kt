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

    @Test
    fun resolvedState_usesUpDownRangesAndTransitionGap() {
        val upRange = SkeletonRomStateRanges(
            perfect = SkeletonRomAngleRange(150f, 170f),
            normal = SkeletonRomAngleRange(140f, 180f),
        )
        val downRange = SkeletonRomStateRanges(
            perfect = SkeletonRomAngleRange(30f, 50f),
            normal = SkeletonRomAngleRange(20f, 60f),
        )

        assertEquals(
            SkeletonRomState.PERFECT,
            SkeletonRomGeometry.resolvedStateForAngle(40f, upRange, downRange),
        )
        assertEquals(
            SkeletonRomState.TRANSITION,
            SkeletonRomGeometry.resolvedStateForAngle(100f, upRange, downRange),
        )
        assertEquals(
            SkeletonRomState.NORMAL,
            SkeletonRomGeometry.resolvedStateForAngle(175f, upRange, downRange),
        )
    }

    @Test
    fun stateColorArgb_usesMovitSemanticTrainingColors() {
        assertEquals(0xFFC4D489, SkeletonRomGeometry.stateColorArgb(SkeletonRomState.PERFECT))
        assertEquals(0xFF8ECFE3, SkeletonRomGeometry.stateColorArgb(SkeletonRomState.NORMAL))
        assertEquals(0xFF8ECFE3, SkeletonRomGeometry.stateColorArgb(SkeletonRomState.TRANSITION))
        assertEquals(0xFFE76D46, SkeletonRomGeometry.stateColorArgb(SkeletonRomState.WARNING))
        assertEquals(0xFFC62828, SkeletonRomGeometry.stateColorArgb(SkeletonRomState.DANGER))
    }
}
