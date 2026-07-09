package com.movit.core.training.report

import com.movit.core.training.config.LocalizedText
import com.movit.core.training.journal.WorkoutUpload
import com.movit.core.training.session.ExerciseWorkoutSummary
import kotlinx.serialization.Serializable

/**
 * KMP session/day report payload — matches legacy [WorkoutTrainingEngine.WorkoutReport] JSON shape
 * for planned-workouts complete/report endpoints.
 */
@Serializable
data class MovitSessionReport(
    val totalExercises: Int,
    val totalSetsCompleted: Int,
    val totalSetsPlanned: Int,
    val totalReps: Int,
    val totalDurationMs: Long,
    val averageAccuracy: Float,
    val averageFormScore: Float,
    val exerciseReports: List<MovitExerciseSessionReport> = emptyList(),
    val reportIds: List<String> = emptyList(),
    val executionIds: List<String> = emptyList(),
)

@Serializable
data class MovitExerciseSessionReport(
    val exerciseSlug: String,
    val exerciseName: String,
    val setsCompleted: Int,
    val totalSets: Int,
    val totalReps: Int,
    val averageAccuracy: Float,
    val averageFormScore: Float,
    val reportId: String? = null,
    val workoutId: String? = null,
    val poseVariantIndex: Int = 0,
)

/** Assessment-mode lightweight result returned to onboarding flow. */
@Serializable
data class AssessmentTrainingResult(
    val sessionId: String,
    val exerciseSlug: String,
    val totalReps: Int,
    val averageFormScore: Float,
    val durationMs: Long,
    val upload: WorkoutUpload? = null,
)

object MovitSessionReportBuilder {
    fun fromExerciseExecution(
        upload: WorkoutUpload,
        summary: ExerciseWorkoutSummary,
        exerciseSlug: String,
        exerciseName: LocalizedText,
        setsCompleted: Int = 1,
        totalSets: Int = 1,
        reportId: String? = null,
        poseVariantIndex: Int = summary.poseVariantIndex,
    ): MovitSessionReport {
        val exerciseReport = MovitExerciseSessionReport(
            exerciseSlug = exerciseSlug,
            exerciseName = exerciseName.en,
            setsCompleted = setsCompleted,
            totalSets = totalSets,
            totalReps = summary.totalReps,
            averageAccuracy = summary.countedRatio * 100f,
            averageFormScore = summary.averageScore,
            reportId = reportId,
            workoutId = upload.id,
            poseVariantIndex = poseVariantIndex,
        )
        return MovitSessionReport(
            totalExercises = 1,
            totalSetsCompleted = 1,
            totalSetsPlanned = totalSets,
            totalReps = summary.totalReps,
            totalDurationMs = summary.durationMs,
            averageAccuracy = summary.countedRatio * 100f,
            averageFormScore = summary.averageScore,
            exerciseReports = listOf(exerciseReport),
            reportIds = listOfNotNull(reportId),
            executionIds = listOf(upload.id),
        )
    }

    fun mergeExercise(
        existing: MovitSessionReport,
        upload: WorkoutUpload,
        summary: ExerciseWorkoutSummary,
        exerciseSlug: String,
        exerciseName: LocalizedText,
        setsCompleted: Int,
        totalSets: Int,
        reportId: String? = null,
        poseVariantIndex: Int = summary.poseVariantIndex,
    ): MovitSessionReport {
        val existingIndex = existing.exerciseReports.indexOfFirst { it.exerciseSlug == exerciseSlug }
        val isNewExercise = existingIndex < 0
        val mergedExercise = if (existingIndex >= 0) {
            val prev = existing.exerciseReports[existingIndex]
            val weightedForm = weightedAverageBySet(
                previousAverage = prev.averageFormScore,
                previousSetsCompleted = prev.setsCompleted,
                newSetValue = summary.averageScore,
            )
            val weightedAccuracy = weightedAverageBySet(
                previousAverage = prev.averageAccuracy,
                previousSetsCompleted = prev.setsCompleted,
                newSetValue = summary.countedRatio * 100f,
            )
            prev.copy(
                setsCompleted = setsCompleted,
                totalSets = maxOf(prev.totalSets, totalSets),
                totalReps = prev.totalReps + summary.totalReps,
                averageAccuracy = weightedAccuracy,
                averageFormScore = weightedForm,
                reportId = reportId ?: prev.reportId,
                workoutId = upload.id,
                poseVariantIndex = poseVariantIndex,
            )
        } else {
            MovitExerciseSessionReport(
                exerciseSlug = exerciseSlug,
                exerciseName = exerciseName.en,
                setsCompleted = setsCompleted,
                totalSets = totalSets,
                totalReps = summary.totalReps,
                averageAccuracy = summary.countedRatio * 100f,
                averageFormScore = summary.averageScore,
                reportId = reportId,
                workoutId = upload.id,
                poseVariantIndex = poseVariantIndex,
            )
        }
        val exercises = if (existingIndex >= 0) {
            existing.exerciseReports.toMutableList().also { it[existingIndex] = mergedExercise }
        } else {
            existing.exerciseReports + mergedExercise
        }
        val totalReps = exercises.sumOf { it.totalReps }
        val avgAccuracy = if (exercises.isEmpty()) 0f else exercises.map { it.averageAccuracy }.average().toFloat()
        val avgForm = if (exercises.isEmpty()) 0f else exercises.map { it.averageFormScore }.average().toFloat()
        return existing.copy(
            totalExercises = exercises.size,
            totalSetsCompleted = existing.totalSetsCompleted + 1,
            totalSetsPlanned = existing.totalSetsPlanned + if (isNewExercise) totalSets else 0,
            totalReps = totalReps,
            totalDurationMs = existing.totalDurationMs + summary.durationMs,
            averageAccuracy = avgAccuracy,
            averageFormScore = avgForm,
            exerciseReports = exercises,
            reportIds = existing.reportIds + listOfNotNull(reportId),
            executionIds = existing.executionIds + upload.id,
        )
    }

    private fun weightedAverageBySet(
        previousAverage: Float,
        previousSetsCompleted: Int,
        newSetValue: Float,
    ): Float {
        val prevSets = previousSetsCompleted.coerceAtLeast(0)
        if (prevSets == 0) return newSetValue
        return ((previousAverage * prevSets) + newSetValue) / (prevSets + 1)
    }

    fun toAssessmentResult(
        sessionId: String,
        exerciseSlug: String,
        summary: ExerciseWorkoutSummary,
        upload: WorkoutUpload?,
    ): AssessmentTrainingResult = AssessmentTrainingResult(
        sessionId = sessionId,
        exerciseSlug = exerciseSlug,
        totalReps = summary.totalReps,
        averageFormScore = summary.averageScore,
        durationMs = summary.durationMs,
        upload = upload,
    )
}
