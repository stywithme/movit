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
        val programId: String,
        val weekNumber: Int,
        val dayNumber: Int,
        val sessions: List<CustomizedSession>,
        val lastModifiedAt: Long = System.currentTimeMillis()
    )

    /**
     * A session with potential modifications.
     * Mirrors ProgramSession but is fully mutable.
     */
    data class CustomizedSession(
        val id: String,
        val name: LocalizedText,
        val sortOrder: Int,
        val items: List<ProgramSessionItem>,
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
        val json = prefs.getString(key, null) ?: return null
        return try {
            gson.fromJson(json, DayCustomization::class.java)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse customization for $key", e)
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
        val customization = get(programId, weekNumber, dayNumber)
        if (customization != null) {
            return customization.sessions.filter { !it.isDeleted }
        }
        // Convert original sessions to customized format
        return originalSessions.map { session ->
            CustomizedSession(
                id = session.id,
                name = session.name,
                sortOrder = session.sortOrder,
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
        save(DayCustomization(
            programId = programId,
            weekNumber = weekNumber,
            dayNumber = dayNumber,
            sessions = sessions
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
        save(current.copy(sessions = updatedSessions, lastModifiedAt = System.currentTimeMillis()))
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
        save(current.copy(sessions = updatedSessions, lastModifiedAt = System.currentTimeMillis()))
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
        save(current.copy(sessions = updatedSessions, lastModifiedAt = System.currentTimeMillis()))
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
            lastModifiedAt = System.currentTimeMillis()
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
    // Helpers
    // ═══════════════════════════════════════════════════════════

    private fun buildKey(programId: String, weekNumber: Int, dayNumber: Int): String {
        return "${KEY_PREFIX}${programId}_${weekNumber}_${dayNumber}"
    }
}
