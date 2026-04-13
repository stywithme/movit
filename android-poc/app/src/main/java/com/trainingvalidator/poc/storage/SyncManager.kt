package com.trainingvalidator.poc.storage

import android.content.Context
import android.util.Log
import com.trainingvalidator.poc.network.ApiClient
import com.trainingvalidator.poc.network.ExerciseConfigWithMeta
import com.trainingvalidator.poc.network.MessageTemplate
import com.trainingvalidator.poc.network.MobileSyncResponse
import com.trainingvalidator.poc.storage.AuthManager
import com.trainingvalidator.poc.training.models.FeedbackMessages
import com.trainingvalidator.poc.training.models.LocalizedText
import com.trainingvalidator.poc.training.models.PoseVariant
import com.trainingvalidator.poc.training.models.StateMessageValue
import com.trainingvalidator.poc.training.models.StateMessages
import com.trainingvalidator.poc.network.ApiConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

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
    private val workoutCache: WorkoutCacheManager? = null,
    private val programCache: ProgramCacheManager? = null
) {
    
    companion object {
        private const val TAG = "SyncManager"
        
        // Minimum time between auto syncs (5 minutes)
        private const val MIN_SYNC_INTERVAL_MS = 5 * 60 * 1000L
        
        private const val PREFS_NAME = "sync_manager_prefs"
        private const val KEY_CACHED_MSG_COUNT = "cached_message_count"
        private const val KEY_CACHED_MSG_AUDIO = "cached_message_audio_count"
        private const val KEY_CACHED_MSG_ASSIGNMENTS = "cached_message_assignments"
    }
    
    private val lastSyncAttemptMs = AtomicLong(0L)
    private val syncBusy = AtomicBoolean(false)
    
    // Store last audio manifest for background downloads
    private var lastAudioManifest: com.trainingvalidator.poc.network.AudioManifest? = null
    private var lastAudioBaseUrl: String? = null
    private val userProgramStore = UserProgramStore(context)
    private val syncPrefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)

    init {
        val (url, man) = audioCache.getPersistedManifestState()
        if (url != null && man != null && man.files.isNotEmpty()) {
            lastAudioBaseUrl = url
            lastAudioManifest = man
            Log.d(TAG, "Restored audio manifest from disk: ${man.files.size} files")
        }
    }
    
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
            val programsUpdated: Int = 0,
            val programsDeleted: Int = 0,
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
         * Local cache is stale — caller should trigger fullRefresh()
         */
        object NeedsFullRefresh : SyncResult()
        
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
        if (!forceCheck && shouldSkipSync()) {
            Log.d(TAG, "Skipping sync - too soon after last attempt")
            return@withContext SyncResult.Skipped
        }

        if (!syncBusy.compareAndSet(false, true)) {
            Log.d(TAG, "Sync already in progress")
            return@withContext SyncResult.Skipped
        }

        lastSyncAttemptMs.set(System.currentTimeMillis())

        try {
            val lastSync = exerciseCache.getLastSyncTimestamp()

            Log.d(TAG, "Starting incremental sync (lastSync: $lastSync)")

            val authHeader = AuthManager.getAuthHeader(context)
            val response = ApiClient.mobileSyncApi.sync(
                authorization = authHeader,
                updatedAfter = lastSync,
                forceRefresh = null
            )

            if (!response.isSuccessful) {
                lastSyncAttemptMs.set(0L)
                return@withContext SyncResult.Error("Server error: ${response.code()}")
            }

            val body = response.body()
            if (body == null || !body.success) {
                lastSyncAttemptMs.set(0L)
                return@withContext SyncResult.Error(body?.error ?: "Unknown error")
            }

            var result = processSyncResponse(body, isFullSync = body.meta?.isFullSync ?: false)

            if (result is SyncResult.NoChanges || result is SyncResult.Success) {
                val needsMessageRefresh = checkMessageStatsMismatch(body.meta?.messageLibraryStats)
                if (needsMessageRefresh) {
                    Log.w(TAG, "Triggering full refresh due to message stats mismatch")
                    result = executeFullRefreshLocked()
                }
            }

            if (result is SyncResult.Success || result is SyncResult.NoChanges) {
                try {
                    flushPendingSessionReports()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to flush pending reports after sync", e)
                }
            }

            return@withContext result
        } catch (e: UnknownHostException) {
            Log.w(TAG, "Offline - using cached data")
            lastSyncAttemptMs.set(0L)
            return@withContext SyncResult.Offline
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            lastSyncAttemptMs.set(0L)
            return@withContext SyncResult.Error(e.message ?: "Unknown error", e)
        } finally {
            syncBusy.set(false)
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
        if (!syncBusy.compareAndSet(false, true)) {
            Log.d(TAG, "Sync already in progress")
            return@withContext SyncResult.Skipped
        }

        lastSyncAttemptMs.set(System.currentTimeMillis())

        try {
            return@withContext try {
                executeFullRefreshLocked()
            } catch (e: UnknownHostException) {
                Log.w(TAG, "Offline - cannot perform full refresh")
                SyncResult.Offline
            } catch (e: Exception) {
                Log.e(TAG, "Full refresh failed", e)
                SyncResult.Error(e.message ?: "Unknown error", e)
            }
        } finally {
            syncBusy.set(false)
        }
    }

    /**
     * Full sync while [syncBusy] is already held (e.g. message-stats reconciliation).
     */
    private suspend fun executeFullRefreshLocked(): SyncResult {
        Log.d(TAG, "Starting full refresh")
        val authHeader = AuthManager.getAuthHeader(context)
        val response = ApiClient.mobileSyncApi.sync(
            authorization = authHeader,
            updatedAfter = null,
            forceRefresh = true
        )
        if (!response.isSuccessful) {
            return SyncResult.Error("Server error: ${response.code()}")
        }
        val body = response.body()
        if (body == null || !body.success) {
            return SyncResult.Error(body?.error ?: "Unknown error")
        }
        return processSyncResponse(body, isFullSync = true)
    }

    /**
     * Prefer server [AudioManifest.baseUrl]; resolve relative bases against API host.
     */
    private fun resolveEffectiveAudioBase(manifest: com.trainingvalidator.poc.network.AudioManifest): String {
        val raw = manifest.baseUrl.trim()
        val api = ApiConfig.getEffectiveBaseUrl().trimEnd('/')
        if (raw.isEmpty()) return api
        if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
            return raw.trimEnd('/')
        }
        val path = raw.trim().trimEnd('/')
        return if (path.startsWith("/")) api + path else "$api/$path"
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
    @Suppress("USELESS_ELVIS")
    private suspend fun processSyncResponse(
        response: MobileSyncResponse,
        isFullSync: Boolean
    ): SyncResult {
        val data = response.data ?: return SyncResult.NoChanges
        val meta = response.meta
        
        val exercises = data.exercises ?: emptyList()
        val messageLibrary = data.messageLibrary ?: emptyList()
        val deletedExerciseIds = data.deletedExerciseIds ?: emptyList()
        val workouts = data.workouts ?: emptyList()
        val deletedWorkoutIds = data.deletedWorkoutIds ?: emptyList()
        val programs = data.programs ?: emptyList()
        val deletedProgramIds = data.deletedProgramIds ?: emptyList()
        val userPrograms = data.userPrograms ?: emptyList()
        val audioManifest = data.audioManifest
        
        // ── Always process user-specific data (userPrograms, sessionReports) ──
        // These must be handled regardless of exercise/program changes,
        // otherwise the startDate and training reports never sync.
        if (userPrograms.isNotEmpty()) {
            userProgramStore.saveUserPrograms(userPrograms)
            Log.d(TAG, "Saved ${userPrograms.size} user programs (startDate, active status)")

            // Hydrate DayCustomizationStore from backend customizations
            val customizationStore = DayCustomizationStore(context)
            userPrograms.forEach { up ->
                val pid = up.programId
                if (pid != null && !up.customizations.isNullOrEmpty()) {
                    customizationStore.hydrateFromBackend(pid, up.customizations)
                }
            }
        }

        // Sync session reports from backend → local store
        val sessionReports = data.sessionReports ?: emptyList()
        if (sessionReports.isNotEmpty()) {
            val reportStore = ProgramSessionReportStore(context)
            var reportsHydrated = 0
            for (sr in sessionReports) {
                val existing = reportStore.getBySession(sr.sessionId)
                if (existing == null) {
                    reportStore.save(
                        ProgramSessionReportStore.ProgramSessionLocalReport(
                            sessionId = sr.sessionId,
                            programId = sr.programId,
                            weekNumber = sr.weekNumber,
                            dayNumber = sr.dayNumber,
                            completedAt = parseIsoTimestamp(sr.completedAt),
                            totalSetsPlanned = sr.totalSets,
                            totalSetsCompleted = sr.completedSets,
                            totalReps = sr.totalReps,
                            averageAccuracy = sr.avgAccuracy.toFloat(),
                            averageFormScore = (sr.avgFormScore ?: 0.0).toFloat(),
                            totalDurationMs = sr.totalDurationMs.toLong(),
                            report = null
                        )
                    )
                    reportsHydrated++
                }
            }
            if (reportsHydrated > 0) {
                Log.d(TAG, "Hydrated $reportsHydrated session reports from backend")
            }
        }

        // System messages + audio manifest (independent of exercise/workout delta)
        val systemMessages = data.systemMessages ?: emptyList()
        if (systemMessages.isNotEmpty()) {
            SystemMessageStore(context).save(systemMessages)
        }
        if (audioManifest.files.isNotEmpty()) {
            lastAudioManifest = audioManifest
            lastAudioBaseUrl = resolveEffectiveAudioBase(audioManifest)
            audioCache.persistAudioManifest(lastAudioBaseUrl!!, audioManifest)
            Log.d(TAG, "Audio manifest stored: ${audioManifest.files.size} files (baseUrl: $lastAudioBaseUrl)")
        }

        // Check if there are content changes (exercises, workouts, programs)
        val hasExerciseChanges = exercises.isNotEmpty() || deletedExerciseIds.isNotEmpty()
        val hasWorkoutChanges = workouts.isNotEmpty() || deletedWorkoutIds.isNotEmpty()
        val hasProgramChanges = programs.isNotEmpty() || deletedProgramIds.isNotEmpty()
        
        if (!hasExerciseChanges && !hasWorkoutChanges && !hasProgramChanges) {
            val localExercises = exerciseCache.getExerciseCount()
            val localWorkouts = workoutCache?.getWorkoutCount() ?: 0
            val localPrograms = programCache?.getProgramCount() ?: 0
            val serverTotalEx = meta?.totalExercises ?: localExercises
            val serverTotalWk = meta?.totalWorkouts ?: localWorkouts
            val serverTotalPr = meta?.totalPrograms ?: localPrograms

            if (localExercises > serverTotalEx ||
                (workoutCache != null && localWorkouts > serverTotalWk) ||
                (programCache != null && localPrograms > serverTotalPr)
            ) {
                Log.w(
                    TAG,
                    "Cache drift: exercises $localExercises/$serverTotalEx, workouts $localWorkouts/$serverTotalWk, programs $localPrograms/$serverTotalPr — needs full refresh"
                )
                if (meta != null) {
                    exerciseCache.saveMetadata(
                        timestamp = response.timestamp,
                        exerciseCount = meta.totalExercises,
                        serverVersion = meta.serverVersion
                    )
                }
                saveMessageStats(meta?.messageLibraryStats)
                return SyncResult.NeedsFullRefresh
            }

            var messageMergeCount = 0
            if (messageLibrary.isNotEmpty() && exerciseCache.hasExercises()) {
                val merged = resolveExerciseMessages(
                    exerciseCache.getAllCachedAsExerciseMeta(),
                    messageLibrary
                )
                exerciseCache.saveExercises(merged, isFullSync = false)
                messageMergeCount = merged.size
                Log.d(TAG, "Applied messageLibrary to $messageMergeCount cached exercises (no entity delta)")
            }

            if (meta != null) {
                exerciseCache.saveMetadata(
                    timestamp = response.timestamp,
                    exerciseCount = meta.totalExercises,
                    serverVersion = meta.serverVersion
                )
                workoutCache?.saveMetadata(
                    timestamp = response.timestamp,
                    workoutCount = meta.totalWorkouts,
                    serverVersion = meta.serverVersion
                )
                programCache?.saveMetadata(
                    timestamp = response.timestamp,
                    programCount = meta.totalPrograms,
                    serverVersion = meta.serverVersion
                )
            }
            saveMessageStats(meta?.messageLibraryStats)

            return if (messageMergeCount > 0) {
                SyncResult.Success(
                    exercisesUpdated = messageMergeCount,
                    exercisesDeleted = 0,
                    workoutsUpdated = 0,
                    workoutsDeleted = 0,
                    programsUpdated = 0,
                    programsDeleted = 0,
                    audioFilesDownloaded = 0,
                    isFullSync = isFullSync
                )
            } else {
                SyncResult.NoChanges
            }
        }
        
        val withAudio = messageLibrary.count { it.content.audioAr != null || it.content.audioEn != null }
        val total = messageLibrary.size
        if (total > 0 && withAudio == 0) {
            Log.w(TAG, "Message library has $total templates but none include audio URLs — TTS fallback may be used")
        }

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

        // Save programs
        var programsUpdatedCount = 0
        var programsDeletedCount = 0
        if (hasProgramChanges && programCache != null) {
            programCache.savePrograms(programs, isFullSync)
            programsUpdatedCount = programs.size

            if (deletedProgramIds.isNotEmpty()) {
                programCache.removePrograms(deletedProgramIds)
                programsDeletedCount = deletedProgramIds.size
            }

            if (meta != null) {
                programCache.saveMetadata(
                    timestamp = response.timestamp,
                    programCount = meta.totalPrograms,
                    serverVersion = meta.serverVersion
                )
            }
        }

        // NOTE: userPrograms and sessionReports are already processed above
        // (before the hasChanges check) to ensure they always sync.
        
        // Save exercise metadata
        if (meta != null) {
            exerciseCache.saveMetadata(
                timestamp = response.timestamp,
                exerciseCount = meta.totalExercises,
                serverVersion = meta.serverVersion
            )
        }
        
        // Persist message stats so future incremental syncs can detect drift
        saveMessageStats(meta?.messageLibraryStats)
        
        Log.d(TAG, "Sync complete: ${exercises.size} exercises updated, ${deletedExerciseIds.size} deleted, " +
                   "$workoutsUpdatedCount workouts updated, $workoutsDeletedCount deleted, " +
                   "$programsUpdatedCount programs updated, $programsDeletedCount deleted")
        
        return SyncResult.Success(
            exercisesUpdated = exercises.size,
            exercisesDeleted = deletedExerciseIds.size,
            workoutsUpdated = workoutsUpdatedCount,
            workoutsDeleted = workoutsDeletedCount,
            programsUpdated = programsUpdatedCount,
            programsDeleted = programsDeletedCount,
            audioFilesDownloaded = 0, // Audio download is now separate
            isFullSync = isFullSync
        )
    }

    /**
     * Compare server-reported message stats with locally cached counts.
     * Returns true if a full sync is needed.
     */
    private fun checkMessageStatsMismatch(
        serverStats: com.trainingvalidator.poc.network.MessageLibraryStats?
    ): Boolean {
        if (serverStats == null) return false
        
        val cachedMessages = syncPrefs.getInt(KEY_CACHED_MSG_COUNT, -1)
        val cachedAudio = syncPrefs.getInt(KEY_CACHED_MSG_AUDIO, -1)
        val cachedAssignments = syncPrefs.getInt(KEY_CACHED_MSG_ASSIGNMENTS, -1)
        
        // Never synced message stats before — if server has messages, we need a full sync
        if (cachedMessages == -1) {
            val serverHasMessages = serverStats.totalMessages > 0 || serverStats.totalAssignments > 0
            if (serverHasMessages) {
                Log.w(
                    TAG,
                    "First sync with message library on server (msgs=${serverStats.totalMessages}, assigns=${serverStats.totalAssignments}) — forcing full refresh"
                )
            }
            return serverHasMessages
        }
        
        val mismatch = cachedMessages != serverStats.totalMessages
                || cachedAudio != serverStats.totalWithAudio
                || cachedAssignments != serverStats.totalAssignments
        
        if (mismatch) {
            Log.w(TAG, "Message stats mismatch: " +
                "cached(msgs=$cachedMessages, audio=$cachedAudio, assigns=$cachedAssignments) vs " +
                "server(msgs=${serverStats.totalMessages}, audio=${serverStats.totalWithAudio}, assigns=${serverStats.totalAssignments})")
        }
        return mismatch
    }
    
    /**
     * Persist server message stats for future comparison.
     */
    private fun saveMessageStats(stats: com.trainingvalidator.poc.network.MessageLibraryStats?) {
        if (stats == null) return
        syncPrefs.edit()
            .putInt(KEY_CACHED_MSG_COUNT, stats.totalMessages)
            .putInt(KEY_CACHED_MSG_AUDIO, stats.totalWithAudio)
            .putInt(KEY_CACHED_MSG_ASSIGNMENTS, stats.totalAssignments)
            .apply()
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
            val variants = exercise.poseVariants
            if (variants.isEmpty()) return@map exercise
            val resolvedVariants = variants.map { variant ->
                resolvePoseVariantMessages(variant, messageMap)
            }
            exercise.copy(poseVariants = resolvedVariants)
        }
    }

    private fun resolvePoseVariantMessages(
        variant: PoseVariant,
        messageMap: Map<String, MessageTemplate>
    ): PoseVariant {
        // Gson can set list fields to null when JSON has explicit null, bypassing Kotlin defaults
        val v = variant.sanitizeGsonDefaults()
        val assignments = v.messageAssignments.sortedBy { it.sortOrder }

        if (assignments.isEmpty()) return v

        val motivational = v.feedbackMessages.motivational.toMutableList()
        val tips = v.feedbackMessages.tips.toMutableList()

        val trackedJoints = v.trackedJoints
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

        val positionChecks = v.positionChecks
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
            v.feedbackMessages
        }

        return v.copy(
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
     * Parse ISO timestamp string to milliseconds epoch.
     */
    private fun parseIsoTimestamp(iso: String): Long {
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            sdf.parse(iso)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            try {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                sdf.parse(iso)?.time ?: System.currentTimeMillis()
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
        }
    }

    /**
     * Check if we should skip sync due to recent attempt
     */
    private fun shouldSkipSync(): Boolean {
        val last = lastSyncAttemptMs.get()
        if (last == 0L) return false
        val elapsed = System.currentTimeMillis() - last
        return elapsed < MIN_SYNC_INTERVAL_MS
    }
    
    // ==================== Offline Queue Sync ====================

    /**
     * Flush pending session reports that were queued while offline.
     * Call this after a successful sync or when network becomes available.
     *
     * @return Number of reports successfully synced
     */
    suspend fun flushPendingSessionReports(): Int = withContext(Dispatchers.IO) {
        val reportStore = ProgramSessionReportStore(context)
        val pendingQueue = reportStore.getPendingSyncQueue()

        if (pendingQueue.isEmpty()) {
            Log.d(TAG, "No pending session reports to sync")
            return@withContext 0
        }

        val authHeader = AuthManager.getAuthHeader(context)
        if (authHeader == null) {
            Log.w(TAG, "No auth token — cannot flush pending reports")
            return@withContext 0
        }

        var synced = 0
        for (entry in pendingQueue) {
            try {
                val payload = entry.payload
                val response = ApiClient.mobileSyncApi.completeSession(
                    entry.sessionId,
                    authHeader,
                    payload
                )
                if (response.isSuccessful) {
                    reportStore.removePendingSync(entry.sessionId)
                    synced++
                    Log.d(TAG, "Flushed pending report for session: ${entry.sessionId}")
                } else {
                    Log.w(TAG, "Failed to flush report for session ${entry.sessionId}: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to flush report for session ${entry.sessionId}: ${e.message}")
                // Don't remove from queue — will retry next time
            }
        }

        Log.d(TAG, "Flushed $synced / ${pendingQueue.size} pending session reports")
        return@withContext synced
    }

    // ==================== Status Methods ====================
    
    /**
     * Check if sync is currently in progress
     */
    fun isSyncInProgress(): Boolean = syncBusy.get()
    
    /**
     * Get sync status for UI display
     */
    fun getSyncStatus(): SyncStatus {
        val exerciseCacheStats = exerciseCache.getCacheStats()
        val workoutCacheStats = workoutCache?.getCacheStats()
        val programCacheStats = programCache?.getCacheStats()
        
        return SyncStatus(
            hasExercises = exerciseCache.hasExercises(),
            exerciseCount = exerciseCacheStats.exerciseCount,
            hasWorkouts = workoutCache?.hasWorkouts() ?: false,
            workoutCount = workoutCacheStats?.workoutCount ?: 0,
            hasPrograms = programCache?.hasPrograms() ?: false,
            programCount = programCacheStats?.programCount ?: 0,
            lastSyncTimestamp = exerciseCacheStats.lastSyncTimestamp,
            serverVersion = exerciseCacheStats.serverVersion,
            isSyncing = syncBusy.get()
        )
    }
    
    data class SyncStatus(
        val hasExercises: Boolean,
        val exerciseCount: Int,
        val hasWorkouts: Boolean = false,
        val workoutCount: Int = 0,
        val hasPrograms: Boolean = false,
        val programCount: Int = 0,
        val lastSyncTimestamp: String?,
        val serverVersion: String?,
        val isSyncing: Boolean
    )
}
