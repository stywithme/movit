package com.movit.resources.strings

import com.movit.resources.localizedString

data class LevelStrings(
    val language: String,
    val phaseCompleted: String,
    val phaseInProgress: String,
    val phaseUpcoming: String,
    val unlocksAfterProgression: String,
    val reassessmentSoon: String,
    val programFallback: String,
) {
    suspend fun phaseWeeks(programName: String, weeks: Int): String =
        localizedString(language, "level_phase_weeks", programName, weeks)

    suspend fun phaseWeekProgress(week: Int, totalWeeks: Int): String =
        localizedString(language, "level_phase_week_progress", week, totalWeeks)

    suspend fun reassessmentNext(week: Int): String =
        localizedString(language, "level_reassessment_next", week)

    suspend fun reassessmentScheduled(date: String): String =
        localizedString(language, "level_reassessment_scheduled", date)

    suspend fun domainName(domain: String): String {
        val key = when (domain.lowercase()) {
            "mobility" -> "level_domain_mobility"
            "stability" -> "level_domain_stability"
            "strength" -> "level_domain_strength"
            "control" -> "level_domain_control"
            "symmetry" -> "level_domain_symmetry"
            "safety" -> "level_domain_safety"
            else -> null
        }
        return key?.let { localizedString(language, it) }
            ?: domain.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    companion object {
        suspend fun load(language: String): LevelStrings = LevelStrings(
            language = language,
            phaseCompleted = localizedString(language, "level_phase_completed"),
            phaseInProgress = localizedString(language, "level_phase_in_progress"),
            phaseUpcoming = localizedString(language, "level_phase_upcoming"),
            unlocksAfterProgression = localizedString(language, "level_unlocks_after_progression"),
            reassessmentSoon = localizedString(language, "level_reassessment_soon"),
            programFallback = localizedString(language, "train_program_fallback"),
        )
    }
}
