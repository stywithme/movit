package com.trainingvalidator.poc.assessment.engine

import com.trainingvalidator.poc.assessment.models.*
import com.trainingvalidator.poc.training.report.PerformanceMetricsBuilder
import com.trainingvalidator.poc.training.report.PerformanceRating
import com.trainingvalidator.poc.training.report.PostTrainingReport
import java.util.UUID

/**
 * AssessmentEngine - Three-layer processing engine for Body Scan.
 * 
 * Layer 1: MEASUREMENT — Extract absolute ROM + confidence level
 * Layer 2: INTERPRETATION — Compare with norms, generate hypotheses
 * Layer 3: PRESCRIPTION — Generate recommendations + safety gates
 * 
 * Takes a list of PostTrainingReport (one per assessment movement)
 * and produces a BodyScanResult.
 */
class AssessmentEngine {
    
    /**
     * Process assessment reports into a BodyScanResult.
     * 
     * @param reports List of PostTrainingReport from each assessment movement
     * @param userId User ID
     * @param assessmentType Type of assessment (initial, periodic, post_program)
     * @param parqPassed Whether PAR-Q+ screening passed
     * @param parqFlags Which PAR-Q+ questions were flagged
     * @param painFlags Any pain reported during assessment
     * @param previousResult Previous assessment for comparison (null for initial)
     */
    fun process(
        reports: List<PostTrainingReport>,
        userId: String,
        assessmentType: AssessmentType,
        parqPassed: Boolean,
        parqFlags: List<String>,
        painFlags: List<PainFlag>,
        previousResult: BodyScanResult?,
        durationMs: Long
    ): BodyScanResult {
        // ═══════════════════════════════════════════════
        // LAYER 1: MEASUREMENT
        // ═══════════════════════════════════════════════
        
        val reportConfidences = reports.map { report ->
            report to ConfidenceCalculator.calculate(report)
        }
        
        val allRegions = mutableListOf<AssessmentRegion>()
        val domainInputs = mutableListOf<DomainScoreCalculator.DomainInput>()
        
        for ((report, confidence) in reportConfidences) {
            val regions = extractRegions(report, confidence)
            allRegions.addAll(regions)
            domainInputs.add(DomainScoreCalculator.DomainInput(report, confidence, regions))
        }
        
        // ═══════════════════════════════════════════════
        // LAYER 2: INTERPRETATION
        // ═══════════════════════════════════════════════
        
        val domainScores = DomainScoreCalculator.calculate(domainInputs)
        val bodyScore = domainScores.getBodyScore()
        val fitnessLevel = FitnessLevel.fromBodyScore(bodyScore)
        
        val symmetryData = extractSymmetryData(reports)
        val hypotheses = HypothesisGenerator.generate(reports, allRegions, reportConfidences)
        
        // ═══════════════════════════════════════════════
        // LAYER 3: PRESCRIPTION
        // ═══════════════════════════════════════════════
        
        val safetyGates = SafetyGateEngine.evaluate(allRegions, painFlags)
        val recommendations = RecommendationGenerator.generate(
            allRegions, hypotheses, safetyGates, fitnessLevel
        )
        
        return BodyScanResult(
            id = UUID.randomUUID().toString(),
            userId = userId,
            type = assessmentType,
            bodyScore = bodyScore,
            domainScores = domainScores,
            fitnessLevel = fitnessLevel,
            regions = allRegions,
            symmetryData = symmetryData,
            hypotheses = hypotheses,
            safetyGates = safetyGates,
            painFlags = painFlags,
            recommendations = recommendations,
            parqPassed = parqPassed,
            parqFlags = parqFlags,
            rawReportIds = reports.map { it.id },
            previousId = previousResult?.id,
            durationMs = durationMs,
            movementCount = reports.size,
            completedAt = System.currentTimeMillis()
        )
    }
    
    /**
     * Extract AssessmentRegion data from a PostTrainingReport.
     *
     * Uses existing app metrics pipeline (PerformanceMetricsBuilder) to keep
     * scoring standards unified with the rest of the app.
     */
    private fun extractRegions(
        report: PostTrainingReport,
        confidence: ConfidenceLevel
    ): List<AssessmentRegion> {
        val regions = mutableListOf<AssessmentRegion>()
        val summary = report.summary

        if (summary.totalReps == 0 && summary.countedReps == 0) return regions

        val metrics = PerformanceMetricsBuilder.build(report)
        val mobilityScore = (
            metrics.formCard.rom?.value
                ?: summary.avgROM
                ?: summary.averageScore
            ).coerceIn(0f, 100f)
        val controlScore = metrics.controlCard.overallScore.value.coerceIn(0f, 100f)
        val safetyScore = metrics.safetyCard.overallScore.value.coerceIn(0f, 100f)
        val stability = (
            metrics.safetyCard.stability?.value
                ?: summary.avgStability
                ?: safetyScore
            ).coerceIn(0f, 100f)
        val symmetryFactor = ((metrics.formCard.symmetry?.value ?: summary.avgSymmetry ?: 100f) / 100f)
            .coerceIn(0f, 1.2f)
        val movementQuality = ratingToMovementQuality(summary.rating)

        val exerciseId = report.exerciseId
        val isOverheadSquat = exerciseId.contains("overhead_squat", ignoreCase = true)
        val isLunge = exerciseId.contains("lunge", ignoreCase = true)
        val isShoulderMobility = exerciseId.contains("shoulder", ignoreCase = true)
        val isForwardFold = exerciseId.contains("forward_fold", ignoreCase = true)
        val isBalance = exerciseId.contains("balance", ignoreCase = true)

        when {
            isOverheadSquat -> {
                addRegion(regions, BodyRegion.HIP, RegionSide.CENTER,
                    mobilityScore, "left_hip", movementQuality, stability, confidence)
                addRegion(regions, BodyRegion.KNEE, RegionSide.CENTER,
                    mobilityScore, "left_knee", movementQuality, stability, confidence)
                addRegion(regions, BodyRegion.SHOULDER, RegionSide.CENTER,
                    mobilityScore, "left_shoulder", movementQuality, stability, confidence)
                addCoreRegion(regions, controlScore, movementQuality, stability, confidence)
            }
            isLunge -> {
                addRegion(regions, BodyRegion.HIP, RegionSide.LEFT,
                    mobilityScore, "left_hip", movementQuality, stability, confidence)
                addRegion(regions, BodyRegion.HIP, RegionSide.RIGHT,
                    mobilityScore * symmetryFactor, "right_hip", movementQuality, stability, confidence)
                addRegion(regions, BodyRegion.KNEE, RegionSide.LEFT,
                    mobilityScore, "left_knee", movementQuality, stability, confidence)
                addRegion(regions, BodyRegion.KNEE, RegionSide.RIGHT,
                    mobilityScore * symmetryFactor, "right_knee", movementQuality, stability, confidence)
                addRegion(regions, BodyRegion.BALANCE, RegionSide.CENTER,
                    stability, "left_knee", movementQuality, stability, confidence)
            }
            isShoulderMobility -> {
                addRegion(regions, BodyRegion.SHOULDER, RegionSide.LEFT,
                    mobilityScore, "left_shoulder", movementQuality, stability, confidence)
                addRegion(regions, BodyRegion.SHOULDER, RegionSide.RIGHT,
                    mobilityScore * symmetryFactor, "right_shoulder", movementQuality, stability, confidence)
                addCoreRegion(regions, controlScore, movementQuality, stability, confidence)
            }
            isForwardFold -> {
                addRegion(regions, BodyRegion.LOWER_BACK, RegionSide.CENTER,
                    mobilityScore, "left_hip", movementQuality, stability, confidence)
            }
            isBalance -> {
                addRegion(regions, BodyRegion.BALANCE, RegionSide.LEFT,
                    stability, "left_knee", movementQuality, stability, confidence)
                addRegion(regions, BodyRegion.BALANCE, RegionSide.RIGHT,
                    stability * symmetryFactor, "right_knee", movementQuality, stability, confidence)
            }
            else -> {
                // Unknown assessment movement: keep one generic core region from existing control score.
                addCoreRegion(regions, controlScore, movementQuality, stability, confidence)
            }
        }

        return regions
    }

    private fun ratingToMovementQuality(rating: PerformanceRating): Int = when (rating) {
        PerformanceRating.EXCELLENT,
        PerformanceRating.GOOD -> 3
        PerformanceRating.FAIR -> 2
        PerformanceRating.NEEDS_WORK -> 1
    }
    
    /**
     * Add a region using performance score (0-100 percentage).
     * Uses existing score standards, then maps to reference norm for display.
     */
    private fun addRegion(
        regions: MutableList<AssessmentRegion>,
        bodyRegion: BodyRegion,
        side: RegionSide,
        scorePercent: Float,
        jointCode: String,
        movementQuality: Int,
        stability: Float,
        confidence: ConfidenceLevel
    ) {
        val norm = ReferenceNormsProvider.getNorm(jointCode)
        val errorBand = norm?.errorBand ?: 7f
        val referenceNorm = norm?.midpoint ?: 0f

        val romPct = scorePercent.coerceIn(0f, 100f)
        val absoluteRom = if (referenceNorm > 0f) {
            (referenceNorm * (romPct / 100f)).coerceAtLeast(0f)
        } else {
            0f
        }
        val minRom = (absoluteRom - errorBand).coerceAtLeast(0f)
        val maxRom = absoluteRom + errorBand

        val romMin = if (referenceNorm > 0f) {
            (minRom / referenceNorm * 100f).coerceIn(0f, 120f)
        } else romPct
        val romMax = if (referenceNorm > 0f) {
            (maxRom / referenceNorm * 100f).coerceIn(0f, 120f)
        } else romPct

        val regionalScore = romPct.coerceIn(0f, 100f)
        val status = RegionStatus.fromScore(regionalScore, confidence)

        regions.add(AssessmentRegion(
            region = bodyRegion,
            side = side,
            absoluteRom = absoluteRom,
            errorBand = errorBand,
            referenceNorm = referenceNorm,
            romPercentage = romPct,
            romPercentageMin = romMin,
            romPercentageMax = romMax,
            movementQualityGrade = movementQuality,
            stabilityScore = stability,
            regionalScore = regionalScore,
            confidence = confidence,
            status = status
        ))
    }
    
    private fun addCoreRegion(
        regions: MutableList<AssessmentRegion>,
        coreScore: Float,
        movementQuality: Int,
        stability: Float,
        confidence: ConfidenceLevel
    ) {
        val score = coreScore.coerceIn(0f, 100f)
        val status = RegionStatus.fromScore(score, confidence)

        regions.add(AssessmentRegion(
            region = BodyRegion.CORE,
            side = RegionSide.CENTER,
            absoluteRom = 0f,
            errorBand = 0f,
            referenceNorm = 0f,
            romPercentage = score,
            romPercentageMin = score,
            romPercentageMax = score,
            movementQualityGrade = movementQuality,
            stabilityScore = stability,
            regionalScore = score,
            confidence = confidence,
            status = status
        ))
    }
    
    private fun extractSymmetryData(reports: List<PostTrainingReport>): Map<String, Float>? {
        val data = mutableMapOf<String, Float>()
        for (report in reports) {
            val sym = report.summary.avgSymmetry ?: continue
            val exerciseId = report.exerciseId
            when {
                exerciseId.contains("lunge", ignoreCase = true) -> {
                    data["hip"] = sym
                    data["knee"] = sym
                }
                exerciseId.contains("shoulder", ignoreCase = true) -> {
                    data["shoulder"] = sym
                }
                exerciseId.contains("balance", ignoreCase = true) -> {
                    data["balance"] = sym
                }
            }
        }
        return data.ifEmpty { null }
    }
}
