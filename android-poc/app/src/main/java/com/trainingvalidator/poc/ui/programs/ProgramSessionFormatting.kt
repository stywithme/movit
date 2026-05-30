package com.trainingvalidator.poc.ui.programs

import com.trainingvalidator.poc.training.models.LocalizedText
import com.trainingvalidator.poc.training.models.ProgramSession
import com.trainingvalidator.poc.training.models.ProgramSessionItem
import java.util.Locale

/**
 * Pure, stateless helpers extracted from [ProgramSessionActivity] as the first slice
 * of decomposing that screen. No Android Context / Activity state dependency.
 *
 * Kept in the same package as top-level `internal` functions, so existing call sites
 * inside ProgramSessionActivity resolve unchanged (no import needed, no behavior change).
 */

/** Collapses raw exercise role strings into a small set of section keys. */
internal fun normalizeRoleKey(role: String?): String =
    when (role?.uppercase(Locale.US)) {
        "WARMUP", "ACTIVATION" -> "WARMUP"
        "MAIN", "ACCESSORY", "CORRECTIVE" -> "MAIN"
        "COOLDOWN" -> "COOLDOWN"
        else -> "OTHER"
    }

/** Prefers the API-provided localized name, falling back to the bundled one. */
internal fun localizedFromEffectiveName(
    api: Map<String, String>?,
    fallback: LocalizedText?
): LocalizedText {
    if (api != null && (api["en"]?.isNotBlank() == true || api["ar"]?.isNotBlank() == true)) {
        return LocalizedText(ar = api["ar"] ?: "", en = api["en"] ?: "")
    }
    return fallback ?: LocalizedText()
}

/** Builds a LocalizedText from an API notes map, or null when empty. */
internal fun notesFromApi(notes: Map<String, String>?): LocalizedText? {
    if (notes == null || notes.isEmpty()) return null
    return LocalizedText(ar = notes["ar"] ?: "", en = notes["en"] ?: "")
}

/**
 * Rough session duration estimate in minutes (reps/hold/rest aware).
 * Used by train UI and anywhere a [ProgramSession] is shown without backend duration.
 */
internal fun estimateSessionDuration(session: ProgramSession): Int {
    var totalSeconds = 0
    session.items.forEach { item ->
        if (item.type == "rest") {
            totalSeconds += ((item.restDurationMs ?: 0L) / 1000).toInt()
        } else {
            val sets = item.sets ?: 1
            val repsTime = (item.targetReps ?: 0) * 4
            val holdTime = item.targetDuration ?: 0
            val exerciseTime = sets * (repsTime + holdTime)
            val restBetweenSets = sets.coerceAtLeast(1) - 1
            totalSeconds += exerciseTime + restBetweenSets * ((item.restBetweenSetsMs ?: 30000L) / 1000).toInt()
        }
    }
    return (totalSeconds / 60).coerceAtLeast(1)
}

/** Rough session duration estimate in minutes from item list (simpler formula for timeline screens). */
internal fun estimateSessionDuration(items: List<ProgramSessionItem>): Int {
    var totalSeconds = 0
    items.forEach { item ->
        if (item.type == "exercise") {
            val sets = item.sets ?: 1
            val perSet = (item.targetDuration ?: 30) + ((item.restBetweenSetsMs ?: 30000L) / 1000).toInt()
            totalSeconds += sets * perSet
        } else if (item.type == "rest") {
            totalSeconds += ((item.restDurationMs ?: 0L) / 1000).toInt()
        }
    }
    return (totalSeconds / 60).coerceAtLeast(1)
}
