package com.trainingvalidator.poc.network

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
    val recentSessions: List<RecentSessionData>?,
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
 *   active          — Active plan with sessions to complete today
 *   program_complete— All weeks done, awaiting reassessment
 *   reassessment_due— A ReassessmentSchedule is pending and overdue
 */
data class TrainModeData(
    val status: String,
    val activeProgram: TrainActiveProgramData?,
    val todaySession: TrainTodaySessionData?,
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

data class TrainTodaySessionData(
    val sessionId: String,
    val name: Map<String, String>,
    val exerciseCount: Int,
    val estimatedMinutes: Int?,
    val role: String?,
    val isCompleted: Boolean,
    val allSessionsCount: Int,
    val completedSessionsCount: Int
)

// ── Stats ─────────────────────────────────────────────────────────────────────

data class HomeStatsData(
    val totalSessions: Int,
    val avgFormScore: Int,
    val streak: Int,
    val thisWeekSessions: Int,
    val totalMinutes: Int
)

// ── Recent Sessions ───────────────────────────────────────────────────────────

data class RecentSessionData(
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
