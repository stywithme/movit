package com.trainingvalidator.poc.ui.programs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.trainingvalidator.poc.R
import androidx.lifecycle.viewModelScope
import com.trainingvalidator.poc.network.ApiClient
import com.trainingvalidator.poc.storage.AuthManager
import com.trainingvalidator.poc.storage.ExerciseRepository
import com.trainingvalidator.poc.storage.HomeRepository
import com.trainingvalidator.poc.storage.ProgramRepository
import com.trainingvalidator.poc.storage.ProgramWorkoutReportStore
import com.trainingvalidator.poc.training.models.ProgramConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class ProgramDetailUiState {
    object Loading : ProgramDetailUiState()
    data class Success(val program: ProgramConfig, val isEnrolled: Boolean) : ProgramDetailUiState()
    data class Error(val message: String) : ProgramDetailUiState()
}

sealed class EnrollState {
    object Idle : EnrollState()
    object Loading : EnrollState()
    /** User must confirm replacing their current active program. */
    data class ConfirmReplace(
        val programIdToEnroll: String,
        val currentProgramName: String,
        val progressPercent: Int
    ) : EnrollState()
    object Success : EnrollState()
    data class Error(val message: String) : EnrollState()
}

class ProgramDetailViewModel(app: Application) : AndroidViewModel(app) {

    private val programRepository by lazy { ProgramRepository.getInstance(app) }
    private val homeRepository by lazy { HomeRepository.getInstance(app) }
    val reportStore by lazy { ProgramWorkoutReportStore(app) }

    private val _uiState = MutableStateFlow<ProgramDetailUiState>(ProgramDetailUiState.Loading)
    val uiState: StateFlow<ProgramDetailUiState> = _uiState.asStateFlow()

    private val _enrollState = MutableStateFlow<EnrollState>(EnrollState.Idle)
    val enrollState: StateFlow<EnrollState> = _enrollState.asStateFlow()

    fun loadProgram(slug: String) {
        viewModelScope.launch {
            _uiState.value = ProgramDetailUiState.Loading
            try {
                val program = withContext(Dispatchers.IO) {
                    programRepository.getOrFetchProgram(slug)
                }
                if (program == null) {
                    _uiState.value = ProgramDetailUiState.Error("not_found")
                    return@launch
                }
                withContext(Dispatchers.IO) { homeRepository.syncFromServer() }
                val isEnrolled = checkEnrollment(program)
                _uiState.value = ProgramDetailUiState.Success(program, isEnrolled)
            } catch (e: Exception) {
                _uiState.value = ProgramDetailUiState.Error(e.message ?: "unknown")
            }
        }
    }

    fun refreshEnrollment() {
        val current = _uiState.value as? ProgramDetailUiState.Success ?: return
        val isEnrolled = checkEnrollment(current.program)
        _uiState.value = current.copy(isEnrolled = isEnrolled)
    }

    fun enrollInProgram(programId: String) {
        if (_enrollState.value is EnrollState.Loading) return
        _enrollState.value = EnrollState.Loading
        viewModelScope.launch {
            try {
                val token = withContext(Dispatchers.IO) {
                    AuthManager.getAuthHeader(getApplication())
                }
                if (token == null) {
                    _enrollState.value = EnrollState.Error("no_auth")
                    return@launch
                }
                val checkResp = withContext(Dispatchers.IO) {
                    ApiClient.mobileSyncApi.enrollmentCheck(token, programId)
                }
                val checkBody = checkResp.body()
                if (!checkResp.isSuccessful || checkBody?.success != true) {
                    _enrollState.value = EnrollState.Error("enroll_check_failed")
                    return@launch
                }
                val check = checkBody.data
                if (check?.willReplace == true && check.hasActiveProgram) {
                    val ap = check.activeProgram
                    val name = resolveLocalizedProgramName(ap?.name)
                    val pct = ap?.progress?.percentage ?: 0
                    _enrollState.value = EnrollState.ConfirmReplace(
                        programIdToEnroll = programId,
                        currentProgramName = name.ifBlank { getApplication<Application>().getString(R.string.enroll_replace_current_program_fallback) },
                        progressPercent = pct
                    )
                    return@launch
                }
                performEnroll(programId, token)
            } catch (e: Exception) {
                _enrollState.value = EnrollState.Error(e.message ?: "unknown")
            }
        }
    }

    /** Call after user confirms replacing the active program. */
    fun confirmEnrollReplace(programId: String) {
        if (_enrollState.value is EnrollState.Loading) return
        _enrollState.value = EnrollState.Loading
        viewModelScope.launch {
            try {
                val token = withContext(Dispatchers.IO) {
                    AuthManager.getAuthHeader(getApplication())
                }
                if (token == null) {
                    _enrollState.value = EnrollState.Error("no_auth")
                    return@launch
                }
                performEnroll(programId, token)
            } catch (e: Exception) {
                _enrollState.value = EnrollState.Error(e.message ?: "unknown")
            }
        }
    }

    private suspend fun performEnroll(programId: String, token: String) {
        val payload = mapOf("programId" to programId)
        val response = withContext(Dispatchers.IO) {
            ApiClient.mobileSyncApi.enrollProgram(token, payload)
        }
        if (response.isSuccessful && response.body()?.success == true) {
            withContext(Dispatchers.IO) {
                HomeRepository.getInstance(getApplication()).syncFromServer()
                try {
                    ExerciseRepository.getInstance(getApplication()).checkForUpdates()
                } catch (_: Exception) { }
            }
            refreshEnrollment()
            if ((_uiState.value as? ProgramDetailUiState.Success)?.isEnrolled != true) {
                markProgramEnrolled(programId)
            }
            _enrollState.value = EnrollState.Success
        } else {
            _enrollState.value = EnrollState.Error("enroll_failed")
        }
    }

    fun resolveCurrentWeek(programId: String): Int {
        val cachedHome = homeRepository.getCachedData()
        cachedHome?.trainMode?.activeProgram
            ?.takeIf { it.id == programId }
            ?.weekNumber
            ?.takeIf { it > 0 }
            ?.let { return it }

        val activePlan = cachedHome?.activePlan
        val enrollment = activePlan?.programs?.firstOrNull {
            it.program?.id == programId && it.status == "active"
        }
        return enrollment?.progress?.currentWeek?.takeIf { it > 0 } ?: 1
    }

    fun resetEnrollState() {
        _enrollState.value = EnrollState.Idle
    }

    private fun markProgramEnrolled(programId: String) {
        val current = _uiState.value as? ProgramDetailUiState.Success ?: return
        if (current.program.id == programId) {
            _uiState.value = current.copy(isEnrolled = true)
        }
    }

    private fun resolveLocalizedProgramName(name: Map<String, String>?): String {
        if (name.isNullOrEmpty()) return ""
        val res = getApplication<Application>().resources
        val lang = res.configuration.locales.get(0)?.language ?: "en"
        return name[lang]?.takeIf { it.isNotBlank() }
            ?: name["en"]?.takeIf { it.isNotBlank() }
            ?: name.values.firstOrNull { it.isNotBlank() }.orEmpty()
    }

    private fun checkEnrollment(program: ProgramConfig): Boolean {
        val cachedHome = homeRepository.getCachedData()
        val cachedPlan = cachedHome?.activePlan
        if (cachedPlan?.programs?.any { it.program?.id == program.id && it.status == "active" } == true) {
            return true
        }

        if (cachedHome?.trainMode?.activeProgram?.id == program.id) {
            return true
        }

        val activeUserProgram = programRepository.getActiveUserProgramExport()
        return activeUserProgram?.isActive == true && activeUserProgram.programId == program.id
    }

    suspend fun callPauseCalendar(): Boolean = withContext(Dispatchers.IO) {
        val token = AuthManager.getAuthHeader(getApplication()) ?: return@withContext false
        val r = ApiClient.mobileSyncApi.pausePlan(token)
        if (!r.isSuccessful) return@withContext false
        val body = r.body() ?: return@withContext false
        @Suppress("UNCHECKED_CAST")
        val map = body as? Map<*, *> ?: return@withContext false
        return@withContext map["success"] == true
    }

    suspend fun callResumeCalendar(): Boolean = withContext(Dispatchers.IO) {
        val token = AuthManager.getAuthHeader(getApplication()) ?: return@withContext false
        val r = ApiClient.mobileSyncApi.resumePlan(token)
        if (!r.isSuccessful) return@withContext false
        val body = r.body() ?: return@withContext false
        @Suppress("UNCHECKED_CAST")
        val map = body as? Map<*, *> ?: return@withContext false
        return@withContext map["success"] == true
    }
}
