package com.trainingvalidator.poc.training.engine

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.trainingvalidator.poc.analysis.SmoothedLandmark
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Tiny TFLite MLP: 26 normalized elbow features -> sigmoid -> angle in [0,180] degrees.
 * Assets: [ASSET_MODEL], [ASSET_NORM].
 *
 * The model outputs a sigmoid value in [0,1] which is multiplied by 180 to get degrees.
 */
class ElbowCorrectionMlpClassifier private constructor(
    private val interpreter: Interpreter,
    private val mean: FloatArray,
    private val std: FloatArray,
) {

    data class ElbowPrediction(
        val angleDeg: Float,
        val rawSigmoid: Float,
    )

    data class TimedElbowResult(
        val leftAngle: Float?,
        val rightAngle: Float?,
        val leftFeatures: FloatArray?,
        val rightFeatures: FloatArray?,
        val latencyMicros: Long,
    )

    fun predictAngle(normalizedFeatures: FloatArray): ElbowPrediction {
        require(normalizedFeatures.size == ElbowMlpFeatureExtractor.FEATURE_COUNT)

        val inputBuf = ByteBuffer.allocateDirect(4 * ElbowMlpFeatureExtractor.FEATURE_COUNT).apply {
            order(ByteOrder.nativeOrder())
            for (v in normalizedFeatures) putFloat(v)
            rewind()
        }
        val outBuf = ByteBuffer.allocateDirect(4).apply {
            order(ByteOrder.nativeOrder())
        }

        interpreter.run(inputBuf, outBuf)
        outBuf.rewind()
        val sigmoid = outBuf.float
        return ElbowPrediction(sigmoid * 180f, sigmoid)
    }

    /**
     * Predict both elbows from landmarks in one call.
     * Returns null only if both sides fail feature extraction.
     */
    fun predictBothElbows(
        normLandmarks: List<SmoothedLandmark>,
        worldLandmarks: List<SmoothedLandmark>,
    ): TimedElbowResult {
        val t0 = System.nanoTime()

        val rawRight = ElbowMlpFeatureExtractor.computeFeatures(normLandmarks, worldLandmarks, "right")
        val rawLeft = ElbowMlpFeatureExtractor.computeFeatures(normLandmarks, worldLandmarks, "left")

        val rightAngle = rawRight?.let {
            val normalized = FloatArray(it.size) { i -> (it[i] - mean[i]) / std[i] }
            predictAngle(normalized).angleDeg
        }
        val leftAngle = rawLeft?.let {
            val normalized = FloatArray(it.size) { i -> (it[i] - mean[i]) / std[i] }
            predictAngle(normalized).angleDeg
        }

        val t1 = System.nanoTime()
        return TimedElbowResult(
            leftAngle = leftAngle,
            rightAngle = rightAngle,
            leftFeatures = rawLeft,
            rightFeatures = rawRight,
            latencyMicros = (t1 - t0) / 1000L,
        )
    }

    fun close() {
        interpreter.close()
    }

    companion object {
        private const val TAG = "ElbowCorrectionMlp"
        const val ASSET_MODEL = "elbow_correction_mlp.tflite"
        const val ASSET_NORM = "elbow_correction_mlp_norm.json"

        private val gson = Gson()

        @Volatile private var instance: ElbowCorrectionMlpClassifier? = null
        @Volatile private var loadAttempted = false
        @Volatile var lastError: String? = null; private set

        fun getOrNull(context: Context): ElbowCorrectionMlpClassifier? {
            if (loadAttempted) return instance
            synchronized(this) {
                if (loadAttempted) return instance
                instance = tryCreate(context.applicationContext)
                loadAttempted = true
                return instance
            }
        }

        fun reload(context: Context): ElbowCorrectionMlpClassifier? {
            synchronized(this) {
                instance?.close()
                instance = null
                loadAttempted = false
                lastError = null
            }
            return getOrNull(context)
        }

        private fun tryCreate(context: Context): ElbowCorrectionMlpClassifier? {
            lastError = null
            return try {
                val normJson = context.assets.open(ASSET_NORM).bufferedReader().use { it.readText() }
                val cfg = gson.fromJson(normJson, NormConfig::class.java)
                if (cfg.featureCount != ElbowMlpFeatureExtractor.FEATURE_COUNT) {
                    lastError = "Norm feature_count ${cfg.featureCount} != ${ElbowMlpFeatureExtractor.FEATURE_COUNT}"
                    Log.w(TAG, lastError!!)
                    return null
                }
                val mean = cfg.mean.toFloatArray()
                val std = cfg.std.toFloatArray()
                if (mean.size != ElbowMlpFeatureExtractor.FEATURE_COUNT ||
                    std.size != ElbowMlpFeatureExtractor.FEATURE_COUNT) {
                    lastError = "Norm arrays size mismatch: mean=${mean.size} std=${std.size}"
                    Log.w(TAG, lastError!!)
                    return null
                }

                val modelBuffer = loadModelFile(context, ASSET_MODEL)
                val interpreter = Interpreter(modelBuffer, Interpreter.Options().apply {
                    setNumThreads(2)
                })
                Log.i(TAG, "Elbow correction model loaded (${modelBuffer.capacity()} bytes)")
                ElbowCorrectionMlpClassifier(interpreter, mean, std)
            } catch (e: Exception) {
                lastError = "${e.javaClass.simpleName}: ${e.message}"
                Log.e(TAG, "Failed to load model: $lastError", e)
                null
            }
        }

        private fun loadModelFile(context: Context, assetName: String): ByteBuffer {
            return try {
                val fd = context.assets.openFd(assetName)
                fd.use {
                    FileInputStream(it.fileDescriptor).use { input ->
                        input.channel.map(FileChannel.MapMode.READ_ONLY, it.startOffset, it.declaredLength)
                    }
                }
            } catch (_: Exception) {
                Log.w(TAG, "Memory-map failed, falling back to byte copy")
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
