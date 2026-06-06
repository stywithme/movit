package com.trainingvalidator.poc.storage

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.trainingvalidator.poc.video.VideoAnalysisResult
import java.io.File

/**
 * AnalysisResultStorage - Persistent storage for video analysis results
 * 
 * Uses Gson + Internal Storage for saving/loading results.
 * No additional dependencies required (Gson already in project).
 * 
 * Storage location: /data/data/[package]/files/analysis_results/
 */
class AnalysisResultStorage(private val context: Context) {
    
    companion object {
        private const val TAG = "AnalysisResultStorage"
        private const val STORAGE_DIR = "analysis_results"
        private const val INDEX_FILE = "index.json"
    }
    
    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()
    
    private val storageDir: File = File(context.filesDir, STORAGE_DIR)
    
    init {
        // Create storage directory if it doesn't exist
        if (!storageDir.exists()) {
            storageDir.mkdirs()
            Log.d(TAG, "Created storage directory: ${storageDir.absolutePath}")
        }
    }
    
    // ==================== Save Operations ====================
    
    /**
     * Save an analysis result
     * @return true if saved successfully
     */
    fun save(result: VideoAnalysisResult): Boolean {
        return try {
            val file = File(storageDir, "${result.id}.json")
            file.writeText(gson.toJson(result))
            
            Log.d(TAG, "Saved analysis result: ${result.id}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save analysis result: ${e.message}")
            false
        }
    }
    
    /**
     * Save multiple analysis results
     * @return number of results saved successfully
     */
    fun saveAll(results: List<VideoAnalysisResult>): Int {
        var savedCount = 0
        results.forEach { result ->
            if (save(result)) {
                savedCount++
            }
        }
        return savedCount
    }
    
    // ==================== Load Operations ====================
    
    /**
     * Get an analysis result by ID
     * @return the result or null if not found
     */
    fun getById(id: String): VideoAnalysisResult? {
        return try {
            val file = File(storageDir, "$id.json")
            if (file.exists()) {
                gson.fromJson(file.readText(), VideoAnalysisResult::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load analysis result $id: ${e.message}")
            null
        }
    }
    
    /**
     * Get all analysis results, sorted by date (newest first)
     */
    fun getAll(): List<VideoAnalysisResult> {
        return try {
            storageDir.listFiles()
                ?.filter { it.extension == "json" && it.name != INDEX_FILE }
                ?.mapNotNull { file ->
                    try {
                        gson.fromJson(file.readText(), VideoAnalysisResult::class.java)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse ${file.name}: ${e.message}")
                        null
                    }
                }
                ?.sortedByDescending { it.analysisDate }
                ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load all analysis results: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Get analysis results for a specific exercise
     */
    fun getByExercise(exerciseId: String): List<VideoAnalysisResult> {
        return getAll().filter { it.exerciseId == exerciseId }
    }
    
    /**
     * Get the most recent analysis results
     * @param limit maximum number of results to return
     */
    fun getRecent(limit: Int = 10): List<VideoAnalysisResult> {
        return getAll().take(limit)
    }
    
    /**
     * Get analysis results within a date range
     */
    fun getByDateRange(startMs: Long, endMs: Long): List<VideoAnalysisResult> {
        return getAll().filter { it.analysisDate in startMs..endMs }
    }
    
    // ==================== Delete Operations ====================
    
    /**
     * Delete an analysis result by ID
     * @return true if deleted successfully
     */
    fun delete(id: String): Boolean {
        return try {
            val file = File(storageDir, "$id.json")
            if (file.exists()) {
                val deleted = file.delete()
                Log.d(TAG, "Deleted analysis result $id: $deleted")
                deleted
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete analysis result $id: ${e.message}")
            false
        }
    }
    
    /**
     * Delete all analysis results for an exercise
     * @return number of results deleted
     */
    fun deleteByExercise(exerciseId: String): Int {
        var deletedCount = 0
        getByExercise(exerciseId).forEach { result ->
            if (delete(result.id)) {
                deletedCount++
            }
        }
        return deletedCount
    }
    
    /**
     * Delete all analysis results
     * @return number of results deleted
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
        Log.d(TAG, "Deleted all analysis results: $deletedCount")
        return deletedCount
    }
    
    // ==================== Statistics ====================
    
    /**
     * Get total number of saved analysis results
     */
    fun getCount(): Int {
        return storageDir.listFiles()
            ?.filter { it.extension == "json" && it.name != INDEX_FILE }
            ?.size ?: 0
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
     * Check if a result exists
     */
    fun exists(id: String): Boolean {
        return File(storageDir, "$id.json").exists()
    }
    
    // ==================== Exercise Statistics ====================
    
    /**
     * Get statistics for an exercise
     */
    fun getExerciseStats(exerciseId: String): ExerciseStats {
        val results = getByExercise(exerciseId)
        
        if (results.isEmpty()) {
            return ExerciseStats(
                exerciseId = exerciseId,
                totalExecutions = 0,
                totalReps = 0,
                averageAccuracy = 0f,
                bestAccuracy = 0f,
                lastExecutionDate = null
            )
        }
        
        return ExerciseStats(
            exerciseId = exerciseId,
            totalExecutions = results.size,
            totalReps = results.sumOf { it.totalReps },
            averageAccuracy = results.map { it.accuracy }.average().toFloat(),
            bestAccuracy = results.maxOf { it.accuracy },
            lastExecutionDate = results.maxOf { it.analysisDate }
        )
    }
}

/**
 * ExerciseStats - Statistics for an exercise
 */
data class ExerciseStats(
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
