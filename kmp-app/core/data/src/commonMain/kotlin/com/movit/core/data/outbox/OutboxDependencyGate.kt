package com.movit.core.data.outbox

import com.movit.core.network.MovitJson

/**
 * Defers planned-workout complete/report until dependent execution uploads settle (D-N3 / P1.2).
 */
internal object OutboxDependencyGate {
    fun shouldDeferComplete(
        completeEntry: OutboxEntry,
        allEntries: List<OutboxEntry>,
    ): DeferDecision {
        if (
            completeEntry.type != OutboxOperationType.PLANNED_WORKOUT_COMPLETE &&
            completeEntry.type != OutboxOperationType.PLANNED_WORKOUT_REPORT
        ) {
            return DeferDecision.Proceed
        }

        val groupId = runCatching {
            MovitJson.decodeFromString<PlannedWorkoutCompleteOutboxPayload>(completeEntry.payload)
                .workoutGroupId
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()

        val executions = allEntries.filter { it.type == OutboxOperationType.WORKOUT_EXECUTION_UPLOAD }
        val relevant = if (groupId != null) {
            executions.filter { executionGroupId(it) == groupId }
        } else {
            // Legacy rows: any older execution still pending blocks this complete.
            executions.filter { it.createdAt <= completeEntry.createdAt }
        }

        val pending = relevant.any {
            it.status == OutboxStatus.PENDING || it.status == OutboxStatus.IN_FLIGHT
        }
        if (pending) return DeferDecision.WaitForExecutions

        val failedPermanent = relevant.any { it.status == OutboxStatus.FAILED_PERMANENT }
        if (failedPermanent) return DeferDecision.ProceedWithWarning

        return DeferDecision.Proceed
    }

    private fun executionGroupId(entry: OutboxEntry): String? =
        runCatching {
            MovitJson.decodeFromString<WorkoutExecutionUploadOutboxPayload>(entry.payload)
                .request.workoutGroupId
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
}

internal enum class DeferDecision {
    Proceed,
    WaitForExecutions,
    ProceedWithWarning,
}
