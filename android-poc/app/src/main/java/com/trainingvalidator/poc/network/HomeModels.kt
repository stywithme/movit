package com.trainingvalidator.poc.network

import com.google.gson.annotations.SerializedName

/**
 * Home API Models
 *
 * Unified response from GET /api/mobile/home — drives all Home screen state.
 */

data class HomeResponse(
    val success: Boolean,
    val data: HomeData?,
    val timestamp: String,
    val error: String? = null
)

data class HomeData(
    val user: HomeUserData?,
    val trainMode: TrainModeData?,
    val stats: HomeStatsData?,
    val recentWorkouts: List<RecentWorkoutData>?,
    val alerts: List<HomeAlertData>?,

    // Legacy fields — kept for backward compatibility with cached data
    val userStats: UserStatsData? = null,
    val levelProfile: LevelProfileData? = null,
    val activePlan: ActivePlanData? = null,
    val todayPlan: TodayPlanData? = null
)

// ── User Header ──────────────────────────────────────────────────────────────

data class HomeUserData(
    val name: String,
    val avatarUrl: String?,
    val level: Int?,
    val levelCode: String?,
    val bodyScore: Double?,
    val levelProgress: Int?           // 0-100, progress toward next level
)

// ── Train Mode ───────────────────────────────────────────────────────────────

/**
 * Drives the main Train Mode card on the Home screen.
 *
 * Status values:
 *   no_assessment   — User has never done a Body Scan
 *   no_plan         — Assessment done but no active program
 *   rest_day        — Active plan, but today is a rest/recovery day
 *   active          — Active plan with planned workouts to complete today
 *   program_complete— All weeks done, awaiting reassessment
 *   reassessment_due— A ReassessmentSchedule is pending and overdue
 */
data class TrainModeData(
    val status: String,
    val activeProgram: TrainActiveProgramData?,
    val todayWorkout: TrainTodayWorkoutData?,
    val dayType: String?,
    val nextReassessment: NextReassessmentData?,
    /** True when the active user program calendar is paused */
    val isPaused: Boolean? = null,
    val catchUpSuggestion: CatchUpSuggestionData? = null
)

data class TrainActiveProgramData(
    val id: String,
    val name: Map<String, String>,
    val weekNumber: Int,
    val dayNumber: Int = 1,
    val totalWeeks: Int,
    val weekProgress: WeekProgressData
)

data class WeekProgressData(
    val completed: Int,
    val total: Int
)

data class TrainTodayWorkoutData(
    val plannedWorkoutId: String,
    val name: Map<String, String>,
    val exerciseCount: Int,
    val estimatedMinutes: Int?,
    val role: String?,
    val isCompleted: Boolean,
    val allWorkoutsCount: Int,
    val completedWorkoutsCount: Int
)

// ── Stats ─────────────────────────────────────────────────────────────────────

data class HomeStatsData(
    val totalWorkoutExecutions: Int,
    val avgFormScore: Int,
    val streak: Int,
    val thisWeekExecutions: Int,
    val totalMinutes: Int
)

// ── Recent Workouts ───────────────────────────────────────────────────────────

data class RecentWorkoutData(
    val exerciseId: String,
    val exerciseName: Map<String, String>,
    val formScore: Int,
    val totalReps: Int,
    val date: String,
    val context: String
)

// ── Alerts ────────────────────────────────────────────────────────────────────

data class HomeAlertData(
    val type: String,               // reassessment_due | progression_applied | level_up | streak_at_risk
    val titleAr: String,
    val titleEn: String,
    val messageAr: String,
    val messageEn: String,
    val actionRoute: String? = null
)
