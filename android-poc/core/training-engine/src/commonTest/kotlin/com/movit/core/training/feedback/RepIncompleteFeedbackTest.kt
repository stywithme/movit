package com.movit.core.training.feedback

import com.movit.core.training.engine.RepIncompleteReason
import com.movit.core.training.engine.feedback.FeedbackRouter
import com.movit.core.training.engine.feedback.FeedbackVisualMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RepIncompleteFeedbackTest {
    @Test
    fun noTargetDepth_mapsToLegacyMessageAndDedupe() {
        assertSignalForReason(
            reason = RepIncompleteReason.NO_TARGET_DEPTH,
            expectedCode = "training_rep_incomplete_depth",
            expectedEn = "You didn't reach the target. Complete the full range.",
            expectedDedupe = "rep_incomplete:NO_TARGET_DEPTH",
        )
    }

    @Test
    fun noFullReturn_mapsToLegacyMessageAndDedupe() {
        assertSignalForReason(
            reason = RepIncompleteReason.NO_FULL_RETURN,
            expectedCode = "training_rep_incomplete_return",
            expectedEn = "Return fully to the start position.",
            expectedDedupe = "rep_incomplete:NO_FULL_RETURN",
        )
    }

    @Test
    fun tooFast_mapsToLegacyMessageAndDedupe() {
        assertSignalForReason(
            reason = RepIncompleteReason.TOO_FAST,
            expectedCode = "training_rep_too_fast",
            expectedEn = "Too fast — slow down.",
            expectedDedupe = "rep_incomplete:TOO_FAST",
        )
    }

    @Test
    fun tooSlow_mapsToLegacyMessageAndDedupe() {
        assertSignalForReason(
            reason = RepIncompleteReason.TOO_SLOW,
            expectedCode = "training_rep_too_slow",
            expectedEn = "Too slow — keep a steady pace.",
            expectedDedupe = "rep_incomplete:TOO_SLOW",
        )
    }

    @Test
    fun routerDeliversAllFourReasonsWithCooldown() {
        val visuals = mutableListOf<FeedbackVisualMessage>()
        val router = FeedbackRouter()
        router.onVisualMessage = { visuals += it }

        RepIncompleteReason.entries.forEach { reason ->
            val copy = RepIncompleteFeedback.defaultCopy(reason)
            val signal = RepIncompleteFeedback.toSignal(reason, copy.en)
            val first = router.submit(signal)
            val second = router.submit(signal)

            assertTrue(first.shouldDeliver, "first delivery for $reason")
            assertEquals("cooldown", second.reason, "cooldown for $reason")
            assertEquals(copy.en, visuals.last().text)
        }
        assertEquals(4, visuals.size)
    }

    private fun assertSignalForReason(
        reason: RepIncompleteReason,
        expectedCode: String,
        expectedEn: String,
        expectedDedupe: String,
    ) {
        assertEquals(expectedCode, RepIncompleteFeedback.messageCode(reason))
        val copy = RepIncompleteFeedback.defaultCopy(reason)
        assertEquals(expectedEn, copy.en)

        val signal = RepIncompleteFeedback.toSignal(reason, expectedEn)
        assertEquals(FeedbackKind.REP, signal.kind)
        assertEquals(FeedbackSeverity.ERROR, signal.severity)
        assertEquals(expectedEn, signal.text)
        assertEquals(expectedDedupe, signal.dedupeKey)
        assertEquals(expectedDedupe, signal.cooldownGroup)
        assertEquals("correction", signal.activeKey)
        assertEquals(expectedCode, signal.messageCode)
        assertTrue(signal.forceAudible)
        assertEquals(FeedbackInterruptPolicy.INTERRUPT, signal.interruptPolicy)
    }
}
