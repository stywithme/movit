package com.trainingvalidator.poc.network

/**
 * API Models for Prescription, ActivePlan, Progression, and Reassessment endpoints.
 */

// ── Prescription ──

data class PrescriptionResponse(
    val success: Boolean,
    val data: PrescriptionData? = null,
    val error: String? = null
)

data class PrescriptionData(
    val classification: ClassificationData,
    val recommendedProgram: RecommendedProgramData? = null,
    val fallbackUsed: Boolean
)

data class ClassificationData(
    val category: String,
    val priority: Int,
    val requiredType: String,
    val targetDomain: String? = null,
    val targetRegions: List<String>,
    val reason: String
)

data class RecommendedProgramData(
    val id: String,
    val name: Map<String, String>,
    val slug: String,
    val type: String,
    val targetDomain: String? = null,
    val durationWeeks: Int,
    val difficulty: String,
    val coverImageUrl: String? = null,
    val matchReason: String
)

// ── Active Plan ──

data class ActivePlanResponse(
    val success: Boolean,
    val data: ActivePlanData? = null,
    val error: String? = null
)

data class ActivePlanData(
    val id: String,
    val userId: String,
    val status: String,
    val programs: List<ActivePlanProgramData>,
    val createdAt: String,
    val updatedAt: String
)

data class ActivePlanProgramData(
    val id: String,
    val sortOrder: Int,
    val status: String,
    val scheduledStartDate: String? = null,
    val actualStartDate: String? = null,
    val completedAt: String? = null,
    val program: PlanProgramInfo? = null,
    val progress: PlanProgressInfo
)

data class PlanProgramInfo(
    val id: String,
    val name: Map<String, String>,
    val slug: String,
    val type: String,
    val durationWeeks: Int,
    val difficulty: String,
    val coverImageUrl: String? = null
)

data class PlanProgressInfo(
    val completedDays: Int,
    val totalDays: Int,
    val currentWeek: Int,
    val currentDay: Int
)

// ── Today Plan ──

data class TodayPlanResponse(
    val success: Boolean,
    val data: TodayPlanData? = null,
    val error: String? = null
)

data class TodayPlanData(
    val activePlanStatus: String,
    val currentProgram: TodayProgramData? = null,
    val nextReassessment: NextReassessmentData? = null
)

data class TodayProgramData(
    val name: Map<String, String>,
    val weekNumber: Int,
    val dayNumber: Int,
    val dayType: String,
    val isRestDay: Boolean,
    val sessions: List<TodaySessionData>
)

data class TodaySessionData(
    val id: String,
    val name: Map<String, String>,
    val sessionCategory: String? = null,
    val estimatedDurationMin: Int? = null,
    val itemCount: Int,
    val isCompleted: Boolean
)

data class NextReassessmentData(
    val scheduledDate: String,
    val reason: String
)

// ── Progression ──

data class ProgressionHistoryResponse(
    val success: Boolean,
    val data: List<ProgressionEntryData>? = null,
    val error: String? = null
)

data class ProgressionEntryData(
    val id: String,
    val ruleName: String,
    val exerciseName: Map<String, String>? = null,
    val exerciseId: String? = null,
    val field: String,
    val previousValue: Double,
    val newValue: Double,
    val reason: String,
    val appliedAt: String,
    val seen: Boolean = true
)

data class ProgressionMarkSeenRequest(
    val ids: List<String>
)

data class ProgressionMarkSeenResponse(
    val success: Boolean,
    val data: ProgressionMarkSeenData? = null,
    val error: String? = null
)

data class ProgressionMarkSeenData(
    val markedCount: Int
)

// ── Reassessment ──

data class ReassessmentListResponse(
    val success: Boolean,
    val data: List<ReassessmentData>? = null,
    val error: String? = null
)

data class ReassessmentResponse(
    val success: Boolean,
    val data: ReassessmentData? = null,
    val error: String? = null
)

data class ReassessmentData(
    val id: String,
    val userId: String,
    val reason: String,
    val scheduledDate: String,
    val status: String,
    val assessmentId: String? = null,
    val notes: String? = null,
    val createdAt: String
)
