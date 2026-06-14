package com.movit.feature.training

import com.movit.core.training.config.LocalizedText
import com.movit.core.training.session.JointSetupGuidance
import com.movit.core.training.session.SetupAxisStatus
import com.movit.core.training.session.SetupAxisStatuses
import com.movit.core.training.session.SetupGuidanceDirection
import com.movit.core.training.session.SetupGuidanceLevel
import com.movit.core.training.session.SetupPhase
import com.movit.core.training.session.SetupReadinessResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SetupGuidanceMapperTest {
    @Test
    fun regionPhase_usesPhaseMessageAsAction() {
        val result = SetupReadinessResult(
            phase = SetupPhase.REGION,
            progressPercent = 0,
            isConfirmed = false,
            axisMatch = null,
            phaseMessage = LocalizedText(ar = "أظهر جسمك كاملاً", en = "Show your full body"),
            axisStatuses = SetupAxisStatuses(
                region = SetupAxisStatus.PENDING,
                posture = SetupAxisStatus.PENDING,
                direction = SetupAxisStatus.PENDING,
            ),
        )
        val ui = result.toSetupGuidanceUi("en")
        assertEquals("Show your full body", ui.actionMessage)
        assertEquals(SetupAxisStatusUi.PENDING, ui.regionStatus)
    }

    @Test
    fun anglesPhase_prefersWorstJointMessage() {
        val result = SetupReadinessResult(
            phase = SetupPhase.ANGLES,
            progressPercent = 75,
            isConfirmed = false,
            axisMatch = null,
            worstJointGuidance = JointSetupGuidance(
                jointCode = "left_knee",
                level = SetupGuidanceLevel.RED,
                currentAngle = 90.0,
                targetMin = 120.0,
                targetMax = 180.0,
                distance = 30.0,
                direction = SetupGuidanceDirection.RAISE,
                message = LocalizedText(ar = "افرد الركبة", en = "Straighten your knee"),
                isPrimary = true,
            ),
            jointGuidanceRows = listOf(
                JointSetupGuidance(
                    jointCode = "left_knee",
                    level = SetupGuidanceLevel.RED,
                    currentAngle = 90.0,
                    targetMin = 120.0,
                    targetMax = 180.0,
                    distance = 30.0,
                    direction = SetupGuidanceDirection.RAISE,
                    message = LocalizedText(ar = "افرد الركبة", en = "Straighten your knee"),
                    isPrimary = true,
                ),
            ),
            axisStatuses = SetupAxisStatuses(
                region = SetupAxisStatus.PASSED,
                posture = SetupAxisStatus.PASSED,
                direction = SetupAxisStatus.PASSED,
            ),
        )
        val ui = result.toSetupGuidanceUi("en")
        assertEquals("Straighten your knee", ui.actionMessage)
        assertEquals(1, ui.jointRows.size)
        assertEquals("left_knee", ui.jointRows.first().jointCode)
    }

    @Test
    fun anglesPhase_whenInStartPose_showsHoldMessage() {
        val result = SetupReadinessResult(
            phase = SetupPhase.ANGLES,
            progressPercent = 100,
            isConfirmed = true,
            axisMatch = null,
            inStartPose = true,
            axisStatuses = SetupAxisStatuses(
                region = SetupAxisStatus.PASSED,
                posture = SetupAxisStatus.PASSED,
                direction = SetupAxisStatus.PASSED,
            ),
        )
        val ui = result.toSetupGuidanceUi("en")
        assertNotNull(ui.actionMessage)
        assertTrue(ui.actionMessage!!.contains("hold", ignoreCase = true))
    }
}
