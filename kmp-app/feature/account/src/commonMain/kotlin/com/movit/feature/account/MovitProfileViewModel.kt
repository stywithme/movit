package com.movit.feature.account

import androidx.lifecycle.ViewModel
import com.movit.core.data.MovitData
import com.movit.core.data.cache.MovitSyncMetadataStore
import com.movit.core.data.outbox.OutboxStatus
import com.movit.core.data.repository.AccountSyncRepository
import com.movit.core.data.sync.MovitSyncOrchestrator
import com.movit.core.data.sync.MovitSyncTelemetry
import com.movit.shared.AppResult
import com.movit.shared.PlatformInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MovitProfileViewModel(
    private val repository: ProfileRepository = defaultProfileRepository(),
) : ViewModel() {
    private val workScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val _state = MutableStateFlow(MovitProfileUiState(isLoading = true))
    val state: StateFlow<MovitProfileUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<MovitProfileEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<MovitProfileEffect> = _effects.asSharedFlow()

    fun loadInitial() {
        workScope.launch { load() }
    }

    suspend fun load() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        when (val result = repository.loadProfile()) {
            is AppResult.Success -> {
                _state.update {
                    it.copy(
                        isLoading = false,
                        isSignedIn = true,
                        profile = result.value,
                        errorMessage = null,
                    )
                }
                refreshSyncSection()
            }
            is AppResult.Failure -> {
                val signedOut = result.message.contains("Sign in", ignoreCase = true)
                _state.update {
                    it.copy(
                        isLoading = false,
                        isSignedIn = !signedOut,
                        profile = null,
                        errorMessage = if (signedOut) null else result.message,
                        syncItems = emptyList(),
                    )
                }
            }
        }
    }

    fun onEvent(event: MovitProfileEvent) {
        when (event) {
            MovitProfileEvent.RetryClicked -> {
                workScope.launch { load() }
            }
            MovitProfileEvent.SignInClicked -> _effects.tryEmit(MovitProfileEffect.OpenAuth)
            MovitProfileEvent.ViewPlansClicked -> openSubscription()
            MovitProfileEvent.ManageSubscriptionClicked -> openSubscription()
            MovitProfileEvent.SubscribeNowClicked -> launchLegacyBilling(restorePurchases = false)
            MovitProfileEvent.RestorePurchasesClicked -> launchLegacyBilling(restorePurchases = true)
            MovitProfileEvent.CloseSubscriptionClicked -> {
                _state.update { it.copy(showSubscription = false) }
            }
            MovitProfileEvent.EditProfileClicked -> {
                _effects.tryEmit(MovitProfileEffect.ShowLocalizedMessage("profile_edit_coming_soon"))
            }
            MovitProfileEvent.TrainingProfileClicked -> {
                _effects.tryEmit(MovitProfileEffect.OpenOnboarding)
            }
            MovitProfileEvent.AssessmentClicked -> {
                _effects.tryEmit(MovitProfileEffect.OpenAssessment)
            }
            MovitProfileEvent.LevelClicked -> {
                _effects.tryEmit(MovitProfileEffect.OpenLevel)
            }
            MovitProfileEvent.LanguageClicked -> {
                _state.update { it.copy(activePicker = ProfilePicker.Language) }
            }
            MovitProfileEvent.AppearanceClicked -> {
                _state.update { it.copy(activePicker = ProfilePicker.Appearance) }
            }
            MovitProfileEvent.LogoutClicked -> {
                workScope.launch { beginLogoutFlow() }
            }
            MovitProfileEvent.LogoutConfirmed -> {
                _state.update { it.copy(activePicker = null) }
                logout(discardPendingOutbox = false)
            }
            MovitProfileEvent.LogoutUploadThenSignOut -> {
                _state.update { it.copy(activePicker = null) }
                logoutAfterRetryFlush()
            }
            MovitProfileEvent.LogoutDiscardPending -> {
                _state.update { it.copy(activePicker = null) }
                logout(discardPendingOutbox = true)
            }
            MovitProfileEvent.DeleteAccountClicked -> {
                workScope.launch { beginDeleteAccountFlow() }
            }
            MovitProfileEvent.DeleteAccountConfirmed -> {
                _state.update { it.copy(activePicker = null) }
                deleteAccount(discardPendingOutbox = false)
            }
            MovitProfileEvent.DeleteAccountUploadThenDelete -> {
                _state.update { it.copy(activePicker = null) }
                deleteAccountAfterRetryFlush()
            }
            MovitProfileEvent.DeleteAccountDiscardPending -> {
                _state.update { it.copy(activePicker = null) }
                deleteAccount(discardPendingOutbox = true)
            }
            MovitProfileEvent.LogoutDismissed,
            MovitProfileEvent.DeleteAccountDismissed,
            MovitProfileEvent.PickerDismissed,
            -> {
                _state.update { it.copy(activePicker = null) }
            }
            is MovitProfileEvent.LanguageSelected -> selectLanguage(event.languageCode)
            is MovitProfileEvent.AppearanceSelected -> selectAppearance(event.themeMode)
            is MovitProfileEvent.AudioCuesChanged -> toggleAudioCues(event.enabled)
            is MovitProfileEvent.HapticChanged -> toggleHaptic(event.enabled)
            MovitProfileEvent.TrainingDebugLabClicked -> {
                if (PlatformInfo.supportsTrainingDebugLab) {
                    _effects.tryEmit(MovitProfileEffect.OpenTrainingDebugLab)
                }
            }
            MovitProfileEvent.SyncRetryClicked -> {
                workScope.launch { retryFailedSync() }
            }
            MovitProfileEvent.SyncRepairCatalogClicked -> {
                workScope.launch { repairCatalog() }
            }
            MovitProfileEvent.SyncNowClicked -> {
                workScope.launch { syncAllNow() }
            }
        }
    }

    private suspend fun refreshSyncSection() {
        if (!MovitData.isInstalled) return
        val items = MovitData.offlineWrites.listVisibleOutbox().map { entry ->
            ProfileSyncItemUi(
                id = entry.id,
                typeLabel = entry.type.name,
                statusLabel = when (entry.status) {
                    OutboxStatus.FAILED_PERMANENT -> "failed"
                    OutboxStatus.IN_FLIGHT -> "inflight"
                    else -> "pending"
                },
                isFailed = entry.status == OutboxStatus.FAILED_PERMANENT,
            )
        }
        val lastOk = readLastSuccessfulSyncAt()
        _state.update {
            it.copy(
                syncItems = items,
                lastSuccessfulSyncAt = lastOk,
            )
        }
    }

    private fun readLastSuccessfulSyncAt(): String? {
        if (!MovitData.isInstalled) return null
        val store = MovitData.localStore
        val cycle = MovitSyncTelemetry(store).readLastSyncCycle()
        if (cycle != null && cycle.outcome != "success") {
            // Still show last successful watermark when available.
        }
        return MovitSyncMetadataStore(store).readLastSyncTimestamp()
    }

    private suspend fun retryFailedSync() {
        if (!MovitData.isInstalled) return
        _state.update {
            it.copy(
                isSyncBusy = true,
                syncStatusMessage = "profile_sync_syncing",
                syncStatusMessageArg = null,
            )
        }
        val reset = MovitData.offlineWrites.retryFailedPermanent()
        refreshSyncSection()
        _state.update {
            it.copy(
                isSyncBusy = false,
                syncStatusMessage = if (reset > 0) {
                    "profile_sync_retry_done"
                } else {
                    "profile_sync_retry_none"
                },
                syncStatusMessageArg = if (reset > 0) reset.toString() else null,
            )
        }
    }

    private suspend fun syncAllNow() {
        if (!MovitData.isInstalled) return
        _state.update {
            it.copy(
                isSyncBusy = true,
                syncStatusMessage = "profile_sync_syncing",
                syncStatusMessageArg = null,
            )
        }
        val outcome = MovitData.sync.fullRefresh()
        refreshSyncSection()
        _state.update {
            it.copy(
                isSyncBusy = false,
                syncStatusMessage = outcomeToStatusKey(outcome),
                syncStatusMessageArg = null,
                lastSuccessfulSyncAt = when (outcome) {
                    is MovitSyncOrchestrator.SyncOutcome.Success ->
                        readLastSuccessfulSyncAt() ?: it.lastSuccessfulSyncAt
                    else -> it.lastSuccessfulSyncAt
                },
            )
        }
    }

    /** R2: repair = full refresh (catalog + configs + messages), not explore-only. */
    private suspend fun repairCatalog() {
        if (!MovitData.isInstalled) return
        _state.update {
            it.copy(
                isSyncBusy = true,
                syncStatusMessage = "profile_sync_syncing",
                syncStatusMessageArg = null,
            )
        }
        val outcome = MovitData.sync.fullRefresh()
        refreshSyncSection()
        _state.update {
            it.copy(
                isSyncBusy = false,
                syncStatusMessage = when (outcome) {
                    is MovitSyncOrchestrator.SyncOutcome.Success -> "profile_sync_repair_done"
                    else -> outcomeToStatusKey(outcome)
                },
                syncStatusMessageArg = null,
                lastSuccessfulSyncAt = when (outcome) {
                    is MovitSyncOrchestrator.SyncOutcome.Success ->
                        readLastSuccessfulSyncAt() ?: it.lastSuccessfulSyncAt
                    else -> it.lastSuccessfulSyncAt
                },
            )
        }
    }

    private fun outcomeToStatusKey(outcome: MovitSyncOrchestrator.SyncOutcome): String =
        when (outcome) {
            is MovitSyncOrchestrator.SyncOutcome.Success -> "profile_sync_success"
            is MovitSyncOrchestrator.SyncOutcome.Offline -> "profile_sync_failed_offline"
            MovitSyncOrchestrator.SyncOutcome.Skipped -> "profile_sync_skipped"
            is MovitSyncOrchestrator.SyncOutcome.Error -> when (outcome.kind) {
                MovitSyncOrchestrator.SyncOutcome.ErrorKind.Network -> "profile_sync_failed_offline"
                MovitSyncOrchestrator.SyncOutcome.ErrorKind.Http,
                MovitSyncOrchestrator.SyncOutcome.ErrorKind.Decode,
                MovitSyncOrchestrator.SyncOutcome.ErrorKind.Unknown,
                -> "profile_sync_failed_server"
            }
        }

    private fun openSubscription() {
        if (!PlatformInfo.supportsInAppSubscription) {
            _effects.tryEmit(MovitProfileEffect.ShowLocalizedMessage("profile_subscription_ios_unavailable"))
            return
        }
        _state.update { it.copy(showSubscription = true) }
        _effects.tryEmit(MovitProfileEffect.OpenSubscription(restorePurchases = false))
    }

    private fun launchLegacyBilling(restorePurchases: Boolean) {
        if (!PlatformInfo.supportsInAppSubscription) {
            _effects.tryEmit(MovitProfileEffect.ShowLocalizedMessage("profile_subscription_ios_unavailable"))
            return
        }
        _state.update { it.copy(showSubscription = false) }
        _effects.tryEmit(MovitProfileEffect.OpenSubscription(restorePurchases = restorePurchases))
    }

    private suspend fun beginLogoutFlow() {
        val prep = repository.prepareLogout()
        _state.update {
            it.copy(
                activePicker = if (prep.requiresWarning) {
                    ProfilePicker.LogoutPendingOutbox(prep.pendingCount)
                } else {
                    ProfilePicker.LogoutConfirm
                },
            )
        }
    }

    private suspend fun beginDeleteAccountFlow() {
        val prep = repository.prepareLogout()
        _state.update {
            it.copy(
                activePicker = if (prep.requiresWarning) {
                    ProfilePicker.DeleteAccountPendingOutbox(prep.pendingCount)
                } else {
                    ProfilePicker.DeleteAccountConfirm
                },
            )
        }
    }

    private fun logout(discardPendingOutbox: Boolean) {
        workScope.launch {
            _state.update { it.copy(isLoggingOut = true) }
            when (val result = repository.logout(discardPendingOutbox = discardPendingOutbox)) {
                is AppResult.Success -> {
                    _state.update {
                        MovitProfileUiState(
                            isLoading = false,
                            isSignedIn = false,
                        )
                    }
                    _effects.tryEmit(MovitProfileEffect.LoggedOut)
                }
                is AppResult.Failure -> {
                    _state.update { it.copy(isLoggingOut = false) }
                    if (resultIndicatesPendingOutbox(result.message)) {
                        _state.update { state ->
                            state.copy(
                                activePicker = ProfilePicker.LogoutPendingOutbox(
                                    pendingCountFromLogoutFailure(result.message),
                                ),
                            )
                        }
                    } else {
                        _effects.tryEmit(MovitProfileEffect.ShowMessage("Unable to sign out."))
                    }
                }
            }
        }
    }

    private fun logoutAfterRetryFlush() {
        workScope.launch {
            _state.update { it.copy(isLoggingOut = true) }
            val prep = repository.prepareLogout(
                flushTimeoutMs = AccountSyncRepository.LOGOUT_RETRY_FLUSH_TIMEOUT_MS,
            )
            if (prep.pendingCount > 0) {
                _state.update {
                    it.copy(
                        isLoggingOut = false,
                        activePicker = ProfilePicker.LogoutPendingOutbox(prep.pendingCount),
                    )
                }
                return@launch
            }
            logout(discardPendingOutbox = false)
        }
    }

    private fun deleteAccount(discardPendingOutbox: Boolean) {
        workScope.launch {
            _state.update { it.copy(isDeletingAccount = true) }
            when (val result = repository.deleteAccount(discardPendingOutbox = discardPendingOutbox)) {
                is AppResult.Success -> {
                    _state.update {
                        MovitProfileUiState(
                            isLoading = false,
                            isSignedIn = false,
                        )
                    }
                    _effects.tryEmit(MovitProfileEffect.LoggedOut)
                }
                is AppResult.Failure -> {
                    _state.update { it.copy(isDeletingAccount = false) }
                    if (resultIndicatesPendingOutbox(result.message)) {
                        _state.update { state ->
                            state.copy(
                                activePicker = ProfilePicker.DeleteAccountPendingOutbox(
                                    pendingCountFromLogoutFailure(result.message),
                                ),
                            )
                        }
                    } else {
                        _effects.tryEmit(MovitProfileEffect.ShowLocalizedMessage("profile_delete_account_error"))
                    }
                }
            }
        }
    }

    private fun deleteAccountAfterRetryFlush() {
        workScope.launch {
            _state.update { it.copy(isDeletingAccount = true) }
            val prep = repository.prepareLogout(
                flushTimeoutMs = AccountSyncRepository.LOGOUT_RETRY_FLUSH_TIMEOUT_MS,
            )
            if (prep.pendingCount > 0) {
                _state.update {
                    it.copy(
                        isDeletingAccount = false,
                        activePicker = ProfilePicker.DeleteAccountPendingOutbox(prep.pendingCount),
                    )
                }
                return@launch
            }
            deleteAccount(discardPendingOutbox = false)
        }
    }

    private fun resultIndicatesPendingOutbox(message: String): Boolean =
        message.startsWith("${AccountSyncRepository.PENDING_OUTBOX_LOGOUT_CODE}:")

    private fun pendingCountFromLogoutFailure(message: String): Long =
        message.substringAfter(':').toLongOrNull() ?: 1L

    private fun selectLanguage(languageCode: String) {
        workScope.launch {
            _state.update { it.copy(activePicker = null) }
            when (
                val result = repository.updateSettings(
                    ProfileSettingsUpdate(preferredLanguage = languageCode),
                )
            ) {
                is AppResult.Success -> {
                    _state.update { current ->
                        current.copy(profile = result.value)
                    }
                    _effects.tryEmit(MovitProfileEffect.LanguageChanged(languageCode))
                }
                is AppResult.Failure -> {
                    _effects.tryEmit(MovitProfileEffect.ShowMessage(result.message))
                }
            }
        }
    }

    private fun selectAppearance(themeMode: String) {
        workScope.launch {
            _state.update { it.copy(activePicker = null) }
            when (val result = repository.setThemeMode(themeMode)) {
                is AppResult.Success -> {
                    _state.update { current ->
                        current.copy(profile = result.value)
                    }
                    _effects.tryEmit(MovitProfileEffect.ThemeModeChanged(themeMode))
                }
                is AppResult.Failure -> {
                    _effects.tryEmit(MovitProfileEffect.ShowMessage(result.message))
                }
            }
        }
    }

    private fun toggleAudioCues(enabled: Boolean) {
        workScope.launch {
            when (val result = repository.updateSettings(ProfileSettingsUpdate(voiceFeedback = enabled))) {
                is AppResult.Success -> {
                    _state.update { current ->
                        current.copy(profile = result.value)
                    }
                }
                is AppResult.Failure -> {
                    _effects.tryEmit(MovitProfileEffect.ShowMessage(result.message))
                }
            }
        }
    }

    private fun toggleHaptic(enabled: Boolean) {
        workScope.launch {
            when (val result = repository.updateSettings(ProfileSettingsUpdate(notifications = enabled))) {
                is AppResult.Success -> {
                    _state.update { current ->
                        current.copy(profile = result.value)
                    }
                }
                is AppResult.Failure -> {
                    _effects.tryEmit(MovitProfileEffect.ShowMessage(result.message))
                }
            }
        }
    }

    override fun onCleared() {
        workScope.cancel()
        super.onCleared()
    }
}
