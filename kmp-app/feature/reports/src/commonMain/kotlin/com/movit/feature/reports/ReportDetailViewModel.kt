package com.movit.feature.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movit.core.data.MovitData
import com.movit.shared.AppResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReportDetailUiState(
    val isLoading: Boolean = false,
    val report: ReportDetailUi? = null,
    val selectedPage: ReportDetailPage = ReportDetailPage.Overview,
    val errorMessage: String? = null,
    /** UX.6 — pending / synced / failed. */
    val uploadStatus: ReportUploadStatus = ReportUploadStatus.Synced,
)

enum class ReportUploadStatus {
    Pending,
    Synced,
    Failed,
}

class ReportDetailViewModel(
    private val reportId: String,
    private val repository: ReportDetailRepository = defaultReportDetailRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(ReportDetailUiState(isLoading = true))
    val state: StateFlow<ReportDetailUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<ReportDetailEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<ReportDetailEffect> = _effects.asSharedFlow()

    fun onEvent(event: ReportDetailEvent) {
        when (event) {
            is ReportDetailEvent.PageSelected -> onPageSelected(event.page)
            ReportDetailEvent.ShareClicked -> onShareClicked()
            ReportDetailEvent.ExportClicked -> onExportClicked()
            ReportDetailEvent.RetryClicked -> Unit
        }
    }

    fun loadInitial() {
        viewModelScope.launch { load() }
    }

    suspend fun load() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        when (val result = repository.getReportDetail(reportId)) {
            is AppResult.Success -> {
                _state.update {
                    it.copy(
                        isLoading = false,
                        report = result.value,
                        uploadStatus = resolveUploadStatus(reportId),
                    )
                }
            }
            is AppResult.Failure -> {
                _state.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
            }
        }
    }

    private suspend fun resolveUploadStatus(id: String): ReportUploadStatus {
        if (!MovitData.isInstalled) return ReportUploadStatus.Synced
        val entry = MovitData.offlineWrites.listVisibleOutbox().firstOrNull { it.id == id }
            ?: return ReportUploadStatus.Synced
        return when (entry.status) {
            com.movit.core.data.outbox.OutboxStatus.FAILED_PERMANENT -> ReportUploadStatus.Failed
            com.movit.core.data.outbox.OutboxStatus.PENDING,
            com.movit.core.data.outbox.OutboxStatus.IN_FLIGHT,
            -> ReportUploadStatus.Pending
            else -> ReportUploadStatus.Synced
        }
    }

    fun onPageSelected(page: ReportDetailPage) {
        _state.update { it.copy(selectedPage = page) }
    }

    fun onShareClicked() {
        emitShareEffect(isExport = false)
    }

    fun onExportClicked() {
        emitShareEffect(isExport = true)
    }

    private fun emitShareEffect(isExport: Boolean) {
        val report = _state.value.report ?: return
        viewModelScope.launch {
            val language = if (MovitData.isInstalled) {
                MovitData.requirePlatform().preferredLanguage()
            } else {
                "en"
            }
            val payload = ReportDetailShareFormatter.sharePayload(
                report = report,
                language = language,
                isExport = isExport,
            )
            val effect = if (isExport) {
                ReportDetailEffect.ExportRequested(payload)
            } else {
                ReportDetailEffect.ShareRequested(payload)
            }
            _effects.emit(effect)
        }
    }
}

sealed interface ReportDetailEffect {
    data class ShareRequested(val payload: ReportSharePayload) : ReportDetailEffect
    data class ExportRequested(val payload: ReportSharePayload) : ReportDetailEffect
}
