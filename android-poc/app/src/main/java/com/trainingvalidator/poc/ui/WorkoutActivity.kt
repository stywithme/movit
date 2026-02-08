package com.trainingvalidator.poc.ui

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.ActivityWorkoutBinding
import com.trainingvalidator.poc.storage.ExerciseRepository
import com.trainingvalidator.poc.storage.WorkoutRepository
import com.trainingvalidator.poc.training.models.*
import com.trainingvalidator.poc.training.workout.LoadedExercise
import com.trainingvalidator.poc.training.workout.WorkoutRunner

/**
 * WorkoutActivity - Manages a workout session with multiple exercises
 * 
 * Supports two modes:
 * 
 * 1. SEQUENTIAL Mode (default):
 *    - Show "Preparing" panel with next exercise info
 *    - Launch TrainingActivity for each exercise
 *    - On return, show rest period or move to next exercise
 *    - Repeat until all exercises and sets are complete
 * 
 * This activity acts as an orchestrator - it doesn't do pose detection itself,
 * but delegates to TrainingActivity.
 */
class WorkoutActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "WorkoutActivity"
        
        // Intent extras
        const val EXTRA_WORKOUT_NAME = "workout_name"
        const val EXTRA_DEFAULT_DIFFICULTY = "default_difficulty"
        
        // Request codes / Result extras
        const val RESULT_REPS_COMPLETED = "reps_completed"
        const val RESULT_DURATION_MS = "duration_ms"
        const val RESULT_ACCURACY = "accuracy"
        const val RESULT_IS_COMPLETED = "is_completed"
        
        // Reserved for legacy workout mode (not used in simplified flow)
        const val EXTRA_MAX_REPS_THIS_SESSION = "max_reps_this_session"
    }

    private lateinit var binding: ActivityWorkoutBinding
    private var workoutConfig: WorkoutConfig? = null
    private var workoutRunner: WorkoutRunner? = null
    private var restTimer: CountDownTimer? = null
    private var startTimeMs: Long = 0L
    private var workoutName: String? = null
    private var difficultyStr: String = "beginner"

    // Activity result launcher for TrainingActivity
    private val trainingLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleTrainingResult(result.resultCode, result.data)
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityWorkoutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Get workout name from intent
        workoutName = intent.getStringExtra(EXTRA_WORKOUT_NAME)
        if (workoutName == null) {
            Toast.makeText(this, getString(R.string.no_workout_specified), Toast.LENGTH_SHORT)
                .show()
            finish()
            return
        }
        
        difficultyStr = intent.getStringExtra(EXTRA_DEFAULT_DIFFICULTY) ?: "beginner"
        
        loadWorkout(workoutName!!)
        setupUI()
    }

    private fun loadWorkout(name: String) {
        // Load from repository (cached/synced data from backend)
        // No fallback to assets - repository is the single source of truth
        workoutConfig = try {
            val repository = WorkoutRepository.getInstance(this)
            repository.getWorkout(name)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to access repository for workout: $name", e)
            null
        }
        
        if (workoutConfig == null) {
            Toast.makeText(
                this,
                getString(R.string.failed_to_load_workout_format, workoutName),
                Toast.LENGTH_SHORT
            ).show()
            finish()
            return
        }
        
        val config = workoutConfig!!
        // NOTE: difficulty has been removed (unified evaluation).
        // difficultyStr is kept only for backward compatibility with older intents.

        // Create workout runner with ExerciseRepository
        val exerciseRepository = com.trainingvalidator.poc.storage.ExerciseRepository.getInstance(this)
        workoutRunner = WorkoutRunner(
            workoutConfig = config,
            exerciseRepository = exerciseRepository
        ).apply {
            setupCallbacks(this)
        }
        
        Log.d(TAG, "Loaded workout: ${config.name.en} with ${config.exercises.size} exercises")
    }

    private fun setupCallbacks(runner: WorkoutRunner) {
        runner.onExerciseReady = { loadedExercise ->
            showPreparingPanel(loadedExercise)
        }
        
        runner.onRestStarted = { durationMs, _ ->
            showRestPanel(durationMs)
        }
        
        runner.onWorkoutCompleted = { result ->
            showCompletePanel(result)
        }
    }

    private fun setupUI() {
        val config = workoutConfig ?: return
        
        // Header
        binding.tvWorkoutName.text = config.name.en
        updateProgressDisplay()
        
        // Close button
        binding.btnClose.setOnClickListener {
            confirmExit()
        }
        
        // Start exercise button
        binding.btnStartExercise.setOnClickListener {
            startCurrentExercise()
        }
        
        // Skip rest button
        binding.btnSkipRest.setOnClickListener {
            skipRest()
        }
        
        // Start next round button
        binding.btnStartNextRound.setOnClickListener {
            skipRest()
        }
        
        // Finish workout button
        binding.btnFinishWorkout.setOnClickListener {
            finish()
        }
        
        // Start the workout
        startTimeMs = System.currentTimeMillis()
        
        // Sequential mode: Use traditional flow
        workoutRunner?.start()
        
        // Setup back button handler (modern way)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                confirmExit()
            }
        })
    }

    private fun updateProgressDisplay() {
        val runner = workoutRunner ?: return
        val progress = runner.progress.value
        
        binding.tvWorkoutProgress.text = progress.getPositionDisplay()
        
        // Update progress bar
        val progressPercent = (progress.getOverallProgress() * 100).toInt()
        binding.progressBarWorkout.progress = progressPercent
    }

    // ==================== Panel Display Methods ====================

    private fun showPreparingPanel(exercise: LoadedExercise) {
        hideAllPanels()
        binding.panelPreparing.visibility = View.VISIBLE
        
        // Update exercise info
        binding.tvNextExerciseName.text = exercise.getDisplayName("en")
        binding.tvNextExerciseNameAr.text = exercise.getDisplayName("ar")
        
        val targetText = when {
            exercise.isHoldExercise() -> {
                val seconds = exercise.getTargetDurationSec() ?: 30
                "Set ${exercise.setIndex}/${exercise.totalSets} • Hold for $seconds seconds"
            }
            else -> {
                val reps = exercise.getTargetReps() ?: 10
                "Set ${exercise.setIndex}/${exercise.totalSets} • $reps reps"
            }
        }
        binding.tvNextExerciseTarget.text = targetText
        binding.btnStartExercise.text = "Start Set"
        
        updateProgressDisplay()
    }

    private fun showRestPanel(durationMs: Long) {
        hideAllPanels()
        binding.panelRest.visibility = View.VISIBLE
        
        val progress = workoutRunner?.progress?.value
        val isBetweenSets = progress?.currentSetIndex?.let { it > 1 } ?: false
        binding.tvRestTitle.text = if (isBetweenSets) "Rest Between Sets" else "Rest Between Exercises"
        
        // Get next exercise name (based on upcoming index)
        val nextExerciseName = try {
            val nextIndex = workoutRunner?.progress?.value?.currentExerciseIndex ?: 0
            val slug = workoutConfig?.exercises?.getOrNull(nextIndex)?.exercise
            val repo = ExerciseRepository.getInstance(this)
            if (slug != null) repo.getExercise(slug)?.name?.en else null
        } catch (e: Exception) {
            null
        }
        binding.tvRestNextExercise.text = nextExerciseName ?: "Next Exercise"
        
        startRestCountdown(durationMs, binding.tvRestCountdown)
        
        updateProgressDisplay()
    }

    private fun showCompletePanel(result: WorkoutResult) {
        hideAllPanels()
        binding.panelComplete.visibility = View.VISIBLE
        
        // Calculate total time
        val totalTimeMs = System.currentTimeMillis() - startTimeMs
        val minutes = (totalTimeMs / 60000).toInt()
        val seconds = ((totalTimeMs % 60000) / 1000).toInt()
        
        binding.tvSummaryExercises.text = result.totalSets.toString()
        binding.tvSummaryRounds.text = result.totalExercises.toString()
        binding.tvSummaryTime.text = String.format("%02d:%02d", minutes, seconds)
        binding.tvSummaryAccuracy.text = "${result.getOverallAccuracy().toInt()}%"
        
        // Hide progress bar for completion
        binding.progressBarWorkout.progress = 100
    }

    private fun hideAllPanels() {
        binding.panelPreparing.visibility = View.GONE
        binding.panelRest.visibility = View.GONE
        binding.panelRoundComplete.visibility = View.GONE
        binding.panelComplete.visibility = View.GONE
        
        // Cancel any running timer
        restTimer?.cancel()
        restTimer = null
    }

    // ==================== Exercise Launch (Sequential Mode) ====================

    private fun startCurrentExercise() {
        val exercise = workoutRunner?.currentExercise?.value ?: return
        val runner = workoutRunner ?: return
        
        runner.markExercising()
        
        // Launch TrainingActivity with the exercise
        val intent = Intent(this, TrainingActivity::class.java).apply {
            putExtra(TrainingActivity.EXTRA_EXERCISE_NAME, exercise.workoutExercise.exercise)
            // Kept for backward compatibility, ignored by new engine
            putExtra(TrainingActivity.EXTRA_DIFFICULTY, "")
            putExtra(TrainingActivity.EXTRA_POSE_VARIANT, exercise.workoutExercise.variantIndex)
            putExtra(TrainingActivity.EXTRA_TRAINING_MODE, TrainingActivity.MODE_CAMERA)

            // Pass target override if specified
            exercise.workoutExercise.targetReps?.let {
                putExtra(TrainingActivity.EXTRA_TARGET_REPS_OVERRIDE, it)
            }
            exercise.workoutExercise.targetDurationSec?.let {
                putExtra(TrainingActivity.EXTRA_TARGET_DURATION_OVERRIDE, it * 1000L)
            }

            // Pass weight (per-set overrides have priority)
            val perSetWeight = exercise.workoutExercise.weightPerSet?.getOrNull(exercise.setIndex - 1)
            val weightKg = perSetWeight ?: exercise.workoutExercise.weightKg
            weightKg?.let { putExtra(TrainingActivity.EXTRA_WEIGHT_KG, it) }
            putExtra(TrainingActivity.EXTRA_WEIGHT_UNIT, "kg")
        }
        
        trainingLauncher.launch(intent)
    }

    private fun handleTrainingResult(resultCode: Int, data: Intent?) {
        // Extract results from TrainingActivity
        val repsCompleted = data?.getIntExtra(RESULT_REPS_COMPLETED, 0) ?: 0
        val durationMs = data?.getLongExtra(RESULT_DURATION_MS, 0L) ?: 0L
        val accuracy = data?.getFloatExtra(RESULT_ACCURACY, 0f) ?: 0f
        val isCompleted = data?.getBooleanExtra(RESULT_IS_COMPLETED, true) ?: true
        
        Log.d(TAG, "Training result: reps=$repsCompleted, duration=$durationMs, accuracy=$accuracy, completed=$isCompleted")
        
        // Notify workout runner that exercise is done
        workoutRunner?.onExerciseCompleted(
            completedReps = repsCompleted,
            actualDurationMs = durationMs,
            accuracy = accuracy,
            isCompleted = isCompleted
        )
    }

    // ==================== Rest Timer ====================

    private fun startRestCountdown(durationMs: Long, textView: android.widget.TextView) {
        restTimer?.cancel()
        
        restTimer = object : CountDownTimer(durationMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000).toInt()
                textView.text = seconds.toString()
            }
            
            override fun onFinish() {
                textView.text = "0"
                workoutRunner?.onRestCompleted()
            }
        }.start()
    }

    private fun skipRest() {
        restTimer?.cancel()
        restTimer = null
        workoutRunner?.skipRest()
    }

    // ==================== Lifecycle ====================

    private fun confirmExit() {
        // TODO: Show confirmation dialog
        // For now, just finish
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        restTimer?.cancel()
        restTimer = null
    }
}
