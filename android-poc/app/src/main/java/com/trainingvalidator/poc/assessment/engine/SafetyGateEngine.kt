package com.trainingvalidator.poc.assessment.engine

import com.trainingvalidator.poc.assessment.models.*
import com.trainingvalidator.poc.training.models.LocalizedText

/**
 * SafetyGateEngine - Evaluates assessment results to determine safety restrictions.
 * 
 * Safety Gates CANNOT be overridden by overall Body Score.
 * They are independent per-region restrictions that block dangerous exercise progression.
 */
object SafetyGateEngine {
    
    /**
     * Evaluate regions and pain flags to produce safety gates.
     */
    fun evaluate(
        regions: List<AssessmentRegion>,
        painFlags: List<PainFlag>
    ): List<SafetyGate> {
        val gates = mutableListOf<SafetyGate>()
        
        // Pain flags = immediate full block
        for (pain in painFlags) {
            val region = parseRegion(pain.region)
            if (region != null) {
                gates.add(SafetyGate(
                    region = region,
                    reason = LocalizedText(
                        ar = "تم الإبلاغ عن ألم في هذه المنطقة — ننصح باستشارة متخصص",
                        en = "Pain reported in this area — consult a specialist"
                    ),
                    blockedExerciseTypes = getExercisesForRegion(region),
                    allowedAlternatives = emptyList(),
                    resolveCondition = "Medical clearance required"
                ))
            }
        }
        
        // Region-based gates
        for (region in regions) {
            if (region.confidence == ConfidenceLevel.LOW) {
                gates.add(SafetyGate(
                    region = region.region,
                    reason = LocalizedText(
                        ar = "القياس غير موثوق — يحتاج إعادة",
                        en = "Measurement unreliable — needs retest"
                    ),
                    blockedExerciseTypes = emptyList(),
                    allowedAlternatives = emptyList(),
                    resolveCondition = "Retest with better visibility"
                ))
                continue
            }
            
            if (region.status.triggersGate()) {
                val (blocked, alternatives) = getGateActions(region.region, region.status)
                gates.add(SafetyGate(
                    region = region.region,
                    reason = getGateReason(region.region, region.status),
                    blockedExerciseTypes = blocked,
                    allowedAlternatives = alternatives,
                    resolveCondition = "Region score > 50%"
                ))
            }
        }
        
        return gates.distinctBy { "${it.region}_${it.reason.en}" }
    }
    
    private fun getGateActions(region: BodyRegion, status: RegionStatus): Pair<List<String>, List<String>> {
        return when (region) {
            BodyRegion.ANKLE -> Pair(
                listOf("deep_squat", "pistol_squat", "jump_squat"),
                listOf("box_squat", "leg_press", "heel_elevated_squat")
            )
            BodyRegion.SHOULDER -> Pair(
                listOf("overhead_press", "snatch", "handstand"),
                listOf("landmine_press", "lateral_raise", "face_pull")
            )
            BodyRegion.KNEE -> Pair(
                listOf("deep_lunge", "sissy_squat", "jump_lunge"),
                listOf("box_squat", "leg_curl", "step_up")
            )
            BodyRegion.HIP -> Pair(
                listOf("full_squat", "sumo_deadlift", "wide_stance"),
                listOf("hip_hinge", "glute_bridge", "clamshell")
            )
            BodyRegion.LOWER_BACK -> Pair(
                listOf("heavy_deadlift", "good_morning", "loaded_back_extension"),
                listOf("bird_dog", "dead_bug", "pallof_press")
            )
            BodyRegion.BALANCE -> Pair(
                listOf("single_leg_deadlift", "pistol_squat", "plyometrics"),
                listOf("supported_balance", "tandem_stance", "stability_exercises")
            )
            else -> Pair(emptyList(), emptyList())
        }
    }
    
    private fun getGateReason(region: BodyRegion, status: RegionStatus): LocalizedText {
        val severity = if (status == RegionStatus.WEAK) "شديدة" else "ملحوظة"
        val severityEn = if (status == RegionStatus.WEAK) "severe" else "notable"
        return LocalizedText(
            ar = "محدودية ${severity} في ${region.labelAr}",
            en = "${severityEn.replaceFirstChar { it.uppercase() }} limitation in ${region.labelEn}"
        )
    }
    
    private fun getExercisesForRegion(region: BodyRegion): List<String> {
        return getGateActions(region, RegionStatus.WEAK).first
    }
    
    private fun parseRegion(regionStr: String): BodyRegion? {
        return try {
            BodyRegion.valueOf(regionStr.uppercase())
        } catch (e: Exception) {
            when (regionStr.lowercase()) {
                "shoulder", "shoulders" -> BodyRegion.SHOULDER
                "hip", "hips" -> BodyRegion.HIP
                "knee", "knees" -> BodyRegion.KNEE
                "ankle", "ankles" -> BodyRegion.ANKLE
                "back", "lower_back", "spine" -> BodyRegion.LOWER_BACK
                "core", "trunk" -> BodyRegion.CORE
                else -> null
            }
        }
    }
}
