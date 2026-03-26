package com.trainingvalidator.poc.training.engine

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Tiny TFLite MLP: 16 normalized skeleton features → 3-class softmax (standing/sitting/lying).
 * Assets: [ASSET_MODEL], [ASSET_NORM]. If either is missing, [tryCreate] returns null.
 */
class PostureMlpClassifier private constructor(
    private val interpreter: Interpreter,
    private val mean: FloatArray,
    private val std: FloatArray,
) {

    data class Prediction(
        val classIndex: Int,
        val probabilities: FloatArray,
        val confidence: Float,
    )

    /**
     * Latency breakdown for debug (microseconds). [classifierMicros] includes
     * normalization, buffer fill, and [Interpreter.run].
     */
    data class InferenceTimings(
        val featuresMicros: Long,
        val classifierMicros: Long,
    ) {
        val totalMicros: Long get() = featuresMicros + classifierMicros
    }

    fun predictNormalizedFeatures(normalizedRow: FloatArray): Prediction {
        require(normalizedRow.size == PostureMlpFeatureExtractor.FEATURE_COUNT)

        val inputBuf = ByteBuffer.allocateDirect(4 * PostureMlpFeatureExtractor.FEATURE_COUNT).apply {
            order(ByteOrder.nativeOrder())
            for (v in normalizedRow) putFloat(v)
            rewind()
        }
        val outBuf = ByteBuffer.allocateDirect(4 * NUM_CLASSES).apply {
            order(ByteOrder.nativeOrder())
        }

        interpreter.run(inputBuf, outBuf)
        outBuf.rewind()
        val probs = FloatArray(NUM_CLASSES) { outBuf.float }
        var best = 0
        for (i in 1 until NUM_CLASSES) {
            if (probs[i] > probs[best]) best = i
        }
        return Prediction(best, probs, probs[best])
    }

    fun predictFromLandmarks(landmarks: List<com.trainingvalidator.poc.analysis.SmoothedLandmark>): Prediction? {
        val raw = PostureMlpFeatureExtractor.computeFeatures(landmarks) ?: return null
        val normalized = FloatArray(raw.size) { i ->
            (raw[i] - mean[i]) / std[i]
        }
        return predictNormalizedFeatures(normalized)
    }

    /**
     * Same as [predictFromLandmarks] but returns per-stage timings and raw features (no second feature pass).
     */
    data class TimedPrediction(
        val prediction: Prediction,
        val timings: InferenceTimings,
        val rawFeatures: FloatArray,
    )

    fun predictFromLandmarksTimed(
        landmarks: List<com.trainingvalidator.poc.analysis.SmoothedLandmark>,
    ): TimedPrediction? {
        val t0 = System.nanoTime()
        val raw = PostureMlpFeatureExtractor.computeFeatures(landmarks) ?: return null
        val t1 = System.nanoTime()
        val normalized = FloatArray(raw.size) { i ->
            (raw[i] - mean[i]) / std[i]
        }
        val prediction = predictNormalizedFeatures(normalized)
        val t2 = System.nanoTime()
        val featuresMicros = (t1 - t0) / 1000L
        val classifierMicros = (t2 - t1) / 1000L
        return TimedPrediction(
            prediction = prediction,
            timings = InferenceTimings(featuresMicros, classifierMicros),
            rawFeatures = raw,
        )
    }

    fun close() {
        interpreter.close()
    }

    companion object {
        private const val TAG = "PostureMlpClassifier"
        const val ASSET_MODEL = "posture_mlp.tflite"
        const val ASSET_NORM = "posture_mlp_norm.json"
        private const val NUM_CLASSES = 3

        private val gson = Gson()

        @Volatile
        private var instance: PostureMlpClassifier? = null

        @Volatile
        private var loadAttempted = false

        /** Last error message from [tryCreate], visible in debug UI. */
        @Volatile
        var lastError: String? = null
            private set

        /**
         * Thread-safe singleton; loads once. Returns null if assets are absent or invalid.
         * After first attempt, never retries (avoids per-frame asset I/O when model is missing).
         * Call [reload] to force a re-attempt.
         */
        fun getOrNull(context: Context): PostureMlpClassifier? {
            if (loadAttempted) return instance
            synchronized(this) {
                if (loadAttempted) return instance
                instance = tryCreate(context.applicationContext)
                loadAttempted = true
                return instance
            }
        }

        /** Force a fresh load attempt (useful from debug UI). */
        fun reload(context: Context): PostureMlpClassifier? {
            synchronized(this) {
                instance?.close()
                instance = null
                loadAttempted = false
                lastError = null
            }
            return getOrNull(context)
        }

        /** For tests / hot-reload */
        fun clearInstance() {
            synchronized(this) {
                instance?.close()
                instance = null
                loadAttempted = false
                lastError = null
            }
        }

        fun tryCreate(context: Context): PostureMlpClassifier? {
            lastError = null
            return try {
                val normJson = context.assets.open(ASSET_NORM).bufferedReader().use { it.readText() }
                val cfg = gson.fromJson(normJson, NormConfig::class.java)
                if (cfg.featureCount != PostureMlpFeatureExtractor.FEATURE_COUNT) {
                    lastError = "Norm feature_count ${cfg.featureCount} != ${PostureMlpFeatureExtractor.FEATURE_COUNT}"
                    Log.w(TAG, lastError!!)
                    return null
                }
                val mean = cfg.mean.toFloatArray()
                val std = cfg.std.toFloatArray()
                if (mean.size != PostureMlpFeatureExtractor.FEATURE_COUNT ||
                    std.size != PostureMlpFeatureExtractor.FEATURE_COUNT
                ) {
                    lastError = "Norm arrays size mismatch: mean=${mean.size} std=${std.size}"
                    Log.w(TAG, lastError!!)
                    return null
                }

                val modelBuffer = loadModelFile(context, ASSET_MODEL)
                val interpreter = Interpreter(modelBuffer, Interpreter.Options().apply {
                    setNumThreads(2)
                })
                Log.i(TAG, "Model loaded successfully (${modelBuffer.capacity()} bytes)")
                PostureMlpClassifier(interpreter, mean, std)
            } catch (e: Exception) {
                lastError = "${e.javaClass.simpleName}: ${e.message}"
                Log.e(TAG, "Failed to load model: $lastError", e)
                null
            }
        }

        /**
         * Loads a TFLite model from assets. Tries memory-mapped (fast, requires
         * uncompressed asset via noCompress) and falls back to byte-buffer copy
         * if the asset is compressed.
         */
        private fun loadModelFile(context: Context, assetName: String): ByteBuffer {
            return try {
                val fd = context.assets.openFd(assetName)
                fd.use {
                    FileInputStream(it.fileDescriptor).use { input ->
                        input.channel.map(FileChannel.MapMode.READ_ONLY, it.startOffset, it.declaredLength)
                    }
                }
            } catch (_: Exception) {
                Log.w(TAG, "Memory-map failed (asset compressed?), falling back to byte copy")
                context.assets.open(assetName).use { stream ->
                    val bytes = stream.readBytes()
                    ByteBuffer.allocateDirect(bytes.size).apply {
                        order(ByteOrder.nativeOrder())
                        put(bytes)
                        rewind()
                    }
                }
            }
        }
    }

    private data class NormConfig(
        @SerializedName("feature_count") val featureCount: Int,
        val mean: List<Float>,
        val std: List<Float>,
    )
}
