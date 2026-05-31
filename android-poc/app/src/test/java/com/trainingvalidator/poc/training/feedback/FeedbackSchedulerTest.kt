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
    fun interruptCanReplaceActiveCriticalWithSameSeverity() {
        val scheduler = scheduler()

        assertTrue(
            scheduler.schedule(
                signal(
                    key = "countdown:3",
                    severity = FeedbackSeverity.CRITICAL,
                    activeKey = "countdown",
                    forceAudible = true,
                    interruptPolicy = FeedbackInterruptPolicy.INTERRUPT
                ),
                FeedbackRuntimeMode.CAMERA
            ).shouldDeliver
        )

        val next = scheduler.schedule(
            signal(
                key = "countdown:2",
                severity = FeedbackSeverity.CRITICAL,
                activeKey = "countdown",
                forceAudible = true,
                interruptPolicy = FeedbackInterruptPolicy.INTERRUPT
            ),
            FeedbackRuntimeMode.CAMERA
        )

        assertTrue(next.shouldDeliver)
        assertEquals(FeedbackAudible.VOICE, next.audible)
    }

    @Test
    fun cameraModeUsesVoiceEvenWhenToneSettingExists() {
        val scheduler = scheduler(cameraCueMode = CameraCueMode.TONES_BASIC)

        val plan = scheduler.schedule(
            signal("wrong", FeedbackSeverity.ERROR, forceAudible = true),
            FeedbackRuntimeMode.CAMERA
        )

        assertTrue(plan.shouldDeliver)
        assertEquals(FeedbackAudible.VOICE, plan.audible)
        assertEquals(FeedbackTone.NONE, plan.tone)
    }

    @Test
    fun normalAndPadUseVoiceWhenNoHigherPriorityIsActive() {
        val scheduler = scheduler()

        val normal = scheduler.schedule(
            signal("normal", FeedbackSeverity.INFO, activeKey = "state:normal"),
            FeedbackRuntimeMode.CAMERA
        )
        nowMs += 2_500L
        val pad = scheduler.schedule(
            signal("pad", FeedbackSeverity.TIP, activeKey = "state:pad"),
            FeedbackRuntimeMode.CAMERA
        )

        assertTrue(normal.shouldDeliver)
        assertEquals(FeedbackAudible.VOICE, normal.audible)
        assertTrue(pad.shouldDeliver)
        assertEquals(FeedbackAudible.VOICE, pad.audible)
    }

    @Test
    fun normalAndPadWaitBehindActiveCorrection() {
        val scheduler = scheduler()

        assertTrue(scheduler.schedule(signal("knee", FeedbackSeverity.WARNING), FeedbackRuntimeMode.CAMERA).shouldDeliver)
        val normal = scheduler.schedule(
            signal("normal", FeedbackSeverity.INFO, activeKey = "state:normal"),
            FeedbackRuntimeMode.CAMERA
        )
        val pad = scheduler.schedule(
            signal("pad", FeedbackSeverity.TIP, activeKey = "state:pad"),
            FeedbackRuntimeMode.CAMERA
        )

        assertFalse(normal.shouldDeliver)
        assertEquals("active:correction", normal.reason)
        assertFalse(pad.shouldDeliver)
        assertEquals("active:correction", pad.reason)
    }

    @Test
    fun forceAudibleReplaceLowerDoesNotReplaceHigherSeverity() {
        val scheduler = scheduler()

        assertTrue(scheduler.schedule(signal("knee", FeedbackSeverity.ERROR), FeedbackRuntimeMode.CAMERA).shouldDeliver)
        val motivation = scheduler.schedule(
            signal(
                key = "target",
                severity = FeedbackSeverity.MOTIVATION,
                activeKey = "session_complete",
                forceAudible = true,
                interruptPolicy = FeedbackInterruptPolicy.REPLACE_LOWER
            ),
            FeedbackRuntimeMode.CAMERA
        )

        assertFalse(motivation.shouldDeliver)
        assertEquals("active:correction", motivation.reason)
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
        forceAudible: Boolean = false,
        interruptPolicy: FeedbackInterruptPolicy = FeedbackInterruptPolicy.defaultFor(severity)
    ) = FeedbackSignal(
        kind = FeedbackKind.POSITION_CHECK,
        severity = severity,
        text = "Fix form",
        dedupeKey = key,
        activeKey = activeKey,
        cooldownGroup = key,
        forceAudible = forceAudible,
        interruptPolicy = interruptPolicy
    )
}
