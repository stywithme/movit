package com.movit.feature.library

import com.movit.core.data.MovitData
import com.movit.core.network.dto.EffectivePlanPayloadDto

/**
 * Resolves a workout-template id for background prefetch when loading a program session.
 */
fun resolveWorkoutTemplateIdForPrefetch(workoutId: String?): String? {
    if (workoutId.isNullOrBlank()) return null
    val parsed = WorkoutSessionKeys.parse(workoutId) ?: return workoutId
    if (!MovitData.isInstalled) return null

    val platform = MovitData.requirePlatform()
    val userProgramId = platform.activeUserProgramId() ?: parsed.programId
    val plan = MovitData.workoutSession.readCachedEffectivePlan(
        userProgramId = userProgramId,
        weekNumber = parsed.weekNumber,
        dayNumber = parsed.dayNumber,
    ) ?: return null

    return plan.plannedWorkoutFor(parsed)?.workoutTemplateId?.takeIf { it.isNotBlank() }
}

internal fun EffectivePlanPayloadDto.plannedWorkoutFor(parsed: ParsedSessionKey) =
    when (parsed.plannedWorkoutId) {
        WorkoutSessionKeys.AUTO_PLANNED_WORKOUT ->
            plannedWorkouts.minByOrNull { it.sortOrder }
        else -> plannedWorkouts.firstOrNull { it.id == parsed.plannedWorkoutId }
    }
