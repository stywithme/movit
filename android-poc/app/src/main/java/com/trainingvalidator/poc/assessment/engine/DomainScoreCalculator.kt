package com.trainingvalidator.poc.assessment.engine

import com.trainingvalidator.poc.assessment.models.*
import com.trainingvalidator.poc.training.report.PerformanceMetricsBuilder
import com.trainingvalidator.poc.training.report.PostTrainingReport

/**
 * DomainScoreCalculator - Calculates Body Scan domains using EXISTING app standards.
 *
 * IMPORTANT:
 * - No custom scoring formulas are introduced here.
 * - We reuse the same report metrics pipeline used by the rest of the app
 *   (PerformanceMetricsBuilder / PostTrainingReport metrics).
 */
object DomainScoreCalculator {
    
    data class DomainInput(
        val report: PostTrainingReport,
        val confidence: ConfidenceLevel,
        val regions: List<AssessmentRegion>
    )
    
    /**
     * Calculate all 4 domain scores from assessment reports.
     */
    fun calculate(inputs: List<DomainInput>): DomainScores {
        val mobility = calculateMobility(inputs)
        val control = calculateControl(inputs)
        val symmetry = calculateSymmetry(inputs)
        val safety = calculateSafety(inputs)
        
        return DomainScores(
            mobility = mobility,
            control = control,
            symmetry = symmetry,
            safety = safety
        )
    }
    
    /**
     * Mobility domain = ROM metric from existing Form card pipeline.
     */
    private fun calculateMobility(inputs: List<DomainInput>): Float {
        var totalWeight = 0f
        var weightedSum = 0f

        for (input in inputs) {
            val weight = confidenceWeight(input.confidence)
            if (weight <= 0f) continue

            val metrics = PerformanceMetricsBuilder.build(input.report)
            val mobility = (
                metrics.formCard.rom?.value
                    ?: input.report.summary.avgROM
                    ?: input.report.summary.averageScore
                ).coerceIn(0f, 100f)

            weightedSum += mobility * weight
            totalWeight += weight
        }

        return if (totalWeight > 0) (weightedSum / totalWeight).coerceIn(0f, 100f) else 50f
    }

    /**
     * Control domain = existing Control card overall score.
     */
    private fun calculateControl(inputs: List<DomainInput>): Float {
        var totalWeight = 0f
        var weightedSum = 0f

        for (input in inputs) {
            val weight = confidenceWeight(input.confidence)
            if (weight <= 0f) continue

            val metrics = PerformanceMetricsBuilder.build(input.report)
            val control = metrics.controlCard.overallScore.value.coerceIn(0f, 100f)

            weightedSum += control * weight
            totalWeight += weight
        }

        return if (totalWeight > 0) (weightedSum / totalWeight).coerceIn(0f, 100f) else 50f
    }

    /**
     * Symmetry domain = existing Form card symmetry metric.
     * Returns null if no bilateral exercises were performed.
     */
    private fun calculateSymmetry(inputs: List<DomainInput>): Float? {
        var totalWeight = 0f
        var weightedSum = 0f

        for (input in inputs) {
            val weight = confidenceWeight(input.confidence)
            if (weight <= 0f) continue

            val metrics = PerformanceMetricsBuilder.build(input.report)
            val sym = metrics.formCard.symmetry?.value ?: input.report.summary.avgSymmetry
            if (sym == null || sym <= 0f) continue

            weightedSum += sym.coerceIn(0f, 100f) * weight
            totalWeight += weight
        }

        return if (totalWeight > 0f) (weightedSum / totalWeight).coerceIn(0f, 100f) else null
    }

    /**
     * Safety domain = existing Safety card overall score.
     */
    private fun calculateSafety(inputs: List<DomainInput>): Float {
        var totalWeight = 0f
        var weightedSum = 0f

        for (input in inputs) {
            val weight = confidenceWeight(input.confidence)
            if (weight <= 0f) continue

            val metrics = PerformanceMetricsBuilder.build(input.report)
            val safety = metrics.safetyCard.overallScore.value.coerceIn(0f, 100f)

            weightedSum += safety * weight
            totalWeight += weight
        }

        return if (totalWeight > 0f) (weightedSum / totalWeight).coerceIn(0f, 100f) else 50f
    }

    private fun confidenceWeight(confidence: ConfidenceLevel): Float = when (confidence) {
        ConfidenceLevel.HIGH -> 1f
        ConfidenceLevel.MEDIUM -> 0.6f
        ConfidenceLevel.LOW -> 0f
    }
}
