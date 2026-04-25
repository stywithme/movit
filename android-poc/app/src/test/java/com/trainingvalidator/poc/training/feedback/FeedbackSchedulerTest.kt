package com.trainingvalidator.poc.training.feedback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackSchedulerTest {
    private var nowMs = 10_000L

    @Test
    fun blocksSecondCorrectionWhileFirstIsActive() {
        val scheduler = scheduler()

        val first = scheduler.schedule(
            signal("knee", FeedbackSeverity.WARNING),
            FeedbackRuntimeMode.CAMERA
        )
        val second = scheduler.schedule(
            signal("back", FeedbackSeverity.WARNING),
            FeedbackRuntimeMode.CAMERA
        )

        assertTrue(first.shouldDeliver)
        assertFalse(second.shouldDeliver)
        assertEquals("active:correction", second.reason)
    }

    @Test
    fun criticalCanReplaceActiveCorrection() {
        val scheduler = scheduler()

        scheduler.schedule(signal("knee", FeedbackSeverity.WARNING), FeedbackRuntimeMode.CAMERA)
        val critical = scheduler.schedule(
            signal(
                key = "visibility",
                severity = FeedbackSeverity.CRITICAL,
                activeKey = "critical",
                forceAudible = true
            ),
            FeedbackRuntimeMode.CAMERA
        )

        assertTrue(critical.shouldDeliver)
        assertEquals(FeedbackAudible.VOICE, critical.audible)
        assertEquals(FeedbackSpeechPriority.INTERRUPT, critical.speechPriority)
    }

    @Test
    fun cameraToneModeUsesToneInsteadOfVoice() {
        val scheduler = scheduler(cameraCueMode = CameraCueMode.TONES_BASIC)

        val plan = scheduler.schedule(
            signal("wrong", FeedbackSeverity.ERROR, forceAudible = true),
            FeedbackRuntimeMode.CAMERA
        )

        assertTrue(plan.shouldDeliver)
        assertEquals(FeedbackAudible.TONE, plan.audible)
        assertEquals(FeedbackTone.ERROR, plan.tone)
    }

    @Test
    fun videoModeUsesVisualAndHapticForWarnings() {
        val scheduler = scheduler()

        val plan = scheduler.schedule(
            signal("warning", FeedbackSeverity.WARNING),
            FeedbackRuntimeMode.VIDEO
        )

        assertTrue(plan.shouldDeliver)
        assertEquals(FeedbackAudible.NONE, plan.audible)
        assertTrue(plan.showVisual)
        assertTrue(plan.vibrate)
    }

    @Test
    fun cooldownSuppressesSameSignalUntilWindowPasses() {
        val scheduler = scheduler()

        assertTrue(scheduler.schedule(signal("knee", FeedbackSeverity.ERROR), FeedbackRuntimeMode.CAMERA).shouldDeliver)
        nowMs += 500L
        val repeat = scheduler.schedule(signal("knee", FeedbackSeverity.ERROR), FeedbackRuntimeMode.CAMERA)

        assertFalse(repeat.shouldDeliver)
        assertEquals("cooldown", repeat.reason)
    }

    private fun scheduler(
        coachIntensity: CoachIntensity = CoachIntensity.STANDARD,
        cameraCueMode: CameraCueMode = CameraCueMode.VOICE
    ) = FeedbackScheduler(
        coachIntensity = coachIntensity,
        cameraCueMode = cameraCueMode,
        nowProvider = { nowMs }
    )

    private fun signal(
        key: String,
        severity: FeedbackSeverity,
        activeKey: String = "correction",
        forceAudible: Boolean = false
    ) = FeedbackSignal(
        kind = FeedbackKind.POSITION_CHECK,
        severity = severity,
        text = "Fix form",
        dedupeKey = key,
        activeKey = activeKey,
        cooldownGroup = key,
        forceAudible = forceAudible
    )
}
