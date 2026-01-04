package com.trainingvalidator.poc.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.AspectRatio
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
 * Updated based on Google's official MediaPipe sample:
 * https://github.com/google-ai-edge/mediapipe-samples
 * 
 * Key improvements:
 * - Uses 4:3 aspect ratio (closest to model's expected input)
 * - Uses target rotation for proper orientation
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
    private var imageAnalysis: ImageAnalysis? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    
    private var frameAnalyzer: ((ImageProxy) -> Unit)? = null

    /**
     * Start camera with specified lens facing
     * @param useFrontCamera true for front camera (selfie mode)
     */
    fun startCamera(
        useFrontCamera: Boolean = true,
        onFrameAvailable: (ImageProxy) -> Unit
    ) {
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

        // Camera selector
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(
                if (useFrontCamera) CameraSelector.LENS_FACING_FRONT 
                else CameraSelector.LENS_FACING_BACK
            )
            .build()

        // Get target rotation from preview view
        val rotation = previewView.display?.rotation ?: android.view.Surface.ROTATION_0

        // Create ResolutionSelector for 4:3 aspect ratio
        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .build()

        // Preview use case - Using 4:3 ratio (closest to MediaPipe models)
        val preview = Preview.Builder()
            .setResolutionSelector(resolutionSelector)
            .setTargetRotation(rotation)
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        // Image analysis use case for pose detection
        // Using RGBA_8888 format as required by MediaPipe
        imageAnalysis = ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector)
            .setTargetRotation(rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    // Pass every frame to analyzer (no frame skipping)
                    // MediaPipe handles its own frame timing internally
                    frameAnalyzer?.invoke(imageProxy)
                }
            }

        try {
            // Unbind previous use cases
            cameraProvider.unbindAll()

            // Bind use cases to camera
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )

            Log.d(TAG, "Camera use cases bound successfully with 4:3 aspect ratio")
        } catch (e: Exception) {
            Log.e(TAG, "Use case binding failed: ${e.message}")
        }
    }

    /**
     * Switch between front and back camera
     */
    fun switchCamera(useFrontCamera: Boolean) {
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
    fun isFrontCamera(): Boolean {
        return try {
            cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) == true
        } catch (e: Exception) {
            true
        }
    }
}
