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
import com.trainingvalidator.poc.databinding.ActivityWorkoutBinding
import com.trainingvalidator.poc.training.loader.WorkoutLoader
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
 *    - Repeat until all exercises and rounds are complete
 * 
 * 2. ALTERNATING Mode (Hot-Swap):
 *    - Launch TrainingActivity ONCE in Workout Mode
 *    - TrainingActivity handles hot-swap internally (no camera restart)
 *    - Faster, smoother transitions between exercises
 *    - Used for true alternating workouts (1 rep left, 1 rep right, etc.)
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
        
        // Extra for alternating mode
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
    
    // Activity result launcher for TrainingActivity in Workout Mode (Hot-Swap)
    private val workoutModeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleWorkoutModeResult(result.resultCode, result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityWorkoutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Get workout name from intent
        workoutName = intent.getStringExtra(EXTRA_WORKOUT_NAME)
        if (workoutName == null) {
            Toast.makeText(this, "No workout specified", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        difficultyStr = intent.getStringExtra(EXTRA_DEFAULT_DIFFICULTY) ?: "beginner"
        
        loadWorkout(workoutName!!)
        setupUI()
    }

    private fun loadWorkout(name: String) {
        workoutConfig = WorkoutLoader.load(assets, name)
        
        if (workoutConfig == null) {
            Toast.makeText(this, "Failed to load workout: $workoutName", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        val config = workoutConfig!!
        
        // Parse difficulty
        val defaultDifficulty = when (difficultyStr.lowercase()) {
            "beginner" -> DifficultyType.BEGINNER
            "normal" -> DifficultyType.NORMAL
            "advanced" -> DifficultyType.ADVANCED
            else -> DifficultyType.BEGINNER
        }
        
        // Create workout runner
        workoutRunner = WorkoutRunner(
            workoutConfig = config,
            assets = assets,
            defaultDifficulty = defaultDifficulty
        ).apply {
            setupCallbacks(this)
        }
        
        Log.d(TAG, "Loaded workout: ${config.name.en} with ${config.exercises.size} exercises")
    }

    private fun setupCallbacks(runner: WorkoutRunner) {
        runner.onExerciseReady = { loadedExercise ->
            showPreparingPanel(loadedExercise)
        }
        
        runner.onRestStarted = { durationMs, isRoundRest ->
            if (isRoundRest) {
                showRoundCompletePanel(durationMs)
            } else {
                showRestPanel(durationMs)
            }
        }
        
        runner.onExerciseSwitched = { loadedExercise, previousName ->
            // In alternating mode with no rest, immediately start next exercise
            Log.d(TAG, "Switched from $previousName to ${loadedExercise.getDisplayName()}")
            // Auto-start the next exercise (no preparing panel in fast alternating mode)
            if (runner.isAlternatingMode && workoutConfig?.restBetweenSwitchMs == 0L) {
                startCurrentExercise()
            } else {
                showPreparingPanel(loadedExercise)
            }
        }
        
        runner.onRoundCompleted = { roundNumber, totalRounds ->
            Log.d(TAG, "Round $roundNumber of $totalRounds completed")
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
        
        // Check if this is alternating mode - use Hot-Swap
        if (workoutConfig?.isAlternating() == true) {
            // Alternating mode: Launch TrainingActivity ONCE in Workout Mode
            startWorkoutModeTraining()
        } else {
            // Sequential mode: Use traditional flow
            workoutRunner?.start()
        }
        
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
        
        // Target info - show effective target for this session (may be limited in alternating mode)
        val targetText = when {
            exercise.isHoldExercise() -> {
                val seconds = exercise.getTargetDurationSec() ?: 30
                "Target: Hold for $seconds seconds"
            }
            exercise.hasRepLimit() -> {
                // Alternating mode: show reps for this session
                val repsThisSession = exercise.maxRepsThisSession ?: 1
                val totalReps = exercise.getTargetReps() ?: 10
                val completed = workoutRunner?.progress?.value?.exerciseRepsCompleted?.get(exercise.indexInRound) ?: 0
                "Do $repsThisSession rep${if (repsThisSession > 1) "s" else ""} ($completed/$totalReps total)"
            }
            else -> {
                val reps = exercise.getTargetReps() ?: 10
                "Target: $reps reps"
            }
        }
        binding.tvNextExerciseTarget.text = targetText
        
        // Update button text for alternating mode
        if (workoutRunner?.isAlternatingMode == true) {
            binding.btnStartExercise.text = "Go!"
        } else {
            binding.btnStartExercise.text = "Start Exercise"
        }
        
        updateProgressDisplay()
    }

    private fun showRestPanel(durationMs: Long) {
        hideAllPanels()
        binding.panelRest.visibility = View.VISIBLE
        
        binding.tvRestTitle.text = "Rest Time"
        
        // Get next exercise name
        val nextExercise = workoutRunner?.currentExercise?.value
        binding.tvRestNextExercise.text = nextExercise?.getDisplayName("en") ?: "Next Exercise"
        
        startRestCountdown(durationMs, binding.tvRestCountdown)
        
        updateProgressDisplay()
    }

    private fun showRoundCompletePanel(durationMs: Long) {
        hideAllPanels()
        binding.panelRoundComplete.visibility = View.VISIBLE
        
        val progress = workoutRunner?.progress?.value ?: return
        
        binding.tvRoundCompleteTitle.text = "Round ${progress.currentRound - 1} Complete!"
        
        val remainingRounds = progress.totalRounds - progress.currentRound + 1
        binding.tvRoundCompleteSubtitle.text = "$remainingRounds more round${if (remainingRounds > 1) "s" else ""} to go"
        
        startRestCountdown(durationMs, binding.tvRoundRestCountdown)
        
        updateProgressDisplay()
    }

    private fun showCompletePanel(result: WorkoutResult) {
        hideAllPanels()
        binding.panelComplete.visibility = View.VISIBLE
        
        // Calculate total time
        val totalTimeMs = System.currentTimeMillis() - startTimeMs
        val minutes = (totalTimeMs / 60000).toInt()
        val seconds = ((totalTimeMs % 60000) / 1000).toInt()
        
        binding.tvSummaryExercises.text = result.totalExercises.toString()
        binding.tvSummaryRounds.text = result.completedRounds.toString()
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

    // ==================== Workout Mode (Hot-Swap) ====================
    
    /**
     * Start training in Workout Mode (Hot-Swap)
     * Launches TrainingActivity ONCE with workout_name instead of exercise_name
     * TrainingActivity handles all exercise switching internally
     */
    private fun startWorkoutModeTraining() {
        val name = workoutName ?: return
        
        Log.d(TAG, "Starting Workout Mode (Hot-Swap) for: $name")
        
        // Hide preparing panel - TrainingActivity handles everything
        hideAllPanels()
        
        // Show a brief loading indicator
        binding.panelPreparing.visibility = View.VISIBLE
        binding.tvNextExerciseName.text = workoutConfig?.name?.en ?: "Workout"
        binding.tvNextExerciseNameAr.text = workoutConfig?.name?.ar ?: ""
        binding.tvNextExerciseTarget.text = "Loading workout..."
        binding.btnStartExercise.visibility = View.GONE
        
        // Launch TrainingActivity in Workout Mode
        val intent = Intent(this, TrainingActivity::class.java).apply {
            putExtra(TrainingActivity.EXTRA_WORKOUT_NAME, name)
            putExtra(TrainingActivity.EXTRA_IS_WORKOUT_MODE, true)
            putExtra(TrainingActivity.EXTRA_DIFFICULTY, difficultyStr)
            putExtra(TrainingActivity.EXTRA_TRAINING_MODE, TrainingActivity.MODE_CAMERA)
        }
        
        workoutModeLauncher.launch(intent)
    }
    
    /**
     * Handle result from TrainingActivity in Workout Mode
     * Workout is fully complete when this returns
     */
    private fun handleWorkoutModeResult(resultCode: Int, data: Intent?) {
        val repsCompleted = data?.getIntExtra(RESULT_REPS_COMPLETED, 0) ?: 0
        val durationMs = data?.getLongExtra(RESULT_DURATION_MS, 0L) ?: 0L
        val accuracy = data?.getFloatExtra(RESULT_ACCURACY, 0f) ?: 0f
        val isCompleted = data?.getBooleanExtra(RESULT_IS_COMPLETED, true) ?: true
        
        Log.d(TAG, "Workout Mode result: reps=$repsCompleted, duration=$durationMs, completed=$isCompleted")
        
        // Show completion panel
        showWorkoutModeCompletePanel(repsCompleted, durationMs, accuracy, isCompleted)
    }
    
    /**
     * Show completion panel for Workout Mode
     */
    private fun showWorkoutModeCompletePanel(
        totalReps: Int,
        durationMs: Long,
        accuracy: Float,
        isCompleted: Boolean
    ) {
        hideAllPanels()
        binding.panelComplete.visibility = View.VISIBLE
        
        // Calculate total time
        val totalTimeMs = System.currentTimeMillis() - startTimeMs
        val minutes = (totalTimeMs / 60000).toInt()
        val seconds = ((totalTimeMs % 60000) / 1000).toInt()
        
        binding.tvSummaryExercises.text = "${workoutConfig?.exercises?.size ?: 0}"
        binding.tvSummaryRounds.text = "${workoutConfig?.rounds ?: 1}"
        binding.tvSummaryTime.text = String.format("%02d:%02d", minutes, seconds)
        binding.tvSummaryAccuracy.text = "${accuracy.toInt()}%"
        
        // Progress bar full
        binding.progressBarWorkout.progress = 100
    }

    // ==================== Exercise Launch (Sequential Mode) ====================

    private fun startCurrentExercise() {
        val exercise = workoutRunner?.currentExercise?.value ?: return
        val runner = workoutRunner ?: return
        
        runner.markExercising()
        
        // Launch TrainingActivity with the exercise
        val intent = Intent(this, TrainingActivity::class.java).apply {
            putExtra(TrainingActivity.EXTRA_EXERCISE_NAME, exercise.config.fileName)
            putExtra(TrainingActivity.EXTRA_DIFFICULTY, exercise.difficulty.name.lowercase())
            putExtra(TrainingActivity.EXTRA_POSE_VARIANT, exercise.workoutExercise.variantIndex)
            putExtra(TrainingActivity.EXTRA_TRAINING_MODE, TrainingActivity.MODE_CAMERA)
            
            // In alternating mode, pass the rep limit for this session
            if (runner.isAlternatingMode && exercise.hasRepLimit()) {
                putExtra(EXTRA_MAX_REPS_THIS_SESSION, exercise.maxRepsThisSession ?: 1)
            }
            
            // Pass target override if specified (for sequential mode)
            exercise.workoutExercise.target.reps?.let {
                putExtra("target_reps_override", it)
            }
            exercise.workoutExercise.target.durationSec?.let {
                putExtra("target_duration_override", it)
            }
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
