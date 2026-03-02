package com.trainingvalidator.poc.ui.debug

import android.Manifest
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
import com.google.android.material.tabs.TabLayout
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.analysis.AngleCalculator
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
import com.trainingvalidator.poc.training.engine.ExpectedDirection
import com.trainingvalidator.poc.training.engine.PoseSceneDetector
import com.trainingvalidator.poc.training.engine.PoseSceneExpectation
import com.trainingvalidator.poc.training.engine.PoseSceneResult
import com.trainingvalidator.poc.training.engine.VisibleRegion
import com.trainingvalidator.poc.training.engine.PositionError
import com.trainingvalidator.poc.training.engine.PositionValidationResult
import com.trainingvalidator.poc.training.engine.PositionValidator
import com.trainingvalidator.poc.training.models.*
import com.trainingvalidator.poc.video.VideoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DebugActivity : AppCompatActivity(), PoseLandmarkerHelper.PoseDetectionListener {

    companion object {
        private const val TAG = "DebugActivity"
        private const val TAB_JOINTS = 0
        private const val TAB_POSITION = 1
        private const val TAB_CAMERA = 2
    }

    enum class InputMode { CAMERA, VIDEO, IMAGE }

    private lateinit var binding: ActivityDebugBinding
    private var cameraManager: CameraManager? = null
    private var poseLandmarkerHelper: PoseLandmarkerHelper? = null
    private lateinit var landmarkSmoother: LandmarkSmoother

    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var useFrontCamera = true
    private var currentTab = TAB_JOINTS
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
            sceneDetector.reset()
        }

        binding.btnGrantPermission.setOnClickListener {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        binding.btnSettings.setOnClickListener {
            showSettingsDialog()
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
                jointConfig.visibility = if (tab == TAB_JOINTS) View.VISIBLE else View.GONE
                posConfig.visibility = if (tab == TAB_POSITION) View.VISIBLE else View.GONE
                camConfig.visibility = if (tab == TAB_CAMERA) View.VISIBLE else View.GONE
            }
            updateConfigVisibility(currentTab)

            tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    val pos = tab?.position ?: TAB_JOINTS
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
            TAB_JOINTS -> {
                updateInfoPanelVisibility()
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

        if (currentInputMode == InputMode.IMAGE) {
            reanalyzeCurrentImage()
        }
    }

    private fun updateInfoPanelVisibility() {
        if (!isInfoPanelVisible) {
            binding.debugInfoPanel.visibility = View.GONE
            return
        }
        binding.debugInfoPanel.visibility = if (currentTab == TAB_JOINTS) View.GONE else View.VISIBLE
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
            TAB_CAMERA -> {
                updateCameraDetectionDisplay(smoothedLandmarks, result.imageWidth, result.imageHeight, result.isFrontCamera)
            }
        }
    }

    private fun handleNoPose() {
        clearOverlay()
        binding.tvLiveValue.text = "--"
        binding.tvLiveValue.setTextColor(Color.WHITE)
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
}
