package com.trainingvalidator.poc.ui.debug

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.tabs.TabLayout
import com.trainingvalidator.poc.analysis.AngleCalculator
import com.trainingvalidator.poc.analysis.JointAngles
import com.trainingvalidator.poc.analysis.LandmarkSmoother
import com.trainingvalidator.poc.analysis.SmoothedLandmark
import com.trainingvalidator.poc.camera.CameraManager
import com.trainingvalidator.poc.databinding.ActivityDebugBinding
import com.trainingvalidator.poc.pose.JointLandmarkMapping
import com.trainingvalidator.poc.pose.ModelType
import com.trainingvalidator.poc.pose.PoseLandmarkerHelper
import com.trainingvalidator.poc.pose.PoseResult
import com.trainingvalidator.poc.training.config.SettingsManager
import com.trainingvalidator.poc.training.engine.CameraPositionDetector
import com.trainingvalidator.poc.training.engine.Phase
import com.trainingvalidator.poc.training.engine.PositionError
import com.trainingvalidator.poc.training.engine.PositionValidationResult
import com.trainingvalidator.poc.training.engine.PositionValidator
import com.trainingvalidator.poc.training.models.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DebugActivity : AppCompatActivity(), PoseLandmarkerHelper.PoseDetectionListener {

    companion object {
        private const val TAG = "DebugActivity"
        private const val TAB_JOINTS = 0
        private const val TAB_POSITION = 1
    }

    private lateinit var binding: ActivityDebugBinding
    private var cameraManager: CameraManager? = null
    private var poseLandmarkerHelper: PoseLandmarkerHelper? = null
    private lateinit var landmarkSmoother: LandmarkSmoother

    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var useFrontCamera = true
    private var currentTab = TAB_JOINTS

    // Joint angle mode
    private var selectedJointCode: String = "left_knee"

    // Position check mode
    private var selectedCheckType = PositionCheckType.VERTICAL_COMPARISON
    private var selectedPrimaryLandmark = "left_knee"
    private var selectedSecondaryLandmark = "left_ankle"
    private var selectedOperator = PositionOperator.SHOULD_NOT_EXCEED
    private var selectedThreshold = 0.05
    private var positionValidator: PositionValidator? = null

    // Inference FPS (how many MediaPipe results per second)
    private var inferenceFrameCount = 0
    private var lastInferenceFpsTime = System.currentTimeMillis()
    private var currentInferenceFps = 0

    // Camera FPS (how many frames the camera delivers per second)
    private var cameraFrameCount = 0
    private var lastCameraFpsTime = System.currentTimeMillis()
    private var currentCameraFps = 0

    // Latest landmarks for debug panel
    private var latestLandmarks: List<SmoothedLandmark>? = null

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

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) onCameraPermissionGranted() else showPermissionDeniedView()
    }

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
        checkCameraPermission()
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
        }

        binding.btnGrantPermission.setOnClickListener {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setupTabs()
        setupJointSpinner()
        setupPositionCheckSpinners()
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTab = tab?.position ?: TAB_JOINTS
                when (currentTab) {
                    TAB_JOINTS -> {
                        binding.jointAngleConfig.visibility = View.VISIBLE
                        binding.positionCheckConfig.visibility = View.GONE
                        binding.debugInfoPanel.visibility = View.GONE
                        binding.tvStatus.visibility = View.GONE
                    }
                    TAB_POSITION -> {
                        binding.jointAngleConfig.visibility = View.GONE
                        binding.positionCheckConfig.visibility = View.VISIBLE
                        binding.debugInfoPanel.visibility = View.VISIBLE
                        binding.tvStatus.visibility = View.VISIBLE
                        rebuildPositionValidator()
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupJointSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, jointCodes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerJoint.adapter = adapter

        val defaultIndex = jointCodes.indexOf("left_knee").coerceAtLeast(0)
        binding.spinnerJoint.setSelection(defaultIndex)

        binding.spinnerJoint.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                selectedJointCode = jointCodes[pos]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupPositionCheckSpinners() {
        // Check type spinner
        setupSpinner(binding.spinnerCheckType, checkTypeNames) { pos ->
            selectedCheckType = PositionCheckType.values()[pos]
            rebuildPositionValidator()
        }

        // Primary landmark spinner
        setupSpinner(binding.spinnerPrimary, landmarkNames) { pos ->
            selectedPrimaryLandmark = landmarkNames[pos]
            rebuildPositionValidator()
        }
        binding.spinnerPrimary.setSelection(landmarkNames.indexOf("left_knee").coerceAtLeast(0))

        // Secondary landmark spinner
        setupSpinner(binding.spinnerSecondary, landmarkNames) { pos ->
            selectedSecondaryLandmark = landmarkNames[pos]
            rebuildPositionValidator()
        }
        binding.spinnerSecondary.setSelection(landmarkNames.indexOf("left_ankle").coerceAtLeast(0))

        // Operator spinner
        setupSpinner(binding.spinnerOperator, operatorNames) { pos ->
            selectedOperator = PositionOperator.values()[pos]
            rebuildPositionValidator()
        }

        // Threshold input
        binding.etThreshold.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                selectedThreshold = binding.etThreshold.text.toString().toDoubleOrNull() ?: 0.05
                rebuildPositionValidator()
            }
        }
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

    // ==================== Position Validator ====================

    private fun rebuildPositionValidator() {
        selectedThreshold = binding.etThreshold.text.toString().toDoubleOrNull() ?: 0.05

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
            expectedCameraPosition = "any",
            expectedFacingDirection = null,
            visibilityThreshold = 0.3f
        )
    }

    // ==================== Camera ====================

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            onCameraPermissionGranted()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun onCameraPermissionGranted() {
        binding.permissionView.visibility = View.GONE
        initializePoseDetection()
        initializeCamera()
    }

    private fun showPermissionDeniedView() {
        binding.permissionView.visibility = View.VISIBLE
    }

    private fun initializePoseDetection() {
        poseLandmarkerHelper = PoseLandmarkerHelper(
            context = applicationContext,
            listener = this
        )
        mainScope.launch(Dispatchers.IO) {
            poseLandmarkerHelper?.initialize(modelType = ModelType.FULL, useGpu = true)
            launch(Dispatchers.Main) {
                if (poseLandmarkerHelper?.isReady() == true) {
                    Log.d(TAG, "Pose detection ready")
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

    // ==================== Pose Detection Callbacks ====================

    override fun onPoseDetected(result: PoseResult) {
        mainScope.launch(Dispatchers.Main) {
            if (!::landmarkSmoother.isInitialized) return@launch

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

            val angles = if (result.isFrontCamera) rawAngles.mirrored() else rawAngles

            latestLandmarks = smoothedLandmarks

            when (currentTab) {
                TAB_JOINTS -> {
                    val angleLandmarks = JointLandmarkMapping.getLandmarksForAngle(selectedJointCode)
                    if (angleLandmarks.size == 3) {
                        binding.skeletonOverlay.updateDebugJoint(
                            jointCode = selectedJointCode,
                            angle = angles.getAngle(selectedJointCode),
                            endpointA = angleLandmarks[0],
                            endpointC = angleLandmarks[2],
                            vertexIdx = angleLandmarks[1],
                            smoothedLandmarks = smoothedLandmarks,
                            imageW = result.imageWidth,
                            imageH = result.imageHeight,
                            useFrontCamera = result.isFrontCamera
                        )
                    }
                    updateJointAngleDisplay(angles)
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
            }
        }
    }

    override fun onNoPoseDetected() {
        mainScope.launch(Dispatchers.Main) {
            updateInferenceFps()
            binding.skeletonOverlay.clearDebugMode()
            binding.skeletonOverlay.clear()
            binding.tvLiveValue.text = "--"
            binding.tvLiveValue.setTextColor(Color.WHITE)
            if (currentTab == TAB_POSITION) {
                binding.tvStatus.text = "NO POSE"
                binding.tvStatus.setTextColor(Color.GRAY)
                binding.tvDebugInfo.text = "No pose detected"
            }
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

        // PASS / FAIL status
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

        // Build detailed debug info
        if (inferenceFrameCount % 2 == 0) {
            updateDebugInfoPanel(landmarks, result, allIssues)
        }
    }

    private fun updateDebugInfoPanel(
        landmarks: List<SmoothedLandmark>,
        result: PositionValidationResult,
        issues: List<PositionError>
    ) {
        val sb = StringBuilder()

        // 1. Result
        val statusStr = if (issues.isEmpty()) "PASS" else "FAIL"
        sb.appendLine("=== RESULT: $statusStr ===")
        sb.appendLine()

        // 2. Landmark coordinates
        sb.appendLine("--- LANDMARKS ---")
        appendLandmarkInfo(sb, "Primary", selectedPrimaryLandmark, landmarks)
        appendLandmarkInfo(sb, "Secondary", selectedSecondaryLandmark, landmarks)
        sb.appendLine()

        // 3. Computed value vs threshold
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
            // Compute raw diff manually for display even on PASS
            val rawDiff = computeRawDiff(landmarks)
            if (rawDiff != null) {
                sb.appendLine("Actual: %.4f".format(rawDiff))
                val delta = rawDiff - selectedThreshold
                sb.appendLine("Delta: %.4f".format(delta))
            }
        }
        sb.appendLine()

        // 4. Camera detection
        sb.appendLine("--- CAMERA ---")
        val camResult = CameraPositionDetector.detect(landmarks)
        sb.appendLine("Position: ${camResult.position}")
        sb.appendLine("Confidence: %.2f".format(camResult.confidence))
        sb.appendLine("Facing: ${camResult.facingDirection}")
        sb.appendLine("Closer: ${camResult.closerSide}")
        sb.appendLine("Active axis: ${getActiveAxis(selectedCheckType, camResult.position)}")
        sb.appendLine()

        // 5. Validation result camera info
        sb.appendLine("--- RESULT DETAIL ---")
        sb.appendLine("Detected cam: ${result.detectedCameraPosition}")
        sb.appendLine("Detected face: ${result.detectedFacing}")
        if (result.cameraWarning != null) {
            sb.appendLine("Cam warn: ${result.cameraWarning.message.en}")
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

    // ==================== FPS ====================

    private fun updateInferenceFps() {
        inferenceFrameCount++
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

    // ==================== Lifecycle ====================

    override fun onDestroy() {
        super.onDestroy()
        cameraManager?.stopCamera()
        poseLandmarkerHelper?.close()
    }
}
