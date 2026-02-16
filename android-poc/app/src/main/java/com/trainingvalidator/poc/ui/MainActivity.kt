package com.trainingvalidator.poc.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.graphics.Color
import com.trainingvalidator.poc.analysis.AngleCalculator
import com.trainingvalidator.poc.analysis.LandmarkSmoother
import com.trainingvalidator.poc.camera.CameraManager
import com.trainingvalidator.poc.databinding.ActivityMainBinding
import com.trainingvalidator.poc.pose.ModelType
import com.trainingvalidator.poc.pose.PoseLandmarkerHelper
import com.trainingvalidator.poc.pose.PoseResult
import com.trainingvalidator.poc.training.config.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.trainingvalidator.poc.network.SessionSyncService
import com.trainingvalidator.poc.network.ApiConfig
import com.trainingvalidator.poc.storage.AuthManager

/**
 * MainActivity - Main entry point for the PoC app
 * 
 * Features:
 * - Camera with pose detection
 * - Landmark smoothing for stable visualization
 * - Visibility filtering to hide non-visible landmarks
 */
class MainActivity : AppCompatActivity(), PoseLandmarkerHelper.PoseDetectionListener {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private var cameraManager: CameraManager? = null
    private var poseLandmarkerHelper: PoseLandmarkerHelper? = null
    
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Landmark smoothing - uses One Euro Filter for responsive, jitter-free tracking
    // Must be initialized AFTER SettingsManager in onCreate()
    private lateinit var landmarkSmoother: LandmarkSmoother
    
    // State
    private var useFrontCamera = true
    private var showAngles = true
    private var currentModel = ModelType.FULL
    
    // FPS calculation
    private var frameCount = 0
    private var lastFpsUpdateTime = System.currentTimeMillis()
    private var currentFps = 0

    // Permission launcher
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onCameraPermissionGranted()
        } else {
            showPermissionDeniedView()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize SettingsManager FIRST (loads app_settings.json)
        try {
            SettingsManager.initialize(this)
            // Now create landmarkSmoother with loaded settings
            landmarkSmoother = LandmarkSmoother.createFromSettings()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize settings, using defaults", e)
            // Fallback to default smoother
            landmarkSmoother = LandmarkSmoother.createBalanced()
        }
        
        setupFullscreen()
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        setupUI()
        checkCameraPermission()
        
        // Initialize session sync (pending sync + background + connectivity listener)
        initializeSessionSync()
    }
    
    /**
     * Initialize session sync: sync pending, start background job, register connectivity listener
     */
    private fun initializeSessionSync() {
        val token = AuthManager.getAccessToken(this) ?: run {
            Log.d(TAG, "No auth token, skipping session sync setup")
            return
        }
        
        val syncService = SessionSyncService.getInstance(this, ApiConfig.getEffectiveBaseUrl())
        syncService.setAuthToken(token)
        
        // Register connectivity listener for auto-sync when network comes back
        syncService.registerConnectivityListener()
        
        // Start periodic background sync (every 10 minutes)
        syncService.startBackgroundSync()
        
        // Sync any pending sessions now
        mainScope.launch(Dispatchers.IO) {
            try {
                val result = syncService.syncPending()
                if (result.total > 0) {
                    Log.d(TAG, "Synced ${result.successCount}/${result.total} pending sessions")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Pending sync error: ${e.message}")
            }
        }
    }

    private fun setupFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun setupUI() {
        // Switch camera button
        binding.btnSwitchCamera.setOnClickListener {
            useFrontCamera = !useFrontCamera
            cameraManager?.switchCamera(useFrontCamera)
            landmarkSmoother.reset() // Reset smoother when switching cameras
        }
        
        // Toggle angles button
        binding.btnToggleAngles.setOnClickListener {
            showAngles = !showAngles
            binding.skeletonOverlay.setShowAngles(showAngles)
            binding.anglesPanel.visibility = if (showAngles) View.VISIBLE else View.GONE
            binding.btnToggleAngles.text = if (showAngles) "Hide Angles" else "Show Angles"
        }
        
        // Switch Model button
        binding.btnSwitchModel.setOnClickListener {
            currentModel = if (currentModel == ModelType.FULL) {
                ModelType.HEAVY
            } else {
                ModelType.FULL
            }
            switchModel(currentModel)
        }
        
        // Permission button
        binding.btnGrantPermission.setOnClickListener {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
        
        // Start Training button
        binding.btnStartTraining.setOnClickListener {
            startTrainingActivity()
        }
    }
    
    /**
     * Start Training Activity with default exercise
     */
    private fun startTrainingActivity() {
        val intent = Intent(this, TrainingActivity::class.java).apply {
            putExtra(TrainingActivity.EXTRA_EXERCISE_NAME, "squat")
            putExtra(TrainingActivity.EXTRA_DIFFICULTY, "beginner")
            putExtra(TrainingActivity.EXTRA_POSE_VARIANT, 0)
        }
        startActivity(intent)
    }

    private fun switchModel(modelType: ModelType) {
        binding.btnSwitchModel.text = "Model: ${if (modelType == ModelType.FULL) "Full" else "Heavy"}"
        updateStatus("Switching to ${modelType.displayName}...")
        binding.skeletonOverlay.clear()
        landmarkSmoother.reset()
        
        mainScope.launch(Dispatchers.IO) {
            poseLandmarkerHelper?.close()
            poseLandmarkerHelper?.initialize(modelType = modelType, useGpu = true)
            
            launch(Dispatchers.Main) {
                if (poseLandmarkerHelper?.isReady() == true) {
                    updateStatus("Switched to ${modelType.displayName} ✓")
                    Toast.makeText(this@MainActivity, "Using ${modelType.displayName} model", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                onCameraPermissionGranted()
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
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
        updateStatus("Initializing pose detection...")
        
        poseLandmarkerHelper = PoseLandmarkerHelper(
            context = applicationContext,
            listener = this
        )
        
        mainScope.launch(Dispatchers.IO) {
            poseLandmarkerHelper?.initialize(modelType = currentModel, useGpu = true)
            
            launch(Dispatchers.Main) {
                if (poseLandmarkerHelper?.isReady() == true) {
                    updateStatus("Pose detection ready!")
                    Log.d(TAG, "PoseLandmarker initialized successfully")
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
            poseLandmarkerHelper?.detectPose(imageProxy, useFrontCamera)
        }
    }

    // ==================== PoseDetectionListener ====================

    override fun onPoseDetected(result: PoseResult) {
        mainScope.launch(Dispatchers.Main) {
            if (!::landmarkSmoother.isInitialized) return@launch
            
            updateFps()
            
            // Update Model Name UI
            binding.tvModelType.text = "Model: ${result.modelType}"
            
            // Apply LIGHTWEIGHT smoothing (simple EMA) for drawing
            val smoothedLandmarks = landmarkSmoother.smooth(
                result.landmarks,
                result.timestampMs
            )
            
            // Convert and smooth world landmarks (One Euro Filter for stable 3D angles)
            val worldLandmarks = result.worldLandmarks?.let {
                landmarkSmoother.convertWorld(it, result.timestampMs)
            }
            
            // Calculate angles using world landmarks (3D) if available
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
            
            // Apply front camera correction: swap LEFT/RIGHT angles
            // because the image was mirrored before pose detection
            val angles = if (result.isFrontCamera) rawAngles.mirrored() else rawAngles
            
            // Update skeleton overlay
            binding.skeletonOverlay.updateSkeleton(
                smoothedLandmarks = smoothedLandmarks,
                inputImageWidth = result.imageWidth,
                inputImageHeight = result.imageHeight,
                angles = angles
            )
            
            // Update angles panel (every 3 frames to reduce UI updates)
            if (frameCount % 3 == 0) {
                updateAnglesDisplay(angles)
            }
            
            // Update status (every 30 frames)
            if (frameCount % 30 == 0) {
                updateStatus("Tracking pose ✓")
            }
        }
    }

    override fun onNoPoseDetected() {
        mainScope.launch(Dispatchers.Main) {
            updateFps()
            binding.skeletonOverlay.clear()
            binding.tvAngles.text = "Angles:\nNo pose detected"
            updateStatus("No pose detected")
        }
    }

    override fun onError(message: String) {
        mainScope.launch(Dispatchers.Main) {
            updateStatus("Error: $message")
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Pose detection error: $message")
        }
    }

    // ==================== UI Updates ====================

    private fun updateStatus(status: String) {
        binding.tvStatus.text = status
    }

    private fun updateFps() {
        frameCount++
        val currentTime = System.currentTimeMillis()
        val elapsed = currentTime - lastFpsUpdateTime
        
        if (elapsed >= 1000) {
            currentFps = frameCount
            frameCount = 0
            lastFpsUpdateTime = currentTime
            binding.tvFps.text = "FPS: $currentFps"
        }
    }

    private fun updateAnglesDisplay(angles: com.trainingvalidator.poc.analysis.JointAngles) {
        val text = buildString {
            appendLine("=== Angles ===")
            appendLine()
            appendLine("ARMS:")
            appendLine("L.Elbow: ${formatAngle(angles.leftElbow)}")
            appendLine("R.Elbow: ${formatAngle(angles.rightElbow)}")
            appendLine("L.Shoulder: ${formatAngle(angles.leftShoulder)}")
            appendLine("R.Shoulder: ${formatAngle(angles.rightShoulder)}")
            appendLine()
            appendLine("TORSO:")
            appendLine("L.Hip: ${formatAngle(angles.leftHip)}")
            appendLine("R.Hip: ${formatAngle(angles.rightHip)}")
            appendLine("Spine: ${formatAngle(angles.spine)}")
            appendLine()
            appendLine("LEGS:")
            appendLine("L.Knee: ${formatAngle(angles.leftKnee)}")
            appendLine("R.Knee: ${formatAngle(angles.rightKnee)}")
            appendLine("L.Ankle: ${formatAngle(angles.leftAnkle)}")
            appendLine("R.Ankle: ${formatAngle(angles.rightAnkle)}")
        }
        
        binding.tvAngles.text = text
    }

    private fun formatAngle(angle: Double?): String {
        return angle?.let { "%.1f°".format(it) } ?: "N/A"
    }

    // ==================== Lifecycle ====================

    override fun onDestroy() {
        super.onDestroy()
        cameraManager?.stopCamera()
        poseLandmarkerHelper?.close()
    }
}
