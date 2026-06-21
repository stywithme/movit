package com.movit.resources.strings

import com.movit.resources.localizedString

data class SessionStrings(
    val language: String,
    val workoutFallback: String,
    val exerciseFallback: String,
    val bestBadge: String,
    val phaseWarmup: String,
    val phaseMain: String,
    val phaseCooldown: String,
    val phaseOther: String,
    val bridgeNotInstalled: String,
    val workoutNotFound: String,
    val dataNotInstalled: String,
    val noEnrollment: String,
    val workoutNotInPlan: String,
) {
    suspend fun weekDayLabel(week: Int, day: Int): String =
        localizedString(language, "session_week_day", week, day)

    fun phaseTitle(role: String): String = when (role.uppercase()) {
        "WARMUP", "ACTIVATION" -> phaseWarmup
        "MAIN", "ACCESSORY", "CORRECTIVE" -> phaseMain
        "COOLDOWN" -> phaseCooldown
        else -> phaseOther
    }

    companion object {
        suspend fun load(language: String): SessionStrings = SessionStrings(
            language = language,
            workoutFallback = localizedString(language, "session_workout_fallback"),
            exerciseFallback = localizedString(language, "session_exercise_fallback"),
            bestBadge = localizedString(language, "session_best_badge"),
            phaseWarmup = localizedString(language, "session_phase_warmup"),
            phaseMain = localizedString(language, "session_phase_main"),
            phaseCooldown = localizedString(language, "session_phase_cooldown"),
            phaseOther = localizedString(language, "session_phase_other"),
            bridgeNotInstalled = localizedString(language, "session_bridge_not_installed"),
            workoutNotFound = localizedString(language, "session_workout_not_found"),
            dataNotInstalled = localizedString(language, "session_data_not_installed"),
            noEnrollment = localizedString(language, "session_no_enrollment"),
            workoutNotInPlan = localizedString(language, "session_workout_not_in_plan"),
        )
    }
}
