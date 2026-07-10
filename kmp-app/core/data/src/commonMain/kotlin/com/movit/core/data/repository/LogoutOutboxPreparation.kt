package com.movit.core.data.repository

/**
 * UX.4 — outcome of a short outbox flush before logout/delete.
 */
data class LogoutOutboxPreparation(
    val pendingCount: Long,
    val flushAttempted: Boolean,
) {
    val requiresWarning: Boolean get() = pendingCount > 0
}
