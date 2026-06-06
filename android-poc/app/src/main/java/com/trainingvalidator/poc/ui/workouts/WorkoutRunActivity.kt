package com.trainingvalidator.poc.ui.workouts

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.trainingvalidator.poc.storage.EntityAudioPrefetchManager
import com.google.gson.Gson
import com.trainingvalidator.poc.PoseApp
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.ActivityWorkoutRunBinding
import com.trainingvalidator.poc.network.ApiClient
import com.trainingvalidator.poc.storage.AuthManager
import com.trainingvalidator.poc.training.models.WorkoutLineItem
import com.trainingvalidator.poc.training.models.WorkoutConfig
import com.trainingvalidator.poc.training.workout.WorkoutTrainingEngine
import com.trainingvalidator.poc.ui.report.WorkoutReportActivity
import com.trainingvalidator.poc.ui.train.TrainingActivity
import com.trainingvalidator.poc.ui.utils.currentLanguage
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * WorkoutRunActivity — Explore Mode Training Orchestrator
 *
 * Thin orchestrator for multi-exercise workouts (Explore / Quick Start).
 * Builds a unified WorkoutLineItem list from [WorkoutConfig] and launches
 * a single [TrainingActivity] in workout mode with ALL exercises at once.
 *
 * [WorkoutTrainingEngine] inside [TrainingActivity] handles the full exercise
 * sequencing, rest timers, set tracking, and per-exercise report generation.
 *
 * On completion, uploads results to the explore endpoint and navigates
 * to the workout report screen.
 */
class WorkoutRunActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "WorkoutRunActivity"

        const val EXTRA_WORKOUT_CONFIG_JSON = "workout_config_json"
        const val EXTRA_WORKOUT_ID = "workout_id"
        const val EXTRA_CONTEXT = "workout_context"   // explore_workout | quick_start

        const val EXTRA_WORKOUT_SLUG = "workout_slug"

        fun createIntent(
            context: Context,
            workoutConfig: WorkoutConfig,
            workoutId: String? = null,
            workoutContext: String = "explore_workout"
        ): Intent = Intent(context, WorkoutRunActivity::class.java).apply {
            putExtra(EXTRA_WORKOUT_CONFIG_JSON, Gson().toJson(workoutConfig))
            putExtra(EXTRA_WORKOUT_ID, workoutId)
            putExtra(EXTRA_CONTEXT, workoutContext)
            putExtra(EXTRA_WORKOUT_SLUG, workoutConfig.fileName)
        }
    }

    // ── State ──────────────────────────────────────────────────────────────────

    private lateinit var binding: ActivityWorkoutRunBinding
    private lateinit var workoutConfig: WorkoutConfig

    private var workoutId: String? = null
    private var workoutContext: String = "explore_workout"

    /** Client-generated group ID linking all executions in this workout block */
    private val groupId: String = UUID.randomUUID().toString()

    private val gson = Gson()

    /** Workout report returned from TrainingActivity */
    private var workoutReportJson: String? = null
    private var workoutReportIds: List<String> = emptyList()    // PostTrainingReport IDs (for report UI)
    private var workoutExecutionIds: List<String> = emptyList()   // WorkoutUpload IDs (for backend linking)

    private val trainingLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleWorkoutResult(result.resultCode, result.data)
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWorkoutRunBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val configJson = intent.getStringExtra(EXTRA_WORKOUT_CONFIG_JSON)
        workoutId = intent.getStringExtra(EXTRA_WORKOUT_ID)
        workoutContext = intent.getStringExtra(EXTRA_CONTEXT) ?: "explore_workout"

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

        val slugExtra = intent.getStringExtra(EXTRA_WORKOUT_SLUG)?.trim().orEmpty()
        if (slugExtra.isNotBlank()) {
            workoutConfig.fileName = slugExtra
        }

        setupUI()
        lifecycleScope.launch {
            val slug = workoutConfig.fileName.ifBlank { slugExtra }
            if (slug.isNotBlank()) {
                EntityAudioPrefetchManager(this@WorkoutRunActivity).prefetchWorkoutIfNeeded(slug, workoutConfig)
            }
        }
        launchWorkoutTraining()
    }

    // ── Setup ──────────────────────────────────────────────────────────────────

    private fun setupUI() {
        val language = currentLanguage
        binding.tvWorkoutTitle.text = workoutConfig.name.get(language).ifBlank { workoutConfig.name.en }
        binding.btnBack.setOnClickListener { finish() }
    }

    /**
     * Builds workout line items from [WorkoutConfig] and launches a single
     * [TrainingActivity] in workout mode with all exercises.
     *
     * Interleaves "rest" items between exercises using [WorkoutExercise.restAfterExerciseMs],
     * matching the pattern used by ProgramWorkoutActivity.
     */
    private fun launchWorkoutTraining() {
        val items = buildWorkoutLineItems()
        if (items.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_no_exercises), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val itemsJson = gson.toJson(items)

        val intent = Intent(this, TrainingActivity::class.java).apply {
            putExtra(TrainingActivity.EXTRA_IS_WORKOUT_MODE, true)
            putExtra(TrainingActivity.EXTRA_WORKOUT_ITEMS_JSON, itemsJson)
            putExtra(TrainingActivity.EXTRA_WORKOUT_ROLE, "MAIN")
            putExtra(TrainingActivity.EXTRA_TRAINING_MODE, TrainingActivity.MODE_CAMERA)
        }

        trainingLauncher.launch(intent)
    }

    /**
     * Converts [WorkoutConfig.exercises] into a flat list of [WorkoutLineItem]s,
     * interleaving "rest" items between exercises (not after the last one).
     */
    private fun buildWorkoutLineItems(): List<WorkoutLineItem> {
        val items = mutableListOf<WorkoutLineItem>()
        var sortIndex = 0

        workoutConfig.exercises.forEachIndexed { index, exercise ->
            items.add(
                WorkoutLineItem(
                    type = "exercise",
                    exerciseSlug = exercise.exercise,
                    sets = exercise.sets,
                    targetReps = exercise.targetReps,
                    targetDuration = exercise.targetDurationSec,
                    restBetweenSetsMs = exercise.restBetweenSetsMs,
                    weightKg = exercise.weightKg,
                    weightPerSet = exercise.weightPerSet,
                    notes = exercise.notes,
                    variantIndex = exercise.variantIndex,
                    sortOrder = sortIndex++
                )
            )

            val isLastExercise = index == workoutConfig.exercises.size - 1
            if (!isLastExercise && exercise.restAfterExerciseMs > 0) {
                items.add(
                    WorkoutLineItem(
                        type = "rest",
                        restDurationMs = exercise.restAfterExerciseMs,
                        sortOrder = sortIndex++
                    )
                )
            }
        }
        return items
    }

    // ── Result Handling ────────────────────────────────────────────────────────

    private fun handleWorkoutResult(resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK && data != null) {
            workoutReportJson = data.getStringExtra(TrainingActivity.RESULT_WORKOUT_REPORT_JSON)
            workoutReportIds = data.getStringArrayListExtra(TrainingActivity.RESULT_WORKOUT_REPORT_IDS) ?: emptyList()
            workoutExecutionIds = data.getStringArrayListExtra(TrainingActivity.RESULT_WORKOUT_EXECUTION_IDS) ?: emptyList()

            Log.d(TAG, "Workout complete: ${workoutReportIds.size} report IDs, " +
                    "${workoutExecutionIds.size} execution IDs, groupId=$groupId")
        } else {
            Log.d(TAG, "Workout cancelled or failed")
        }

        onWorkoutComplete()
    }

    private fun onWorkoutComplete() {
        uploadExploreWorkout()
        navigateToReport()
        finish()
    }

    private fun navigateToReport() {
        if (workoutReportIds.isEmpty() && workoutReportJson.isNullOrBlank()) {
            Log.w(TAG, "No report data available — skipping report screen")
            return
        }

        val reportIntent = WorkoutReportActivity.createWorkoutIntent(
            context = this,
            reportIds = workoutReportIds,
            workoutReportJson = workoutReportJson
        )
        startActivity(reportIntent)
    }

    // ── Backend Upload ─────────────────────────────────────────────────────────

    /**
     * Uploads the explore workout using [PoseApp.applicationScope] so the coroutine
     * survives this Activity's finish().
     *
     * Individual exercise executions are already synced by TrainingActivity.
     * This endpoint links them under a shared [groupId].
     */
    private fun uploadExploreWorkout() {
        if (workoutExecutionIds.isEmpty()) return
        val authHeader = AuthManager.getAuthHeader(this) ?: return

        val exerciseSlugs = extractExerciseSlugsFromReport()
        val payload = buildExplorePayload(exerciseSlugs)

        PoseApp.instance.applicationScope.launch {
            try {
                ApiClient.mobileSyncApi.uploadExploreWorkout(authHeader, payload)
                Log.d(TAG, "Explore workout uploaded successfully, groupId=$groupId")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to upload explore workout (non-fatal)", e)
            }
        }
    }

    private fun extractExerciseSlugsFromReport(): List<String> {
        val json = workoutReportJson ?: return emptyList()
        return try {
            val type = object : com.google.gson.reflect.TypeToken<WorkoutTrainingEngine.WorkoutReport>() {}.type
            val report = gson.fromJson<WorkoutTrainingEngine.WorkoutReport>(json, type)
            report.exerciseReports.map { it.exerciseSlug }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse workout report for exercise slugs", e)
            workoutConfig.exercises.map { it.exercise }
        }
    }

    /**
     * Builds the explore upload payload linking individual executions under [groupId].
     */
    private fun buildExplorePayload(exerciseSlugs: List<String>): Map<String, Any?> {
        return mapOf(
            "workoutGroupId" to groupId,
            "workoutTemplateId" to workoutId,
            "isCustomized" to false,
            "context" to workoutContext,
            "executions" to workoutExecutionIds.mapIndexed { index, id ->
                mapOf(
                    "id" to id,
                    "workoutGroupId" to groupId,
                    "workoutTemplateId" to workoutId,
                    "context" to workoutContext,
                    "exerciseId" to (exerciseSlugs.getOrNull(index) ?: "")
                )
            }
        )
    }
}
