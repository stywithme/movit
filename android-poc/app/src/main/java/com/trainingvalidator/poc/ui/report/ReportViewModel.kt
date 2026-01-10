package com.trainingvalidator.poc.ui.report

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trainingvalidator.poc.storage.ReportStorage
import com.trainingvalidator.poc.training.report.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * ReportViewModel - ViewModel for Post-Training Report screen
 * 
 * Manages report data loading and UI state.
 */
class ReportViewModel(application: Application) : AndroidViewModel(application) {
    
    companion object {
        private const val TAG = "ReportViewModel"
    }
    
    // Storage
    private val reportStorage = ReportStorage(application)
    
    // State
    private val _report = MutableStateFlow<PostTrainingReport?>(null)
    val report: StateFlow<PostTrainingReport?> = _report.asStateFlow()
    
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    // Current tab
    private val _currentTab = MutableStateFlow(0)
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()
    
    // Selected rep for detail dialog
    private val _selectedRep = MutableStateFlow<RepTimelineEntry?>(null)
    val selectedRep: StateFlow<RepTimelineEntry?> = _selectedRep.asStateFlow()
    
    // Language preference (ar/en)
    private val _useArabic = MutableStateFlow(false)
    val useArabic: StateFlow<Boolean> = _useArabic.asStateFlow()
    
    /**
     * Load report by ID
     */
    fun loadReport(reportId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                val loadedReport = reportStorage.getById(reportId)
                if (loadedReport != null) {
                    _report.value = loadedReport
                    Log.d(TAG, "Loaded report: ${loadedReport.id}")
                } else {
                    _error.value = "Report not found"
                    Log.e(TAG, "Report not found: $reportId")
                }
            } catch (e: Exception) {
                _error.value = "Failed to load report: ${e.message}"
                Log.e(TAG, "Error loading report", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Load the most recent report
     */
    fun loadLatestReport() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                val latestReport = reportStorage.getRecent(1).firstOrNull()
                if (latestReport != null) {
                    _report.value = latestReport
                    Log.d(TAG, "Loaded latest report: ${latestReport.id}")
                } else {
                    _error.value = "No reports found"
                }
            } catch (e: Exception) {
                _error.value = "Failed to load report: ${e.message}"
                Log.e(TAG, "Error loading latest report", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Set report directly (from TrainingActivity)
     */
    fun setReport(report: PostTrainingReport) {
        _report.value = report
        _isLoading.value = false
        _error.value = null
        Log.d(TAG, "Report set directly: ${report.id}")
    }
    
    /**
     * Change current tab
     */
    fun setCurrentTab(tabIndex: Int) {
        _currentTab.value = tabIndex
    }
    
    /**
     * Select a rep to show details
     */
    fun selectRep(rep: RepTimelineEntry?) {
        _selectedRep.value = rep
    }
    
    /**
     * Toggle language
     */
    fun toggleLanguage() {
        _useArabic.value = !_useArabic.value
    }
    
    /**
     * Set language preference
     */
    fun setUseArabic(useArabic: Boolean) {
        _useArabic.value = useArabic
    }
    
    // ==================== Computed Properties ====================
    
    /**
     * Get exercise name in current language
     */
    fun getExerciseName(): String {
        val report = _report.value ?: return ""
        return if (_useArabic.value) report.exerciseName.ar else report.exerciseName.en
    }
    
    /**
     * Get motivational message in current language
     */
    fun getMotivationalMessage(): String {
        val report = _report.value ?: return ""
        return if (_useArabic.value) {
            report.summary.motivationalMessage.ar
        } else {
            report.summary.motivationalMessage.en
        }
    }
    
    /**
     * Check if frame file exists
     */
    fun isFrameAvailable(frameUri: String?): Boolean {
        if (frameUri.isNullOrEmpty()) return false
        return File(frameUri).exists()
    }
    
    /**
     * Get best rep frames that are available
     */
    fun getAvailableBestRepFrames(): List<FrameCapture> {
        return _report.value?.bestReps
            ?.mapNotNull { it.frameCapture }
            ?.filter { isFrameAvailable(it.frameUri) }
            ?: emptyList()
    }
    
    /**
     * Get error frames that are available
     */
    fun getAvailableErrorFrames(): Map<String, FrameCapture> {
        return _report.value?.errorAnalysis
            ?.mapNotNull { error -> 
                error.errorFrame?.let { frame ->
                    if (isFrameAvailable(frame.frameUri)) error.errorKey to frame else null
                }
            }
            ?.toMap()
            ?: emptyMap()
    }
    
    /**
     * Delete current report
     */
    fun deleteReport() {
        val report = _report.value ?: return
        viewModelScope.launch {
            reportStorage.delete(report.id)
            _report.value = null
        }
    }
    
    // ==================== Factory ====================
    
    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ReportViewModel::class.java)) {
                return ReportViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
