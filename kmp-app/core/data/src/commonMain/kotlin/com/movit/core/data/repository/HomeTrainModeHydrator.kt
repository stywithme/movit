package com.movit.core.data.repository

import com.movit.core.network.MovitMobileApi
import com.movit.core.network.dto.ActivePlanProgramDto
import com.movit.core.network.dto.HomeDataDto
import com.movit.core.network.dto.TodayPlanDto
import com.movit.core.network.dto.TodayProgramDto
import com.movit.core.network.dto.TrainActiveProgramDto
import com.movit.core.network.dto.TrainModeDto
import com.movit.core.network.dto.TrainTodayWorkoutDto
import com.movit.core.network.dto.WeekProgressDto

/**
 * Repairs [HomeDataDto.trainMode] when `/mobile/home` returns assessment/no-plan gates
 * while the user still has an active program on `/mobile/plan` + `/mobile/plan/today`.
 */
internal object HomeTrainModeHydrator {

    suspend fun hydrateIfNeeded(
        home: HomeDataDto,
        api: MovitMobileApi,
        authorization: String?,
    ): HomeDataDto {
        val trainMode = home.trainMode ?: return home
        if (!needsHydration(trainMode) || authorization.isNullOrBlank()) {
            return home
        }

        val activePlan = api.fetchActivePlan(authorization).getOrNull()?.data ?: return home
        val activeSlot = activePlan.programs.firstOrNull { it.status == "active" } ?: return home
        val program = activeSlot.program ?: return home
        val todayPlan = api.fetchTodayPlan(authorization).getOrNull()?.data

        val hydratedTrainMode = mergeTrainMode(
            current = trainMode,
            activeSlot = activeSlot,
            programId = program.id,
            programName = program.name,
            durationWeeks = program.durationWeeks,
            todayPlan = todayPlan,
        )

        return home.copy(trainMode = hydratedTrainMode)
    }

    private fun needsHydration(trainMode: TrainModeDto): Boolean {
        if (trainMode.activeProgram != null) return false
        return trainMode.status == "no_assessment" || trainMode.status == "no_plan" || trainMode.status.isBlank()
    }

    private fun mergeTrainMode(
        current: TrainModeDto,
        activeSlot: ActivePlanProgramDto,
        programId: String,
        programName: Map<String, String>,
        durationWeeks: Int,
        todayPlan: TodayPlanDto?,
    ): TrainModeDto {
        val currentProgram = todayPlan?.currentProgram
        val progress = activeSlot.progress
        val activeProgram = TrainActiveProgramDto(
            id = programId,
            name = programName,
            weekNumber = currentProgram?.weekNumber?.coerceAtLeast(1) ?: progress.currentWeek.coerceAtLeast(1),
            dayNumber = currentProgram?.dayNumber?.coerceAtLeast(1) ?: progress.currentDay.coerceAtLeast(1),
            totalWeeks = durationWeeks.coerceAtLeast(1),
            weekProgress = WeekProgressDto(
                completed = progress.completedDays.coerceAtLeast(0),
                total = progress.totalDays.coerceAtLeast(1),
            ),
        )

        val status = resolveHydratedStatus(todayPlan, currentProgram)
        val todayWorkout = buildTodayWorkout(currentProgram)

        return current.copy(
            status = status,
            activeProgram = activeProgram,
            todayWorkout = todayWorkout,
            dayType = currentProgram?.dayType ?: current.dayType,
            isTrainingDay = todayPlan?.isTrainingDay ?: current.isTrainingDay,
            catchUpSuggestion = todayPlan?.catchUpSuggestion ?: current.catchUpSuggestion,
            nextReassessment = todayPlan?.nextReassessment ?: current.nextReassessment,
        )
    }

    private fun resolveHydratedStatus(
        todayPlan: TodayPlanDto?,
        currentProgram: TodayProgramDto?,
    ): String {
        when (todayPlan?.activePlanStatus) {
            "program_complete" -> return "program_complete"
        }
        if (currentProgram == null) {
            return "active"
        }
        if (currentProgram.isRestDay) {
            return "rest_day"
        }
        val nextWorkout = currentProgram.plannedWorkouts.firstOrNull { !it.isCompleted }
        return if (nextWorkout != null) {
            "active"
        } else {
            "rest_day"
        }
    }

    private fun buildTodayWorkout(currentProgram: TodayProgramDto?): TrainTodayWorkoutDto? {
        val program = currentProgram ?: return null
        val workouts = program.plannedWorkouts
        if (workouts.isEmpty()) return null

        val completedCount = workouts.count { it.isCompleted }
        val nextWorkout = workouts.firstOrNull { !it.isCompleted } ?: workouts.last()
        val allCompleted = workouts.all { it.isCompleted }

        return TrainTodayWorkoutDto(
            plannedWorkoutId = nextWorkout.id,
            name = nextWorkout.name,
            exerciseCount = nextWorkout.itemCount,
            estimatedMinutes = nextWorkout.estimatedDurationMin,
            isCompleted = allCompleted,
            allWorkoutsCount = workouts.size,
            completedWorkoutsCount = completedCount,
        )
    }
}
