package com.trainingvalidator.poc.storage

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import com.trainingvalidator.poc.training.session.SessionTrainingEngine

/**
 * ProgramSessionReportStore
 *
 * Persists session reports locally for progress and calendar indicators.
 */
class ProgramSessionReportStore(context: Context) {

    data class ProgramSessionLocalReport(
        val sessionId: String,
        val programId: String,
        val weekNumber: Int,
        val dayNumber: Int,
        val completedAt: Long,
        val totalSetsPlanned: Int,
        val totalSetsCompleted: Int,
        val totalReps: Int,
        val averageAccuracy: Float,
        val totalDurationMs: Long,
        val report: SessionTrainingEngine.SessionReport?
    )

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson: Gson = GsonBuilder()
        .setStrictness(Strictness.LENIENT)
        .create()

    fun save(report: ProgramSessionLocalReport) {
        val current = getAllMutable()
        val index = current.indexOfFirst { it.sessionId == report.sessionId }
        if (index >= 0) {
            current[index] = report
        } else {
            current.add(report)
        }
        saveAll(current)
    }

    fun getBySession(sessionId: String): ProgramSessionLocalReport? {
        return getAll().firstOrNull { it.sessionId == sessionId }
    }

    fun getByDay(programId: String, weekNumber: Int, dayNumber: Int): List<ProgramSessionLocalReport> {
        return getAll().filter {
            it.programId == programId &&
                it.weekNumber == weekNumber &&
                it.dayNumber == dayNumber
        }
    }

    fun getByWeek(programId: String, weekNumber: Int): List<ProgramSessionLocalReport> {
        return getAll().filter { it.programId == programId && it.weekNumber == weekNumber }
    }

    fun delete(sessionId: String) {
        val current = getAllMutable()
        val updated = current.filterNot { it.sessionId == sessionId }
        saveAll(updated)
    }

    fun getAll(): List<ProgramSessionLocalReport> {
        val json = prefs.getString(KEY_REPORTS, null) ?: return emptyList()
        return runCatching {
            gson.fromJson(json, Array<ProgramSessionLocalReport>::class.java).toList()
        }.getOrDefault(emptyList())
    }

    private fun getAllMutable(): MutableList<ProgramSessionLocalReport> {
        return getAll().toMutableList()
    }

    private fun saveAll(reports: List<ProgramSessionLocalReport>) {
        val json = gson.toJson(reports)
        prefs.edit().putString(KEY_REPORTS, json).apply()
    }

    companion object {
        private const val PREFS_NAME = "program_session_report_store"
        private const val KEY_REPORTS = "reports"
    }
}
