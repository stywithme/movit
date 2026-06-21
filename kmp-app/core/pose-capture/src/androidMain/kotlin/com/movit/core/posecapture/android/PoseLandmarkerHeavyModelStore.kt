package com.movit.core.posecapture.android

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

/**
 * On-demand cache for MediaPipe `pose_landmarker_heavy.task` (F8).
 *
 * Release APK ships only [FULL_MODEL_ASSET]; heavy is downloaded on first use when the user
 * selects the heavy model. Debug builds may still bundle heavy under `src/debug/assets/`.
 */
object PoseLandmarkerHeavyModelStore {
    private const val TAG = "PoseHeavyModel"

    const val MODEL_FILE_NAME = "pose_landmarker_heavy.task"
    const val FULL_MODEL_ASSET = "pose_landmarker_full.task"

    private const val DOWNLOAD_URL =
        "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_heavy/float16/latest/pose_landmarker_heavy.task"

    /** SHA-256 of the official float16 heavy bundle (verified at build-hygiene time). */
    private const val EXPECTED_SHA256 =
        "64437af838a65d18e5ba7a0d39b465540069bc8aae8308de3e318aad31fcbc7b"

    private val httpClient = OkHttpClient()

    sealed class ResolveResult {
        /** Absolute path for `BaseOptions.setModelAssetFileDescriptor`. */
        data class HeavyFile(val absolutePath: String) : ResolveResult()

        /** Debug-bundled heavy asset for `BaseOptions.setModelAssetPath`. */
        data class HeavyAsset(val assetName: String = MODEL_FILE_NAME) : ResolveResult()

        /** Bundled fallback when download/cache unavailable. */
        data class FallbackFullAsset(val assetName: String = FULL_MODEL_ASSET) : ResolveResult()
    }

    fun cacheFile(context: Context): File =
        File(context.filesDir, "pose_models/$MODEL_FILE_NAME")

    fun hasValidCache(context: Context): Boolean {
        val file = cacheFile(context)
        return file.isFile && verifySha256(file)
    }

    /**
     * Resolves heavy model path or falls back to the bundled full asset name.
     * Debug APK may serve heavy from assets without a network download.
     */
    fun resolveHeavyOrFallback(context: Context): ResolveResult {
        val cached = cacheFile(context)
        if (cached.isFile && verifySha256(cached)) {
            return ResolveResult.HeavyFile(cached.absolutePath)
        }
        if (assetExists(context, MODEL_FILE_NAME)) {
            return ResolveResult.HeavyAsset()
        }
        return ResolveResult.FallbackFullAsset()
    }

    /**
     * Downloads and verifies the heavy model into app-private storage.
     * @return true when a valid cached file is available after this call.
     */
    suspend fun ensureCached(context: Context): Boolean = withContext(Dispatchers.IO) {
        val target = cacheFile(context)
        if (target.isFile && verifySha256(target)) {
            return@withContext true
        }
        if (assetExists(context, MODEL_FILE_NAME)) {
            return@withContext true
        }

        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "$MODEL_FILE_NAME.part")
        try {
            downloadTo(temp)
            if (!verifySha256(temp)) {
                Log.w(TAG, "Checksum mismatch after download — discarding")
                temp.delete()
                return@withContext false
            }
            if (target.exists()) {
                target.delete()
            }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            Log.i(TAG, "Heavy pose model cached at ${target.absolutePath}")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Heavy model download failed: ${e.message}", e)
            temp.delete()
            false
        }
    }

    private fun assetExists(context: Context, name: String): Boolean =
        try {
            context.assets.open(name).close()
            true
        } catch (_: Exception) {
            false
        }

    private fun downloadTo(destination: File) {
        val request = Request.Builder().url(DOWNLOAD_URL).build()
        httpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            val body = response.body ?: error("Empty response body")
            destination.outputStream().use { out ->
                body.byteStream().use { input -> input.copyTo(out) }
            }
        }
    }

    private fun verifySha256(file: File): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var read = input.read(buffer)
            while (read > 0) {
                digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return actual.equals(EXPECTED_SHA256, ignoreCase = true)
    }
}
