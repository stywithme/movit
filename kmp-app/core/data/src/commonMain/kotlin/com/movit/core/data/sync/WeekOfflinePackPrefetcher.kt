package com.movit.core.data.sync

import com.movit.core.data.audio.AudioPrefetchRunner
import com.movit.core.data.audio.EntityAudioManifestFetcher
import com.movit.core.data.platform.MovitPlatformBindings
import com.movit.core.data.repository.TrainingConfigRepository
import com.movit.core.data.repository.WorkoutSessionSyncRepository
import com.movit.core.network.dto.ProgramExportDto
import com.movit.shared.AppResult

/**
 * F10 / N-25 — «حزمة الأسبوع» offline-readiness hook.
 *
 * Wires existing sync + audio prefetch to a program week. UI reads [isWeekReadyOffline]
 * and triggers [prefetchWeek] from the program detail week panel.
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

    data class WeekPrefetchProgress(
        val phase: Phase,
        val percent: Int,
    ) {
        enum class Phase {
            Syncing,
            LoadingPlans,
            CachingAudio,
            Finishing,
        }
    }

    sealed class PrefetchOutcome {
        data class Ready(val plan: WeekPrefetchPlan) : PrefetchOutcome()
        data object SkippedNoWeek : PrefetchOutcome()
        data class Failed(val message: String) : PrefetchOutcome()
    }

    fun planFromProgram(program: ProgramExportDto, weekNumber: Int): WeekPrefetchPlan? =
        Companion.planFromProgram(program, weekNumber)

    fun isWeekReadyOffline(programId: String, weekNumber: Int): Boolean {
        val bindings = platform()
        return bindings.readCache(OFFLINE_STORE, offlineReadyKey(programId, weekNumber)) == READY_MARKER
    }

    suspend fun prefetchWeek(
        program: ProgramExportDto,
        weekNumber: Int,
        onProgress: (WeekPrefetchProgress) -> Unit = {},
    ): PrefetchOutcome {
        val basePlan = Companion.planFromProgram(program, weekNumber) ?: return PrefetchOutcome.SkippedNoWeek

        onProgress(WeekPrefetchProgress(WeekPrefetchProgress.Phase.Syncing, percent = 5))

        when (val syncOutcome = sync.syncIfNeeded(forceCheck = true)) {
            is MovitSyncOrchestrator.SyncOutcome.Error ->
                return PrefetchOutcome.Failed(syncOutcome.message)
            else -> Unit
        }

        onProgress(WeekPrefetchProgress(WeekPrefetchProgress.Phase.Syncing, percent = 25))

        val bindings = platform()
        val userProgramId = bindings.activeUserProgramId()
        val exerciseSlugs = linkedSetOf<String>()
        val workoutTemplateIds = linkedSetOf<String>()
        val imageUrls = linkedSetOf<String>()
        basePlan.coverImageUrl?.let { imageUrls += it }

        val workoutDays = basePlan.workoutDayNumbers
        if (userProgramId != null && workoutDays.isNotEmpty()) {
            workoutDays.forEachIndexed { index, dayNumber ->
                onProgress(
                    WeekPrefetchProgress(
                        phase = WeekPrefetchProgress.Phase.LoadingPlans,
                        percent = 25 + ((index + 1) * 45 / workoutDays.size),
                    ),
                )
                when (
                    val result = workoutSession.syncEffectivePlan(
                        userProgramId = userProgramId,
                        weekNumber = weekNumber,
                        dayNumber = dayNumber,
                    )
                ) {
                    is AppResult.Success -> {
                        result.value.plannedWorkouts.forEach { planned ->
                            planned.workoutTemplateId
                                ?.takeIf(String::isNotBlank)
                                ?.let { workoutTemplateIds += it }
                        }
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
        } else {
            onProgress(WeekPrefetchProgress(WeekPrefetchProgress.Phase.LoadingPlans, percent = 55))
        }

        val configsCached = exerciseSlugs.count { trainingConfig.supports(it) }

        onProgress(WeekPrefetchProgress(WeekPrefetchProgress.Phase.CachingAudio, percent = 75))
        audioPrefetch.prefetchForTargets(
            EntityAudioManifestFetcher.Targets(
                exerciseSlugs = exerciseSlugs,
                workoutTemplateIds = workoutTemplateIds,
            ),
        )

        // TODO(N-25): platform Coil3 prefetch for [imageUrls] once a common ImagePrefetchPort exists.

        onProgress(WeekPrefetchProgress(WeekPrefetchProgress.Phase.Finishing, percent = 95))

        val readyPlan = basePlan.copy(
            exerciseSlugs = exerciseSlugs.toList(),
            imageUrls = imageUrls.toList(),
            configsCached = configsCached,
        )
        markWeekReady(readyPlan.programId, weekNumber)

        onProgress(WeekPrefetchProgress(WeekPrefetchProgress.Phase.Finishing, percent = 100))
        return PrefetchOutcome.Ready(readyPlan)
    }

    private fun markWeekReady(programId: String, weekNumber: Int) {
        platform().writeCache(OFFLINE_STORE, offlineReadyKey(programId, weekNumber), READY_MARKER)
    }

    companion object {
        const val OFFLINE_STORE = "week_offline_cache"
        private const val READY_MARKER = "1"
        private const val READY_KEY_PREFIX = "ready_"

        fun offlineReadyKey(programId: String, weekNumber: Int): String =
            "${READY_KEY_PREFIX}${programId}_$weekNumber"

        /** Week numbers marked ready via [markWeekReady] — protected from effective-plan GC (F4). */
        fun protectedOfflineWeekNumbers(bindings: MovitPlatformBindings): Set<Int> =
            bindings.readAllCacheEntries(OFFLINE_STORE)
                .asSequence()
                .filter { (key, value) -> key.startsWith(READY_KEY_PREFIX) && value == READY_MARKER }
                .mapNotNull { (key, _) -> key.substringAfterLast('_').toIntOrNull() }
                .toSet()

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
