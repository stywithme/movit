package com.trainingvalidator.poc.storage

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import com.trainingvalidator.poc.training.session.SessionTrainingEngine
import java.io.File

/**
 * ProgramSessionReportStore
 *
 * Persists session reports locally for progress tracking and calendar indicators.
 *
 * Storage strategy:
 *  - Individual reports stored as separate JSON files (scalable, no SharedPreferences size limit)
 *  - Index file maintains a lightweight lookup map (sessionId → file path)
 *  - Supports offline queue for pending backend syncs
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
        val averageFormScore: Float = 0f,
        val totalDurationMs: Long,
        val report: SessionTrainingEngine.SessionReport?
    )

    /**
     * Lightweight index entry — stored in a single index file for fast lookups.
     */
    private data class ReportIndex(
        val sessionId: String,
        val programId: String,
        val weekNumber: Int,
        val dayNumber: Int,
        val completedAt: Long,
        val fileName: String
    )

    /**
     * Pending sync entry — reports that need to be sent to backend.
     */
    data class PendingSyncEntry(
        val sessionId: String,
        val programId: String,
        val weekNumber: Int,
        val dayNumber: Int,
        val payload: Map<String, Any>,
        val createdAt: Long = System.currentTimeMillis(),
        val retryCount: Int = 0
    )

    private val reportsDir: File = File(context.filesDir, REPORTS_DIR).also { it.mkdirs() }
    private val indexFile: File = File(reportsDir, INDEX_FILE)
    private val pendingSyncFile: File = File(reportsDir, PENDING_SYNC_FILE)

    private val gson: Gson = GsonBuilder()
        .setStrictness(Strictness.LENIENT)
        .create()

    // In-memory index cache
    private var indexCache: MutableList<ReportIndex>? = null

    // ═══════════════════════════════════════════════════════════
    // Read
    // ═══════════════════════════════════════════════════════════

    fun getBySession(sessionId: String): ProgramSessionLocalReport? {
        val index = getIndex().firstOrNull { it.sessionId == sessionId } ?: return null
        return readReport(index.fileName)
    }

    fun getByDay(programId: String, weekNumber: Int, dayNumber: Int): List<ProgramSessionLocalReport> {
        return getIndex()
            .filter { it.programId == programId && it.weekNumber == weekNumber && it.dayNumber == dayNumber }
            .mapNotNull { readReport(it.fileName) }
    }

    fun getByWeek(programId: String, weekNumber: Int): List<ProgramSessionLocalReport> {
        return getIndex()
            .filter { it.programId == programId && it.weekNumber == weekNumber }
            .mapNotNull { readReport(it.fileName) }
    }

    fun getAll(): List<ProgramSessionLocalReport> {
        return getIndex().mapNotNull { readReport(it.fileName) }
    }

    // ═══════════════════════════════════════════════════════════
    // Write
    // ═══════════════════════════════════════════════════════════

    fun save(report: ProgramSessionLocalReport) {
        val fileName = "report_${report.sessionId}.json"
        writeReport(fileName, report)

        val index = getIndex().toMutableList()
        index.removeAll { it.sessionId == report.sessionId }
        index.add(
            ReportIndex(
                sessionId = report.sessionId,
                programId = report.programId,
                weekNumber = report.weekNumber,
                dayNumber = report.dayNumber,
                completedAt = report.completedAt,
                fileName = fileName
            )
        )
        saveIndex(index)
    }

    fun delete(sessionId: String) {
        val index = getIndex().toMutableList()
        val entry = index.firstOrNull { it.sessionId == sessionId }
        if (entry != null) {
            File(reportsDir, entry.fileName).delete()
            index.removeAll { it.sessionId == sessionId }
            saveIndex(index)
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Offline Sync Queue
    // ═══════════════════════════════════════════════════════════

    fun addPendingSync(entry: PendingSyncEntry) {
        val queue = getPendingSyncQueue().toMutableList()
        queue.removeAll { it.sessionId == entry.sessionId }
        queue.add(entry)
        savePendingSyncQueue(queue)
    }

    fun getPendingSyncQueue(): List<PendingSyncEntry> {
        return try {
            if (!pendingSyncFile.exists()) return emptyList()
            val json = pendingSyncFile.readText()
            gson.fromJson(json, Array<PendingSyncEntry>::class.java)?.toList() ?: emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read pending sync queue", e)
            emptyList()
        }
    }

    fun removePendingSync(sessionId: String) {
        val queue = getPendingSyncQueue().toMutableList()
        queue.removeAll { it.sessionId == sessionId }
        savePendingSyncQueue(queue)
    }

    fun clearPendingSyncQueue() {
        pendingSyncFile.delete()
    }

    // ═══════════════════════════════════════════════════════════
    // Migration from SharedPreferences (one-time)
    // ═══════════════════════════════════════════════════════════

    fun migrateFromSharedPreferences(context: Context) {
        val prefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(LEGACY_KEY_REPORTS, null) ?: return

        try {
            val legacyReports = gson.fromJson(json, Array<ProgramSessionLocalReport>::class.java)
            if (legacyReports.isNullOrEmpty()) return

            Log.d(TAG, "Migrating ${legacyReports.size} reports from SharedPreferences to file storage")
            legacyReports.forEach { save(it) }

            // Clear legacy storage
            prefs.edit().clear().apply()
            Log.d(TAG, "Migration complete — legacy SharedPreferences cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Migration failed", e)
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Private
    // ═══════════════════════════════════════════════════════════

    private fun getIndex(): List<ReportIndex> {
        indexCache?.let { return it }

        val loaded = try {
            if (!indexFile.exists()) emptyList()
            else {
                val json = indexFile.readText()
                gson.fromJson(json, Array<ReportIndex>::class.java)?.toList() ?: emptyList()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read index", e)
            emptyList()
        }

        indexCache = loaded.toMutableList()
        return loaded
    }

    private fun saveIndex(index: List<ReportIndex>) {
        indexCache = index.toMutableList()
        try {
            indexFile.writeText(gson.toJson(index))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save index", e)
        }
    }

    private fun readReport(fileName: String): ProgramSessionLocalReport? {
        return try {
            val file = File(reportsDir, fileName)
            if (!file.exists()) return null
            gson.fromJson(file.readText(), ProgramSessionLocalReport::class.java)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read report: $fileName", e)
            null
        }
    }

    private fun writeReport(fileName: String, report: ProgramSessionLocalReport) {
        try {
            File(reportsDir, fileName).writeText(gson.toJson(report))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write report: $fileName", e)
        }
    }

    private fun savePendingSyncQueue(queue: List<PendingSyncEntry>) {
        try {
            pendingSyncFile.writeText(gson.toJson(queue))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save pending sync queue", e)
        }
    }

    companion object {
        private const val TAG = "ProgramSessionReportStore"
        private const val REPORTS_DIR = "program_session_reports"
        private const val INDEX_FILE = "index.json"
        private const val PENDING_SYNC_FILE = "pending_sync.json"
        // Legacy SharedPreferences keys for migration
        private const val LEGACY_PREFS_NAME = "program_session_report_store"
        private const val LEGACY_KEY_REPORTS = "reports"
    }
}
