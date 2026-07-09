package com.movit.core.training.engine.evaluation

import com.movit.core.training.config.AngleRange
import com.movit.core.training.config.StateRanges
import com.movit.core.training.config.TrackedJoint
import com.movit.core.training.engine.JointState
import com.movit.core.training.engine.Phase
import com.movit.core.training.engine.policy.StabilityPolicy
import kotlin.test.Test
import kotlin.test.assertEquals

class JointEvaluatorTest {
    @Test
    fun evaluate_delaysInitialOutwardFallbackDangerUntilMinFrames() {
        val evaluator = JointEvaluator(
            trackedJoints = listOf(
                TrackedJoint(
                    joint = "left_knee",
                    upRange = StateRanges(
                        perfect = AngleRange(90.0, 100.0),
                        normal = AngleRange(100.0, 110.0),
                        pad = AngleRange(110.0, 120.0),
                        warning = AngleRange(120.0, 130.0),
                        danger = AngleRange(130.0, 140.0),
                    ),
                    downRange = StateRanges(
                        perfect = AngleRange(40.0, 50.0),
                        normal = AngleRange(50.0, 60.0),
                        pad = AngleRange(60.0, 70.0),
                        warning = AngleRange(70.0, 80.0),
                        danger = AngleRange(80.0, 90.0),
                    ),
                ),
            ),
            policy = StabilityPolicy(minDangerFrames = 3),
        )

        assertEquals(
            JointState.WARNING,
            evaluator.evaluate(mapOf("left_knee" to 150.0), Phase.UP).getValue("left_knee").state,
        )
        assertEquals(
            JointState.WARNING,
            evaluator.evaluate(mapOf("left_knee" to 150.0), Phase.UP).getValue("left_knee").state,
        )
        assertEquals(
            JointState.DANGER,
            evaluator.evaluate(mapOf("left_knee" to 150.0), Phase.UP).getValue("left_knee").state,
        )
    }
}
