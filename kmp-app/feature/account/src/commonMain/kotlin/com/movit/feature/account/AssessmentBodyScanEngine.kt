package com.movit.feature.account

import com.movit.core.network.dto.AssessmentRegionDto
import com.movit.core.network.dto.BodyScanUploadRequestDto
import com.movit.core.training.model.JointAngles
import com.movit.core.training.model.PoseFrame
import kotlin.math.abs
import kotlin.math.roundToInt

class AssessmentBodyScanEngine(
    private val template: AssessmentTemplateUi,
    private val language: String = "en",
    private val samplesPerMovement: Int = 24,
) {
    private val movements = template.safeMovements
    private val samples = movements.map { mutableListOf<MovementSample>() }
    private var currentIndex = 0
    private var firstFrameTimestampMs: Long? = null
    private var lastFrameTimestampMs: Long? = null

    val isComplete: Boolean
        get() = currentIndex >= movements.size

    val progressPercent: Int
        get() {
            if (movements.isEmpty()) return 100
            val completed = samples.count { it.size >= samplesPerMovement }
            val currentProgress = samples.getOrNull(currentIndex)
                ?.size
                ?.coerceAtMost(samplesPerMovement)
                ?: samplesPerMovement
            val raw = ((completed * samplesPerMovement) + currentProgress).toDouble() /
                (movements.size * samplesPerMovement).toDouble()
            return (raw * 100.0).roundToInt().coerceIn(0, 100)
        }

    val currentMovementIndex: Int
        get() = currentIndex.coerceIn(0, movements.lastIndex.coerceAtLeast(0))

    fun ingest(frame: PoseFrame?): AssessmentScanUpdate {
        if (isComplete) return snapshot(poseDetected = frame?.hasPose == true)
        if (frame?.hasPose != true) return snapshot(poseDetected = false)
        firstFrameTimestampMs = firstFrameTimestampMs ?: frame.timestampMs
        lastFrameTimestampMs = frame.timestampMs

        val movement = movements[currentIndex]
        samples[currentIndex] += scoreMovement(movement, frame)
        if (samples[currentIndex].size >= samplesPerMovement) {
            currentIndex += 1
        }
        return snapshot(poseDetected = true)
    }

    fun buildResult(
        parqPassed: Boolean,
        parqFlags: List<String>,
    ): AssessmentBodyScanResult {
        val summaries = movements.mapIndexedNotNull { index, movement ->
            val movementSamples = samples.getOrNull(index).orEmpty()
            if (movementSamples.isEmpty()) null else summarize(movement, movementSamples)
        }
        val safeSummaries = summaries.ifEmpty {
            movements.map { movement ->
                MovementSummary(
                    movement = movement,
                    mobility = 0.0,
                    control = 0.0,
                    symmetry = 0.0,
                    safety = 0.0,
                    absoluteRom = 0.0,
                    referenceNorm = movement.referenceNormDegrees ?: 0.0,
                    qualityGrade = 1,
                    confidence = "low",
                )
            }
        }
        val mobility = safeSummaries.map { it.mobility }.average()
        val control = safeSummaries.map { it.control }.average()
        val symmetry = safeSummaries.map { it.symmetry }.average()
        val safety = if (parqPassed) {
            safeSummaries.map { it.safety }.average()
        } else {
            safeSummaries.map { it.safety }.average().coerceAtMost(60.0)
        }
        val bodyScore = weightedBodyScore(mobility, control, symmetry, safety)
        val regions = safeSummaries
            .groupBy { regionKey(it.movement.targetRegion) }
            .map { (region, regionSummaries) -> regionDto(region, regionSummaries) }
        val uiRegions = regions.map { dto ->
            AssessmentRegionUi(
                regionKey = dto.region,
                score = dto.regionalScore.roundToInt().coerceIn(0, 100),
                tone = dto.regionalScore.toTone(),
                confidence = dto.confidence,
                status = dto.status,
            )
        }
        val safetyGates = AssessmentSafetyGateEngine.evaluate(regions, parqFlags)
        val uiResults = AssessmentResultsUi(
            bodyScore = bodyScore.roundToInt().coerceIn(0, 100),
            levelLabel = levelLabel(bodyScore),
            domains = listOf(
                AssessmentDomainUi("mobility", mobility.roundToInt().coerceIn(0, 100)),
                AssessmentDomainUi("control", control.roundToInt().coerceIn(0, 100)),
                AssessmentDomainUi("symmetry", symmetry.roundToInt().coerceIn(0, 100)),
                AssessmentDomainUi("safety", safety.roundToInt().coerceIn(0, 100)),
            ),
            regions = uiRegions,
            insights = insights(uiRegions),
            safetyGates = safetyGates,
            resultsSavedToServer = false,
        )
        val duration = firstFrameTimestampMs?.let { start ->
            lastFrameTimestampMs?.let { end -> (end - start).coerceAtLeast(0) }
        }
        val uploadRequest = BodyScanUploadRequestDto(
            type = template.type.ifBlank { "initial" },
            bodyScore = bodyScore,
            mobilityScore = mobility,
            controlScore = control,
            symmetryScore = symmetry,
            safetyScore = safety,
            levelNumber = bodyScoreToLevel(bodyScore),
            fitnessLevel = bodyScoreToFitnessLevel(bodyScore),
            regions = regions,
            symmetryData = mapOf("overall" to symmetry),
            recommendations = null,
            rawReportIds = safeSummaries.map { it.movement.exerciseSlug },
            durationMs = duration,
            movementCount = safeSummaries.size,
            templateId = template.templateId,
        )
        return AssessmentBodyScanResult(
            uiResults = uiResults,
            uploadRequest = uploadRequest,
            parqPassed = parqPassed,
            parqFlags = parqFlags,
        )
    }

    private fun snapshot(poseDetected: Boolean): AssessmentScanUpdate =
        AssessmentScanUpdate(
            progressPercent = progressPercent,
            movementIndex = currentMovementIndex,
            movementKey = movements.getOrNull(currentMovementIndex)?.titleKey
                ?: AssessmentDefaults.initialTemplate.movements.first().titleKey,
            movementTotal = movements.size.coerceAtLeast(1),
            poseDetected = poseDetected,
            isComplete = isComplete,
        )

    private fun scoreMovement(movement: AssessmentMovementUi, frame: PoseFrame): MovementSample {
        val angles = frame.angles
        val region = movement.targetRegion.lowercase()
        val mobilityAngle = when {
            "shoulder" in region -> averageAngles(angles.leftShoulder, angles.rightShoulder)
            "knee" in region || "squat" in movement.exerciseSlug -> averageAngles(angles.leftKnee, angles.rightKnee)
            "hip" in region || "fold" in movement.exerciseSlug -> averageAngles(angles.leftHip, angles.rightHip)
            "balance" in region -> averageAngles(angles.leftKnee, angles.rightKnee, angles.leftHip, angles.rightHip)
            else -> averageAngles(angles.leftHip, angles.rightHip, angles.leftShoulder, angles.rightShoulder)
        }
        val reference = movement.referenceNormDegrees ?: defaultReference(region)
        val mobility = if (reference > 0.0 && mobilityAngle != null) {
            (mobilityAngle / reference * 100.0).coerceIn(0.0, 100.0)
        } else {
            45.0
        }
        val symmetry = symmetryScore(angles)
        val visibility = frame.landmarks.orEmpty().map { it.visibility.toDouble() }.averageOrNull() ?: 0.0
        val control = ((symmetry * 0.45) + (visibility * 100.0 * 0.55)).coerceIn(0.0, 100.0)
        val safety = when {
            visibility < 0.45 -> 45.0
            mobility < 35.0 -> 60.0
            else -> (70.0 + visibility * 25.0).coerceIn(0.0, 100.0)
        }
        return MovementSample(
            mobility = mobility,
            control = control,
            symmetry = symmetry,
            safety = safety,
            absoluteRom = mobilityAngle ?: 0.0,
        )
    }

    private fun summarize(movement: AssessmentMovementUi, samples: List<MovementSample>): MovementSummary {
        val mobility = samples.map { it.mobility }.average()
        val control = samples.map { it.control }.average()
        val symmetry = samples.map { it.symmetry }.average()
        val safety = samples.map { it.safety }.average()
        val confidence = when {
            samples.size >= samplesPerMovement && control >= 70.0 -> "high"
            samples.size >= samplesPerMovement / 2 -> "medium"
            else -> "low"
        }
        return MovementSummary(
            movement = movement,
            mobility = mobility,
            control = control,
            symmetry = symmetry,
            safety = safety,
            absoluteRom = samples.map { it.absoluteRom }.average(),
            referenceNorm = movement.referenceNormDegrees ?: defaultReference(movement.targetRegion),
            qualityGrade = when {
                control >= 75.0 && safety >= 70.0 -> 3
                control >= 50.0 -> 2
                else -> 1
            },
            confidence = confidence,
        )
    }

    private fun regionDto(region: String, summaries: List<MovementSummary>): AssessmentRegionDto {
        val score = summaries.map {
            weightedBodyScore(it.mobility, it.control, it.symmetry, it.safety)
        }.average()
        val rom = summaries.map { it.mobility }.average().coerceIn(0.0, 100.0)
        val reference = summaries.map { it.referenceNorm }.filter { it > 0.0 }.averageOrNull() ?: 0.0
        val absoluteRom = summaries.map { it.absoluteRom }.average()
        return AssessmentRegionDto(
            region = region,
            side = summaries.firstOrNull()?.movement?.side?.ifBlank { "center" } ?: "center",
            absoluteRom = absoluteRom,
            errorBand = 7.0,
            referenceNorm = reference,
            romPercentage = rom,
            romPercentageMin = (rom - 7.0).coerceAtLeast(0.0),
            romPercentageMax = (rom + 7.0).coerceAtMost(120.0),
            movementQualityGrade = summaries.map { it.qualityGrade }.average().roundToInt().coerceIn(1, 3),
            stabilityScore = summaries.map { it.control }.average(),
            regionalScore = score.coerceIn(0.0, 100.0),
            confidence = if (summaries.any { it.confidence == "low" }) "medium" else summaries.firstOrNull()?.confidence ?: "medium",
            status = scoreToStatus(score),
        )
    }

    private fun weightedBodyScore(
        mobility: Double,
        control: Double,
        symmetry: Double,
        safety: Double,
    ): Double {
        val weights = template.domainWeights
        val total = weights.mobility + weights.control + weights.symmetry + weights.safety
        val safeTotal = if (total <= 0.0) 1.0 else total
        return (
            mobility * weights.mobility +
                control * weights.control +
                symmetry * weights.symmetry +
                safety * weights.safety
            ) / safeTotal
    }

    private fun symmetryScore(angles: JointAngles): Double {
        val pairs = listOf(
            angles.leftShoulder to angles.rightShoulder,
            angles.leftHip to angles.rightHip,
            angles.leftKnee to angles.rightKnee,
            angles.leftAnkle to angles.rightAnkle,
        )
        val deltas = pairs.mapNotNull { (left, right) ->
            if (left == null || right == null) null else abs(left - right)
        }
        if (deltas.isEmpty()) return 65.0
        return (100.0 - deltas.average() * 1.4).coerceIn(0.0, 100.0)
    }

    private fun averageAngles(vararg values: Double?): Double? =
        values.mapNotNull { it }.averageOrNull()

    private fun List<Double>.averageOrNull(): Double? =
        if (isEmpty()) null else average()

    private fun defaultReference(region: String): Double = when {
        "shoulder" in region.lowercase() -> 170.0
        "knee" in region.lowercase() -> 140.0
        "hip" in region.lowercase() -> 120.0
        "balance" in region.lowercase() -> 100.0
        else -> 110.0
    }

    private fun regionKey(targetRegion: String): String = when (targetRegion.lowercase()) {
        "hip", "hips" -> "hips"
        "shoulder", "shoulders" -> "shoulders"
        "knee", "knees" -> "knees"
        "spine", "lower_back", "back" -> "spine"
        "balance" -> "balance"
        "core" -> "core"
        else -> targetRegion.lowercase().ifBlank { "core" }
    }

    private fun Double.toTone(): AssessmentRegionTone = when {
        this >= 80.0 -> AssessmentRegionTone.Good
        this < 65.0 -> AssessmentRegionTone.Warning
        else -> AssessmentRegionTone.Neutral
    }

    private fun scoreToStatus(score: Double): String = when {
        score >= 85.0 -> "excellent"
        score >= 65.0 -> "good"
        score >= 45.0 -> "average"
        score >= 25.0 -> "limited"
        else -> "weak"
    }

    private fun bodyScoreToLevel(score: Double): Int = when {
        score >= 85.0 -> 5
        score >= 65.0 -> 4
        score >= 45.0 -> 3
        score >= 25.0 -> 2
        else -> 1
    }

    private fun bodyScoreToFitnessLevel(score: Double): String = when {
        score >= 85.0 -> "excellent"
        score >= 65.0 -> "good"
        score >= 45.0 -> "average"
        score >= 25.0 -> "limited"
        else -> "needs_rehab"
    }

    private fun levelLabel(score: Double): String {
        val level = bodyScoreToLevel(score)
        val label = when (bodyScoreToFitnessLevel(score)) {
            "excellent" -> if (language == "ar") "ممتاز" else "Excellent"
            "good" -> if (language == "ar") "جيد" else "Good"
            "average" -> if (language == "ar") "متوسط" else "Average"
            "limited" -> if (language == "ar") "محدود" else "Limited"
            else -> if (language == "ar") "يحتاج تأهيل" else "Needs rehab"
        }
        return "Level $level · $label"
    }

    private fun insights(regions: List<AssessmentRegionUi>): List<AssessmentInsightUi> {
        val limiting = regions.filter { it.tone == AssessmentRegionTone.Warning }
            .sortedBy { it.score }
            .take(2)
        if (limiting.isEmpty()) {
            return listOf(
                AssessmentInsightUi(
                    titleKey = "assessment_insight_hip_title",
                    messageKey = "assessment_insight_hip_message",
                ),
            )
        }
        return limiting.map { region ->
            AssessmentInsightUi(
                titleKey = "assessment_insight_region_limit_title",
                messageKey = "assessment_insight_region_limit_message",
                titleArgs = listOf(region.regionKey.replaceFirstChar { it.uppercase() }),
                messageArgs = listOf(region.score),
            )
        }
    }
}

data class AssessmentScanUpdate(
    val progressPercent: Int,
    val movementIndex: Int,
    val movementKey: String,
    val movementTotal: Int,
    val poseDetected: Boolean,
    val isComplete: Boolean,
)

data class AssessmentBodyScanResult(
    val uiResults: AssessmentResultsUi,
    val uploadRequest: BodyScanUploadRequestDto,
    val parqPassed: Boolean,
    val parqFlags: List<String>,
)

private data class MovementSample(
    val mobility: Double,
    val control: Double,
    val symmetry: Double,
    val safety: Double,
    val absoluteRom: Double,
)

private data class MovementSummary(
    val movement: AssessmentMovementUi,
    val mobility: Double,
    val control: Double,
    val symmetry: Double,
    val safety: Double,
    val absoluteRom: Double,
    val referenceNorm: Double,
    val qualityGrade: Int,
    val confidence: String,
)
