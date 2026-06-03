package com.trainingvalidator.poc.ui.onboarding

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Activity-scoped state holder shared across all onboarding step fragments.
 * Each step reads/writes the single [OnboardingData] instance so state survives
 * page swipes and configuration changes.
 */
class OnboardingViewModel : ViewModel() {

    private val _data = MutableStateFlow(OnboardingData())
    val data: StateFlow<OnboardingData> = _data.asStateFlow()

    /** Display unit only — values are always stored metric in [OnboardingData]. */
    var weightUnit: String = UNIT_KG
    var heightUnit: String = UNIT_CM

    private inline fun update(block: (OnboardingData) -> OnboardingData) {
        _data.value = block(_data.value)
    }

    fun setAge(age: Int) = update { it.copy(ageYears = age) }
    fun setBiologicalSex(sex: String) = update { it.copy(biologicalSex = sex) }
    fun setHeightCm(cm: Float) = update { it.copy(heightCm = cm) }
    fun setWeightKg(kg: Float) = update { it.copy(weightKg = kg) }
    fun setResistanceExperience(level: String) = update { it.copy(resistanceExperience = level) }
    fun setTargetDaysPerWeek(days: Int) = update { it.copy(targetDaysPerWeek = days) }
    fun setTrainingGoal(goal: String) = update { it.copy(trainingGoal = goal) }
    fun setTrainingWeekdays(days: Set<Int>) = update { it.copy(trainingWeekdays = days) }

    fun setTrainingLocation(location: String) = update {
        // Switching away from home keeps prior equipment selection; gym overrides at payload time.
        it.copy(trainingLocation = location)
    }

    fun toggleEquipment(code: String, enabled: Boolean) = update {
        val next = it.availableEquipment.toMutableSet()
        if (enabled) next.add(code) else next.remove(code)
        it.copy(availableEquipment = next)
    }

    fun setHealthDisclaimerAccepted(accepted: Boolean) =
        update { it.copy(healthDisclaimerAccepted = accepted) }

    /** Per-step gate used by the host to enable the "Next/Continue" button. */
    fun isStepValid(step: Int): Boolean {
        val d = _data.value
        return when (step) {
            STEP_AGE_GENDER -> d.ageYears != null && d.biologicalSex != null
            STEP_BODY_METRICS -> d.heightCm != null && d.weightKg != null
            STEP_EXPERIENCE -> d.resistanceExperience != null && d.targetDaysPerWeek != null
            STEP_GOAL -> d.trainingGoal != null
            STEP_WEEKDAYS -> d.trainingWeekdays.isNotEmpty()
            STEP_LOCATION -> d.trainingLocation != null &&
                (d.trainingLocation == "gym" || d.availableEquipment.isNotEmpty())
            STEP_SUMMARY -> d.healthDisclaimerAccepted
            else -> true
        }
    }

    companion object {
        const val UNIT_KG = "kg"
        const val UNIT_LB = "lb"
        const val UNIT_CM = "cm"
        const val UNIT_FT = "ft"

        const val STEP_AGE_GENDER = 0
        const val STEP_BODY_METRICS = 1
        const val STEP_EXPERIENCE = 2
        const val STEP_GOAL = 3
        const val STEP_WEEKDAYS = 4
        const val STEP_LOCATION = 5
        const val STEP_SUMMARY = 6
        const val STEP_COUNT = 7
    }
}
