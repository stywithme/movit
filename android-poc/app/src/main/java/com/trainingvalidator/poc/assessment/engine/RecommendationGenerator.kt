package com.trainingvalidator.poc.assessment.engine

import com.trainingvalidator.poc.assessment.models.*
import com.trainingvalidator.poc.training.models.LocalizedText

/**
 * RecommendationGenerator - Generates training recommendations based on assessment.
 * 
 * Follows the Corrective Exercise Continuum (NASM):
 * 1. Inhibit → 2. Lengthen → 3. Activate → 4. Integrate
 */
object RecommendationGenerator {
    
    fun generate(
        regions: List<AssessmentRegion>,
        hypotheses: List<HypothesisCard>,
        safetyGates: List<SafetyGate>,
        fitnessLevel: FitnessLevel
    ): List<Recommendation> {
        val recommendations = mutableListOf<Recommendation>()
        var priority = 1
        
        // Priority 1: Address safety gates
        for (gate in safetyGates) {
            recommendations.add(Recommendation(
                priority = priority++,
                phase = CorrectionPhase.INHIBIT,
                targetRegion = gate.region,
                description = LocalizedText(
                    ar = "برنامج تأهيل ${gate.region.labelAr} — أولوية قصوى",
                    en = "${gate.region.labelEn} rehabilitation program — top priority"
                )
            ))
        }
        
        // Priority 2: Address confirmed hypotheses
        for (hypothesis in hypotheses) {
            for (rec in hypothesis.recommendations) {
                if (rec.priority == RecommendationPriority.HIGH) {
                    val phase = when (rec.type) {
                        RecommendationType.STRETCH -> CorrectionPhase.LENGTHEN
                        RecommendationType.STRENGTHEN -> CorrectionPhase.ACTIVATE
                        RecommendationType.MOBILIZE -> CorrectionPhase.INHIBIT
                    }
                    
                    val targetRegion = hypothesis.possibleCauses
                        .filter { it.status == CauseStatus.CONFIRMED }
                        .firstOrNull()
                        ?.let { inferRegion(it.cause.en) }
                        ?: BodyRegion.CORE
                    
                    recommendations.add(Recommendation(
                        priority = priority++,
                        phase = phase,
                        targetRegion = targetRegion,
                        description = rec.description
                    ))
                }
            }
        }
        
        // Priority 3: Address LIMITED regions (not gated but needs work)
        for (region in regions.filter { it.status == RegionStatus.AVERAGE }) {
            recommendations.add(Recommendation(
                priority = priority++,
                phase = CorrectionPhase.LENGTHEN,
                targetRegion = region.region,
                description = LocalizedText(
                    ar = "تحسين مرونة ${region.region.labelAr}",
                    en = "Improve ${region.region.labelEn} mobility"
                )
            ))
        }
        
        // Priority 4: General recommendation based on fitness level
        if (fitnessLevel == FitnessLevel.LIMITED || fitnessLevel == FitnessLevel.NEEDS_REHAB) {
            recommendations.add(Recommendation(
                priority = priority,
                phase = CorrectionPhase.INTEGRATE,
                targetRegion = BodyRegion.CORE,
                description = LocalizedText(
                    ar = "برنامج تصحيحي شامل 4-6 أسابيع قبل تدريب القوة",
                    en = "Comprehensive corrective program 4-6 weeks before strength training"
                )
            ))
        }
        
        return recommendations
    }
    
    private fun inferRegion(causeText: String): BodyRegion {
        val lower = causeText.lowercase()
        return when {
            "ankle" in lower -> BodyRegion.ANKLE
            "shoulder" in lower -> BodyRegion.SHOULDER
            "hip" in lower -> BodyRegion.HIP
            "knee" in lower -> BodyRegion.KNEE
            "core" in lower || "trunk" in lower -> BodyRegion.CORE
            "back" in lower -> BodyRegion.LOWER_BACK
            "chest" in lower -> BodyRegion.SHOULDER
            "glut" in lower -> BodyRegion.HIP
            else -> BodyRegion.CORE
        }
    }
}
