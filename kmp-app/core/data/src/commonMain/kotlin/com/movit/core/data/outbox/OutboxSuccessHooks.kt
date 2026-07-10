package com.movit.core.data.outbox

import com.movit.core.data.local.MovitLocalStore
import com.movit.core.data.repository.DayCustomizationLocalStore
import com.movit.core.network.MovitJson

/** Post-success local cache reconciliation (P1.4 / P1.9 / P2.8). */
internal object OutboxSuccessHooks {
    fun apply(localStore: MovitLocalStore, type: OutboxOperationType, payload: String) {
        when (type) {
            OutboxOperationType.SAVE_DAY_CUSTOMIZATIONS -> onDayCustomizationsSucceeded(localStore, payload)
            else -> Unit
        }
    }

    private fun onDayCustomizationsSucceeded(localStore: MovitLocalStore, payload: String) {
        val parsed = runCatching {
            MovitJson.decodeFromString<SaveDayCustomizationsOutboxPayload>(payload)
        }.getOrNull() ?: return
        DayCustomizationLocalStore(localStore).markServerAcknowledged(
            userProgramId = parsed.userProgramId,
            weekNumber = parsed.weekNumber,
            dayNumber = parsed.dayNumber,
        )
    }
}
