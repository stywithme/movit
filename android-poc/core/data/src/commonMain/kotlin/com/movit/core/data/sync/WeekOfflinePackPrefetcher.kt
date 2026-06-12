package com.movit.core.data.sync

import com.movit.core.data.audio.AudioPrefetchRunner
import com.movit.core.data.platform.MovitPlatformBindings
import com.movit.core.data.repository.TrainingConfigRepository
import com.movit.core.data.repository.WorkoutSessionSyncRepository
import com.movit.core.network.dto.ProgramExportDto
import com.movit.shared.AppResult

/**
 * F10 / N-25 — «حزمة الأسبوع» offline-readiness hook.
 *
 * Wires existing sync + audio prefetch to a program week. UI entry (button + badge) is deferred;
 * call [prefetchWeek] from the week-plan screen when that ships.
 */
class WeekOfflinePackPrefetcher(
    private val sync: MovitSyncOrchestrator,
    private val audioPrefetch: AudioPrefetchRunner,
    private val workoutSession: WorkoutSessionSyncRepository,
    private val trainingConfig: TrainingConfigRepository,
    private val platform: () -> MovitPlatformBindings,
) {
    data class WeekPrefetchPlan(
        val programId: String,
        val weekNumber: Int,
        val plannedWorkoutIds: List<String>,
        val workoutDayNumbers: List<Int>,
        val coverImageUrl: String?,
        val exerciseSlugs: List<String>,
        val imageUrls: List<String>,
        val configsCached: Int,
    )

    sealed class PrefetchOutcome {
        data class Ready(val plan: WeekPrefetchPlan) : PrefetchOutcome()
        data object SkippedNoWeek : PrefetchOutcome()
        data class Failed(val message: String) : PrefetchOutcome()
    }

    fun planFromProgram(program: ProgramExportDto, weekNumber: Int): WeekPrefetchPlan? =
        Companion.planFromProgram(program, weekNumber)

    suspend fun prefetchWeek(program: ProgramExportDto, weekNumber: Int): PrefetchOutcome {
        val basePlan = Companion.planFromProgram(program, weekNumber) ?: return PrefetchOutcome.SkippedNoWeek

        when (val syncOutcome = sync.syncIfNeeded(forceCheck = true)) {
            is MovitSyncOrchestrator.SyncOutcome.Error ->
                return PrefetchOutcome.Failed(syncOutcome.message)
            else -> Unit
        }

        val bindings = platform()
        val userProgramId = bindings.activeUserProgramId()
        val exerciseSlugs = linkedSetOf<String>()
        val imageUrls = linkedSetOf<String>()
        basePlan.coverImageUrl?.let { imageUrls += it }

        if (userProgramId != null) {
            for (dayNumber in basePlan.workoutDayNumbers) {
                when (
                    val result = workoutSession.syncEffectivePlan(
                        userProgramId = userProgramId,
                        weekNumber = weekNumber,
                        dayNumber = dayNumber,
                    )
                ) {
                    is AppResult.Success -> {
                        result.value.plannedWorkouts
                            .flatMap { it.items }
                            .mapNotNull { it.exerciseId?.takeIf(String::isNotBlank) }
                            .forEach { exerciseId ->
                                val slug = trainingConfig.slugForExerciseId(exerciseId) ?: exerciseId
                                exerciseSlugs += slug
                                bindings.exerciseImageUrl(slug)?.let { imageUrls += it }
                            }
                    }
                    is AppResult.Failure -> Unit
                }
            }
        }

        val configsCached = exerciseSlugs.count { trainingConfig.supports(it) }
        audioPrefetch.afterManifestApplied(isFullSync = false)

        // TODO(N-25): platform Coil3 prefetch for [imageUrls] once a common ImagePrefetchPort exists.
        // TODO(N-25): per-workout audio manifest fetch for [plannedWorkoutIds] when API consumer lands.
        // ProgramExportDto has no exercise slugs — enrollment + effective-plan sync above is required.

        return PrefetchOutcome.Ready(
            basePlan.copy(
                exerciseSlugs = exerciseSlugs.toList(),
                imageUrls = imageUrls.toList(),
                configsCached = configsCached,
            ),
        )
    }

    companion object {
        fun planFromProgram(program: ProgramExportDto, weekNumber: Int): WeekPrefetchPlan? {
            val week = program.weeks.firstOrNull { it.weekNumber == weekNumber } ?: return null
            val workoutDays = week.days
                .filter { !it.isRestDay && it.plannedWorkouts.isNotEmpty() }
                .map { it.dayNumber }
            val plannedWorkoutIds = week.days
                .flatMap { day -> day.plannedWorkouts.mapNotNull { it.id.takeIf(String::isNotBlank) } }
                .distinct()
            return WeekPrefetchPlan(
                programId = program.id.ifBlank { program.slug },
                weekNumber = weekNumber,
                plannedWorkoutIds = plannedWorkoutIds,
                workoutDayNumbers = workoutDays,
                coverImageUrl = program.coverImageUrl?.takeIf { it.isNotBlank() },
                exerciseSlugs = emptyList(),
                imageUrls = listOfNotNull(program.coverImageUrl?.takeIf { it.isNotBlank() }),
                configsCached = 0,
            )
        }
    }
}
