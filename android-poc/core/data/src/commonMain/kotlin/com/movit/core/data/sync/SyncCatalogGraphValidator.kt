package com.movit.core.data.sync

import com.movit.core.data.repository.TrainingConfigRepository
import com.movit.core.data.repository.WorkoutExportMapper
import com.movit.core.network.dto.ProgramExportDto
import com.movit.core.network.dto.WorkoutExportDto

/**
 * Verifies program → workout template → exercise references resolve in local catalog caches
 * after `/api/mobile/sync` apply.
 */
data class SyncCatalogGraphReport(
    val missingWorkoutTemplateIds: List<String> = emptyList(),
    val missingExerciseSlugs: List<String> = emptyList(),
) {
    val isComplete: Boolean =
        missingWorkoutTemplateIds.isEmpty() && missingExerciseSlugs.isEmpty()
}

object SyncCatalogGraphValidator {

    fun validate(
        programs: List<ProgramExportDto>,
        workoutTemplates: List<WorkoutExportDto>,
        trainingConfig: TrainingConfigRepository,
    ): SyncCatalogGraphReport {
        val workoutIds = workoutTemplates.map { it.id }.filter { it.isNotBlank() }.toSet()
        val missingWorkouts = linkedSetOf<String>()
        val missingExercises = linkedSetOf<String>()

        programs.forEach { program ->
            program.weeks.forEach { week ->
                week.days.forEach { day ->
                    day.plannedWorkouts.forEach { planned ->
                        val templateId = planned.workoutTemplateId.trim()
                        if (templateId.isNotBlank() && templateId !in workoutIds) {
                            missingWorkouts += templateId
                        }
                        planned.items.forEach { item ->
                            if (item.type.equals("exercise", ignoreCase = true) && !item.deletedExercise) {
                                val slug = item.exerciseSlug?.trim().orEmpty()
                                if (slug.isNotBlank() && !trainingConfig.supports(slug)) {
                                    missingExercises += slug
                                }
                            }
                        }
                    }
                }
            }
        }

        workoutTemplates.forEach { template ->
            WorkoutExportMapper.exerciseSlugs(template).forEach { slug ->
                if (!trainingConfig.supports(slug)) {
                    missingExercises += slug
                }
            }
        }

        return SyncCatalogGraphReport(
            missingWorkoutTemplateIds = missingWorkouts.sorted(),
            missingExerciseSlugs = missingExercises.sorted(),
        )
    }
}
