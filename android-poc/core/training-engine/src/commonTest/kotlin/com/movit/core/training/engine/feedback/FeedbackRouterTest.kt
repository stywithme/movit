package com.movit.core.training.engine.feedback

import com.movit.core.training.feedback.FeedbackKind
import com.movit.core.training.feedback.FeedbackRuntimeMode
import com.movit.core.training.feedback.FeedbackSeverity
import com.movit.core.training.feedback.FeedbackSignal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeedbackRouterTest {
    @Test
    fun deliversVoiceAndVisualForWarning() {
        val visuals = mutableListOf<FeedbackVisualMessage>()
        val router = FeedbackRouter()
        router.onVisualMessage = { visuals += it }

        val plan = router.submit(
            FeedbackSignal(
                kind = FeedbackKind.JOINT_QUALITY,
                severity = FeedbackSeverity.WARNING,
                text = "Keep your back straight",
                dedupeKey = "back",
            ),
            FeedbackRuntimeMode.CAMERA,
        )

        assertTrue(plan.shouldDeliver)
        assertEquals(1, visuals.size)
        assertEquals("Keep your back straight", visuals.first().text)
    }

    @Test
    fun respectsSchedulerCooldown() {
        val router = FeedbackRouter()
        val signal = FeedbackSignal(
            kind = FeedbackKind.JOINT_QUALITY,
            severity = FeedbackSeverity.WARNING,
            text = "A",
            dedupeKey = "joint:a",
        )
        assertTrue(router.submit(signal).shouldDeliver)
        assertEquals("cooldown", router.submit(signal).reason)
    }

    @Test
    fun routesVoiceThroughAudioPlayerWhenPresent() {
        val player = RecordingAudioPlayer()
        val router = FeedbackRouter(audioPlayer = player)
        router.submit(
            FeedbackSignal(
                kind = FeedbackKind.JOINT_QUALITY,
                severity = FeedbackSeverity.WARNING,
                text = "Keep back straight",
                dedupeKey = "back",
                audioUrl = "/audio/tts_en_9.wav",
            ),
        )
        assertEquals(1, player.played.size)
        assertEquals("Keep back straight", player.played.single().text)
        assertEquals("/audio/tts_en_9.wav", player.played.single().audioUrl)
    }

    @Test
    fun setupFeedbackUsesSetupActiveKeyAndSchedulerDedupe() {
        val player = RecordingAudioPlayer()
        val router = FeedbackRouter(audioPlayer = player)
        val signal = com.movit.core.training.feedback.SetupFeedbackSignals.phaseGuidance(
            com.movit.core.training.config.LocalizedText(en = "Show full body"),
            "en",
        )!!
        assertTrue(router.submitSetup(signal).shouldDeliver)
        assertEquals(1, player.played.size)
        assertEquals(FeedbackKind.SETUP, player.played.single().kind)
        assertEquals("setup", player.played.single().activeKey)

        val repeat = router.submitSetup(signal)
        assertEquals(false, repeat.shouldDeliver)
        assertEquals("cooldown", repeat.reason)
        assertEquals(1, player.played.size)
    }

    @Test
    fun resetSetupFeedbackAllowsRepeatAfterCategoryReset() {
        val player = RecordingAudioPlayer()
        val router = FeedbackRouter(audioPlayer = player)
        val signal = com.movit.core.training.feedback.SetupFeedbackSignals.jointGuidance(
            com.movit.core.training.session.JointSetupGuidance(
                jointCode = "right_knee",
                level = com.movit.core.training.session.SetupGuidanceLevel.RED,
                currentAngle = 120.0,
                targetMin = 160.0,
                targetMax = 180.0,
                distance = 40.0,
                direction = com.movit.core.training.session.SetupGuidanceDirection.RAISE,
                message = com.movit.core.training.config.LocalizedText(en = "Straighten knee"),
                isPrimary = true,
            ),
            "en",
        )!!
        assertTrue(router.submitSetup(signal).shouldDeliver)
        assertEquals(false, router.submitSetup(signal).shouldDeliver)
        router.resetSetupFeedback()
        assertTrue(router.submitSetup(signal).shouldDeliver)
        assertEquals(2, player.played.size)
    }

    @Test
    fun deliversTargetReachedMotivationVoice() {
        val player = RecordingAudioPlayer()
        val router = FeedbackRouter(audioPlayer = player)
        val eventRouter = TrainingFeedbackEventRouter(
            messages = TrainingSystemMessagePort { _, _, en, _ -> en },
        )
        val signal = eventRouter.routeTargetReached(10)
        assertEquals(FeedbackKind.TARGET, signal.kind)
        val plan = router.submit(signal)
        assertTrue(plan.shouldDeliver)
        assertEquals(1, player.played.size)
    }

    @Test
    fun deliversHoldGraceWarningWithoutVisual() {
        val visuals = mutableListOf<FeedbackVisualMessage>()
        val router = FeedbackRouter()
        router.onVisualMessage = { visuals += it }
        val eventRouter = TrainingFeedbackEventRouter(
            messages = TrainingSystemMessagePort { _, _, en, _ -> en },
        )
        val plan = router.submit(eventRouter.routeHoldGraceStarted())
        assertTrue(plan.shouldDeliver)
        assertTrue(visuals.isEmpty())
    }

    @Test
    fun repRouteBatchSubmitsAllSignals() {
        val player = RecordingAudioPlayer()
        val router = FeedbackRouter(audioPlayer = player)
        val eventRouter = TrainingFeedbackEventRouter(
            messages = TrainingSystemMessagePort { _, _, en, _ -> en },
        )
        eventRouter.routeRepCompleted(1, isCounted = true)
        eventRouter.routeRepCompleted(2, isCounted = true)
        val batch = eventRouter.routeRepCompleted(3, isCounted = true)
        assertEquals(2, batch.signals.size)
        batch.signals.forEach { router.submit(it) }
        assertTrue(player.played.isNotEmpty())
        assertTrue(player.played.all { it.kind == FeedbackKind.REP })
    }

    private class RecordingAudioPlayer : com.movit.core.training.boundary.AudioFeedbackPlayer {
        val played = mutableListOf<FeedbackSignal>()
        override fun prepare() = Unit
        override fun play(signal: FeedbackSignal) {
            played += signal
        }
        override fun stopAll() = Unit
        override fun release() = Unit
    }
}
