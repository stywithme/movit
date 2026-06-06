package com.trainingvalidator.poc.storage

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import com.google.gson.annotations.SerializedName
import com.trainingvalidator.poc.training.workout.WorkoutTrainingEngine
import java.io.File

/**
 * ProgramWorkoutReportStore
 *
 * Persists planned-workout reports locally for progress tracking and calendar indicators.
 *
 * Storage strategy:
 *  - Individual reports stored as separate JSON files (scalable, no SharedPreferences size limit)
 *  - Index file maintains a lightweight lookup map (plannedWorkoutId → file path)
 *  - Supports offline queue for pending backend syncs
 */
class ProgramWorkoutReportStore(context: Context) {

    data class ProgramWorkoutLocalReport(
        @SerializedName("plannedWorkoutId") val workoutId: String,
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
        val report: WorkoutTrainingEngine.WorkoutReport?
    )

    /**
     * Lightweight index entry — stored in a single index file for fast lookups.
     */
    private data class ReportIndex(
        @SerializedName("plannedWorkoutId") val workoutId: String,
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
        @SerializedName("plannedWorkoutId") val workoutId: String,
        val programId: String,
        val weekNumber: Int,
        val dayNumber: Int,
        val payload: Map<String, Any>,
        val createdAt: Long = System.currentTimeMillis(),
        val retryCount: Int = 0
    )

    private val reportsDir: File = File(context.filesDir, REPORTS_DIR).also {
        migrateLegacyReportsDir(context, it)
        it.mkdirs()
    }
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

    fun getByWorkout(workoutId: String): ProgramWorkoutLocalReport? {
        val index = getIndex().firstOrNull { it.workoutId == workoutId } ?: return null
        return readReport(index.fileName)
    }

    /** @deprecated Use [getByWorkout] */
    fun getByDay(programId: String, weekNumber: Int, dayNumber: Int): List<ProgramWorkoutLocalReport> {
        return getIndex()
            .filter { it.programId == programId && it.weekNumber == weekNumber && it.dayNumber == dayNumber }
            .mapNotNull { readReport(it.fileName) }
    }

    fun getByWeek(programId: String, weekNumber: Int): List<ProgramWorkoutLocalReport> {
        return getIndex()
            .filter { it.programId == programId && it.weekNumber == weekNumber }
            .mapNotNull { readReport(it.fileName) }
    }

    fun getAll(): List<ProgramWorkoutLocalReport> {
        return getIndex().mapNotNull { readReport(it.fileName) }
    }

    // ═══════════════════════════════════════════════════════════
    // Write
    // ═══════════════════════════════════════════════════════════

    fun save(report: ProgramWorkoutLocalReport) {
        val fileName = "report_${report.workoutId}.json"
        writeReport(fileName, report)

        val index = getIndex().toMutableList()
        index.removeAll { it.workoutId == report.workoutId }
        index.add(
            ReportIndex(
                workoutId = report.workoutId,
                programId = report.programId,
                weekNumber = report.weekNumber,
                dayNumber = report.dayNumber,
                completedAt = report.completedAt,
                fileName = fileName
            )
        )
        saveIndex(index)
    }

    fun delete(workoutId: String) {
        val index = getIndex().toMutableList()
        val entry = index.firstOrNull { it.workoutId == workoutId }
        if (entry != null) {
            File(reportsDir, entry.fileName).delete()
            index.removeAll { it.workoutId == workoutId }
            saveIndex(index)
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Offline Sync Queue
    // ═══════════════════════════════════════════════════════════

    fun addPendingSync(entry: PendingSyncEntry) {
        val queue = getPendingSyncQueue().toMutableList()
        queue.removeAll { it.workoutId == entry.workoutId }
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

    fun removePendingSync(workoutId: String) {
        val queue = getPendingSyncQueue().toMutableList()
        queue.removeAll { it.workoutId == workoutId }
        savePendingSyncQueue(queue)
    }

    fun clearPendingSyncQueue() {
        pendingSyncFile.delete()
    }

    // ═══════════════════════════════════════════════════════════
    // Migration from SharedPreferences (one-time)
    // ═══════════════════════════════════════════════════════════

    fun migrateFromSharedPreferences(
        context: Context,
        prefsName: String = LEGACY_PREFS_NAME
    ) {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val json = prefs.getString(LEGACY_KEY_REPORTS, null) ?: return

        try {
            val legacyReports = gson.fromJson(json, Array<ProgramWorkoutLocalReport>::class.java)
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

    private fun readReport(fileName: String): ProgramWorkoutLocalReport? {
        return try {
            val file = File(reportsDir, fileName)
            if (!file.exists()) return null
            gson.fromJson(file.readText(), ProgramWorkoutLocalReport::class.java)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read report: $fileName", e)
            null
        }
    }

    private fun writeReport(fileName: String, report: ProgramWorkoutLocalReport) {
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

    init {
        val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS_NAME_OLD, Context.MODE_PRIVATE)
        if (legacyPrefs.getString(LEGACY_KEY_REPORTS, null) != null) {
            migrateFromSharedPreferences(context, LEGACY_PREFS_NAME_OLD)
        }
    }

    companion object {
        private const val TAG = "ProgramWorkoutReportStore"
        private const val REPORTS_DIR = "program_workout_reports"
        private const val LEGACY_REPORTS_DIR = "program_session_reports"
        private const val INDEX_FILE = "index.json"
        private const val PENDING_SYNC_FILE = "pending_sync.json"
        private const val LEGACY_PREFS_NAME = "program_workout_report_store"
        private const val LEGACY_PREFS_NAME_OLD = "program_session_report_store"
        private const val LEGACY_KEY_REPORTS = "reports"

        private fun migrateLegacyReportsDir(context: Context, targetDir: File) {
            val legacyDir = File(context.filesDir, LEGACY_REPORTS_DIR)
            if (!legacyDir.exists() || !legacyDir.isDirectory) return
            targetDir.mkdirs()
            legacyDir.listFiles()?.forEach { file ->
                val dest = File(targetDir, file.name)
                if (!dest.exists()) file.copyTo(dest, overwrite = false)
            }
            legacyDir.deleteRecursively()
            Log.d(TAG, "Migrated reports from $LEGACY_REPORTS_DIR to $REPORTS_DIR")
        }
    }
}
