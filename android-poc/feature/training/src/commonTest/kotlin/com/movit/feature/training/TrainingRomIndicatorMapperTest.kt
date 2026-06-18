package com.movit.feature.training

import com.movit.core.training.config.AngleRange
import com.movit.core.training.config.StateRanges
import com.movit.core.training.engine.JointState
import com.movit.core.training.engine.JointStateInfo
import com.movit.core.training.engine.ZoneType
import com.movit.designsystem.components.SkeletonLandmarkPoint
import com.movit.designsystem.components.SkeletonRomIndicatorStyle
import com.movit.designsystem.components.SkeletonRomState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TrainingRomIndicatorMapperTest {
    @Test
    fun buildsIndicatorFromJointRangesEvenWhenJointStateIsNormal() {
        val upRange = StateRanges(
            perfect = AngleRange(150.0, 170.0),
            normal = AngleRange(140.0, 180.0),
        )
        val downRange = StateRanges(
            perfect = AngleRange(30.0, 50.0),
            normal = AngleRange(20.0, 60.0),
        )

        val indicators = buildSkeletonRomIndicators(
            landmarks = landmarkSet(),
            jointStateInfos = mapOf(
                "left_elbow" to JointStateInfo(
                    jointCode = "left_elbow",
                    state = JointState.NORMAL,
                    isPrimary = true,
                    currentAngle = 70.0,
                    currentZone = ZoneType.TRANSITION,
                    stateRanges = downRange,
                    upStateRanges = upRange,
                    downStateRanges = downRange,
                    invertIndicator = true,
                ),
            ),
            indicatorType = "line",
        )

        val indicator = indicators.single()
        assertEquals(SkeletonRomIndicatorStyle.LINE, indicator.style)
        assertEquals(SkeletonRomState.NORMAL, indicator.currentState)
        assertEquals(70f, indicator.currentAngleDeg)
        assertTrue(indicator.invertAngles)
        assertNotNull(indicator.upStateRanges)
        assertNotNull(indicator.downStateRanges)
        assertEquals(0.10f, indicator.upperEndX)
        assertEquals(0.30f, indicator.centerX)
        assertEquals(0.50f, indicator.lowerEndX)
    }

    private fun landmarkSet(): List<SkeletonLandmarkPoint> =
        MutableList(33) { SkeletonLandmarkPoint(0f, 0f, visible = false) }.apply {
            this[11] = SkeletonLandmarkPoint(0.10f, 0.20f)
            this[13] = SkeletonLandmarkPoint(0.30f, 0.40f)
            this[15] = SkeletonLandmarkPoint(0.50f, 0.60f)
        }
}
