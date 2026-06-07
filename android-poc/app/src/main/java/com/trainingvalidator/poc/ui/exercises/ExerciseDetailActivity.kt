package com.trainingvalidator.poc.ui.exercises

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.trainingvalidator.poc.storage.EntityAudioPrefetchManager
import com.trainingvalidator.poc.ui.utils.LocalizationHelper
import com.trainingvalidator.poc.ui.utils.currentLanguage
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.ActivityExerciseDetailBinding
import com.trainingvalidator.poc.training.models.CountingMethod
import com.trainingvalidator.poc.training.models.ExerciseConfig
import com.trainingvalidator.poc.storage.UserExercisePreferenceStore
import com.trainingvalidator.poc.ui.train.TrainingActivity
import kotlinx.coroutines.launch

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityExerciseDetailBinding.inflate(layoutInflater)
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

        lifecycleScope.launch {
            EntityAudioPrefetchManager(this@ExerciseDetailActivity).prefetchExerciseIfNeeded(exerciseName)
        }
    }

    private fun setupUI() {
        val exercise = exerciseConfig ?: return
        val language = currentLanguage
        
        // Back button
        binding.btnBack.setOnClickListener { finish() }
        
        // Exercise info
        binding.tvExerciseName.text = exercise.name.get(language).ifBlank { exercise.name.en }
        binding.tvExerciseNameAr.text = if (language == "ar") exercise.name.en else exercise.name.ar
        binding.tvCategory.text = exercise.category.name.get(language).ifBlank { exercise.category.name.en }
        binding.tvMuscles.text = exercise.muscles.joinToString(", ") { 
            it.replaceFirstChar { c -> c.uppercase() } 
        }
        binding.tvEquipment.text = exercise.equipment.joinToString(", ") { 
            it.replaceFirstChar { c -> c.uppercase() } 
        }
        
        // Counting method
        binding.tvCountingMethod.text = when (exercise.countingMethod) {
            CountingMethod.UP_DOWN -> getString(R.string.counting_method_up_down)
            CountingMethod.HOLD -> getString(R.string.counting_method_hold)
        }
        
        // Difficulty selection removed (unified evaluation for all users)
        setupDifficultySection()
        
        // Setup pose variant selection
        setupPoseVariants()
        
        // Setup tracked joints display
        displayTrackedJoints()
        
        // Setup indicator type selection
        setupIndicatorSelection()
        
        binding.btnStartCamera.setOnClickListener {
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
                backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(this@ExerciseDetailActivity, R.color.primary)
                )
                setTextColor(ContextCompat.getColor(this@ExerciseDetailActivity, R.color.on_primary))
            } else {
                backgroundTintList = ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
                setTextColor(ContextCompat.getColor(this@ExerciseDetailActivity, R.color.primary))
            }
        }
        
        // Arc button
        binding.btnIndicatorArc.apply {
            if (isArcSelected) {
                backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(this@ExerciseDetailActivity, R.color.info)
                )
                setTextColor(ContextCompat.getColor(this@ExerciseDetailActivity, R.color.on_primary))
            } else {
                backgroundTintList = ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
                setTextColor(ContextCompat.getColor(this@ExerciseDetailActivity, R.color.info))
            }
        }
    }

    private fun setupDifficultySection() {
        // Hide buttons if present in layout
        binding.btnBeginner.visibility = View.GONE
        binding.btnNormal.visibility = View.GONE
        binding.btnAdvanced.visibility = View.GONE

        binding.tvTolerance.text = getString(R.string.unified_evaluation_message)

        // Target display: user preference overrides defaults when present
        val exercise = exerciseConfig ?: return
        val slug = exercise.fileName
        val pref = UserExercisePreferenceStore(this).get(slug)

        binding.tvTargetReps.text = when (exercise.countingMethod) {
            CountingMethod.HOLD -> {
                val defaultSec = exercise.repCountingConfig.duration ?: 30
                val effective = pref?.customDurationSec ?: defaultSec
                if (pref?.customDurationSec != null) {
                    getString(R.string.exercise_target_duration_custom, effective)
                } else {
                    getString(R.string.exercise_target_duration_default, effective)
                }
            }
            else -> {
                val defaultReps = exercise.repCountingConfig.reps
                val effective = pref?.customReps ?: defaultReps
                if (pref?.customReps != null) {
                    getString(R.string.exercise_target_reps_custom, effective)
                } else {
                    getString(R.string.exercise_target_reps_default, effective)
                }
            }
        }
    }

    private fun setupPoseVariants() {
        val exercise = exerciseConfig ?: return
        val language = currentLanguage
        val exerciseTitle = exercise.name.get(language).ifBlank { exercise.name.en }
        
        if (exercise.poseVariants.size <= 1) {
            binding.variantSelector.visibility = View.GONE
            return
        }
        
        binding.variantSelector.visibility = View.VISIBLE
        
        // Show variant buttons (pose position names — avoid duplicating exercise title)
        exercise.poseVariants.forEachIndexed { index, variant ->
            val text = LocalizationHelper.getPoseVariantButtonLabel(
                this, exerciseTitle, variant, language
            )
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

    private fun startCameraTraining() {
        val exercise = exerciseConfig ?: return
        
        val intent = Intent(this, TrainingActivity::class.java).apply {
            putExtra(TrainingActivity.EXTRA_EXERCISE_NAME, exercise.fileName)
            putExtra(TrainingActivity.EXTRA_POSE_VARIANT, selectedVariantIndex)
            putExtra(TrainingActivity.EXTRA_TRAINING_MODE, TrainingActivity.MODE_CAMERA)
            putExtra(TrainingActivity.EXTRA_INDICATOR_TYPE, selectedIndicatorType)
        }
        startActivity(intent)
    }

}
