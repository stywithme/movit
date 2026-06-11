package com.movit.core.training.engine.policy

import com.movit.core.training.feedback.CoachIntensity
import kotlin.test.Test
import kotlin.test.assertEquals

class TimingPolicyTest {

    @Test
    fun default_usesAppSettingsParityConstants() {
        val policy = TimingPolicy.default()
        assertEquals(400L, policy.defaultMinRepIntervalMs)
        assertEquals(5000L, policy.defaultMaxRepIntervalMs)
        assertEquals(100L, policy.defaultMinPhaseDurationMs)
        assertEquals(30, policy.defaultHoldDurationSeconds)
        assertEquals(3000L, policy.defaultGracePeriodMs)
        assertEquals(3, policy.smoothingWindowSize)
        assertEquals(2000L, policy.stateMessageCooldownMs)
    }

    @Test
    fun minRepIntervalFor_usesOverrideWhenPresent() {
        val policy = TimingPolicy.default()
        val overrides = RepCountingTimingOverrides(minRepIntervalMs = 800L)
        assertEquals(800L, policy.minRepIntervalFor(overrides))
        assertEquals(400L, policy.minRepIntervalFor(null))
    }

    @Test
    fun minPhaseDurationFor_dividesIntervalByPhaseCount() {
        val policy = TimingPolicy.default()
        val overrides = RepCountingTimingOverrides(minRepIntervalMs = 800L)
        assertEquals(200L, policy.minPhaseDurationFor(overrides, numberOfPhases = 4))
        assertEquals(100L, policy.minPhaseDurationFor(null, numberOfPhases = 4))
    }

    @Test
    fun withCoachIntensity_scalesStateMessageCooldown() {
        val strict = TimingPolicy.withCoachIntensity(CoachIntensity.STRICT)
        val calm = TimingPolicy.withCoachIntensity(CoachIntensity.CALM)
        assertEquals(1500L, strict.stateMessageCooldownMs)
        assertEquals(3200L, calm.stateMessageCooldownMs)
    }
}
