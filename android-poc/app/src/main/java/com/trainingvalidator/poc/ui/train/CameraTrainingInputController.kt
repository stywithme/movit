package com.trainingvalidator.poc.ui.train

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.analysis.AngleCalculator
import com.trainingvalidator.poc.camera.CameraManager
import com.trainingvalidator.poc.pose.ModelType
import com.trainingvalidator.poc.pose.PoseLandmarkerHelper
import com.trainingvalidator.poc.pose.PoseResult
import com.trainingvalidator.poc.training.session.SessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.os.SystemClock

/**
 * Live camera + ML Kit pose path: [CameraManager], [PoseLandmarkerHelper], and pose→ViewModel
 * on a coalesced frame stream ([isProcessingPoseFrame]).
 */
class CameraTrainingInputController(
    private val host: TrainingActivity
) {
    private val tag: String
        get() = TrainingActivity.TAG

    var poseLandmarkerHelper: PoseLandmarkerHelper? = null
        private set

    private var cameraManager: CameraManager? = null

    @Volatile
    var isProcessingPoseFrame = false
        private set

    // FPS
    private var frameCount = 0
    private var lastFpsUpdateTime = System.currentTimeMillis()
    private var currentFps = 0

    fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            host,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Call after the user granted camera permission from [androidx.activity.result.ActivityResultCallback].
     */
    fun onPermissionGranted() {
        initializePoseDetection()
        initializeCamera()
    }

    fun onPermissionDeniedShowToastAndFinish() {
        Toast.makeText(
            host,
            host.getString(R.string.camera_permission_required),
            Toast.LENGTH_LONG
        ).show()
        host.finish()
    }

    private fun initializePoseDetection() {
        poseLandmarkerHelper = PoseLandmarkerHelper(
            context = host.applicationContext,
            listener = host
        )

        val modelType = if (host.currentModelType == "heavy") ModelType.HEAVY else ModelType.FULL
        host.lifecycleScope.launch(Dispatchers.IO) {
            poseLandmarkerHelper?.initialize(modelType = modelType, useGpu = true)
            host.lifecycleScope.launch(Dispatchers.Main) {
                if (poseLandmarkerHelper?.isReady() == true) {
                    Log.d(tag, "Pose detection ready")
                }
            }
        }
    }

    fun reinitializePoseDetector() {
        val modelType = if (host.currentModelType == "heavy") ModelType.HEAVY else ModelType.FULL
        host.lifecycleScope.launch(Dispatchers.IO) {
            poseLandmarkerHelper?.close()
            poseLandmarkerHelper?.initialize(modelType = modelType, useGpu = true)
            host.lifecycleScope.launch(Dispatchers.Main) {
                if (poseLandmarkerHelper?.isReady() == true) {
                    Log.d(tag, "Pose detector reinitialized with model: ${host.currentModelType}")
                }
            }
        }
    }

    private fun initializeCamera() {
        val b = host.binding
        cameraManager = CameraManager(
            context = host,
            lifecycleOwner = host,
            previewView = b.previewView
        )
        cameraManager?.startCamera(useFrontCamera = host.useFrontCamera) { imageProxy ->
            poseLandmarkerHelper?.detectPose(imageProxy, host.useFrontCamera)
        }
        b.skeletonOverlay.updateFrontCameraState(host.useFrontCamera)
        // Match previewView's FILL_CENTER crop so landmarks align with the fullscreen preview.
        b.skeletonOverlay.setScaleMode(fitCenter = false)
        b.skeletonOverlay.setFillCenterMode(enabled = true)
    }

    /**
     * Training settings dialog: flip front/back camera and keep smoothing + overlay in sync.
     */
    fun applySwitchCameraFromSettings() {
        host.useFrontCamera = !host.useFrontCamera
        cameraManager?.switchCamera(host.useFrontCamera)
        if (host.isLandmarkSmootherReady()) {
            host.landmarkSmoother.reset()
        }
        host.elbowAngleEstimator.reset()
        host.binding.skeletonOverlay.updateFrontCameraState(host.useFrontCamera)
    }

    private fun isSessionPanelOverlayVisible(): Boolean {
        val b = host.binding
        return b.sessionPreExercisePanel.visibility == View.VISIBLE ||
            b.sessionRestPanel.visibility == View.VISIBLE ||
            b.sessionCompletePanel.visibility == View.VISIBLE
    }

    /**
     * Forward from [com.trainingvalidator.poc.pose.PoseLandmarkerHelper.PoseDetectionListener.onPoseDetected].
     */
    fun onPoseDetected(result: PoseResult) {
        if (host.preferenceDialogs.isWeightDialogVisible) return
        if (host.isSessionMode && isSessionPanelOverlayVisible()) return
        if (isProcessingPoseFrame) return
        isProcessingPoseFrame = true

        host.lifecycleScope.launch(Dispatchers.Default) {
            try {
                if (!host.isLandmarkSmootherReady()) {
                    return@launch
                }
                val smoothedLandmarks = host.landmarkSmoother.smooth(result.landmarks, result.timestampMs)
                val worldLandmarks = result.worldLandmarks?.let {
                    host.landmarkSmoother.convertWorld(it, result.timestampMs)
                }
                val rawAngles = if (worldLandmarks != null) {
                    AngleCalculator.calculateAllAnglesSmoothed(
                        worldLandmarks,
                        visibilityThreshold = 0.5f,
                        use3D = true
                    )
                } else {
                    AngleCalculator.calculateAllAnglesSmoothed(
                        smoothedLandmarks,
                        visibilityThreshold = 0.5f
                    )
                }
                val correctedAngles = if (worldLandmarks != null) {
                    host.elbowAngleEstimator.correct(
                        rawAngles, worldLandmarks, smoothedLandmarks, result.timestampMs
                    )
                } else {
                    rawAngles
                }
                val angles = if (result.isFrontCamera) correctedAngles.mirrored() else correctedAngles

                withContext(Dispatchers.Main) {
                    updateFps()
                    host.wasPoseDetectedLastFrame = true
                    host.noPoseTtsSpoken = false
                    host.viewModel.onPoseFrame(
                        angles, smoothedLandmarks, result.isFrontCamera, result.timestampMs,
                        imageWidth = result.imageWidth, imageHeight = result.imageHeight
                    )
                    val stateInfos = host.viewModel.trainingEngine?.jointStateInfos?.value ?: emptyMap()
                    val positionErrors = host.viewModel.trainingEngine?.positionErrors?.value ?: emptyList()
                    val bilateralFlipped = host.viewModel.trainingEngine?.isBilateralFlipped ?: false
                    host.binding.skeletonOverlay.updateWithStateInfos(
                        smoothedLandmarks = smoothedLandmarks,
                        inputImageWidth = result.imageWidth,
                        inputImageHeight = result.imageHeight,
                        angles = angles,
                        stateInfos = stateInfos,
                        positionErrors = positionErrors,
                        bilateralFlipped = bilateralFlipped,
                        anySideDimmedJointCodes = host.viewModel.trainingEngine?.anySideDimmedJointCodes?.value
                            ?: emptySet()
                    )
                }
            } finally {
                isProcessingPoseFrame = false
            }
        }
    }

    /**
     * Forward from [com.trainingvalidator.poc.pose.PoseLandmarkerHelper.PoseDetectionListener.onNoPoseDetected].
     */
    fun onNoPoseDetected() {
        if (host.preferenceDialogs.isWeightDialogVisible) return
        if (host.isSessionMode && isSessionPanelOverlayVisible()) return

        host.lifecycleScope.launch(Dispatchers.Main) {
            updateFps()
            host.binding.skeletonOverlay.clear()
            if (host.wasPoseDetectedLastFrame && host.viewModel.supervisor.state.value == SessionState.TRAINING) {
                host.binding.glassmorphicMessage.hide()
                host.binding.vignetteOverlay.clear()
            }
            host.wasPoseDetectedLastFrame = false
            host.viewModel.onNoPoseDetected(SystemClock.uptimeMillis())
            if (host.viewModel.supervisor.state.value == SessionState.SETUP_POSE) {
                host.viewModel.poseSetupGuide.reset()
            }
        }
    }

    private fun updateFps() {
        frameCount++
        val currentTime = System.currentTimeMillis()
        val elapsed = currentTime - lastFpsUpdateTime
        if (elapsed >= 1000) {
            currentFps = frameCount
            frameCount = 0
            lastFpsUpdateTime = currentTime
            host.binding.tvFps.text = host.getString(R.string.fps_format, currentFps)
        }
    }

    fun onPoseError(message: String) {
        host.lifecycleScope.launch(Dispatchers.Main) {
            Toast.makeText(host, message, Toast.LENGTH_SHORT).show()
            Log.e(tag, "Pose detection error: $message")
        }
    }

    fun onDestroy() {
        cameraManager?.stopCamera()
        poseLandmarkerHelper?.close()
    }
}
