package com.movit.core.training.engine.feedback

import com.movit.core.training.feedback.FeedbackKind
import com.movit.core.training.feedback.FeedbackSeverity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrainingFeedbackEventRouterTest {
    private val messages = TrainingSystemMessagePort { key, defaultAr, defaultEn, substitutions ->
        var text = if (key.startsWith("training_countdown_")) defaultEn else defaultEn
        substitutions.forEach { (name, value) -> text = text.replace("{$name}", value) }
        text
    }

    @Test
    fun repAnnouncement_everyThirdRep() {
        val router = TrainingFeedbackEventRouter(messages)
        assertTrue(router.routeRepCompleted(1, isCounted = true).signals.isEmpty())
        assertTrue(router.routeRepCompleted(2, isCounted = true).signals.isEmpty())
        val third = router.routeRepCompleted(3, isCounted = true)
        assertEquals(2, third.signals.size)
        assertEquals(FeedbackKind.REP, third.signals[0].kind)
        assertEquals(FeedbackSeverity.MOTIVATION, third.signals[0].severity)
        assertEquals(FeedbackKind.REP, third.signals[1].kind)
        assertEquals(FeedbackSeverity.INFO, third.signals[1].severity)
        assertEquals("3", third.signals[1].text)
    }

    @Test
    fun repStreak_resetsOnIncorrectRep() {
        val router = TrainingFeedbackEventRouter(messages)
        router.routeRepCompleted(1, isCounted = true)
        router.routeRepCompleted(2, isCounted = true)
        val broken = router.routeRepCompleted(3, isCounted = false)
        assertEquals(1, broken.signals.size)
        assertEquals(FeedbackKind.REP, broken.signals.single().kind)
        assertEquals(FeedbackSeverity.INFO, broken.signals.single().severity)
    }

    @Test
    fun targetReached_usesSubstitution() {
        val router = TrainingFeedbackEventRouter(messages)
        val signal = router.routeTargetReached(12)
        assertEquals(FeedbackKind.TARGET, signal.kind)
        assertEquals(FeedbackSeverity.MOTIVATION, signal.severity)
        assertTrue(signal.text.contains("12"))
        assertTrue(signal.forceAudible)
    }

    @Test
    fun holdRoutes_matchLegacyKinds() {
        val router = TrainingFeedbackEventRouter(messages)
        assertEquals(FeedbackKind.HOLD, router.routeHoldGraceStarted().kind)
        assertEquals(FeedbackSeverity.WARNING, router.routeHoldGraceStarted().severity)
        assertEquals(FeedbackKind.HOLD, router.routeHoldResumed().kind)
        assertEquals(FeedbackSeverity.SUCCESS, router.routeHoldResumed().severity)
        assertEquals(FeedbackKind.HOLD, router.routeHoldCompleted(5_000L).kind)
        assertTrue(router.routeHoldCompleted(5_000L).text.contains("5"))
        assertEquals(FeedbackKind.HOLD, router.routeHoldFailed().kind)
        assertEquals(FeedbackSeverity.ERROR, router.routeHoldFailed().severity)
    }

    @Test
    fun vignetteCues_forCountdownAndVisibility() {
        val router = TrainingFeedbackEventRouter(messages)
        assertEquals(VignetteCue.WARNING, router.routeCountdownFrozen())
        assertEquals(VignetteCue.CLEAR, router.routeCountdownUnfrozen())
        assertEquals(VignetteCue.WARNING, router.routeVisibilityWarning())
    }

    @Test
    fun reset_clearsStreakAndRepAnnouncement() {
        val router = TrainingFeedbackEventRouter(messages)
        router.routeRepCompleted(3, isCounted = true)
        router.reset()
        assertTrue(router.routeRepCompleted(3, isCounted = true).signals.isEmpty())
    }

    @Test
    fun trainingNumeralKey_matchesLegacyRange() {
        assertEquals("training_countdown_7", TrainingFeedbackEventRouter.trainingNumeralKey(7))
        assertNull(TrainingFeedbackEventRouter.trainingNumeralKey(0))
        assertNull(TrainingFeedbackEventRouter.trainingNumeralKey(31))
    }
}
