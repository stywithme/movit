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
import com.trainingvalidator.poc.training.models.DifficultyType
import com.trainingvalidator.poc.training.models.ExerciseConfig

/**
 * ExerciseDetailActivity - Shows exercise details and instructions
 * 
 * Flow:
 * 1. Show exercise name, instructions, required angles
 * 2. User selects difficulty level
 * 3. User selects pose variant (camera position)
 * 4. User taps "Start Training"
 * 5. Navigate to TrainingActivity
 */
class ExerciseDetailActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ExerciseDetailActivity"
        const val EXTRA_EXERCISE_NAME = "exercise_name"
    }

    private lateinit var binding: ActivityExerciseDetailBinding
    private var exerciseConfig: ExerciseConfig? = null
    private var selectedDifficulty: DifficultyType = DifficultyType.BEGINNER
    private var selectedVariantIndex: Int = 0
    
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
        exerciseConfig = ExerciseLoader.load(assets, exerciseName)
        
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
        
        // Setup difficulty buttons
        setupDifficultyButtons()
        
        // Setup pose variant selection
        setupPoseVariants()
        
        // Setup tracked joints display
        displayTrackedJoints()
        
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

    private fun setupDifficultyButtons() {
        binding.btnBeginner.setOnClickListener {
            selectDifficulty(DifficultyType.BEGINNER)
        }
        
        binding.btnNormal.setOnClickListener {
            selectDifficulty(DifficultyType.NORMAL)
        }
        
        binding.btnAdvanced.setOnClickListener {
            selectDifficulty(DifficultyType.ADVANCED)
        }
        
        // Default selection
        selectDifficulty(DifficultyType.BEGINNER)
    }

    private fun selectDifficulty(difficulty: DifficultyType) {
        selectedDifficulty = difficulty
        val exercise = exerciseConfig ?: return
        
        // Update button states
        binding.btnBeginner.alpha = if (difficulty == DifficultyType.BEGINNER) 1f else 0.5f
        binding.btnNormal.alpha = if (difficulty == DifficultyType.NORMAL) 1f else 0.5f
        binding.btnAdvanced.alpha = if (difficulty == DifficultyType.ADVANCED) 1f else 0.5f
        
        // Update target display based on counting method
        val variant = exercise.poseVariants.getOrNull(selectedVariantIndex)
        val diffLevel = variant?.difficultyLevels?.find { it.level == difficulty }
        val repConfig = diffLevel?.repCountingConfig
        
        binding.tvTargetReps.text = when (exercise.countingMethod) {
            CountingMethod.HOLD -> {
                val duration = repConfig?.duration ?: 30
                "Target: ${duration} seconds"
            }
            else -> {
                val reps = repConfig?.reps ?: 12
                "Target: $reps reps"
            }
        }
        
        // Show difficulty label instead of tolerance
        val diffLabel = when (difficulty) {
            DifficultyType.BEGINNER -> "Beginner (Wider Range)"
            DifficultyType.NORMAL -> "Normal"
            DifficultyType.ADVANCED -> "Advanced (Strict Range)"
        }
        binding.tvTolerance.text = "Level: $diffLabel"
        
        // Update tracked joints display to reflect new difficulty
        displayTrackedJoints()
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
        
        // Update difficulty info
        selectDifficulty(selectedDifficulty)
    }

    private fun displayTrackedJoints() {
        val variant = exerciseConfig?.poseVariants?.getOrNull(selectedVariantIndex) ?: return
        
        val primaryJoints = variant.getPrimaryJoints()
        val secondaryJoints = variant.getSecondaryJoints()
        
        val text = buildString {
            appendLine("📍 Primary Joints (for rep counting):")
            primaryJoints.forEach { joint ->
                val upRange = joint.getUpRange(selectedDifficulty)
                val downRange = joint.getDownRange(selectedDifficulty)
                appendLine("  • ${formatJointName(joint.joint)}")
                appendLine("    UP:   ${upRange.min.toInt()}° - ${upRange.max.toInt()}°")
                appendLine("    DOWN: ${downRange.min.toInt()}° - ${downRange.max.toInt()}°")
            }
            
            if (secondaryJoints.isNotEmpty()) {
                appendLine()
                appendLine("📌 Secondary Joints (for feedback):")
                secondaryJoints.forEach { joint ->
                    appendLine("  • ${formatJointName(joint.joint)}")
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
            putExtra(TrainingActivity.EXTRA_DIFFICULTY, selectedDifficulty.name.lowercase())
            putExtra(TrainingActivity.EXTRA_POSE_VARIANT, selectedVariantIndex)
            putExtra(TrainingActivity.EXTRA_TRAINING_MODE, TrainingActivity.MODE_CAMERA)
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
            putExtra(TrainingActivity.EXTRA_DIFFICULTY, selectedDifficulty.name.lowercase())
            putExtra(TrainingActivity.EXTRA_POSE_VARIANT, selectedVariantIndex)
            putExtra(TrainingActivity.EXTRA_TRAINING_MODE, TrainingActivity.MODE_VIDEO)
            putExtra(TrainingActivity.EXTRA_VIDEO_URI, videoUri)
        }
        startActivity(intent)
    }
}
