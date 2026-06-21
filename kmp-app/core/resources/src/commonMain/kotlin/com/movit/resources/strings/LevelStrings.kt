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

    suspend fun regionName(region: String): String {
        val key = when (region.lowercase()) {
            "hips", "hip" -> "assessment_region_hips"
            "shoulders", "shoulder" -> "assessment_region_shoulders"
            "knees", "knee" -> "assessment_region_knees"
            "spine", "core", "lower_back", "back" -> "assessment_region_spine"
            "balance" -> "assessment_region_balance"
            else -> null
        }
        return key?.let { localizedString(language, it) }
            ?: region.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    suspend fun limitingFactorName(type: String, code: String): String {
        val normalized = code.lowercase().replace('_', ' ')
        val key = when {
            type.equals("domain", ignoreCase = true) && code.contains("mobility", ignoreCase = true) ->
                "level_domain_mobility"
            type.equals("domain", ignoreCase = true) && code.contains("control", ignoreCase = true) ->
                "level_domain_control"
            type.equals("domain", ignoreCase = true) && code.contains("symmetry", ignoreCase = true) ->
                "level_domain_symmetry"
            type.equals("domain", ignoreCase = true) && code.contains("safety", ignoreCase = true) ->
                "level_domain_safety"
            else -> null
        }
        return key?.let { localizedString(language, it) }
            ?: normalized.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    suspend fun fetchError(): String = localizedString(language, "level_fetch_error")

    suspend fun noProfileMessage(): String = localizedString(language, "level_no_profile_message")

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
