package com.movit.core.posecapture.android

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.movit.core.posecapture.boundary.trainingdebug.PoseModelType
import com.movit.core.posecapture.boundary.trainingdebug.PoseModelTypePort
import com.movit.core.posecapture.boundary.trainingdebug.ResolvedPoseModel
import com.movit.core.training.boundary.PoseDetectorConfiguration
import java.io.File

internal object PoseModelResolver {
    private const val TAG = "PoseModelResolver"

    fun resolve(
        context: Context,
        baseBuilder: BaseOptions.Builder,
        configuration: PoseDetectorConfiguration,
        modelPort: PoseModelTypePort,
        requestedOverride: PoseModelType? = null,
    ): ResolvedPoseModel {
        configuration.modelAssetName?.let { explicitAsset ->
            baseBuilder.setModelAssetPath(explicitAsset)
            val requested = requestedOverride ?: modelPort.getSelectedModel()
            return ResolvedPoseModel(
                requestedType = requested,
                resolvedAssetLabel = explicitAsset,
                displayLabel = explicitAsset,
                usesHeavyFallback = false,
                scheduleHeavyUpgrade = false,
            )
        }

        val requested = requestedOverride ?: modelPort.getSelectedModel()
        if (requested == PoseModelType.FULL) {
            baseBuilder.setModelAssetPath(ResolvedPoseModel.FULL_ASSET)
            return ResolvedPoseModel.fullBundled()
        }

        return when (val resolved = PoseLandmarkerHeavyModelStore.resolveHeavyOrFallback(context)) {
            is PoseLandmarkerHeavyModelStore.ResolveResult.HeavyFile -> {
                applyHeavyFileToBaseOptions(baseBuilder, File(resolved.absolutePath))
                ResolvedPoseModel(
                    requestedType = PoseModelType.HEAVY,
                    resolvedAssetLabel = resolved.absolutePath,
                    displayLabel = "Heavy",
                    usesHeavyFallback = false,
                    scheduleHeavyUpgrade = false,
                )
            }
            is PoseLandmarkerHeavyModelStore.ResolveResult.HeavyAsset -> {
                baseBuilder.setModelAssetPath(resolved.assetName)
                ResolvedPoseModel(
                    requestedType = PoseModelType.HEAVY,
                    resolvedAssetLabel = resolved.assetName,
                    displayLabel = "Heavy",
                    usesHeavyFallback = false,
                    scheduleHeavyUpgrade = false,
                )
            }
            is PoseLandmarkerHeavyModelStore.ResolveResult.FallbackFullAsset -> {
                baseBuilder.setModelAssetPath(resolved.assetName)
                Log.i(TAG, "Heavy model unavailable — using bundled full; download will run in background")
                ResolvedPoseModel(
                    requestedType = PoseModelType.HEAVY,
                    resolvedAssetLabel = resolved.assetName,
                    displayLabel = "Heavy → Full (fallback)",
                    usesHeavyFallback = true,
                    scheduleHeavyUpgrade = true,
                )
            }
        }
    }

    fun resolveForDebug(
        context: Context,
        baseBuilder: BaseOptions.Builder,
        modelPort: PoseModelTypePort,
        requested: PoseModelType,
    ): ResolvedPoseModel = resolve(
        context = context,
        baseBuilder = baseBuilder,
        configuration = PoseDetectorConfiguration(),
        modelPort = modelPort,
        requestedOverride = requested,
    )

    private fun applyHeavyFileToBaseOptions(baseBuilder: BaseOptions.Builder, modelFile: File) {
        ParcelFileDescriptor.open(modelFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            baseBuilder.setModelAssetFileDescriptor(pfd.detachFd())
        }
    }
}
