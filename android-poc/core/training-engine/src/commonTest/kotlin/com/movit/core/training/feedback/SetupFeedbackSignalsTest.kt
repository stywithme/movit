package com.movit.core.training.feedback

import com.movit.core.training.config.LocalizedText
import com.movit.core.training.session.JointSetupGuidance
import com.movit.core.training.session.SetupGuidanceDirection
import com.movit.core.training.session.SetupGuidanceLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SetupFeedbackSignalsTest {
    @Test
    fun phaseGuidanceUsesSetupKindAndDedupeKey() {
        val message = LocalizedText(ar = "قف", en = "Stand")
        val signal = SetupFeedbackSignals.phaseGuidance(message, "en")
        assertNotNull(signal)
        assertEquals(FeedbackKind.SETUP, signal.kind)
        assertEquals("setup_phase:${"Stand".hashCode()}", signal.dedupeKey)
        assertEquals(SetupFeedbackSignals.SETUP_ACTIVE_KEY, signal.activeKey)
        assertEquals(FeedbackInterruptPolicy.WAIT_FOR_SLOT, signal.interruptPolicy)
        assertEquals(true, signal.forceAudible)
        assertEquals(false, signal.allowVisual)
    }

    @Test
    fun jointGuidanceUsesJointCodeDedupeKey() {
        val joint = JointSetupGuidance(
            jointCode = "right_elbow",
            level = SetupGuidanceLevel.RED,
            currentAngle = 90.0,
            targetMin = 150.0,
            targetMax = 170.0,
            distance = 60.0,
            direction = SetupGuidanceDirection.RAISE,
            message = LocalizedText(ar = "ارفع", en = "Raise your elbow"),
            isPrimary = true,
        )
        val signal = SetupFeedbackSignals.jointGuidance(joint, "en")
        assertNotNull(signal)
        assertEquals("setup:right_elbow", signal.dedupeKey)
        assertEquals("Raise your elbow", signal.text)
    }

    @Test
    fun blankTextReturnsNull() {
        assertNull(SetupFeedbackSignals.phaseGuidance(LocalizedText(), "en"))
    }
}
