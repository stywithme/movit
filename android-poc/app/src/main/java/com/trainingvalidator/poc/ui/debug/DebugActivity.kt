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
import android.text.Editable
import android.text.TextWatcher
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
import androidx.appcompat.app.AlertDialog
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
import com.trainingvalidator.poc.PoseApp
import com.trainingvalidator.poc.analysis.AngleCalculator
import com.trainingvalidator.poc.analysis.ElbowAngleEstimator
import com.trainingvalidator.poc.analysis.JointAngles
import com.trainingvalidator.poc.analysis.LandmarkSmoother
import com.trainingvalidator.poc.analysis.SmoothedLandmark
import com.trainingvalidator.poc.camera.CameraManager
import com.trainingvalidator.poc.databinding.ActivityDebugBinding
import com.trainingvalidator.poc.overlay.SkeletonOverlayView
import com.trainingvalidator.poc.pose.BodyLandmarks
import com.trainingvalidator.poc.pose.JointLandmarkMapping
import com.trainingvalidator.poc.pose.ModelType
import com.trainingvalidator.poc.pose.PoseLandmarkerHelper
import com.trainingvalidator.poc.pose.PoseResult
import com.trainingvalidator.poc.training.config.SettingsManager
import com.trainingvalidator.poc.training.engine.CameraPositionDetector
import com.trainingvalidator.poc.training.engine.Phase
import com.trainingvalidator.poc.training.engine.BodyPosture
import com.trainingvalidator.poc.training.engine.ExpectedDirection
import com.trainingvalidator.poc.training.engine.LandmarkTiltCorrector
import com.trainingvalidator.poc.training.engine.PoseSceneDetector
import com.trainingvalidator.poc.training.engine.PoseSceneExpectation
import com.trainingvalidator.poc.training.engine.PoseSceneResult
import com.trainingvalidator.poc.training.engine.VisibleRegion
import com.trainingvalidator.poc.training.engine.PositionError
import com.trainingvalidator.poc.training.engine.PositionCheckDebugStatus
import com.trainingvalidator.poc.training.engine.PositionValidationResult
import com.trainingvalidator.poc.training.engine.PositionValidator
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
        private const val TAB_ANGLE_DIAGNOSTICS = 0
        private const val TAB_POSITION = 1
        private const val TAB_CAMERA = 2
    }

    enum class InputMode { CAMERA, VIDEO, IMAGE }

    private lateinit var binding: ActivityDebugBinding
    private var cameraManager: CameraManager? = null
    private var poseLandmarkerHelper: PoseLandmarkerHelper? = null
    private lateinit var landmarkSmoother: LandmarkSmoother
    private val elbowAngleEstimator = ElbowAngleEstimator()

    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var useFrontCamera = true
    private var currentTab = TAB_ANGLE_DIAGNOSTICS
    private var currentInputMode = InputMode.CAMERA

    // Angle Lab: one or more joints at once
    private val selectedJointCodes = linkedSetOf("left_knee")

    // Position check mode
    private var selectedCheckType = PositionCheckType.VERTICAL_COMPARISON
    private var selectedPrimaryLandmark = "left_knee"
    private var selectedSecondaryLandmark = "left_ankle"
    private var selectedOperator = PositionOperator.SHOULD_NOT_EXCEED
    private var selectedThreshold = 0.05
    private var positionValidator: PositionValidator? = null
    private var isPositionTiltCorrectionEnabled = false

    private val debugTiltOwner = "debug-position"

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

    override fun onResume() {
        super.onResume()
        updateTiltProviderState()
    }

    override fun onPause() {
        PoseApp.instance.tiltProvider.release(debugTiltOwner)
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        PoseApp.instance.tiltProvider.release(debugTiltOwner)
        cameraManager?.stopCamera()
        videoManager?.release()
        poseLandmarkerHelper?.close()
        currentImageBitmap?.recycle()
        mainScope.cancel()
    }

    private fun selectedPoseModelType(): ModelType {
        return if (SettingsManager.getModelType() == "heavy") ModelType.HEAVY else ModelType.FULL
    }

    private fun ensurePoseLandmarkerHelper() {
        if (poseLandmarkerHelper == null) {
            poseLandmarkerHelper = PoseLandmarkerHelper(
                context = applicationContext,
                listener = this
            )
        }
    }

    private fun resetPoseAnalysisState() {
        clearOverlay()
        landmarkSmoother.reset()
        elbowAngleEstimator.reset()
        sceneDetector.reset()
        resetFpsCounters()
    }

    private fun applyDebugModelType(modelType: String) {
        if (SettingsManager.getModelType() == modelType) return

        SettingsManager.setModelType(modelType)
        resetPoseAnalysisState()
        poseLandmarkerHelper?.close()
        poseLandmarkerHelper = null

        when (currentInputMode) {
            InputMode.CAMERA -> initializePoseDetection()
            InputMode.VIDEO -> {
                poseLandmarkerHelper?.resetForVideo()
                ensureVideoModeReady()
            }
            InputMode.IMAGE -> {
                ensureImageModeReady(onReady = { reanalyzeCurrentImage() })
            }
        }

        Toast.makeText(this, "Debug model: ${modelType.uppercase()}", Toast.LENGTH_SHORT).show()
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

            val modelGroup = view.findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.modelToggleGroup)
            val checkedModelId = if (SettingsManager.getModelType() == "heavy") {
                R.id.btnDebugModelHeavy
            } else {
                R.id.btnDebugModelFull
            }
            modelGroup.check(checkedModelId)
            modelGroup.addOnButtonCheckedListener { _, id, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener
                val selectedModel = when (id) {
                    R.id.btnDebugModelHeavy -> "heavy"
                    R.id.btnDebugModelFull -> "full"
                    else -> return@addOnButtonCheckedListener
                }
                applyDebugModelType(selectedModel)
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
                    val pos = tab?.position ?: TAB_ANGLE_DIAGNOSTICS
                    updateConfigVisibility(pos)
                    handleTabChange(pos)
                }
                override fun onTabUnselected(tab: TabLayout.Tab?) {}
                override fun onTabReselected(tab: TabLayout.Tab?) {}
            })

            val switchTiltCorrection = view.findViewById<SwitchMaterial>(R.id.switchTiltCorrection)
            switchTiltCorrection.isChecked = isPositionTiltCorrectionEnabled
            switchTiltCorrection.setOnCheckedChangeListener { _, isChecked ->
                isPositionTiltCorrectionEnabled = isChecked
                rebuildPositionValidator()
                updateTiltProviderState()
            }

            val tvSelectedJointsSummary = view.findViewById<android.widget.TextView>(R.id.tvSelectedJointsSummary)
            val btnSelectJoints = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSelectJoints)
            fun refreshJointSelectionUi() {
                val sorted = selectedJointCodes.sorted()
                tvSelectedJointsSummary.text = sorted.joinToString("\n")
                btnSelectJoints.text = "Select joints (${sorted.size})"
            }
            refreshJointSelectionUi()
            btnSelectJoints.setOnClickListener {
                showJointMultiSelectDialog {
                    refreshJointSelectionUi()
                    if (currentInputMode == InputMode.IMAGE) reanalyzeCurrentImage()
                }
            }

            val operatorRow = view.findViewById<View>(R.id.operatorRow)
            fun syncOperatorRowVisibility() {
                operatorRow.visibility = if (isAlignmentCheck(selectedCheckType)) View.GONE else View.VISIBLE
            }
            setupSpinner(view.findViewById(R.id.spinnerCheckType), checkTypeNames) { pos ->
                selectedCheckType = PositionCheckType.values()[pos]
                syncOperatorRowVisibility()
                rebuildPositionValidator()
                if (currentInputMode == InputMode.IMAGE) reanalyzeCurrentImage()
            }
            view.findViewById<android.widget.Spinner>(R.id.spinnerCheckType).setSelection(selectedCheckType.ordinal)
            syncOperatorRowVisibility()

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
            etThreshold.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    val newThreshold = s?.toString()?.toDoubleOrNull() ?: return
                    if (newThreshold == selectedThreshold) return
                    selectedThreshold = newThreshold
                    rebuildPositionValidator()
                    if (currentInputMode == InputMode.IMAGE) reanalyzeCurrentImage()
                }
            })

            setupSpinner(view.findViewById(R.id.spinnerExpectedCamPos), allPostures.map { it.name }) { pos ->
                selectedPostures = listOf(allPostures[pos])
                rebuildPositionValidator()
                if (currentInputMode == InputMode.IMAGE) reanalyzeCurrentImage()
            }
            view.findViewById<android.widget.Spinner>(R.id.spinnerExpectedCamPos).setSelection(allPostures.indexOf(selectedPostures.first()).coerceAtLeast(0))

            setupSpinner(view.findViewById(R.id.spinnerExpectedDirection), allDirections.map { it.code }) { pos ->
                selectedDirections = listOf(allDirections[pos])
                rebuildPositionValidator()
                if (currentInputMode == InputMode.IMAGE) reanalyzeCurrentImage()
            }
            view.findViewById<android.widget.Spinner>(R.id.spinnerExpectedDirection).setSelection(allDirections.indexOf(selectedDirections.first()).coerceAtLeast(0))

            setupSpinner(view.findViewById(R.id.spinnerExpectedRegion), allRegions.map { it.name }) { pos ->
                selectedRegions = listOf(allRegions[pos])
                rebuildPositionValidator()
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
        binding.tvLiveValue.textSize = 56f

        when (currentTab) {
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
        }

        updateTiltProviderState()

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

    private fun showJointMultiSelectDialog(onApplied: () -> Unit) {
        val checked = BooleanArray(jointCodes.size) { jointCodes[it] in selectedJointCodes }
        AlertDialog.Builder(this)
            .setTitle("Select joints")
            .setMultiChoiceItems(jointCodes.toTypedArray(), checked) { _, which, isChecked ->
                val code = jointCodes[which]
                if (isChecked) selectedJointCodes.add(code) else selectedJointCodes.remove(code)
            }
            .setPositiveButton("OK") { _, _ ->
                if (selectedJointCodes.isEmpty()) {
                    selectedJointCodes.add("left_knee")
                }
                onApplied()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
        if (currentTab == TAB_POSITION) {
            rebuildPositionValidator()
        }
        updateTiltProviderState()

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
        ensurePoseLandmarkerHelper()
        val modelType = selectedPoseModelType()
        mainScope.launch(Dispatchers.IO) {
            poseLandmarkerHelper?.initialize(modelType = modelType, useGpu = true)
            withContext(Dispatchers.Main) {
                if (poseLandmarkerHelper?.isReady() == true) {
                    Log.d(TAG, "Pose detection LIVE_STREAM ready ($modelType)")
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
            ensurePoseLandmarkerHelper()
            val modelType = selectedPoseModelType()
            mainScope.launch(Dispatchers.IO) {
                poseLandmarkerHelper?.initializeForVideo(modelType = modelType, useGpu = true)
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "Pose detection VIDEO mode ready ($modelType)")
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

    private fun ensureImageModeReady(onReady: (() -> Unit)? = null) {
        if (poseLandmarkerHelper?.isImageModeReady() != true) {
            ensurePoseLandmarkerHelper()
            val modelType = selectedPoseModelType()
            mainScope.launch(Dispatchers.IO) {
                poseLandmarkerHelper?.initializeForImage(modelType = modelType, useGpu = true)
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "Pose detection IMAGE mode ready ($modelType)")
                    onReady?.invoke()
                }
            }
        } else {
            onReady?.invoke()
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
            TAB_ANGLE_DIAGNOSTICS -> {
                updateSelectedJointsOverlay(angles, smoothedLandmarks, result.imageWidth, result.imageHeight, result.isFrontCamera)
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
        }
    }

    private fun handleNoPose() {
        clearOverlay()
        binding.tvLiveValue.text = "--"
        binding.tvLiveValue.setTextColor(Color.WHITE)
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

    private fun updateSelectedJointsOverlay(
        angles: JointAngles,
        smoothedLandmarks: List<SmoothedLandmark>,
        imageW: Int,
        imageH: Int,
        isFrontCamera: Boolean
    ) {
        val highlights = selectedJointCodes.mapNotNull { jointCode ->
            val angleLandmarks = JointLandmarkMapping.getLandmarksForAngle(jointCode)
            if (angleLandmarks.size != 3) return@mapNotNull null
            SkeletonOverlayView.DebugJointHighlight(
                jointCode = jointCode,
                angle = angles.getAngle(jointCode),
                endpointA = angleLandmarks[0],
                endpointC = angleLandmarks[2],
                vertexIdx = angleLandmarks[1]
            )
        }
        binding.skeletonOverlay.updateDebugJoints(
            joints = highlights,
            smoothedLandmarks = smoothedLandmarks,
            imageW = imageW,
            imageH = imageH,
            useFrontCamera = isFrontCamera
        )
    }

    private fun formatJointCodeShort(jointCode: String): String =
        jointCode.replace('_', ' ')

    private fun updateAngleDiagnosticsDisplay(
        poseResult: PoseResult,
        angles: JointAngles,
        smoothedLandmarks: List<SmoothedLandmark>,
        smoothedWorldLandmarks: List<SmoothedLandmark>?
    ) {
        val diagnosticsList = selectedJointCodes.mapNotNull { jointCode ->
            buildAngleDiagnosticsData(
                jointCode = jointCode,
                poseResult = poseResult,
                angles = angles,
                smoothedLandmarks = smoothedLandmarks,
                smoothedWorldLandmarks = smoothedWorldLandmarks
            )
        }

        if (diagnosticsList.isEmpty()) {
            binding.tvLiveValue.text = "N/A"
            binding.tvLiveValue.setTextColor(Color.GRAY)
            binding.tvStatus.visibility = View.VISIBLE
            binding.tvStatus.text = "No valid joint mapping"
            binding.tvStatus.setTextColor(Color.GRAY)
            binding.tvDebugInfo.text = "Selected joints do not have a 3-point angle mapping."
            return
        }

        binding.tvLiveValue.text = diagnosticsList.joinToString("\n") { data ->
            val angle = data.displayedAngle
            if (angle != null) {
                "${formatJointCodeShort(data.displayJointCode)}  %.1f°".format(angle)
            } else {
                "${formatJointCodeShort(data.displayJointCode)}  N/A"
            }
        }
        binding.tvLiveValue.setTextColor(Color.WHITE)
        binding.tvLiveValue.textSize = if (diagnosticsList.size > 1) 28f else 56f

        binding.tvStatus.visibility = View.VISIBLE
        binding.tvStatus.text = diagnosticsList.joinToString(" | ") { buildAngleDiagnosticsSummary(it) }
        binding.tvStatus.setTextColor(Color.WHITE)

        if (inferenceFrameCount % 2 == 0 || currentInputMode == InputMode.IMAGE) {
            binding.tvDebugInfo.text = buildAngleDiagnosticsPanelText(diagnosticsList)
        }
    }

    private fun buildAngleDiagnosticsData(
        jointCode: String,
        poseResult: PoseResult,
        angles: JointAngles,
        smoothedLandmarks: List<SmoothedLandmark>,
        smoothedWorldLandmarks: List<SmoothedLandmark>?
    ): AngleDiagnosticsData? {
        val mappedIndices = JointLandmarkMapping.getLandmarksForAngle(jointCode)
        if (mappedIndices.size != 3) return null

        val effectiveIndices = if (poseResult.isFrontCamera) {
            mappedIndices.map { BodyLandmarks.getMirroredIndex(it) }
        } else {
            mappedIndices
        }

        val sourceJointCode = if (poseResult.isFrontCamera) {
            getMirroredJointCode(jointCode)
        } else {
            jointCode
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
            displayJointCode = jointCode,
            sourceJointCode = sourceJointCode,
            effectiveIndices = effectiveIndices,
            displayedAngle = angles.getAngle(jointCode),
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

    private fun buildAngleDiagnosticsPanelText(diagnosticsList: List<AngleDiagnosticsData>): String {
        return diagnosticsList.joinToString("\n\n") { buildSingleJointDiagnosticsPanelText(it) }
    }

    private fun buildSingleJointDiagnosticsPanelText(data: AngleDiagnosticsData): String {
        val sb = StringBuilder()
        val visibilityReference = data.worldSmoothed ?: data.normalizedSmoothed

        sb.appendLine("=== ANGLE DIAGNOSTICS: ${data.displayJointCode} ===")
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
        sb.appendLine("Model:         ${SettingsManager.getModelType().uppercase()}")
        sb.appendLine("Pipeline:      ${data.pipelineSourceLabel}")
        sb.appendLine("Displayed:     ${formatAngleLong(data.displayedAngle)}")
        sb.appendLine("Displayed note:same pre-engine angle sent to training")
        sb.appendLine("Training note: TrainingEngine may smooth/filter tracked joints")
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

    private fun shouldRunTiltProvider(): Boolean {
        return isPositionTiltCorrectionEnabled &&
            currentTab == TAB_POSITION &&
            currentInputMode == InputMode.CAMERA
    }

    private fun updateTiltProviderState() {
        if (shouldRunTiltProvider()) {
            PoseApp.instance.tiltProvider.acquire(debugTiltOwner)
        } else {
            PoseApp.instance.tiltProvider.release(debugTiltOwner)
        }
    }

    private fun buildPositionTiltStatusSuffix(): String {
        if (!isPositionTiltCorrectionEnabled) return ""
        if (currentInputMode != InputMode.CAMERA) return " | tilt: camera only"
        val provider = PoseApp.instance.tiltProvider
        if (!provider.isAvailable) return " | tilt: unavailable"
        return " | tilt: %.1f°".format(provider.rollDegrees)
    }

    private fun buildDebugEffectiveLandmarks(landmarks: List<SmoothedLandmark>): List<SmoothedLandmark>? {
        if (!isPositionTiltCorrectionEnabled || currentInputMode != InputMode.CAMERA) return null
        val provider = PoseApp.instance.tiltProvider
        if (!provider.isAvailable || provider.correctionRadians == 0f) return null
        return LandmarkTiltCorrector.correct(landmarks, provider.correctionRadians)
    }

    private fun updatePositionCheckDisplay(landmarks: List<SmoothedLandmark>, isFrontCamera: Boolean) {
        val validator = positionValidator ?: run {
            rebuildPositionValidator()
            positionValidator
        } ?: return

        val result = validator.validate(
            landmarks = landmarks,
            currentPhase = Phase.START,
            isBilateralFlipped = false,
            isFrontCamera = isFrontCamera
        )

        val allIssues = result.getAllIssues()
        val debugCheck = result.debugChecks.firstOrNull()
        val statusText = when (debugCheck?.status) {
            PositionCheckDebugStatus.PASS -> "PASS"
            PositionCheckDebugStatus.FAIL -> "FAIL"
            PositionCheckDebugStatus.FAIL_PENDING -> "FAIL*"
            PositionCheckDebugStatus.SKIPPED -> "SKIPPED"
            null -> if (allIssues.isEmpty()) "PASS" else "FAIL"
        }
        val statusColor = when (debugCheck?.status) {
            PositionCheckDebugStatus.PASS -> Color.GREEN
            PositionCheckDebugStatus.FAIL -> Color.RED
            PositionCheckDebugStatus.FAIL_PENDING -> Color.rgb(255, 152, 0)
            PositionCheckDebugStatus.SKIPPED -> Color.YELLOW
            null -> if (allIssues.isEmpty()) Color.GREEN else Color.RED
        }
        val debugActual = debugCheck?.actualValue
        val debugThreshold = debugCheck?.threshold ?: selectedThreshold

        binding.tvLiveValue.text = statusText
        binding.tvLiveValue.setTextColor(statusColor)
        binding.tvStatus.setTextColor(statusColor)
        binding.tvStatus.visibility = View.VISIBLE
        val baseStatusText = when {
            debugCheck?.status == PositionCheckDebugStatus.SKIPPED ->
                "SKIPPED | ${debugCheck.skipReason ?: "not evaluated"}"
            debugActual != null ->
                "%s | actual: %.4f | threshold: %.4f".format(
                    statusText,
                    debugActual,
                    debugThreshold
                )
            allIssues.isNotEmpty() -> {
                val issue = allIssues.first()
                "FAIL | actual: %.4f | threshold: %.4f".format(issue.actualValue, issue.threshold)
            }
            else -> statusText
        }
        binding.tvStatus.text = baseStatusText + buildPositionTiltStatusSuffix()

        if (inferenceFrameCount % 2 == 0 || currentInputMode == InputMode.IMAGE) {
            updateDebugInfoPanel(
                landmarks = landmarks,
                result = result,
                issues = allIssues
            )
        }
    }

    private fun updateDebugInfoPanel(
        landmarks: List<SmoothedLandmark>,
        result: PositionValidationResult,
        issues: List<PositionError>
    ) {
        val sb = StringBuilder()

        val debugCheck = result.debugChecks.firstOrNull()
        val statusStr = when (debugCheck?.status) {
            PositionCheckDebugStatus.PASS -> "PASS"
            PositionCheckDebugStatus.FAIL -> "FAIL"
            PositionCheckDebugStatus.FAIL_PENDING -> "FAIL_PENDING"
            PositionCheckDebugStatus.SKIPPED -> "SKIPPED"
            null -> if (issues.isEmpty()) "PASS" else "FAIL"
        }
        sb.appendLine("=== RESULT: $statusStr ===")
        sb.appendLine()

        sb.appendLine("--- TILT CORRECTION ---")
        sb.appendLine("Enabled: ${if (isPositionTiltCorrectionEnabled) "YES" else "no"}")
        sb.appendLine("Scope:   Position debug / camera input only")
        val tiltProvider = PoseApp.instance.tiltProvider
        sb.appendLine("Sensor:  ${when {
            !isPositionTiltCorrectionEnabled -> "idle"
            currentInputMode != InputMode.CAMERA -> "idle (not camera input)"
            !tiltProvider.isAvailable -> "unavailable"
            tiltProvider.isRunning -> "running"
            else -> "idle"
        }}")
        if (tiltProvider.isAvailable && currentInputMode == InputMode.CAMERA) {
            sb.appendLine("Roll:    %.1f deg".format(tiltProvider.rollDegrees))
            sb.appendLine("Applied: %.1f deg".format(Math.toDegrees(tiltProvider.correctionRadians.toDouble())))
        }
        val effectiveLandmarks = buildDebugEffectiveLandmarks(landmarks)
        sb.appendLine()

        sb.appendLine("--- LANDMARKS (RAW) ---")
        appendLandmarkInfo(sb, "Primary", selectedPrimaryLandmark, landmarks)
        appendLandmarkInfo(sb, "Secondary", selectedSecondaryLandmark, landmarks)
        if (effectiveLandmarks != null) {
            sb.appendLine()
            sb.appendLine("--- EFFECTIVE LANDMARKS (CORRECTED) ---")
            appendLandmarkInfo(sb, "Primary", selectedPrimaryLandmark, effectiveLandmarks)
            appendLandmarkInfo(sb, "Secondary", selectedSecondaryLandmark, effectiveLandmarks)
        }
        sb.appendLine()

        sb.appendLine("--- COMPARISON ---")
        sb.appendLine("Type: ${selectedCheckType.name}")
        sb.appendLine("Operator: ${selectedOperator.name}")
        sb.appendLine("Threshold: %.4f".format(selectedThreshold))
        sb.appendLine("Runtime phase: ${Phase.START.name} (debug non-IDLE)")

        val debugActual = debugCheck?.actualValue
        val debugThreshold = debugCheck?.threshold ?: selectedThreshold
        if (debugActual != null) {
            sb.appendLine("Actual: %.4f".format(debugActual))
            val delta = debugActual - debugThreshold
            sb.appendLine("Delta: %.4f".format(delta))
            sb.appendLine("Effective landmarks: ${debugCheck.landmark1} -> ${debugCheck.landmark2}")
            if (debugCheck.requiredFrames > 1 || debugCheck.status == PositionCheckDebugStatus.FAIL_PENDING) {
                sb.appendLine("Confirm frames: ${debugCheck.frameCount}/${debugCheck.requiredFrames}")
            }
        } else if (debugCheck?.status == PositionCheckDebugStatus.SKIPPED) {
            sb.appendLine("Skip reason: ${debugCheck.skipReason ?: "not evaluated"}")
        } else {
            val fallbackDiff = computeRawDiff(effectiveLandmarks ?: landmarks)
            if (fallbackDiff != null) {
                sb.appendLine("Actual: %.4f".format(fallbackDiff))
                val delta = fallbackDiff - selectedThreshold
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
            sb.appendLine("Active axis: ${getActiveAxis(selectedCheckType, sceneResult)}")
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

    /**
     * Alignment checks ignore the operator: they only test whether the points
     * are within `threshold` of each other (range <= threshold). We hide the
     * operator dropdown to avoid implying it has any effect.
     */
    private fun isAlignmentCheck(checkType: PositionCheckType): Boolean = when (checkType) {
        PositionCheckType.HORIZONTAL_ALIGNMENT,
        PositionCheckType.VERTICAL_ALIGNMENT,
        PositionCheckType.DEPTH_ALIGNMENT -> true
        else -> false
    }

    private fun getActiveAxis(
        checkType: PositionCheckType,
        scene: PoseSceneResult?
    ): String {
        val cameraPosition = scene?.direction ?: CameraPositionDetector.DetectedCameraPosition.UNKNOWN
        val posture = scene?.posture ?: BodyPosture.STANDING
        val isLying = posture == BodyPosture.LYING_PRONE ||
            posture == BodyPosture.LYING_SUPINE ||
            posture == BodyPosture.LYING_SIDE
        val isSideCamera = cameraPosition == CameraPositionDetector.DetectedCameraPosition.SIDE_VIEW_LEFT ||
            cameraPosition == CameraPositionDetector.DetectedCameraPosition.SIDE_VIEW_RIGHT

        return when (checkType) {
            PositionCheckType.VERTICAL_COMPARISON ->
                if (isLying && isSideCamera) "X (lying side vertical)" else "Y"
            PositionCheckType.HORIZONTAL_ALIGNMENT -> "Y (range)"
            PositionCheckType.VERTICAL_ALIGNMENT -> "X (range)"
            PositionCheckType.DEPTH_ALIGNMENT -> "Z (abs diff)"
            PositionCheckType.DISTANCE_RATIO -> "3D distance ratio"
            PositionCheckType.FORWARD_COMPARISON -> {
                when {
                    isLying && isSideCamera -> "Y (lying side forward)"
                    isLying -> "X (lying front/back forward)"
                    isSideCamera -> "X (forward/side, facing-aware)"
                    cameraPosition == CameraPositionDetector.DetectedCameraPosition.FRONT_VIEW ||
                        cameraPosition == CameraPositionDetector.DetectedCameraPosition.BACK_VIEW -> "-Z (forward/front)"
                    else -> "X (default)"
                }
            }
            PositionCheckType.SIDEWAYS_COMPARISON -> {
                when {
                    isSideCamera -> "Z (sideways/side)"
                    isLying -> "Y (lying front/back sideways)"
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
            activePhases = listOf("all"),
            errorMessage = LocalizedText(en = "Debug check failed"),
            severity = CheckSeverity.ERROR,
            cooldownMs = 0,
            minErrorFrames = 1
        )

        positionValidator = PositionValidator(
            positionChecks = listOf(check),
            posePositionCode = "standing_side",
            sceneExpectation = buildExpectation(),
            tiltSource = PoseApp.instance.tiltProvider.takeIf {
                isPositionTiltCorrectionEnabled && currentInputMode == InputMode.CAMERA
            }
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
            TAB_ANGLE_DIAGNOSTICS -> "Angle Lab"
            TAB_POSITION -> "Positions"
            TAB_CAMERA -> "Scene"
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
