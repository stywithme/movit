package com.movit.legacy

import android.content.Context
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.movit.core.training.geometry.PoseFrameAssembler
import com.movit.core.training.model.Landmark
import com.movit.core.training.model.PoseFrame
import com.movit.feature.account.assessment.KmpAssessmentSession
import com.movit.feature.account.assessment.KmpAssessmentSessionFactory
import com.trainingvalidator.poc.analysis.LandmarkSmoother
import com.trainingvalidator.poc.camera.CameraManager
import com.trainingvalidator.poc.pose.ModelType
import com.trainingvalidator.poc.pose.PoseLandmarkerHelper
import com.trainingvalidator.poc.pose.PoseResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

object LegacyKmpAssessmentSessionFactory : KmpAssessmentSessionFactory {
    override fun create(
        hostContext: Any,
        lifecycleOwner: Any,
        onPoseFrame: (PoseFrame?) -> Unit,
        onError: (String) -> Unit,
    ): KmpAssessmentSession? {
        val context = hostContext as? Context ?: return null
        val owner = lifecycleOwner as? LifecycleOwner ?: return null
        return LegacyKmpAssessmentSession(context, owner, onPoseFrame, onError)
    }
}

private class LegacyKmpAssessmentSession(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onPoseFrame: (PoseFrame?) -> Unit,
    private val onError: (String) -> Unit,
) : KmpAssessmentSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var cameraManager: CameraManager? = null
    private var poseHelper: PoseLandmarkerHelper? = null
    private val landmarkSmoother = LandmarkSmoother()
    private var previewView: PreviewView? = null
    private var useFrontCamera = true

    override fun bindPreview(surface: Any) {
        previewView = surface as? PreviewView
    }

    override fun start() {
        val preview = previewView ?: run {
            onError("Camera preview is not ready.")
            return
        }
        poseHelper = PoseLandmarkerHelper(
            context = context.applicationContext,
            listener = object : PoseLandmarkerHelper.PoseDetectionListener {
                override fun onPoseDetected(result: PoseResult) {
                    scope.launch(Dispatchers.Default) {
                        val smoothed = landmarkSmoother.smooth(
                            result.landmarks,
                            result.timestampMs,
                        ).map { s ->
                            Landmark(s.x, s.y, s.z, s.visibility, s.presence)
                        }
                        val frame = PoseFrameAssembler.assemble(
                            landmarks = smoothed,
                            timestampMs = result.timestampMs,
                            isFrontCamera = result.isFrontCamera,
                        )
                        onPoseFrame(frame)
                    }
                }

                override fun onNoPoseDetected() {
                    onPoseFrame(null)
                }

                override fun onError(message: String) {
                    onError(message)
                }
            },
        )
        scope.launch(Dispatchers.IO) {
            poseHelper?.initialize(modelType = ModelType.FULL, useGpu = true)
            scope.launch(Dispatchers.Main) {
                cameraManager = CameraManager(
                    context = context,
                    lifecycleOwner = lifecycleOwner,
                    previewView = preview,
                )
                cameraManager?.startCamera(useFrontCamera = useFrontCamera) { imageProxy ->
                    poseHelper?.detectPose(imageProxy, useFrontCamera)
                }
            }
        }
    }

    override fun close() {
        scope.cancel()
        cameraManager?.stopCamera()
        cameraManager = null
        poseHelper?.close()
        poseHelper = null
        previewView = null
    }
}
