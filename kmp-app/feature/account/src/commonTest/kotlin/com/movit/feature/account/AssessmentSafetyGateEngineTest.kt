package com.movit.feature.account

import com.movit.core.network.dto.AssessmentRegionDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AssessmentSafetyGateEngineTest {

    @Test
    fun evaluate_parqFlagsAddGeneralGate() {
        val gates = AssessmentSafetyGateEngine.evaluate(emptyList(), listOf("assessment_parq_heart"))
        assertEquals(1, gates.size)
        assertEquals("assessment_safety_gate_parq", gates.first().reasonKey)
    }

    @Test
    fun evaluate_limitedRegionAddsGate() {
        val gates = AssessmentSafetyGateEngine.evaluate(
            regions = listOf(
                AssessmentRegionDto(
                    region = "shoulders",
                    regionalScore = 42.0,
                    status = "limited",
                    confidence = "high",
                ),
            ),
            parqFlags = emptyList(),
        )
        assertEquals(1, gates.size)
        assertEquals("assessment_safety_gate_limited", gates.first().reasonKey)
        assertTrue(gates.first().blockedExerciseTypes.isNotEmpty())
    }

    @Test
    fun evaluate_lowConfidenceAddsRetestGate() {
        val gates = AssessmentSafetyGateEngine.evaluate(
            regions = listOf(
                AssessmentRegionDto(
                    region = "hips",
                    regionalScore = 70.0,
                    status = "good",
                    confidence = "low",
                ),
            ),
            parqFlags = emptyList(),
        )
        assertEquals(1, gates.size)
        assertEquals("assessment_safety_gate_unreliable", gates.first().reasonKey)
    }
}
