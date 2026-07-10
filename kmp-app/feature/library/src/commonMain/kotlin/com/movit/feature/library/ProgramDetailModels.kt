package com.movit.feature.library

enum class ProgramDetailTab {
    Overview,
    Edit,
}

enum class ProgramDayStatus {
    Done,
    Next,
    Upcoming,
    Rest,
}

enum class ProgramEditReason {
    ScheduleChanged,
    EquipmentMissing,
    TooEasyHard,
    InjuryDiscomfort,
}

enum class ProgramEditScope {
    PlanSettings,
    WeekCalendar,
    DaySessions,
    ExerciseTargets,
}

data class ProgramStatUi(
    val value: String,
    val label: String,
    val hint: String,
)

data class ProgramDayUi(
    val dayNumber: Int,
    val title: String,
    val meta: String,
    val status: ProgramDayStatus,
    val sessionCount: Int = 0,
    val exerciseCount: Int = 0,
    val plannedWorkoutId: String? = null,
)

data class ProgramWeekUi(
    val weekNumber: Int,
    val label: String,
    val theme: String,
    val subtitle: String,
    val progressPercent: Int,
    val isCurrent: Boolean,
    val isLocked: Boolean,
    val days: List<ProgramDayUi> = emptyList(),
)

data class ProgramDetailCardUi(
    val title: String,
    val description: String,
)

data class ProgramEnrollmentUi(
    val isEnrolled: Boolean,
    val startedLabel: String? = null,
    val customEditsCount: Int = 0,
    val syncLabel: String? = null,
    val isPaused: Boolean = false,
)

data class ProgramDaySessionUi(
    val title: String,
    val subtitle: String,
    val exerciseCount: Int,
    val sessionKey: String,
)

data class ProgramNextSessionUi(
    val weekNumber: Int,
    val dayNumber: Int,
    val title: String,
    val subtitle: String,
    val sessionWorkoutId: String,
)

data class ProgramEditExerciseUi(
    val id: String,
    val name: String,
    val sets: Int,
    val reps: Int?,
    val weightKg: Double?,
    val restSeconds: Int,
    val isEdited: Boolean = false,
)

data class ProgramEditSessionUi(
    val id: String,
    val title: String,
    val sortOrder: Int,
    val exercises: List<ProgramEditExerciseUi>,
    val isEdited: Boolean = false,
)

data class ProgramEditUiState(
    val selectedReason: ProgramEditReason = ProgramEditReason.ScheduleChanged,
    val selectedScope: ProgramEditScope = ProgramEditScope.DaySessions,
    val weeklyTarget: Int = 3,
    val startDateLabel: String = "",
    val pauseCalendar: Boolean = false,
    val showSaveToast: Boolean = false,
    val editingWeekNumber: Int = 1,
    val editingDayNumber: Int = 2,
    val editingDayTitle: String = "",
    val daySessions: List<ProgramEditSessionUi> = emptyList(),
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val isDirty: Boolean = false,
)

enum class WeekOfflineStatus {
    Idle,
    Downloading,
    Ready,
    Failed,
}

data class WeekOfflineUiState(
    val status: WeekOfflineStatus = WeekOfflineStatus.Idle,
    val progressPercent: Int = 0,
    val errorMessageKey: String? = null,
    val errorMessage: String? = null,
)

data class ProgramDetailUiState(
    val isLoading: Boolean = false,
    val programId: String = "",
    val title: String = "",
    val subtitle: String = "",
    val description: String = "",
    val imageUrl: String? = null,
    val kickers: List<String> = emptyList(),
    val stats: List<ProgramStatUi> = emptyList(),
    val detailCards: List<ProgramDetailCardUi> = emptyList(),
    val selectedTab: ProgramDetailTab = ProgramDetailTab.Overview,
    val selectedWeekNumber: Int = 1,
    val selectedDayNumber: Int? = null,
    val selectedDaySessions: List<ProgramDaySessionUi> = emptyList(),
    val weeks: List<ProgramWeekUi> = emptyList(),
    val enrollment: ProgramEnrollmentUi = ProgramEnrollmentUi(isEnrolled = false),
    val nextSession: ProgramNextSessionUi? = null,
    val weekOffline: WeekOfflineUiState = WeekOfflineUiState(),
    val edit: ProgramEditUiState = ProgramEditUiState(),
    val isStarting: Boolean = false,
    /** UX.5 — thin offline banner when the device has no network. */
    val isOffline: Boolean = false,
    val errorMessage: String? = null,
)
