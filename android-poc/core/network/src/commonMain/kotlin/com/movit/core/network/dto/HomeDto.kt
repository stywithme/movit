package com.movit.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class HomeApiResponse(
    val success: Boolean = false,
    val data: HomeDataDto? = null,
    val timestamp: String = "",
    val error: String? = null,
)

@Serializable
data class HomeDataDto(
    val user: HomeUserDto? = null,
    val trainMode: TrainModeDto? = null,
    val stats: HomeStatsDto? = null,
    val recentWorkouts: List<RecentWorkoutDto>? = null,
    val alerts: List<HomeAlertDto>? = null,
    val userStats: LegacyUserStatsDto? = null,
    val levelProfile: LevelProfileDto? = null,
)

@Serializable
data class HomeUserDto(
    val name: String = "",
    val avatarUrl: String? = null,
    val level: Int? = null,
    val levelCode: String? = null,
    val bodyScore: Double? = null,
    val levelProgress: Int? = null,
)

@Serializable
data class TrainModeDto(
    val status: String = "",
    val activeProgram: TrainActiveProgramDto? = null,
    val todayWorkout: TrainTodayWorkoutDto? = null,
    val dayType: String? = null,
    val isPaused: Boolean? = null,
    val nextReassessment: NextReassessmentDto? = null,
    val isTrainingDay: Boolean? = null,
    val catchUpSuggestion: CatchUpSuggestionDto? = null,
    val weekCalendars: List<WeekCalendarDto> = emptyList(),
)

/** Per-week, per-day truth for the Train week-calendar component (built server-side). */
@Serializable
data class WeekCalendarDto(
    val weekNumber: Int = 1,
    val isCurrentWeek: Boolean = false,
    val completedDays: Int = 0,
    val totalTrainingDays: Int = 0,
    val days: List<WeekCalendarDayDto> = emptyList(),
)

@Serializable
data class WeekCalendarDayDto(
    val dayNumber: Int = 0,
    /** 0=Sun through 6=Sat; server counts rest slots as calendar days. */
    val weekdayIndex: Int? = null,
    val dayType: String = "training",
    val isRestDay: Boolean = false,
    /** completed | today | in_progress | upcoming | missed | needs_attention | needs_catch_up | rest | active_recovery */
    val status: String = "upcoming",
    val isToday: Boolean = false,
    val workout: WeekCalendarWorkoutDto? = null,
)

@Serializable
data class WeekCalendarWorkoutDto(
    val plannedWorkoutId: String = "",
    val name: Map<String, String> = emptyMap(),
    val exerciseCount: Int = 0,
    val estimatedMinutes: Int? = null,
    val allWorkoutsCount: Int = 1,
    val completedWorkoutsCount: Int = 0,
)

@Serializable
data class CatchUpSuggestionDto(
    val missedTrainingDays: Int = 0,
    val message: String = "",
    val missedSlots: List<MissedSlotDto> = emptyList(),
)

@Serializable
data class MissedSlotDto(
    val weekNumber: Int = 0,
    val dayNumber: Int = 0,
)

@Serializable
data class NextReassessmentDto(
    val reason: String = "",
    val scheduledDate: String = "",
)

@Serializable
data class TrainActiveProgramDto(
    val id: String = "",
    val name: Map<String, String> = emptyMap(),
    val weekNumber: Int = 1,
    val dayNumber: Int = 1,
    val totalWeeks: Int = 1,
    val weekProgress: WeekProgressDto = WeekProgressDto(),
)

@Serializable
data class WeekProgressDto(
    val completed: Int = 0,
    val total: Int = 0,
)

@Serializable
data class TrainTodayWorkoutDto(
    val plannedWorkoutId: String = "",
    val name: Map<String, String> = emptyMap(),
    val exerciseCount: Int = 0,
    val estimatedMinutes: Int? = null,
    val workoutTemplateId: String? = null,
    val isCompleted: Boolean = false,
    val allWorkoutsCount: Int = 1,
    val completedWorkoutsCount: Int = 0,
)

@Serializable
data class HomeStatsDto(
    val totalWorkoutExecutions: Int = 0,
    val avgFormScore: Int = 0,
    val streak: Int = 0,
    val thisWeekExecutions: Int = 0,
    val totalMinutes: Int = 0,
)

@Serializable
data class LegacyUserStatsDto(
    val weeklyPlannedWorkouts: Int? = null,
    val avgFormScore: Double? = null,
    val streak: Int? = null,
)

@Serializable
data class LevelProfileDto(
    val overallLevel: Int = 0,
    val bodyScore: Double = 0.0,
    val levelInfo: LevelInfoDto = LevelInfoDto(),
)

@Serializable
data class LevelInfoDto(
    val name: LocalizedNameDto = LocalizedNameDto(),
)

@Serializable
data class RecentWorkoutDto(
    val exerciseId: String = "",
    val exerciseName: Map<String, String> = emptyMap(),
    val formScore: Int = 0,
    val totalReps: Int = 0,
    val date: String = "",
)

@Serializable
data class HomeAlertDto(
    val type: String = "",
    val titleAr: String = "",
    val titleEn: String = "",
    val messageAr: String = "",
    val messageEn: String = "",
    val actionRoute: String? = null,
)
