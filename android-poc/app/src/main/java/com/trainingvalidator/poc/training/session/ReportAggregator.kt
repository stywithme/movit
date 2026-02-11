package com.trainingvalidator.poc.training.session

import com.trainingvalidator.poc.storage.ProgramSessionReportStore.ProgramSessionLocalReport

/**
 * ReportAggregator — Single source of truth for aggregating reports
 * across all levels: Session → Day → Week → Program.
 *
 * All UI code should use this aggregator instead of computing manually.
 */
object ReportAggregator {

    // ═══════════════════════════════════════════════════════════
    // Aggregated Report Data Classes
    // ═══════════════════════════════════════════════════════════

    data class DayReport(
        val programId: String,
        val weekNumber: Int,
        val dayNumber: Int,
        val sessionsCompleted: Int,
        val sessionsTotal: Int,
        val totalExercises: Int,
        val totalSets: Int,
        val totalReps: Int,
        val totalDurationMs: Long,
        val averageAccuracy: Float,
        val averageFormScore: Float,
        val isComplete: Boolean
    )

    data class WeekReport(
        val programId: String,
        val weekNumber: Int,
        val daysCompleted: Int,
        val daysTotal: Int,
        val totalSessions: Int,
        val totalExercises: Int,
        val totalSets: Int,
        val totalReps: Int,
        val totalDurationMs: Long,
        val averageAccuracy: Float,
        val averageFormScore: Float,
        val dayReports: List<DayReport>
    )

    data class ProgramReport(
        val programId: String,
        val daysTrained: Int,
        val totalExercises: Int,
        val totalSets: Int,
        val totalReps: Int,
        val totalDurationMs: Long,
        val averageAccuracy: Float,
        val averageFormScore: Float,
        val weekReports: List<WeekReport>
    )

    // ═══════════════════════════════════════════════════════════
    // Aggregation Methods
    // ═══════════════════════════════════════════════════════════

    /**
     * Aggregate session reports into a day report.
     *
     * @param sessionReports All session reports for this day
     * @param totalSessionsInDay Total sessions defined in the program for this day
     */
    fun aggregateDay(
        programId: String,
        weekNumber: Int,
        dayNumber: Int,
        sessionReports: List<ProgramSessionLocalReport>,
        totalSessionsInDay: Int
    ): DayReport {
        val totalExercises = sessionReports.sumOf {
            it.report?.exerciseReports?.size ?: 0
        }
        val totalSets = sessionReports.sumOf { it.totalSetsCompleted }
        val totalReps = sessionReports.sumOf { it.totalReps }
        val totalDurationMs = sessionReports.sumOf { it.totalDurationMs }

        val accuracies = sessionReports.map { it.averageAccuracy }.filter { it > 0f }
        val avgAccuracy = if (accuracies.isNotEmpty()) accuracies.average().toFloat() else 0f

        val formScores = sessionReports.mapNotNull { it.averageFormScore.takeIf { s -> s > 0f } }
        val avgFormScore = if (formScores.isNotEmpty()) formScores.average().toFloat() else 0f

        return DayReport(
            programId = programId,
            weekNumber = weekNumber,
            dayNumber = dayNumber,
            sessionsCompleted = sessionReports.size,
            sessionsTotal = totalSessionsInDay,
            totalExercises = totalExercises,
            totalSets = totalSets,
            totalReps = totalReps,
            totalDurationMs = totalDurationMs,
            averageAccuracy = avgAccuracy,
            averageFormScore = avgFormScore,
            isComplete = sessionReports.size >= totalSessionsInDay && totalSessionsInDay > 0
        )
    }

    /**
     * Aggregate day reports into a week report.
     *
     * @param dayReports All day reports for this week
     * @param totalDaysInWeek Total non-rest days in this week
     */
    fun aggregateWeek(
        programId: String,
        weekNumber: Int,
        dayReports: List<DayReport>,
        totalDaysInWeek: Int
    ): WeekReport {
        val daysCompleted = dayReports.count { it.isComplete }
        val totalSessions = dayReports.sumOf { it.sessionsCompleted }
        val totalExercises = dayReports.sumOf { it.totalExercises }
        val totalSets = dayReports.sumOf { it.totalSets }
        val totalReps = dayReports.sumOf { it.totalReps }
        val totalDurationMs = dayReports.sumOf { it.totalDurationMs }

        val accuracies = dayReports.map { it.averageAccuracy }.filter { it > 0f }
        val avgAccuracy = if (accuracies.isNotEmpty()) accuracies.average().toFloat() else 0f

        val formScores = dayReports.map { it.averageFormScore }.filter { it > 0f }
        val avgFormScore = if (formScores.isNotEmpty()) formScores.average().toFloat() else 0f

        return WeekReport(
            programId = programId,
            weekNumber = weekNumber,
            daysCompleted = daysCompleted,
            daysTotal = totalDaysInWeek,
            totalSessions = totalSessions,
            totalExercises = totalExercises,
            totalSets = totalSets,
            totalReps = totalReps,
            totalDurationMs = totalDurationMs,
            averageAccuracy = avgAccuracy,
            averageFormScore = avgFormScore,
            dayReports = dayReports
        )
    }

    /**
     * Aggregate week reports into a program report.
     */
    fun aggregateProgram(
        programId: String,
        weekReports: List<WeekReport>
    ): ProgramReport {
        val daysTrained = weekReports.sumOf { it.daysCompleted }
        val totalExercises = weekReports.sumOf { it.totalExercises }
        val totalSets = weekReports.sumOf { it.totalSets }
        val totalReps = weekReports.sumOf { it.totalReps }
        val totalDurationMs = weekReports.sumOf { it.totalDurationMs }

        val accuracies = weekReports.map { it.averageAccuracy }.filter { it > 0f }
        val avgAccuracy = if (accuracies.isNotEmpty()) accuracies.average().toFloat() else 0f

        val formScores = weekReports.map { it.averageFormScore }.filter { it > 0f }
        val avgFormScore = if (formScores.isNotEmpty()) formScores.average().toFloat() else 0f

        return ProgramReport(
            programId = programId,
            daysTrained = daysTrained,
            totalExercises = totalExercises,
            totalSets = totalSets,
            totalReps = totalReps,
            totalDurationMs = totalDurationMs,
            averageAccuracy = avgAccuracy,
            averageFormScore = avgFormScore,
            weekReports = weekReports
        )
    }

    // ═══════════════════════════════════════════════════════════
    // Form Score Rating — human-friendly labels
    // ═══════════════════════════════════════════════════════════

    enum class FormRating(val label: String) {
        EXCELLENT("Excellent"),
        GOOD("Good"),
        SOLID("Solid"),
        KEEP_PRACTICING("Keep Practicing");
    }

    fun getFormRating(formScore: Float): FormRating {
        return when {
            formScore >= 85f -> FormRating.EXCELLENT
            formScore >= 70f -> FormRating.GOOD
            formScore >= 50f -> FormRating.SOLID
            else -> FormRating.KEEP_PRACTICING
        }
    }

    /**
     * Format duration as human-friendly string.
     */
    fun formatDuration(durationMs: Long): String {
        val totalMinutes = durationMs / 60000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }
}
