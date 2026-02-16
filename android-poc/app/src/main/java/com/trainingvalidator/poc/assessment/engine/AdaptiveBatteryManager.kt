package com.trainingvalidator.poc.assessment.engine

import com.trainingvalidator.poc.training.report.PostTrainingReport

/**
 * AdaptiveBatteryManager - Determines which additional exercises 
 * should be added based on flags from core exercises.
 * 
 * The Body Scan starts with 3 core movements. If specific patterns
 * are detected, additional movements are triggered for confirmation.
 */
object AdaptiveBatteryManager {
    
    enum class AdaptiveExercise(val slug: String, val descriptionAr: String, val descriptionEn: String) {
        FORWARD_FOLD(
            "assessment_forward_fold",
            "حركة إضافية للتأكد من مرونة خلفية الفخذ",
            "Additional movement to confirm hamstring flexibility"
        ),
        SINGLE_LEG_BALANCE(
            "assessment_single_leg_balance",
            "حركة إضافية للتأكد من التوازن",
            "Additional movement to confirm balance"
        )
    }
    
    data class AdaptiveDecision(
        val exercise: AdaptiveExercise,
        val reason: String
    )
    
    /**
     * Check completed reports and determine if adaptive exercises are needed.
     * Called after each core exercise completes.
     * 
     * @param completedReports Reports from already-completed exercises
     * @return List of additional exercises to add
     */
    fun checkForAdaptive(completedReports: List<PostTrainingReport>): List<AdaptiveDecision> {
        val decisions = mutableListOf<AdaptiveDecision>()
        
        for (report in completedReports) {
            val exerciseId = report.exerciseId.lowercase()
            val summary = report.summary
            
            // Rule 1: Trunk lean in Squat → add Forward Fold to confirm hamstring vs ankle
            if (exerciseId.contains("overhead_squat")) {
                val hasCompensations = summary.positionWarningReps > 0 || summary.positionErrorReps > 0
                val lowRom = (summary.avgROM ?: 100f) < 70f
                
                if (hasCompensations || lowRom) {
                    decisions.add(AdaptiveDecision(
                        AdaptiveExercise.FORWARD_FOLD,
                        "Trunk compensations or limited depth detected in Overhead Squat"
                    ))
                }
            }
            
            // Rule 2: Low stability or LSI in Lunge → add Single-Leg Balance
            if (exerciseId.contains("lunge")) {
                val lowStability = (summary.avgStability ?: 100f) < 60f
                val lowSymmetry = summary.avgSymmetry?.let { it < 80f } ?: false
                
                if (lowStability || lowSymmetry) {
                    decisions.add(AdaptiveDecision(
                        AdaptiveExercise.SINGLE_LEG_BALANCE,
                        "Low stability or asymmetry detected in Lunge"
                    ))
                }
            }
        }
        
        return decisions.distinctBy { it.exercise }
    }
}
