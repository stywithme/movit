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

    fun isAgeValid(): Boolean = ageYears?.let { it in MIN_AGE..MAX_AGE } == true

    fun isHeightValid(): Boolean = heightCm?.let { it in MIN_HEIGHT_CM..MAX_HEIGHT_CM } == true

    fun isWeightValid(): Boolean = weightKg?.let { it in MIN_WEIGHT_KG..MAX_WEIGHT_KG } == true

    fun isStepValid(step: Int): Boolean = when (step) {
        STEP_AGE_GENDER -> isAgeValid() && biologicalSex in VALID_SEX
        STEP_BODY_METRICS -> isHeightValid() && isWeightValid()
        STEP_EXPERIENCE -> resistanceExperience in VALID_EXPERIENCE && targetDaysPerWeek != null
        STEP_GOAL -> trainingGoal in VALID_GOALS
        STEP_WEEKDAYS -> trainingWeekdays.isNotEmpty()
        STEP_LOCATION -> trainingLocation in VALID_LOCATIONS &&
            (trainingLocation == "gym" || availableEquipment.isNotEmpty())
        STEP_SUMMARY -> healthDisclaimerAccepted
        else -> true
    }

    fun stepErrorKey(step: Int): String? {
        if (isStepValid(step)) return null
        return when (step) {
            STEP_AGE_GENDER -> when {
                ageYears == null -> "onboarding_error_age_required"
                !isAgeValid() -> "onboarding_error_age_range"
                biologicalSex == null -> "onboarding_error_sex_required"
                else -> "onboarding_error_step_incomplete"
            }
            STEP_BODY_METRICS -> when {
                heightCm == null || !isHeightValid() -> "onboarding_error_height_range"
                weightKg == null || !isWeightValid() -> "onboarding_error_weight_range"
                else -> "onboarding_error_step_incomplete"
            }
            STEP_EXPERIENCE -> when {
                resistanceExperience == null -> "onboarding_error_experience_required"
                targetDaysPerWeek == null -> "onboarding_error_sessions_required"
                else -> "onboarding_error_step_incomplete"
            }
            STEP_GOAL -> "onboarding_error_goal_required"
            STEP_WEEKDAYS -> "onboarding_error_weekdays_required"
            STEP_LOCATION -> when {
                trainingLocation == null -> "onboarding_error_location_required"
                trainingLocation == "home" && availableEquipment.isEmpty() ->
                    "onboarding_error_equipment_required"
                else -> "onboarding_error_step_incomplete"
            }
            STEP_SUMMARY -> "onboarding_error_disclaimer_required"
            else -> "onboarding_error_step_incomplete"
        }
    }

    fun withDefaultsForStep(step: Int): OnboardingData = when (step) {
        STEP_BODY_METRICS -> copy(
            heightCm = heightCm ?: DEFAULT_HEIGHT_CM,
            weightKg = weightKg ?: DEFAULT_WEIGHT_KG,
        )
        STEP_EXPERIENCE -> copy(
            targetDaysPerWeek = targetDaysPerWeek ?: DEFAULT_DAYS_PER_WEEK,
        )
        else -> this
    }

    fun withHomeLocationDefaults(): OnboardingData {
        if (trainingLocation != "home" || availableEquipment.isNotEmpty()) return this
        return copy(availableEquipment = setOf("bodyweight"))
    }

    companion object {
        const val MIN_AGE = 13
        const val MAX_AGE = 90
        const val DEFAULT_AGE = 25

        const val MIN_HEIGHT_CM = 120f
        const val MAX_HEIGHT_CM = 220f
        const val DEFAULT_HEIGHT_CM = 170f

        const val MIN_WEIGHT_KG = 30f
        const val MAX_WEIGHT_KG = 200f
        const val DEFAULT_WEIGHT_KG = 70f

        const val DEFAULT_DAYS_PER_WEEK = 3

        val VALID_SEX = setOf("male", "female", "other")
        val VALID_EXPERIENCE = setOf("beginner", "intermediate", "advanced")
        val VALID_GOALS = setOf("STRENGTH", "GENERAL_HEALTH", "HYPERTROPHY")
        val VALID_LOCATIONS = setOf("home", "gym")

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

        val WEEKDAY_LABEL_KEYS = listOf(
            "onboarding_weekday_sun",
            "onboarding_weekday_mon",
            "onboarding_weekday_tue",
            "onboarding_weekday_wed",
            "onboarding_weekday_thu",
            "onboarding_weekday_fri",
            "onboarding_weekday_sat",
        )

        /** Mon-first display order matching prototype `12-profile-onboarding.html`. */
        val WEEKDAY_DISPLAY_ORDER = listOf(1, 2, 3, 4, 5, 6, 0)

        val WEEKDAY_SHORT_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")
    }
}
