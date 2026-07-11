package com.movit.core.training.session

import com.movit.core.training.config.AngleRange
import com.movit.core.training.config.ExerciseConfig
import com.movit.core.training.config.JointRole
import com.movit.core.training.config.LocalizedText
import com.movit.core.training.config.PoseVariant
import com.movit.core.training.config.StateRanges
import com.movit.core.training.config.TrackedJoint
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class MovitTrainingEnginePoseVariantSafetyTest {

    @Test
    fun outOfRangePoseVariantIndex_coercesInsteadOfThrowing() {
        val config = configWithVariants(2)
        // WP-01 / J-02: index 2 with only 2 variants must not crash.
        val engine = MovitTrainingEngine(exerciseConfig = config, poseVariantIndex = 2)
        assertNotNull(engine)
        engine.stop()
    }

    @Test
    fun emptyPoseVariants_stillErrors() {
        val config = ExerciseConfig(
            name = LocalizedText(en = "Empty"),
            poseVariants = emptyList(),
        )
        assertFailsWith<IllegalStateException> {
            MovitTrainingEngine(exerciseConfig = config, poseVariantIndex = 0)
        }
    }

    private fun configWithVariants(count: Int): ExerciseConfig {
        val joint = TrackedJoint(
            joint = "left_knee",
            role = JointRole.PRIMARY,
            startPose = AngleRange(150.0, 180.0),
            upRange = StateRanges(perfect = AngleRange(160.0, 180.0)),
            downRange = StateRanges(perfect = AngleRange(60.0, 90.0)),
        )
        return ExerciseConfig(
            name = LocalizedText(en = "Multi"),
            poseVariants = List(count) { PoseVariant(trackedJoints = listOf(joint)) },
        )
    }
}
