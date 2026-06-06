package com.trainingvalidator.poc.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.google.gson.Gson
import com.trainingvalidator.poc.storage.AnalyticsStorage
import com.trainingvalidator.poc.training.analytics.WorkoutUpload
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * WorkoutSyncService - Syncs workout executions to the backend
 * 
 * Features:
 * - Automatic retry with exponential backoff
 * - Offline queue support via AnalyticsStorage
 * - Connectivity-aware: checks network before syncing
 * - Auto-sync when connectivity is restored via NetworkCallback
 * - Smart retry: skips non-retryable HTTP errors (401, 403, 404, 405)
 */
class WorkoutSyncService(
    private val context: Context,
    private val baseUrl: String
) {
    companion object {
        private const val TAG = "WorkoutSyncService"
        private const val ENDPOINT = "api/mobile/workout-executions"  // No leading slash - baseUrl ends with /
        private const val MAX_RETRIES = 3
        private const val INITIAL_BACKOFF_MS = 1000L
        private const val BACKGROUND_SYNC_INTERVAL_MS = 10 * 60 * 1000L  // 10 minutes
        
        @Volatile
        private var instance: WorkoutSyncService? = null
        
        fun getInstance(context: Context, baseUrl: String): WorkoutSyncService {
            return instance ?: synchronized(this) {
                instance ?: WorkoutSyncService(context.applicationContext, baseUrl).also {
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
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private var authToken: String? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var backgroundSyncJob: Job? = null
    
    // Build the full URL once, ensuring no double slashes
    private val uploadUrl: String by lazy {
        val base = baseUrl.trimEnd('/')
        val endpoint = ENDPOINT.trimStart('/')
        "$base/$endpoint"
    }
    
    /**
     * Set authentication token
     */
    fun setAuthToken(token: String) {
        authToken = token
    }
    
    // ==================== Connectivity ====================
    
    /**
     * Check if device has active network connectivity
     */
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    
    /**
     * Register a NetworkCallback to auto-sync when connectivity is restored.
     * Call this once (e.g., on app start).
     */
    fun registerConnectivityListener() {
        if (networkCallback != null) return  // Already registered
        
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return
        
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()
        
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available - triggering pending sync")
                scope.launch {
                    try {
                        val result = syncPending()
                        if (result.total > 0) {
                            Log.d(TAG, "Connectivity sync: ${result.successCount}/${result.total}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Connectivity sync error: ${e.message}")
                    }
                }
            }
        }
        
        try {
            connectivityManager.registerNetworkCallback(request, networkCallback!!)
            Log.d(TAG, "Connectivity listener registered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register connectivity listener: ${e.message}")
            networkCallback = null
        }
    }
    
    /**
     * Unregister the connectivity listener
     */
    fun unregisterConnectivityListener() {
        networkCallback?.let { callback ->
            try {
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                connectivityManager?.unregisterNetworkCallback(callback)
                Log.d(TAG, "Connectivity listener unregistered")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister connectivity listener: ${e.message}")
            }
            networkCallback = null
        }
    }
    
    // ==================== Upload ====================
    
    /**
     * Upload a workout execution immediately
     * Falls back to local storage if upload fails
     */
    suspend fun uploadWorkout(upload: WorkoutUpload): SyncResult {
        return withContext(Dispatchers.IO) {
            // Save locally first (guaranteed persistence)
            storage.savePending(upload)
            
            // Check connectivity before attempting upload
            if (!isNetworkAvailable()) {
                Log.d(TAG, "No network - workout ${upload.id} saved for later sync")
                return@withContext SyncResult(false, error = "No network connectivity")
            }
            
            try {
                val result = uploadWithRetry(upload)
                
                if (result.success) {
                    storage.deletePending(upload.id)
                    Log.d(TAG, "Workout ${upload.id} uploaded successfully")
                } else {
                    Log.w(TAG, "Workout ${upload.id} saved for later sync: ${result.error}")
                }
                
                result
            } catch (e: Exception) {
                Log.e(TAG, "Upload failed, saved locally: ${e.message}")
                SyncResult(false, error = e.message)
            }
        }
    }
    
    /**
     * Sync all pending workout executions
     */
    suspend fun syncPending(): SyncBatchResult {
        return withContext(Dispatchers.IO) {
            val pending = storage.getAllPending()
            
            if (pending.isEmpty()) {
                return@withContext SyncBatchResult(0, 0, emptyList())
            }
            
            // Check connectivity before attempting batch sync
            if (!isNetworkAvailable()) {
                Log.d(TAG, "${pending.size} workouts pending - no network, will sync later")
                return@withContext SyncBatchResult(pending.size, 0, listOf("No network connectivity"))
            }
            
            Log.d(TAG, "Syncing ${pending.size} pending workouts to: $uploadUrl")
            
            var successCount = 0
            val errors = mutableListOf<String>()
            
            for (upload in pending) {
                val result = uploadWithRetry(upload)
                if (result.success) {
                    storage.markSynced(upload.id, keepCopy = false)
                    successCount++
                } else {
                    errors.add("${upload.id}: ${result.error}")
                    
                    // Stop batch if we hit a non-retryable error (server issue)
                    if (result.isClientError) {
                        Log.w(TAG, "Client error (${result.error}) - stopping batch sync")
                        break
                    }
                }
            }
            
            Log.d(TAG, "Sync complete: $successCount/${pending.size} succeeded")
            SyncBatchResult(pending.size, successCount, errors)
        }
    }
    
    /**
     * Start background sync job (periodic, every 10 minutes)
     */
    fun startBackgroundSync() {
        // Cancel existing job if any
        backgroundSyncJob?.cancel()
        
        backgroundSyncJob = scope.launch {
            while (isActive) {
                try {
                    if (isNetworkAvailable() && storage.getPendingCount() > 0) {
                        val result = syncPending()
                        if (result.total > 0) {
                            Log.d(TAG, "Background sync: ${result.successCount}/${result.total}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Background sync error: ${e.message}")
                }
                
                delay(BACKGROUND_SYNC_INTERVAL_MS)
            }
        }
        
        Log.d(TAG, "Background sync started (every ${BACKGROUND_SYNC_INTERVAL_MS / 60000} min)")
    }
    
    // ==================== Internal ====================
    
    private suspend fun uploadWithRetry(upload: WorkoutUpload): SyncResult {
        var lastError: String? = null
        var backoffMs = INITIAL_BACKOFF_MS
        
        repeat(MAX_RETRIES) { attempt ->
            try {
                val result = doUpload(upload)
                if (result.success) return result
                
                // Don't retry on non-retryable errors
                if (result.isAuthError || result.isClientError) {
                    return result
                }
                
                lastError = result.error
                Log.d(TAG, "Upload attempt ${attempt + 1}/$MAX_RETRIES failed: ${result.error}")
            } catch (e: Exception) {
                lastError = e.message
                Log.d(TAG, "Upload attempt ${attempt + 1}/$MAX_RETRIES exception: ${e.message}")
            }
            
            // Exponential backoff (skip after last attempt)
            if (attempt < MAX_RETRIES - 1) {
                delay(backoffMs)
                backoffMs *= 2
            }
        }
        
        return SyncResult(false, error = lastError ?: "Unknown error after $MAX_RETRIES retries")
    }
    
    private fun doUpload(upload: WorkoutUpload): SyncResult {
        val token = authToken ?: return SyncResult(
            false, error = "No auth token", isAuthError = true
        )
        
        val json = gson.toJson(upload)
        val requestBody = json.toRequestBody("application/json".toMediaType())
        
        val request = Request.Builder()
            .url(uploadUrl)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()
        
        return try {
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> {
                        SyncResult(true)
                    }
                    // Auth errors - don't retry, token is invalid
                    response.code == 401 || response.code == 403 -> {
                        val errorBody = response.body?.string() ?: "Unauthorized"
                        Log.w(TAG, "Auth error ${response.code}: $errorBody")
                        SyncResult(false, error = "HTTP ${response.code}: $errorBody", isAuthError = true)
                    }
                    // Client errors (404, 405, 400, 422) - don't retry, request is wrong
                    response.code in 400..499 -> {
                        val errorBody = response.body?.string() ?: "Client error"
                        Log.w(TAG, "Client error ${response.code}: $errorBody")
                        SyncResult(false, error = "HTTP ${response.code}: $errorBody", isClientError = true)
                    }
                    // Server errors (500+) - retry, server might recover
                    else -> {
                        val errorBody = response.body?.string() ?: "Server error"
                        Log.w(TAG, "Server error ${response.code}: $errorBody")
                        SyncResult(false, error = "HTTP ${response.code}: $errorBody")
                    }
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Network error: ${e.message}")
            SyncResult(false, error = "Network error: ${e.message}")
        }
    }
    
    // ==================== Public API ====================
    
    /**
     * Get pending sync count
     */
    fun getPendingCount(): Int = storage.getPendingCount()
    
    /**
     * Cancel background sync and unregister listeners
     */
    fun cancelBackgroundSync() {
        backgroundSyncJob?.cancel()
        backgroundSyncJob = null
    }
    
    /**
     * Release all resources (call on app termination)
     */
    fun release() {
        cancelBackgroundSync()
        unregisterConnectivityListener()
        scope.cancel()
    }
}

/**
 * Result of a single sync operation
 */
data class SyncResult(
    val success: Boolean,
    val workoutId: String? = null,
    val error: String? = null,
    val isAuthError: Boolean = false,    // True for 401/403 - don't retry
    val isClientError: Boolean = false   // True for 4xx - don't retry (endpoint/payload issue)
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
