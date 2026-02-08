package com.trainingvalidator.poc.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.ActivityProgramSessionBinding
import com.trainingvalidator.poc.storage.ExerciseRepository
import com.trainingvalidator.poc.storage.ProgramRepository
import com.trainingvalidator.poc.storage.ProgramSessionReportStore
import com.trainingvalidator.poc.storage.AuthManager
import com.trainingvalidator.poc.network.ApiClient
import com.trainingvalidator.poc.training.models.ProgramSessionItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ProgramSessionActivity - Displays session overview and launches TrainingActivity
 * in session mode (single Activity for the entire session).
 *
 * Flow:
 * 1. Load program & session items from repository
 * 2. Show item list (exercises + rests)
 * 3. User taps "Start Session" → launches TrainingActivity in session mode
 *    with ALL session items serialized as JSON
 * 4. TrainingActivity manages the entire session (pre-exercise, training,
 *    rest countdowns, weight dialogs) within a single Activity
 * 5. On session complete, result is returned here → navigate to report
 */
class ProgramSessionActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PROGRAM_SLUG = "program_slug"
        const val EXTRA_SESSION_ID = "session_id"
        private const val TAG = "ProgramSessionActivity"
    }

    private lateinit var binding: ActivityProgramSessionBinding

    private val sessionItems = mutableListOf<ProgramSessionItem>()
    private val exerciseNameMap = mutableMapOf<String, String>()
    private var availableExercises: List<com.trainingvalidator.poc.training.models.ExerciseConfig> = emptyList()
    private var totalSetsPlanned = 0
    private var isEditMode = false
    private var currentWeekNumber: Int = 1
    private var currentDayNumber: Int = 1
    private var currentProgramId: String? = null
    private val reportStore by lazy { ProgramSessionReportStore(this) }
    private var itemTouchHelper: androidx.recyclerview.widget.ItemTouchHelper? = null

    private val sessionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleSessionResult(result.resultCode, result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityProgramSessionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        loadSession()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnStartSession.setOnClickListener { startSession() }
        binding.btnEditSession.setOnClickListener { toggleEditMode() }
        // Hide rest/complete panels – they are no longer used here
        binding.panelPreparing.visibility = View.GONE
        binding.panelRest.visibility = View.GONE
        binding.panelComplete.visibility = View.GONE

        binding.rvSessionItems.layoutManager = LinearLayoutManager(this)
        binding.rvSessionItems.adapter = ProgramSessionItemAdapter(sessionItems)
        setupDragAndDrop()
    }

    private fun loadSession() {
        val slug = intent.getStringExtra(EXTRA_PROGRAM_SLUG)
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
        if (slug.isNullOrBlank() || sessionId.isNullOrBlank()) {
            Toast.makeText(this, "Session not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            val programRepo = ProgramRepository.getInstance(this@ProgramSessionActivity)
            val exerciseRepo = ExerciseRepository.getInstance(this@ProgramSessionActivity)

            withContext(Dispatchers.IO) {
                programRepo.initialize()
                exerciseRepo.initialize(autoSync = false)
            }
            availableExercises = exerciseRepo.getAllExercises()

            val program = programRepo.getProgram(slug)
            val session = program?.weeks
                ?.flatMap { it.days }
                ?.flatMap { it.sessions }
                ?.firstOrNull { it.id == sessionId }

            if (session == null) {
                Toast.makeText(this@ProgramSessionActivity, "Session not found", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            binding.tvSessionTitle.text = session.name.en
            currentProgramId = program.id
            currentWeekNumber = program.weeks.firstOrNull { week -> week.days.any { day -> day.sessions.any { it.id == sessionId } } }
                ?.weekNumber ?: 1
            currentDayNumber = program.weeks
                .flatMap { it.days }
                .firstOrNull { day -> day.sessions.any { it.id == sessionId } }
                ?.dayNumber ?: 1

            sessionItems.clear()
            sessionItems.addAll(session.items.sortedBy { it.sortOrder })
            binding.rvSessionItems.adapter?.notifyDataSetChanged()

            // Resolve exercise names for the list display
            exerciseNameMap.clear()
            val language = getCurrentLanguage()
            totalSetsPlanned = 0

            sessionItems.forEach { item ->
                if (item.type == "exercise") {
                    val slugExercise = item.exerciseSlug ?: return@forEach
                    val exerciseConfig = exerciseRepo.getExercise(slugExercise)
                    if (exerciseConfig != null) {
                        exerciseNameMap[slugExercise] =
                            exerciseConfig.name.get(language).ifBlank { exerciseConfig.name.en }
                    }
                    totalSetsPlanned += item.sets?.coerceAtLeast(1) ?: 1
                }
            }

            // Refresh list with resolved names
            binding.rvSessionItems.adapter?.notifyDataSetChanged()
        }
    }

    private fun toggleEditMode() {
        isEditMode = !isEditMode
        binding.btnEditSession.alpha = if (isEditMode) 0.6f else 1f
        binding.rvSessionItems.adapter?.notifyDataSetChanged()
    }

    private fun recalculateTotalSets() {
        totalSetsPlanned = sessionItems.sumOf { item ->
            if (item.type == "exercise") item.sets?.coerceAtLeast(1) ?: 1 else 0
        }
    }

    private fun setupDragAndDrop() {
        val callback = object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
            androidx.recyclerview.widget.ItemTouchHelper.UP or androidx.recyclerview.widget.ItemTouchHelper.DOWN,
            0
        ) {
            override fun getMovementFlags(
                recyclerView: androidx.recyclerview.widget.RecyclerView,
                viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder
            ): Int {
                if (!isEditMode) return 0
                return makeMovementFlags(
                    androidx.recyclerview.widget.ItemTouchHelper.UP or androidx.recyclerview.widget.ItemTouchHelper.DOWN,
                    0
                )
            }

            override fun onMove(
                recyclerView: androidx.recyclerview.widget.RecyclerView,
                viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder,
                target: androidx.recyclerview.widget.RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == androidx.recyclerview.widget.RecyclerView.NO_POSITION ||
                    to == androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                    return false
                }
                java.util.Collections.swap(sessionItems, from, to)
                recyclerView.adapter?.notifyItemMoved(from, to)
                return true
            }

            override fun onSwiped(
                viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder,
                direction: Int
            ) = Unit
        }

        itemTouchHelper = androidx.recyclerview.widget.ItemTouchHelper(callback).also {
            it.attachToRecyclerView(binding.rvSessionItems)
        }
    }

    /**
     * Launch TrainingActivity in session mode with ALL items serialized as JSON.
     * TrainingActivity manages the entire session internally.
     */
    private fun startSession() {
        if (sessionItems.isEmpty()) {
            Toast.makeText(this, "No exercises in this session", Toast.LENGTH_SHORT).show()
            return
        }

        sendSessionStart()

        val gson = com.google.gson.Gson()
        val itemsJson = gson.toJson(sessionItems)

        val intent = Intent(this, TrainingActivity::class.java).apply {
            putExtra(TrainingActivity.EXTRA_IS_SESSION_MODE, true)
            putExtra(TrainingActivity.EXTRA_SESSION_ITEMS_JSON, itemsJson)
            putExtra(TrainingActivity.EXTRA_TRAINING_MODE, TrainingActivity.MODE_CAMERA)
        }

        sessionLauncher.launch(intent)
    }

    /**
     * Handle result from session-mode TrainingActivity.
     * Navigate to ProgramSessionReportActivity with aggregate metrics.
     */
    private fun handleSessionResult(resultCode: Int, data: Intent?) {
        Log.d(TAG, "Session result code: $resultCode")

        if (resultCode == RESULT_OK && data != null) {
            val completedSets = data.getIntExtra(TrainingActivity.RESULT_SESSION_SETS_COMPLETED, 0)
            val totalSets = data.getIntExtra(TrainingActivity.RESULT_SESSION_SETS_PLANNED, totalSetsPlanned)
            val durationMs = data.getLongExtra(TrainingActivity.RESULT_DURATION_MS, 0L)
            val totalReps = data.getIntExtra(TrainingActivity.RESULT_SESSION_TOTAL_REPS, 0)
            val avgAccuracy = data.getFloatExtra(TrainingActivity.RESULT_SESSION_AVG_ACCURACY, 0f)
            val reportJson = data.getStringExtra(TrainingActivity.RESULT_SESSION_REPORT_JSON)
            val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)

            val reportIntent = Intent(this, ProgramSessionReportActivity::class.java).apply {
                putExtra(ProgramSessionReportActivity.EXTRA_TOTAL_ITEMS, sessionItems.size)
                putExtra(ProgramSessionReportActivity.EXTRA_TOTAL_SETS, totalSets)
                putExtra(ProgramSessionReportActivity.EXTRA_COMPLETED_SETS, completedSets)
                putExtra(ProgramSessionReportActivity.EXTRA_DURATION_MS, durationMs)
                putExtra(ProgramSessionReportActivity.EXTRA_AVG_ACCURACY, avgAccuracy)
                if (!reportJson.isNullOrBlank()) {
                    putExtra(ProgramSessionReportActivity.EXTRA_SESSION_REPORT_JSON, reportJson)
                }
            }
            startActivity(reportIntent)
            finish()
            sendSessionComplete(durationMs, sessionItems.size, totalSets, completedSets, totalReps, avgAccuracy, reportJson)
            saveLocalSessionReport(
                sessionId = sessionId,
                durationMs = durationMs,
                totalSets = totalSets,
                completedSets = completedSets,
                totalReps = totalReps,
                avgAccuracy = avgAccuracy,
                reportJson = reportJson
            )
        }
        // If cancelled (user pressed back during session), stay on this screen
    }

    private fun getCurrentLanguage(): String {
        return java.util.Locale.getDefault().language
    }

    // ==================== Adapter ====================

    private inner class ProgramSessionItemAdapter(
        private val items: List<ProgramSessionItem>
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<ProgramSessionItemAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
            val tvItemType: android.widget.TextView = view.findViewById(R.id.tvItemType)
            val tvItemTitle: android.widget.TextView = view.findViewById(R.id.tvItemTitle)
            val tvItemSubtitle: android.widget.TextView = view.findViewById(R.id.tvItemSubtitle)
            val btnEditItem: com.google.android.material.button.MaterialButton = view.findViewById(R.id.btnEditItem)
            val layoutSetControls: android.view.View = view.findViewById(R.id.layoutSetControls)
            val btnSetPlus: com.google.android.material.button.MaterialButton = view.findViewById(R.id.btnSetPlus)
            val btnSetMinus: com.google.android.material.button.MaterialButton = view.findViewById(R.id.btnSetMinus)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_program_session_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val isRest = item.type == "rest"

            holder.tvItemType.text = if (isRest) "REST" else "EXERCISE"
            holder.tvItemTitle.text = if (isRest) {
                getString(R.string.rest_time)
            } else {
                val slug = item.exerciseSlug
                if (slug != null) {
                    exerciseNameMap[slug] ?: slug
                } else {
                    "Exercise"
                }
            }

            holder.tvItemSubtitle.text = when {
                isRest -> {
                    val sec = (item.restDurationMs ?: 0L) / 1000L
                    "${sec}s"
                }
                item.targetDuration != null -> {
                    "${item.sets ?: 1} sets • ${item.targetDuration}s"
                }
                item.targetReps != null -> {
                    "${item.sets ?: 1} sets • ${item.targetReps} reps"
                }
                else -> {
                    "${item.sets ?: 1} sets"
                }
            }

            holder.btnEditItem.visibility = if (!isRest && isEditMode) View.VISIBLE else View.GONE
            holder.btnEditItem.setOnClickListener {
                openEditItemDialog(position)
            }

            holder.layoutSetControls.visibility = if (!isRest && isEditMode) View.VISIBLE else View.GONE
            holder.btnSetPlus.setOnClickListener {
                val current = item.sets?.coerceAtLeast(1) ?: 1
                sessionItems[position] = item.copy(sets = current + 1)
                recalculateTotalSets()
                notifyItemChanged(position)
            }
            holder.btnSetMinus.setOnClickListener {
                val current = item.sets?.coerceAtLeast(1) ?: 1
                val next = (current - 1).coerceAtLeast(1)
                sessionItems[position] = item.copy(sets = next)
                recalculateTotalSets()
                notifyItemChanged(position)
            }
        }

        override fun getItemCount(): Int = items.size
    }

    private fun openEditItemDialog(position: Int) {
        val item = sessionItems.getOrNull(position) ?: return
        if (item.type != "exercise") return

        val dialogView = layoutInflater.inflate(R.layout.dialog_session_item_edit, null)
        val spinner = dialogView.findViewById<android.widget.Spinner>(R.id.spinnerExercise)
        val inputSets = dialogView.findViewById<android.widget.EditText>(R.id.inputSets)
        val inputReps = dialogView.findViewById<android.widget.EditText>(R.id.inputTargetReps)
        val inputDuration = dialogView.findViewById<android.widget.EditText>(R.id.inputTargetDuration)
        val inputRestBetweenSets = dialogView.findViewById<android.widget.EditText>(R.id.inputRestBetweenSets)
        val inputWeight = dialogView.findViewById<android.widget.EditText>(R.id.inputWeightKg)

        val language = getCurrentLanguage()
        val options = availableExercises.map { ex ->
            val name = ex.name.get(language).ifBlank { ex.name.en }
            name to ex.fileName
        }
        val labels = options.map { it.first }
        val adapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            labels
        )
        spinner.adapter = adapter
        val currentIndex = options.indexOfFirst { it.second == item.exerciseSlug }
        if (currentIndex >= 0) spinner.setSelection(currentIndex)

        inputSets.setText((item.sets ?: 1).toString())
        inputReps.setText(item.targetReps?.toString() ?: "")
        inputDuration.setText(item.targetDuration?.toString() ?: "")
        inputRestBetweenSets.setText(item.restBetweenSetsMs?.toString() ?: "")
        inputWeight.setText(item.weightKg?.toString() ?: "")

        val bottomSheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        bottomSheet.setContentView(dialogView)
        bottomSheet.setCancelable(true)

        val saveButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSave)
        val cancelButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)

        saveButton.setOnClickListener {
            val selectedSlug = options.getOrNull(spinner.selectedItemPosition)?.second
            val updated = item.copy(
                exerciseSlug = selectedSlug ?: item.exerciseSlug,
                sets = inputSets.text.toString().toIntOrNull() ?: item.sets,
                targetReps = inputReps.text.toString().toIntOrNull(),
                targetDuration = inputDuration.text.toString().toIntOrNull(),
                restBetweenSetsMs = inputRestBetweenSets.text.toString().toLongOrNull(),
                weightKg = inputWeight.text.toString().toFloatOrNull()
            )
            sessionItems[position] = updated
            if (selectedSlug != null) {
                val config = availableExercises.firstOrNull { it.fileName == selectedSlug }
                if (config != null) {
                    exerciseNameMap[selectedSlug] =
                        config.name.get(language).ifBlank { config.name.en }
                }
            }
            recalculateTotalSets()
            binding.rvSessionItems.adapter?.notifyItemChanged(position)
            bottomSheet.dismiss()
        }

        cancelButton.setOnClickListener { bottomSheet.dismiss() }
        bottomSheet.show()
    }

    private fun saveLocalSessionReport(
        sessionId: String?,
        durationMs: Long,
        totalSets: Int,
        completedSets: Int,
        totalReps: Int,
        avgAccuracy: Float,
        reportJson: String?
    ) {
        val programId = currentProgramId ?: return
        val sessionKey = sessionId ?: return
        val report = if (!reportJson.isNullOrBlank()) {
            val gson = com.google.gson.Gson()
            val type = object :
                com.google.gson.reflect.TypeToken<com.trainingvalidator.poc.training.session.SessionTrainingEngine.SessionReport>() {}.type
            runCatching {
                gson.fromJson<com.trainingvalidator.poc.training.session.SessionTrainingEngine.SessionReport>(
                    reportJson,
                    type
                )
            }.getOrNull()
        } else {
            null
        }

        reportStore.save(
            ProgramSessionReportStore.ProgramSessionLocalReport(
                sessionId = sessionKey,
                programId = programId,
                weekNumber = currentWeekNumber,
                dayNumber = currentDayNumber,
                completedAt = System.currentTimeMillis(),
                totalSetsPlanned = totalSets,
                totalSetsCompleted = completedSets,
                totalReps = totalReps,
                averageAccuracy = avgAccuracy,
                totalDurationMs = durationMs,
                report = report
            )
        )
    }

    private fun sendSessionStart() {
        val token = AuthManager.getAccessToken(this) ?: return
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return
        val payload = mapOf<String, Any>(
            "programId" to (currentProgramId ?: ""),
            "weekNumber" to currentWeekNumber,
            "dayNumber" to currentDayNumber,
            "startedAt" to System.currentTimeMillis()
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                ApiClient.mobileSyncApi.startSession(sessionId, "Bearer $token", payload)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to post session start: ${e.message}")
            }
        }
    }

    private fun sendSessionComplete(
        durationMs: Long,
        totalExercises: Int,
        totalSets: Int,
        completedSets: Int,
        totalReps: Int,
        avgAccuracy: Float,
        reportJson: String?
    ) {
        val token = AuthManager.getAccessToken(this) ?: return
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return

        val payloadMap = mutableMapOf<String, Any>(
            "completedAt" to System.currentTimeMillis(),
            "totalDurationMs" to durationMs,
            "totalExercises" to totalExercises,
            "totalSets" to totalSets,
            "completedSets" to completedSets,
            "totalReps" to totalReps,
            "avgAccuracy" to avgAccuracy
        )

        if (!reportJson.isNullOrBlank()) {
            val gson = com.google.gson.Gson()
            val reportElement = gson.fromJson(reportJson, com.google.gson.JsonElement::class.java)
            payloadMap["report"] = reportElement
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                ApiClient.mobileSyncApi.completeSession(sessionId, "Bearer $token", payloadMap)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to post session complete: ${e.message}")
            }
            try {
                ApiClient.mobileSyncApi.reportSession(sessionId, "Bearer $token", payloadMap)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to post session report: ${e.message}")
            }
        }
    }
}
