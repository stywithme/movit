package com.movit.core.data.outbox

/**
 * Deterministic replay order for mixed offline writes in one training session.
 *
 * Execution metrics must reach the backend before planned-workout completion so progression
 * and report hydration see the uploaded exercise data.
 */
internal object OutboxReplayOrdering {
    fun sortForReplay(entries: List<OutboxEntry>): List<OutboxEntry> =
        entries.sortedWith(
            compareBy({ priority(it.type) }, { it.createdAt }, { it.id }),
        )

    private fun priority(type: OutboxOperationType): Int = when (type) {
        OutboxOperationType.WORKOUT_EXECUTION_UPLOAD -> 0
        OutboxOperationType.PLANNED_WORKOUT_START -> 10
        OutboxOperationType.SAVE_DAY_CUSTOMIZATIONS -> 20
        OutboxOperationType.EXERCISE_PREFERENCE_UPSERT -> 30
        OutboxOperationType.EXERCISE_PREFERENCE_DELETE -> 31
        OutboxOperationType.USER_PROGRAM_OVERRIDE_CREATE -> 40
        OutboxOperationType.USER_PROGRAM_OVERRIDE_DELETE -> 41
        OutboxOperationType.PROGRESSION_MARK_SEEN -> 50
        OutboxOperationType.PLANNED_WORKOUT_COMPLETE -> 80
        OutboxOperationType.PLANNED_WORKOUT_REPORT -> 85
        OutboxOperationType.PLAN_COMPLETE -> 90
    }
}
