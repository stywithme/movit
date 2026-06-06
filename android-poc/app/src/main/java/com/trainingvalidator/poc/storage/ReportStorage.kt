package com.trainingvalidator.poc.storage

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.trainingvalidator.poc.training.report.PostTrainingReport
import java.io.File

/**
 * ReportStorage - Persistent storage for post-training reports
 * 
 * Uses Gson + Internal Storage for saving/loading reports.
 * Storage location: /data/data/[package]/files/training_reports/
 */
class ReportStorage(private val context: Context) {
    
    companion object {
        private const val TAG = "ReportStorage"
        private const val STORAGE_DIR = "training_reports"
        private const val MAX_REPORTS = 50  // Keep last 50 reports
    }
    
    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()
    
    private val storageDir: File = File(context.filesDir, STORAGE_DIR)
    
    init {
        if (!storageDir.exists()) {
            storageDir.mkdirs()
            Log.d(TAG, "Created storage directory: ${storageDir.absolutePath}")
        }
    }
    
    // ==================== Save Operations ====================
    
    /**
     * Save a report
     * @return true if saved successfully
     */
    fun save(report: PostTrainingReport): Boolean {
        return try {
            val file = File(storageDir, "${report.id}.json")
            file.writeText(gson.toJson(report))
            
            Log.d(TAG, "Saved report: ${report.id}")
            
            // Cleanup old reports
            cleanupOldReports()
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save report: ${e.message}")
            false
        }
    }
    
    // ==================== Load Operations ====================
    
    /**
     * Get a report by ID
     * @return the report or null if not found
     */
    fun getById(id: String): PostTrainingReport? {
        return try {
            val file = File(storageDir, "$id.json")
            if (file.exists()) {
                gson.fromJson(file.readText(), PostTrainingReport::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load report $id: ${e.message}")
            null
        }
    }
    
    /**
     * Get all reports, sorted by timestamp (newest first)
     */
    fun getAll(): List<PostTrainingReport> {
        return try {
            storageDir.listFiles()
                ?.filter { it.extension == "json" }
                ?.mapNotNull { file ->
                    try {
                        gson.fromJson(file.readText(), PostTrainingReport::class.java)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse ${file.name}: ${e.message}")
                        null
                    }
                }
                ?.sortedByDescending { it.timestamp }
                ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load all reports: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Get reports for a specific exercise
     */
    fun getByExercise(exerciseId: String): List<PostTrainingReport> {
        return getAll().filter { it.exerciseId == exerciseId }
    }
    
    /**
     * Get the most recent reports
     * @param limit maximum number of reports to return
     */
    fun getRecent(limit: Int = 10): List<PostTrainingReport> {
        return getAll().take(limit)
    }
    
    /**
     * Get the most recent report for an exercise
     */
    fun getLatestForExercise(exerciseId: String): PostTrainingReport? {
        return getByExercise(exerciseId).firstOrNull()
    }
    
    /**
     * Get reports within a date range
     */
    fun getByDateRange(startMs: Long, endMs: Long): List<PostTrainingReport> {
        return getAll().filter { it.timestamp in startMs..endMs }
    }
    
    // ==================== Delete Operations ====================
    
    /**
     * Delete a report by ID
     * @return true if deleted successfully
     */
    fun delete(id: String): Boolean {
        return try {
            val file = File(storageDir, "$id.json")
            if (file.exists()) {
                val deleted = file.delete()
                Log.d(TAG, "Deleted report $id: $deleted")
                deleted
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete report $id: ${e.message}")
            false
        }
    }
    
    /**
     * Delete all reports for an exercise
     * @return number of reports deleted
     */
    fun deleteByExercise(exerciseId: String): Int {
        var deletedCount = 0
        getByExercise(exerciseId).forEach { report ->
            if (delete(report.id)) {
                deletedCount++
            }
        }
        return deletedCount
    }
    
    /**
     * Delete all reports
     * @return number of reports deleted
     */
    fun deleteAll(): Int {
        var deletedCount = 0
        storageDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.forEach { file ->
                if (file.delete()) {
                    deletedCount++
                }
            }
        Log.d(TAG, "Deleted all reports: $deletedCount")
        return deletedCount
    }
    
    // ==================== Statistics ====================
    
    /**
     * Get total number of saved reports
     */
    fun getCount(): Int {
        return storageDir.listFiles()
            ?.count { it.extension == "json" } ?: 0
    }
    
    /**
     * Get storage size in bytes
     */
    fun getStorageSize(): Long {
        return storageDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.sumOf { it.length() } ?: 0L
    }
    
    /**
     * Get formatted storage size
     */
    fun getFormattedStorageSize(): String {
        val bytes = getStorageSize()
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }
    
    /**
     * Check if a report exists
     */
    fun exists(id: String): Boolean {
        return File(storageDir, "$id.json").exists()
    }
    
    // ==================== Maintenance ====================
    
    /**
     * Cleanup old reports if exceeding max count
     */
    private fun cleanupOldReports() {
        val reports = getAll()
        if (reports.size > MAX_REPORTS) {
            // Delete oldest reports
            reports.drop(MAX_REPORTS).forEach { report ->
                delete(report.id)
                
                // Also delete associated frame captures
                deleteFrameCaptures(report.workoutId)
            }
            Log.d(TAG, "Cleaned up ${reports.size - MAX_REPORTS} old reports")
        }
    }
    
    /**
     * Delete frame captures for a workout execution
     */
    private fun deleteFrameCaptures(workoutId: String) {
        val capturesDir = File(context.filesDir, "frame_captures/$workoutId")
        if (capturesDir.exists()) {
            capturesDir.deleteRecursively()
            Log.d(TAG, "Deleted frame captures for workout: $workoutId")
        }
    }
    
    // ==================== Exercise Statistics ====================
    
    /**
     * Get statistics for an exercise across all reports
     */
    fun getExerciseStats(exerciseId: String): ReportStats {
        val reports = getByExercise(exerciseId)
        
        if (reports.isEmpty()) {
            return ReportStats(
                exerciseId = exerciseId,
                totalExecutions = 0,
                totalReps = 0,
                averageAccuracy = 0f,
                bestAccuracy = 0f,
                lastExecutionDate = null
            )
        }
        
        return ReportStats(
            exerciseId = exerciseId,
            totalExecutions = reports.size,
            totalReps = reports.sumOf { it.summary.totalReps },
            averageAccuracy = reports.map { it.summary.accuracy }.average().toFloat(),
            bestAccuracy = reports.maxOf { it.summary.accuracy },
            lastExecutionDate = reports.maxOf { it.timestamp }
        )
    }
}

/**
 * ReportStats - Statistics for an exercise based on reports
 */
data class ReportStats(
    val exerciseId: String,
    val totalExecutions: Int,
    val totalReps: Int,
    val averageAccuracy: Float,
    val bestAccuracy: Float,
    val lastExecutionDate: Long?
) {
    fun getFormattedAverageAccuracy(): String = String.format("%.0f%%", averageAccuracy)
    fun getFormattedBestAccuracy(): String = String.format("%.0f%%", bestAccuracy)
    
    fun getFormattedLastExecution(): String {
        return lastExecutionDate?.let {
            val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
            sdf.format(java.util.Date(it))
        } ?: "Never"
    }
}
