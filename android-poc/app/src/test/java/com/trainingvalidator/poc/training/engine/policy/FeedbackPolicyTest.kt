package com.trainingvalidator.poc.training.engine.policy

import com.trainingvalidator.poc.training.models.JointState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackPolicyTest {
    @Test
    fun stateMessagesUseCandidateIntervalInsteadOfFullDeliveryCooldown() {
        val policy = FeedbackPolicy(
            stateMessageCooldownMs = 5_000L,
            maxCandidateIntervalMs = 250L
        )

        assertTrue(policy.shouldEmitStateMessage("knee", JointState.NORMAL, 1_000L))
        policy.recordStateMessage("knee", JointState.NORMAL, 1_000L)

        assertFalse(policy.shouldEmitStateMessage("knee", JointState.NORMAL, 1_100L))
        assertTrue(policy.shouldEmitStateMessage("knee", JointState.NORMAL, 1_260L))
    }
}
