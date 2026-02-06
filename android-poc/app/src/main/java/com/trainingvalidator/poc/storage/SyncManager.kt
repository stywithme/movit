package com.trainingvalidator.poc.storage

import android.content.Context
import android.util.Log
import com.trainingvalidator.poc.network.ApiClient
import com.trainingvalidator.poc.network.ExerciseConfigWithMeta
import com.trainingvalidator.poc.network.MessageTemplate
import com.trainingvalidator.poc.network.MobileSyncResponse
import com.trainingvalidator.poc.training.models.FeedbackMessages
import com.trainingvalidator.poc.training.models.LocalizedText
import com.trainingvalidator.poc.training.models.PoseVariant
import com.trainingvalidator.poc.training.models.StateMessageValue
import com.trainingvalidator.poc.training.models.StateMessages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.UnknownHostException

/**
 * SyncManager
 * 
 * Orchestrates synchronization between the mobile app and backend server.
 * Handles both incremental (auto) sync and full (manual) refresh.
 * 
 * Sync Strategies:
 * - Auto Sync: Incremental sync using last sync timestamp
 * - Manual Refresh: Full sync that replaces all cached data
 * 
 * Usage:
 * ```kotlin
 * val syncManager = SyncManager(context, exerciseCache, audioCache)
 * 
 * // Auto sync on app start
 * lifecycleScope.launch {
 *     when (val result = syncManager.syncIfNeeded()) {
 *         is SyncResult.Success -> Log.d("Sync", "Updated ${result.exercisesUpdated} exercises")
 *         is SyncResult.NoChanges -> Log.d("Sync", "Already up to date")
 *         is SyncResult.Offline -> Log.d("Sync", "Offline - using cache")
 *         is SyncResult.Error -> Log.e("Sync", result.message)
 *     }
 * }
 * 
 * // Manual refresh
 * syncManager.fullRefresh()
 * ```
 */
class SyncManager(
    private val context: Context,
    private val exerciseCache: ExerciseCacheManager,
    private val audioCache: AudioCacheManager,
    private val workoutCache: WorkoutCacheManager? = null
) {
    
    companion object {
        private const val TAG = "SyncManager"
        
        // Minimum time between auto syncs (5 minutes)
        private const val MIN_SYNC_INTERVAL_MS = 5 * 60 * 1000L
    }
    
    private var lastSyncAttempt: Long = 0
    private var isSyncing = false
    
    // Store last audio manifest for background downloads
    private var lastAudioManifest: com.trainingvalidator.poc.network.AudioManifest? = null
    private var lastAudioBaseUrl: String? = null
    
    // ==================== Result Types ====================
    
    sealed class SyncResult {
        /**
         * Sync completed successfully
         */
        data class Success(
            val exercisesUpdated: Int,
            val exercisesDeleted: Int,
            val workoutsUpdated: Int = 0,
            val workoutsDeleted: Int = 0,
            val audioFilesDownloaded: Int,
            val isFullSync: Boolean
        ) : SyncResult()
        
        /**
         * No changes since last sync
         */
        object NoChanges : SyncResult()
        
        /**
         * Device is offline
         */
        object Offline : SyncResult()
        
        /**
         * Sync skipped (too soon after last sync)
         */
        object Skipped : SyncResult()
        
        /**
         * Sync failed with error
         */
        data class Error(val message: String, val exception: Exception? = null) : SyncResult()
    }
    
    // ==================== Public Sync Methods ====================
    
    /**
     * Perform incremental sync if needed.
     * 
     * This is the primary sync method for auto-sync on app start.
     * - Checks if enough time has passed since last sync
     * - Uses last sync timestamp for incremental updates
     * - Downloads only new/updated exercises
     * - Background downloads audio files
     * 
     * @param forceCheck If true, ignores the minimum sync interval
     * @return SyncResult indicating outcome
     */
    suspend fun syncIfNeeded(forceCheck: Boolean = false): SyncResult = withContext(Dispatchers.IO) {
        // Check if we should skip
        if (!forceCheck && shouldSkipSync()) {
            Log.d(TAG, "Skipping sync - too soon after last attempt")
            return@withContext SyncResult.Skipped
        }
        
        // Prevent concurrent syncs
        if (isSyncing) {
            Log.d(TAG, "Sync already in progress")
            return@withContext SyncResult.Skipped
        }
        
        isSyncing = true
        lastSyncAttempt = System.currentTimeMillis()
        
        try {
            // Get last sync timestamp for incremental sync
            val lastSync = exerciseCache.getLastSyncTimestamp()
            
            Log.d(TAG, "Starting incremental sync (lastSync: $lastSync)")
            
            val response = ApiClient.mobileSyncApi.sync(
                updatedAfter = lastSync,
                forceRefresh = null
            )
            
            if (!response.isSuccessful) {
                return@withContext SyncResult.Error("Server error: ${response.code()}")
            }
            
            val body = response.body()
            if (body == null || !body.success) {
                return@withContext SyncResult.Error(body?.error ?: "Unknown error")
            }
            
            return@withContext processSyncResponse(body, isFullSync = body.meta?.isFullSync ?: false)
            
        } catch (e: UnknownHostException) {
            Log.w(TAG, "Offline - using cached data")
            return@withContext SyncResult.Offline
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            return@withContext SyncResult.Error(e.message ?: "Unknown error", e)
        } finally {
            isSyncing = false
        }
    }
    
    /**
     * Perform full refresh.
     * 
     * This clears the cache and downloads all exercises fresh.
     * Use for manual "Refresh" button or when cache appears corrupted.
     * 
     * @return SyncResult indicating outcome
     */
    suspend fun fullRefresh(): SyncResult = withContext(Dispatchers.IO) {
        if (isSyncing) {
            Log.d(TAG, "Sync already in progress")
            return@withContext SyncResult.Skipped
        }
        
        isSyncing = true
        lastSyncAttempt = System.currentTimeMillis()
        
        try {
            Log.d(TAG, "Starting full refresh")
            
            val response = ApiClient.mobileSyncApi.sync(
                updatedAfter = null,
                forceRefresh = true
            )
            
            if (!response.isSuccessful) {
                return@withContext SyncResult.Error("Server error: ${response.code()}")
            }
            
            val body = response.body()
            if (body == null || !body.success) {
                return@withContext SyncResult.Error(body?.error ?: "Unknown error")
            }
            
            return@withContext processSyncResponse(body, isFullSync = true)
            
        } catch (e: UnknownHostException) {
            Log.w(TAG, "Offline - cannot perform full refresh")
            return@withContext SyncResult.Offline
        } catch (e: Exception) {
            Log.e(TAG, "Full refresh failed", e)
            return@withContext SyncResult.Error(e.message ?: "Unknown error", e)
        } finally {
            isSyncing = false
        }
    }
    
    /**
     * Download pending audio files in background.
     * 
     * Call this after exercises are loaded to background-download audio.
     * Uses the audio manifest from the last sync.
     * 
     * @param onProgress Progress callback (downloaded, total)
     * @return Number of files downloaded
     */
    suspend fun downloadPendingAudio(
        onProgress: ((Int, Int) -> Unit)? = null
    ): Int {
        val manifest = lastAudioManifest
        val baseUrl = lastAudioBaseUrl
        
        if (manifest == null || baseUrl == null) {
            Log.d(TAG, "No audio manifest available for background download")
            return 0
        }
        
        if (manifest.files.isEmpty()) {
            Log.d(TAG, "No audio files to download")
            return 0
        }
        
        Log.d(TAG, "Starting background audio download: ${manifest.files.size} files")
        
        return audioCache.downloadAudioFiles(
            audioFiles = manifest.files,
            baseUrl = baseUrl,
            onProgress = onProgress
        )
    }
    
    /**
     * Check if there are pending audio downloads
     */
    fun hasPendingAudioDownloads(): Boolean {
        val manifest = lastAudioManifest ?: return false
        return manifest.files.any { !audioCache.hasAudio(it.filename) }
    }
    
    // ==================== Private Methods ====================
    
    /**
     * Process sync response and update caches
     */
    private suspend fun processSyncResponse(
        response: MobileSyncResponse,
        isFullSync: Boolean
    ): SyncResult {
        val data = response.data ?: return SyncResult.NoChanges
        val meta = response.meta
        
        @Suppress("UNNECESSARY_SAFE_CALL", "USELESS_ELVIS")
        val exercises = data.exercises ?: emptyList()
        val messageLibrary = data.messageLibrary ?: emptyList()
        val deletedExerciseIds = data.deletedExerciseIds ?: emptyList()
        val workouts = data.workouts ?: emptyList()
        val deletedWorkoutIds = data.deletedWorkoutIds ?: emptyList()
        val audioManifest = data.audioManifest
        
        // Check if there are any changes
        val hasExerciseChanges = exercises.isNotEmpty() || deletedExerciseIds.isNotEmpty()
        val hasWorkoutChanges = workouts.isNotEmpty() || deletedWorkoutIds.isNotEmpty()
        
        if (!hasExerciseChanges && !hasWorkoutChanges) {
            Log.d(TAG, "No changes since last sync")
            
            // Still update timestamp
            if (meta != null) {
                exerciseCache.saveMetadata(
                    timestamp = response.timestamp,
                    exerciseCount = meta.totalExercises,
                    serverVersion = meta.serverVersion
                )
            }
            
            return SyncResult.NoChanges
        }
        
        // Resolve messages from library before saving
        val resolvedExercises = resolveExerciseMessages(exercises, messageLibrary)
        
        // Save exercises
        if (hasExerciseChanges) {
            exerciseCache.saveExercises(resolvedExercises, isFullSync)
            
            // Remove deleted exercises
            if (deletedExerciseIds.isNotEmpty()) {
                exerciseCache.removeExercises(deletedExerciseIds)
            }
        }
        
        // Save workouts
        var workoutsUpdatedCount = 0
        var workoutsDeletedCount = 0
        if (hasWorkoutChanges && workoutCache != null) {
            workoutCache.saveWorkouts(workouts, isFullSync)
            workoutsUpdatedCount = workouts.size
            
            // Remove deleted workouts
            if (deletedWorkoutIds.isNotEmpty()) {
                workoutCache.removeWorkouts(deletedWorkoutIds)
                workoutsDeletedCount = deletedWorkoutIds.size
            }
            
            // Save workout metadata
            if (meta != null) {
                workoutCache.saveMetadata(
                    timestamp = response.timestamp,
                    workoutCount = meta.totalWorkouts,
                    serverVersion = meta.serverVersion
                )
            }
        }
        
        // Save exercise metadata
        if (meta != null) {
            exerciseCache.saveMetadata(
                timestamp = response.timestamp,
                exerciseCount = meta.totalExercises,
                serverVersion = meta.serverVersion
            )
        }
        
        // Store audio manifest for background downloads (don't block sync)
        if (audioManifest.files.isNotEmpty()) {
            lastAudioManifest = audioManifest
            // Use the effective base URL from ApiConfig (handles emulator vs physical device)
            // The server's baseUrl may be incorrect for physical devices
            lastAudioBaseUrl = com.trainingvalidator.poc.network.ApiConfig.getEffectiveBaseUrl().trimEnd('/')
            Log.d(TAG, "Audio manifest stored: ${audioManifest.files.size} files pending download (baseUrl: $lastAudioBaseUrl)")
        }
        
        Log.d(TAG, "Sync complete: ${exercises.size} exercises updated, ${deletedExerciseIds.size} deleted, " +
                   "$workoutsUpdatedCount workouts updated, $workoutsDeletedCount deleted")
        
        return SyncResult.Success(
            exercisesUpdated = exercises.size,
            exercisesDeleted = deletedExerciseIds.size,
            workoutsUpdated = workoutsUpdatedCount,
            workoutsDeleted = workoutsDeletedCount,
            audioFilesDownloaded = 0, // Audio download is now separate
            isFullSync = isFullSync
        )
    }

    /**
     * Resolve message assignments using the message library.
     * Keeps training models unchanged by expanding messages before caching.
     */
    private fun resolveExerciseMessages(
        exercises: List<ExerciseConfigWithMeta>,
        messageLibrary: List<MessageTemplate>
    ): List<ExerciseConfigWithMeta> {
        val messageMap = messageLibrary.associateBy { it.id }
        
        return exercises.map { exercise ->
            @Suppress("UNNECESSARY_SAFE_CALL", "USELESS_ELVIS")
            val variants = exercise.poseVariants ?: emptyList()
            if (variants.isEmpty()) return@map exercise
            val resolvedVariants = variants.map { variant ->
                resolvePoseVariantMessages(variant, messageMap)
            }
            exercise.copy(poseVariants = resolvedVariants)
        }
    }

    @Suppress("UNNECESSARY_SAFE_CALL", "USELESS_ELVIS")
    private fun resolvePoseVariantMessages(
        variant: PoseVariant,
        messageMap: Map<String, MessageTemplate>
    ): PoseVariant {
        val assignments = (variant.messageAssignments ?: emptyList())
            .sortedBy { it.sortOrder }

        // Always sanitize Gson-null fields, even when there are no assignments
        if (assignments.isEmpty()) return variant.sanitizeGsonDefaults()

        val motivational = (variant.feedbackMessages?.motivational ?: emptyList()).toMutableList()
        val tips = (variant.feedbackMessages?.tips ?: emptyList()).toMutableList()

        @Suppress("UNNECESSARY_SAFE_CALL", "USELESS_ELVIS")
        val trackedJoints = variant.trackedJoints ?: emptyList()
        val jointMessageMap: MutableMap<String, StateMessages?> =
            trackedJoints.associate { it.joint to it.stateMessages }.toMutableMap()
        val positionMessageMap = mutableMapOf<String, LocalizedText>()

        for (assignment in assignments) {
            val template = messageMap[assignment.messageId] ?: continue
            val content = template.content

            when (assignment.target) {
                "feedback" -> when (assignment.context) {
                    "motivational" -> motivational.add(content)
                    "tip" -> tips.add(content)
                }

                "joint_state" -> {
                    val jointCode = assignment.jointCode ?: continue
                    val stateKey = assignment.context ?: continue
                    val current = jointMessageMap[jointCode]
                    jointMessageMap[jointCode] = applyStateMessage(
                        current,
                        stateKey,
                        assignment.zone,
                        content
                    )
                }

                "position" -> {
                    val checkId = assignment.checkId ?: continue
                    positionMessageMap[checkId] = content
                }
            }
        }

        val updatedTrackedJoints = trackedJoints.map { joint ->
            val updatedMessages = jointMessageMap[joint.joint]
            val finalMessages = updatedMessages ?: joint.stateMessages
            if (finalMessages != joint.stateMessages) {
                joint.copy(stateMessages = finalMessages)
            } else {
                joint
            }
        }

        @Suppress("UNNECESSARY_SAFE_CALL", "USELESS_ELVIS")
        val positionChecks = variant.positionChecks ?: emptyList()
        val updatedPositionChecks = positionChecks.map { check ->
            val message = positionMessageMap[check.id]
            if (message != null) {
                check.copy(errorMessage = message)
            } else {
                check
            }
        }

        val updatedFeedback = if (motivational.isNotEmpty() || tips.isNotEmpty()) {
            FeedbackMessages(motivational = motivational, tips = tips)
        } else {
            variant.feedbackMessages ?: FeedbackMessages()
        }

        return variant.copy(
            trackedJoints = updatedTrackedJoints,
            positionChecks = updatedPositionChecks,
            feedbackMessages = updatedFeedback
        )
    }

    private fun applyStateMessage(
        current: StateMessages?,
        stateKey: String,
        zone: String?,
        message: LocalizedText
    ): StateMessages {
        val existing = current ?: StateMessages()
        val normalizedState = stateKey.lowercase()
        val normalizedZone = zone?.lowercase()
        val updatedValue = mergeStateMessageValue(
            when (normalizedState) {
                "perfect" -> existing.perfect
                "normal" -> existing.normal
                "pad" -> existing.pad
                "warning" -> existing.warning
                "danger" -> existing.danger
                else -> null
            },
            normalizedZone,
            message
        )

        return when (normalizedState) {
            "perfect" -> existing.copy(perfect = updatedValue)
            "normal" -> existing.copy(normal = updatedValue)
            "pad" -> existing.copy(pad = updatedValue)
            "warning" -> existing.copy(warning = updatedValue)
            "danger" -> existing.copy(danger = updatedValue)
            else -> existing
        }
    }

    private fun mergeStateMessageValue(
        existing: StateMessageValue?,
        zone: String?,
        message: LocalizedText
    ): StateMessageValue {
        if (zone.isNullOrBlank()) {
            return StateMessageValue.Single(message)
        }

        val current = existing as? StateMessageValue.ZoneSpecific
            ?: StateMessageValue.ZoneSpecific()

        return when (zone) {
            "down" -> current.copy(down = message)
            else -> current.copy(up = message)
        }
    }
    
    /**
     * Check if we should skip sync due to recent attempt
     */
    private fun shouldSkipSync(): Boolean {
        val elapsed = System.currentTimeMillis() - lastSyncAttempt
        return elapsed < MIN_SYNC_INTERVAL_MS
    }
    
    // ==================== Status Methods ====================
    
    /**
     * Check if sync is currently in progress
     */
    fun isSyncInProgress(): Boolean = isSyncing
    
    /**
     * Get sync status for UI display
     */
    fun getSyncStatus(): SyncStatus {
        val exerciseCacheStats = exerciseCache.getCacheStats()
        val workoutCacheStats = workoutCache?.getCacheStats()
        
        return SyncStatus(
            hasExercises = exerciseCache.hasExercises(),
            exerciseCount = exerciseCacheStats.exerciseCount,
            hasWorkouts = workoutCache?.hasWorkouts() ?: false,
            workoutCount = workoutCacheStats?.workoutCount ?: 0,
            lastSyncTimestamp = exerciseCacheStats.lastSyncTimestamp,
            serverVersion = exerciseCacheStats.serverVersion,
            isSyncing = isSyncing
        )
    }
    
    data class SyncStatus(
        val hasExercises: Boolean,
        val exerciseCount: Int,
        val hasWorkouts: Boolean = false,
        val workoutCount: Int = 0,
        val lastSyncTimestamp: String?,
        val serverVersion: String?,
        val isSyncing: Boolean
    )
}
