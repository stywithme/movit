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
}
