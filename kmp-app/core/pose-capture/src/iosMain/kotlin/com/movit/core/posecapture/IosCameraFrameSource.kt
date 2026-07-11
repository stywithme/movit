package com.movit.core.posecapture

import com.movit.core.training.boundary.AdaptiveThroughputController
import com.movit.core.training.boundary.CameraFrameSource
import com.movit.core.training.boundary.CameraSourceConfiguration
import com.movit.core.training.boundary.PoseDetectorConfiguration
import com.movit.core.training.boundary.TrainingThroughputProfiles
import com.movit.core.training.diagnostics.TrainingPipelineDiagnostics
import com.movit.core.training.geometry.AngleModeStickyState
import com.movit.core.training.geometry.ElbowAngleEstimator
import com.movit.core.training.geometry.PoseFrameAssembler
import com.movit.core.training.model.PoseFrame
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceDiscoverySession
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDevicePositionFront
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPreset352x288
import platform.AVFoundation.AVCaptureSessionPreset640x480
import platform.AVFoundation.AVCaptureVideoDataOutput
import platform.AVFoundation.AVCaptureVideoDataOutputSampleBufferDelegateProtocol
import platform.AVFoundation.AVCaptureVideoOrientationPortrait
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreVideo.kCVPixelBufferPixelFormatTypeKey
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCATransactionDisableActions
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create

/**
 * AVFoundation camera source — 4:3 preset, front/back lens, live preview layer.
 * Pairs with [IosPoseDetector] + optional Swift [IosPoseLandmarkerBridge].
 */
@OptIn(ExperimentalForeignApi::class)
class IosCameraFrameSource(
    private val poseDetector: IosPoseDetector,
) : CameraFrameSource {
    private var captureSession: AVCaptureSession? = null
    private var previewLayer: AVCaptureVideoPreviewLayer? = null
    private var previewHost: UIView? = null
    private var videoOutput: AVCaptureVideoDataOutput? = null
    private var sampleDelegate: SampleBufferDelegate? = null
    private var frameListener: ((PoseFrame?) -> Unit)? = null
    private var errorListener: ((String) -> Unit)? = null
    private var onCameraBoundListener: (() -> Unit)? = null
    private var configuration = CameraSourceConfiguration()
    private var lastSubmittedFrameMs = 0L
    // Shared One-Euro smoothing — identical to the Android MediaPipe path so pose-frame inputs match.
    private val landmarkSmoother = PoseLandmarkSmoother()
    private val elbowAngleEstimator = ElbowAngleEstimator()
    private val angleModeStickyState = AngleModeStickyState()
    private val adaptiveThroughput = AdaptiveThroughputController()
    private val outputQueue = dispatch_queue_create("com.movit.pose-capture.video", null)

    override fun setFrameListener(listener: ((PoseFrame?) -> Unit)?) {
        frameListener = listener
    }

    override fun setErrorListener(listener: ((String) -> Unit)?) {
        errorListener = listener
    }

    override fun setOnCameraBoundListener(listener: (() -> Unit)?) {
        onCameraBoundListener = listener
    }

    override fun resetAngleTracking() {
        elbowAngleEstimator.reset()
        angleModeStickyState.reset()
    }

    private fun maybeAdaptiveDowngrade(inferenceMs: Float) {
        val current = TrainingThroughputProfiles.resolve(configuration.throughputProfileId)
        val next = adaptiveThroughput.onInferenceMs(inferenceMs, current) ?: return
        configuration = configuration.copy(
            targetFps = next.targetFps,
            analysisWidth = next.analysisWidth,
            analysisHeight = next.analysisHeight,
            throughputProfileId = next.id,
        )
        TrainingPipelineDiagnostics.logMilestone(
            "adaptive throughput ${current.id} -> ${next.id} (fps throttle; resolution on next rebind)",
        )
        TrainingPipelineDiagnostics.setCameraConfig(
            targetFps = next.targetFps,
            analysisWidth = next.analysisWidth,
            analysisHeight = next.analysisHeight,
            appliedFpsRange = "adaptive",
            throughputProfileId = next.id,
        )
    }

    fun attachPreview(host: UIView) {
        previewHost = host
        val session = captureSession ?: return
        val existing = previewLayer
        if (existing != null) {
            layoutPreview(host, existing)
            return
        }
        val layer = AVCaptureVideoPreviewLayer(session = session).apply {
            videoGravity = AVLayerVideoGravityResizeAspectFill
        }
        CATransaction.begin()
        CATransaction.setValue(true, kCATransactionDisableActions)
        host.layer.addSublayer(layer)
        CATransaction.commit()
        previewLayer = layer
        layoutPreview(host, layer)
    }

    fun updatePreviewLayout() {
        val host = previewHost ?: return
        val layer = previewLayer ?: return
        layoutPreview(host, layer)
    }

    override fun start(configuration: CameraSourceConfiguration) {
        this.configuration = configuration
        lastSubmittedFrameMs = 0L
        landmarkSmoother.reset()
        adaptiveThroughput.resetSession()
        resetAngleTracking()
        stopSessionOnly()
        val analysisW = configuration.analysisWidth.coerceAtLeast(1)
        val analysisH = configuration.analysisHeight.coerceAtLeast(1)
        poseDetector.setAnalysisImageSize(analysisW, analysisH)
        poseDetector.setListener(
            object : IosPoseDetector.Listener {
                override fun onPoseDetected(result: IosPoseDetector.DetectionResult) {
                    TrainingPipelineDiagnostics.recordPoseResult(
                        inferenceMs = result.inferenceTimeMs,
                        hasPose = true,
                        busySkippedSinceLastResult = poseDetector.consumeBusySkipCount(),
                    )
                    maybeAdaptiveDowngrade(result.inferenceTimeMs.toFloat())
                    val timestampMs = result.timestampMs
                    val smoothed = landmarkSmoother.smooth(result.landmarks, timestampMs)
                    val smoothedWorld = result.worldLandmarks
                        ?.let { landmarkSmoother.smoothWorld(it, timestampMs) }
                    val frame = PoseFrameAssembler.assemble(
                        landmarks = smoothed,
                        timestampMs = timestampMs,
                        isFrontCamera = result.isFrontCamera,
                        worldLandmarks = smoothedWorld,
                        analysisImageWidth = result.analysisImageWidth,
                        analysisImageHeight = result.analysisImageHeight,
                        applyElbowCorrection = configuration.applyElbowCorrection,
                        collectElbowDiagnostics = configuration.collectElbowDiagnostics,
                        estimator = elbowAngleEstimator,
                        stickyState = angleModeStickyState,
                    )
                    frameListener?.invoke(frame)
                }

                override fun onNoPoseDetected(@Suppress("UNUSED_PARAMETER") isFrontCamera: Boolean) {
                    TrainingPipelineDiagnostics.recordPoseResult(
                        inferenceMs = poseDetector.lastInferenceTimeMs,
                        hasPose = false,
                        busySkippedSinceLastResult = poseDetector.consumeBusySkipCount(),
                    )
                    frameListener?.invoke(null)
                }

                override fun onError(message: String) {
                    frameListener?.invoke(null)
                }
            },
        )
        poseDetector.warmUp(PoseDetectorConfiguration(useGpu = true))
        val preferredPreset = sessionPresetFor(analysisW, analysisH)
        val session = AVCaptureSession().apply {
            sessionPreset = when {
                canSetSessionPreset(preferredPreset) -> preferredPreset
                canSetSessionPreset(AVCaptureSessionPreset640x480) -> AVCaptureSessionPreset640x480
                else -> AVCaptureSessionPreset352x288
            }
        }
        val device = discoverCamera(configuration.useFrontCamera) ?: run {
            failStart("Requested camera lens is not available")
            return
        }
        val input = AVCaptureDeviceInput.deviceInputWithDevice(device, error = null) ?: run {
            failStart("Camera input could not be created")
            return
        }
        if (!session.canAddInput(input)) {
            failStart("Camera input could not be added")
            return
        }
        session.addInput(input)
        val useFront = configuration.useFrontCamera
        val minFrameIntervalMs = (1_000L / configuration.targetFps.coerceAtLeast(1)).coerceAtLeast(1L)
        val output = AVCaptureVideoDataOutput().apply {
            videoSettings = mapOf(
                kCVPixelBufferPixelFormatTypeKey to kCVPixelFormatType_32BGRA,
            )
            alwaysDiscardsLateVideoFrames = true
        }
        val delegate = SampleBufferDelegate { buffer ->
            val nowMs = IosPoseDetector.uptimeMillis()
            if (nowMs - lastSubmittedFrameMs < minFrameIntervalMs) {
                TrainingPipelineDiagnostics.recordCameraFrame(acceptedForAnalysis = false)
                return@SampleBufferDelegate
            }
            lastSubmittedFrameMs = nowMs
            TrainingPipelineDiagnostics.recordCameraFrame(acceptedForAnalysis = true)
            poseDetector.detectAsync(buffer, useFront)
        }
        sampleDelegate = delegate
        output.setSampleBufferDelegate(delegate, queue = outputQueue)
        if (!session.canAddOutput(output)) {
            failStart("Camera output could not be added")
            return
        }
        session.addOutput(output)
        (output.connections.firstOrNull() as? AVCaptureConnection)?.let { connection ->
            if (connection.isVideoOrientationSupported()) {
                connection.setVideoOrientation(AVCaptureVideoOrientationPortrait)
            }
            // B-02 blocked pending M6 landmark dump — do not remove capture mirroring yet.
            if (connection.isVideoMirroringSupported() && configuration.useFrontCamera) {
                connection.setAutomaticallyAdjustsVideoMirroring(false)
                connection.setVideoMirrored(true)
            }
        }
        videoOutput = output
        captureSession = session
        TrainingPipelineDiagnostics.setCameraConfig(
            targetFps = configuration.targetFps,
            analysisWidth = analysisW,
            analysisHeight = analysisH,
            appliedFpsRange = "${configuration.targetFps}",
            throughputProfileId = configuration.throughputProfileId,
        )
        dispatch_get_main_queue().let { queue ->
            platform.darwin.dispatch_async(queue) {
                session.startRunning()
                previewHost?.let { attachPreview(it) }
                onCameraBoundListener?.invoke()
            }
        }
    }

    override fun stop() {
        stopSessionOnly()
        poseDetector.shutdown()
        poseDetector.setListener(null)
    }

    private fun stopSessionOnly() {
        captureSession?.stopRunning()
        previewLayer?.removeFromSuperlayer()
        previewLayer = null
        videoOutput?.setSampleBufferDelegate(null, queue = null)
        videoOutput = null
        sampleDelegate = null
        captureSession = null
    }

    private fun failStart(message: String) {
        poseDetector.setListener(null)
        errorListener?.invoke(message)
    }

    private fun layoutPreview(host: UIView, layer: AVCaptureVideoPreviewLayer) {
        CATransaction.begin()
        CATransaction.setValue(true, kCATransactionDisableActions)
        layer.frame = host.bounds
        CATransaction.commit()
    }

    private fun discoverCamera(useFrontCamera: Boolean): AVCaptureDevice? {
        val position = if (useFrontCamera) {
            AVCaptureDevicePositionFront
        } else {
            AVCaptureDevicePositionBack
        }
        val discovery = AVCaptureDeviceDiscoverySession.discoverySessionWithDeviceTypes(
            deviceTypes = listOf(AVCaptureDeviceTypeBuiltInWideAngleCamera),
            mediaType = AVMediaTypeVideo,
            position = position,
        )
        return discovery.devices.firstOrNull() as? AVCaptureDevice
    }

    /**
     * B-01: bind capture preset to throughput analysis size (4:3).
     * 320×240 → CIF 352×288 (Swift downscales to target before MediaPipe);
     * larger profiles → VGA 640×480 then downscale.
     */
    private fun sessionPresetFor(analysisWidth: Int, analysisHeight: Int): String? {
        val maxDim = maxOf(analysisWidth, analysisHeight)
        // K/N AVFoundation binding types these platform constants as String?.
        return if (maxDim <= 352) {
            AVCaptureSessionPreset352x288
        } else {
            AVCaptureSessionPreset640x480
        }
    }

    private class SampleBufferDelegate(
        private val onSample: (CMSampleBufferRef?) -> Unit,
    ) : NSObject(), AVCaptureVideoDataOutputSampleBufferDelegateProtocol {
        override fun captureOutput(
            output: AVCaptureOutput,
            didOutputSampleBuffer: CMSampleBufferRef?,
            fromConnection: AVCaptureConnection,
        ) {
            onSample(didOutputSampleBuffer)
        }
    }
}
