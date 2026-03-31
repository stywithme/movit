package com.trainingvalidator.poc.ui.debug

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.switchmaterial.SwitchMaterial
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.exifinterface.media.ExifInterface
import com.google.mediapipe.tasks.components.containers.Landmark
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.android.material.tabs.TabLayout
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.analysis.AngleCalculator
import com.trainingvalidator.poc.analysis.ElbowAngleEstimator
import com.trainingvalidator.poc.analysis.ElbowDiagnostics
import com.trainingvalidator.poc.analysis.JointAngles
import com.trainingvalidator.poc.analysis.LandmarkSmoother
import com.trainingvalidator.poc.analysis.SmoothedLandmark
import com.trainingvalidator.poc.camera.CameraManager
import com.trainingvalidator.poc.databinding.ActivityDebugBinding
import com.trainingvalidator.poc.pose.BodyLandmarks
import com.trainingvalidator.poc.pose.JointLandmarkMapping
import com.trainingvalidator.poc.pose.ModelType
import com.trainingvalidator.poc.pose.PoseLandmarkerHelper
import com.trainingvalidator.poc.pose.PoseResult
import com.trainingvalidator.poc.training.config.SettingsManager
import com.trainingvalidator.poc.training.engine.CameraPositionDetector
import com.trainingvalidator.poc.training.engine.Phase
import com.trainingvalidator.poc.training.engine.BodyPosture
import com.trainingvalidator.poc.training.engine.BodyPostureDetector
import com.trainingvalidator.poc.training.engine.ExpectedDirection
import com.trainingvalidator.poc.training.engine.PoseSceneDetector
import com.trainingvalidator.poc.training.engine.PoseSceneExpectation
import com.trainingvalidator.poc.training.engine.PoseSceneResult
import com.trainingvalidator.poc.training.engine.VisibleRegion
import com.trainingvalidator.poc.training.engine.PositionError
import com.trainingvalidator.poc.training.engine.PositionValidationResult
import com.trainingvalidator.poc.training.engine.PositionValidator
import com.trainingvalidator.poc.training.engine.ElbowCorrectionMlpClassifier
import com.trainingvalidator.poc.training.engine.ElbowFit3dV2Classifier
import com.trainingvalidator.poc.training.engine.ElbowFit3dV2FeatureExtractor
import com.trainingvalidator.poc.training.engine.ElbowMlpFeatureExtractor
import com.trainingvalidator.poc.training.engine.PostureMlpClassifier
import com.trainingvalidator.poc.training.engine.PostureMlpFeatureExtractor
import com.trainingvalidator.poc.training.models.*
import com.trainingvalidator.poc.video.VideoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DebugActivity : AppCompatActivity(), PoseLandmarkerHelper.PoseDetectionListener {

    companion object {
        private const val TAG = "DebugActivity"
        private const val TAB_ELBOWS = 0
        private const val TAB_ANGLE_DIAGNOSTICS = 1
        private const val TAB_POSITION = 2
        private const val TAB_CAMERA = 3
        private const val TAB_POSTURE_MLP = 4
    }

    enum class InputMode { CAMERA, VIDEO, IMAGE }

    private lateinit var binding: ActivityDebugBinding
    private var cameraManager: CameraManager? = null
    private var poseLandmarkerHelper: PoseLandmarkerHelper? = null
    private lateinit var landmarkSmoother: LandmarkSmoother
    private val elbowAngleEstimator = ElbowAngleEstimator()

    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var useFrontCamera = true
    private var currentTab = TAB_ELBOWS
    private var currentInputMode = InputMode.CAMERA

    // Joint angle mode
    private var selectedJointCode: String = "left_knee"

    // Position check mode
    private var selectedCheckType = PositionCheckType.VERTICAL_COMPARISON
    private var selectedPrimaryLandmark = "left_knee"
    private var selectedSecondaryLandmark = "left_ankle"
    private var selectedOperator = PositionOperator.SHOULD_NOT_EXCEED
    private var selectedThreshold = 0.05
    private var positionValidator: PositionValidator? = null

    // FPS tracking
    private var inferenceFrameCount = 0
    private var lastInferenceFpsTime = System.currentTimeMillis()
    private var currentInferenceFps = 0
    private var cameraFrameCount = 0
    private var lastCameraFpsTime = System.currentTimeMillis()
    private var currentCameraFps = 0

    // Pose scene detection mode (3 independent axes, multi-value)
    private val sceneDetector = PoseSceneDetector()
    private var selectedPostures = listOf(BodyPosture.STANDING)
    private var selectedDirections = listOf(ExpectedDirection.FRONT)
    private var selectedRegions = listOf(VisibleRegion.FULL_BODY)
    private val allPostures = BodyPosture.entries.filter { it != BodyPosture.UNKNOWN }
    private val allDirections = ExpectedDirection.entries.filter { it != ExpectedDirection.ANY && it != ExpectedDirection.DIAGONAL }
    private val allRegions = VisibleRegion.entries.filter { it != VisibleRegion.UNKNOWN }
    private fun buildExpectation() = PoseSceneExpectation(selectedPostures, selectedDirections, selectedRegions)

    // Latest landmarks for debug panel
    private var latestLandmarks: List<SmoothedLandmark>? = null

    // Elbow MLP debug
    private var elbowMlpRetryRequested = false
    private val elbowMlpLatencyRing = ArrayDeque<Long>(32)
    private val elbowMlpLatencyRingMax = 30

    // FIT3D v2 MLP debug
    private val fit3dLatencyRing = ArrayDeque<Long>(32)
    private val fit3dLatencyRingMax = 30

    private data class AngleDebugPoint(
        val index: Int,
        val name: String,
        val x: Float,
        val y: Float,
        val z: Float,
        val visibility: Float,
        val presence: Float
    )

    private data class AngleSegmentMetrics(
        val dx: Double,
        val dy: Double,
        val dz: Double,
        val length2D: Double,
        val length3D: Double
    ) {
        val depthShare: Double
            get() = if (length3D > 0.0) kotlin.math.abs(dz) / length3D else 0.0

        val planarRatio: Double
            get() = if (length3D > 0.0) length2D / length3D else 0.0
    }

    private data class AngleDebugFrame(
        val pointA: AngleDebugPoint,
        val pointB: AngleDebugPoint,
        val pointC: AngleDebugPoint,
        val xyAngle: Double?,
        val xzAngle: Double?,
        val yzAngle: Double?,
        val xyzAngle: Double?,
        val segmentBA: AngleSegmentMetrics,
        val segmentBC: AngleSegmentMetrics
    ) {
        val minVisibility: Float
            get() = minOf(pointA.visibility, pointB.visibility, pointC.visibility)

        val minPresence: Float
            get() = minOf(pointA.presence, pointB.presence, pointC.presence)
    }

    private data class AngleDiagnosticsData(
        val displayJointCode: String,
        val sourceJointCode: String,
        val effectiveIndices: List<Int>,
        val displayedAngle: Double?,
        val pipelineSourceLabel: String,
        val normalizedRaw: AngleDebugFrame?,
        val normalizedSmoothed: AngleDebugFrame?,
        val worldRaw: AngleDebugFrame?,
        val worldSmoothed: AngleDebugFrame?
    )

    private val jointCodes = JointLandmarkMapping.trackedJointCodes.toList().sorted()
    private val landmarkNames = listOf(
        "nose",
        "left_shoulder", "right_shoulder",
        "left_elbow", "right_elbow",
        "left_wrist", "right_wrist",
        "left_hip", "right_hip",
        "left_knee", "right_knee",
        "left_ankle", "right_ankle",
        "left_heel", "right_heel",
        "left_foot_index", "right_foot_index",
        "neck", "spine"
    )
    private val checkTypeNames = PositionCheckType.values().map { it.name }
    private val operatorNames = PositionOperator.values().map { it.name }

    // Video mode
    private var videoManager: VideoManager? = null
    private var isVideoSeeking = false

    // Image mode
    private var currentImageBitmap: Bitmap? = null

    // ==================== Activity Result Launchers ====================

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) onCameraPermissionGranted() else showPermissionDeniedView()
    }

    private val videoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { loadVideo(it) }
    }

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { loadImage(it) }
    }

    private var isInfoPanelVisible = true
    private var settingsDialog: BottomSheetDialog? = null

    // ==================== Lifecycle ====================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            SettingsManager.initialize(this)
            landmarkSmoother = LandmarkSmoother.createFromSettings()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize settings, using defaults", e)
            landmarkSmoother = LandmarkSmoother.createBalanced()
        }

        setupFullscreen()
        binding = ActivityDebugBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setupUI()
        initializePoseDetection()
        checkCameraPermission()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraManager?.stopCamera()
        videoManager?.release()
        poseLandmarkerHelper?.close()
        currentImageBitmap?.recycle()
        mainScope.cancel()
    }

    // ==================== UI Setup ====================

    private fun setupFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnSwitchCamera.setOnClickListener {
            useFrontCamera = !useFrontCamera
            cameraManager?.switchCamera(useFrontCamera)
            landmarkSmoother.reset()
            elbowAngleEstimator.reset()
            sceneDetector.reset()
        }

        binding.btnGrantPermission.setOnClickListener {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        binding.btnSettings.setOnClickListener {
            showSettingsDialog()
        }

        binding.btnCopyDebugInfo.setOnClickListener {
            copyCurrentDebugInfo()
        }

        setupVideoControls()
    }

    private fun showSettingsDialog() {
        if (settingsDialog == null) {
            settingsDialog = BottomSheetDialog(this)
            val view = LayoutInflater.from(this).inflate(R.layout.dialog_debug_settings, null)
            settingsDialog?.setContentView(view)

            // Info Panel Switch
            val switchInfo = view.findViewById<SwitchMaterial>(R.id.switchDebugInfo)
            switchInfo.isChecked = isInfoPanelVisible
            switchInfo.setOnCheckedChangeListener { _, isChecked ->
                isInfoPanelVisible = isChecked
                updateInfoPanelVisibility()
            }

            // Input Mode
            val modeGroup = view.findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.inputModeGroup)
            val btnPickFile = view.findViewById<View>(R.id.btnPickFile)
            
            val checkedId = when (currentInputMode) {
                InputMode.CAMERA -> R.id.btnModeCamera
                InputMode.VIDEO -> R.id.btnModeVideo
                InputMode.IMAGE -> R.id.btnModeImage
            }
            modeGroup.check(checkedId)
            btnPickFile.visibility = if (currentInputMode != InputMode.CAMERA) View.VISIBLE else View.GONE

            modeGroup.addOnButtonCheckedListener { _, id, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener
                val newMode = when (id) {
                    R.id.btnModeCamera -> InputMode.CAMERA
                    R.id.btnModeVideo -> InputMode.VIDEO
                    R.id.btnModeImage -> InputMode.IMAGE
                    else -> return@addOnButtonCheckedListener
                }
                if (newMode != currentInputMode) {
                    switchInputMode(newMode)
                    btnPickFile.visibility = if (newMode != InputMode.CAMERA) View.VISIBLE else View.GONE
                }
            }

            // Pick File Button
            btnPickFile.setOnClickListener {
                when (currentInputMode) {
                    InputMode.VIDEO -> videoPickerLauncher.launch("video/*")
                    InputMode.IMAGE -> imagePickerLauncher.launch("image/*")
                    else -> {}
                }
                settingsDialog?.dismiss()
            }

            // Tabs
            val tabLayout = view.findViewById<TabLayout>(R.id.tabLayout)
            val jointConfig = view.findViewById<View>(R.id.jointAngleConfig)
            val posConfig = view.findViewById<View>(R.id.positionCheckConfig)
            val camConfig = view.findViewById<View>(R.id.cameraDetectionConfig)

            tabLayout.getTabAt(currentTab)?.select()
            
            fun updateConfigVisibility(tab: Int) {
                jointConfig.visibility = if (tab == TAB_ANGLE_DIAGNOSTICS) View.VISIBLE else View.GONE
                posConfig.visibility = if (tab == TAB_POSITION) View.VISIBLE else View.GONE
                camConfig.visibility = if (tab == TAB_CAMERA) View.VISIBLE else View.GONE
            }
            updateConfigVisibility(currentTab)

            tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    val pos = tab?.position ?: TAB_ELBOWS
                    updateConfigVisibility(pos)
                    handleTabChange(pos)
                }
                override fun onTabUnselected(tab: TabLayout.Tab?) {}
                override fun onTabReselected(tab: TabLayout.Tab?) {}
            })

            // Spinners
            setupSpinner(view.findViewById(R.id.spinnerJoint), jointCodes) { pos ->
                selectedJointCode = jointCodes[pos]
                if (currentInputMode == InputMode.IMAGE) reanalyzeCurrentImage()
            }
            view.findViewById<android.widget.Spinner>(R.id.spinnerJoint).setSelection(jointCodes.indexOf(selectedJointCode).coerceAtLeast(0))

            setupSpinner(view.findViewById(R.id.spinnerCheckType), checkTypeNames) { pos ->
                selectedCheckType = PositionCheckType.values()[pos]
                rebuildPositionValidator()
                if (currentInputMode == InputMode.IMAGE) reanalyzeCurrentImage()
            }
            view.findViewById<android.widget.Spinner>(R.id.spinnerCheckType).setSelection(selectedCheckType.ordinal)

            setupSpinner(view.findViewById(R.id.spinnerPrimary), landmarkNames) { pos ->
                selectedPrimaryLandmark = landmarkNames[pos]
                rebuildPositionValidator()
                if (currentInputMode == InputMode.IMAGE) reanalyzeCurrentImage()
            }
            view.findViewById<android.widget.Spinner>(R.id.spinnerPrimary).setSelection(landmarkNames.indexOf(selectedPrimaryLandmark).coerceAtLeast(0))

            setupSpinner(view.findViewById(R.id.spinnerSecondary), landmarkNames) { pos ->
                selectedSecondaryLandmark = landmarkNames[pos]
                rebuildPositionValidator()
                if (currentInputMode == InputMode.IMAGE) reanalyzeCurrentImage()
            }
            view.findViewById<android.widget.Spinner>(R.id.spinnerSecondary).setSelection(landmarkNames.indexOf(selectedSecondaryLandmark).coerceAtLeast(0))

            setupSpinner(view.findViewById(R.id.spinnerOperator), operatorNames) { pos ->
                selectedOperator = PositionOperator.values()[pos]
                rebuildPositionValidator()
                if (currentInputMode == InputMode.IMAGE) reanalyzeCurrentImage()
            }
            view.findViewById<android.widget.Spinner>(R.id.spinnerOperator).setSelection(selectedOperator.ordinal)

            val etThreshold = view.findViewById<android.widget.EditText>(R.id.etThreshold)
            etThreshold.setText(selectedThreshold.toString())
            etThreshold.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    selectedThreshold = etThreshold.text.toString().toDoubleOrNull() ?: 0.05
                    rebuildPositionValidator()
                    if (currentInputMode == InputMode.IMAGE) reanalyzeCurrentImage()
                }
            }

            setupSpinner(view.findViewById(R.id.spinnerExpectedCamPos), allPostures.map { it.name }) { pos ->
                selectedPostures = listOf(allPostures[pos])
                if (currentInputMode == InputMode.IMAGE) reanalyzeCurrentImage()
            }
            view.findViewById<android.widget.Spinner>(R.id.spinnerExpectedCamPos).setSelection(allPostures.indexOf(selectedPostures.first()).coerceAtLeast(0))

            setupSpinner(view.findViewById(R.id.spinnerExpectedDirection), allDirections.map { it.code }) { pos ->
                selectedDirections = listOf(allDirections[pos])
                if (currentInputMode == InputMode.IMAGE) reanalyzeCurrentImage()
            }
            view.findViewById<android.widget.Spinner>(R.id.spinnerExpectedDirection).setSelection(allDirections.indexOf(selectedDirections.first()).coerceAtLeast(0))

            setupSpinner(view.findViewById(R.id.spinnerExpectedRegion), allRegions.map { it.name }) { pos ->
                selectedRegions = listOf(allRegions[pos])
                if (currentInputMode == InputMode.IMAGE) reanalyzeCurrentImage()
            }
            view.findViewById<android.widget.Spinner>(R.id.spinnerExpectedRegion).setSelection(allRegions.indexOf(selectedRegions.first()).coerceAtLeast(0))
        }
        settingsDialog?.show()
    }

    private fun handleTabChange(newTab: Int) {
        currentTab = newTab

        clearOverlay()
        sceneDetector.reset()
        binding.tvLiveValue.text = "--"
        binding.tvLiveValue.setTextColor(Color.WHITE)
        binding.tvDebugInfo.text = ""
        binding.tvStatus.text = ""

        binding.tvStatus.visibility = View.GONE
        binding.tvLiveValue.visibility = View.VISIBLE

        when (currentTab) {
            TAB_ELBOWS -> {
                updateInfoPanelVisibility()
                binding.tvStatus.visibility = View.VISIBLE
            }
            TAB_ANGLE_DIAGNOSTICS -> {
                updateInfoPanelVisibility()
                binding.tvStatus.visibility = View.VISIBLE
            }
            TAB_POSITION -> {
                updateInfoPanelVisibility()
                binding.tvStatus.visibility = View.VISIBLE
                rebuildPositionValidator()
            }
            TAB_CAMERA -> {
                updateInfoPanelVisibility()
                binding.tvLiveValue.visibility = View.GONE
            }
            TAB_POSTURE_MLP -> {
                updateInfoPanelVisibility()
                binding.tvStatus.visibility = View.VISIBLE
            }
        }

        if (currentInputMode == InputMode.IMAGE) {
            reanalyzeCurrentImage()
        }
    }

    private fun updateInfoPanelVisibility() {
        if (!isInfoPanelVisible) {
            binding.debugInfoPanel.visibility = View.GONE
            return
        }
        binding.debugInfoPanel.visibility = View.VISIBLE
    }

    private fun setupSpinner(spinner: android.widget.Spinner, items: List<String>, onSelected: (Int) -> Unit) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                onSelected(pos)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupVideoControls() {
        binding.btnPlayPause.setOnClickListener {
            videoManager?.togglePlayPause()
        }

        binding.seekBarVideo.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val duration = videoManager?.getDuration() ?: 0L
                    val seekPos = (progress / 1000f * duration).toLong()
                    videoManager?.seekTo(seekPos)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { isVideoSeeking = true }
            override fun onStopTrackingTouch(seekBar: SeekBar?) { isVideoSeeking = false }
        })
    }

    // ==================== Input Mode Switching ====================

    private fun switchInputMode(newMode: InputMode) {
        val oldMode = currentInputMode
        currentInputMode = newMode

        // Stop old mode
        when (oldMode) {
            InputMode.CAMERA -> {
                cameraManager?.stopCamera()
                cameraManager = null
            }
            InputMode.VIDEO -> {
                videoManager?.release()
                videoManager = null
            }
            InputMode.IMAGE -> {
                currentImageBitmap?.recycle()
                currentImageBitmap = null
            }
        }

        clearOverlay()
        landmarkSmoother.reset()
        elbowAngleEstimator.reset()
        sceneDetector.reset()
        resetFpsCounters()

        binding.skeletonOverlay.setScaleMode(fitCenter = newMode == InputMode.IMAGE)

        // Update view visibility
        binding.previewView.visibility = if (newMode == InputMode.CAMERA) View.VISIBLE else View.GONE
        binding.videoTextureView.visibility = if (newMode == InputMode.VIDEO) View.VISIBLE else View.GONE
        binding.staticImageView.visibility = if (newMode == InputMode.IMAGE) View.VISIBLE else View.GONE
        binding.btnSwitchCamera.visibility = if (newMode == InputMode.CAMERA) View.VISIBLE else View.GONE
        binding.videoControls.visibility = if (newMode == InputMode.VIDEO) View.VISIBLE else View.GONE

        // Start new mode
        when (newMode) {
            InputMode.CAMERA -> {
                startCameraMode()
            }
            InputMode.VIDEO -> {
                binding.tvFps.text = "Video mode"
                ensureVideoModeReady()
            }
            InputMode.IMAGE -> {
                binding.tvFps.text = "Image mode"
                ensureImageModeReady()
            }
        }
    }

    // ==================== Camera Mode ====================

    private fun startCameraMode() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            showPermissionDeniedView()
            return
        }
        binding.permissionView.visibility = View.GONE
        initializeCamera()
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            onCameraPermissionGranted()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun onCameraPermissionGranted() {
        binding.permissionView.visibility = View.GONE
        if (currentInputMode == InputMode.CAMERA) {
            initializeCamera()
        }
    }

    private fun showPermissionDeniedView() {
        if (currentInputMode == InputMode.CAMERA) {
            binding.permissionView.visibility = View.VISIBLE
        }
    }

    private fun initializePoseDetection() {
        poseLandmarkerHelper = PoseLandmarkerHelper(
            context = applicationContext,
            listener = this
        )
        mainScope.launch(Dispatchers.IO) {
            poseLandmarkerHelper?.initialize(modelType = ModelType.FULL, useGpu = true)
            withContext(Dispatchers.Main) {
                if (poseLandmarkerHelper?.isReady() == true) {
                    Log.d(TAG, "Pose detection LIVE_STREAM ready")
                }
            }
        }
    }

    private fun initializeCamera() {
        cameraManager = CameraManager(
            context = this,
            lifecycleOwner = this,
            previewView = binding.previewView
        )
        cameraManager?.startCamera(useFrontCamera = useFrontCamera) { imageProxy ->
            updateCameraFps()
            poseLandmarkerHelper?.detectPose(imageProxy, useFrontCamera)
        }
    }

    // ==================== Video Mode ====================

    private fun ensureVideoModeReady() {
        if (poseLandmarkerHelper?.isVideoModeReady() != true) {
            mainScope.launch(Dispatchers.IO) {
                poseLandmarkerHelper?.initializeForVideo(modelType = ModelType.FULL, useGpu = true)
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "Pose detection VIDEO mode ready")
                }
            }
        }
    }

    private fun loadVideo(uri: Uri) {
        videoManager?.release()
        videoManager = null
        clearOverlay()
        landmarkSmoother.reset()
        elbowAngleEstimator.reset()
        sceneDetector.reset()
        poseLandmarkerHelper?.resetForVideo()

        videoManager = VideoManager(
            context = this,
            textureView = binding.videoTextureView,
            onFrameAvailable = { bitmap, timestampMs -> processVideoFrame(bitmap, timestampMs) },
            onPlaybackStateChanged = { state -> onVideoStateChanged(state) },
            onProgressChanged = { currentMs, durationMs -> onVideoProgressChanged(currentMs, durationMs) },
            onSeekPerformed = {
                landmarkSmoother.reset()
                elbowAngleEstimator.reset()
                sceneDetector.reset()
                poseLandmarkerHelper?.resetForVideo()
            },
            onVideoEnded = {
                mainScope.launch(Dispatchers.Main) {
                    binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
                }
            }
        )
        videoManager?.loadVideo(uri)
    }

    private fun processVideoFrame(bitmap: Bitmap, timestampMs: Long) {
        val result = poseLandmarkerHelper?.detectPoseFromBitmap(bitmap, timestampMs) ?: return
        mainScope.launch(Dispatchers.Main) {
            processPoseResult(result)
        }
    }

    private fun onVideoStateChanged(state: VideoManager.PlaybackState) {
        mainScope.launch(Dispatchers.Main) {
            when (state) {
                VideoManager.PlaybackState.PLAYING -> {
                    binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
                }
                VideoManager.PlaybackState.READY -> {
                    binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
                    videoManager?.play()
                }
                VideoManager.PlaybackState.PAUSED,
                VideoManager.PlaybackState.ENDED -> {
                    binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
                }
                else -> {}
            }
        }
    }

    private fun onVideoProgressChanged(currentMs: Long, durationMs: Long) {
        mainScope.launch(Dispatchers.Main) {
            if (!isVideoSeeking && durationMs > 0) {
                binding.seekBarVideo.progress = ((currentMs.toFloat() / durationMs) * 1000).toInt()
            }
            binding.tvVideoTime.text = "${formatTime(currentMs)} / ${formatTime(durationMs)}"
        }
    }

    // ==================== Image Mode ====================

    private fun ensureImageModeReady() {
        if (poseLandmarkerHelper?.isImageModeReady() != true) {
            mainScope.launch(Dispatchers.IO) {
                poseLandmarkerHelper?.initializeForImage(modelType = ModelType.FULL, useGpu = true)
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "Pose detection IMAGE mode ready")
                }
            }
        }
    }

    private fun loadImage(uri: Uri) {
        clearOverlay()
        landmarkSmoother.reset()
        elbowAngleEstimator.reset()
        sceneDetector.reset()

        mainScope.launch(Dispatchers.IO) {
            try {
                val bitmap = decodeBitmapFromUri(uri)
                if (bitmap == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@DebugActivity, "Failed to load image", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    currentImageBitmap?.recycle()
                    currentImageBitmap = bitmap
                    binding.staticImageView.setImageBitmap(bitmap)
                }

                // Wait for IMAGE mode to be ready
                while (poseLandmarkerHelper?.isImageModeReady() != true) {
                    kotlinx.coroutines.delay(50)
                }

                val result = poseLandmarkerHelper?.detectPoseFromImage(bitmap)
                withContext(Dispatchers.Main) {
                    if (result != null) {
                        processPoseResult(result)
                    } else {
                        handleNoPose()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading image: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DebugActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun reanalyzeCurrentImage() {
        val bitmap = currentImageBitmap ?: return
        if (currentInputMode != InputMode.IMAGE) return

        mainScope.launch(Dispatchers.IO) {
            val result = poseLandmarkerHelper?.detectPoseFromImage(bitmap)
            withContext(Dispatchers.Main) {
                if (result != null) {
                    processPoseResult(result)
                } else {
                    handleNoPose()
                }
            }
        }
    }

    private fun decodeBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            val rotation = getExifRotation(uri)
            if (rotation != 0 && bitmap != null) {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated !== bitmap) bitmap.recycle()
                rotated
            } else {
                bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode bitmap: ${e.message}")
            null
        }
    }

    private fun getExifRotation(uri: Uri): Int {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return 0
            val exif = ExifInterface(inputStream)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            inputStream.close()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
    }

    // ==================== Unified Pose Processing Pipeline ====================

    /**
     * Central processing method used by all input modes (Camera, Video, Image).
     * Must be called on the Main thread.
     */
    private fun processPoseResult(result: PoseResult) {
        if (!::landmarkSmoother.isInitialized) return

        updateInferenceFps()

        val smoothedLandmarks = landmarkSmoother.smooth(result.landmarks, result.timestampMs)
        val worldLandmarks = result.worldLandmarks?.let {
            landmarkSmoother.convertWorld(it, result.timestampMs)
        }

        val rawAngles = if (worldLandmarks != null) {
            AngleCalculator.calculateAllAnglesSmoothed(worldLandmarks, visibilityThreshold = 0.5f, use3D = true)
        } else {
            AngleCalculator.calculateAllAnglesSmoothed(smoothedLandmarks, visibilityThreshold = 0.5f)
        }

        val correctedAngles = if (worldLandmarks != null) {
            elbowAngleEstimator.correct(rawAngles, worldLandmarks, smoothedLandmarks, result.timestampMs)
        } else rawAngles

        val angles = if (result.isFrontCamera) correctedAngles.mirrored() else correctedAngles
        latestLandmarks = smoothedLandmarks

        when (currentTab) {
            TAB_ELBOWS -> {
                updateElbowDebugDisplay(
                    angles = angles,
                    smoothedLandmarks = smoothedLandmarks,
                    worldLandmarks = worldLandmarks,
                    imageW = result.imageWidth,
                    imageH = result.imageHeight,
                    isFrontCamera = result.isFrontCamera
                )
            }
            TAB_ANGLE_DIAGNOSTICS -> {
                updateSelectedJointOverlay(angles, smoothedLandmarks, result.imageWidth, result.imageHeight, result.isFrontCamera)
                updateAngleDiagnosticsDisplay(
                    poseResult = result,
                    angles = angles,
                    smoothedLandmarks = smoothedLandmarks,
                    smoothedWorldLandmarks = worldLandmarks
                )
            }
            TAB_POSITION -> {
                val primaryIdx = JointLandmarkMapping.jointToLandmark(selectedPrimaryLandmark) ?: -1
                val secondaryIdx = JointLandmarkMapping.jointToLandmark(selectedSecondaryLandmark) ?: -1
                if (primaryIdx >= 0 && secondaryIdx >= 0) {
                    binding.skeletonOverlay.updateDebugJoint(
                        jointCode = "_position_check",
                        angle = null,
                        endpointA = primaryIdx,
                        endpointC = secondaryIdx,
                        vertexIdx = primaryIdx,
                        smoothedLandmarks = smoothedLandmarks,
                        imageW = result.imageWidth,
                        imageH = result.imageHeight,
                        useFrontCamera = result.isFrontCamera
                    )
                }
                updatePositionCheckDisplay(smoothedLandmarks, result.isFrontCamera)
            }
            TAB_CAMERA -> {
                updateCameraDetectionDisplay(smoothedLandmarks, result.imageWidth, result.imageHeight, result.isFrontCamera)
            }
            TAB_POSTURE_MLP -> {
                updatePostureMlpDisplay(smoothedLandmarks)
            }
        }
    }

    private fun handleNoPose() {
        clearOverlay()
        binding.tvLiveValue.text = "--"
        binding.tvLiveValue.setTextColor(Color.WHITE)
        if (currentTab == TAB_ELBOWS) {
            binding.tvStatus.text = "NO POSE"
            binding.tvStatus.setTextColor(Color.GRAY)
            binding.tvStatus.visibility = View.VISIBLE
            binding.tvDebugInfo.text = "No pose detected"
        }
        if (currentTab == TAB_ANGLE_DIAGNOSTICS) {
            binding.tvStatus.text = "NO POSE"
            binding.tvStatus.setTextColor(Color.GRAY)
            binding.tvStatus.visibility = View.VISIBLE
            binding.tvDebugInfo.text = "No pose detected"
        }
        if (currentTab == TAB_POSITION) {
            binding.tvStatus.text = "NO POSE"
            binding.tvStatus.setTextColor(Color.GRAY)
            binding.tvDebugInfo.text = "No pose detected"
        }
        if (currentTab == TAB_CAMERA) {
            binding.tvDebugInfo.text = "No pose detected"
        }
        if (currentTab == TAB_POSTURE_MLP) {
            binding.tvStatus.text = "NO POSE"
            binding.tvStatus.setTextColor(Color.GRAY)
            binding.tvDebugInfo.text = "No pose detected - MLP needs landmarks"
        }
    }

    // ==================== Pose Detection Callbacks (Camera mode only) ====================

    override fun onPoseDetected(result: PoseResult) {
        mainScope.launch(Dispatchers.Main) {
            if (currentInputMode != InputMode.CAMERA) return@launch
            processPoseResult(result)
        }
    }

    override fun onNoPoseDetected() {
        mainScope.launch(Dispatchers.Main) {
            if (currentInputMode != InputMode.CAMERA) return@launch
            updateInferenceFps()
            handleNoPose()
        }
    }

    override fun onError(message: String) {
        mainScope.launch(Dispatchers.Main) {
            Toast.makeText(this@DebugActivity, message, Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Pose detection error: $message")
        }
    }

    // ==================== Display Updates ====================

    private fun updateJointAngleDisplay(angles: JointAngles) {
        val value = angles.getAngle(selectedJointCode)
        if (value != null) {
            binding.tvLiveValue.text = "%.1f°".format(value)
            binding.tvLiveValue.setTextColor(Color.WHITE)
        } else {
            binding.tvLiveValue.text = "N/A"
            binding.tvLiveValue.setTextColor(Color.GRAY)
        }
    }

    private fun updateSelectedJointOverlay(
        angles: JointAngles,
        smoothedLandmarks: List<SmoothedLandmark>,
        imageW: Int,
        imageH: Int,
        isFrontCamera: Boolean
    ) {
        val angleLandmarks = JointLandmarkMapping.getLandmarksForAngle(selectedJointCode)
        if (angleLandmarks.size != 3) return

        binding.skeletonOverlay.updateDebugJoint(
            jointCode = selectedJointCode,
            angle = angles.getAngle(selectedJointCode),
            endpointA = angleLandmarks[0],
            endpointC = angleLandmarks[2],
            vertexIdx = angleLandmarks[1],
            smoothedLandmarks = smoothedLandmarks,
            imageW = imageW,
            imageH = imageH,
            useFrontCamera = isFrontCamera
        )
    }

    // ==================== Elbow Debug Display ====================

    private fun updateElbowDebugDisplay(
        angles: JointAngles,
        smoothedLandmarks: List<SmoothedLandmark>,
        worldLandmarks: List<SmoothedLandmark>?,
        imageW: Int,
        imageH: Int,
        isFrontCamera: Boolean
    ) {
        // Legacy MLP (26 features)
        if (elbowMlpRetryRequested) {
            elbowMlpRetryRequested = false
            ElbowCorrectionMlpClassifier.reload(this)
            ElbowFit3dV2Classifier.reload(this)
        }
        val legacyMlp = ElbowCorrectionMlpClassifier.getOrNull(this)
        var legacyResult: ElbowCorrectionMlpClassifier.TimedElbowResult? = null

        if (legacyMlp != null && worldLandmarks != null) {
            legacyResult = legacyMlp.predictBothElbows(smoothedLandmarks, worldLandmarks)
            elbowMlpLatencyRing.addLast(legacyResult.latencyMicros)
            while (elbowMlpLatencyRing.size > elbowMlpLatencyRingMax) elbowMlpLatencyRing.removeFirst()
        }

        // FIT3D v2 MLP (38 features, residual mode)
        val fit3dMlp = ElbowFit3dV2Classifier.getOrNull(this)
        var fit3dResult: ElbowFit3dV2Classifier.TimedElbowResult? = null

        if (fit3dMlp != null && worldLandmarks != null) {
            fit3dResult = fit3dMlp.predictBothElbows(smoothedLandmarks, worldLandmarks)
            fit3dLatencyRing.addLast(fit3dResult.latencyMicros)
            while (fit3dLatencyRing.size > fit3dLatencyRingMax) fit3dLatencyRing.removeFirst()
        }

        // Mirror for front camera
        val legacyScreenLeft = if (isFrontCamera) legacyResult?.rightAngle else legacyResult?.leftAngle
        val legacyScreenRight = if (isFrontCamera) legacyResult?.leftAngle else legacyResult?.rightAngle
        val fit3dScreenLeft = if (isFrontCamera) fit3dResult?.rightAngle else fit3dResult?.leftAngle
        val fit3dScreenRight = if (isFrontCamera) fit3dResult?.leftAngle else fit3dResult?.rightAngle
        val fit3dFeatLeft = if (isFrontCamera) fit3dResult?.rightFeatures else fit3dResult?.leftFeatures
        val fit3dFeatRight = if (isFrontCamera) fit3dResult?.leftFeatures else fit3dResult?.rightFeatures

        val diagScreenLeft = elbowAngleEstimator.lastDiagnostics.getOrNull(if (isFrontCamera) 1 else 0)
        val diagScreenRight = elbowAngleEstimator.lastDiagnostics.getOrNull(if (isFrontCamera) 0 else 1)

        // Live value: FIT3D v2 → Legacy MLP → Heuristic
        val displayLeft: Double?
        val displayRight: Double?
        val sourceLabel: String

        if (fit3dResult != null && (fit3dScreenLeft != null || fit3dScreenRight != null)) {
            displayLeft = fit3dScreenLeft?.toDouble()
            displayRight = fit3dScreenRight?.toDouble()
            sourceLabel = "FIT3D"
        } else if (legacyResult != null && (legacyScreenLeft != null || legacyScreenRight != null)) {
            displayLeft = legacyScreenLeft?.toDouble()
            displayRight = legacyScreenRight?.toDouble()
            sourceLabel = "MLP"
        } else {
            displayLeft = angles.leftElbow
            displayRight = angles.rightElbow
            sourceLabel = "Heuristic"
        }

        val leftStr = displayLeft?.let { "%.1f".format(it) } ?: "--"
        val rightStr = displayRight?.let { "%.1f".format(it) } ?: "--"
        binding.tvLiveValue.text = "L:${leftStr}° | R:${rightStr}°"
        binding.tvLiveValue.setTextColor(Color.WHITE)

        val overlayShIdx = if (isFrontCamera) BodyLandmarks.RIGHT_SHOULDER else BodyLandmarks.LEFT_SHOULDER
        val overlayElIdx = if (isFrontCamera) BodyLandmarks.RIGHT_ELBOW else BodyLandmarks.LEFT_ELBOW
        val overlayWrIdx = if (isFrontCamera) BodyLandmarks.RIGHT_WRIST else BodyLandmarks.LEFT_WRIST

        binding.skeletonOverlay.updateDebugJoint(
            jointCode = if (isFrontCamera) "right_elbow" else "left_elbow",
            angle = displayLeft,
            endpointA = overlayShIdx,
            endpointC = overlayWrIdx,
            vertexIdx = overlayElIdx,
            smoothedLandmarks = smoothedLandmarks,
            imageW = imageW,
            imageH = imageH,
            useFrontCamera = isFrontCamera
        )

        // Status bar
        val heuL = angles.leftElbow?.let { "%.0f".format(it) } ?: "-"
        val heuR = angles.rightElbow?.let { "%.0f".format(it) } ?: "-"
        val statusParts = mutableListOf("[$sourceLabel]")
        statusParts.add("Heur L:$heuL R:$heuR")
        if (diagScreenLeft != null) statusParts.add("L:${diagScreenLeft.strategy}")
        if (diagScreenRight != null) statusParts.add("R:${diagScreenRight.strategy}")
        binding.tvStatus.text = statusParts.joinToString(" | ")
        binding.tvStatus.setTextColor(Color.WHITE)
        binding.tvStatus.visibility = View.VISIBLE

        if (inferenceFrameCount % 2 == 0 || currentInputMode == InputMode.IMAGE) {
            binding.tvDebugInfo.text = buildElbowDebugPanel(
                angles, legacyMlp, legacyResult,
                legacyScreenLeft, legacyScreenRight,
                fit3dMlp, fit3dResult,
                fit3dScreenLeft, fit3dScreenRight,
                fit3dFeatLeft, fit3dFeatRight,
                diagScreenLeft, diagScreenRight,
            )
        }
    }

    private fun buildElbowDebugPanel(
        angles: JointAngles,
        legacyMlp: ElbowCorrectionMlpClassifier?,
        legacyResult: ElbowCorrectionMlpClassifier.TimedElbowResult?,
        legacyScreenLeft: Float?,
        legacyScreenRight: Float?,
        fit3dMlp: ElbowFit3dV2Classifier?,
        fit3dResult: ElbowFit3dV2Classifier.TimedElbowResult?,
        fit3dScreenLeft: Float?,
        fit3dScreenRight: Float?,
        fit3dFeatLeft: FloatArray?,
        fit3dFeatRight: FloatArray?,
        diagScreenLeft: ElbowDiagnostics?,
        diagScreenRight: ElbowDiagnostics?,
    ): String {
        val sb = StringBuilder()
        sb.appendLine("=== ELBOW DEBUG ===")
        sb.appendLine()

        // --- Comparison table per side ---
        for ((label, idx) in arrayOf("LEFT" to 0, "RIGHT" to 1)) {
            val heuristic = if (idx == 0) angles.leftElbow else angles.rightElbow
            val diag = if (idx == 0) diagScreenLeft else diagScreenRight
            val legacy = if (idx == 0) legacyScreenLeft else legacyScreenRight
            val fit3d = if (idx == 0) fit3dScreenLeft else fit3dScreenRight

            sb.appendLine("--- $label ELBOW ---")
            sb.appendLine("  FIT3D v2:    ${fit3d?.let { "%.1f°".format(it) } ?: "N/A"}")
            sb.appendLine("  Legacy MLP:  ${legacy?.let { "%.1f°".format(it) } ?: "N/A"}")
            sb.appendLine("  Heuristic:   ${formatAngleLong(heuristic)}")

            if (diag != null) {
                sb.appendLine("  Screen 2D:   ${formatAngleLong(diag.screenAngle)}")
                sb.appendLine("  World 3D:    ${formatAngleLong(diag.worldAngle)}")
                sb.appendLine("  Strategy:    ${diag.strategy}")
                sb.appendLine("  dzShare:     UA=%.3f FA=%.3f".format(diag.uaDzShare, diag.faDzShare))
                sb.appendLine("  Facing:      %.3f".format(diag.facingRatio))
            }

            if (fit3d != null && legacy != null) {
                sb.appendLine("  FIT3D-Legacy: %+.1f°".format(fit3d - legacy))
            }
            if (fit3d != null && heuristic != null) {
                sb.appendLine("  FIT3D-Heur:   %+.1f°".format(fit3d - heuristic))
            }
            sb.appendLine()
        }

        // --- FIT3D v2 Status ---
        sb.appendLine("--- FIT3D v2 STATUS ---")
        if (fit3dMlp == null) {
            sb.appendLine("Model:  NOT LOADED")
            val err = ElbowFit3dV2Classifier.lastError
            if (err != null) sb.appendLine("Error:  $err")
            sb.appendLine("Tap Live Value to retry")
            binding.tvLiveValue.setOnClickListener {
                elbowMlpRetryRequested = true
                Toast.makeText(this, "Retrying model load...", Toast.LENGTH_SHORT).show()
            }
        } else {
            binding.tvLiveValue.setOnClickListener(null)
            sb.appendLine("Model:  LOADED (38-D, residual)")
            if (fit3dResult != null) {
                val lastMs = fit3dResult.latencyMicros / 1000.0
                val avgMicros = if (fit3dLatencyRing.isNotEmpty())
                    fit3dLatencyRing.sumOf { it } / fit3dLatencyRing.size.toDouble() else 0.0
                sb.appendLine("Latency: %.3f ms (avg %.3f ms)".format(lastMs, avgMicros / 1000.0))
            }
        }
        sb.appendLine()

        // --- Legacy MLP Status ---
        sb.appendLine("--- Legacy MLP STATUS ---")
        if (legacyMlp == null) {
            sb.appendLine("Model:  NOT LOADED")
            val err = ElbowCorrectionMlpClassifier.lastError
            if (err != null) sb.appendLine("Error:  $err")
        } else {
            sb.appendLine("Model:  LOADED (26-D, sigmoid)")
            if (legacyResult != null) {
                val lastMs = legacyResult.latencyMicros / 1000.0
                val avgMicros = if (elbowMlpLatencyRing.isNotEmpty())
                    elbowMlpLatencyRing.sumOf { it } / elbowMlpLatencyRing.size.toDouble() else 0.0
                sb.appendLine("Latency: %.3f ms (avg %.3f ms)".format(lastMs, avgMicros / 1000.0))
            }
        }
        sb.appendLine()

        // --- FIT3D Feature Table ---
        for ((sideName, feats) in arrayOf("LEFT" to fit3dFeatLeft, "RIGHT" to fit3dFeatRight)) {
            if (feats != null) {
                sb.appendLine("--- $sideName FEATURES (38-D) ---")
                for (i in feats.indices) {
                    val name = ElbowFit3dV2FeatureExtractor.FEATURE_NAMES.getOrElse(i) { "f$i" }
                    sb.appendLine("  [%2d] %-20s %8.4f".format(i, name, feats[i]))
                }
                sb.appendLine()
            }
        }

        return sb.toString()
    }

    private fun updateAngleDiagnosticsDisplay(
        poseResult: PoseResult,
        angles: JointAngles,
        smoothedLandmarks: List<SmoothedLandmark>,
        smoothedWorldLandmarks: List<SmoothedLandmark>?
    ) {
        val diagnostics = buildAngleDiagnosticsData(
            poseResult = poseResult,
            angles = angles,
            smoothedLandmarks = smoothedLandmarks,
            smoothedWorldLandmarks = smoothedWorldLandmarks
        )

        if (diagnostics == null) {
            binding.tvLiveValue.text = "N/A"
            binding.tvLiveValue.setTextColor(Color.GRAY)
            binding.tvStatus.visibility = View.VISIBLE
            binding.tvStatus.text = "Angle mapping unavailable"
            binding.tvStatus.setTextColor(Color.GRAY)
            binding.tvDebugInfo.text = "Selected joint does not have a 3-point angle mapping."
            return
        }

        val displayedAngle = diagnostics.displayedAngle
        if (displayedAngle != null) {
            binding.tvLiveValue.text = "%.1f°".format(displayedAngle)
            binding.tvLiveValue.setTextColor(Color.WHITE)
        } else {
            binding.tvLiveValue.text = "N/A"
            binding.tvLiveValue.setTextColor(Color.GRAY)
        }

        binding.tvStatus.visibility = View.VISIBLE
        binding.tvStatus.text = buildAngleDiagnosticsSummary(diagnostics)
        binding.tvStatus.setTextColor(Color.WHITE)

        if (inferenceFrameCount % 2 == 0 || currentInputMode == InputMode.IMAGE) {
            binding.tvDebugInfo.text = buildAngleDiagnosticsPanelText(diagnostics)
        }
    }

    private fun buildAngleDiagnosticsData(
        poseResult: PoseResult,
        angles: JointAngles,
        smoothedLandmarks: List<SmoothedLandmark>,
        smoothedWorldLandmarks: List<SmoothedLandmark>?
    ): AngleDiagnosticsData? {
        val mappedIndices = JointLandmarkMapping.getLandmarksForAngle(selectedJointCode)
        if (mappedIndices.size != 3) return null

        val effectiveIndices = if (poseResult.isFrontCamera) {
            mappedIndices.map { BodyLandmarks.getMirroredIndex(it) }
        } else {
            mappedIndices
        }

        val sourceJointCode = if (poseResult.isFrontCamera) {
            getMirroredJointCode(selectedJointCode)
        } else {
            selectedJointCode
        }

        val normalizedRaw = buildAngleDebugFrame(effectiveIndices) { index ->
            poseResult.landmarks.getOrNull(index)?.toAngleDebugPoint(index)
        }
        val normalizedSmoothed = buildAngleDebugFrame(effectiveIndices) { index ->
            smoothedLandmarks.getOrNull(index)?.toAngleDebugPoint(index)
        }
        val worldRaw = poseResult.worldLandmarks?.let { rawWorldLandmarks ->
            buildAngleDebugFrame(effectiveIndices) { index ->
                rawWorldLandmarks.getOrNull(index)?.toAngleDebugPoint(index)
            }
        }
        val worldSmoothed = smoothedWorldLandmarks?.let { smoothedWorld ->
            buildAngleDebugFrame(effectiveIndices) { index ->
                smoothedWorld.getOrNull(index)?.toAngleDebugPoint(index)
            }
        }

        return AngleDiagnosticsData(
            displayJointCode = selectedJointCode,
            sourceJointCode = sourceJointCode,
            effectiveIndices = effectiveIndices,
            displayedAngle = angles.getAngle(selectedJointCode),
            pipelineSourceLabel = if (worldSmoothed != null) "World XYZ" else "Screen XY fallback",
            normalizedRaw = normalizedRaw,
            normalizedSmoothed = normalizedSmoothed,
            worldRaw = worldRaw,
            worldSmoothed = worldSmoothed
        )
    }

    private fun buildAngleDebugFrame(
        indices: List<Int>,
        pointProvider: (Int) -> AngleDebugPoint?
    ): AngleDebugFrame? {
        if (indices.size != 3) return null

        val pointA = pointProvider(indices[0]) ?: return null
        val pointB = pointProvider(indices[1]) ?: return null
        val pointC = pointProvider(indices[2]) ?: return null

        return AngleDebugFrame(
            pointA = pointA,
            pointB = pointB,
            pointC = pointC,
            xyAngle = calculateAngle2D(pointA.x, pointA.y, pointB.x, pointB.y, pointC.x, pointC.y),
            xzAngle = calculateAngle2D(pointA.x, pointA.z, pointB.x, pointB.z, pointC.x, pointC.z),
            yzAngle = calculateAngle2D(pointA.y, pointA.z, pointB.y, pointB.z, pointC.y, pointC.z),
            xyzAngle = calculateAngle3D(pointA, pointB, pointC),
            segmentBA = calculateSegmentMetrics(pointB, pointA),
            segmentBC = calculateSegmentMetrics(pointB, pointC)
        )
    }

    private fun buildAngleDiagnosticsSummary(data: AngleDiagnosticsData): String {
        val normalizedAngle = data.normalizedSmoothed?.xyAngle
        val worldAngle = data.worldSmoothed?.xyzAngle

        return when {
            normalizedAngle != null && worldAngle != null -> {
                "2D ${formatAngleShort(normalizedAngle)} | 3D ${formatAngleShort(worldAngle)} | Delta ${formatAngleDelta(worldAngle, normalizedAngle)}"
            }
            normalizedAngle != null -> {
                "2D ${formatAngleShort(normalizedAngle)} | no world landmarks"
            }
            else -> {
                "Angle data unavailable"
            }
        }
    }

    private fun buildAngleDiagnosticsPanelText(data: AngleDiagnosticsData): String {
        val sb = StringBuilder()
        val visibilityReference = data.worldSmoothed ?: data.normalizedSmoothed

        sb.appendLine("=== ANGLE DIAGNOSTICS ===")
        sb.appendLine()
        sb.appendLine("Display joint: ${data.displayJointCode}")
        if (data.displayJointCode == data.sourceJointCode) {
            sb.appendLine("Source joint:  ${data.sourceJointCode}")
        } else {
            sb.appendLine("Source joint:  ${data.sourceJointCode} (front camera correction)")
        }
        sb.appendLine(
            "Landmarks:     A=${formatPointRef(data.effectiveIndices[0])} | " +
                "B=${formatPointRef(data.effectiveIndices[1])} | " +
                "C=${formatPointRef(data.effectiveIndices[2])}"
        )
        sb.appendLine()

        sb.appendLine("--- SUMMARY ---")
        sb.appendLine("Pipeline:      ${data.pipelineSourceLabel}")
        sb.appendLine("Displayed:     ${formatAngleLong(data.displayedAngle)}")
        sb.appendLine("Gate @0.50:    ${formatVisibilityGate(visibilityReference)}")
        sb.appendLine("Norm XY raw:   ${formatAngleLong(data.normalizedRaw?.xyAngle)}")
        sb.appendLine("Norm XY smth:  ${formatAngleLong(data.normalizedSmoothed?.xyAngle)}")
        sb.appendLine("World XY raw:  ${formatAngleLong(data.worldRaw?.xyAngle)}")
        sb.appendLine("World XY smth: ${formatAngleLong(data.worldSmoothed?.xyAngle)}")
        sb.appendLine("World XYZ raw: ${formatAngleLong(data.worldRaw?.xyzAngle)}")
        sb.appendLine("World XYZ smth:${formatAngleLong(data.worldSmoothed?.xyzAngle)}")
        sb.appendLine("3D - 2D:       ${formatAngleDelta(data.worldSmoothed?.xyzAngle, data.normalizedSmoothed?.xyAngle)}")
        sb.appendLine("3D - world XY: ${formatAngleDelta(data.worldSmoothed?.xyzAngle, data.worldSmoothed?.xyAngle)}")
        sb.appendLine("Norm drift:    ${formatAngleDelta(data.normalizedSmoothed?.xyAngle, data.normalizedRaw?.xyAngle)}")
        sb.appendLine("World drift:   ${formatAngleDelta(data.worldSmoothed?.xyzAngle, data.worldRaw?.xyzAngle)}")

        data.worldSmoothed?.let { worldFrame ->
            sb.appendLine()
            sb.appendLine("--- WORLD PROJECTIONS ---")
            sb.appendLine("XY:            ${formatAngleLong(worldFrame.xyAngle)}")
            sb.appendLine("XZ:            ${formatAngleLong(worldFrame.xzAngle)}")
            sb.appendLine("YZ:            ${formatAngleLong(worldFrame.yzAngle)}")
        }

        data.normalizedSmoothed?.let { normalizedFrame ->
            sb.appendLine()
            sb.appendLine("--- SCREEN SEGMENTS (SMOOTHED) ---")
            appendSegmentMetrics(
                sb,
                "B->A ${normalizedFrame.pointB.name} -> ${normalizedFrame.pointA.name}",
                normalizedFrame.segmentBA,
                includeDepth = false
            )
            appendSegmentMetrics(
                sb,
                "B->C ${normalizedFrame.pointB.name} -> ${normalizedFrame.pointC.name}",
                normalizedFrame.segmentBC,
                includeDepth = false
            )
        }

        data.worldSmoothed?.let { worldFrame ->
            sb.appendLine()
            sb.appendLine("--- WORLD SEGMENTS (SMOOTHED) ---")
            appendSegmentMetrics(
                sb,
                "B->A ${worldFrame.pointB.name} -> ${worldFrame.pointA.name}",
                worldFrame.segmentBA,
                includeDepth = true
            )
            appendSegmentMetrics(
                sb,
                "B->C ${worldFrame.pointB.name} -> ${worldFrame.pointC.name}",
                worldFrame.segmentBC,
                includeDepth = true
            )
        }

        sb.appendLine()
        sb.appendLine("--- RAW -> SMOOTHED SHIFT ---")
        appendShiftSummary(
            sb,
            "Norm XY",
            data.normalizedRaw,
            data.normalizedSmoothed,
            includeDepth = false
        )
        appendShiftSummary(
            sb,
            "World XYZ",
            data.worldRaw,
            data.worldSmoothed,
            includeDepth = true
        )

        appendPointSection(sb, "NORM RAW", data.normalizedRaw)
        appendPointSection(sb, "NORM SMOOTHED", data.normalizedSmoothed)
        appendPointSection(sb, "WORLD RAW", data.worldRaw)
        appendPointSection(sb, "WORLD SMOOTHED", data.worldSmoothed)

        appendElbowPipelineDiagnostics(sb, data.sourceJointCode)

        return sb.toString()
    }

    private fun appendElbowPipelineDiagnostics(sb: StringBuilder, jointCode: String) {
        val side = when (jointCode) {
            "left_elbow" -> 0
            "right_elbow" -> 1
            else -> return
        }
        val diag = elbowAngleEstimator.lastDiagnostics.getOrNull(side) ?: return

        sb.appendLine()
        sb.appendLine("--- ELBOW PIPELINE ---")
        sb.appendLine("Strategy:      ${diag.strategy}")
        sb.appendLine("Facing ratio:  %.3f (1=front 0=side)".format(diag.facingRatio))
        sb.appendLine("Screen 2D:     ${formatAngleLong(diag.screenAngle)}")
        sb.appendLine("World 3D:      ${formatAngleLong(diag.worldAngle)}")
        sb.appendLine("dzShare:       UA=%.3f  FA=%.3f  max=%.3f".format(diag.uaDzShare, diag.faDzShare, diag.maxDzShare))
        sb.appendLine("Correction:    %.0f%%".format(kotlin.math.abs(diag.correctionPct) * 100))
        sb.appendLine("Output:        ${formatAngleLong(diag.outputAngle)}")
        sb.appendLine("Holding:       ${if (diag.isHolding) "YES" else "no"}")
    }

    private fun appendSegmentMetrics(
        sb: StringBuilder,
        label: String,
        metrics: AngleSegmentMetrics,
        includeDepth: Boolean
    ) {
        sb.appendLine(label)
        if (includeDepth) {
            sb.appendLine(
                "  dx=${formatScalar(metrics.dx)} dy=${formatScalar(metrics.dy)} dz=${formatScalar(metrics.dz)}"
            )
            sb.appendLine(
                "  lenXY=${formatScalar(metrics.length2D)} lenXYZ=${formatScalar(metrics.length3D)}"
            )
            sb.appendLine(
                "  |dz|/XYZ=${formatRatio(metrics.depthShare)}  XY/XYZ=${formatRatio(metrics.planarRatio)}"
            )
        } else {
            sb.appendLine(
                "  dx=${formatScalar(metrics.dx)} dy=${formatScalar(metrics.dy)}"
            )
            sb.appendLine("  lenXY=${formatScalar(metrics.length2D)}")
        }
    }

    private fun appendShiftSummary(
        sb: StringBuilder,
        label: String,
        rawFrame: AngleDebugFrame?,
        smoothedFrame: AngleDebugFrame?,
        includeDepth: Boolean
    ) {
        if (rawFrame == null || smoothedFrame == null) {
            sb.appendLine("$label:        N/A")
            return
        }

        val shiftA = calculatePointShift(rawFrame.pointA, smoothedFrame.pointA, includeDepth)
        val shiftB = calculatePointShift(rawFrame.pointB, smoothedFrame.pointB, includeDepth)
        val shiftC = calculatePointShift(rawFrame.pointC, smoothedFrame.pointC, includeDepth)
        sb.appendLine(
            "$label:        A=${formatScalar(shiftA)} B=${formatScalar(shiftB)} C=${formatScalar(shiftC)}"
        )
    }

    private fun appendPointSection(
        sb: StringBuilder,
        label: String,
        frame: AngleDebugFrame?
    ) {
        sb.appendLine()
        sb.appendLine("--- $label ---")
        if (frame == null) {
            sb.appendLine("Unavailable")
            return
        }

        appendPointInfo(sb, "A", frame.pointA)
        appendPointInfo(sb, "B", frame.pointB)
        appendPointInfo(sb, "C", frame.pointC)
        sb.appendLine("Min vis: ${formatRatio(frame.minVisibility.toDouble())}  Min pres: ${formatRatio(frame.minPresence.toDouble())}")
    }

    private fun appendPointInfo(
        sb: StringBuilder,
        label: String,
        point: AngleDebugPoint
    ) {
        sb.appendLine("$label[${point.index}] ${point.name}")
        sb.appendLine(
            "  x=${formatScalar(point.x.toDouble())} y=${formatScalar(point.y.toDouble())} z=${formatScalar(point.z.toDouble())}"
        )
        sb.appendLine(
            "  vis=${formatRatio(point.visibility.toDouble())} pres=${formatRatio(point.presence.toDouble())}"
        )
    }

    private fun calculatePointShift(
        rawPoint: AngleDebugPoint,
        smoothedPoint: AngleDebugPoint,
        includeDepth: Boolean
    ): Double {
        val dx = (smoothedPoint.x - rawPoint.x).toDouble()
        val dy = (smoothedPoint.y - rawPoint.y).toDouble()
        val dz = (smoothedPoint.z - rawPoint.z).toDouble()
        return if (includeDepth) {
            kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
        } else {
            kotlin.math.sqrt(dx * dx + dy * dy)
        }
    }

    private fun calculateSegmentMetrics(
        from: AngleDebugPoint,
        to: AngleDebugPoint
    ): AngleSegmentMetrics {
        val dx = (to.x - from.x).toDouble()
        val dy = (to.y - from.y).toDouble()
        val dz = (to.z - from.z).toDouble()
        val length2D = kotlin.math.sqrt(dx * dx + dy * dy)
        val length3D = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
        return AngleSegmentMetrics(
            dx = dx,
            dy = dy,
            dz = dz,
            length2D = length2D,
            length3D = length3D
        )
    }

    private fun calculateAngle2D(
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float,
        cx: Float,
        cy: Float
    ): Double? {
        val baX = (ax - bx).toDouble()
        val baY = (ay - by).toDouble()
        val bcX = (cx - bx).toDouble()
        val bcY = (cy - by).toDouble()
        val magBA = kotlin.math.sqrt(baX * baX + baY * baY)
        val magBC = kotlin.math.sqrt(bcX * bcX + bcY * bcY)
        if (magBA == 0.0 || magBC == 0.0) return null

        var angle = Math.toDegrees(
            kotlin.math.atan2(baY, baX) - kotlin.math.atan2(bcY, bcX)
        )
        angle = kotlin.math.abs(angle)
        if (angle > 180.0) angle = 360.0 - angle
        return angle
    }

    private fun calculateAngle3D(
        pointA: AngleDebugPoint,
        pointB: AngleDebugPoint,
        pointC: AngleDebugPoint
    ): Double? {
        val baX = (pointA.x - pointB.x).toDouble()
        val baY = (pointA.y - pointB.y).toDouble()
        val baZ = (pointA.z - pointB.z).toDouble()
        val bcX = (pointC.x - pointB.x).toDouble()
        val bcY = (pointC.y - pointB.y).toDouble()
        val bcZ = (pointC.z - pointB.z).toDouble()

        val magBA = kotlin.math.sqrt(baX * baX + baY * baY + baZ * baZ)
        val magBC = kotlin.math.sqrt(bcX * bcX + bcY * bcY + bcZ * bcZ)
        if (magBA == 0.0 || magBC == 0.0) return null

        val dot = baX * bcX + baY * bcY + baZ * bcZ
        val cosAngle = (dot / (magBA * magBC)).coerceIn(-1.0, 1.0)
        return Math.toDegrees(kotlin.math.acos(cosAngle))
    }

    private fun NormalizedLandmark.toAngleDebugPoint(index: Int): AngleDebugPoint {
        return AngleDebugPoint(
            index = index,
            name = BodyLandmarks.getName(index),
            x = x(),
            y = y(),
            z = z(),
            visibility = visibility().orElse(0f),
            presence = presence().orElse(0f)
        )
    }

    private fun Landmark.toAngleDebugPoint(index: Int): AngleDebugPoint {
        return AngleDebugPoint(
            index = index,
            name = BodyLandmarks.getName(index),
            x = x(),
            y = y(),
            z = z(),
            visibility = visibility().orElse(0f),
            presence = presence().orElse(0f)
        )
    }

    private fun SmoothedLandmark.toAngleDebugPoint(index: Int): AngleDebugPoint {
        return AngleDebugPoint(
            index = index,
            name = BodyLandmarks.getName(index),
            x = x,
            y = y,
            z = z,
            visibility = visibility,
            presence = presence
        )
    }

    private fun getMirroredJointCode(jointCode: String): String {
        return when {
            jointCode == "neck_left" -> "neck_right"
            jointCode == "neck_right" -> "neck_left"
            jointCode.startsWith("left_") -> "right_${jointCode.removePrefix("left_")}"
            jointCode.startsWith("right_") -> "left_${jointCode.removePrefix("right_")}"
            else -> jointCode
        }
    }

    private fun formatAngleShort(value: Double): String = "%.1f°".format(value)

    private fun formatAngleLong(value: Double?): String =
        value?.let { "%.1f°".format(it) } ?: "N/A"

    private fun formatAngleDelta(primary: Double?, secondary: Double?): String {
        if (primary == null || secondary == null) return "N/A"
        return "%+.1f°".format(primary - secondary)
    }

    private fun formatScalar(value: Double): String = "%.4f".format(value)

    private fun formatRatio(value: Double): String = "%.3f".format(value)

    private fun formatPointRef(index: Int): String = "$index ${BodyLandmarks.getName(index)}"

    private fun formatVisibilityGate(frame: AngleDebugFrame?): String {
        val minVisibility = frame?.minVisibility ?: return "N/A"
        val gateStatus = if (minVisibility >= 0.5f) "PASS" else "FAIL"
        return "$gateStatus (min vis ${formatRatio(minVisibility.toDouble())})"
    }

    private fun updatePositionCheckDisplay(landmarks: List<SmoothedLandmark>, isFrontCamera: Boolean) {
        val validator = positionValidator ?: run {
            rebuildPositionValidator()
            positionValidator
        } ?: return

        val result = validator.validate(
            landmarks = landmarks,
            currentPhase = Phase.IDLE,
            isBilateralFlipped = false,
            isFrontCamera = isFrontCamera
        )

        val allIssues = result.getAllIssues()
        if (allIssues.isEmpty()) {
            binding.tvLiveValue.text = "PASS"
            binding.tvLiveValue.setTextColor(Color.GREEN)
            binding.tvStatus.text = "PASS"
            binding.tvStatus.setTextColor(Color.GREEN)
            binding.tvStatus.visibility = View.VISIBLE
        } else {
            val issue = allIssues.first()
            binding.tvLiveValue.text = "FAIL"
            binding.tvLiveValue.setTextColor(Color.RED)
            binding.tvStatus.text = "FAIL | actual: %.4f | threshold: %.4f".format(issue.actualValue, issue.threshold)
            binding.tvStatus.setTextColor(Color.RED)
            binding.tvStatus.visibility = View.VISIBLE
        }

        if (inferenceFrameCount % 2 == 0 || currentInputMode == InputMode.IMAGE) {
            updateDebugInfoPanel(landmarks, result, allIssues)
        }
    }

    private fun updateDebugInfoPanel(
        landmarks: List<SmoothedLandmark>,
        result: PositionValidationResult,
        issues: List<PositionError>
    ) {
        val sb = StringBuilder()

        val statusStr = if (issues.isEmpty()) "PASS" else "FAIL"
        sb.appendLine("=== RESULT: $statusStr ===")
        sb.appendLine()

        sb.appendLine("--- LANDMARKS ---")
        appendLandmarkInfo(sb, "Primary", selectedPrimaryLandmark, landmarks)
        appendLandmarkInfo(sb, "Secondary", selectedSecondaryLandmark, landmarks)
        sb.appendLine()

        sb.appendLine("--- COMPARISON ---")
        sb.appendLine("Type: ${selectedCheckType.name}")
        sb.appendLine("Operator: ${selectedOperator.name}")
        sb.appendLine("Threshold: %.4f".format(selectedThreshold))

        if (issues.isNotEmpty()) {
            val issue = issues.first()
            sb.appendLine("Actual: %.4f".format(issue.actualValue))
            val delta = issue.actualValue - issue.threshold
            sb.appendLine("Delta: %.4f".format(delta))
        } else {
            val rawDiff = computeRawDiff(landmarks)
            if (rawDiff != null) {
                sb.appendLine("Actual: %.4f".format(rawDiff))
                val delta = rawDiff - selectedThreshold
                sb.appendLine("Delta: %.4f".format(delta))
            }
        }
        sb.appendLine()

        sb.appendLine("--- SCENE ---")
        val sceneResult = positionValidator?.getLastSceneResult()
        val camResult = positionValidator?.getLastCameraResult()
        if (sceneResult != null) {
            sb.appendLine("Direction: ${sceneResult.direction}")
            sb.appendLine("Posture:   ${sceneResult.posture} (%.1f°)".format(sceneResult.bodyAxisAngleDeg))
            sb.appendLine("Region:    ${sceneResult.region}")
            sb.appendLine("Facing:    ${sceneResult.facing}")
            sb.appendLine("Active axis: ${getActiveAxis(selectedCheckType, sceneResult.direction)}")
        } else if (camResult != null) {
            sb.appendLine("Direction: ${camResult.position}")
            sb.appendLine("Facing:    ${camResult.facingDirection}")
        } else {
            sb.appendLine("(no scene result)")
        }
        sb.appendLine()

        sb.appendLine("--- RESULT DETAIL ---")
        sb.appendLine("Detected dir: ${result.detectedCameraPosition}")
        sb.appendLine("Detected face: ${result.detectedFacing}")
        for (w in result.sceneWarnings) {
            sb.appendLine("Scene warn [${w.axis}]: ${w.message.en}")
        }

        binding.tvDebugInfo.text = sb.toString()
    }

    private fun appendLandmarkInfo(sb: StringBuilder, label: String, name: String, landmarks: List<SmoothedLandmark>) {
        val index = JointLandmarkMapping.jointToLandmark(name)
        if (index != null && index < landmarks.size) {
            val lm = landmarks[index]
            sb.appendLine("$label: $name")
            sb.appendLine("  x: %.4f  y: %.4f".format(lm.x, lm.y))
            sb.appendLine("  z: %.4f  vis: %.2f".format(lm.z, lm.visibility))
        } else {
            sb.appendLine("$label: $name [NOT FOUND]")
        }
    }

    private fun computeRawDiff(landmarks: List<SmoothedLandmark>): Double? {
        val pi = JointLandmarkMapping.jointToLandmark(selectedPrimaryLandmark) ?: return null
        val si = JointLandmarkMapping.jointToLandmark(selectedSecondaryLandmark) ?: return null
        if (pi >= landmarks.size || si >= landmarks.size) return null

        val primary = landmarks[pi]
        val secondary = landmarks[si]

        return when (selectedCheckType) {
            PositionCheckType.VERTICAL_COMPARISON -> (primary.y - secondary.y).toDouble()
            PositionCheckType.FORWARD_COMPARISON -> (primary.x - secondary.x).toDouble()
            PositionCheckType.SIDEWAYS_COMPARISON -> (primary.z - secondary.z).toDouble()
            PositionCheckType.HORIZONTAL_ALIGNMENT -> kotlin.math.abs(primary.y - secondary.y).toDouble()
            PositionCheckType.VERTICAL_ALIGNMENT -> kotlin.math.abs(primary.x - secondary.x).toDouble()
            PositionCheckType.DEPTH_ALIGNMENT -> kotlin.math.abs(primary.z - secondary.z).toDouble()
            PositionCheckType.DISTANCE_RATIO -> null
        }
    }

    private fun getActiveAxis(
        checkType: PositionCheckType,
        cameraPosition: CameraPositionDetector.DetectedCameraPosition
    ): String {
        return when (checkType) {
            PositionCheckType.VERTICAL_COMPARISON -> "Y"
            PositionCheckType.HORIZONTAL_ALIGNMENT -> "Y (range)"
            PositionCheckType.VERTICAL_ALIGNMENT -> "X (range)"
            PositionCheckType.DEPTH_ALIGNMENT -> "Z (abs diff)"
            PositionCheckType.DISTANCE_RATIO -> "3D distance ratio"
            PositionCheckType.FORWARD_COMPARISON -> {
                when (cameraPosition) {
                    CameraPositionDetector.DetectedCameraPosition.SIDE_VIEW_LEFT,
                    CameraPositionDetector.DetectedCameraPosition.SIDE_VIEW_RIGHT -> "X (forward/side)"
                    CameraPositionDetector.DetectedCameraPosition.FRONT_VIEW,
                    CameraPositionDetector.DetectedCameraPosition.BACK_VIEW -> "Z (forward/front)"
                    else -> "X (default)"
                }
            }
            PositionCheckType.SIDEWAYS_COMPARISON -> {
                when (cameraPosition) {
                    CameraPositionDetector.DetectedCameraPosition.SIDE_VIEW_LEFT,
                    CameraPositionDetector.DetectedCameraPosition.SIDE_VIEW_RIGHT -> "Z (sideways/side)"
                    else -> "X (sideways/front)"
                }
            }
        }
    }

    // ==================== Pose Scene Detection ====================

    private fun updateCameraDetectionDisplay(
        landmarks: List<SmoothedLandmark>,
        imageW: Int,
        imageH: Int,
        isFrontCamera: Boolean
    ) {
        val scene = sceneDetector.detect(landmarks, isFrontCamera)
        val stableResult = CameraPositionDetector.CameraDetectionResult(
            scene.direction, scene.directionConfidence, scene.facing, scene.closerSide, scene.depthInfo
        )

        val matchResult = buildExpectation().matchesScene(scene)

        binding.skeletonOverlay.updateCameraDetection(
            detectionResult = stableResult,
            matchesExpected = matchResult.allMatch,
            smoothedLandmarks = landmarks,
            imageW = imageW,
            imageH = imageH,
            useFrontCamera = isFrontCamera
        )

        if (inferenceFrameCount % 2 == 0 || currentInputMode == InputMode.IMAGE) {
            updatePoseSceneDebugPanel(landmarks, scene)
        }
    }

    private fun updatePoseSceneDebugPanel(
        landmarks: List<SmoothedLandmark>,
        scene: PoseSceneResult
    ) {
        val sb = StringBuilder()
        val exp = buildExpectation()
        val match = exp.matchesScene(scene)

        sb.appendLine("=== ${if (match.allMatch) "ALL MATCH" else "MISMATCH"} ===")
        sb.appendLine()

        sb.appendLine("--- AXIS 1: DIRECTION ---")
        sb.appendLine("Detected:  ${scene.direction}")
        sb.appendLine("Expected:  ${exp.directionLabel()}")
        sb.appendLine("Conf:      %.2f".format(scene.directionConfidence))
        sb.appendLine("Facing:    ${scene.facing}")
        sb.appendLine("Closer:    ${scene.closerSide}")
        sb.appendLine("Match:     ${if (match.directionMatch) "YES" else "NO"}")
        sb.appendLine()

        sb.appendLine("--- AXIS 2: POSTURE ---")
        sb.appendLine("Detected:  ${scene.posture}")
        sb.appendLine("Expected:  ${exp.postureLabel()}")
        sb.appendLine("Conf:      %.2f".format(scene.postureConfidence))
        sb.appendLine("Body axis: %.1f°".format(scene.bodyAxisAngleDeg))
        sb.appendLine("Match:     ${if (match.postureMatch) "YES" else "NO"}")
        sb.appendLine()

        sb.appendLine("--- AXIS 3: REGION ---")
        sb.appendLine("Detected:  ${scene.region}")
        sb.appendLine("Expected:  ${exp.regionLabel()}")
        sb.appendLine("Upper:     %.2f".format(scene.upperScore))
        sb.appendLine("Core:      %.2f".format(scene.coreScore))
        sb.appendLine("Lower:     %.2f".format(scene.lowerScore))
        sb.appendLine("Match:     ${if (match.regionMatch) "YES" else "NO"}")
        sb.appendLine()

        val metrics = CameraPositionDetector.computeMetrics(landmarks)
        if (metrics != null) {
            sb.appendLine("--- PRIMARY (2D, rotation-invariant) ---")
            sb.appendLine("Width ratio:  %.3f".format(metrics.widthRatio))
            sb.appendLine("Cross score:  %.3f".format(metrics.crossScore))
            sb.appendLine("Vis L-R diff: %.3f".format(metrics.visibilityDiff))
            sb.appendLine()
            sb.appendLine("--- SECONDARY (Z) ---")
            sb.appendLine("X/Z ratio:    %.3f (sh) %.3f (hip) %.3f (avg)".format(
                metrics.xzRatio, metrics.hipXZRatio, metrics.combinedRatio))
            sb.appendLine("Side Z diff:  %.4f".format(metrics.sideZDiff))
            sb.appendLine()
            sb.appendLine("--- RAW VALUES ---")
            sb.appendLine("Shoulder 2D:  %.4f  Hip 2D: %.4f".format(metrics.shoulderWidth2D, metrics.hipWidth2D))
            sb.appendLine("Shoulder Z:   %.4f".format(metrics.shoulderZDiff))
            sb.appendLine("Torso len:    %.4f".format(metrics.torsoLength))
            sb.appendLine("Face score:   %.3f".format(metrics.faceScore))
        }

        sb.appendLine()
        sb.appendLine("--- DEPTH ---")
        val d = scene.depthInfo
        sb.appendLine("L Shoulder Z: %.4f".format(d.leftShoulderZ))
        sb.appendLine("R Shoulder Z: %.4f".format(d.rightShoulderZ))
        sb.appendLine("Avg Body Z:   %.4f".format(d.averageBodyZ))
        sb.appendLine("Depth Range:  %.4f".format(d.bodyDepthRange))

        binding.tvDebugInfo.text = sb.toString()
    }

    // ==================== Posture MLP Debug ====================

    private val mlpClassLabels = arrayOf("Standing", "Sitting", "Lying")

    private val mlpFeatureNames = arrayOf(
        "spine_angle", "torso_len", "cos_torso_thigh",
        "knee_ang_L", "knee_ang_R",
        "shoulder_w", "hip_w",
        "knee_drop", "ankle_drop", "nose_off",
        "sh_v_sep", "hip_v_sep",
        "vis_knee", "vis_hip", "vis_sh",
        "z_torso"
    )

    private var mlpRetryRequested = false

    /** Rolling window of total MLP stack latency (µs) for Debug MLP tab. */
    private val mlpLatencyMicrosRing = ArrayDeque<Long>(32)
    private val mlpLatencyRingMax = 30

    private fun recordMlpLatencyMicros(totalMicros: Long): Double {
        mlpLatencyMicrosRing.addLast(totalMicros)
        while (mlpLatencyMicrosRing.size > mlpLatencyRingMax) {
            mlpLatencyMicrosRing.removeFirst()
        }
        return mlpLatencyMicrosRing.sumOf { it } / mlpLatencyMicrosRing.size.toDouble()
    }

    private fun updatePostureMlpDisplay(landmarks: List<SmoothedLandmark>) {
        if (mlpRetryRequested) {
            mlpRetryRequested = false
            PostureMlpClassifier.reload(this)
        }
        val classifier = PostureMlpClassifier.getOrNull(this)
        val ruleResult = BodyPostureDetector.detect(landmarks)

        if (classifier == null) {
            val rawFeatures = PostureMlpFeatureExtractor.computeFeatures(landmarks)
            binding.tvLiveValue.text = "NO MODEL"
            binding.tvLiveValue.setTextColor(Color.parseColor("#FF9800"))
            binding.tvStatus.text = "Model not loaded — tap Live Value to retry"
            binding.tvStatus.setTextColor(Color.parseColor("#FF9800"))
            binding.tvStatus.visibility = View.VISIBLE
            binding.tvLiveValue.setOnClickListener {
                mlpRetryRequested = true
                Toast.makeText(this, "Retrying model load…", Toast.LENGTH_SHORT).show()
            }

            val sb = StringBuilder()
            sb.appendLine("=== POSTURE MLP ===")
            sb.appendLine()
            sb.appendLine("Model:  NOT LOADED")
            val err = PostureMlpClassifier.lastError
            if (err != null) {
                sb.appendLine("Error:  $err")
            } else {
                sb.appendLine("Place posture_mlp.tflite & posture_mlp_norm.json")
                sb.appendLine("in app/src/main/assets/")
            }
            sb.appendLine()
            sb.appendLine("--- RULE-BASED FALLBACK ---")
            sb.appendLine("Posture:    ${ruleResult.posture}")
            sb.appendLine("Confidence: %.2f".format(ruleResult.confidence))
            sb.appendLine("Body axis:  %.1f°".format(ruleResult.bodyAxisAngleDeg))
            if (rawFeatures != null) {
                sb.appendLine()
                sb.appendLine("--- RAW FEATURES (16-D) ---")
                appendFeatureTable(sb, rawFeatures)
            }
            binding.tvDebugInfo.text = sb.toString()
            return
        }

        binding.tvLiveValue.setOnClickListener(null)

        val timed = classifier.predictFromLandmarksTimed(landmarks)
        if (timed == null) {
            binding.tvLiveValue.text = "--"
            binding.tvLiveValue.setTextColor(Color.GRAY)
            binding.tvStatus.text = "Features unavailable (torso too short?)"
            binding.tvStatus.setTextColor(Color.GRAY)
            binding.tvStatus.visibility = View.VISIBLE
            binding.tvDebugInfo.text = "Cannot compute feature vector."
            return
        }

        val prediction = timed.prediction
        val timings = timed.timings
        val rawFeatures = timed.rawFeatures
        val avgMicros = recordMlpLatencyMicros(timings.totalMicros)
        val lastMs = timings.totalMicros / 1000.0
        val avgMs = avgMicros / 1000.0
        val infPerSec = if (lastMs > 1e-6) (1000.0 / lastMs).toInt() else 0

        val label = mlpClassLabels.getOrElse(prediction.classIndex) { "?" }
        val conf = prediction.confidence
        val confColor = when {
            conf >= 0.8f -> Color.GREEN
            conf >= 0.5f -> Color.YELLOW
            else -> Color.RED
        }

        binding.tvLiveValue.text = "$label (${prediction.classIndex})"
        binding.tvLiveValue.setTextColor(confColor)

        val ruleCoarse = when (ruleResult.posture) {
            BodyPosture.STANDING -> "Standing"
            BodyPosture.SITTING -> "Sitting"
            BodyPosture.LYING_PRONE, BodyPosture.LYING_SUPINE, BodyPosture.LYING_SIDE -> "Lying"
            else -> "Unknown"
        }
        val agree = (ruleCoarse == label)
        val matchSymbol = if (agree) "AGREE" else "DISAGREE"
        val matchColor = if (agree) Color.GREEN else Color.parseColor("#FF9800")

        binding.tvStatus.text =
            "MLP=$label (%.0f%%) | Rule=$ruleCoarse | $matchSymbol | %.3fms (~%d/s)".format(
                conf * 100f,
                lastMs,
                infPerSec,
            )
        binding.tvStatus.setTextColor(matchColor)
        binding.tvStatus.visibility = View.VISIBLE

        if (inferenceFrameCount % 2 == 0 || currentInputMode == InputMode.IMAGE) {
            val sb = StringBuilder()
            sb.appendLine("=== POSTURE MLP ===")
            sb.appendLine()
            sb.appendLine("--- LATENCY ---")
            sb.appendLine("Total (last):   %.3f ms".format(lastMs))
            sb.appendLine("Rolling avg:    %.3f ms  (last %d frames)".format(avgMs, mlpLatencyMicrosRing.size))
            sb.appendLine("Features:       %.3f ms  (PostureMlpFeatureExtractor)".format(timings.featuresMicros / 1000.0))
            sb.appendLine("Classifier:     %.3f ms  (norm + TFLite Interpreter.run)".format(timings.classifierMicros / 1000.0))
            sb.appendLine("Equiv. rate:    ~%d inf/s (from last total)".format(infPerSec))
            sb.appendLine()
            sb.appendLine("--- PREDICTION ---")
            sb.appendLine("Class:       $label (${prediction.classIndex})")
            sb.appendLine("Confidence:  %.1f%%".format(conf * 100f))
            sb.appendLine()
            sb.appendLine("Probabilities:")
            for (i in prediction.probabilities.indices) {
                val pLabel = mlpClassLabels.getOrElse(i) { "?" }
                val pct = prediction.probabilities[i] * 100f
                val bar = "█".repeat((pct / 5f).toInt().coerceAtMost(20))
                sb.appendLine("  [$i] %-9s %5.1f%%  %s".format(pLabel, pct, bar))
            }
            sb.appendLine()

            sb.appendLine("--- RULE-BASED COMPARISON ---")
            sb.appendLine("Rule posture:  ${ruleResult.posture}")
            sb.appendLine("Rule conf:     %.2f".format(ruleResult.confidence))
            sb.appendLine("Body axis:     %.1f°".format(ruleResult.bodyAxisAngleDeg))
            sb.appendLine("Agreement:     $matchSymbol")
            sb.appendLine()

            sb.appendLine("--- RAW FEATURES (16-D) ---")
            appendFeatureTable(sb, rawFeatures)

            binding.tvDebugInfo.text = sb.toString()
        }
    }

    private fun appendFeatureTable(sb: StringBuilder, features: FloatArray) {
        for (i in features.indices) {
            val name = mlpFeatureNames.getOrElse(i) { "f$i" }
            sb.appendLine("  [%2d] %-16s %8.4f".format(i, name, features[i]))
        }
    }

    // ==================== Position Validator ====================

    private fun rebuildPositionValidator() {
        val check = PositionCheck(
            id = "debug_check",
            type = selectedCheckType,
            landmarks = LandmarkGroup(
                primary = selectedPrimaryLandmark,
                secondary = selectedSecondaryLandmark
            ),
            condition = PositionCondition(
                operator = selectedOperator,
                threshold = selectedThreshold
            ),
            activePhases = listOf("idle", "start", "down", "bottom", "up"),
            errorMessage = LocalizedText(en = "Debug check failed"),
            severity = CheckSeverity.ERROR,
            cooldownMs = 0,
            minErrorFrames = 1
        )

        positionValidator = PositionValidator(
            positionChecks = listOf(check),
            posePositionCode = "standing_side",
            sceneExpectation = PoseSceneExpectation.fromLegacyCode("standing_side")
        )
    }

    // ==================== FPS ====================

    private fun updateInferenceFps() {
        inferenceFrameCount++
        if (currentInputMode != InputMode.CAMERA) return

        val currentTime = System.currentTimeMillis()
        val elapsed = currentTime - lastInferenceFpsTime
        if (elapsed >= 1000) {
            currentInferenceFps = inferenceFrameCount
            inferenceFrameCount = 0
            lastInferenceFpsTime = currentTime
            val supported = cameraManager?.diagSupportedRanges ?: "?"
            val applied = cameraManager?.diagAppliedRange ?: "?"
            binding.tvFps.text = "Cam:$currentCameraFps Inf:$currentInferenceFps | $applied of $supported"
        }
    }

    private fun updateCameraFps() {
        cameraFrameCount++
        val currentTime = System.currentTimeMillis()
        val elapsed = currentTime - lastCameraFpsTime
        if (elapsed >= 1000) {
            currentCameraFps = cameraFrameCount
            cameraFrameCount = 0
            lastCameraFpsTime = currentTime
        }
    }

    private fun resetFpsCounters() {
        inferenceFrameCount = 0
        cameraFrameCount = 0
        currentInferenceFps = 0
        currentCameraFps = 0
        lastInferenceFpsTime = System.currentTimeMillis()
        lastCameraFpsTime = System.currentTimeMillis()
    }

    // ==================== Helpers ====================

    private fun clearOverlay() {
        binding.skeletonOverlay.clearDebugMode()
        binding.skeletonOverlay.clearCameraDetectionMode()
        binding.skeletonOverlay.clear()
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    private fun copyCurrentDebugInfo() {
        val exportText = buildDebugExportText()
        if (exportText.isBlank()) {
            Toast.makeText(this, "No debug info to copy", Toast.LENGTH_SHORT).show()
            return
        }

        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("debug-info", exportText))
        Toast.makeText(this, "Debug info copied", Toast.LENGTH_SHORT).show()
    }

    private fun buildDebugExportText(): String {
        val sections = mutableListOf<String>()

        val modeLabel = when (currentTab) {
            TAB_ELBOWS -> "Elbows"
            TAB_ANGLE_DIAGNOSTICS -> "Angle Lab"
            TAB_POSITION -> "Positions"
            TAB_CAMERA -> "Scene"
            TAB_POSTURE_MLP -> "Posture MLP"
            else -> "Unknown"
        }
        sections.add("Mode: $modeLabel")

        val liveValue = binding.tvLiveValue.text?.toString()?.trim().orEmpty()
        if (liveValue.isNotEmpty() && liveValue != "--") {
            sections.add("Live Value: $liveValue")
        }

        val status = binding.tvStatus.text?.toString()?.trim().orEmpty()
        if (status.isNotEmpty()) {
            sections.add("Status: $status")
        }

        val panelText = binding.tvDebugInfo.text?.toString()?.trim().orEmpty()
        if (panelText.isNotEmpty()) {
            sections.add("")
            sections.add(panelText)
        }

        return sections.joinToString("\n").trim()
    }
}
