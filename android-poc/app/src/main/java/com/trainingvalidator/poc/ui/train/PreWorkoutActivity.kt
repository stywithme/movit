package com.trainingvalidator.poc.ui.train

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Animatable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import coil.load
import com.trainingvalidator.poc.ui.utils.currentLanguage
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.ActivityPreWorkoutBinding
import com.trainingvalidator.poc.training.models.ExerciseConfig
import com.trainingvalidator.poc.ui.utils.LocalizationHelper
import com.google.android.material.chip.Chip

/**
 * PreWorkoutActivity - Exercise detail screen before starting workout
 * 
 * Shows:
 * - Exercise preview image (static or animated)
 * - Exercise name, equipment, and target muscles
 * - Instructions and description
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
        // Load from repository (cached/synced data from backend)
        // No fallback to assets - repository is the single source of truth
        exerciseConfig = try {
            val repository = com.trainingvalidator.poc.storage.ExerciseRepository.getInstance(this)
            repository.getExercise(exerciseName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to access repository for exercise: $exerciseName", e)
            null
        }

        if (exerciseConfig == null) {
            Toast.makeText(this, getString(R.string.failed_to_load_exercise), Toast.LENGTH_SHORT).show()
            finish()
            return
        }
    }

    private fun setupUI() {
        val exercise = exerciseConfig ?: return
        val language = currentLanguage

        // Setup toolbar
        binding.toolbar.setNavigationOnClickListener { finish() }

        // Exercise info
        binding.tvExerciseName.text = exercise.name.get(language).ifBlank { exercise.name.en }

        // Localized equipment
        binding.tvEquipment.text = LocalizationHelper.getEquipmentDisplayText(this, exercise.equipment)

        setupPreviewImage()
        setupTargetMuscles()

        // Instructions
        setupInstructions()
        setupDescription()

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
    
    private fun setupInstructions() {
        val exercise = exerciseConfig ?: return
        val language = currentLanguage

        val instructionsText = exercise.instructions?.let { instr ->
            instr.get(language).ifBlank { instr.en }
        } ?: ""

        binding.tvInstructions.text = if (instructionsText.isNotBlank()) {
            instructionsText
        } else {
            getString(R.string.no_instructions_available)
        }
    }

    private fun setupDescription() {
        val exercise = exerciseConfig ?: return
        val language = currentLanguage
        val descriptionText = exercise.description?.let { desc ->
            desc.get(language).ifBlank { desc.en }
        }.orEmpty()

        if (descriptionText.isBlank()) {
            binding.descriptionSection.visibility = View.GONE
            return
        }

        binding.descriptionSection.visibility = View.VISIBLE
        binding.tvDescription.text = descriptionText
    }

    private fun setupTargetMuscles() {
        val exercise = exerciseConfig ?: return
        val localizedMuscles = exercise.muscles
            .map { LocalizationHelper.getMuscleDisplayName(this, it) }
            .distinct()

        binding.chipGroupTargetMuscles.removeAllViews()

        if (localizedMuscles.isEmpty()) {
            binding.targetMusclesSection.visibility = View.GONE
            return
        }

        binding.targetMusclesSection.visibility = View.VISIBLE
        localizedMuscles.forEach { muscleName ->
            val chipContext = ContextThemeWrapper(this, R.style.Widget_WayToFix_Chip_Filter)
            val chip = Chip(chipContext, null).apply {
                text = muscleName
                isCheckable = false
                isClickable = false
            }
            binding.chipGroupTargetMuscles.addView(chip)
        }
    }

    private fun setupPreviewImage() {
        val imageUrl = exerciseConfig?.imageUrl
        binding.badgeLoopingPreview.visibility = View.GONE

        if (imageUrl.isNullOrBlank()) {
            binding.ivExercisePreview.setImageDrawable(null)
            binding.ivExerciseFallbackIcon.visibility = View.VISIBLE
            return
        }

        binding.ivExercisePreview.load(imageUrl) {
            placeholder(R.drawable.gradient_report_hero)
            error(R.drawable.gradient_report_hero)
            crossfade(true)
            listener(
                onStart = { _ ->
                    binding.ivExerciseFallbackIcon.visibility = View.GONE
                    binding.badgeLoopingPreview.visibility = View.GONE
                },
                onError = { _, _ ->
                    binding.ivExerciseFallbackIcon.visibility = View.VISIBLE
                    binding.badgeLoopingPreview.visibility = View.GONE
                },
                onSuccess = { _, result ->
                    binding.ivExerciseFallbackIcon.visibility = View.GONE
                    binding.badgeLoopingPreview.visibility =
                        if (result.drawable is Animatable) View.VISIBLE else View.GONE
                }
            )
        }
    }


    private fun setupCameraPositions() {
        val exercise = exerciseConfig ?: return
        val language = currentLanguage

        if (exercise.poseVariants.size <= 1) {
            binding.cameraPositionSection.visibility = View.GONE
            return
        }

        binding.cameraPositionSection.visibility = View.VISIBLE

        val exerciseTitle = exercise.name.get(language).ifBlank { exercise.name.en }
        exercise.poseVariants.forEachIndexed { index, variant ->
            val buttonText = LocalizationHelper.getPoseVariantButtonLabel(
                this, exerciseTitle, variant, language
            )
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
}
