package com.trainingvalidator.poc.ui.workouts

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import com.trainingvalidator.poc.training.session.SessionTrainingEngine
import com.trainingvalidator.poc.ui.report.SessionReportActivity
import com.trainingvalidator.poc.ui.train.TrainingActivity
import com.trainingvalidator.poc.ui.utils.currentLanguage
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * WorkoutSessionActivity — Explore Mode Training Orchestrator
 *
 * Thin orchestrator for multi-exercise workout sessions (Explore / Quick Start).
 * Builds a unified ProgramSessionItem list from [WorkoutConfig] and launches
 * a single [TrainingActivity] in session mode with ALL exercises at once.
 *
 * [SessionTrainingEngine] inside [TrainingActivity] handles the full exercise
 * sequencing, rest timers, set tracking, and per-exercise report generation.
 *
 * On completion, uploads results to the explore endpoint and navigates
 * to the session report screen.
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

    private val gson = Gson()

    /** Session report returned from TrainingActivity */
    private var sessionReportJson: String? = null
    private var sessionReportIds: List<String> = emptyList()    // PostTrainingReport IDs (for report UI)
    private var sessionSessionIds: List<String> = emptyList()   // SessionUpload IDs (for backend linking)

    private val trainingLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleSessionResult(result.resultCode, result.data)
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
        launchTrainingSession()
    }

    // ── Setup ──────────────────────────────────────────────────────────────────

    private fun setupUI() {
        val language = currentLanguage
        binding.tvWorkoutTitle.text = workoutConfig.name.get(language).ifBlank { workoutConfig.name.en }
        binding.btnBack.setOnClickListener { finish() }
    }

    /**
     * Builds session items from [WorkoutConfig] and launches a single
     * [TrainingActivity] in session mode with all exercises.
     *
     * Interleaves "rest" items between exercises using [WorkoutExercise.restAfterExerciseMs],
     * matching the pattern used by ProgramSessionActivity.
     */
    private fun launchTrainingSession() {
        val items = buildSessionItems()
        if (items.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_no_exercises), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val itemsJson = gson.toJson(items)

        val intent = Intent(this, TrainingActivity::class.java).apply {
            putExtra(TrainingActivity.EXTRA_IS_SESSION_MODE, true)
            putExtra(TrainingActivity.EXTRA_SESSION_ITEMS_JSON, itemsJson)
            putExtra(TrainingActivity.EXTRA_TRAINING_MODE, TrainingActivity.MODE_CAMERA)
        }

        trainingLauncher.launch(intent)
    }

    /**
     * Converts [WorkoutConfig.exercises] into a flat list of [ProgramSessionItem]s,
     * interleaving "rest" items between exercises (not after the last one).
     */
    private fun buildSessionItems(): List<ProgramSessionItem> {
        val items = mutableListOf<ProgramSessionItem>()
        var sortIndex = 0

        workoutConfig.exercises.forEachIndexed { index, exercise ->
            items.add(
                ProgramSessionItem(
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
                    ProgramSessionItem(
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

    private fun handleSessionResult(resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK && data != null) {
            sessionReportJson = data.getStringExtra(TrainingActivity.RESULT_SESSION_REPORT_JSON)
            sessionReportIds = data.getStringArrayListExtra(TrainingActivity.RESULT_SESSION_REPORT_IDS) ?: emptyList()
            sessionSessionIds = data.getStringArrayListExtra(TrainingActivity.RESULT_SESSION_SESSION_IDS) ?: emptyList()

            Log.d(TAG, "Session complete: ${sessionReportIds.size} report IDs, " +
                    "${sessionSessionIds.size} session IDs, groupId=$groupId")
        } else {
            Log.d(TAG, "Session cancelled or failed")
        }

        onSessionComplete()
    }

    private fun onSessionComplete() {
        uploadExploreSession()
        navigateToReport()
        finish()
    }

    private fun navigateToReport() {
        if (sessionReportIds.isEmpty() && sessionReportJson.isNullOrBlank()) {
            Log.w(TAG, "No report data available — skipping report screen")
            return
        }

        val reportIntent = SessionReportActivity.createSessionIntent(
            context = this,
            reportIds = sessionReportIds,
            sessionReportJson = sessionReportJson
        )
        startActivity(reportIntent)
    }

    // ── Backend Upload ─────────────────────────────────────────────────────────

    /**
     * Uploads the explore session using [PoseApp.applicationScope] so the coroutine
     * survives this Activity's finish().
     *
     * Individual exercise sessions are already synced by TrainingActivity.
     * This endpoint links them under a shared [groupId].
     */
    private fun uploadExploreSession() {
        if (sessionSessionIds.isEmpty()) return
        val authHeader = AuthManager.getAuthHeader(this) ?: return

        val exerciseSlugs = extractExerciseSlugsFromReport()
        val payload = buildExplorePayload(exerciseSlugs)

        PoseApp.instance.applicationScope.launch {
            try {
                ApiClient.mobileSyncApi.uploadExploreSession(authHeader, payload)
                Log.d(TAG, "Explore session uploaded successfully, groupId=$groupId")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to upload explore session (non-fatal)", e)
            }
        }
    }

    private fun extractExerciseSlugsFromReport(): List<String> {
        val json = sessionReportJson ?: return emptyList()
        return try {
            val type = object : com.google.gson.reflect.TypeToken<SessionTrainingEngine.SessionReport>() {}.type
            val report = gson.fromJson<SessionTrainingEngine.SessionReport>(json, type)
            report.exerciseReports.map { it.exerciseSlug }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse session report for exercise slugs", e)
            workoutConfig.exercises.map { it.exercise }
        }
    }

    /**
     * Builds the explore upload payload linking individual sessions under [groupId].
     */
    private fun buildExplorePayload(exerciseSlugs: List<String>): Map<String, Any?> {
        return mapOf(
            "groupId" to groupId,
            "workoutId" to workoutId,
            "isCustomized" to false,
            "context" to sessionContext,
            "sessions" to sessionSessionIds.mapIndexed { index, id ->
                mapOf(
                    "id" to id,
                    "groupId" to groupId,
                    "workoutId" to workoutId,
                    "context" to sessionContext,
                    "exerciseId" to (exerciseSlugs.getOrNull(index) ?: "")
                )
            }
        )
    }
}
