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
 * ProgramDayCalculator — Date-based program day calculation.
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
 * The program does NOT loop — when dayIndex >= totalDays, the program is complete.
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
        return getCurrentDayFromDate(program, startDate)
    }

    /**
     * Calculate current day from a specific start date.
     */
    fun getCurrentDayFromDate(program: ProgramConfig, startDate: Date): CurrentDayRef? {
        if (program.weeks.isEmpty()) return null

        val dayIndex = getDayIndex(startDate, Date())
        val totalDays = program.durationWeeks * 7
        val isProgramComplete = dayIndex >= totalDays

        // If complete, point to the last day
        val effectiveDayIndex = if (isProgramComplete) totalDays - 1 else dayIndex

        val weekNumber = (effectiveDayIndex / 7) + 1
        val dayNumber = (effectiveDayIndex % 7) + 1

        val week = program.weeks.firstOrNull { it.weekNumber == weekNumber }
        val day = week?.days?.firstOrNull { it.dayNumber == dayNumber }

        if (week == null || day == null) {
            // Fallback: if the program's week/day structure doesn't match,
            // find the closest available day
            return findClosestDay(program, weekNumber, dayNumber, dayIndex, isProgramComplete)
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
     * Calculate the overall progress percentage.
     */
    fun calculateProgress(
        program: ProgramConfig,
        reportStore: ProgramSessionReportStore
    ): Float {
        val allReports = reportStore.getAll().filter { it.programId == program.id }
        val totalNonRestDays = program.weeks.sumOf { w ->
            w.days.count { d -> !d.isRestDay && d.sessions.isNotEmpty() }
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
     * Check if a specific day is completed (all sessions done).
     */
    fun isDayComplete(
        program: ProgramConfig,
        weekNumber: Int,
        dayNumber: Int,
        reportStore: ProgramSessionReportStore,
        customizationStore: DayCustomizationStore
    ): Boolean {
        val week = program.weeks.firstOrNull { it.weekNumber == weekNumber } ?: return false
        val day = week.days.firstOrNull { it.dayNumber == dayNumber } ?: return false

        if (day.isRestDay) return true

        val effectiveSessions = customizationStore.getEffectiveSessions(
            programId = program.id,
            weekNumber = weekNumber,
            dayNumber = dayNumber,
            originalSessions = day.sessions
        )

        if (effectiveSessions.isEmpty()) return true

        val reports = reportStore.getByDay(program.id, weekNumber, dayNumber)
        val completedSessionIds = reports.map { it.sessionId }.toSet()

        return effectiveSessions.all { it.id in completedSessionIds }
    }

    // ═══════════════════════════════════════════════════════════
    // Private helpers
    // ═══════════════════════════════════════════════════════════

    private fun getDayIndex(startDate: Date, now: Date): Int {
        val startCal = normalizeToDay(startDate)
        val todayCal = normalizeToDay(now)
        val diffMs = (todayCal.timeInMillis - startCal.timeInMillis).coerceAtLeast(0)
        return (diffMs / (24L * 60L * 60L * 1000L)).toInt()
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

    private fun findClosestDay(
        program: ProgramConfig,
        targetWeek: Int,
        targetDay: Int,
        dayIndex: Int,
        isProgramComplete: Boolean
    ): CurrentDayRef? {
        // 1. Try to find the target week
        val week = program.weeks.firstOrNull { it.weekNumber == targetWeek }
        if (week != null) {
            // Try exact day match
            val exactDay = week.days.firstOrNull { it.dayNumber == targetDay }
            if (exactDay != null) {
                return CurrentDayRef(week, exactDay, targetWeek, targetDay, dayIndex, isProgramComplete)
            }

            // Find the closest day in this week (prefer the nearest day number)
            val closestDay = week.days.minByOrNull { kotlin.math.abs(it.dayNumber - targetDay) }
            if (closestDay != null) {
                return CurrentDayRef(week, closestDay, targetWeek, closestDay.dayNumber, dayIndex, isProgramComplete)
            }
        }

        // 2. Week not found — find the closest available week
        val closestWeek = program.weeks.minByOrNull { kotlin.math.abs(it.weekNumber - targetWeek) }
        if (closestWeek != null) {
            val day = closestWeek.days.firstOrNull { it.dayNumber == targetDay }
                ?: closestWeek.days.minByOrNull { kotlin.math.abs(it.dayNumber - targetDay) }

            if (day != null) {
                return CurrentDayRef(closestWeek, day, closestWeek.weekNumber, day.dayNumber, dayIndex, isProgramComplete)
            }
        }

        // 3. Last resort: return the last day of the last week
        // ONLY mark as complete if dayIndex actually exceeded the program duration
        val lastWeek = program.weeks.maxByOrNull { it.weekNumber } ?: return null
        val lastDay = lastWeek.days.maxByOrNull { it.dayNumber } ?: return null
        return CurrentDayRef(lastWeek, lastDay, lastWeek.weekNumber, lastDay.dayNumber, dayIndex, isProgramComplete)
    }
}
