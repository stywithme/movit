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
    /** Present on POST /api/mobile/plan/complete when the program is finalized on the server. */
    val completion: ProgramCompleteData? = null,
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
    val nextReassessment: NextReassessmentData? = null,
    val isPaused: Boolean? = null,
    val catchUpSuggestion: CatchUpSuggestionData? = null
)

data class CatchUpSuggestionData(
    val missedTrainingDays: Int,
    val message: String,
    val missedSlots: List<MissedSlotData>
)

data class MissedSlotData(
    val weekNumber: Int,
    val dayNumber: Int
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
    val field: String,
    val previousValue: Double,
    val newValue: Double,
    val reason: String,
    val appliedAt: String,
    val seen: Boolean = true,
    val axis: String? = null,
    val decisionType: String? = null
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

// ── Effective plan & overrides (Blueprint) ──

data class EffectivePlanApiResponse(
    val success: Boolean,
    val data: EffectivePlanPayload? = null,
    val error: String? = null
)

data class EffectivePlanPayload(
    val userProgramId: String,
    val programId: String?,
    val weekNumber: Int,
    val dayNumber: Int,
    val sessions: List<EffectivePlanSessionData>
)

data class EffectivePlanSessionData(
    val id: String,
    val name: Map<String, String>?,
    val sortOrder: Int,
    val estimatedDurationMin: Int? = null,
    val items: List<EffectivePlanItemData>
)

data class EffectivePlanItemData(
    val id: String,
    val type: String,
    val exerciseId: String? = null,
    val sets: Int? = null,
    val targetReps: Int? = null,
    val targetDuration: Int? = null,
    val restBetweenSetsMs: Int? = null,
    val weightKg: Double? = null,
    val weightPerSet: List<Double>? = null,
    val notes: Map<String, String>? = null,
    val restDurationMs: Int? = null,
    val sortOrder: Int,
    val role: String? = null,
    val intent: String? = null,
    val coachingNotes: Map<String, @JvmSuppressWildcards Any>? = null,
    val skipped: Boolean? = null,
    val suggestion: EffectivePlanSuggestion? = null,
    val allowedSubstitutions: List<String>? = null
)

data class EffectivePlanSuggestion(
    val suggestedWeightKg: Double? = null,
    val suggestedReps: Int? = null,
    val suggestedSets: Int? = null,
    val suggestedDuration: Int? = null,
    val source: String? = null
)

data class UserProgramOverridesApiResponse(
    val success: Boolean,
    val data: List<UserProgramOverrideData>? = null,
    val error: String? = null
)

data class UserProgramOverrideData(
    val id: String,
    val userProgramId: String,
    val weekNumber: Int,
    val dayNumber: Int,
    val sessionItemId: String,
    val overrideType: String,
    val reasonCode: String? = null,
    val data: Map<String, @JvmSuppressWildcards Any>? = null,
    val appliedBy: String? = null,
    val createdAt: String? = null
)

data class UserProgramOverrideCreateApiResponse(
    val success: Boolean,
    val data: UserProgramOverrideData? = null,
    val error: String? = null
)

data class ProgramCompleteApiResponse(
    val success: Boolean,
    val data: ProgramCompleteData? = null,
    val error: String? = null
)

data class ProgramCompleteData(
    val nextAction: String,
    val nextProgramId: String? = null,
    val reassessmentTemplateId: String? = null
)

data class TrainingProfileApiResponse(
    val success: Boolean,
    val data: TrainingProfilePayload? = null,
    val error: String? = null
)

data class TrainingProfilePayload(
    val trainingGoal: String? = null,
    val profile: Map<String, @JvmSuppressWildcards Any?>? = null
)

// ── Enrollment / preview / substitutions / progress metrics ──

data class EnrollmentCheckApiResponse(
    val success: Boolean,
    val data: EnrollmentCheckData? = null,
    val error: String? = null
)

data class EnrollmentCheckData(
    val hasActiveProgram: Boolean,
    val willReplace: Boolean,
    val activeProgram: ActiveEnrollmentSummary? = null
)

data class ActiveEnrollmentSummary(
    val id: String,
    val name: Map<String, String>,
    val programId: String?,
    val progress: EnrollmentProgressSummary
)

data class EnrollmentProgressSummary(
    val completedDays: Int,
    val totalDays: Int,
    val percentage: Int
)

data class ProgramPreviewApiResponse(
    val success: Boolean,
    val data: com.trainingvalidator.poc.training.models.ProgramConfig? = null,
    val error: String? = null
)

data class SubstitutionExercisesApiResponse(
    val success: Boolean,
    val data: List<SubstitutionExerciseRow>? = null,
    val error: String? = null
)

data class SubstitutionExerciseRow(
    val id: String,
    val slug: String,
    val name: Map<String, String>? = null
)

data class ProgramProgressMetricsApiResponse(
    val success: Boolean,
    val data: ProgramProgressMetricsPayload? = null,
    val error: String? = null
)

data class ProgramProgressMetricsPayload(
    val userProgramId: String,
    val programId: String?,
    val weeks: List<WeekProgressPoint>,
    val exerciseOverload: List<ExerciseOverloadRow>
)

data class WeekProgressPoint(
    val weekNumber: Int,
    val totalVolumeLoad: Double,
    val avgFormScore: Double?,
    val sessionCount: Int,
    val avgRpe: Double?,
    val volumeChangePercent: Double?
)

data class ExerciseOverloadRow(
    val exerciseId: String,
    val exerciseSlug: String?,
    val currentWeightKg: Double?,
    val initialWeightKg: Double?,
    val overloadPercent: Double?
)
