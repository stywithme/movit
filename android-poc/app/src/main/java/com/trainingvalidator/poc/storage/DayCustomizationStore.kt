package com.trainingvalidator.poc.storage

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import com.google.gson.reflect.TypeToken
import com.trainingvalidator.poc.training.models.ProgramWorkout
import com.trainingvalidator.poc.training.models.WorkoutLineItem
import com.trainingvalidator.poc.training.models.LocalizedText

/**
 * DayCustomizationStore � Offline-first local storage for day/workout customizations.
 *
 * Stores user modifications to planned workouts (reorder, rename, delete) and workout items
 * (add, remove, reorder, edit sets/reps/weight) persistently in SharedPreferences.
 *
 * Key format: "day_{programId}_{weekNumber}_{dayNumber}"
 *
 * Each key maps to a DayCustomization containing the full modified planned workout list.
 * This is the source of truth for the day view � if a customization exists,
 * it overrides the original program data.
 */
class DayCustomizationStore(context: Context) {

    companion object {
        private const val TAG = "DayCustomizationStore"
        private const val PREFS_NAME = "day_customization_store"
        private const val KEY_PREFIX = "day_"
    }

    /**
     * Represents a complete customization of a program day.
     * Contains the full modified planned workout list with all items.
     */
    data class DayCustomization(
        val programId: String = "",
        val weekNumber: Int = 0,
        val dayNumber: Int = 0,
        val plannedWorkouts: List<CustomizedPlannedWorkout> = emptyList(),
        val lastModifiedAt: Long = System.currentTimeMillis(),
        /** True when the user edited this day locally (vs server-only import). */
        val isUserModified: Boolean = false
    )

    /**
     * A planned workout with potential modifications.
     * Mirrors ProgramWorkout but is fully mutable.
     */
    data class CustomizedPlannedWorkout(
        val id: String = "",
        val name: LocalizedText = LocalizedText(),
        val sortOrder: Int = 0,
        val role: String = "MAIN",
        val estimatedDurationMin: Int? = null,
        val items: List<WorkoutLineItem> = emptyList(),
        val isDeleted: Boolean = false
    )

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson: Gson = GsonBuilder()
        .setStrictness(Strictness.LENIENT)
        .create()

    // ???????????????????????????????????????????????????????????
    // Read
    // ???????????????????????????????????????????????????????????

    /**
     * Get customization for a specific day, or null if no customization exists.
     */
    fun get(programId: String, weekNumber: Int, dayNumber: Int): DayCustomization? {
        val key = buildKey(programId, weekNumber, dayNumber)
        var json = prefs.getString(key, null)
        if (json == null) {
            Log.d(TAG, "get: NO DATA for key=$key (all keys: ${prefs.all.keys.filter { it.startsWith(KEY_PREFIX) }})")
            return null
        }
        // Migrate legacy persisted key "sessions" ? "plannedWorkouts"
        if (json.contains("\"sessions\"") && !json.contains("\"plannedWorkouts\"")) {
            json = json.replace("\"sessions\"", "\"plannedWorkouts\"")
        }
        return try {
            val result = gson.fromJson(json, DayCustomization::class.java)
            Log.d(TAG, "get: FOUND key=$key, workouts=${result?.plannedWorkouts?.size ?: 0}, json_length=${json.length}")
            result?.let { sanitizeDayCustomization(it) }
        } catch (e: Exception) {
            Log.e(TAG, "get: PARSE FAILED for key=$key, json_length=${json.length}", e)
            null
        }
    }

    /**
     * Get the effective planned workouts for a day.
     * Returns customized workouts if they exist, otherwise converts original program workouts.
     */
    fun getEffectivePlannedWorkouts(
        programId: String,
        weekNumber: Int,
        dayNumber: Int,
        originalWorkouts: List<ProgramWorkout>
    ): List<CustomizedPlannedWorkout> {
        val key = buildKey(programId, weekNumber, dayNumber)
        val customization = get(programId, weekNumber, dayNumber)
        if (customization != null) {
            val result = customization.plannedWorkouts
                .filter { !it.isDeleted }
                .mapIndexed { wIdx, workout ->
                    workout.copy(
                        sortOrder = wIdx,
                        items = workout.items.mapIndexed { iIdx, item ->
                            item.copy(sortOrder = iIdx)
                        }
                    )
                }
            Log.d(TAG, "getEffectivePlannedWorkouts: CUSTOMIZED key=$key, workouts=${result.size}")
            return result
        }
        Log.d(TAG, "getEffectivePlannedWorkouts: NO CUSTOMIZATION for key=$key, using ${originalWorkouts.size} original workouts")
        return originalWorkouts.map { workout ->
            CustomizedPlannedWorkout(
                id = workout.id,
                name = workout.name,
                sortOrder = workout.sortOrder,
                role = workout.role.ifBlank { "MAIN" },
                estimatedDurationMin = workout.estimatedDurationMin,
                items = workout.items.sortedBy { it.sortOrder }
            )
        }.sortedBy { it.sortOrder }
    }

    // ???????????????????????????????????????????????????????????
    // Write
    // ???????????????????????????????????????????????????????????

    /**
     * Save the full day customization.
     */
    fun save(customization: DayCustomization) {
        val key = buildKey(customization.programId, customization.weekNumber, customization.dayNumber)
        val json = gson.toJson(customization)
        prefs.edit().putString(key, json).apply()
        Log.d(TAG, "Saved customization: $key (${customization.plannedWorkouts.size} workouts)")
    }

    /**
     * Save planned workouts for a specific day.
     */
    fun savePlannedWorkouts(
        programId: String,
        weekNumber: Int,
        dayNumber: Int,
        plannedWorkouts: List<CustomizedPlannedWorkout>
    ) {
        val key = buildKey(programId, weekNumber, dayNumber)
        Log.d(TAG, "savePlannedWorkouts: key=$key, workouts=${plannedWorkouts.size}")
        plannedWorkouts.forEach { w ->
            Log.d(TAG, "  saving workout '${w.name.en}' sortOrder=${w.sortOrder}, items=${w.items.size}")
        }
        save(
            DayCustomization(
                programId = programId,
                weekNumber = weekNumber,
                dayNumber = dayNumber,
                plannedWorkouts = plannedWorkouts,
                isUserModified = true
            )
        )
    }

    /**
     * Update a specific planned workout's items.
     */
    fun updatePlannedWorkoutItems(
        programId: String,
        weekNumber: Int,
        dayNumber: Int,
        workoutId: String,
        items: List<WorkoutLineItem>
    ) {
        val current = get(programId, weekNumber, dayNumber) ?: return
        val updatedWorkouts = current.plannedWorkouts.map { workout ->
            if (workout.id == workoutId) {
                workout.copy(items = items)
            } else {
                workout
            }
        }
        save(current.copy(plannedWorkouts = updatedWorkouts, lastModifiedAt = System.currentTimeMillis(), isUserModified = true))
    }

    /**
     * Rename a planned workout.
     */
    fun renamePlannedWorkout(
        programId: String,
        weekNumber: Int,
        dayNumber: Int,
        workoutId: String,
        newName: LocalizedText
    ) {
        val current = get(programId, weekNumber, dayNumber) ?: return
        val updatedWorkouts = current.plannedWorkouts.map { workout ->
            if (workout.id == workoutId) workout.copy(name = newName) else workout
        }
        save(current.copy(plannedWorkouts = updatedWorkouts, lastModifiedAt = System.currentTimeMillis(), isUserModified = true))
    }

    /**
     * Soft-delete a planned workout (mark as deleted).
     */
    fun deletePlannedWorkout(
        programId: String,
        weekNumber: Int,
        dayNumber: Int,
        workoutId: String
    ) {
        val current = get(programId, weekNumber, dayNumber) ?: return
        val updatedWorkouts = current.plannedWorkouts.map { workout ->
            if (workout.id == workoutId) workout.copy(isDeleted = true) else workout
        }
        save(current.copy(plannedWorkouts = updatedWorkouts, lastModifiedAt = System.currentTimeMillis(), isUserModified = true))
    }

    /**
     * Reorder planned workouts.
     */
    fun reorderPlannedWorkouts(
        programId: String,
        weekNumber: Int,
        dayNumber: Int,
        plannedWorkoutIds: List<String>
    ) {
        val current = get(programId, weekNumber, dayNumber) ?: return
        val workoutMap = current.plannedWorkouts.associateBy { it.id }
        val reordered = plannedWorkoutIds.mapIndexedNotNull { index, id ->
            workoutMap[id]?.copy(sortOrder = index)
        }
        val remaining = current.plannedWorkouts.filter { it.id !in plannedWorkoutIds }
        save(
            current.copy(
                plannedWorkouts = reordered + remaining,
                lastModifiedAt = System.currentTimeMillis(),
                isUserModified = true
            )
        )
    }

    // ???????????????????????????????????????????????????????????
    // Delete
    // ???????????????????????????????????????????????????????????

    fun clear(programId: String, weekNumber: Int, dayNumber: Int) {
        val key = buildKey(programId, weekNumber, dayNumber)
        prefs.edit().remove(key).apply()
        Log.d(TAG, "Cleared customization: $key")
    }

    fun clearProgram(programId: String) {
        val editor = prefs.edit()
        prefs.all.keys
            .filter { it.startsWith("${KEY_PREFIX}${programId}_") }
            .forEach { editor.remove(it) }
        editor.apply()
        Log.d(TAG, "Cleared all customizations for program: $programId")
    }

    fun hasCustomization(programId: String, weekNumber: Int, dayNumber: Int): Boolean {
        return prefs.contains(buildKey(programId, weekNumber, dayNumber))
    }

    // ???????????????????????????????????????????????????????????
    // Hydrate from Backend (sync response)
    // ???????????????????????????????????????????????????????????

    @Suppress("UNCHECKED_CAST")
    fun hydrateFromBackend(
        programId: String,
        customizations: Map<String, Any>?,
        serverCustomizationsUpdatedAt: String? = null
    ) {
        if (customizations.isNullOrEmpty()) {
            Log.d(TAG, "hydrateFromBackend: no customizations for programId=$programId")
            return
        }

        val serverMs = serverCustomizationsUpdatedAt?.let { iso -> parseIsoToEpochMs(iso) }

        var imported = 0
        for ((dayKey, value) in customizations) {
            val parts = dayKey.removePrefix("day_").split("_")
            if (parts.size != 2) continue
            val weekNumber = parts[0].toIntOrNull() ?: continue
            val dayNumber = parts[1].toIntOrNull() ?: continue

            if (hasCustomization(programId, weekNumber, dayNumber)) {
                val existing = get(programId, weekNumber, dayNumber) ?: continue
                if (existing.isUserModified) {
                    if (serverMs != null && serverMs > existing.lastModifiedAt) {
                        Log.w(TAG, "hydrateFromBackend: CONFLICT $dayKey � local user edits kept; server is newer")
                    } else {
                        Log.d(TAG, "hydrateFromBackend: SKIP $dayKey � local user customization exists")
                    }
                    continue
                }
                if (serverMs != null && serverMs <= existing.lastModifiedAt) {
                    Log.d(TAG, "hydrateFromBackend: SKIP $dayKey � local copy same or newer than server marker")
                    continue
                }
            }

            try {
                var workoutsJson = gson.toJson(value)
                if (workoutsJson.contains("\"sessions\"") && !workoutsJson.contains("\"plannedWorkouts\"")) {
                    workoutsJson = workoutsJson.replace("\"sessions\"", "\"plannedWorkouts\"")
                }
                val workoutsType = object : TypeToken<List<CustomizedPlannedWorkout>>() {}.type
                val plannedWorkouts: List<CustomizedPlannedWorkout> = gson.fromJson(workoutsJson, workoutsType)
                val sanitizedWorkouts = sanitizePlannedWorkouts(plannedWorkouts)

                save(
                    DayCustomization(
                        programId = programId,
                        weekNumber = weekNumber,
                        dayNumber = dayNumber,
                        plannedWorkouts = sanitizedWorkouts,
                        lastModifiedAt = serverMs ?: System.currentTimeMillis(),
                        isUserModified = false
                    )
                )
                imported++
                Log.d(TAG, "hydrateFromBackend: IMPORTED $dayKey (${sanitizedWorkouts.size} workouts)")
            } catch (e: Exception) {
                Log.w(TAG, "hydrateFromBackend: FAILED to parse $dayKey", e)
            }
        }
        Log.d(TAG, "hydrateFromBackend: done � imported $imported day(s) for programId=$programId")
    }

    // ???????????????????????????????????????????????????????????
    // Helpers
    // ???????????????????????????????????????????????????????????

    private fun WorkoutLineItem.sanitizeGsonLists(): WorkoutLineItem = this

    private fun sanitizePlannedWorkouts(workouts: List<CustomizedPlannedWorkout>): List<CustomizedPlannedWorkout> =
        workouts.map { workout ->
            workout.copy(items = workout.items.map { it.sanitizeGsonLists() })
        }

    private fun sanitizeDayCustomization(d: DayCustomization): DayCustomization =
        d.copy(plannedWorkouts = sanitizePlannedWorkouts(d.plannedWorkouts))

    private fun buildKey(programId: String, weekNumber: Int, dayNumber: Int): String {
        return "${KEY_PREFIX}${programId}_${weekNumber}_${dayNumber}"
    }

    private fun parseIsoToEpochMs(iso: String): Long? {
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
            "yyyy-MM-dd"
        )
        for (format in formats) {
            try {
                val sdf = java.text.SimpleDateFormat(format, java.util.Locale.US)
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                return sdf.parse(iso)?.time
            } catch (_: Exception) {
            }
        }
        return null
    }
}
