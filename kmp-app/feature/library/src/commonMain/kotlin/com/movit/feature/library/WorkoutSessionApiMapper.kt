package com.movit.feature.library

import com.movit.core.network.dto.EffectivePlanItemDto
import com.movit.core.network.dto.EffectivePlanPayloadDto
import com.movit.core.network.dto.EffectivePlannedWorkoutDto
import com.movit.core.network.dto.ExploreDataDto
import com.movit.core.network.dto.ExploreExerciseDto
import com.movit.core.network.dto.LocalizedNameDto
import com.movit.core.network.dto.SubstitutionExerciseDto
import com.movit.resources.strings.SessionStrings

data class ExerciseCatalogEntry(
    val slug: String,
    val serverId: String,
    val name: String,
    val category: String,
    val imageUrl: String? = null,
)

object WorkoutSessionApiMapper {

    fun buildExerciseCatalog(
        explore: ExploreDataDto?,
        language: String,
        imageUrlForSlug: (String) -> String? = { null },
    ): Pair<Map<String, ExerciseCatalogEntry>, Map<String, ExerciseCatalogEntry>> {
        val bySlug = mutableMapOf<String, ExerciseCatalogEntry>()
        val byId = mutableMapOf<String, ExerciseCatalogEntry>()
        explore?.exercises.orEmpty().forEach { exercise ->
            val entry = exercise.toCatalogEntry(language, imageUrlForSlug)
            bySlug[entry.slug] = entry
            if (entry.serverId.isNotBlank()) {
                byId[entry.serverId] = entry
            }
        }
        return bySlug to byId
    }

    fun listExerciseCandidates(
        explore: ExploreDataDto?,
        language: String,
        query: String,
        excludingSlug: String? = null,
        imageUrlForSlug: (String) -> String? = { null },
        limit: Int = 24,
    ): List<SessionSwapCandidateUi> {
        val (bySlug, _) = buildExerciseCatalog(explore, language, imageUrlForSlug)
        return bySlug.values
            .asSequence()
            .filter { it.slug.isNotBlank() && it.slug != excludingSlug }
            .filter { entry ->
                if (query.isBlank()) true
                else {
                    entry.name.contains(query, ignoreCase = true) ||
                        entry.slug.contains(query, ignoreCase = true) ||
                        entry.category.contains(query, ignoreCase = true)
                }
            }
            .take(limit)
            .map { entry ->
                SessionSwapCandidateUi(
                    slug = entry.slug,
                    name = entry.name,
                    subtitle = entry.category,
                    imageUrl = entry.imageUrl,
                )
            }
            .toList()
    }

    fun mapPlannedWorkoutCards(
        plan: EffectivePlanPayloadDto,
        selectedPlannedWorkoutId: String,
        language: String,
        strings: SessionStrings,
    ): List<PlannedWorkoutCardUi> =
        plan.plannedWorkouts
            .sortedBy { it.sortOrder }
            .map { workout ->
                val exerciseCount = workout.items.count {
                    it.skipped != true && !it.type.equals("rest", ignoreCase = true)
                }
                PlannedWorkoutCardUi(
                    id = workout.id,
                    title = workout.name.localized(language).ifBlank { strings.workoutFallback },
                    exerciseCount = exerciseCount,
                    durationLabel = "~${workout.estimatedDurationMin ?: 30}m",
                    isSelected = workout.id == selectedPlannedWorkoutId,
                )
            }

    suspend fun mapSession(
        parsed: ParsedSessionKey,
        plan: EffectivePlanPayloadDto,
        language: String,
        strings: SessionStrings,
        exerciseBySlug: Map<String, ExerciseCatalogEntry>,
        exerciseById: Map<String, ExerciseCatalogEntry>,
    ): WorkoutSessionUi? {
        val plannedWorkout = when (parsed.plannedWorkoutId) {
            WorkoutSessionKeys.AUTO_PLANNED_WORKOUT ->
                plan.plannedWorkouts.minByOrNull { it.sortOrder }
            else -> plan.plannedWorkouts.firstOrNull { it.id == parsed.plannedWorkoutId }
        } ?: return null
        val sections = mapItemsToSections(
            items = plannedWorkout.items.filter { it.skipped != true }.sortedBy { it.sortOrder },
            language = language,
            strings = strings,
            exerciseBySlug = exerciseBySlug,
            exerciseById = exerciseById,
        )
        val exercises = sections.flatMap { it.items }.filterIsInstance<WorkoutSessionBlockUi.Exercise>()
        val workoutTitle = plannedWorkout.name.localized(language).ifBlank { strings.workoutFallback }
        val subtitle = strings.weekDayLabel(parsed.weekNumber, parsed.dayNumber)
        val programSlug = plan.programId ?: parsed.programId

        return WorkoutSessionUi(
            id = WorkoutSessionKeys.encode(
                programId = parsed.programId,
                weekNumber = parsed.weekNumber,
                dayNumber = parsed.dayNumber,
                plannedWorkoutId = parsed.plannedWorkoutId,
            ),
            title = workoutTitle,
            subtitle = subtitle,
            exerciseCount = exercises.size,
            durationLabel = "~${plannedWorkout.estimatedDurationMin ?: WorkoutSessionFormatting.estimateDurationMinutes(sections)}m",
            setCount = exercises.sumOf { it.sets },
            sections = sections,
            context = WorkoutSessionContextUi(
                programId = parsed.programId,
                programSlug = programSlug,
                weekNumber = parsed.weekNumber,
                dayNumber = parsed.dayNumber,
                plannedWorkoutId = parsed.plannedWorkoutId,
            ),
        )
    }

    fun mapSubstitutionCandidates(
        rows: List<SubstitutionExerciseDto>,
        query: String,
        replacingSlug: String,
        language: String,
        strings: SessionStrings,
        exerciseBySlug: Map<String, ExerciseCatalogEntry>,
    ): List<SessionSwapCandidateUi> {
        val filtered = rows
            .filter { it.slug.isNotBlank() && it.slug != replacingSlug }
            .filter { row ->
                if (query.isBlank()) true
                else {
                    val name = row.name.localized(language)
                    name.contains(query, ignoreCase = true) || row.slug.contains(query, ignoreCase = true)
                }
            }
            .take(12)

        return filtered.mapIndexed { index, row ->
            val catalog = exerciseBySlug[row.slug]
            SessionSwapCandidateUi(
                slug = row.slug,
                name = catalog?.name ?: row.name.localized(language).ifBlank { row.slug },
                subtitle = catalog?.category?.ifBlank { row.archetype.orEmpty() }
                    ?: row.archetype.orEmpty(),
                badge = if (index == 0 && query.isBlank()) strings.bestBadge else null,
                imageUrl = catalog?.imageUrl,
            )
        }
    }

    private fun mapItemsToSections(
        items: List<EffectivePlanItemDto>,
        language: String,
        strings: SessionStrings,
        exerciseBySlug: Map<String, ExerciseCatalogEntry>,
        exerciseById: Map<String, ExerciseCatalogEntry>,
    ): List<WorkoutSessionSectionUi> {
        if (items.isEmpty()) return emptyList()
        val sections = mutableListOf<WorkoutSessionSectionUi>()
        var currentRole = ""
        var currentBlocks = mutableListOf<WorkoutSessionBlockUi>()
        var exerciseIndex = 0

        fun flushSection() {
            if (currentBlocks.isEmpty()) return
            sections += WorkoutSessionSectionUi(
                title = strings.phaseTitle(currentRole),
                phaseRole = currentRole,
                items = currentBlocks.toList(),
            )
            currentBlocks = mutableListOf()
            exerciseIndex = 0
        }

        items.forEach { item ->
            val role = normalizeRoleKey(item.phaseRole)
            if (role != currentRole && currentBlocks.isNotEmpty()) {
                flushSection()
            }
            currentRole = role
            currentBlocks += when {
                item.type.equals("rest", ignoreCase = true) -> {
                    val seconds = ((item.restDurationMs ?: item.targetDuration?.times(1000) ?: 90_000) / 1000)
                    WorkoutSessionBlockUi.Rest(
                        id = item.id,
                        durationLabel = WorkoutSessionFormatting.restDurationLabel(seconds),
                        durationSeconds = seconds,
                    )
                }
                else -> {
                    exerciseIndex += 1
                    mapExerciseItem(item, exerciseIndex, language, strings, exerciseBySlug, exerciseById)
                }
            }
        }
        flushSection()
        return sections
    }

    private fun mapExerciseItem(
        item: EffectivePlanItemDto,
        index: Int,
        language: String,
        strings: SessionStrings,
        exerciseBySlug: Map<String, ExerciseCatalogEntry>,
        exerciseById: Map<String, ExerciseCatalogEntry>,
    ): WorkoutSessionBlockUi.Exercise {
        val catalog = item.exerciseId?.let { exerciseById[it] }
        val slug = catalog?.slug ?: item.exerciseId.orEmpty()
        val sets = item.sets ?: item.suggestion?.suggestedSets ?: 1
        val reps = item.targetReps ?: item.suggestion?.suggestedReps
        val durationSeconds = item.targetDuration ?: item.suggestion?.suggestedDuration
        val restSeconds = ((item.restBetweenSetsMs ?: 60_000) / 1000)
        val weight = item.weightPerSet?.firstOrNull()?.toFloat()
            ?: item.suggestion?.suggestedWeightKg?.toFloat()

        return WorkoutSessionBlockUi.Exercise(
            id = item.id,
            exerciseSlug = slug,
            index = index,
            name = catalog?.name ?: slug.ifBlank { strings.exerciseFallback },
            category = catalog?.category.orEmpty(),
            imageUrl = catalog?.imageUrl,
            sets = sets,
            reps = reps,
            durationSeconds = durationSeconds,
            restSeconds = restSeconds,
            weightKg = weight,
            setsLabel = WorkoutSessionFormatting.setsLabel(sets, reps, durationSeconds),
            restLabel = WorkoutSessionFormatting.restLabel(restSeconds),
            weightLabel = WorkoutSessionFormatting.weightLabel(weight),
            phaseRole = normalizeRoleKey(item.phaseRole),
        )
    }

    private fun normalizeRoleKey(role: String?): String = when (role?.uppercase()) {
        "WARMUP", "ACTIVATION" -> "WARMUP"
        "MAIN", "ACCESSORY", "CORRECTIVE" -> "MAIN"
        "COOLDOWN" -> "COOLDOWN"
        else -> "OTHER"
    }

    private fun ExploreExerciseDto.toCatalogEntry(
        language: String,
        imageUrlForSlug: (String) -> String?,
    ): ExerciseCatalogEntry {
        val resolvedSlug = slug.ifBlank { id }
        val category = categoryName?.localized(language)?.takeIf { it.isNotBlank() }
            ?: categoryCode.orEmpty()
        return ExerciseCatalogEntry(
            slug = resolvedSlug,
            serverId = id,
            name = name.localized(language).ifBlank { slug },
            category = category,
            // P2.9: use explore DTO imageUrl directly (avoids N× blob decode via imageUrlForSlug).
            imageUrl = imageUrl?.takeIf { it.isNotBlank() } ?: imageUrlForSlug(resolvedSlug),
        )
    }

    private fun Map<String, String>?.localized(language: String): String {
        if (this == null) return ""
        val primary = if (language == "ar") this["ar"] else this["en"]
        val fallback = if (language == "ar") this["en"] else this["ar"]
        return primary?.takeIf { it.isNotBlank() }
            ?: fallback?.takeIf { it.isNotBlank() }
            ?: values.firstOrNull().orEmpty()
    }

    private fun LocalizedNameDto.localized(language: String): String {
        val primary = if (language == "ar") ar else en
        val fallback = if (language == "ar") en else ar
        return primary.takeIf { it.isNotBlank() }
            ?: fallback.takeIf { it.isNotBlank() }
            ?: ""
    }
}
