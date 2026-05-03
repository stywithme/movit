package com.trainingvalidator.poc.assessment

import android.content.Context
import android.util.Log
import com.trainingvalidator.poc.assessment.engine.AssessmentTemplateManager
import com.trainingvalidator.poc.assessment.models.*
import com.trainingvalidator.poc.network.ApiClient
import com.trainingvalidator.poc.network.AssessmentFullData
import com.trainingvalidator.poc.network.AssessmentUploadOutcome
import com.trainingvalidator.poc.storage.AuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AssessmentUploadService — Handles uploading BodyScanResult to the backend.
 *
 * Phase 0: Wires the existing AssessmentEngine output to POST /assessment.
 * The endpoint already exists on the backend; this service maps the Android
 * model to the API format and handles the upload lifecycle.
 */
object AssessmentUploadService {

    private const val TAG = "AssessmentUpload"

    /**
     * Upload a BodyScanResult to the backend.
     *
     * @return Upload outcome (server id + prescription fields from response), or null on auth/network failure.
     */
    suspend fun upload(context: Context, result: BodyScanResult): AssessmentUploadOutcome? =
        withContext(Dispatchers.IO) {
            val authHeader = AuthManager.getAuthHeader(context)
            if (authHeader == null) {
                Log.w(TAG, "No auth token — cannot upload assessment")
                return@withContext null
            }

            try {
                val payload = mapToPayload(result)
                Log.d(TAG, "Uploading assessment: bodyScore=${result.bodyScore}, type=${result.type}")

                val response = ApiClient.mobileSyncApi.uploadAssessment(authHeader, payload)

                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()?.data
                    val serverId = data?.id
                    val outcome = AssessmentUploadOutcome(
                        serverId = serverId,
                        autoPrescription = data?.autoPrescription,
                        recommendation = data?.recommendation,
                    )
                    Log.d(TAG, "Assessment uploaded successfully: id=$serverId")
                    return@withContext outcome
                } else {
                    val error = response.body()?.error ?: "HTTP ${response.code()}"
                    Log.e(TAG, "Assessment upload failed: $error")
                    return@withContext null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Assessment upload error", e)
                return@withContext null
            }
        }

    /**
     * Fetch the user's latest assessment from the backend.
     *
     * Used to pass as `previousResult` to AssessmentEngine.process()
     * so the comparison delta is calculated correctly.
     *
     * @return The latest assessment data, or null if none/error.
     */
    suspend fun fetchLatest(context: Context): AssessmentFullData? =
        withContext(Dispatchers.IO) {
            val authHeader = AuthManager.getAuthHeader(context)
            if (authHeader == null) {
                Log.w(TAG, "No auth token — cannot fetch latest assessment")
                return@withContext null
            }

            try {
                val response = ApiClient.mobileSyncApi.getLatestAssessment(authHeader)
                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()?.data
                    Log.d(TAG, "Fetched latest assessment: id=${data?.id}, bodyScore=${data?.bodyScore}")
                    return@withContext data
                } else {
                    Log.d(TAG, "No previous assessment found (${response.code()})")
                    return@withContext null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch latest assessment (offline?)", e)
                return@withContext null
            }
        }

    /**
     * Fetch the latest assessment and convert it to a BodyScanResult.
     * Used to provide previousResult to AssessmentEngine.process().
     */
    suspend fun fetchLatestAsBodyScanResult(context: Context): BodyScanResult? {
        val apiData = fetchLatest(context) ?: return null
        return apiData.toBodyScanResult()
    }

    /**
     * Convert AssessmentFullData (API response) to BodyScanResult (domain model).
     * Complex nested fields (regions, hypotheses, etc.) are parsed from generic JSON.
     */
    private fun AssessmentFullData.toBodyScanResult(): BodyScanResult {
        val assessmentType = when (type.lowercase()) {
            "periodic" -> AssessmentType.PERIODIC
            "post_program" -> AssessmentType.POST_PROGRAM
            "progression", "level_specific" -> AssessmentType.PROGRESSION
            else -> AssessmentType.INITIAL
        }
        val level = when (fitnessLevel.lowercase()) {
            "excellent" -> FitnessLevel.EXCELLENT
            "good" -> FitnessLevel.GOOD
            "average" -> FitnessLevel.AVERAGE
            "limited" -> FitnessLevel.LIMITED
            else -> FitnessLevel.NEEDS_REHAB
        }
        return BodyScanResult(
            id = id,
            userId = userId,
            type = assessmentType,
            bodyScore = bodyScore.toFloat(),
            domainScores = DomainScores(
                mobility = mobilityScore.toFloat(),
                control = controlScore.toFloat(),
                symmetry = symmetryScore?.toFloat(),
                safety = safetyScore.toFloat()
            ),
            fitnessLevel = level,
            regions = emptyList(),
            symmetryData = null,
            hypotheses = emptyList(),
            safetyGates = emptyList(),
            painFlags = emptyList(),
            recommendations = emptyList(),
            parqPassed = parqPassed,
            parqFlags = emptyList(),
            rawReportIds = emptyList(),
            previousId = previousId,
            durationMs = durationMs?.toLong(),
            movementCount = movementCount,
            completedAt = try {
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                    .parse(completedAt)?.time ?: System.currentTimeMillis()
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
        )
    }

    /**
     * Map the Android BodyScanResult to the backend's expected API payload.
     */
    private fun mapToPayload(result: BodyScanResult): Map<String, Any?> {
        return mapOf(
            "type" to result.type.name.lowercase(),
            "bodyScore" to result.bodyScore,
            "mobilityScore" to result.domainScores.mobility,
            "controlScore" to result.domainScores.control,
            "symmetryScore" to result.domainScores.symmetry,
            "safetyScore" to result.domainScores.safety,
            "fitnessLevel" to mapFitnessLevel(result.fitnessLevel),
            "regions" to result.regions.map { mapRegion(it) },
            "symmetryData" to result.symmetryData,
            "hypotheses" to result.hypotheses.map { mapHypothesis(it) },
            "safetyGates" to result.safetyGates.map { mapSafetyGate(it) },
            "painFlags" to result.painFlags.map { mapOf("movement" to it.movement, "region" to it.region) },
            "recommendations" to result.recommendations.map { mapRecommendation(it) },
            "parqPassed" to result.parqPassed,
            "parqFlags" to result.parqFlags,
            "rawReportIds" to result.rawReportIds,
            "previousId" to result.previousId,
            "durationMs" to result.durationMs?.toInt(),
            "movementCount" to result.movementCount,
            "templateId" to AssessmentTemplateManager.getTemplateId(),
        )
    }

    private fun mapFitnessLevel(level: FitnessLevel): String = when (level) {
        FitnessLevel.EXCELLENT -> "excellent"
        FitnessLevel.GOOD -> "good"
        FitnessLevel.AVERAGE -> "average"
        FitnessLevel.LIMITED -> "limited"
        FitnessLevel.NEEDS_REHAB -> "needs_rehab"
    }

    private fun mapRegion(region: AssessmentRegion): Map<String, Any?> = mapOf(
        "region" to region.region.name.lowercase(),
        "side" to region.side.name.lowercase(),
        "absoluteRom" to region.absoluteRom,
        "errorBand" to region.errorBand,
        "referenceNorm" to region.referenceNorm,
        "romPercentage" to region.romPercentage,
        "romPercentageMin" to region.romPercentageMin,
        "romPercentageMax" to region.romPercentageMax,
        "movementQualityGrade" to region.movementQualityGrade,
        "stabilityScore" to region.stabilityScore,
        "regionalScore" to region.regionalScore,
        "confidence" to region.confidence.name.lowercase(),
        "status" to region.status.name.lowercase(),
    )

    private fun mapHypothesis(card: HypothesisCard): Map<String, Any?> = mapOf(
        "observation" to mapOf("ar" to card.observation.ar, "en" to card.observation.en),
        "possibleCauses" to card.possibleCauses.map { cause ->
            mapOf(
                "cause" to mapOf("ar" to cause.cause.ar, "en" to cause.cause.en),
                "status" to cause.status.name.lowercase(),
                "evidence" to cause.evidence,
            )
        },
        "recommendations" to card.recommendations.map { rec ->
            mapOf(
                "type" to rec.type.name.lowercase(),
                "priority" to rec.priority.name.lowercase(),
                "description" to mapOf("ar" to rec.description.ar, "en" to rec.description.en),
            )
        },
        "confidence" to card.confidence.name.lowercase(),
    )

    private fun mapSafetyGate(gate: SafetyGate): Map<String, Any?> = mapOf(
        "region" to gate.region.name.lowercase(),
        "reason" to mapOf("ar" to gate.reason.ar, "en" to gate.reason.en),
        "blockedExerciseTypes" to gate.blockedExerciseTypes,
        "allowedAlternatives" to gate.allowedAlternatives,
        "resolveCondition" to gate.resolveCondition,
    )

    private fun mapRecommendation(rec: Recommendation): Map<String, Any?> = mapOf(
        "priority" to rec.priority,
        "type" to rec.phase.name.lowercase(),
        "exercises" to rec.exerciseSlugs,
    )
}
