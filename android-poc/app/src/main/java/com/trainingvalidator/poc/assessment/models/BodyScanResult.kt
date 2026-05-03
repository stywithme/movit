package com.trainingvalidator.poc.assessment.models

import com.trainingvalidator.poc.training.models.LocalizedText

/**
 * BodyScanResult - Complete assessment result from a Body Scan session.
 * 
 * Contains domain scores, regional assessments, hypothesis cards,
 * safety gates, and recommendations.
 */
data class BodyScanResult(
    val id: String,
    val userId: String,
    val type: AssessmentType,
    val bodyScore: Float,
    val domainScores: DomainScores,
    val fitnessLevel: FitnessLevel,
    val regions: List<AssessmentRegion>,
    val symmetryData: Map<String, Float>?,
    val hypotheses: List<HypothesisCard>,
    val safetyGates: List<SafetyGate>,
    val painFlags: List<PainFlag>,
    val recommendations: List<Recommendation>,
    val parqPassed: Boolean,
    val parqFlags: List<String>,
    val rawReportIds: List<String>,
    val previousId: String?,
    val durationMs: Long?,
    val movementCount: Int,
    val completedAt: Long
)

enum class AssessmentType {
    INITIAL,
    PERIODIC,
    POST_PROGRAM,
    /** Level / program exit gate — maps to API `progression`. */
    PROGRESSION,
}

data class DomainScores(
    val mobility: Float,
    val control: Float,
    val symmetry: Float?,
    val safety: Float
) {
    /**
     * Calculate body score using dynamic weights from template, or defaults.
     */
    fun getBodyScore(): Float {
        return try {
            val weights = com.trainingvalidator.poc.assessment.engine.AssessmentTemplateManager.getDomainWeights()
            val symScore = symmetry ?: ((mobility + control) / 2f)
            (mobility * weights.mobility) + (control * weights.control) + (symScore * weights.symmetry) + (safety * weights.safety)
        } catch (e: Exception) {
            // Fallback to hardcoded weights
            val symScore = symmetry ?: ((mobility + control) / 2f)
            (mobility * 0.35f) + (control * 0.25f) + (symScore * 0.20f) + (safety * 0.20f)
        }
    }
}

enum class FitnessLevel(val labelAr: String, val labelEn: String) {
    EXCELLENT("ممتاز", "Excellent"),
    GOOD("جيد", "Good"),
    AVERAGE("متوسط", "Average"),
    LIMITED("محدود", "Limited"),
    NEEDS_REHAB("يحتاج تأهيل", "Needs Rehabilitation");

    companion object {
        /**
         * Determine fitness level from body score.
         * Uses dynamic thresholds from LevelThresholdsManager when available.
         * Falls back to hardcoded defaults when no thresholds are loaded.
         */
        fun fromBodyScore(score: Float): FitnessLevel {
            return try {
                com.trainingvalidator.poc.assessment.engine.LevelThresholdsManager.fromBodyScore(score)
            } catch (e: Exception) {
                // Fallback to hardcoded defaults
                fromBodyScoreDefault(score)
            }
        }

        private fun fromBodyScoreDefault(score: Float): FitnessLevel = when {
            score >= 85f -> EXCELLENT
            score >= 65f -> GOOD
            score >= 45f -> AVERAGE
            score >= 25f -> LIMITED
            else -> NEEDS_REHAB
        }
    }

    fun getLabel(language: String = "en"): String =
        if (language == "ar") labelAr else labelEn
}

enum class ConfidenceLevel {
    HIGH,
    MEDIUM,
    LOW;

    fun getLabel(language: String = "en"): String = when (this) {
        HIGH -> if (language == "ar") "ثقة عالية" else "High Confidence"
        MEDIUM -> if (language == "ar") "ثقة متوسطة" else "Medium Confidence"
        LOW -> if (language == "ar") "يحتاج إعادة" else "Needs Retest"
    }

    fun getColor(): Int = when (this) {
        HIGH -> 0xFF4CAF50.toInt()
        MEDIUM -> 0xFFFFC107.toInt()
        LOW -> 0xFF9E9E9E.toInt()
    }
}

enum class RegionStatus(val labelAr: String, val labelEn: String, val color: Int) {
    EXCELLENT("ممتاز", "Excellent", 0xFF4CAF50.toInt()),
    GOOD("جيد", "Good", 0xFF8BC34A.toInt()),
    AVERAGE("متوسط", "Average", 0xFFFFC107.toInt()),
    LIMITED("محدود", "Limited", 0xFFFF9800.toInt()),
    WEAK("ضعيف", "Weak", 0xFFFF5252.toInt()),
    INCONCLUSIVE("غير مؤكد", "Inconclusive", 0xFF9E9E9E.toInt());

    companion object {
        fun fromScore(score: Float, confidence: ConfidenceLevel): RegionStatus {
            if (confidence == ConfidenceLevel.LOW) return INCONCLUSIVE
            return when {
                score >= 85f -> EXCELLENT
                score >= 65f -> GOOD
                score >= 45f -> AVERAGE
                score >= 25f -> LIMITED
                else -> WEAK
            }
        }
    }

    fun getLabel(language: String = "en"): String =
        if (language == "ar") labelAr else labelEn

    fun triggersGate(): Boolean = this == LIMITED || this == WEAK
}
