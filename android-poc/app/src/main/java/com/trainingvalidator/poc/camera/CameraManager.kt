package com.trainingvalidator.poc.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.util.Log
import android.util.Range
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * CameraManager - Handles CameraX setup and frame delivery
 *
 * Key features:
 * - Uses 4:3 aspect ratio (closest to model's expected input)
 * - Targets 60 FPS via Camera2CameraControl (session-level)
 * - Background executor for frame processing
 */
class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView
) {
    companion object {
        private const val TAG = "CameraManager"
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageAnalysis: ImageAnalysis? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var currentFacing: Boolean = true
    private var frameAnalyzer: ((ImageProxy) -> Unit)? = null

    /** Diagnostic info populated after binding. */
    var diagSupportedRanges: String = "N/A"; private set
    var diagAppliedRange: String = "N/A"; private set

    /**
     * Start camera with specified lens facing
     * @param useFrontCamera true for front camera (selfie mode)
     */
    fun startCamera(
        useFrontCamera: Boolean = true,
        onFrameAvailable: (ImageProxy) -> Unit
    ) {
        this.currentFacing = useFrontCamera
        this.frameAnalyzer = onFrameAvailable

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(useFrontCamera)
                Log.d(TAG, "Camera started successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start camera: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases(useFrontCamera: Boolean) {
        val cameraProvider = cameraProvider ?: return

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(
                if (useFrontCamera) CameraSelector.LENS_FACING_FRONT
                else CameraSelector.LENS_FACING_BACK
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
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        imageAnalysis = ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector)
            .setTargetRotation(rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    frameAnalyzer?.invoke(imageProxy)
                }
            }

        try {
            cameraProvider.unbindAll()

            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )

            applyHighFps()

            Log.d(TAG, "Camera use cases bound successfully with 4:3 aspect ratio")
        } catch (e: Exception) {
            Log.e(TAG, "Use case binding failed: ${e.message}")
        }
    }

    /**
     * After binding, set the highest supported FPS range on the camera session.
     * This affects ALL use cases (Preview + ImageAnalysis) together.
     */
    @androidx.camera.camera2.interop.ExperimentalCamera2Interop
    private fun applyHighFps() {
        val cam = camera ?: return

        try {
            val camera2Info = Camera2CameraInfo.from(cam.cameraInfo)
            val supportedRanges: Array<Range<Int>>? = camera2Info.getCameraCharacteristic(
                CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES
            )

            diagSupportedRanges = supportedRanges?.joinToString { "[${it.lower},${it.upper}]" } ?: "null"
            Log.d(TAG, "Supported FPS ranges: $diagSupportedRanges")

            val bestRange = supportedRanges
                ?.sortedWith(compareByDescending<Range<Int>> { it.upper }.thenByDescending { it.lower })
                ?.firstOrNull()

            if (bestRange != null) {
                diagAppliedRange = "[${bestRange.lower},${bestRange.upper}]"
                val camera2Control = Camera2CameraControl.from(cam.cameraControl)
                camera2Control.setCaptureRequestOptions(
                    CaptureRequestOptions.Builder()
                        .setCaptureRequestOption(
                            CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                            bestRange
                        )
                        .build()
                )
                Log.d(TAG, "Applied FPS range: $diagAppliedRange")
            } else {
                diagAppliedRange = "none"
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply high FPS: ${e.message}")
        }
    }

    /**
     * Switch between front and back camera
     */
    fun switchCamera(useFrontCamera: Boolean) {
        currentFacing = useFrontCamera
        frameAnalyzer?.let {
            cameraProvider?.unbindAll()
            bindCameraUseCases(useFrontCamera)
        }
    }

    /**
     * Stop camera and release resources
     */
    fun stopCamera() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        Log.d(TAG, "Camera stopped")
    }

    /**
     * Get current camera facing
     */
    fun isFrontCamera(): Boolean = currentFacing
}
