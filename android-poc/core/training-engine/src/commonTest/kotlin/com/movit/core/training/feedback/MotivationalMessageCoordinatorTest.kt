package com.movit.core.training.feedback

import com.movit.core.training.config.FeedbackMessages
import com.movit.core.training.config.LocalizedText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.random.Random

class MotivationalMessageCoordinatorTest {
    private var now = 10_000L

    @Test
    fun deliversMotivationalAfterIdleWindow() {
        val coordinator = coordinator()
        coordinator.setMessages(
            FeedbackMessages(
                motivational = listOf(LocalizedText(en = "Great pace!")),
                tips = emptyList(),
            ),
        )
        coordinator.markHighPriorityDelivered(atMs = 0L)
        now = 6_000L

        val signal = coordinator.tryBuildSignal(hasActiveErrors = false, language = "en")
        assertNotNull(signal)
        assertEquals(FeedbackSeverity.MOTIVATION, signal.severity)
        assertEquals("Great pace!", signal.text)
    }

    @Test
    fun suppressesWhenErrorsActive() {
        val coordinator = coordinator()
        coordinator.setMessages(
            FeedbackMessages(motivational = listOf(LocalizedText(en = "Keep going"))),
        )
        assertNull(coordinator.tryBuildSignal(hasActiveErrors = true, language = "en"))
    }

    @Test
    fun dedupesIdenticalMotivationBackToBack() {
        val coordinator = coordinator()
        coordinator.setMessages(
            FeedbackMessages(motivational = listOf(LocalizedText(en = "Nice"))),
        )
        coordinator.markHighPriorityDelivered(atMs = 0L)
        now = 6_000L
        assertNotNull(coordinator.tryBuildSignal(hasActiveErrors = false, language = "en"))
        now += 11_000L
        assertNull(coordinator.tryBuildSignal(hasActiveErrors = false, language = "en"))
    }

    @Test
    fun respectsRandomCooldown() {
        val coordinator = coordinator()
        coordinator.setMessages(
            FeedbackMessages(
                motivational = listOf(
                    LocalizedText(en = "A"),
                    LocalizedText(en = "B"),
                ),
            ),
        )
        coordinator.markHighPriorityDelivered(atMs = 0L)
        now = 6_000L
        assertNotNull(coordinator.tryBuildSignal(hasActiveErrors = false, language = "en"))
        now += 1_000L
        assertNull(coordinator.tryBuildSignal(hasActiveErrors = false, language = "en"))
    }

    private fun coordinator() = MotivationalMessageCoordinator(
        nowProvider = { now },
        random = Random(0),
    )
}
