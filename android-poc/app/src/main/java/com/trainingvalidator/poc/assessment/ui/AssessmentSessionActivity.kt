package com.trainingvalidator.poc.assessment.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.trainingvalidator.poc.assessment.AssessmentUploadService
import com.trainingvalidator.poc.assessment.engine.AdaptiveBatteryManager
import com.trainingvalidator.poc.assessment.engine.AssessmentEngine
import com.trainingvalidator.poc.assessment.engine.AssessmentTemplateManager
import com.trainingvalidator.poc.assessment.models.AssessmentType
import com.trainingvalidator.poc.assessment.models.BodyScanResult
import com.trainingvalidator.poc.assessment.models.PainFlag
import com.trainingvalidator.poc.storage.ReportStorage
import com.trainingvalidator.poc.training.report.PostTrainingReport
import com.trainingvalidator.poc.ui.train.TrainingActivity
import kotlinx.coroutines.launch

/**
 * AssessmentSessionActivity - Orchestrates the Body Scan assessment flow.
 * 
 * Runs exercises sequentially:
 * 1. Shows movement explanation screen
 * 2. Launches TrainingActivity in assessment mode for each exercise
 * 3. Collects PostTrainingReport from ReportStorage after each exercise
 * 4. Checks adaptive battery after core exercises
 * 5. After all exercises, runs AssessmentEngine and shows results
 */
class AssessmentSessionActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AssessmentSession"
        private const val EXTRA_PARQ_PASSED = "parq_passed"
        private const val EXTRA_PARQ_FLAGS = "parq_flags"
        private const val EXTRA_ASSESSMENT_TYPE = "assessment_type"

        fun createIntent(
            context: Context,
            parqPassed: Boolean = true,
            parqFlags: List<String> = emptyList(),
            assessmentType: AssessmentType = AssessmentType.INITIAL
        ): Intent {
            return Intent(context, AssessmentSessionActivity::class.java).apply {
                putExtra(EXTRA_PARQ_PASSED, parqPassed)
                putStringArrayListExtra(EXTRA_PARQ_FLAGS, ArrayList(parqFlags))
                putExtra(EXTRA_ASSESSMENT_TYPE, assessmentType.name)
            }
        }
    }

    private var parqPassed = true
    private var parqFlags = emptyList<String>()
    private var assessmentType = AssessmentType.INITIAL

    // Assessment state
    private val completedReports = mutableListOf<PostTrainingReport>()
    private val painFlags = mutableListOf<PainFlag>()
    private var currentExerciseIndex = 0
    private var startTimeMs = 0L
    private var currentExerciseSlug: String? = null

    // Core exercises (loaded from template or defaults)
    private var coreExercises = AssessmentTemplateManager.DEFAULT_CORE_EXERCISES.toMutableList()

    // Adaptive exercises (added when needed)
    private val adaptiveExercises = mutableListOf<String>()

    // UI
    private lateinit var rootLayout: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var exerciseCountText: TextView

    // Storage
    private lateinit var reportStorage: ReportStorage

    // Activity result launcher for TrainingActivity
    private lateinit var exerciseLauncher: ActivityResultLauncher<Intent>

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#121212")

        parqPassed = intent.getBooleanExtra(EXTRA_PARQ_PASSED, true)
        parqFlags = intent.getStringArrayListExtra(EXTRA_PARQ_FLAGS) ?: emptyList()
        assessmentType = AssessmentType.valueOf(
            intent.getStringExtra(EXTRA_ASSESSMENT_TYPE) ?: "INITIAL"
        )

        reportStorage = ReportStorage(this)
        startTimeMs = System.currentTimeMillis()

        // Try to load assessment template from server
        lifecycleScope.launch {
            loadTemplate()
        }

        // Register the activity result launcher before any UI setup
        exerciseLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            handleExerciseResult(result.resultCode, result.data)
        }

        setupUI()
        showExerciseIntro(currentExerciseIndex)
    }

    private fun setupUI() {
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(dp(24), dp(48), dp(24), dp(32))
            gravity = Gravity.CENTER
        }

        exerciseCountText = TextView(this).apply {
            setTextColor(Color.parseColor("#B0B0B0"))
            textSize = 14f
            gravity = Gravity.CENTER
        }
        rootLayout.addView(exerciseCountText)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(4)
            )
            lp.topMargin = dp(8)
            lp.bottomMargin = dp(32)
            layoutParams = lp
        }
        rootLayout.addView(progressBar)

        statusText = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        rootLayout.addView(statusText)

        setContentView(rootLayout)
    }

    private fun showExerciseIntro(index: Int) {
        val allExercises = getAllExercises()
        if (index >= allExercises.size) {
            processResults()
            return
        }

        val exerciseSlug = allExercises[index]
        val totalExercises = allExercises.size

        exerciseCountText.text = "Movement ${index + 1} of $totalExercises"
        progressBar.progress = ((index.toFloat() / totalExercises) * 100).toInt()

        val exerciseName = getExerciseDisplayName(exerciseSlug)
        statusText.text = exerciseName

        // Remove old intro views (keep progress, bar, status = 3 base views)
        while (rootLayout.childCount > 3) {
            rootLayout.removeViewAt(rootLayout.childCount - 1)
        }

        // Exercise description
        rootLayout.addView(TextView(this).apply {
            text = getExerciseDescription(exerciseSlug)
            setTextColor(Color.parseColor("#B0B0B0"))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(32))
        })

        // Ready button
        rootLayout.addView(Button(this).apply {
            text = "Ready"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#4CAF50"))
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(16)
            layoutParams = lp

            setOnClickListener { launchExercise(exerciseSlug) }
        })

        // Pain / Skip button
        rootLayout.addView(TextView(this).apply {
            text = "I feel pain in this movement \u2014 Skip"
            setTextColor(Color.parseColor("#FF5252"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
            setOnClickListener {
                painFlags.add(PainFlag(
                    movement = exerciseSlug,
                    region = inferRegionFromExercise(exerciseSlug)
                ))
                currentExerciseIndex++
                showExerciseIntro(currentExerciseIndex)
            }
        })
    }

    private fun launchExercise(exerciseSlug: String) {
        currentExerciseSlug = exerciseSlug
        Log.d(TAG, "Launching TrainingActivity for: $exerciseSlug")

        val intent = Intent(this, TrainingActivity::class.java).apply {
            putExtra(TrainingActivity.EXTRA_EXERCISE_NAME, exerciseSlug)
            putExtra(TrainingActivity.EXTRA_TRAINING_MODE, TrainingActivity.MODE_CAMERA)
            putExtra(TrainingActivity.EXTRA_ASSESSMENT_MODE, true)
        }

        exerciseLauncher.launch(intent)
    }

    private fun handleExerciseResult(resultCode: Int, data: Intent?) {
        val slug = currentExerciseSlug ?: return

        if (resultCode == Activity.RESULT_OK && data != null) {
            val reportId = data.getStringExtra(TrainingActivity.RESULT_REPORT_ID)
            Log.d(TAG, "Exercise $slug completed. Report ID: $reportId")

            if (reportId != null) {
                val report = reportStorage.getById(reportId)
                if (report != null) {
                    completedReports.add(report)
                    Log.d(TAG, "Report loaded for $slug: accuracy=${report.summary.accuracy}%")

                    // Check adaptive battery after core exercises
                    if (currentExerciseIndex < coreExercises.size) {
                        val adaptiveDecisions = AdaptiveBatteryManager.checkForAdaptive(completedReports)
                        for (decision in adaptiveDecisions) {
                            if (decision.exercise.slug !in adaptiveExercises &&
                                decision.exercise.slug !in coreExercises) {
                                adaptiveExercises.add(decision.exercise.slug)
                                Log.d(TAG, "Adaptive exercise added: ${decision.exercise.slug}")
                            }
                        }

                        // Also check template-based adaptive exercises
                        val templateAdaptive = AssessmentTemplateManager.getAdaptiveExercises()
                        for (exercise in templateAdaptive) {
                            if (exercise.exerciseSlug !in adaptiveExercises &&
                                exercise.exerciseSlug !in coreExercises) {
                                // Check activation condition from template
                                if (shouldActivateFromTemplate(exercise, completedReports)) {
                                    adaptiveExercises.add(exercise.exerciseSlug)
                                    Log.d(TAG, "Template adaptive exercise added: ${exercise.exerciseSlug}")
                                }
                            }
                        }
                    }
                } else {
                    Log.w(TAG, "Report not found in storage for ID: $reportId")
                }
            }
        } else {
            Log.d(TAG, "Exercise $slug was cancelled or returned no data")
        }

        currentExerciseIndex++
        showExerciseIntro(currentExerciseIndex)
    }

    private fun processResults() {
        statusText.text = "Analyzing your movement..."
        exerciseCountText.text = ""
        progressBar.progress = 100

        // Remove extra views
        while (rootLayout.childCount > 3) {
            rootLayout.removeViewAt(rootLayout.childCount - 1)
        }

        if (completedReports.isEmpty()) {
            statusText.text = "No exercises were completed"

            rootLayout.addView(TextView(this).apply {
                text = if (painFlags.isNotEmpty()) {
                    "You reported pain in ${painFlags.size} movement(s).\nPlease consult a specialist before taking the assessment."
                } else {
                    "Please try the assessment again."
                }
                setTextColor(Color.parseColor("#B0B0B0"))
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(0, dp(16), 0, dp(32))
            })

            rootLayout.addView(Button(this).apply {
                text = "Go Back"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#333333"))
                setOnClickListener { finish() }
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.topMargin = dp(16)
                layoutParams = lp
            })
            return
        }

        // Phase 0: Fetch previous assessment for comparison, then process & upload
        lifecycleScope.launch {
            val previousAssessment = fetchPreviousAssessment()

            val engine = AssessmentEngine()
            val result = engine.process(
                reports = completedReports,
                userId = getUserId(),
                assessmentType = assessmentType,
                parqPassed = parqPassed,
                parqFlags = parqFlags,
                painFlags = painFlags,
                previousResult = previousAssessment,
                durationMs = System.currentTimeMillis() - startTimeMs
            )

            // Upload to backend (fire-and-forget — don't block the user)
            uploadAssessment(result)

            // Navigate to results screen
            val intent = AssessmentResultActivity.createIntent(
                this@AssessmentSessionActivity,
                result
            )
            startActivity(intent)
            finish()
        }
    }

    /**
     * Fetch the user's latest assessment as a BodyScanResult.
     * Used to pass as previousResult to AssessmentEngine for comparison and linking.
     */
    private suspend fun fetchPreviousAssessment(): BodyScanResult? {
        return try {
            AssessmentUploadService.fetchLatestAsBodyScanResult(this)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch previous assessment", e)
            null
        }
    }

    /**
     * Upload the assessment result to the backend.
     * Runs in background — does not block the UI flow.
     */
    private fun uploadAssessment(result: BodyScanResult) {
        lifecycleScope.launch {
            val serverId = AssessmentUploadService.upload(this@AssessmentSessionActivity, result)
            if (serverId != null) {
                Log.d(TAG, "Assessment uploaded to server: $serverId")
            } else {
                Log.w(TAG, "Assessment upload failed — will retry on next sync")
            }
        }
    }

    private fun getUserId(): String {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return prefs.getString("user_id", "unknown") ?: "unknown"
    }

    private suspend fun loadTemplate() {
        try {
            val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val token = prefs.getString("auth_token", null) ?: return
            
            val template = AssessmentTemplateManager.resolve(this, token)
            if (template != null) {
                val coreSlugs = template.exercises
                    .filter { it.entryType == "core" }
                    .sortedBy { it.sortOrder }
                    .map { it.exerciseSlug }
                
                if (coreSlugs.isNotEmpty()) {
                    coreExercises = coreSlugs.toMutableList()
                    Log.d(TAG, "Loaded ${coreSlugs.size} core exercises from template")
                    
                    // Restart exercise display if still on first exercise
                    if (currentExerciseIndex == 0 && completedReports.isEmpty()) {
                        runOnUiThread { showExerciseIntro(0) }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load template, using defaults", e)
        }
    }

    private fun shouldActivateFromTemplate(
        exercise: com.trainingvalidator.poc.network.TemplateExerciseData,
        reports: List<PostTrainingReport>
    ): Boolean {
        val condition = exercise.activationCondition ?: return true
        try {
            val metric = condition["metric"]?.toString() ?: return true
            val operator = condition["operator"]?.toString() ?: return true
            val targetValue = (condition["value"] as? Number)?.toFloat() ?: return true

            for (report in reports) {
                val actualValue = when (metric) {
                    "avgROM" -> report.summary.avgROM
                    "avgStability" -> report.summary.avgStability
                    "avgSymmetry" -> report.summary.avgSymmetry
                    "accuracy" -> report.summary.accuracy
                    else -> null
                } ?: continue

                val matches = when (operator) {
                    "<" -> actualValue < targetValue
                    "<=" -> actualValue <= targetValue
                    ">" -> actualValue > targetValue
                    ">=" -> actualValue >= targetValue
                    else -> false
                }
                if (matches) return true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to evaluate activation condition", e)
        }
        return false
    }

    private fun getAllExercises(): List<String> = coreExercises + adaptiveExercises

    private fun getExerciseDisplayName(slug: String): String {
        // Try to get from template exercise info
        val info = AssessmentTemplateManager.getExerciseInfo(slug)
        if (info != null) {
            // Use slug as display name since template exercises might be dynamic
            return slug.replace("assessment_", "")
                .replace("_", " ")
                .split(" ")
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        }
        return when (slug) {
            "assessment_overhead_squat" -> "Overhead Squat"
            "assessment_lunge" -> "Lunge"
            "assessment_shoulder_mobility" -> "Shoulder Mobility"
            "assessment_forward_fold" -> "Forward Fold"
            "assessment_single_leg_balance" -> "Single-Leg Balance"
            else -> slug.replace("assessment_", "").replace("_", " ").replaceFirstChar { it.uppercase() }
        }
    }

    private fun getExerciseDescription(slug: String): String = when (slug) {
        "assessment_overhead_squat" -> "Stand with arms overhead, slowly lower into a squat.\n5 reps at comfortable pace."
        "assessment_lunge" -> "Step forward and bend your knee, alternate legs.\n3 reps per leg."
        "assessment_shoulder_mobility" -> "Slowly raise each arm overhead.\n5 reps per arm."
        "assessment_forward_fold" -> "Bend forward and hold for 10 seconds."
        "assessment_single_leg_balance" -> "Stand on one leg for 15 seconds, then switch."
        else -> ""
    }

    private fun inferRegionFromExercise(slug: String): String = when (slug) {
        "assessment_overhead_squat" -> "hip"
        "assessment_lunge" -> "knee"
        "assessment_shoulder_mobility" -> "shoulder"
        "assessment_forward_fold" -> "lower_back"
        "assessment_single_leg_balance" -> "balance"
        else -> "core"
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
}
