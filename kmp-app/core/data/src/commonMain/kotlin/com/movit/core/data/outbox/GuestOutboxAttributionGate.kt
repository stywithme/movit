package com.movit.core.data.outbox

/**
 * UX.7 gate: pending guest outbox rows waiting for explicit attribution after login.
 * UI can poll [pendingGuestCount] / [pendingPrompt] and call [accept] or [discard].
 */
data class GuestOutboxAttributionPrompt(
    val guestRowCount: Int,
)

class GuestOutboxAttributionGate(
    private val localStore: com.movit.core.data.local.MovitLocalStore,
) {
    private var acceptedForUserId: String? = null

    suspend fun pendingGuestCount(): Int = localStore.countGuestOutbox().toInt()

    suspend fun pendingPrompt(): GuestOutboxAttributionPrompt? {
        val count = pendingGuestCount()
        if (count <= 0) return null
        return GuestOutboxAttributionPrompt(guestRowCount = count)
    }

    /** True when the current user has accepted guest rows for replay (or there are none). */
    fun isGuestReplayAccepted(userId: String): Boolean =
        acceptedForUserId != null && acceptedForUserId == userId

    suspend fun accept(userId: String) {
        require(userId.isNotBlank())
        localStore.attributeGuestOutboxToUser(userId)
        acceptedForUserId = userId
    }

    suspend fun discard() {
        localStore.deleteAllGuestOutbox()
        acceptedForUserId = null
    }

    fun clearAcceptance() {
        acceptedForUserId = null
    }
}
