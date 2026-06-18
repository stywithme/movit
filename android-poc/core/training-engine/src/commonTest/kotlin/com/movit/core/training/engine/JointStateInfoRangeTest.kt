package com.movit.core.training.engine

import com.movit.core.training.config.AngleRange
import com.movit.core.training.config.StateRanges
import com.movit.core.training.engine.evaluation.JointEval
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JointStateInfoRangeTest {
    @Test
    fun jointEval_preservesRomRangeMetadata() {
        val activeRange = StateRanges(perfect = AngleRange(80.0, 100.0))
        val upRange = StateRanges(perfect = AngleRange(150.0, 170.0))
        val downRange = StateRanges(perfect = AngleRange(30.0, 50.0))

        val info = JointEval(
            code = "left_elbow",
            rawAngle = 72.0,
            smoothedAngle = 70.0,
            zoneType = ZoneType.DOWN_ZONE,
            state = JointState.NORMAL,
            stateRanges = activeRange,
            upStateRanges = upRange,
            downStateRanges = downRange,
            isPrimary = true,
            invertIndicator = true,
        ).toJointStateInfo()

        assertEquals(activeRange, info.stateRanges)
        assertEquals(upRange, info.upStateRanges)
        assertEquals(downRange, info.downStateRanges)
        assertTrue(info.invertIndicator)
    }
}
