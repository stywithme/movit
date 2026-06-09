package com.movit.feature.account

import com.movit.core.network.dto.TrainingProfilePutRequest

/**
 * Collected answers from the profile onboarding flow.
 * Always stored in metric units (kg, cm). Maps to [TrainingProfilePutRequest].
 */
data class OnboardingData(
    val ageYears: Int? = null,
    val biologicalSex: String? = null,
    val heightCm: Float? = null,
    val weightKg: Float? = null,
    val resistanceExperience: String? = null,
    val targetDaysPerWeek: Int? = null,
    val trainingGoal: String? = null,
    val trainingWeekdays: Set<Int> = emptySet(),
    val trainingLocation: String? = null,
    val availableEquipment: Set<String> = emptySet(),
    val healthDisclaimerAccepted: Boolean = false,
) {
    fun dateOfBirthIso(): String? {
        val age = ageYears ?: return null
        val birthYear = currentCalendarYear() - age
        return "$birthYear-01-01"
    }

    private fun experienceMonths(): Int? = when (resistanceExperience) {
        "beginner" -> 0
        "intermediate" -> 12
        "advanced" -> 36
        else -> null
    }

    private fun effectiveEquipment(): List<String> {
        return if (trainingLocation == "gym") {
            GYM_EQUIPMENT
        } else {
            (availableEquipment + "bodyweight").toList()
        }
    }

    fun toTrainingProfileRequest(): TrainingProfilePutRequest {
        val weekdays = trainingWeekdays.sorted()
        return TrainingProfilePutRequest(
            dateOfBirth = dateOfBirthIso(),
            biologicalSex = biologicalSex,
            heightCm = heightCm,
            weightKg = weightKg,
            resistanceExperience = resistanceExperience,
            trainingExperienceMonths = experienceMonths(),
            trainingWeekdays = weekdays,
            availableDaysPerWeek = weekdays.size.takeIf { it > 0 } ?: targetDaysPerWeek,
            trainingLocation = trainingLocation,
            availableEquipment = effectiveEquipment(),
            healthDisclaimerAccepted = healthDisclaimerAccepted,
            trainingGoal = trainingGoal,
        )
    }

    fun isStepValid(step: Int): Boolean = when (step) {
        STEP_AGE_GENDER -> ageYears != null && biologicalSex != null
        STEP_BODY_METRICS -> heightCm != null && weightKg != null
        STEP_EXPERIENCE -> resistanceExperience != null && targetDaysPerWeek != null
        STEP_GOAL -> trainingGoal != null
        STEP_WEEKDAYS -> trainingWeekdays.isNotEmpty()
        STEP_LOCATION -> trainingLocation != null &&
            (trainingLocation == "gym" || availableEquipment.isNotEmpty())
        STEP_SUMMARY -> healthDisclaimerAccepted
        else -> true
    }

    companion object {
        const val STEP_AGE_GENDER = 0
        const val STEP_BODY_METRICS = 1
        const val STEP_EXPERIENCE = 2
        const val STEP_GOAL = 3
        const val STEP_WEEKDAYS = 4
        const val STEP_LOCATION = 5
        const val STEP_SUMMARY = 6
        const val STEP_COUNT = 7

        val GYM_EQUIPMENT = listOf(
            "bodyweight", "dumbbell", "barbell", "kettlebell",
            "cable", "machine", "bench", "bands", "pull_up_bar", "mat",
        )

        val HOME_EQUIPMENT = listOf(
            "dumbbell", "bands", "kettlebell", "pull_up_bar", "bench", "mat",
        )
    }
}
