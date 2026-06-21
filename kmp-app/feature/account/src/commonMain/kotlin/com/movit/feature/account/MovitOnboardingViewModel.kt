package com.movit.feature.account

import androidx.lifecycle.ViewModel
import com.movit.shared.AppResult
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

class MovitOnboardingViewModel(
    private val repository: OnboardingRepository = defaultOnboardingRepository(),
) : ViewModel() {
    private val workScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val _state = MutableStateFlow(MovitOnboardingUiState())
    val state: StateFlow<MovitOnboardingUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<MovitOnboardingEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<MovitOnboardingEffect> = _effects.asSharedFlow()

    fun onEvent(event: MovitOnboardingEvent) {
        when (event) {
            MovitOnboardingEvent.BackClicked -> goBack()
            MovitOnboardingEvent.ContinueClicked -> goForward()
            MovitOnboardingEvent.RetrySubmitClicked -> submit()
            is MovitOnboardingEvent.AgeChanged -> {
                val age = event.value.toIntOrNull()
                updateData { it.copy(ageYears = age) }
            }
            is MovitOnboardingEvent.SexSelected -> {
                updateData { it.copy(biologicalSex = event.value) }
            }
            is MovitOnboardingEvent.HeightChanged -> {
                updateData { it.copy(heightCm = event.value.toFloatOrNull()) }
            }
            is MovitOnboardingEvent.WeightChanged -> {
                updateData { it.copy(weightKg = event.value.toFloatOrNull()) }
            }
            is MovitOnboardingEvent.ExperienceSelected -> {
                updateData {
                    it.copy(
                        resistanceExperience = event.value,
                        targetDaysPerWeek = it.targetDaysPerWeek ?: OnboardingData.DEFAULT_DAYS_PER_WEEK,
                    )
                }
            }
            is MovitOnboardingEvent.TargetDaysChanged -> {
                updateData { it.copy(targetDaysPerWeek = event.days.coerceIn(1, 7)) }
            }
            is MovitOnboardingEvent.GoalSelected -> {
                updateData { it.copy(trainingGoal = event.value) }
            }
            is MovitOnboardingEvent.WeekdayToggled -> {
                updateData { current ->
                    val next = current.trainingWeekdays.toMutableSet()
                    if (next.contains(event.day)) next.remove(event.day) else next.add(event.day)
                    current.copy(trainingWeekdays = next)
                }
            }
            is MovitOnboardingEvent.LocationSelected -> {
                updateData { current ->
                    current.copy(trainingLocation = event.value).withHomeLocationDefaults()
                }
            }
            is MovitOnboardingEvent.EquipmentToggled -> {
                updateData { current ->
                    val next = current.availableEquipment.toMutableSet()
                    if (event.enabled) next.add(event.code) else next.remove(event.code)
                    current.copy(availableEquipment = next)
                }
            }
            is MovitOnboardingEvent.DisclaimerChanged -> {
                updateData { it.copy(healthDisclaimerAccepted = event.accepted) }
            }
        }
    }

    private fun goBack() {
        val step = _state.value.step
        if (step > OnboardingData.STEP_AGE_GENDER && !_state.value.isSubmitting) {
            _state.update {
                it.copy(
                    step = step - 1,
                    validationErrorKey = null,
                    submitErrorMessage = null,
                )
            }
        }
    }

    private fun goForward() {
        val current = _state.value
        if (current.isSubmitting) return

        val data = current.data.withDefaultsForStep(current.step)
        if (data != current.data) {
            _state.update { it.copy(data = data) }
        }

        val errorKey = data.stepErrorKey(current.step)
        if (errorKey != null) {
            _state.update { it.copy(validationErrorKey = errorKey) }
            return
        }

        if (current.step == OnboardingData.STEP_SUMMARY) {
            submit()
            return
        }

        val nextStep = current.step + 1
        _state.update {
            it.copy(
                step = nextStep,
                data = data.withDefaultsForStep(nextStep),
                validationErrorKey = null,
                submitErrorMessage = null,
            )
        }
    }

    private fun submit() {
        val data = _state.value.data
        if (!data.isStepValid(OnboardingData.STEP_SUMMARY)) {
            _state.update {
                it.copy(validationErrorKey = data.stepErrorKey(OnboardingData.STEP_SUMMARY))
            }
            return
        }

        workScope.launch {
            _state.update { it.copy(isSubmitting = true, submitErrorMessage = null) }
            when (val result = repository.putTrainingProfile(data)) {
                is AppResult.Success -> {
                    _state.update { it.copy(isSubmitting = false) }
                    _effects.tryEmit(MovitOnboardingEffect.Completed)
                }
                is AppResult.Failure -> {
                    _state.update {
                        it.copy(isSubmitting = false, submitErrorMessage = result.message)
                    }
                }
            }
        }
    }

    private inline fun updateData(block: (OnboardingData) -> OnboardingData) {
        _state.update { it.copy(data = block(it.data), validationErrorKey = null) }
    }

    override fun onCleared() {
        workScope.cancel()
        super.onCleared()
    }
}
