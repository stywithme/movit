package com.trainingvalidator.poc.ui.utils

import android.content.Context
import com.trainingvalidator.poc.R

/**
 * Helper object for translating muscle, equipment, and camera position codes
 * to localized strings based on the current app language.
 */
object LocalizationHelper {

    /**
     * Get localized muscle name from code
     */
    fun getMuscleDisplayName(context: Context, muscleCode: String): String {
        val resId = when (muscleCode.lowercase()) {
            "quadriceps", "quads" -> R.string.muscle_quadriceps
            "glutes", "gluteus" -> R.string.muscle_glutes
            "hamstrings" -> R.string.muscle_hamstrings
            "calves", "calf" -> R.string.muscle_calves
            "biceps" -> R.string.muscle_biceps
            "triceps" -> R.string.muscle_triceps
            "shoulders", "deltoids", "delts" -> R.string.muscle_shoulders
            "chest", "pectorals", "pecs" -> R.string.muscle_chest
            "back" -> R.string.muscle_back
            "core" -> R.string.muscle_core
            "abs", "abdominals" -> R.string.muscle_abs
            "forearms" -> R.string.muscle_forearms
            "lats", "latissimus" -> R.string.muscle_lats
            "lower_back", "lower back", "lowerback" -> R.string.muscle_lower_back
            "hip_flexors", "hip flexors", "hipflexors" -> R.string.muscle_hip_flexors
            "obliques" -> R.string.muscle_obliques
            else -> null
        }
        
        return if (resId != null) {
            context.getString(resId)
        } else {
            // Fallback: capitalize the code
            muscleCode.replace("_", " ").split(" ")
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        }
    }

    /**
     * Get localized equipment name from code
     */
    fun getEquipmentDisplayName(context: Context, equipmentCode: String): String {
        val resId = when (equipmentCode.lowercase()) {
            "bodyweight", "body_weight", "none" -> R.string.equipment_bodyweight
            "barbell" -> R.string.equipment_barbell
            "dumbbell", "dumbbells" -> R.string.equipment_dumbbell
            "kettlebell" -> R.string.equipment_kettlebell
            "resistance_band", "resistance band", "band", "bands" -> R.string.equipment_resistance_band
            "mat", "yoga_mat" -> R.string.equipment_mat
            "bench" -> R.string.equipment_bench
            "pull_up_bar", "pullup_bar", "pull up bar" -> R.string.equipment_pull_up_bar
            "cable_machine", "cable", "cables" -> R.string.equipment_cable_machine
            else -> null
        }
        
        return if (resId != null) {
            context.getString(resId)
        } else {
            // Fallback: capitalize the code
            equipmentCode.replace("_", " ").split(" ")
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        }
    }

    /**
     * Get localized camera position name from code
     */
    fun getCameraPositionDisplayName(context: Context, positionCode: String): String {
        val resId = when (positionCode.lowercase()) {
            "side_view", "side", "sideview" -> R.string.camera_side_view
            "front_view", "front", "frontview" -> R.string.camera_front_view
            "back_view", "back", "backview", "rear" -> R.string.camera_back_view
            "angle_45", "45_angle", "diagonal" -> R.string.camera_angle_45
            else -> null
        }
        
        return if (resId != null) {
            context.getString(resId)
        } else {
            // Fallback: capitalize the code
            positionCode.replace("_", " ").split(" ")
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        }
    }

    /**
     * Get list of localized muscle names
     */
    fun getMusclesDisplayText(context: Context, muscles: List<String>, separator: String = " & "): String {
        return muscles.joinToString(separator) { getMuscleDisplayName(context, it) }
    }

    /**
     * Get list of localized equipment names
     */
    fun getEquipmentDisplayText(context: Context, equipment: List<String>, separator: String = ", "): String {
        return if (equipment.isEmpty()) {
            context.getString(R.string.no_equipment)
        } else {
            equipment.joinToString(separator) { getEquipmentDisplayName(context, it) }
        }
    }
}
