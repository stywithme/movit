package com.trainingvalidator.poc.ui.onboarding

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Collected answers from the profile onboarding flow.
 *
 * Always stored in metric units (kg, cm); the chosen display unit is UI-only state
 * kept in [OnboardingViewModel]. Maps to the backend `TrainingProfile` model via [toPayload].
 */
data class OnboardingData(
    val ageYears: Int? = null,
    /** "male" | "female" — see genderValueCodeFromProfile on backend. */
    val biologicalSex: String? = null,
    val heightCm: Float? = null,
    val weightKg: Float? = null,
    /** "beginner" | "intermediate" | "advanced". */
    val resistanceExperience: String? = null,
    /** Target sessions per week (1..7); drives how many weekdays to pick in the weekday step. */
    val targetDaysPerWeek: Int? = null,
    /** STRENGTH | HYPERTROPHY | POWER | GENERAL_HEALTH (User.trainingGoal). */
    val trainingGoal: String? = null,
    /** 0=Sunday … 6=Saturday. */
    val trainingWeekdays: Set<Int> = emptySet(),
    /** "home" | "gym". */
    val trainingLocation: String? = null,
    val availableEquipment: Set<String> = emptySet(),
    val healthDisclaimerAccepted: Boolean = false,
) {

    /** Approx birth date (yyyy-MM-dd) from age, anchored to Jan 1 of birth year for stable age buckets. */
    fun dateOfBirthIso(): String? {
        val age = ageYears ?: return null
        val cal = Calendar.getInstance()
        cal.add(Calendar.YEAR, -age)
        cal.set(Calendar.MONTH, Calendar.JANUARY)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    /** Coarse experience-in-months derived from resistance experience tier. */
    private fun experienceMonths(): Int? = when (resistanceExperience) {
        "beginner" -> 0
        "intermediate" -> 12
        "advanced" -> 36
        else -> null
    }

    /**
     * Effective equipment list sent to the backend.
     * Gym → full standard equipment set; Home → user picks (bodyweight always included).
     */
    private fun effectiveEquipment(): List<String> {
        return if (trainingLocation == "gym") {
            GYM_EQUIPMENT
        } else {
            (availableEquipment + "bodyweight").toList()
        }
    }

    /** Body of the PUT /api/mobile/training-profile request. */
    fun toPayload(): Map<String, Any?> {
        val weekdays = trainingWeekdays.sorted()
        val payload = mutableMapOf<String, Any?>(
            "dateOfBirth" to dateOfBirthIso(),
            "biologicalSex" to biologicalSex,
            "heightCm" to heightCm,
            "weightKg" to weightKg,
            "resistanceExperience" to resistanceExperience,
            "trainingExperienceMonths" to experienceMonths(),
            "trainingWeekdays" to weekdays,
            "availableDaysPerWeek" to (weekdays.size.takeIf { it > 0 } ?: targetDaysPerWeek),
            "trainingLocation" to trainingLocation,
            "availableEquipment" to effectiveEquipment(),
            "healthDisclaimerAccepted" to healthDisclaimerAccepted,
        )
        trainingGoal?.let { payload["trainingGoal"] = it }
        return payload
    }

    companion object {
        val GYM_EQUIPMENT = listOf(
            "bodyweight", "dumbbell", "barbell", "kettlebell",
            "cable", "machine", "bench", "bands", "pull_up_bar", "mat",
        )

        /** Equipment offered for the home option (codes match backend equipmentValueCodeFromProfileString). */
        val HOME_EQUIPMENT = listOf(
            "dumbbell", "bands", "kettlebell", "pull_up_bar", "bench", "mat",
        )
    }
}
