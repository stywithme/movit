package com.trainingvalidator.poc.network

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.trainingvalidator.poc.storage.AnalyticsStorage
import com.trainingvalidator.poc.training.analytics.SessionUpload
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * SessionSyncService - Syncs training sessions to the backend
 * 
 * Features:
 * - Automatic retry with exponential backoff
 * - Offline queue support via AnalyticsStorage
 * - Background sync when connectivity is restored
 */
class SessionSyncService(
    private val context: Context,
    private val baseUrl: String
) {
    companion object {
        private const val TAG = "SessionSyncService"
        private const val ENDPOINT = "/api/mobile/sessions"
        private const val MAX_RETRIES = 3
        private const val INITIAL_BACKOFF_MS = 1000L
        
        @Volatile
        private var instance: SessionSyncService? = null
        
        fun getInstance(context: Context, baseUrl: String): SessionSyncService {
            return instance ?: synchronized(this) {
                instance ?: SessionSyncService(context.applicationContext, baseUrl).also {
                    instance = it
                }
            }
        }
    }
    
    private val gson = Gson()
    private val storage = AnalyticsStorage(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)  // Sessions can be larger
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private var authToken: String? = null
    
    /**
     * Set authentication token
     */
    fun setAuthToken(token: String) {
        authToken = token
    }
    
    /**
     * Upload a session immediately
     * Falls back to local storage if upload fails
     */
    suspend fun uploadSession(upload: SessionUpload): SyncResult {
        return withContext(Dispatchers.IO) {
            try {
                val result = uploadWithRetry(upload)
                
                if (result.success) {
                    // Remove from pending if it was queued
                    storage.deletePending(upload.id)
                    Log.d(TAG, "Session ${upload.id} uploaded successfully")
                } else {
                    // Save for later sync
                    storage.savePending(upload)
                    Log.w(TAG, "Session ${upload.id} saved for later sync: ${result.error}")
                }
                
                result
            } catch (e: Exception) {
                // Save for later sync
                storage.savePending(upload)
                Log.e(TAG, "Upload failed, saved for later: ${e.message}")
                SyncResult(false, error = e.message)
            }
        }
    }
    
    /**
     * Sync all pending sessions
     */
    suspend fun syncPending(): SyncBatchResult {
        return withContext(Dispatchers.IO) {
            val pending = storage.getAllPending()
            
            if (pending.isEmpty()) {
                return@withContext SyncBatchResult(0, 0, emptyList())
            }
            
            Log.d(TAG, "Syncing ${pending.size} pending sessions...")
            
            var successCount = 0
            val errors = mutableListOf<String>()
            
            pending.forEach { upload ->
                val result = uploadWithRetry(upload)
                if (result.success) {
                    storage.markSynced(upload.id, keepCopy = false)
                    successCount++
                } else {
                    errors.add("${upload.id}: ${result.error}")
                }
            }
            
            Log.d(TAG, "Sync complete: $successCount/${pending.size} succeeded")
            SyncBatchResult(pending.size, successCount, errors)
        }
    }
    
    /**
     * Start background sync job
     */
    fun startBackgroundSync() {
        scope.launch {
            while (isActive) {
                try {
                    val result = syncPending()
                    if (result.total > 0) {
                        Log.d(TAG, "Background sync: ${result.successCount}/${result.total}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Background sync error: ${e.message}")
                }
                
                // Retry every 5 minutes
                delay(5 * 60 * 1000)
            }
        }
    }
    
    private suspend fun uploadWithRetry(upload: SessionUpload): SyncResult {
        var lastError: String? = null
        var backoffMs = INITIAL_BACKOFF_MS
        
        repeat(MAX_RETRIES) { attempt ->
            try {
                val result = doUpload(upload)
                if (result.success) return result
                
                // Don't retry on auth errors (401/403) - they won't succeed
                if (result.isAuthError) {
                    Log.w(TAG, "Auth error - not retrying. Token may be expired.")
                    return result
                }
                
                lastError = result.error
            } catch (e: Exception) {
                lastError = e.message
            }
            
            // Exponential backoff
            if (attempt < MAX_RETRIES - 1) {
                delay(backoffMs)
                backoffMs *= 2
            }
        }
        
        return SyncResult(false, error = lastError ?: "Unknown error after $MAX_RETRIES retries")
    }
    
    private fun doUpload(upload: SessionUpload): SyncResult {
        val token = authToken ?: return SyncResult(false, error = "No auth token", isAuthError = true)
        
        val json = gson.toJson(upload)
        val requestBody = json.toRequestBody("application/json".toMediaType())
        
        val request = Request.Builder()
            .url("$baseUrl$ENDPOINT")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()
        
        return try {
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> SyncResult(true)
                    response.code == 401 || response.code == 403 -> {
                        val errorBody = response.body?.string() ?: "Unauthorized"
                        SyncResult(false, error = "HTTP ${response.code}: $errorBody", isAuthError = true)
                    }
                    else -> {
                        val errorBody = response.body?.string() ?: "Unknown error"
                        SyncResult(false, error = "HTTP ${response.code}: $errorBody")
                    }
                }
            }
        } catch (e: IOException) {
            SyncResult(false, error = "Network error: ${e.message}")
        }
    }
    
    /**
     * Get pending sync count
     */
    fun getPendingCount(): Int = storage.getPendingCount()
    
    /**
     * Cancel background sync
     */
    fun cancelBackgroundSync() {
        scope.cancel()
    }
}

/**
 * Result of a single sync operation
 */
data class SyncResult(
    val success: Boolean,
    val sessionId: String? = null,
    val error: String? = null,
    val isAuthError: Boolean = false  // True for 401/403 - don't retry
)

/**
 * Result of batch sync operation
 */
data class SyncBatchResult(
    val total: Int,
    val successCount: Int,
    val errors: List<String>
) {
    val failedCount: Int get() = total - successCount
    val allSucceeded: Boolean get() = successCount == total
}
