package com.trainingvalidator.poc.segmentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import com.trainingvalidator.poc.training.config.BackgroundEffectSettings
import com.trainingvalidator.poc.training.config.SettingsManager
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Applies portrait matting + background blur/tint to the report hero image only.
 *
 * Engine priority: configured engine → ONNX fallback (u2net/modnet) → MediaPipe selfie → original.
 */
class ReportBackgroundEffectProcessor(
    private val context: Context
) {
    companion object {
        private const val TAG = "ReportBackgroundEffect"
        private const val FALLBACK_MEDIAPIPE_MODEL = "selfie_segmenter.tflite"
        private const val CACHE_VERSION = 1
        private const val MAX_CACHE_FILES = 12
    }

    fun apply(sourceFile: File): Bitmap? {
        if (!sourceFile.exists()) return null

        val settings = SettingsManager.getBackgroundEffectSettings()
        if (!settings.enabled) {
            return BitmapFactory.decodeFile(sourceFile.absolutePath)
        }

        val cacheFile = cacheFileFor(sourceFile, settings)
        loadCachedBitmap(cacheFile)?.let { cached ->
            Log.d(TAG, "Loaded cached report background effect: ${cacheFile.name}")
            return cached
        }

        val decoded = BitmapFactory.decodeFile(sourceFile.absolutePath) ?: return null
        val output = apply(decoded, settings)
        saveCachedBitmap(cacheFile, output)
        return output
    }

    fun apply(source: Bitmap): Bitmap {
        val settings = SettingsManager.getBackgroundEffectSettings()
        return apply(source, settings)
    }

    private fun apply(source: Bitmap, settings: BackgroundEffectSettings): Bitmap {
        if (!settings.enabled) return source

        val input = ensureArgb8888(source)
        val startMs = SystemClock.uptimeMillis()
        val primaryEngine = MattingEngine.fromSettings(settings.mattingEngine)

        val attempts = buildAttemptChain(primaryEngine, settings)
        for ((engine, modelAsset, inputSize) in attempts) {
            val mask = runMatting(engine, modelAsset, inputSize, settings, input) ?: continue
            val output = ReportImageCompositor.composite(input, mask, settings)
            Log.d(
                TAG,
                "Report background effect applied in ${SystemClock.uptimeMillis() - startMs}ms " +
                    "engine=$engine model=$modelAsset inputSize=$inputSize " +
                    "image=${input.width}x${input.height} mask=${mask.width}x${mask.height} " +
                    "blur=${settings.blurRadius} tintColor=${settings.tintColor} tintAlpha=${settings.tintAlpha}"
            )
            return output
        }

        Log.w(TAG, "All matting engines failed; returning original hero image")
        return input
    }

    private fun cacheFileFor(sourceFile: File, settings: BackgroundEffectSettings): File {
        val cacheDir = File(context.cacheDir, "report_hero_effects").apply { mkdirs() }
        val cacheKey = buildString {
            append("v=").append(CACHE_VERSION)
            append("|path=").append(sourceFile.absolutePath)
            append("|modified=").append(sourceFile.lastModified())
            append("|length=").append(sourceFile.length())
            append("|engine=").append(settings.mattingEngine)
            append("|model=").append(settings.modelAsset)
            append("|inputSize=").append(settings.inputSize)
            append("|indexes=").append(settings.personCategoryIndexes.joinToString(","))
            append("|threshold=").append(settings.personThreshold)
            append("|blur=").append(settings.blurRadius)
            append("|tintColor=").append(settings.tintColor)
            append("|tintAlpha=").append(settings.tintAlpha)
        }
        return File(cacheDir, "hero_${sha256(cacheKey)}.png")
    }

    private fun loadCachedBitmap(cacheFile: File): Bitmap? {
        if (!cacheFile.exists()) return null
        return BitmapFactory.decodeFile(cacheFile.absolutePath) ?: run {
            cacheFile.delete()
            null
        }
    }

    private fun saveCachedBitmap(cacheFile: File, bitmap: Bitmap) {
        try {
            val cacheDir = cacheFile.parentFile ?: return
            cacheDir.mkdirs()
            val tempFile = File(cacheDir, "${cacheFile.name}.tmp")
            FileOutputStream(tempFile).use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    tempFile.delete()
                    return
                }
            }
            if (cacheFile.exists()) cacheFile.delete()
            if (!tempFile.renameTo(cacheFile)) {
                tempFile.delete()
                return
            }
            pruneCache(cacheDir)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cache report background effect: ${e.message}", e)
        }
    }

    private fun pruneCache(cacheDir: File) {
        val files = cacheDir.listFiles { file ->
            file.isFile && file.name.startsWith("hero_") && file.name.endsWith(".png")
        } ?: return
        files
            .sortedByDescending { it.lastModified() }
            .drop(MAX_CACHE_FILES)
            .forEach { it.delete() }
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    private data class MattingAttempt(
        val engine: MattingEngine,
        val modelAsset: String,
        val inputSize: Int
    )

    private fun buildAttemptChain(
        primary: MattingEngine,
        settings: BackgroundEffectSettings
    ): List<MattingAttempt> {
        val modelAsset = settings.modelAsset.ifBlank {
            MattingEngine.defaultModelAsset(primary)
        }
        val inputSize = if (settings.inputSize > 0) {
            settings.inputSize
        } else {
            MattingEngine.defaultInputSize(primary)
        }

        val chain = linkedSetOf<MattingAttempt>()
        chain += MattingAttempt(primary, modelAsset, inputSize)

        if (primary != MattingEngine.MODNET) {
            chain += MattingAttempt(
                MattingEngine.MODNET,
                MattingEngine.defaultModelAsset(MattingEngine.MODNET),
                MattingEngine.defaultInputSize(MattingEngine.MODNET)
            )
        }
        if (primary != MattingEngine.U2NET) {
            chain += MattingAttempt(
                MattingEngine.U2NET,
                MattingEngine.defaultModelAsset(MattingEngine.U2NET),
                MattingEngine.defaultInputSize(MattingEngine.U2NET)
            )
        }
        chain += MattingAttempt(
            MattingEngine.MEDIAPIPE,
            FALLBACK_MEDIAPIPE_MODEL,
            MattingEngine.defaultInputSize(MattingEngine.MEDIAPIPE)
        )
        return chain.toList()
    }

    private fun runMatting(
        engine: MattingEngine,
        modelAsset: String,
        inputSize: Int,
        settings: BackgroundEffectSettings,
        input: Bitmap
    ): PortraitMask? {
        return try {
            when (engine) {
                MattingEngine.MODNET, MattingEngine.U2NET -> {
                    OnnxPortraitMatting(context, modelAsset, engine, inputSize).use { matting ->
                        matting.extractMask(input)
                    }
                }
                MattingEngine.MEDIAPIPE -> {
                    MediaPipePortraitMatting(
                        context,
                        modelAsset,
                        settings.personCategoryIndexes
                    ).use { matting ->
                        matting.extractMask(input)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Matting failed engine=$engine model=$modelAsset: ${e.message}", e)
            null
        }
    }

    private fun ensureArgb8888(source: Bitmap): Bitmap {
        return if (source.config == Bitmap.Config.ARGB_8888) {
            source
        } else {
            source.copy(Bitmap.Config.ARGB_8888, false) ?: source
        }
    }
}
