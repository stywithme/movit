package com.trainingvalidator.poc.storage

import android.content.Context
import android.util.Log
import com.trainingvalidator.poc.network.ApiClient
import com.trainingvalidator.poc.network.ApiConfig
import com.trainingvalidator.poc.network.AudioManifest
import com.trainingvalidator.poc.training.feedback.SystemMessageRegistry
import com.trainingvalidator.poc.training.models.ExerciseConfig
import com.trainingvalidator.poc.training.models.LocalizedText
import com.trainingvalidator.poc.training.models.PoseVariant
import com.trainingvalidator.poc.training.models.StateMessageValue
import com.trainingvalidator.poc.training.models.StateMessages
import com.trainingvalidator.poc.training.models.TrackedJoint
import com.trainingvalidator.poc.training.models.WorkoutConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Collects audio URLs referenced by merged exercise config (post-sync).
 */
object AudioUrlCollector {

    fun collectFromExercise(config: ExerciseConfig): Set<String> {
        val out = linkedSetOf<String>()
        for (v in config.poseVariants) {
            collectFromPoseVariant(v, out)
        }
        return out
    }

    fun collectFromWorkout(config: WorkoutConfig, exerciseRepo: ExerciseRepository): Set<String> {
        val out = linkedSetOf<String>()
        out.addAll(collectFromSystemRegistry())
        for (we in config.exercises) {
            val ex = exerciseRepo.getExercise(we.exercise) ?: continue
            out.addAll(collectFromExercise(ex))
        }
        return out
    }

    fun collectFromSystemRegistry(): Set<String> {
        val out = linkedSetOf<String>()
        for (lt in SystemMessageRegistry.getAllSyncedContents()) {
            addLocalizedAudio(lt, out)
        }
        return out
    }

    private fun collectFromPoseVariant(variant: PoseVariant, out: MutableSet<String>) {
        val fm = variant.feedbackMessages
        for (lt in fm.motivational) addLocalizedAudio(lt, out)
        for (lt in fm.tips) addLocalizedAudio(lt, out)
        for (pc in variant.positionChecks) {
            addLocalizedAudio(pc.errorMessage, out)
        }
        for (j in variant.trackedJoints) {
            collectFromTrackedJoint(j, out)
        }
    }

    private fun collectFromTrackedJoint(joint: TrackedJoint, out: MutableSet<String>) {
        collectFromStateMessages(joint.stateMessages, out)
        joint.phaseStateMessages?.values?.forEach { sm ->
            collectFromStateMessages(sm, out)
        }
    }

    private fun collectFromStateMessages(sm: StateMessages?, out: MutableSet<String>) {
        if (sm == null) return
        listOf(sm.perfect, sm.normal, sm.pad, sm.warning, sm.danger).forEach { v ->
            collectFromStateMessageValue(v, out)
        }
    }

    private fun collectFromStateMessageValue(v: StateMessageValue?, out: MutableSet<String>) {
        when (v) {
            is StateMessageValue.Single -> addLocalizedAudio(v.message, out)
            is StateMessageValue.ZoneSpecific -> {
                v.up?.let { addLocalizedAudio(it, out) }
                v.down?.let { addLocalizedAudio(it, out) }
            }
            null -> {}
        }
    }

    private fun addLocalizedAudio(lt: LocalizedText?, out: MutableSet<String>) {
        if (lt == null) return
        lt.audioAr?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
        lt.audioEn?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
    }
}

/**
 * Fetches per-entity audio manifests when local cache is missing files, then downloads in background.
 */
class EntityAudioPrefetchManager(private val context: Context) {

    companion object {
        private const val TAG = "EntityAudioPrefetch"
        private val mutexByKey = ConcurrentHashMap<String, Mutex>()
        private fun mutex(key: String): Mutex = mutexByKey.getOrPut(key) { Mutex() }

        private fun resolveEffectiveAudioBase(manifest: AudioManifest): String {
            val raw = manifest.baseUrl.trim()
            val api = ApiConfig.getEffectiveBaseUrl().trimEnd('/')
            if (raw.isEmpty()) return api
            if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
                return raw.trimEnd('/')
            }
            val path = raw.trim().trimEnd('/')
            return if (path.startsWith("/")) api + path else "$api/$path"
        }
    }

    private val audioCache: AudioCacheManager get() = ExerciseRepository.getInstance(context).getAudioCache()

    suspend fun prefetchExerciseIfNeeded(slug: String) {
        val key = "ex:$slug"
        mutex(key).withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    val cfg = ExerciseRepository.getInstance(context).getExercise(slug) ?: return@withContext
                    val urls = AudioUrlCollector.collectFromExercise(cfg) + AudioUrlCollector.collectFromSystemRegistry()
                    if (!shouldAskBackend(urls)) return@withContext
                    val auth = AuthManager.getAuthHeader(context)
                    val resp = ApiClient.mobileSyncApi.getExerciseAudioManifest(slug, auth)
                    if (!resp.isSuccessful || resp.body()?.success != true) return@withContext
                    val man = resp.body()?.data?.audioManifest ?: return@withContext
                    if (man.files.isEmpty()) return@withContext
                    val base = resolveEffectiveAudioBase(man)
                    audioCache.mergePartialPersistedManifest(base, man)
                    val n = audioCache.downloadAudioFiles(man.files, base, null)
                    if (n > 0) Log.d(TAG, "Exercise $slug: downloaded $n audio file(s)")
                }.onFailure { e ->
                    Log.w(TAG, "prefetchExerciseIfNeeded($slug): ${e.message}", e)
                }
            }
        }
    }

    suspend fun prefetchWorkoutIfNeeded(workoutSlug: String, workoutConfig: WorkoutConfig) {
        val key = "wk:$workoutSlug"
        mutex(key).withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    val repo = ExerciseRepository.getInstance(context)
                    val urls = AudioUrlCollector.collectFromWorkout(workoutConfig, repo)
                    if (!shouldAskBackend(urls)) return@withContext
                    val auth = AuthManager.getAuthHeader(context)
                    val resp = ApiClient.mobileSyncApi.getWorkoutAudioManifest(workoutSlug, auth)
                    if (!resp.isSuccessful || resp.body()?.success != true) return@withContext
                    val man = resp.body()?.data?.audioManifest ?: return@withContext
                    if (man.files.isEmpty()) return@withContext
                    val base = resolveEffectiveAudioBase(man)
                    audioCache.mergePartialPersistedManifest(base, man)
                    val n = audioCache.downloadAudioFiles(man.files, base, null)
                    if (n > 0) Log.d(TAG, "Workout $workoutSlug: downloaded $n audio file(s)")
                }.onFailure { e ->
                    Log.w(TAG, "prefetchWorkoutIfNeeded($workoutSlug): ${e.message}", e)
                }
            }
        }
    }

    private fun shouldAskBackend(urls: Set<String>): Boolean {
        if (urls.isEmpty()) return true
        return urls.any { u ->
            val fn = u.substringAfterLast("/")
            fn.isNotBlank() && !audioCache.hasAudio(fn)
        }
    }
}
