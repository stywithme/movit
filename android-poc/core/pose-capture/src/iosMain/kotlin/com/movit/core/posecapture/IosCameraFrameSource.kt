package com.movit.core.posecapture

import com.movit.core.training.boundary.CameraFrameSource
import com.movit.core.training.boundary.CameraSourceConfiguration
import com.movit.core.training.boundary.PoseDetectorConfiguration
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
import platform.AVFoundation.AVCaptureSessionPresetHigh
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
 * Pairs with [IosPoseDetector] (MediaPipe stub until Swift bridge lands).
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
    private var configuration = CameraSourceConfiguration()
    private val outputQueue = dispatch_queue_create("com.movit.pose-capture.video", null)

    override fun setFrameListener(listener: ((PoseFrame?) -> Unit)?) {
        frameListener = listener
    }

    override fun setErrorListener(listener: ((String) -> Unit)?) = Unit

    override fun setOnCameraBoundListener(listener: (() -> Unit)?) = Unit

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
        stopSessionOnly()
        poseDetector.setListener(
            object : IosPoseDetector.Listener {
                override fun onPoseDetected(result: IosPoseDetector.DetectionResult) {
                    val frame = PoseFrameAssembler.assemble(
                        landmarks = result.landmarks,
                        timestampMs = result.timestampMs,
                        isFrontCamera = result.isFrontCamera,
                        worldLandmarks = result.worldLandmarks,
                    )
                    frameListener?.invoke(frame)
                }

                override fun onNoPoseDetected() {
                    frameListener?.invoke(null)
                }

                override fun onError(message: String) {
                    frameListener?.invoke(null)
                }
            },
        )
        poseDetector.warmUp(PoseDetectorConfiguration(useGpu = true))
        val session = AVCaptureSession().apply {
            sessionPreset = AVCaptureSessionPresetHigh
        }
        val device = discoverCamera(configuration.useFrontCamera) ?: run {
            poseDetector.setListener(null)
            return
        }
        val input = AVCaptureDeviceInput.deviceInputWithDevice(device, error = null) ?: run {
            poseDetector.setListener(null)
            return
        }
        if (!session.canAddInput(input)) {
            poseDetector.setListener(null)
            return
        }
        session.addInput(input)
        val useFront = configuration.useFrontCamera
        val output = AVCaptureVideoDataOutput().apply {
            videoSettings = mapOf(
                kCVPixelBufferPixelFormatTypeKey to kCVPixelFormatType_32BGRA,
            )
            alwaysDiscardsLateVideoFrames = true
        }
        val delegate = SampleBufferDelegate { buffer ->
            poseDetector.detectAsync(buffer, useFront)
        }
        sampleDelegate = delegate
        output.setSampleBufferDelegate(delegate, queue = outputQueue)
        if (!session.canAddOutput(output)) {
            poseDetector.setListener(null)
            return
        }
        session.addOutput(output)
        (output.connections.firstOrNull() as? AVCaptureConnection)?.let { connection ->
            if (connection.isVideoOrientationSupported()) {
                connection.setVideoOrientation(AVCaptureVideoOrientationPortrait)
            }
            if (connection.isVideoMirroringSupported() && configuration.useFrontCamera) {
                connection.setAutomaticallyAdjustsVideoMirroring(false)
                connection.setVideoMirrored(true)
            }
        }
        videoOutput = output
        captureSession = session
        dispatch_get_main_queue().let { queue ->
            platform.darwin.dispatch_async(queue) {
                session.startRunning()
                previewHost?.let { attachPreview(it) }
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
