package com.trainingvalidator.poc.network

/**
 * Assessment API Models
 *
 * Request/response models for the backend assessment endpoints.
 */

// ── Upload response ──

/** Prescription snippet returned with POST /assessment (auto-enroll or suggestion only). */
data class AssessmentPrescriptionFromUpload(
    val programId: String,
    val programName: Map<String, String>? = null,
    val levelNumber: Int,
    val enrolled: Boolean,
)

/** Parsed outcome after upload; passed to AssessmentResultActivity. */
data class AssessmentUploadOutcome(
    val serverId: String?,
    val autoPrescription: AssessmentPrescriptionFromUpload?,
    val recommendation: AssessmentPrescriptionFromUpload?,
)

data class AssessmentUploadResponse(
    val success: Boolean,
    val data: AssessmentData? = null,
    val error: String? = null
)

data class AssessmentData(
    val id: String,
    val userId: String,
    val type: String,
    val bodyScore: Double,
    val mobilityScore: Double,
    val controlScore: Double,
    val symmetryScore: Double?,
    val safetyScore: Double,
    val levelNumber: Int? = null,
    /** @deprecated Prefer [levelNumber]; kept for backward compatibility. */
    val fitnessLevel: String? = null,
    val completedAt: String,
    val createdAt: String,
    val autoPrescription: AssessmentPrescriptionFromUpload? = null,
    val recommendation: AssessmentPrescriptionFromUpload? = null,
)

// ── Latest assessment response ──

data class AssessmentApiResponse(
    val success: Boolean,
    val data: AssessmentFullData? = null,
    val error: String? = null
)

data class AssessmentFullData(
    val id: String,
    val userId: String,
    val type: String,
    val bodyScore: Double,
    val mobilityScore: Double,
    val controlScore: Double,
    val symmetryScore: Double?,
    val safetyScore: Double,
    val levelNumber: Int? = null,
    /** @deprecated Prefer [levelNumber]; kept for backward compatibility. */
    val fitnessLevel: String? = null,
    val regions: Any?,
    val symmetryData: Any?,
    val hypotheses: Any?,
    val safetyGates: Any?,
    val painFlags: Any?,
    val recommendations: Any?,
    val parqPassed: Boolean,
    val parqFlags: Any?,
    val rawReportIds: Any?,
    val previousId: String?,
    val durationMs: Int?,
    val movementCount: Int,
    val completedAt: String,
    val createdAt: String
)

// ── Progress comparison response ──

data class AssessmentProgressResponse(
    val success: Boolean,
    val data: AssessmentProgressData? = null,
    val error: String? = null
)

data class AssessmentProgressData(
    val current: AssessmentSnapshot,
    val previous: AssessmentSnapshot? = null,
    val changes: AssessmentChanges? = null
)

data class AssessmentSnapshot(
    val bodyScore: Double,
    val domainScores: DomainScoresData,
    val completedAt: String
)

data class DomainScoresData(
    val mobility: Double,
    val control: Double,
    val symmetry: Double?,
    val safety: Double
)

data class AssessmentChanges(
    val bodyScoreDelta: Double,
    val mobilityDelta: Double,
    val controlDelta: Double,
    val symmetryDelta: Double?,
    val safetyDelta: Double,
    val isRealImprovement: Boolean
)

// ── User stats response ──

data class UserStatsResponse(
    val success: Boolean,
    val data: UserStatsData? = null,
    val error: String? = null
)

data class UserStatsData(
    val weeklyPlannedWorkouts: Int,
    val avgFormScore: Double,
    val streak: Int,
    val totalWorkoutExecutions: Int,
    val totalMinutes: Int,
    val latestAssessment: LatestAssessmentInfo? = null
)

data class LatestAssessmentInfo(
    val bodyScore: Double,
    val fitnessLevel: String,
    val completedAt: String
)
