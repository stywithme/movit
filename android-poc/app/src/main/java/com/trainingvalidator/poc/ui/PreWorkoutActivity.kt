package com.trainingvalidator.poc.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import coil.load
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.ActivityPreWorkoutBinding
import com.trainingvalidator.poc.training.loader.ExerciseLoader
import com.trainingvalidator.poc.training.models.ExerciseConfig

/**
 * PreWorkoutActivity - Exercise detail screen before starting workout
 * 
 * Shows:
 * - Exercise preview image/video
 * - Exercise name and metadata (muscles, equipment)
 * - Form checklist with steps to follow
 * - Camera position selection (if multiple variants)
 * - Action buttons (Analyze Video / Start Camera)
 */
class PreWorkoutActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PreWorkoutActivity"
        const val EXTRA_EXERCISE_NAME = "exercise_name"
    }

    private lateinit var binding: ActivityPreWorkoutBinding
    private var exerciseConfig: ExerciseConfig? = null
    private var selectedVariantIndex: Int = 0

    // Modern Photo Picker (Android 13+ with backport)
    private val photoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        handleVideoSelection(uri)
    }

    // Legacy video picker for older devices
    private val legacyPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            handleVideoSelection(result.data?.data)
        } else {
            Toast.makeText(this, getString(R.string.no_video_selected), Toast.LENGTH_SHORT).show()
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
                getString(R.string.video_permission_required),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPreWorkoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get exercise name from intent
        val exerciseName = intent.getStringExtra(EXTRA_EXERCISE_NAME)
        if (exerciseName == null) {
            Toast.makeText(this, getString(R.string.no_exercise_specified), Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, getString(R.string.failed_to_load_exercise), Toast.LENGTH_SHORT).show()
            finish()
            return
        }
    }

    private fun setupUI() {
        val exercise = exerciseConfig ?: return
        val language = getCurrentLanguage()

        // Setup toolbar
        binding.toolbar.setNavigationOnClickListener { finish() }

        // Exercise info
        binding.tvExerciseName.text = exercise.name.get(language).ifBlank { exercise.name.en }
        binding.tvMuscles.text = exercise.muscles.joinToString(" & ") {
            it.replaceFirstChar { c -> c.uppercase() }
        }
        binding.tvEquipment.text = if (exercise.equipment.isEmpty()) {
            getString(R.string.no_equipment)
        } else {
            exercise.equipment.joinToString(", ") {
                it.replaceFirstChar { c -> c.uppercase() }
            }
        }

        binding.ivExercisePreview.load(exercise.imageUrl) {
            placeholder(R.drawable.gradient_report_hero)
            error(R.drawable.gradient_report_hero)
            crossfade(true)
        }

        // Setup form checklist
        setupFormChecklist()

        // Setup camera position variants
        setupCameraPositions()

        // Button actions
        binding.btnStartCamera.setOnClickListener {
            startCameraTraining()
        }

        binding.btnAnalyzeVideo.setOnClickListener {
            openVideoPicker()
        }
    }

    private fun setupFormChecklist() {
        val exercise = exerciseConfig ?: return
        val language = getCurrentLanguage()

        // Get checklist items from exercise instructions
        val instructionsText = exercise.instructions?.let { instr ->
            instr.get(language).ifBlank { instr.en }
        } ?: ""
        val checklistItems = parseInstructions(instructionsText)

        binding.tvStepsCount.text = getString(R.string.steps_format, checklistItems.size)

        binding.checklistContainer.removeAllViews()

        checklistItems.forEachIndexed { index, item ->
            val itemView = LayoutInflater.from(this).inflate(
                R.layout.item_checklist,
                binding.checklistContainer,
                false
            )

            val tvTitle = itemView.findViewById<TextView>(R.id.tvTitle)
            val tvDescription = itemView.findViewById<TextView>(R.id.tvDescription)
            val checkboxBackground = itemView.findViewById<View>(R.id.checkboxBackground)
            val ivCheckmark = itemView.findViewById<ImageView>(R.id.ivCheckmark)

            // Set title and description
            tvTitle.text = item.title
            tvDescription.text = item.description

            // First item is always checked (demo purposes)
            if (index == 0) {
                checkboxBackground.setBackgroundResource(R.drawable.bg_circle_primary)
                ivCheckmark.visibility = View.VISIBLE
            }

            // Add click listener to toggle check
            itemView.setOnClickListener {
                toggleChecklistItem(checkboxBackground, ivCheckmark)
            }

            binding.checklistContainer.addView(itemView)
        }
    }

    private fun toggleChecklistItem(background: View, checkmark: ImageView) {
        if (checkmark.visibility == View.VISIBLE) {
            background.setBackgroundResource(R.drawable.bg_circle_surface)
            checkmark.visibility = View.GONE
        } else {
            background.setBackgroundResource(R.drawable.bg_circle_primary)
            checkmark.visibility = View.VISIBLE
        }
    }

    private fun parseInstructions(instructions: String): List<ChecklistItem> {
        // Parse instructions into checklist items
        // Expected format: numbered list or bullet points
        val items = mutableListOf<ChecklistItem>()

        // Try to split by newlines first
        val lines = instructions.split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (lines.size >= 2) {
            lines.forEach { line ->
                // Remove numbering if present (e.g., "1.", "2.", "-", "•")
                val cleanLine = line.replace(Regex("^[0-9]+\\.\\s*|^[-•]\\s*"), "")
                if (cleanLine.isNotBlank()) {
                    // Try to split into title and description
                    val parts = cleanLine.split(":", limit = 2)
                    if (parts.size == 2) {
                        items.add(ChecklistItem(parts[0].trim(), parts[1].trim()))
                    } else {
                        // Use first few words as title
                        val words = cleanLine.split(" ")
                        if (words.size > 3) {
                            items.add(ChecklistItem(
                                words.take(2).joinToString(" "),
                                cleanLine
                            ))
                        } else {
                            items.add(ChecklistItem(cleanLine, ""))
                        }
                    }
                }
            }
        }

        // If parsing failed, create default items
        if (items.isEmpty()) {
            items.add(ChecklistItem("Stance", "Stand with feet shoulder-width apart."))
            items.add(ChecklistItem("Posture", "Keep your chest up and back straight."))
            items.add(ChecklistItem("Depth", "Lower hips until thighs are parallel to floor."))
            items.add(ChecklistItem("Return", "Push through heels to return to standing."))
        }

        return items
    }

    private fun setupCameraPositions() {
        val exercise = exerciseConfig ?: return

        if (exercise.poseVariants.size <= 1) {
            binding.cameraPositionSection.visibility = View.GONE
            return
        }

        binding.cameraPositionSection.visibility = View.VISIBLE

        exercise.poseVariants.forEachIndexed { index, variant ->
            val buttonText = variant.name.en.ifBlank { variant.cameraPosition }
            when (index) {
                0 -> {
                    binding.btnVariant1.text = buttonText
                    binding.btnVariant1.visibility = View.VISIBLE
                    binding.btnVariant1.setOnClickListener { selectVariant(0) }
                }
                1 -> {
                    binding.btnVariant2.text = buttonText
                    binding.btnVariant2.visibility = View.VISIBLE
                    binding.btnVariant2.setOnClickListener { selectVariant(1) }
                }
            }
        }

        selectVariant(0)
    }

    private fun selectVariant(index: Int) {
        selectedVariantIndex = index

        // Update button states
        binding.btnVariant1.apply {
            if (index == 0) {
                setBackgroundColor(ContextCompat.getColor(context, R.color.primary))
                setTextColor(ContextCompat.getColor(context, R.color.on_primary))
            } else {
                setBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent))
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            }
        }

        binding.btnVariant2.apply {
            if (index == 1) {
                setBackgroundColor(ContextCompat.getColor(context, R.color.primary))
                setTextColor(ContextCompat.getColor(context, R.color.on_primary))
            } else {
                setBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent))
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            }
        }
    }

    private fun handleVideoSelection(uri: Uri?) {
        if (uri != null) {
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
            Toast.makeText(this, getString(R.string.no_video_selected), Toast.LENGTH_SHORT).show()
        }
    }

    private fun openVideoPicker() {
        val permission = getRequiredVideoPermission()

        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                launchVideoPicker()
            }
            shouldShowRequestPermissionRationale(permission) -> {
                Toast.makeText(
                    this,
                    getString(R.string.video_permission_rationale),
                    Toast.LENGTH_LONG
                ).show()
                permissionLauncher.launch(permission)
            }
            else -> {
                permissionLauncher.launch(permission)
            }
        }
    }

    private fun getRequiredVideoPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    private fun launchVideoPicker() {
        if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(this)) {
            photoPickerLauncher.launch(
                androidx.activity.result.PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.VideoOnly
                )
            )
        } else {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI).apply {
                type = "video/*"
            }
            legacyPickerLauncher.launch(intent)
        }
    }

    private fun getCurrentLanguage(): String {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        val locale = if (appLocales.isEmpty) {
            resources.configuration.locales[0]
        } else {
            appLocales[0]
        }
        return locale?.language ?: "en"
    }

    private fun startCameraTraining() {
        val exercise = exerciseConfig ?: return

        val intent = Intent(this, TrainingActivity::class.java).apply {
            putExtra(TrainingActivity.EXTRA_EXERCISE_NAME, exercise.fileName)
            putExtra(TrainingActivity.EXTRA_DIFFICULTY, "")
            putExtra(TrainingActivity.EXTRA_POSE_VARIANT, selectedVariantIndex)
            putExtra(TrainingActivity.EXTRA_TRAINING_MODE, TrainingActivity.MODE_CAMERA)
            // Get indicator type from settings
            val indicatorType = com.trainingvalidator.poc.training.config.SettingsManager.getIndicatorType()
            putExtra(TrainingActivity.EXTRA_INDICATOR_TYPE, indicatorType)
        }
        startActivity(intent)
    }

    private fun startVideoTraining(videoUri: Uri) {
        val exercise = exerciseConfig ?: return

        val intent = Intent(this, TrainingActivity::class.java).apply {
            putExtra(TrainingActivity.EXTRA_EXERCISE_NAME, exercise.fileName)
            putExtra(TrainingActivity.EXTRA_DIFFICULTY, "")
            putExtra(TrainingActivity.EXTRA_POSE_VARIANT, selectedVariantIndex)
            putExtra(TrainingActivity.EXTRA_TRAINING_MODE, TrainingActivity.MODE_VIDEO)
            putExtra(TrainingActivity.EXTRA_VIDEO_URI, videoUri)
            val indicatorType = com.trainingvalidator.poc.training.config.SettingsManager.getIndicatorType()
            putExtra(TrainingActivity.EXTRA_INDICATOR_TYPE, indicatorType)
        }
        startActivity(intent)
    }

    /**
     * Data class for checklist items
     */
    data class ChecklistItem(
        val title: String,
        val description: String
    )
}
