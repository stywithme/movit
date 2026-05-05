package com.trainingvalidator.poc.storage

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import com.google.gson.reflect.TypeToken
import com.trainingvalidator.poc.training.models.ProgramSession
import com.trainingvalidator.poc.training.models.ProgramSessionItem
import com.trainingvalidator.poc.training.models.LocalizedText

/**
 * DayCustomizationStore — Offline-first local storage for day/session customizations.
 *
 * Stores user modifications to sessions (reorder, rename, delete) and session items
 * (add, remove, reorder, edit sets/reps/weight) persistently in SharedPreferences.
 *
 * Key format: "day_{programId}_{weekNumber}_{dayNumber}"
 *
 * Each key maps to a DayCustomization containing the full modified sessions list.
 * This is the source of truth for the day view — if a customization exists,
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
     * Contains the full modified session list with all items.
     */
    data class DayCustomization(
        val programId: String = "",
        val weekNumber: Int = 0,
        val dayNumber: Int = 0,
        val sessions: List<CustomizedSession> = emptyList(),
        val lastModifiedAt: Long = System.currentTimeMillis(),
        /** True when the user edited this day locally (vs server-only import). */
        val isUserModified: Boolean = false
    )

    /**
     * A session with potential modifications.
     * Mirrors ProgramSession but is fully mutable.
     */
    data class CustomizedSession(
        val id: String = "",
        val name: LocalizedText = LocalizedText(),
        val sortOrder: Int = 0,
        val role: String = "MAIN",
        val estimatedDurationMin: Int? = null,
        val items: List<ProgramSessionItem> = emptyList(),
        val isDeleted: Boolean = false
    )

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson: Gson = GsonBuilder()
        .setStrictness(Strictness.LENIENT)
        .create()

    // ═══════════════════════════════════════════════════════════
    // Read
    // ═══════════════════════════════════════════════════════════

    /**
     * Get customization for a specific day, or null if no customization exists.
     */
    fun get(programId: String, weekNumber: Int, dayNumber: Int): DayCustomization? {
        val key = buildKey(programId, weekNumber, dayNumber)
        val json = prefs.getString(key, null)
        if (json == null) {
            Log.d(TAG, "get: NO DATA for key=$key (all keys: ${prefs.all.keys.filter { it.startsWith(KEY_PREFIX) }})")
            return null
        }
        return try {
            val result = gson.fromJson(json, DayCustomization::class.java)
            Log.d(TAG, "get: FOUND key=$key, sessions=${result?.sessions?.size ?: 0}, json_length=${json.length}")
            result?.let { sanitizeDayCustomization(it) }
        } catch (e: Exception) {
            Log.e(TAG, "get: PARSE FAILED for key=$key, json_length=${json.length}", e)
            null
        }
    }

    /**
     * Get the effective sessions for a day.
     * Returns customized sessions if they exist, otherwise converts original sessions.
     */
    fun getEffectiveSessions(
        programId: String,
        weekNumber: Int,
        dayNumber: Int,
        originalSessions: List<ProgramSession>
    ): List<CustomizedSession> {
        val key = buildKey(programId, weekNumber, dayNumber)
        val customization = get(programId, weekNumber, dayNumber)
        if (customization != null) {
            // Normalize sortOrder on read to fix any stale values from older saves.
            // The array order IS the source of truth — sortOrder must match array index.
            val result = customization.sessions
                .filter { !it.isDeleted }
                .mapIndexed { sIdx, session ->
                    session.copy(
                        sortOrder = sIdx,
                        items = session.items.mapIndexed { iIdx, item ->
                            item.copy(sortOrder = iIdx)
                        }
                    )
                }
            Log.d(TAG, "getEffectiveSessions: CUSTOMIZED key=$key, sessions=${result.size}")
            return result
        }
        Log.d(TAG, "getEffectiveSessions: NO CUSTOMIZATION for key=$key, using ${originalSessions.size} original sessions")
        // Convert original sessions to customized format
        return originalSessions.map { session ->
            CustomizedSession(
                id = session.id,
                name = session.name,
                sortOrder = session.sortOrder,
                role = session.role.ifBlank { "MAIN" },
                estimatedDurationMin = session.estimatedDurationMin,
                items = session.items.sortedBy { it.sortOrder }
            )
        }.sortedBy { it.sortOrder }
    }

    // ═══════════════════════════════════════════════════════════
    // Write
    // ═══════════════════════════════════════════════════════════

    /**
     * Save the full day customization.
     */
    fun save(customization: DayCustomization) {
        val key = buildKey(customization.programId, customization.weekNumber, customization.dayNumber)
        val json = gson.toJson(customization)
        prefs.edit().putString(key, json).apply()
        Log.d(TAG, "Saved customization: $key (${customization.sessions.size} sessions)")
    }

    /**
     * Save sessions for a specific day.
     * Convenience method that wraps sessions in a DayCustomization.
     */
    fun saveSessions(
        programId: String,
        weekNumber: Int,
        dayNumber: Int,
        sessions: List<CustomizedSession>
    ) {
        val key = buildKey(programId, weekNumber, dayNumber)
        Log.d(TAG, "saveSessions: key=$key, sessions=${sessions.size}")
        sessions.forEach { s ->
            Log.d(TAG, "  saving session '${s.name.en}' sortOrder=${s.sortOrder}, items=${s.items.size}")
        }
        save(DayCustomization(
            programId = programId,
            weekNumber = weekNumber,
            dayNumber = dayNumber,
            sessions = sessions,
            isUserModified = true
        ))
    }

    /**
     * Update a specific session's items.
     */
    fun updateSessionItems(
        programId: String,
        weekNumber: Int,
        dayNumber: Int,
        sessionId: String,
        items: List<ProgramSessionItem>
    ) {
        val current = get(programId, weekNumber, dayNumber) ?: return
        val updatedSessions = current.sessions.map { session ->
            if (session.id == sessionId) {
                session.copy(items = items)
            } else {
                session
            }
        }
        save(current.copy(sessions = updatedSessions, lastModifiedAt = System.currentTimeMillis(), isUserModified = true))
    }

    /**
     * Rename a session.
     */
    fun renameSession(
        programId: String,
        weekNumber: Int,
        dayNumber: Int,
        sessionId: String,
        newName: LocalizedText
    ) {
        val current = get(programId, weekNumber, dayNumber) ?: return
        val updatedSessions = current.sessions.map { session ->
            if (session.id == sessionId) session.copy(name = newName) else session
        }
        save(current.copy(sessions = updatedSessions, lastModifiedAt = System.currentTimeMillis(), isUserModified = true))
    }

    /**
     * Soft-delete a session (mark as deleted).
     */
    fun deleteSession(
        programId: String,
        weekNumber: Int,
        dayNumber: Int,
        sessionId: String
    ) {
        val current = get(programId, weekNumber, dayNumber) ?: return
        val updatedSessions = current.sessions.map { session ->
            if (session.id == sessionId) session.copy(isDeleted = true) else session
        }
        save(current.copy(sessions = updatedSessions, lastModifiedAt = System.currentTimeMillis(), isUserModified = true))
    }

    /**
     * Reorder sessions.
     */
    fun reorderSessions(
        programId: String,
        weekNumber: Int,
        dayNumber: Int,
        sessionIds: List<String>
    ) {
        val current = get(programId, weekNumber, dayNumber) ?: return
        val sessionMap = current.sessions.associateBy { it.id }
        val reordered = sessionIds.mapIndexedNotNull { index, id ->
            sessionMap[id]?.copy(sortOrder = index)
        }
        // Add any sessions not in the reorder list at the end
        val remaining = current.sessions.filter { it.id !in sessionIds }
        save(current.copy(
            sessions = reordered + remaining,
            lastModifiedAt = System.currentTimeMillis(),
            isUserModified = true
        ))
    }

    // ═══════════════════════════════════════════════════════════
    // Delete
    // ═══════════════════════════════════════════════════════════

    /**
     * Remove customization for a specific day (revert to original).
     */
    fun clear(programId: String, weekNumber: Int, dayNumber: Int) {
        val key = buildKey(programId, weekNumber, dayNumber)
        prefs.edit().remove(key).apply()
        Log.d(TAG, "Cleared customization: $key")
    }

    /**
     * Remove all customizations for a program.
     */
    fun clearProgram(programId: String) {
        val editor = prefs.edit()
        prefs.all.keys
            .filter { it.startsWith("${KEY_PREFIX}${programId}_") }
            .forEach { editor.remove(it) }
        editor.apply()
        Log.d(TAG, "Cleared all customizations for program: $programId")
    }

    /**
     * Check if a day has been customized.
     */
    fun hasCustomization(programId: String, weekNumber: Int, dayNumber: Int): Boolean {
        return prefs.contains(buildKey(programId, weekNumber, dayNumber))
    }

    // ═══════════════════════════════════════════════════════════
    // Hydrate from Backend (sync response)
    // ═══════════════════════════════════════════════════════════

    /**
     * Populate local DayCustomizationStore from backend customizations
     * (received via sync in UserProgramExport.customizations).
     *
     * Called after sync to ensure local store matches backend.
     * Only imports keys that don't already exist locally (local edits take priority).
     *
     * Expected format from backend:
     *   { "day_1_1": [ { id, name, sortOrder, isDeleted, items: [...] }, ... ], ... }
     */
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
            // Parse key format: "day_{weekNumber}_{dayNumber}"
            val parts = dayKey.removePrefix("day_").split("_")
            if (parts.size != 2) continue
            val weekNumber = parts[0].toIntOrNull() ?: continue
            val dayNumber = parts[1].toIntOrNull() ?: continue

            if (hasCustomization(programId, weekNumber, dayNumber)) {
                val existing = get(programId, weekNumber, dayNumber) ?: continue
                if (existing.isUserModified) {
                    if (serverMs != null && serverMs > existing.lastModifiedAt) {
                        Log.w(TAG, "hydrateFromBackend: CONFLICT $dayKey — local user edits kept; server is newer")
                    } else {
                        Log.d(TAG, "hydrateFromBackend: SKIP $dayKey — local user customization exists")
                    }
                    continue
                }
                if (serverMs != null && serverMs <= existing.lastModifiedAt) {
                    Log.d(TAG, "hydrateFromBackend: SKIP $dayKey — local copy same or newer than server marker")
                    continue
                }
            }

            // Parse sessions from backend format
            try {
                val sessionsJson = gson.toJson(value)
                val sessionsType = object : TypeToken<List<CustomizedSession>>() {}.type
                val sessions: List<CustomizedSession> = gson.fromJson(sessionsJson, sessionsType)
                val sanitizedSessions = sanitizeSessions(sessions)

                save(
                    DayCustomization(
                        programId = programId,
                        weekNumber = weekNumber,
                        dayNumber = dayNumber,
                        sessions = sanitizedSessions,
                        lastModifiedAt = serverMs ?: System.currentTimeMillis(),
                        isUserModified = false
                    )
                )
                imported++
                Log.d(TAG, "hydrateFromBackend: IMPORTED $dayKey (${sanitizedSessions.size} sessions)")
            } catch (e: Exception) {
                Log.w(TAG, "hydrateFromBackend: FAILED to parse $dayKey", e)
            }
        }
        Log.d(TAG, "hydrateFromBackend: done — imported $imported day(s) for programId=$programId")
    }

    // ═══════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════

    /**
     * Gson can deserialize items with missing keys; [ProgramSessionItem] uses non-null lists with defaults.
     * Kept as a single place to normalize items if we add more list fields later.
     */
    private fun ProgramSessionItem.sanitizeGsonLists(): ProgramSessionItem = this

    private fun sanitizeSessions(sessions: List<CustomizedSession>): List<CustomizedSession> =
        sessions.map { session ->
            session.copy(items = session.items.map { it.sanitizeGsonLists() })
        }

    private fun sanitizeDayCustomization(d: DayCustomization): DayCustomization =
        d.copy(sessions = sanitizeSessions(d.sessions))

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
