package com.movit.core.training.engine.policy

import com.movit.core.training.engine.JointState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeedbackPolicyTest {

    @Test
    fun stateMessagesUseCandidateIntervalInsteadOfFullDeliveryCooldown() {
        val policy = FeedbackPolicy(
            stateMessageCooldownMs = 5_000L,
            maxCandidateIntervalMs = 250L,
        )

        assertTrue(policy.shouldEmitStateMessage("knee", JointState.NORMAL, 1_000L))
        policy.recordStateMessage("knee", JointState.NORMAL, 1_000L)

        assertFalse(policy.shouldEmitStateMessage("knee", JointState.NORMAL, 1_100L))
        assertTrue(policy.shouldEmitStateMessage("knee", JointState.NORMAL, 1_260L))
    }

    @Test
    fun from_timingPolicy_usesStateMessageCooldown() {
        val timing = TimingPolicy(stateMessageCooldownMs = 3_000L)
        val policy = FeedbackPolicy.from(timing)
        policy.recordStateMessage("hip", JointState.PAD, 0L)
        assertFalse(policy.shouldEmitStateMessage("hip", JointState.PAD, 100L))
        assertTrue(policy.shouldEmitStateMessage("hip", JointState.PAD, 251L))
    }
}
