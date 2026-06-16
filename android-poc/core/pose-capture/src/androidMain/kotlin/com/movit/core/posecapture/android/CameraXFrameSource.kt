package com.movit.core.posecapture.android

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.os.SystemClock
import android.util.Size
import android.util.Log
import android.util.Range
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.movit.core.posecapture.CameraStartGate
import com.movit.core.posecapture.LensSwitchFrameGate
import com.movit.core.posecapture.deliversToConsumers
import com.movit.core.training.boundary.CameraFrameSource
import com.movit.core.training.boundary.CameraSourceConfiguration
import com.movit.core.training.boundary.PoseDetectorConfiguration
import com.movit.core.training.diagnostics.TrainingPipelineDiagnostics
import com.movit.core.training.geometry.PoseFrameAssembler
import com.movit.core.training.model.PoseFrame
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class CameraXFrameSource(
    private val context: Context,
    private val poseDetector: MediaPipePoseDetector,
) : CameraFrameSource {
    companion object {
        private const val TAG = "CameraXFrameSource"
    }

    data class CameraDiagnostics(
        val supportedFpsRanges: String = "N/A",
        val appliedFpsRange: String = "N/A",
        val zoom: String = "N/A",
        val resolution: String = "N/A",
    )

    private var previewView: PreviewView? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var previewUseCase: Preview? = null
    private var analysisUseCase: ImageAnalysis? = null
    private var frameListener: ((PoseFrame?) -> Unit)? = null
    private var debugFrameListener: ((MediaPipePoseDetector.DetectionResult?, PoseFrame?) -> Unit)? = null
    private var errorListener: ((String) -> Unit)? = null
    private var onCameraBoundListener: (() -> Unit)? = null
    private var configuration = CameraSourceConfiguration()
    private val startGate = CameraStartGate()
    private val lensSwitchGate = LensSwitchFrameGate()
    private var analysisExecutor: ExecutorService? = null
    private val debugFpsEnabled = AtomicBoolean(false)
    private val providerInitializing = AtomicBoolean(false)
    private val providerReady = AtomicBoolean(false)
    private val detectorWarmedUp = AtomicBoolean(false)
    private val switchingCamera = AtomicBoolean(false)
    private val bindingInProgress = AtomicBoolean(false)
    private var skippedAnalysisFrameCount = 0
    private var lastAcceptedAnalysisMs = 0L

    var diagnostics: CameraDiagnostics = CameraDiagnostics()
        private set

    val isSwitchingCamera: Boolean
        get() = switchingCamera.get()

    fun bindPreview(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        this.previewView = previewView
        this.lifecycleOwner = lifecycleOwner
        handleGateAction(startGate.onPreviewReady(configuration.useFrontCamera))
    }

    fun setDebugFpsEnabled(enabled: Boolean) {
        debugFpsEnabled.set(enabled)
    }

    override fun setFrameListener(listener: ((PoseFrame?) -> Unit)?) {
        frameListener = listener
    }

    fun setDebugFrameListener(
        listener: ((MediaPipePoseDetector.DetectionResult?, PoseFrame?) -> Unit)?,
    ) {
        debugFrameListener = listener
    }

    override fun setErrorListener(listener: ((String) -> Unit)?) {
        errorListener = listener
    }

    override fun setOnCameraBoundListener(listener: (() -> Unit)?) {
        onCameraBoundListener = listener
    }

    override fun start(configuration: CameraSourceConfiguration) {
        this.configuration = configuration
        handleGateAction(startGate.onStartRequested(configuration.useFrontCamera))
    }

    private fun handleGateAction(action: CameraStartGate.Action) {
        when (action) {
            CameraStartGate.Action.Defer,
            CameraStartGate.Action.NoOp,
            -> Unit
            CameraStartGate.Action.InitialBind -> beginInitialSession()
            is CameraStartGate.Action.SwitchFacing -> switchCamera(action.useFrontCamera)
        }
    }

    private fun beginInitialSession() {
        ensurePoseDetectorReady()
        ensureCameraProvider { bindUseCases(configuration.useFrontCamera, isSwitch = false) }
    }

    private fun switchCamera(useFrontCamera: Boolean) {
        prepareForLensSwitch(useFrontCamera)
        if (!providerReady.get()) {
            ensureCameraProvider { bindUseCases(useFrontCamera, isSwitch = true) }
            return
        }
        bindUseCases(useFrontCamera, isSwitch = true)
    }

    private fun prepareForLensSwitch(useFrontCamera: Boolean) {
        lensSwitchGate.beginAwaitingFrames(useFrontCamera)
        switchingCamera.set(true)
        poseDetector.resetTrackingState()
        PoseFrameAssembler.resetElbowEstimator()
    }

    private fun emitPoseFrame(
        frame: PoseFrame?,
        facingOverride: Boolean? = null,
        debugResult: MediaPipePoseDetector.DetectionResult? = null,
    ) {
        val facing = facingOverride ?: frame?.isFrontCamera ?: configuration.useFrontCamera
        val decision = lensSwitchGate.acceptFrame(facing)
        if (!decision.deliversToConsumers()) return
        if (decision == LensSwitchFrameGate.FrameDecision.DeliverCompleteSwitch) {
            switchingCamera.set(false)
            onCameraBoundListener?.invoke()
        }
        deliverDebugFrame(debugResult, frame)
        frameListener?.invoke(frame)
    }

    private fun deliverDebugFrame(
        debugResult: MediaPipePoseDetector.DetectionResult?,
        frame: PoseFrame?,
    ) {
        val listener = debugFrameListener ?: return
        if (frame == null) {
            listener(null, null)
        } else if (debugResult != null) {
            listener(debugResult, frame)
        }
    }

    private fun ensurePoseDetectorReady() {
        if (!detectorWarmedUp.compareAndSet(false, true)) return
        poseDetector.setListener(object : MediaPipePoseDetector.Listener {
            override fun onPoseDetected(result: MediaPipePoseDetector.DetectionResult) {
                val frame = PoseFrameAssembler.assemble(
                    landmarks = result.landmarks,
                    timestampMs = result.timestampMs,
                    isFrontCamera = result.isFrontCamera,
                    worldLandmarks = result.worldLandmarks,
                    analysisImageWidth = result.analysisImageWidth,
                    analysisImageHeight = result.analysisImageHeight,
                )
                TrainingPipelineDiagnostics.recordPoseResult(
                    inferenceMs = result.inferenceTimeMs,
                    hasPose = true,
                    busySkippedSinceLastResult = poseDetector.consumeBusySkipCount(),
                )
                emitPoseFrame(frame, debugResult = result)
            }

            override fun onNoPoseDetected(isFrontCamera: Boolean) {
                TrainingPipelineDiagnostics.recordPoseResult(
                    inferenceMs = poseDetector.lastInferenceTimeMs,
                    hasPose = false,
                    busySkippedSinceLastResult = poseDetector.consumeBusySkipCount(),
                )
                emitPoseFrame(null, facingOverride = isFrontCamera)
            }

            override fun onError(message: String) {
                reportError(message)
            }
        })
        poseDetector.warmUp(PoseDetectorConfiguration(useGpu = true))
    }

    private fun ensureCameraProvider(onReady: () -> Unit) {
        if (providerReady.get()) {
            onReady()
            return
        }
        if (!providerInitializing.compareAndSet(false, true)) {
            pendingProviderReady = onReady
            return
        }
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                cameraProvider = providerFuture.get()
                providerReady.set(true)
                onReady()
                pendingProviderReady?.invoke()
                pendingProviderReady = null
            } catch (e: Exception) {
                providerInitializing.set(false)
                switchingCamera.set(false)
                reportError("Camera provider failed: ${e.message ?: "unknown error"}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private var pendingProviderReady: (() -> Unit)? = null

    private fun bindUseCases(useFrontCamera: Boolean, isSwitch: Boolean) {
        if (!bindingInProgress.compareAndSet(false, true)) {
            if (debugFpsEnabled.get()) {
                Log.d(TAG, "Skipping duplicate bind (already in progress)")
            }
            return
        }
        val preview = previewView ?: run {
            bindingInProgress.set(false)
            reportError("Camera preview is not bound")
            switchingCamera.set(false)
            return
        }
        val owner = lifecycleOwner ?: run {
            bindingInProgress.set(false)
            reportError("Camera lifecycle owner is not bound")
            switchingCamera.set(false)
            return
        }
        val provider = cameraProvider ?: run {
            bindingInProgress.set(false)
            reportError("Camera provider is not ready")
            switchingCamera.set(false)
            return
        }
        if (!isSwitch && camera != null && startGate.boundFacing() == useFrontCamera) {
            if (debugFpsEnabled.get()) {
                Log.d(TAG, "Skipping duplicate bind for front=$useFrontCamera")
            }
            bindingInProgress.set(false)
            return
        }
        if (isSwitch) {
            switchingCamera.set(true)
        }
        try {
            val selector = CameraSelector.Builder()
                .requireLensFacing(
                    if (useFrontCamera) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    },
                )
                .build()
            val rotation = preview.display?.rotation ?: android.view.Surface.ROTATION_0
            val resolutionSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .build()
            val previewUseCase = Preview.Builder()
                .setResolutionSelector(resolutionSelector)
                .setTargetRotation(rotation)
                .build()
                .also { it.surfaceProvider = preview.surfaceProvider }
            val analysisSize = Size(
                configuration.analysisWidth.coerceAtLeast(1),
                configuration.analysisHeight.coerceAtLeast(1),
            )
            val analysisResolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        analysisSize,
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                    ),
                )
                .build()
            val analysisUseCase = ImageAnalysis.Builder()
                .setResolutionSelector(analysisResolutionSelector)
                .setTargetRotation(rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { imageAnalysis ->
                    imageAnalysis.setAnalyzer(analysisExecutor()) { proxy ->
                        if (!shouldAnalyzeFrame()) {
                            skippedAnalysisFrameCount++
                            TrainingPipelineDiagnostics.recordCameraFrame(acceptedForAnalysis = false)
                            proxy.close()
                            return@setAnalyzer
                        }
                        TrainingPipelineDiagnostics.recordCameraFrame(acceptedForAnalysis = true)
                        poseDetector.detectAsync(proxy, useFrontCamera)
                    }
                }
            provider.unbindAll()
            camera = provider.bindToLifecycle(owner, selector, previewUseCase, analysisUseCase)
            this.previewUseCase = previewUseCase
            this.analysisUseCase = analysisUseCase
            configuration = configuration.copy(useFrontCamera = useFrontCamera)
            startGate.markBound(useFrontCamera)
            applyHighFps()
            applyWidestZoom(preview)
            captureResolutionDiagnostics(previewUseCase, analysisUseCase)
            val analysisRes = runCatching { analysisUseCase.resolutionInfo?.resolution }.getOrNull()
            TrainingPipelineDiagnostics.setCameraConfig(
                targetFps = configuration.targetFps,
                analysisWidth = analysisRes?.width ?: configuration.analysisWidth,
                analysisHeight = analysisRes?.height ?: configuration.analysisHeight,
                appliedFpsRange = diagnostics.appliedFpsRange,
                throughputProfileId = configuration.throughputProfileId,
            )
            if (debugFpsEnabled.get()) {
                Log.d(
                    TAG,
                    "Camera bound: front=$useFrontCamera fps=${diagnostics.appliedFpsRange} " +
                        "zoom=${diagnostics.zoom} res=${diagnostics.resolution}",
                )
            }
            if (!isSwitch) {
                onCameraBoundListener?.invoke()
            }
        } catch (e: Exception) {
            lensSwitchGate.clear()
            switchingCamera.set(false)
            reportError("Camera bind failed: ${e.message ?: "unknown error"}")
        } finally {
            bindingInProgress.set(false)
        }
    }

    private fun analysisExecutor(): ExecutorService {
        val existing = analysisExecutor
        if (existing != null && !existing.isShutdown) return existing
        return Executors.newSingleThreadExecutor().also { analysisExecutor = it }
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun applyHighFps() {
        val cam = camera ?: return
        try {
            val camera2Info = Camera2CameraInfo.from(cam.cameraInfo)
            val supportedRanges: Array<Range<Int>>? = camera2Info.getCameraCharacteristic(
                CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES,
            )
            val supportedText = supportedRanges?.joinToString { "[${it.lower},${it.upper}]" } ?: "null"
            val bestRange = supportedRanges?.let { chooseFpsRange(it, configuration.targetFps) }
            val appliedText = if (bestRange != null) {
                val camera2Control = Camera2CameraControl.from(cam.cameraControl)
                camera2Control.setCaptureRequestOptions(
                    CaptureRequestOptions.Builder()
                        .setCaptureRequestOption(
                            CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                            bestRange,
                        )
                        .build(),
                )
                "[${bestRange.lower},${bestRange.upper}]"
            } else {
                "none"
            }
            diagnostics = diagnostics.copy(
                supportedFpsRanges = supportedText,
                appliedFpsRange = appliedText,
            )
            if (debugFpsEnabled.get()) {
                Log.d(TAG, "Supported FPS ranges: $supportedText; applied: $appliedText")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply high FPS: ${e.message}")
        }
    }

    private fun chooseFpsRange(ranges: Array<Range<Int>>, targetFps: Int): Range<Int>? {
        val target = targetFps.coerceIn(1, 60)
        return ranges
            .filter { target in it.lower..it.upper }
            .minWithOrNull(compareBy<Range<Int>> { it.upper - it.lower }.thenBy { kotlin.math.abs(it.upper - target) })
            ?: ranges
                .filter { it.upper <= target }
                .maxByOrNull { it.upper }
            ?: ranges.maxByOrNull { it.upper }
    }

    private fun shouldAnalyzeFrame(): Boolean {
        val target = configuration.targetFps.coerceAtLeast(1)
        val minIntervalMs = (1_000L / target).coerceAtLeast(1L)
        val now = SystemClock.elapsedRealtime()
        if (lastAcceptedAnalysisMs == 0L || now - lastAcceptedAnalysisMs >= minIntervalMs) {
            lastAcceptedAnalysisMs = now
            return true
        }
        return false
    }

    private fun applyWidestZoom(previewView: PreviewView) {
        val cam = camera ?: return
        applyWidestZoomOnce(cam)
        scheduleWidestZoomRetry(previewView, cam)
    }

    private fun applyWidestZoomOnce(cam: Camera) {
        try {
            val zoomState = cam.cameraInfo.zoomState.value
            if (zoomState != null) {
                cam.cameraControl.setZoomRatio(zoomState.minZoomRatio)
                diagnostics = diagnostics.copy(
                    zoom = "[min=${zoomState.minZoomRatio}, max=${zoomState.maxZoomRatio}]",
                )
            } else {
                cam.cameraControl.setLinearZoom(0f)
                diagnostics = diagnostics.copy(zoom = "linearZoom=0 (pending)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply widest zoom: ${e.message}")
        }
    }

    private fun scheduleWidestZoomRetry(previewView: PreviewView, boundCamera: Camera) {
        previewView.postDelayed({
            if (camera === boundCamera) {
                applyWidestZoomOnce(boundCamera)
            }
        }, 300L)
        previewView.postDelayed({
            if (camera === boundCamera) {
                applyWidestZoomOnce(boundCamera)
            }
        }, 1_000L)
    }

    private fun captureResolutionDiagnostics(preview: Preview, analysis: ImageAnalysis) {
        val previewRes = runCatching { preview.resolutionInfo?.resolution }.getOrNull()
        val analysisRes = runCatching { analysis.resolutionInfo?.resolution }.getOrNull()
        diagnostics = diagnostics.copy(
            resolution = "preview=$previewRes, analysis=$analysisRes",
        )
    }

    override fun stop() {
        switchingCamera.set(false)
        bindingInProgress.set(false)
        pendingProviderReady = null
        val analysis = analysisUseCase
        runCatching { analysis?.clearAnalyzer() }
        runCatching { cameraProvider?.unbindAll() }
        camera = null
        previewUseCase = null
        analysisUseCase = null
        lastAcceptedAnalysisMs = 0L
        skippedAnalysisFrameCount = 0
        lensSwitchGate.clear()
        startGate.reset()
    }

    fun reinitializePoseDetector() {
        stop()
        poseDetector.shutdown()
        detectorWarmedUp.set(false)
    }

    fun dispose() {
        stop()
        poseDetector.setListener(null)
        poseDetector.shutdown()
        detectorWarmedUp.set(false)
        providerReady.set(false)
        providerInitializing.set(false)
        cameraProvider = null
        analysisExecutor?.shutdown()
        analysisExecutor = null
    }

    private fun reportError(message: String) {
        Log.e(TAG, message)
        errorListener?.invoke(message)
    }
}
