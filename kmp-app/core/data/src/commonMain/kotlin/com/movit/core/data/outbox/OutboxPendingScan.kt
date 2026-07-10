package com.movit.core.data.outbox

import com.movit.core.data.local.MovitLocalStore
import com.movit.core.network.MovitJson

/** Scans outbox rows that must block server-wins hydration (pending or in-flight dispatch). */
internal object OutboxPendingScan {
    fun awaitingDispatch(entries: List<OutboxEntry>): List<OutboxEntry> =
        entries.filter { entry ->
            entry.status == OutboxStatus.PENDING || entry.status == OutboxStatus.IN_FLIGHT
        }

    suspend fun awaitingDispatch(localStore: MovitLocalStore): List<OutboxEntry> =
        awaitingDispatch(localStore.listAllOutbox())

    fun pendingDayCustomizationKeys(entries: List<OutboxEntry>): Set<DayCustomizationDayKey> {
        val keys = mutableSetOf<DayCustomizationDayKey>()
        for (entry in awaitingDispatch(entries)) {
            if (entry.type != OutboxOperationType.SAVE_DAY_CUSTOMIZATIONS) continue
            val payload = runCatching {
                MovitJson.decodeFromString<SaveDayCustomizationsOutboxPayload>(entry.payload)
            }.getOrNull() ?: continue
            keys += DayCustomizationDayKey(
                userProgramId = payload.userProgramId,
                weekNumber = payload.weekNumber,
                dayNumber = payload.dayNumber,
            )
        }
        return keys
    }

    fun pendingPlannedWorkoutIds(entries: List<OutboxEntry>): Set<String> {
        val ids = mutableSetOf<String>()
        for (entry in awaitingDispatch(entries)) {
            when (entry.type) {
                OutboxOperationType.PLANNED_WORKOUT_COMPLETE,
                OutboxOperationType.PLANNED_WORKOUT_REPORT,
                -> {
                    val workoutId = runCatching {
                        MovitJson.decodeFromString<PlannedWorkoutCompleteOutboxPayload>(entry.payload)
                            .workoutId
                    }.getOrNull()?.takeIf { it.isNotBlank() } ?: continue
                    ids += workoutId
                }
                else -> Unit
            }
        }
        return ids
    }
}

data class DayCustomizationDayKey(
    val userProgramId: String,
    val weekNumber: Int,
    val dayNumber: Int,
)
