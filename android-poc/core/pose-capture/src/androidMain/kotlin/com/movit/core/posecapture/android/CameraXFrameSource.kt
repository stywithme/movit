package com.movit.core.posecapture.android

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.movit.core.training.boundary.CameraFrameSource
import com.movit.core.training.boundary.CameraSourceConfiguration
import com.movit.core.training.boundary.PoseDetectorConfiguration
import com.movit.core.training.geometry.PoseFrameAssembler
import com.movit.core.training.model.PoseFrame
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class CameraXFrameSource(
    private val context: Context,
    private val poseDetector: MediaPipePoseDetector,
) : CameraFrameSource {
    companion object {
        private const val TAG = "CameraXFrameSource"
    }

    private var previewView: PreviewView? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var frameListener: ((PoseFrame?) -> Unit)? = null
    private var configuration = CameraSourceConfiguration()
    private val executor = Executors.newSingleThreadExecutor()
    private val debugFpsEnabled = AtomicBoolean(false)
    private var frameCount = 0
    private var fpsWindowStartMs = 0L

    fun bindPreview(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        this.previewView = previewView
        this.lifecycleOwner = lifecycleOwner
    }

    fun setDebugFpsEnabled(enabled: Boolean) {
        debugFpsEnabled.set(enabled)
    }

    override fun setFrameListener(listener: ((PoseFrame?) -> Unit)?) {
        frameListener = listener
    }

    override fun start(configuration: CameraSourceConfiguration) {
        this.configuration = configuration
        val preview = previewView ?: run {
            Log.e(TAG, "Preview not bound")
            return
        }
        val owner = lifecycleOwner ?: run {
            Log.e(TAG, "LifecycleOwner not bound")
            return
        }
        poseDetector.setListener(object : MediaPipePoseDetector.Listener {
            override fun onPoseDetected(result: MediaPipePoseDetector.DetectionResult) {
                val frame = PoseFrameAssembler.assemble(
                    landmarks = result.landmarks,
                    timestampMs = result.timestampMs,
                    isFrontCamera = result.isFrontCamera,
                    worldLandmarks = result.worldLandmarks,
                )
                maybeLogFps(result.inferenceTimeMs)
                frameListener?.invoke(frame)
            }

            override fun onNoPoseDetected() {
                frameListener?.invoke(null)
            }

            override fun onError(message: String) {
                Log.e(TAG, message)
            }
        })
        poseDetector.warmUp(
            PoseDetectorConfiguration(useGpu = true),
        )
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                cameraProvider = providerFuture.get()
                bindUseCases(preview, owner, configuration.useFrontCamera)
            } catch (e: Exception) {
                Log.e(TAG, "Camera start failed: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindUseCases(previewView: PreviewView, owner: LifecycleOwner, useFrontCamera: Boolean) {
        val provider = cameraProvider ?: return
        val selector = CameraSelector.Builder()
            .requireLensFacing(
                if (useFrontCamera) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK,
            )
            .build()
        val rotation = previewView.display?.rotation ?: android.view.Surface.ROTATION_0
        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .build()
        val preview = Preview.Builder()
            .setResolutionSelector(resolutionSelector)
            .setTargetRotation(rotation)
            .build()
            .also { it.surfaceProvider = previewView.surfaceProvider }
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector)
            .setTargetRotation(rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { imageAnalysis ->
                imageAnalysis.setAnalyzer(executor) { proxy ->
                    poseDetector.detectAsync(proxy, useFrontCamera)
                }
            }
        provider.unbindAll()
        provider.bindToLifecycle(owner, selector, preview, analysis)
    }

    override fun stop() {
        cameraProvider?.unbindAll()
        poseDetector.shutdown()
        poseDetector.setListener(null)
        executor.shutdown()
    }

    private fun maybeLogFps(inferenceMs: Long) {
        if (!debugFpsEnabled.get()) return
        frameCount++
        val now = System.currentTimeMillis()
        if (fpsWindowStartMs == 0L) fpsWindowStartMs = now
        if (now - fpsWindowStartMs >= 1000L) {
            Log.d(TAG, "FPS≈$frameCount inferenceMs=$inferenceMs")
            frameCount = 0
            fpsWindowStartMs = now
        }
    }
}
