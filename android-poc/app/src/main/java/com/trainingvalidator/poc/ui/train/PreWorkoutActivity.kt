package com.trainingvalidator.poc.ui.train

import android.content.Intent
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import coil.load
import com.google.android.material.chip.Chip
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.ActivityPreWorkoutBinding
import com.trainingvalidator.poc.storage.EntityAudioPrefetchManager
import com.trainingvalidator.poc.training.models.ExerciseConfig
import com.trainingvalidator.poc.ui.utils.LocalizationHelper
import com.trainingvalidator.poc.ui.utils.currentLanguage
import kotlinx.coroutines.launch

/**
 * PreWorkoutActivity - Exercise detail screen before starting workout
 *
 * Shows exercise preview, instructions, camera position selection, and Start Camera.
 */
class PreWorkoutActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PreWorkoutActivity"
        const val EXTRA_EXERCISE_NAME = "exercise_name"
    }

    private lateinit var binding: ActivityPreWorkoutBinding
    private var exerciseConfig: ExerciseConfig? = null
    private var selectedVariantIndex: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPreWorkoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

        lifecycleScope.launch {
            EntityAudioPrefetchManager(this@PreWorkoutActivity).prefetchExerciseIfNeeded(exerciseName)
        }
    }

    private fun setupUI() {
        val exercise = exerciseConfig ?: return
        val language = currentLanguage

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.tvExerciseName.text = exercise.name.get(language).ifBlank { exercise.name.en }
        binding.tvEquipment.text = LocalizationHelper.getEquipmentDisplayText(this, exercise.equipment)

        setupPreviewImage()
        setupTargetMuscles()
        setupInstructions()
        setupDescription()
        setupCameraPositions()

        binding.btnStartCamera.setOnClickListener { startCameraTraining() }
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

    private fun startCameraTraining() {
        val exercise = exerciseConfig ?: return

        val intent = Intent(this, TrainingActivity::class.java).apply {
            putExtra(TrainingActivity.EXTRA_EXERCISE_NAME, exercise.fileName)
            putExtra(TrainingActivity.EXTRA_POSE_VARIANT, selectedVariantIndex)
            putExtra(TrainingActivity.EXTRA_TRAINING_MODE, TrainingActivity.MODE_CAMERA)
            val indicatorType = com.trainingvalidator.poc.training.config.SettingsManager.getIndicatorType()
            putExtra(TrainingActivity.EXTRA_INDICATOR_TYPE, indicatorType)
        }
        startActivity(intent)
    }

    private fun startVideoTraining(videoUri: Uri) {
        val exercise = exerciseConfig ?: return

        val intent = Intent(this, TrainingActivity::class.java).apply {
            putExtra(TrainingActivity.EXTRA_EXERCISE_NAME, exercise.fileName)
            putExtra(TrainingActivity.EXTRA_POSE_VARIANT, selectedVariantIndex)
            putExtra(TrainingActivity.EXTRA_TRAINING_MODE, TrainingActivity.MODE_VIDEO)
            putExtra(TrainingActivity.EXTRA_VIDEO_URI, videoUri)
            val indicatorType = com.trainingvalidator.poc.training.config.SettingsManager.getIndicatorType()
            putExtra(TrainingActivity.EXTRA_INDICATOR_TYPE, indicatorType)
        }
        startActivity(intent)
    }
}
