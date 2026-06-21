package com.movit.feature.library

import com.movit.core.network.dto.ExploreDataDto
import com.movit.core.network.dto.ExploreWorkoutDto
import com.movit.core.network.dto.WorkoutTemplateExerciseDto
import com.movit.core.network.dto.WorkoutTemplatePhaseDto
import com.movit.core.network.dto.WorkoutTemplateTrainingConfigDto
import com.movit.resources.strings.SessionStrings

internal object WorkoutTemplateSessionMapper {

    fun mapSession(
        slugOrId: String,
        config: WorkoutTemplateTrainingConfigDto,
        templateMeta: ExploreWorkoutDto?,
        language: String,
        strings: SessionStrings,
        exerciseBySlug: Map<String, ExerciseCatalogEntry>,
    ): WorkoutSessionUi {
        val sections = buildSections(config, language, strings, exerciseBySlug)
        val exercises = sections.flatMap { it.items }.filterIsInstance<WorkoutSessionBlockUi.Exercise>()
        val title = config.name.localized(language).ifBlank {
            templateMeta?.name?.localized(language).orEmpty()
        }.ifBlank { strings.workoutFallback }
        val durationMinutes = config.estimatedDurationMin
            ?: templateMeta?.estimatedDurationMin
            ?: WorkoutSessionFormatting.estimateDurationMinutes(sections)

        return WorkoutSessionUi(
            id = config.slug.ifBlank { slugOrId },
            title = title,
            subtitle = config.description?.localized(language).orEmpty(),
            exerciseCount = exercises.size,
            durationLabel = "~${durationMinutes}m",
            setCount = exercises.sumOf { it.sets },
            sections = sections,
            context = null,
        )
    }

    fun mapExploreFallback(
        slugOrId: String,
        templateMeta: ExploreWorkoutDto?,
        explore: ExploreDataDto?,
        language: String,
        strings: SessionStrings,
        exerciseBySlug: Map<String, ExerciseCatalogEntry>,
    ): WorkoutSessionUi? {
        val meta = templateMeta
            ?: explore?.workoutTemplates?.firstOrNull { it.slug == slugOrId || it.id == slugOrId }
            ?: return null

        val title = meta.name.localized(language).ifBlank { strings.workoutFallback }
        val durationMinutes = meta.estimatedDurationMin ?: 30
        val placeholderSlug = meta.slug.ifBlank { slugOrId }
        val catalog = exerciseBySlug[placeholderSlug]

        return WorkoutSessionUi(
            id = placeholderSlug,
            title = title,
            subtitle = "",
            exerciseCount = meta.exerciseCount.coerceAtLeast(1),
            durationLabel = "~${durationMinutes}m",
            setCount = 3,
            sections = listOf(
                WorkoutSessionSectionUi(
                    title = strings.phaseTitle("MAIN"),
                    phaseRole = "MAIN",
                    items = listOf(
                        WorkoutSessionBlockUi.Exercise(
                            id = placeholderSlug,
                            exerciseSlug = placeholderSlug,
                            index = 1,
                            name = catalog?.name ?: title,
                            category = catalog?.category.orEmpty(),
                            imageUrl = catalog?.imageUrl ?: meta.coverImageUrl,
                            sets = 3,
                            reps = 12,
                            restSeconds = 60,
                            setsLabel = WorkoutSessionFormatting.setsLabel(3, 12, null),
                            restLabel = WorkoutSessionFormatting.restLabel(60),
                            phaseRole = "MAIN",
                        ),
                    ),
                ),
            ),
            context = null,
        )
    }

    private fun buildSections(
        config: WorkoutTemplateTrainingConfigDto,
        language: String,
        strings: SessionStrings,
        exerciseBySlug: Map<String, ExerciseCatalogEntry>,
    ): List<WorkoutSessionSectionUi> {
        val phases = config.phases.filter { it.exercises.isNotEmpty() }
        if (phases.isNotEmpty()) {
            return phases
                .sortedBy { it.sortOrder }
                .map { phase -> mapPhase(phase, language, strings, exerciseBySlug) }
        }

        val items = mapTemplateExercises(config.exercises, language, strings, exerciseBySlug)
        if (items.isEmpty()) return emptyList()

        return listOf(
            WorkoutSessionSectionUi(
                title = strings.phaseTitle("MAIN"),
                phaseRole = "MAIN",
                items = items,
            ),
        )
    }

    private fun mapPhase(
        phase: WorkoutTemplatePhaseDto,
        language: String,
        strings: SessionStrings,
        exerciseBySlug: Map<String, ExerciseCatalogEntry>,
    ): WorkoutSessionSectionUi {
        val role = normalizeRoleKey(phase.role)
        return WorkoutSessionSectionUi(
            title = phase.name.localized(language).ifBlank { strings.phaseTitle(role) },
            phaseRole = role,
            items = mapTemplateExercises(phase.exercises, language, strings, exerciseBySlug),
        )
    }

    private fun mapTemplateExercises(
        exercises: List<WorkoutTemplateExerciseDto>,
        language: String,
        strings: SessionStrings,
        exerciseBySlug: Map<String, ExerciseCatalogEntry>,
    ): List<WorkoutSessionBlockUi> {
        val blocks = mutableListOf<WorkoutSessionBlockUi>()
        var index = 0
        exercises
            .sortedBy { it.sortOrder }
            .forEach { item ->
                index += 1
                blocks += mapExercise(item, index, language, strings, exerciseBySlug)
                val restMs = item.restAfterExerciseMs ?: 0L
                if (restMs > 0) {
                    val seconds = (restMs / 1000).toInt().coerceAtLeast(1)
                    blocks += WorkoutSessionBlockUi.Rest(
                        id = "rest-after-${item.workoutExerciseId.ifBlank { index.toString() }}",
                        durationLabel = WorkoutSessionFormatting.restDurationLabel(seconds),
                        durationSeconds = seconds,
                    )
                }
            }
        return blocks
    }

    private fun mapExercise(
        item: WorkoutTemplateExerciseDto,
        index: Int,
        language: String,
        strings: SessionStrings,
        exerciseBySlug: Map<String, ExerciseCatalogEntry>,
    ): WorkoutSessionBlockUi.Exercise {
        val slug = item.exercise.slug.ifBlank { item.exercise.id }
        val catalog = exerciseBySlug[slug]
        val sets = item.sets?.coerceAtLeast(1) ?: 1
        val reps = item.targetReps
        val durationSeconds = item.targetDuration
        val restSeconds = ((item.restBetweenSetsMs ?: 60_000L) / 1000).toInt()
        val weight = item.weightPerSet?.firstOrNull()?.toFloat()
        val category = catalog?.category?.ifBlank {
            item.exercise.attributes.firstOrNull()?.name?.localized(language).orEmpty()
        }.orEmpty()

        return WorkoutSessionBlockUi.Exercise(
            id = item.workoutExerciseId.ifBlank { "$slug-$index" },
            exerciseSlug = slug,
            index = index,
            name = catalog?.name ?: item.exercise.name.localized(language).ifBlank {
                slug.ifBlank { strings.exerciseFallback }
            },
            category = category,
            imageUrl = catalog?.imageUrl,
            sets = sets,
            reps = reps,
            durationSeconds = durationSeconds,
            restSeconds = restSeconds,
            weightKg = weight,
            setsLabel = WorkoutSessionFormatting.setsLabel(sets, reps, durationSeconds),
            restLabel = WorkoutSessionFormatting.restLabel(restSeconds),
            weightLabel = WorkoutSessionFormatting.weightLabel(weight),
            phaseRole = "MAIN",
        )
    }

    private fun normalizeRoleKey(role: String?): String = when (role?.uppercase()) {
        "WARMUP", "ACTIVATION" -> "WARMUP"
        "MAIN", "ACCESSORY", "CORRECTIVE" -> "MAIN"
        "COOLDOWN" -> "COOLDOWN"
        else -> "OTHER"
    }

    private fun Map<String, String>.localized(language: String): String {
        val primary = if (language == "ar") this["ar"] else this["en"]
        val fallback = if (language == "ar") this["en"] else this["ar"]
        return primary?.takeIf { it.isNotBlank() }
            ?: fallback?.takeIf { it.isNotBlank() }
            ?: values.firstOrNull().orEmpty()
    }

    private fun com.movit.core.network.dto.LocalizedNameDto.localized(language: String): String {
        val primary = if (language == "ar") ar else en
        val fallback = if (language == "ar") en else ar
        return primary.takeIf { it.isNotBlank() }
            ?: fallback.takeIf { it.isNotBlank() }
            ?: ""
    }
}
