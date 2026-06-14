package com.movit.feature.training

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SkeletonOverlayMapperTest {

    @Test
    fun projector_mapsCenterLandmarkToViewCenter() {
        val project = skeletonLandmarkProjector(
            analysisWidth = 640,
            analysisHeight = 480,
            mirrorPreview = false,
        )
        assertNotNull(project)
        val point = project(0.5f, 0.5f, 1080f, 2400f)
        assertEquals(540f, point.x, 0.01f)
        assertEquals(1200f, point.y, 0.01f)
    }

    @Test
    fun projector_returnsNullWhenAnalysisDimensionsMissing() {
        assertEquals(null, skeletonLandmarkProjector(0, 480, mirrorPreview = true))
    }
}
