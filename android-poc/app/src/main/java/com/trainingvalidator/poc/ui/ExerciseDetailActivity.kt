package com.trainingvalidator.poc.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.trainingvalidator.poc.databinding.ActivityExerciseDetailBinding
import com.trainingvalidator.poc.training.loader.ExerciseLoader
import com.trainingvalidator.poc.training.models.CountingMethod
import com.trainingvalidator.poc.training.models.ExerciseConfig

/**
 * ExerciseDetailActivity - Shows exercise details and instructions
 * 
 * Flow:
 * 1. Show exercise name, instructions, required angles
 * 2. User selects pose variant (camera position)
 * 3. User taps "Start Training"
 * 4. Navigate to TrainingActivity
 */
class ExerciseDetailActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ExerciseDetailActivity"
        const val EXTRA_EXERCISE_NAME = "exercise_name"
    }

    private lateinit var binding: ActivityExerciseDetailBinding
    private var exerciseConfig: ExerciseConfig? = null
    private var selectedVariantIndex: Int = 0
    private var selectedIndicatorType: String = "line" // "line" or "arc"
    
    // Modern Photo Picker (Android 13+ with backport) - Opens Gallery directly
    private val photoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        handleVideoSelection(uri)
    }
    
    // Legacy video picker for older devices - Opens Gallery
    private val legacyPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            handleVideoSelection(result.data?.data)
        } else {
            Toast.makeText(this, "No video selected", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Permission request launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchVideoPicker()
        } else {
            Toast.makeText(
                this, 
                "Permission required to access videos", 
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    /**
     * Handle video selection from any picker
     */
    private fun handleVideoSelection(uri: Uri?) {
        if (uri != null) {
            // Grant read permission for the URI
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Permission might not be persistable, continue anyway
            }
            startVideoTraining(uri)
        } else {
            Toast.makeText(this, "No video selected", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityExerciseDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Get exercise name from intent
        val exerciseName = intent.getStringExtra(EXTRA_EXERCISE_NAME)
        if (exerciseName == null) {
            Toast.makeText(this, "No exercise specified", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        loadExercise(exerciseName)
        setupUI()
    }

    private fun loadExercise(exerciseName: String) {
        // Try repository first (cached/synced data), fall back to assets
        exerciseConfig = try {
            val repository = com.trainingvalidator.poc.storage.ExerciseRepository.getInstance(this)
            repository.getExercise(exerciseName) ?: ExerciseLoader.load(assets, exerciseName)
        } catch (e: Exception) {
            // Repository not initialized, use assets
            ExerciseLoader.load(assets, exerciseName)
        }
        
        if (exerciseConfig == null) {
            Toast.makeText(this, "Failed to load exercise", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
    }

    private fun setupUI() {
        val exercise = exerciseConfig ?: return
        
        // Back button
        binding.btnBack.setOnClickListener { finish() }
        
        // Exercise info
        binding.tvExerciseName.text = exercise.name.en
        binding.tvExerciseNameAr.text = exercise.name.ar
        binding.tvCategory.text = exercise.category.name.en
        binding.tvMuscles.text = exercise.muscles.joinToString(", ") { 
            it.replaceFirstChar { c -> c.uppercase() } 
        }
        binding.tvEquipment.text = exercise.equipment.joinToString(", ") { 
            it.replaceFirstChar { c -> c.uppercase() } 
        }
        
        // Counting method
        binding.tvCountingMethod.text = when (exercise.countingMethod) {
            com.trainingvalidator.poc.training.models.CountingMethod.UP_DOWN -> "Up & Down (like Squat)"
            com.trainingvalidator.poc.training.models.CountingMethod.PUSH_PULL -> "Push & Pull (like Push-up)"
            com.trainingvalidator.poc.training.models.CountingMethod.HOLD -> "Hold (like Plank)"
        }
        
        // Difficulty selection removed (unified evaluation for all users)
        setupDifficultySection()
        
        // Setup pose variant selection
        setupPoseVariants()
        
        // Setup tracked joints display
        displayTrackedJoints()
        
        // Setup indicator type selection
        setupIndicatorSelection()
        
        // Camera mode button
        binding.btnStartCamera.setOnClickListener {
            startCameraTraining()
        }
        
        // Video mode button
        binding.btnStartVideo.setOnClickListener {
            openVideoPicker()
        }
        
        // Legacy start button (hidden)
        binding.btnStart.setOnClickListener {
            startCameraTraining()
        }
    }
    
    private fun setupIndicatorSelection() {
        // Load default from settings
        val defaultIndicator = com.trainingvalidator.poc.training.config.SettingsManager.getIndicatorType()
        selectedIndicatorType = defaultIndicator
        
        // Update button states based on default
        updateIndicatorButtons()
        
        // Line button
        binding.btnIndicatorLine.setOnClickListener {
            selectedIndicatorType = "line"
            updateIndicatorButtons()
        }
        
        // Arc button
        binding.btnIndicatorArc.setOnClickListener {
            selectedIndicatorType = "arc"
            updateIndicatorButtons()
        }
    }
    
    private fun updateIndicatorButtons() {
        val isLineSelected = selectedIndicatorType == "line"
        val isArcSelected = selectedIndicatorType == "arc"
        
        // Line button
        binding.btnIndicatorLine.apply {
            if (isLineSelected) {
                // Selected: filled green button
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#00E676")
                )
                setTextColor(android.graphics.Color.WHITE)
            } else {
                // Unselected: outlined button (transparent background)
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.TRANSPARENT
                )
                setTextColor(android.graphics.Color.parseColor("#00E676"))
            }
        }
        
        // Arc button
        binding.btnIndicatorArc.apply {
            if (isArcSelected) {
                // Selected: filled blue button
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#2196F3")
                )
                setTextColor(android.graphics.Color.WHITE)
            } else {
                // Unselected: outlined button (transparent background)
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.TRANSPARENT
                )
                setTextColor(android.graphics.Color.parseColor("#2196F3"))
            }
        }
    }

    private fun setupDifficultySection() {
        // Hide buttons if present in layout
        binding.btnBeginner.visibility = View.GONE
        binding.btnNormal.visibility = View.GONE
        binding.btnAdvanced.visibility = View.GONE

        // Replace difficulty label with unified evaluation message (UI text in English)
        binding.tvTolerance.text = "Unified evaluation (no difficulty selection)"

        // Target display now comes from exerciseConfig.repCountingConfig
        val exercise = exerciseConfig ?: return
        binding.tvTargetReps.text = when (exercise.countingMethod) {
            CountingMethod.HOLD -> {
                val duration = exercise.repCountingConfig.duration ?: 30
                "Target: ${duration} seconds"
            }
            else -> {
                val reps = exercise.repCountingConfig.reps
                "Target: $reps reps"
            }
        }
    }

    private fun setupPoseVariants() {
        val exercise = exerciseConfig ?: return
        
        if (exercise.poseVariants.size <= 1) {
            binding.variantSelector.visibility = View.GONE
            return
        }
        
        binding.variantSelector.visibility = View.VISIBLE
        
        // Show variant buttons
        exercise.poseVariants.forEachIndexed { index, variant ->
            val text = "${variant.name.en}\n(${variant.cameraPosition})"
            when (index) {
                0 -> {
                    binding.btnVariant1.text = text
                    binding.btnVariant1.visibility = View.VISIBLE
                    binding.btnVariant1.setOnClickListener { selectVariant(0) }
                }
                1 -> {
                    binding.btnVariant2.text = text
                    binding.btnVariant2.visibility = View.VISIBLE
                    binding.btnVariant2.setOnClickListener { selectVariant(1) }
                }
            }
        }
        
        selectVariant(0)
    }

    private fun selectVariant(index: Int) {
        selectedVariantIndex = index
        
        binding.btnVariant1.alpha = if (index == 0) 1f else 0.5f
        binding.btnVariant2.alpha = if (index == 1) 1f else 0.5f
        
        // Update tracked joints display
        displayTrackedJoints()
    }

    private fun displayTrackedJoints() {
        val config = exerciseConfig ?: return
        val variant = config.poseVariants.getOrNull(selectedVariantIndex) ?: return
        
        val primaryJoints = variant.getPrimaryJoints()
        val secondaryJoints = variant.getSecondaryJoints()
        val isHoldExercise = config.countingMethod == com.trainingvalidator.poc.training.models.CountingMethod.HOLD
        
        val text = buildString {
            appendLine("📍 Primary Joints (for ${if (isHoldExercise) "hold" else "rep"} counting):")
            primaryJoints.forEach { joint ->
                appendLine("  • ${formatJointName(joint.joint)}")
                
                // Handle HOLD vs REP exercises
                if (isHoldExercise && joint.hasStateHoldRange()) {
                    // HOLD exercise - use range
                    val holdRange = joint.getStateHoldRange().perfect
                    appendLine("    HOLD: ${holdRange.min.toInt()}° - ${holdRange.max.toInt()}°")
                } else if (joint.hasStateUpDownRanges()) {
                    // REP exercise - use upRange/downRange
                    val upRange = joint.getStateUpRange().perfect
                    val downRange = joint.getStateDownRange().perfect
                    appendLine("    UP:   ${upRange.min.toInt()}° - ${upRange.max.toInt()}°")
                    appendLine("    DOWN: ${downRange.min.toInt()}° - ${downRange.max.toInt()}°")
                } else {
                    appendLine("    (No state ranges defined)")
                }
            }
            
            if (secondaryJoints.isNotEmpty()) {
                appendLine()
                appendLine("📌 Secondary Joints (for feedback):")
                secondaryJoints.forEach { joint ->
                    appendLine("  • ${formatJointName(joint.joint)}")
                    if (joint.hasStateHoldRange()) {
                        val range = joint.getStateHoldRange().perfect
                        appendLine("    RANGE: ${range.min.toInt()}° - ${range.max.toInt()}°")
                    }
                }
            }
        }
        
        binding.tvTrackedJoints.text = text
    }

    private fun formatJointName(jointCode: String): String {
        return jointCode
            .replace("_", " ")
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }

    /**
     * Open video picker to select a video for analysis
     * Checks for required permissions first
     */
    private fun openVideoPicker() {
        val permission = getRequiredVideoPermission()
        
        when {
            // Permission already granted
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                launchVideoPicker()
            }
            // Should show rationale
            shouldShowRequestPermissionRationale(permission) -> {
                Toast.makeText(
                    this,
                    "Video access permission is needed to analyze exercise videos",
                    Toast.LENGTH_LONG
                ).show()
                permissionLauncher.launch(permission)
            }
            // Request permission
            else -> {
                permissionLauncher.launch(permission)
            }
        }
    }
    
    /**
     * Get the required permission based on Android version
     */
    private fun getRequiredVideoPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }
    
    /**
     * Launch the actual video picker after permission is granted
     * Uses Photo Picker on supported devices, falls back to ACTION_PICK
     */
    private fun launchVideoPicker() {
        // Check if Photo Picker is available (Android 11+ with Google Play services backport)
        if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(this)) {
            // Use modern Photo Picker - shows Gallery with recent videos
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
            )
        } else {
            // Fallback to legacy ACTION_PICK - also opens Gallery
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI).apply {
                type = "video/*"
            }
            legacyPickerLauncher.launch(intent)
        }
    }
    
    /**
     * Start camera-based training
     */
    private fun startCameraTraining() {
        val exercise = exerciseConfig ?: return
        
        val intent = Intent(this, TrainingActivity::class.java).apply {
            putExtra(TrainingActivity.EXTRA_EXERCISE_NAME, exercise.fileName)
            // Kept for backward compatibility, ignored by new engine
            putExtra(TrainingActivity.EXTRA_DIFFICULTY, "")
            putExtra(TrainingActivity.EXTRA_POSE_VARIANT, selectedVariantIndex)
            putExtra(TrainingActivity.EXTRA_TRAINING_MODE, TrainingActivity.MODE_CAMERA)
            putExtra(TrainingActivity.EXTRA_INDICATOR_TYPE, selectedIndicatorType)
        }
        startActivity(intent)
    }
    
    /**
     * Start video-based training
     */
    private fun startVideoTraining(videoUri: Uri) {
        val exercise = exerciseConfig ?: return
        
        val intent = Intent(this, TrainingActivity::class.java).apply {
            putExtra(TrainingActivity.EXTRA_EXERCISE_NAME, exercise.fileName)
            // Kept for backward compatibility, ignored by new engine
            putExtra(TrainingActivity.EXTRA_DIFFICULTY, "")
            putExtra(TrainingActivity.EXTRA_POSE_VARIANT, selectedVariantIndex)
            putExtra(TrainingActivity.EXTRA_TRAINING_MODE, TrainingActivity.MODE_VIDEO)
            putExtra(TrainingActivity.EXTRA_VIDEO_URI, videoUri)
            putExtra(TrainingActivity.EXTRA_INDICATOR_TYPE, selectedIndicatorType)
        }
        startActivity(intent)
    }
}
