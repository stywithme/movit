package com.trainingvalidator.poc.storage

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.trainingvalidator.poc.training.analytics.SessionUpload
import java.io.*
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * AnalyticsStorage - Persistent storage for session analytics (offline sync)
 * 
 * OPTIMIZED VERSION: No raw frame data
 * 
 * Purpose:
 * - Temporarily store SessionUpload (metrics only) until synced to server
 * - Provide offline capability - sessions can be uploaded when online
 * - Lightweight: ~500 bytes per session (vs ~15KB with raw frames)
 * 
 * Storage Strategy:
 * - Pending sync → Compressed JSON files (.json.gz)
 * - After successful sync → File deleted
 * 
 * File Structure:
 * /files/analytics/pending/
 *   ├── session_abc123.json.gz  (waiting for sync)
 *   ├── session_def456.json.gz
 *   └── ...
 */
class AnalyticsStorage(private val context: Context) {
    
    companion object {
        private const val TAG = "AnalyticsStorage"
        private const val STORAGE_DIR = "analytics"
        private const val PENDING_DIR = "pending"
        private const val SYNCED_DIR = "synced"  // Kept briefly for debugging
        private const val FILE_PREFIX = "session_"
        private const val FILE_EXTENSION = ".json.gz"
        
        // Cleanup settings
        private const val DEFAULT_MAX_PENDING = 50
        private const val DEFAULT_KEEP_SYNCED_HOURS = 24
    }
    
    private val gson: Gson = GsonBuilder()
        .serializeNulls()
        .create()
    
    private val pendingDir: File = File(File(context.filesDir, STORAGE_DIR), PENDING_DIR)
    private val syncedDir: File = File(File(context.filesDir, STORAGE_DIR), SYNCED_DIR)
    
    init {
        pendingDir.mkdirs()
        syncedDir.mkdirs()
        Log.d(TAG, "Analytics storage initialized: ${pendingDir.absolutePath}")
    }
    
    // ==================== Save Operations ====================
    
    /**
     * Save a session upload for later sync
     * 
     * @param upload SessionUpload to save
     * @return true if saved successfully
     */
    fun savePending(upload: SessionUpload): Boolean {
        return try {
            val file = File(pendingDir, "$FILE_PREFIX${upload.id}$FILE_EXTENSION")
            val json = gson.toJson(upload)
            
            // Write compressed
            GZIPOutputStream(FileOutputStream(file)).bufferedWriter().use { writer ->
                writer.write(json)
            }
            
            val sizeBytes = file.length()
            Log.d(TAG, "Saved pending session ${upload.id}: $sizeBytes bytes, " +
                       "${upload.totalReps} reps, NO raw frames")
            
            // Trigger cleanup if needed
            cleanupOldPending()
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save pending session: ${e.message}", e)
            false
        }
    }
    
    /**
     * Mark a session as synced (move to synced folder or delete)
     */
    fun markSynced(sessionId: String, keepCopy: Boolean = false): Boolean {
        val pendingFile = File(pendingDir, "$FILE_PREFIX$sessionId$FILE_EXTENSION")
        
        if (!pendingFile.exists()) {
            Log.w(TAG, "Pending session not found: $sessionId")
            return false
        }
        
        return try {
            if (keepCopy) {
                // Move to synced folder
                val syncedFile = File(syncedDir, "$FILE_PREFIX$sessionId$FILE_EXTENSION")
                pendingFile.renameTo(syncedFile)
                Log.d(TAG, "Session $sessionId marked synced (kept)")
            } else {
                // Just delete
                pendingFile.delete()
                Log.d(TAG, "Session $sessionId synced and deleted")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mark session synced: ${e.message}", e)
            false
        }
    }
    
    // ==================== Load Operations ====================
    
    /**
     * Load a pending session
     */
    fun loadPending(sessionId: String): SessionUpload? {
        val file = File(pendingDir, "$FILE_PREFIX$sessionId$FILE_EXTENSION")
        return loadFromFile(file)
    }
    
    /**
     * Get all pending sessions (for sync)
     */
    fun getAllPending(): List<SessionUpload> {
        return pendingDir.listFiles { file -> 
            file.name.endsWith(FILE_EXTENSION) 
        }?.mapNotNull { loadFromFile(it) } ?: emptyList()
    }
    
    /**
     * Get pending session IDs
     */
    fun getPendingIds(): List<String> {
        return pendingDir.listFiles { file -> 
            file.name.endsWith(FILE_EXTENSION) 
        }?.map { file ->
            file.name.removePrefix(FILE_PREFIX).removeSuffix(FILE_EXTENSION)
        } ?: emptyList()
    }
    
    /**
     * Get count of pending sessions
     */
    fun getPendingCount(): Int {
        return pendingDir.listFiles { file -> 
            file.name.endsWith(FILE_EXTENSION) 
        }?.size ?: 0
    }
    
    private fun loadFromFile(file: File): SessionUpload? {
        if (!file.exists()) return null
        
        return try {
            val json = GZIPInputStream(FileInputStream(file)).bufferedReader().use { 
                it.readText() 
            }
            gson.fromJson(json, SessionUpload::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load session from ${file.name}: ${e.message}")
            null
        }
    }
    
    // ==================== Delete Operations ====================
    
    /**
     * Delete a pending session
     */
    fun deletePending(sessionId: String): Boolean {
        val file = File(pendingDir, "$FILE_PREFIX$sessionId$FILE_EXTENSION")
        return if (file.exists()) {
            file.delete()
        } else false
    }
    
    /**
     * Clear all pending sessions (careful!)
     */
    fun clearAllPending(): Int {
        val files = pendingDir.listFiles() ?: return 0
        var count = 0
        files.forEach { if (it.delete()) count++ }
        Log.w(TAG, "Cleared all pending sessions: $count files")
        return count
    }
    
    // ==================== Cleanup ====================
    
    private fun cleanupOldPending() {
        try {
            val files = pendingDir.listFiles() ?: return
            
            // Keep only last N pending sessions
            if (files.size > DEFAULT_MAX_PENDING) {
                val sorted = files.sortedBy { it.lastModified() }
                val toDelete = sorted.take(files.size - DEFAULT_MAX_PENDING)
                toDelete.forEach { 
                    it.delete()
                    Log.d(TAG, "Cleaned up old pending: ${it.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup failed: ${e.message}")
        }
    }
    
    /**
     * Cleanup old synced files
     */
    fun cleanupSynced() {
        try {
            val files = syncedDir.listFiles() ?: return
            val cutoff = System.currentTimeMillis() - (DEFAULT_KEEP_SYNCED_HOURS * 60 * 60 * 1000)
            
            files.filter { it.lastModified() < cutoff }.forEach { file ->
                file.delete()
                Log.d(TAG, "Cleaned up synced: ${file.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Synced cleanup failed: ${e.message}")
        }
    }
    
    // ==================== Stats ====================
    
    /**
     * Get storage statistics
     */
    fun getStats(): StorageStats {
        val pendingFiles = pendingDir.listFiles() ?: emptyArray()
        val syncedFiles = syncedDir.listFiles() ?: emptyArray()
        
        return StorageStats(
            pendingCount = pendingFiles.size,
            pendingSizeBytes = pendingFiles.sumOf { it.length() },
            syncedCount = syncedFiles.size,
            syncedSizeBytes = syncedFiles.sumOf { it.length() }
        )
    }
}

/**
 * Storage statistics
 */
data class StorageStats(
    val pendingCount: Int,
    val pendingSizeBytes: Long,
    val syncedCount: Int,
    val syncedSizeBytes: Long
) {
    val totalCount: Int get() = pendingCount + syncedCount
    val totalSizeKB: Double get() = (pendingSizeBytes + syncedSizeBytes) / 1024.0
    
    fun getFormattedPendingSize(): String = "%.1f KB".format(pendingSizeBytes / 1024.0)
    fun getFormattedTotalSize(): String = "%.1f KB".format(totalSizeKB)
}
