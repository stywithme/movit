package com.movit.core.training.session

/**
 * Workout-level progress semantics (legacy [countsTowardWorkoutProgress] parity).
 *
 * Warm-up, activation, and cool-down exercises are performed but excluded from totals.
 */
object WorkoutFlowProgress {
    private val EXCLUDED_ROLES = setOf("WARMUP", "ACTIVATION", "COOLDOWN")

    fun countsTowardWorkoutProgress(phaseRole: String?): Boolean {
        val role = phaseRole?.trim()?.uppercase().orEmpty()
        if (role.isEmpty()) return true
        return role !in EXCLUDED_ROLES
    }

    /**
     * @param itemIndex index of the current flow item (exercise slot).
     * @param completedSetsInCurrent number of sets already finished in the current exercise (0-based).
     */
    fun percentComplete(
        items: List<TrainingFlowItem>,
        itemIndex: Int,
        completedSetsInCurrent: Int,
    ): Int {
        val counting = items.filterIsInstance<TrainingFlowItem.Exercise>()
            .filter { countsTowardWorkoutProgress(it.phaseRole) }
        if (counting.isEmpty()) return 0

        val totalSets = counting.sumOf { it.sets.coerceAtLeast(1) }
        if (totalSets == 0) return 0

        var completed = 0
        for (item in items) {
            if (item !is TrainingFlowItem.Exercise) continue
            val itemPos = items.indexOf(item)
            if (itemPos < itemIndex) {
                if (countsTowardWorkoutProgress(item.phaseRole)) {
                    completed += item.sets.coerceAtLeast(1)
                }
            } else if (itemPos == itemIndex) {
                if (countsTowardWorkoutProgress(item.phaseRole)) {
                    completed += completedSetsInCurrent.coerceAtLeast(0)
                }
                break
            }
        }
        return ((completed.toFloat() / totalSets) * 100).toInt().coerceIn(0, 100)
    }
}
