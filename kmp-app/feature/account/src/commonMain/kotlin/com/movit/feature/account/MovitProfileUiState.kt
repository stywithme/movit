package com.movit.feature.account

sealed interface ProfilePicker {
    data object Language : ProfilePicker
    data object Appearance : ProfilePicker
    data object LogoutConfirm : ProfilePicker
    data class LogoutPendingOutbox(val pendingCount: Long) : ProfilePicker
    data object DeleteAccountConfirm : ProfilePicker
    data class DeleteAccountPendingOutbox(val pendingCount: Long) : ProfilePicker
}

data class MovitProfileUiState(
    val isLoading: Boolean = true,
    val isSignedIn: Boolean = false,
    val profile: ProfileUi? = null,
    val errorMessage: String? = null,
    val showSubscription: Boolean = false,
    val isLoggingOut: Boolean = false,
    val isDeletingAccount: Boolean = false,
    val activePicker: ProfilePicker? = null,
    /** UX.2a — pending/failed outbox rows for the Sync settings section. */
    val syncItems: List<ProfileSyncItemUi> = emptyList(),
    val isSyncBusy: Boolean = false,
    val syncStatusMessage: String? = null,
)

data class ProfileSyncItemUi(
    val id: String,
    val typeLabel: String,
    val statusLabel: String,
    val isFailed: Boolean,
)
