package com.movit.feature.library

import androidx.compose.runtime.Composable
import com.movit.resources.movitText

@Composable
fun sessionSetsLabel(sets: Int, reps: Int?, durationSeconds: Int?): String = when {
    durationSeconds != null && reps == null ->
        movitText("session_sets_duration", sets, durationSeconds)
    reps != null -> movitText("session_sets_reps", sets, reps)
    else -> movitText("session_sets_only", sets)
}

@Composable
fun sessionRestLabel(restSeconds: Int): String =
    if (restSeconds <= 0) "" else movitText("session_rest_between", restSeconds)

@Composable
fun sessionWeightLabel(weightKg: Float?): String? {
    val value = weightKg?.takeIf { it > 0f } ?: return null
    val text = if (value == value.toInt().toFloat()) value.toInt().toString() else value.toString()
    return movitText("session_weight_kg", text)
}

@Composable
fun sessionRestBlockLabel(durationLabel: String): String =
    movitText("session_rest_block", durationLabel)
