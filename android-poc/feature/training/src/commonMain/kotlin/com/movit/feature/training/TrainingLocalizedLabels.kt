package com.movit.feature.training

import androidx.compose.runtime.Composable
import com.movit.core.training.engine.Phase
import com.movit.resources.movitText

fun Phase.toDisplayLabel(): String = when (this) {
    Phase.IDLE -> "training_phase_ready"
    Phase.START -> "training_phase_start"
    Phase.DOWN -> "training_phase_down"
    Phase.BOTTOM -> "training_phase_bottom"
    Phase.UP -> "training_phase_up"
    Phase.COUNT -> "training_phase_rep"
}

fun setupPhaseLabelKey(phaseName: String): String = when (phaseName.uppercase()) {
    "REGION" -> "training_setup_phase_region"
    "POSTURE" -> "training_setup_phase_posture"
    "DIRECTION" -> "training_setup_phase_direction"
    "ANGLES" -> "training_setup_phase_angles"
    else -> "training_setup_phase_region"
}

private val legacyPhaseToKey = mapOf(
    "Ready" to "training_phase_ready",
    "Start" to "training_phase_start",
    "Down" to "training_phase_down",
    "Bottom" to "training_phase_bottom",
    "Up" to "training_phase_up",
    "Rep" to "training_phase_rep",
)

@Composable
fun localizedTrainingPhase(labelOrKey: String): String {
    val key = when {
        labelOrKey.startsWith("training_phase_") -> labelOrKey
        else -> legacyPhaseToKey[labelOrKey] ?: labelOrKey
    }
    return if (key.startsWith("training_phase_")) movitText(key) else labelOrKey
}

@Composable
fun localizedSetupPhase(phaseName: String): String = movitText(setupPhaseLabelKey(phaseName))
