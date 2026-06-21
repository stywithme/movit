package com.movit.feature.library

data class SessionDayContext(
    val plannedWorkoutCards: List<PlannedWorkoutCardUi> = emptyList(),
    val catchUpPrompt: SessionCatchUpPromptUi? = null,
)

internal object SessionCatchUpResolver {
    fun resolve(
        weekNumber: Int,
        dayNumber: Int,
        catchUpMessage: String?,
        missedSlots: List<Pair<Int, Int>>,
    ): SessionCatchUpPromptUi? {
        if (catchUpMessage.isNullOrBlank() || missedSlots.isEmpty()) return null
        val isCatchUpDay = missedSlots.any { (week, day) ->
            week == weekNumber && day == dayNumber
        }
        if (!isCatchUpDay) return null
        val (missedWeek, missedDay) = missedSlots.first()
        return SessionCatchUpPromptUi(
            message = catchUpMessage,
            missedWeekNumber = missedWeek,
            missedDayNumber = missedDay,
        )
    }
}
