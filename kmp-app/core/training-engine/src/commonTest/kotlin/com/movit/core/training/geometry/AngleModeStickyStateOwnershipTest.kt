package com.movit.core.training.geometry

import com.movit.core.training.model.Landmark
import com.movit.core.training.model.PoseLandmarkIndices
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AngleModeStickyStateOwnershipTest {
    @Test
    fun reset_onOneInstance_doesNotClearOther() {
        val a = AngleModeStickyState()
        val b = AngleModeStickyState()
        val switched = mutableSetOf<String>()
        a.resolveUse3d("left_knee", wants3d = true, switched)
        b.resolveUse3d("left_knee", wants3d = true, switched)
        a.reset()
        // a is cleared — first observation after reset locks immediately to 2D
        assertFalse(a.resolveUse3d("left_knee", wants3d = false, switched))
        // b still sticky-locked to 3D despite one opposite observation
        assertTrue(b.resolveUse3d("left_knee", wants3d = false, switched))
        assertTrue(switched.isEmpty())
    }
}
