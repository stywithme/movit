package com.movit.feature.account

import com.movit.core.network.dto.AssessmentRegionDto

/**
 * Parity with legacy [SafetyGateEngine] — region-based restrictions independent of body score.
 */
object AssessmentSafetyGateEngine {

    fun evaluate(
        regions: List<AssessmentRegionDto>,
        parqFlags: List<String>,
    ): List<AssessmentSafetyGateUi> {
        val gates = mutableListOf<AssessmentSafetyGateUi>()

        if (parqFlags.isNotEmpty()) {
            gates += AssessmentSafetyGateUi(
                regionKey = "general",
                reasonKey = "assessment_safety_gate_parq",
                blockedExerciseTypes = listOf("high_intensity", "loaded_compound"),
                allowedAlternatives = listOf("mobility", "physician_clearance"),
            )
        }

        for (region in regions) {
            if (region.confidence.equals("low", ignoreCase = true)) {
                gates += AssessmentSafetyGateUi(
                    regionKey = regionKey(region.region),
                    reasonKey = "assessment_safety_gate_unreliable",
                    reasonArgs = listOf(regionKey(region.region)),
                    blockedExerciseTypes = emptyList(),
                    allowedAlternatives = listOf("retest_scan"),
                )
                continue
            }
            if (region.status.equals("limited", ignoreCase = true) ||
                region.status.equals("weak", ignoreCase = true)
            ) {
                val key = regionKey(region.region)
                val (blocked, alternatives) = gateActions(key, region.status)
                gates += AssessmentSafetyGateUi(
                    regionKey = key,
                    reasonKey = if (region.status.equals("weak", ignoreCase = true)) {
                        "assessment_safety_gate_weak"
                    } else {
                        "assessment_safety_gate_limited"
                    },
                    reasonArgs = listOf(key),
                    blockedExerciseTypes = blocked,
                    allowedAlternatives = alternatives,
                )
            }
        }

        return gates.distinctBy { "${it.regionKey}_${it.reasonKey}" }
    }

    fun evaluateFromUi(
        regions: List<AssessmentRegionUi>,
        parqFlags: List<String>,
    ): List<AssessmentSafetyGateUi> {
        val dtos = regions.map { region ->
            AssessmentRegionDto(
                region = region.regionKey,
                regionalScore = region.score.toDouble(),
                confidence = region.confidence,
                status = region.status,
            )
        }
        return evaluate(dtos, parqFlags)
    }

    private fun gateActions(regionKey: String, status: String): Pair<List<String>, List<String>> {
        return when (regionKey) {
            "knees", "knee" -> listOf("deep_lunge", "sissy_squat", "jump_lunge") to
                listOf("box_squat", "leg_curl", "step_up")
            "shoulders", "shoulder" -> listOf("overhead_press", "snatch", "handstand") to
                listOf("landmine_press", "lateral_raise", "face_pull")
            "hips", "hip" -> listOf("full_squat", "sumo_deadlift", "wide_stance") to
                listOf("hip_hinge", "glute_bridge", "clamshell")
            "spine", "core" -> listOf("heavy_deadlift", "good_morning", "loaded_back_extension") to
                listOf("bird_dog", "dead_bug", "pallof_press")
            "balance" -> listOf("single_leg_deadlift", "pistol_squat", "plyometrics") to
                listOf("supported_balance", "tandem_stance", "stability_exercises")
            else -> emptyList<String>() to emptyList()
        }
    }

    private fun regionKey(region: String): String = when (region.lowercase()) {
        "hip", "hips" -> "hips"
        "shoulder", "shoulders" -> "shoulders"
        "knee", "knees" -> "knees"
        "spine", "lower_back", "back" -> "spine"
        "balance" -> "balance"
        "core" -> "core"
        else -> region.lowercase().ifBlank { "core" }
    }
}
