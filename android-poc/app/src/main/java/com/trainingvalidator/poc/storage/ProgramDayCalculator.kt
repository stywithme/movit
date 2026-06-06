package com.trainingvalidator.poc.storage

import com.trainingvalidator.poc.network.UserProgramExport
import com.trainingvalidator.poc.training.models.ProgramConfig
import com.trainingvalidator.poc.training.models.ProgramDay
import com.trainingvalidator.poc.training.models.ProgramWeek
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * ProgramDayCalculator ? Date-based program day calculation.
 *
 * Determines the current week/day based on the enrollment start date,
 * matching the backend's calculation logic exactly.
 *
 * This is the SINGLE SOURCE OF TRUTH for "where is the user in the program?"
 * Both offline (from startDate) and online (from backend) use the same formula:
 *   dayIndex = floor((today - startDate) / 1 day)
 *   weekNumber = floor(dayIndex / 7) + 1
 *   dayNumber = (dayIndex % 7) + 1
 *
 * The program does NOT loop ? when dayIndex >= totalDays, the program is complete.
 */
object ProgramDayCalculator {

    data class CurrentDayRef(
        val week: ProgramWeek,
        val day: ProgramDay,
        val weekNumber: Int,
        val dayNumber: Int,
        val dayIndex: Int,       // 0-based total day offset from start
        val isProgramComplete: Boolean
    )

    /**
     * Calculate the current day reference using the enrollment start date.
     *
     * @param program The program config
     * @param userProgram The user's enrollment (contains startDate)
     * @return CurrentDayRef or null if program data is insufficient
     */
    fun getCurrentDay(program: ProgramConfig, userProgram: UserProgramExport): CurrentDayRef? {
        val startDate = parseStartDate(userProgram.startDate) ?: return null
        return getCurrentDayFromDate(program, startDate, userProgram)
    }

    /**
     * Calculate current day from a specific start date.
     */
    fun getCurrentDayFromDate(
        program: ProgramConfig,
        startDate: Date,
        userProgram: UserProgramExport? = null
    ): CurrentDayRef? {
        if (program.weeks.isEmpty()) return null

        val dayIndex = getDayIndex(startDate, Date(), userProgram)
        val totalDays = program.durationWeeks * 7
        val isProgramComplete = dayIndex >= totalDays

        val slotDayIndex = if (isProgramComplete) totalDays - 1 else dayIndex

        val weekNumber = kotlin.math.min(slotDayIndex / 7 + 1, program.durationWeeks)
        val dayNumber = (slotDayIndex % 7) + 1

        val week = program.weeks.firstOrNull { it.weekNumber == weekNumber }
        val day = week?.days?.firstOrNull { it.dayNumber == dayNumber }

        if (week == null || day == null) {
            return null
        }

        return CurrentDayRef(
            week = week,
            day = day,
            weekNumber = weekNumber,
            dayNumber = dayNumber,
            dayIndex = dayIndex,
            isProgramComplete = isProgramComplete
        )
    }

    /**
     * Resolve the real calendar date for a program day.
     * Day 1 is always the enrollment start date, not the calendar week's first day.
     */
    fun getDateForProgramDay(
        userProgram: UserProgramExport,
        weekNumber: Int,
        dayNumber: Int
    ): Date? {
        val startDate = parseStartDate(userProgram.startDate) ?: return null
        val offset = ((weekNumber - 1).coerceAtLeast(0) * 7) + (dayNumber - 1).coerceAtLeast(0)
        return normalizeToDay(startDate).apply {
            add(Calendar.DAY_OF_YEAR, offset)
        }.time
    }

    /**
     * Calculate the overall progress percentage.
     */
    fun calculateProgress(
        program: ProgramConfig,
        reportStore: ProgramWorkoutReportStore
    ): Float {
        val allReports = reportStore.getAll().filter { it.programId == program.id }
        val totalNonRestDays = program.weeks.sumOf { w ->
            w.days.count { d -> !d.isRestDay && d.workouts.isNotEmpty() }
        }
        if (totalNonRestDays == 0) return 0f

        // Count unique completed days
        val completedDays = allReports
            .map { "${it.weekNumber}_${it.dayNumber}" }
            .toSet()
            .size

        return (completedDays.toFloat() / totalNonRestDays.toFloat() * 100f).coerceIn(0f, 100f)
    }

    /**
     * Check if a specific day is completed (all planned workouts done).
     */
    fun isDayComplete(
        program: ProgramConfig,
        weekNumber: Int,
        dayNumber: Int,
        reportStore: ProgramWorkoutReportStore,
        customizationStore: DayCustomizationStore
    ): Boolean {
        val week = program.weeks.firstOrNull { it.weekNumber == weekNumber } ?: return false
        val day = week.days.firstOrNull { it.dayNumber == dayNumber } ?: return false

        if (day.isRestDay) return true

        val effectivePlannedWorkouts = customizationStore.getEffectivePlannedWorkouts(
            programId = program.id,
            weekNumber = weekNumber,
            dayNumber = dayNumber,
            originalWorkouts = day.workouts
        )

        if (effectivePlannedWorkouts.isEmpty()) return true

        val reports = reportStore.getByDay(program.id, weekNumber, dayNumber)
        val completedWorkoutIds = reports.map { it.workoutId }.toSet()

        return effectivePlannedWorkouts.all { it.id in completedWorkoutIds }
    }

    // -----------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------

    private fun getDayIndex(startDate: Date, now: Date, userProgram: UserProgramExport?): Int {
        val startCal = normalizeToDay(startDate)
        val todayCal = normalizeToDay(now)
        val diffMs = (todayCal.timeInMillis - startCal.timeInMillis).coerceAtLeast(0)
        var raw = (diffMs / (24L * 60L * 60L * 1000L)).toInt()
        if (userProgram != null) {
            val totalPaused = userProgram.totalPausedDays.coerceAtLeast(0)
            val ongoingPause = userProgram.pausedAt?.let { parseStartDate(it) }?.let { pausedSince ->
                getDayIndex(pausedSince, now, null)
            } ?: 0
            raw = (raw - totalPaused - ongoingPause).coerceAtLeast(0)
        }
        return raw
    }

    private fun normalizeToDay(date: Date): Calendar {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun parseStartDate(dateString: String): Date? {
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
            "yyyy-MM-dd"
        )
        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                return sdf.parse(dateString)
            } catch (_: Exception) {
                // Try next format
            }
        }
        return null
    }
}
