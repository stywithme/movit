package com.trainingvalidator.poc.ui.workouts

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.trainingvalidator.poc.PoseApp
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.ActivityWorkoutSessionBinding
import com.trainingvalidator.poc.network.ApiClient
import com.trainingvalidator.poc.storage.AuthManager
import com.trainingvalidator.poc.training.models.ProgramSessionItem
import com.trainingvalidator.poc.training.models.WorkoutConfig
import com.trainingvalidator.poc.ui.programs.ProgramSessionReportActivity
import com.trainingvalidator.poc.ui.train.TrainingActivity
import com.trainingvalidator.poc.ui.utils.currentLanguage
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * WorkoutSessionActivity — Explore Mode Training Sequencer
 *
 * Drives a multi-exercise workout session in Explore mode (free training).
 * Accepts a [WorkoutConfig] and sequences exercises through [TrainingActivity].
 *
 * After all exercises are complete, uploads results to the explore endpoint
 * and shows a [ProgramSessionReportActivity] summary.
 *
 * This activity is intentionally stateless between exercises — all session
 * data is collected from TrainingActivity results and uploaded at the end.
 */
class WorkoutSessionActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "WorkoutSessionActivity"

        const val EXTRA_WORKOUT_CONFIG_JSON = "workout_config_json"
        const val EXTRA_WORKOUT_ID = "workout_id"
        const val EXTRA_CONTEXT = "session_context"   // explore_workout | quick_start

        fun createIntent(
            context: Context,
            workoutConfig: WorkoutConfig,
            workoutId: String? = null,
            sessionContext: String = "explore_workout"
        ): Intent = Intent(context, WorkoutSessionActivity::class.java).apply {
            putExtra(EXTRA_WORKOUT_CONFIG_JSON, Gson().toJson(workoutConfig))
            putExtra(EXTRA_WORKOUT_ID, workoutId)
            putExtra(EXTRA_CONTEXT, sessionContext)
        }
    }

    // ── State ──────────────────────────────────────────────────────────────────

    private lateinit var binding: ActivityWorkoutSessionBinding
    private lateinit var workoutConfig: WorkoutConfig

    private var workoutId: String? = null
    private var sessionContext: String = "explore_workout"

    /** Client-generated group ID linking all sessions in this workout block */
    private val groupId: String = UUID.randomUUID().toString()

    /**
     * Maps session report IDs → exerciseSlug.
     * Populated in [handleExerciseResult] as each exercise completes.
     * Using a list of pairs preserves insertion order and handles multiple IDs per exercise.
     */
    private val sessionIdToExercise = mutableListOf<Pair<String, String>>()

    /** Aggregated metrics for the final report */
    private var totalDurationMs: Long = 0L
    private var totalReps: Int = 0
    private var totalSets: Int = 0
    private var completedSets: Int = 0
    private var totalFormScoreSum: Float = 0f
    private var sessionCount: Int = 0

    /** Current exercise index being trained */
    private var currentExerciseIndex: Int = 0

    /** Built session items for each exercise (converted from WorkoutConfig) */
    private var sessionItems: List<ProgramSessionItem> = emptyList()

    private val gson = Gson()

    private val trainingLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleExerciseResult(result.resultCode, result.data)
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWorkoutSessionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val configJson = intent.getStringExtra(EXTRA_WORKOUT_CONFIG_JSON)
        workoutId = intent.getStringExtra(EXTRA_WORKOUT_ID)
        sessionContext = intent.getStringExtra(EXTRA_CONTEXT) ?: "explore_workout"

        if (configJson.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.error_workout_not_found), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        workoutConfig = try {
            gson.fromJson(configJson, WorkoutConfig::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse workout config", e)
            Toast.makeText(this, getString(R.string.error_workout_not_found), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupUI()
        buildSessionItems()
        startNextExercise()
    }

    // ── Setup ──────────────────────────────────────────────────────────────────

    private fun setupUI() {
        val language = currentLanguage
        binding.tvWorkoutTitle.text = workoutConfig.name.get(language).ifBlank { workoutConfig.name.en }
        binding.btnBack.setOnClickListener { showExitConfirmation() }
        updateProgress()
    }

    private fun buildSessionItems() {
        sessionItems = workoutConfig.exercises.mapIndexed { index, exercise ->
            ProgramSessionItem(
                type = "exercise",
                exerciseSlug = exercise.exercise,
                sets = exercise.sets,
                targetReps = exercise.targetReps,
                targetDuration = exercise.targetDurationSec,
                restBetweenSetsMs = exercise.restBetweenSetsMs,
                weightKg = exercise.weightKg,
                sortOrder = index
            )
        }
    }

    private fun updateProgress() {
        val total = sessionItems.size
        val current = currentExerciseIndex + 1
        binding.tvProgress.text = getString(R.string.workout_session_progress_format, current, total)
        binding.progressBar.max = total
        binding.progressBar.progress = currentExerciseIndex
    }

    // ── Training Sequencer ─────────────────────────────────────────────────────

    private fun startNextExercise() {
        if (currentExerciseIndex >= sessionItems.size) {
            onAllExercisesComplete()
            return
        }

        val item = sessionItems[currentExerciseIndex]
        updateProgress()

        // Pass single exercise as a one-item session
        val singleItemList = listOf(item)
        val itemsJson = gson.toJson(singleItemList)

        val intent = Intent(this, TrainingActivity::class.java).apply {
            putExtra(TrainingActivity.EXTRA_IS_SESSION_MODE, true)
            putExtra(TrainingActivity.EXTRA_SESSION_ITEMS_JSON, itemsJson)
            putExtra(TrainingActivity.EXTRA_TRAINING_MODE, TrainingActivity.MODE_CAMERA)
        }

        trainingLauncher.launch(intent)
    }

    private fun handleExerciseResult(resultCode: Int, data: android.content.Intent?) {
        if (resultCode == RESULT_OK && data != null) {
            val completedSetsCount = data.getIntExtra(TrainingActivity.RESULT_SESSION_SETS_COMPLETED, 0)
            val plannedSets = data.getIntExtra(TrainingActivity.RESULT_SESSION_SETS_PLANNED, 0)
            val durationMs = data.getLongExtra(TrainingActivity.RESULT_DURATION_MS, 0L)
            val reps = data.getIntExtra(TrainingActivity.RESULT_SESSION_TOTAL_REPS, 0)
            val formScore = data.getFloatExtra(TrainingActivity.RESULT_SESSION_AVG_FORM_SCORE, 0f)
            val reportIds = data.getStringArrayListExtra(TrainingActivity.RESULT_SESSION_REPORT_IDS)

            // Aggregate metrics
            totalDurationMs += durationMs
            totalReps += reps
            totalSets += plannedSets
            this.completedSets += completedSetsCount
            if (formScore > 0f) {
                totalFormScoreSum += formScore
                sessionCount++
            }

            // Map each report ID to the exercise slug for accurate upload payload
            val exerciseSlug = sessionItems.getOrNull(currentExerciseIndex)?.exerciseSlug ?: ""
            if (!reportIds.isNullOrEmpty()) {
                reportIds.forEach { id -> sessionIdToExercise.add(id to exerciseSlug) }
            }

            currentExerciseIndex++

            if (currentExerciseIndex < sessionItems.size) {
                startNextExercise()
            } else {
                onAllExercisesComplete()
            }
        } else {
            // User exited mid-session — still show report with partial data
            Log.d(TAG, "Exercise cancelled at index $currentExerciseIndex")
            onAllExercisesComplete()
        }
    }

    private fun onAllExercisesComplete() {
        // Fire-and-forget upload using applicationScope so it survives finish()
        uploadExploreSession()

        // Navigate to session report immediately — don't wait for upload
        val allIds = sessionIdToExercise.map { it.first }
        val reportIntent = Intent(this, ProgramSessionReportActivity::class.java).apply {
            putExtra(ProgramSessionReportActivity.EXTRA_TOTAL_ITEMS, sessionItems.size)
            putExtra(ProgramSessionReportActivity.EXTRA_TOTAL_SETS, totalSets)
            putExtra(ProgramSessionReportActivity.EXTRA_COMPLETED_SETS, completedSets)
            putExtra(ProgramSessionReportActivity.EXTRA_DURATION_MS, totalDurationMs)
            putExtra(
                ProgramSessionReportActivity.EXTRA_AVG_ACCURACY,
                if (totalSets > 0) (completedSets.toFloat() / totalSets) * 100f else 0f
            )
            if (allIds.isNotEmpty()) {
                putStringArrayListExtra(ProgramSessionReportActivity.EXTRA_REPORT_IDS, ArrayList(allIds))
            }
        }
        startActivity(reportIntent)
        finish()
    }

    // ── Backend Upload ─────────────────────────────────────────────────────────

    /**
     * Uploads the explore session using [PoseApp.applicationScope] so the coroutine
     * is NOT cancelled when this Activity calls finish().
     *
     * Progression notifications are intentionally NOT marked as seen here.
     * [ProgramSessionReportActivity] handles showing and acknowledging them.
     */
    private fun uploadExploreSession() {
        if (sessionIdToExercise.isEmpty()) return
        val authHeader = AuthManager.getAuthHeader(this) ?: return
        val payload = buildExplorePayload()

        PoseApp.instance.applicationScope.launch {
            try {
                ApiClient.mobileSyncApi.uploadExploreSession(authHeader, payload)
                Log.d(TAG, "Explore session uploaded successfully, groupId=$groupId")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to upload explore session (non-fatal)", e)
            }
        }
    }

    /**
     * Builds the explore upload payload.
     * Each session ID is correctly mapped to its source exercise slug
     * using [sessionIdToExercise] which was populated during result collection.
     */
    private fun buildExplorePayload(): Map<String, Any?> {
        return mapOf(
            "groupId" to groupId,
            "workoutId" to workoutId,
            "isCustomized" to false,
            "context" to sessionContext,
            "sessions" to sessionIdToExercise.map { (id, exerciseSlug) ->
                mapOf(
                    "id" to id,
                    "groupId" to groupId,
                    "workoutId" to workoutId,
                    "context" to sessionContext,
                    "exerciseId" to exerciseSlug
                )
            }
        )
    }

    // ── Exit Confirmation ──────────────────────────────────────────────────────

    private fun showExitConfirmation() {
        if (currentExerciseIndex == 0) {
            finish()
            return
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.exit_workout_title))
            .setMessage(getString(R.string.exit_workout_message))
            .setPositiveButton(getString(R.string.exit_confirm)) { _, _ -> onAllExercisesComplete() }
            .setNegativeButton(getString(R.string.back)) { dialog, _ -> dialog.dismiss() }
            .show()
    }
}
