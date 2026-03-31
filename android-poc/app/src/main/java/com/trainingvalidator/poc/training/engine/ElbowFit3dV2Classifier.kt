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
 * FIT3D v2 elbow correction model (38 features, residual mode).
 *
 * In residual mode the model predicts a correction delta:
 *   final_angle = ang3d_feature * 180 + model_output * 180
 *
 * In absolute mode (sigmoid output):
 *   final_angle = sigmoid_output * 180
 */
class ElbowFit3dV2Classifier private constructor(
    private val interpreter: Interpreter,
    private val mean: FloatArray,
    private val std: FloatArray,
    private val residualMode: Boolean,
    private val ang3dFeatureIdx: Int,
) {

    data class TimedElbowResult(
        val leftAngle: Float?,
        val rightAngle: Float?,
        val leftFeatures: FloatArray?,
        val rightFeatures: FloatArray?,
        val latencyMicros: Long,
    )

    private fun predictAngle(rawFeatures: FloatArray): Float {
        val normalized = FloatArray(rawFeatures.size) { i -> (rawFeatures[i] - mean[i]) / std[i] }

        val inputBuf = ByteBuffer.allocateDirect(4 * normalized.size).apply {
            order(ByteOrder.nativeOrder())
            for (v in normalized) putFloat(v)
            rewind()
        }
        val outBuf = ByteBuffer.allocateDirect(4).apply {
            order(ByteOrder.nativeOrder())
        }

        interpreter.run(inputBuf, outBuf)
        outBuf.rewind()
        val rawOutput = outBuf.float

        return if (residualMode) {
            val ang3dNorm = rawFeatures[ang3dFeatureIdx]
            ang3dNorm * 180f + rawOutput * 180f
        } else {
            rawOutput * 180f
        }
    }

    fun predictBothElbows(
        normLandmarks: List<SmoothedLandmark>,
        worldLandmarks: List<SmoothedLandmark>,
    ): TimedElbowResult {
        val t0 = System.nanoTime()

        val rawRight = ElbowFit3dV2FeatureExtractor.computeFeatures(normLandmarks, worldLandmarks, "right")
        val rawLeft = ElbowFit3dV2FeatureExtractor.computeFeatures(normLandmarks, worldLandmarks, "left")

        val rightAngle = rawRight?.let { predictAngle(it) }
        val leftAngle = rawLeft?.let { predictAngle(it) }

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
        private const val TAG = "ElbowFit3dV2"
        const val ASSET_MODEL = "elbow_fit3d_v2.tflite"
        const val ASSET_NORM = "elbow_fit3d_v2_norm.json"

        private val gson = Gson()

        @Volatile private var instance: ElbowFit3dV2Classifier? = null
        @Volatile private var loadAttempted = false
        @Volatile var lastError: String? = null; private set

        fun getOrNull(context: Context): ElbowFit3dV2Classifier? {
            if (loadAttempted) return instance
            synchronized(this) {
                if (loadAttempted) return instance
                instance = tryCreate(context.applicationContext)
                loadAttempted = true
                return instance
            }
        }

        fun reload(context: Context): ElbowFit3dV2Classifier? {
            synchronized(this) {
                instance?.close()
                instance = null
                loadAttempted = false
                lastError = null
            }
            return getOrNull(context)
        }

        private fun tryCreate(context: Context): ElbowFit3dV2Classifier? {
            lastError = null
            return try {
                val normJson = context.assets.open(ASSET_NORM).bufferedReader().use { it.readText() }
                val cfg = gson.fromJson(normJson, NormConfig::class.java)
                val expectedCount = ElbowFit3dV2FeatureExtractor.FEATURE_COUNT
                if (cfg.featureCount != expectedCount) {
                    lastError = "Norm feature_count ${cfg.featureCount} != $expectedCount"
                    Log.w(TAG, lastError!!)
                    return null
                }
                val mean = cfg.mean.toFloatArray()
                val std = cfg.std.toFloatArray()
                if (mean.size != expectedCount || std.size != expectedCount) {
                    lastError = "Norm arrays size mismatch: mean=${mean.size} std=${std.size}"
                    Log.w(TAG, lastError!!)
                    return null
                }

                val modelBuffer = loadModelFile(context, ASSET_MODEL)
                val interpreter = Interpreter(modelBuffer, Interpreter.Options().apply {
                    setNumThreads(2)
                })

                val residualMode = cfg.residualMode ?: false
                val ang3dIdx = cfg.ang3dFeatureIdx ?: 1

                Log.i(TAG, "FIT3D v2 model loaded (${modelBuffer.capacity()} bytes, residual=$residualMode)")
                ElbowFit3dV2Classifier(interpreter, mean, std, residualMode, ang3dIdx)
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
        @SerializedName("residual_mode") val residualMode: Boolean?,
        @SerializedName("ang3d_feature_idx") val ang3dFeatureIdx: Int?,
    )
}
