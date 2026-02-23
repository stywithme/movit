package com.trainingvalidator.poc.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.ActivityProgramSessionBinding
import com.trainingvalidator.poc.network.ApiClient
import com.trainingvalidator.poc.storage.AuthManager
import com.trainingvalidator.poc.storage.DayCustomizationStore
import com.trainingvalidator.poc.storage.ExerciseRepository
import com.trainingvalidator.poc.storage.ProgramRepository
import com.trainingvalidator.poc.storage.ProgramSessionReportStore
import com.trainingvalidator.poc.training.models.ExerciseConfig
import com.trainingvalidator.poc.training.models.LocalizedText
import com.trainingvalidator.poc.training.models.ProgramConfig
import com.trainingvalidator.poc.training.models.ProgramDay
import com.trainingvalidator.poc.training.models.ProgramSessionItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ProgramSessionActivity — Full Day View
 *
 * Shows ALL sessions for the current day with:
 * - Completed sessions collapsed with ✓ status
 * - Current session expanded with full timeline
 * - Upcoming sessions collapsed, expandable for browsing
 * - Full customization: reorder/delete sessions, edit items
 * - Offline-first persistence via DayCustomizationStore
 */
class ProgramSessionActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PROGRAM_SLUG = "program_slug"
        const val EXTRA_PROGRAM_ID = "program_id"
        const val EXTRA_WEEK_NUMBER = "week_number"
        const val EXTRA_DAY_NUMBER = "day_number"
        const val EXTRA_TARGET_SESSION_ID = "target_session_id"
        private const val TAG = "ProgramSessionActivity"
    }

    // ═══════════════════════════════════════════════════════════
    // State
    // ═══════════════════════════════════════════════════════════

    private lateinit var binding: ActivityProgramSessionBinding

    private var program: ProgramConfig? = null
    private var day: ProgramDay? = null
    private var weekNumber: Int = 1
    private var dayNumber: Int = 1
    private var programId: String? = null
    private var programSlug: String? = null
    private var targetSessionId: String? = null

    /** Current list of customized sessions (the source of truth for display) */
    private var sessions = mutableListOf<DayCustomizationStore.CustomizedSession>()

    /** Track which session cards are expanded */
    private val expandedSessionIds = mutableSetOf<String>()

    /** Edit mode flag */
    private var isEditMode = false

    /** Which session is being edited (for add exercise/rest) */
    private var editingSessionId: String? = null

    // Stores
    private val customizationStore by lazy { DayCustomizationStore(this) }
    private val reportStore by lazy { ProgramSessionReportStore(this) }

    // Exercise data
    private var exerciseConfigMap = mutableMapOf<String, ExerciseConfig>()
    private var exerciseNameMap = mutableMapOf<String, String>()
    private var allExercises: List<ExerciseConfig> = emptyList()

    // Session launcher
    private val sessionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleSessionResult(result.resultCode, result.data)
    }

    /** The session ID that was just launched for training */
    private var launchedSessionId: String? = null

    // ═══════════════════════════════════════════════════════════
    // Lifecycle
    // ═══════════════════════════════════════════════════════════

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProgramSessionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        loadDay()
    }

    // ═══════════════════════════════════════════════════════════
    // Setup
    // ═══════════════════════════════════════════════════════════

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnEditMode.setOnClickListener { toggleEditMode() }
        binding.btnStartSession.setOnClickListener { startNextSession() }
        binding.btnAddExercise.setOnClickListener { showAddExerciseSheet() }
        binding.btnAddRest.setOnClickListener { showAddRestSheet() }
    }

    private fun toggleEditMode() {
        isEditMode = !isEditMode
        binding.btnEditMode.text = if (isEditMode) getString(R.string.ds_done) else getString(R.string.ds_edit_mode)
        binding.layoutEditActions.visibility = if (isEditMode) View.VISIBLE else View.GONE
        renderAllSessions()
    }

    // ═══════════════════════════════════════════════════════════
    // Data Loading
    // ═══════════════════════════════════════════════════════════

    private fun loadDay() {
        programSlug = intent.getStringExtra(EXTRA_PROGRAM_SLUG)
        programId = intent.getStringExtra(EXTRA_PROGRAM_ID)
        weekNumber = intent.getIntExtra(EXTRA_WEEK_NUMBER, 1)
        dayNumber = intent.getIntExtra(EXTRA_DAY_NUMBER, 1)
        targetSessionId = intent.getStringExtra(EXTRA_TARGET_SESSION_ID)

        if (programSlug.isNullOrBlank() && programId.isNullOrBlank()) {
            Toast.makeText(this, "Program not found", Toast.LENGTH_SHORT).show()
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

            // Load exercises
            allExercises = exerciseRepo.getAllExercises()
            exerciseConfigMap.clear()
            exerciseNameMap.clear()
            val language = getCurrentLanguage()
            allExercises.forEach { ex ->
                exerciseConfigMap[ex.fileName] = ex
                exerciseNameMap[ex.fileName] = ex.name.get(language).ifBlank { ex.name.en }
            }

            // Load program
            val resolvedProgram = if (!programSlug.isNullOrBlank()) {
                programRepo.getProgram(programSlug!!)
            } else {
                programRepo.getProgramById(programId!!)
            }

            if (resolvedProgram == null) {
                Toast.makeText(this@ProgramSessionActivity, "Program not found", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            program = resolvedProgram
            programId = resolvedProgram.id
            programSlug = resolvedProgram.slug

            // Find the day
            val resolvedDay = resolvedProgram.weeks
                .firstOrNull { it.weekNumber == weekNumber }
                ?.days
                ?.firstOrNull { it.dayNumber == dayNumber }

            if (resolvedDay == null) {
                Toast.makeText(this@ProgramSessionActivity, "Day not found", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            day = resolvedDay

            // Load effective sessions (customized or original)
            val effectiveSessions = customizationStore.getEffectiveSessions(
                programId = resolvedProgram.id,
                weekNumber = weekNumber,
                dayNumber = dayNumber,
                originalSessions = resolvedDay.sessions
            )

            sessions.clear()
            sessions.addAll(effectiveSessions)

            // Auto-expand the target session or the first non-completed session
            determineExpandedSessions()

            // Render
            renderHeader()
            renderAllSessions()
            updateBottomBar()
        }
    }

    private fun determineExpandedSessions() {
        expandedSessionIds.clear()
        val completedSessionIds = getCompletedSessionIds()

        if (targetSessionId != null && sessions.any { it.id == targetSessionId }) {
            expandedSessionIds.add(targetSessionId!!)
        } else {
            // Expand the first non-completed session
            val firstActive = sessions.firstOrNull { it.id !in completedSessionIds }
            if (firstActive != null) {
                expandedSessionIds.add(firstActive.id)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Rendering
    // ═══════════════════════════════════════════════════════════

    private fun renderHeader() {
        val dayName = day?.name?.let { getLocalizedName(it) } ?: ""
        binding.tvDayTitle.text = if (dayName.isNotBlank()) {
            getString(R.string.ds_day_title_format, dayNumber, dayName)
        } else {
            "Day $dayNumber"
        }
        binding.tvDaySubtitle.text = getString(R.string.ds_day_subtitle_format, weekNumber, sessions.size)

        // Show modified indicator
        if (customizationStore.hasCustomization(programId ?: "", weekNumber, dayNumber)) {
            binding.tvDaySubtitle.text = "${binding.tvDaySubtitle.text}  ${getString(R.string.ds_modified_hint)}"
        }
    }

    private fun renderAllSessions() {
        binding.layoutSessions.removeAllViews()

        if (sessions.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = getString(R.string.ds_no_sessions_message)
                textSize = 16f
                setTextColor(ContextCompat.getColor(context, R.color.text_hint))
                setPadding(0, dpToPx(48), 0, 0)
                gravity = android.view.Gravity.CENTER
            }
            binding.layoutSessions.addView(emptyView)
            return
        }

        val completedSessionIds = getCompletedSessionIds()

        // Find the first non-completed session — that's the "current" one
        val currentSessionId = sessions.firstOrNull {
            it.id !in completedSessionIds && !it.isDeleted
        }?.id

        sessions.forEachIndexed { index, session ->
            val isCompleted = session.id in completedSessionIds
            val isExpanded = session.id in expandedSessionIds
            val isCurrentSession = session.id == currentSessionId

            val view = renderSessionCard(session, index, isCompleted, isCurrentSession, isExpanded)
            binding.layoutSessions.addView(view)
        }
    }

    private fun renderSessionCard(
        session: DayCustomizationStore.CustomizedSession,
        index: Int,
        isCompleted: Boolean,
        isCurrent: Boolean,
        isExpanded: Boolean
    ): View {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.item_day_session_card, binding.layoutSessions, false)

        val card = view.findViewById<MaterialCardView>(R.id.cardSession)
        val statusDot = view.findViewById<View>(R.id.viewStatusDot)
        val tvName = view.findViewById<TextView>(R.id.tvSessionName)
        val tvMeta = view.findViewById<TextView>(R.id.tvSessionMeta)
        val tvStatus = view.findViewById<TextView>(R.id.tvSessionStatus)
        val ivExpand = view.findViewById<ImageView>(R.id.ivExpandIcon)
        val layoutExpanded = view.findViewById<LinearLayout>(R.id.layoutExpandedContent)
        val layoutHeader = view.findViewById<LinearLayout>(R.id.layoutSessionHeader)
        val rvTimeline = view.findViewById<RecyclerView>(R.id.rvTimelineItems)
        val layoutEditBar = view.findViewById<LinearLayout>(R.id.layoutSessionEditBar)
        val btnRename = view.findViewById<MaterialButton>(R.id.btnRenameSession)
        val btnDelete = view.findViewById<MaterialButton>(R.id.btnDeleteSession)
        val btnMoveUp = view.findViewById<ImageButton>(R.id.btnMoveSessionUp)
        val btnMoveDown = view.findViewById<ImageButton>(R.id.btnMoveSessionDown)

        // --- Session Name ---
        tvName.text = getLocalizedName(session.name)

        // --- Meta info ---
        val exerciseCount = session.items.count { it.type == "exercise" }
        val estimatedMinutes = estimateSessionDuration(session.items)
        tvMeta.text = getString(R.string.ds_exercises_meta_format, exerciseCount, estimatedMinutes)

        // --- Status dot & badge ---
        val dotColor: Int
        if (isCompleted) {
            dotColor = ContextCompat.getColor(this, R.color.success)
            tvStatus.text = getString(R.string.ds_session_completed)
            tvStatus.visibility = View.VISIBLE
            tvStatus.backgroundTintList = ContextCompat.getColorStateList(this, R.color.success)
            card.alpha = 0.7f
        } else if (isCurrent) {
            dotColor = ContextCompat.getColor(this, R.color.primary)
            tvStatus.text = getString(R.string.ds_session_up_next)
            tvStatus.visibility = View.VISIBLE
            tvStatus.backgroundTintList = ContextCompat.getColorStateList(this, R.color.primary)
            card.strokeWidth = dpToPx(2)
            card.setStrokeColor(ContextCompat.getColorStateList(this, R.color.primary))
        } else {
            dotColor = ContextCompat.getColor(this, R.color.text_hint)
            tvStatus.visibility = View.GONE
        }
        (statusDot.background as? GradientDrawable)?.setColor(dotColor)

        // --- Expand/Collapse ---
        ivExpand.rotation = if (isExpanded) 180f else 0f
        layoutExpanded.visibility = if (isExpanded) View.VISIBLE else View.GONE

        layoutHeader.setOnClickListener {
            if (isExpanded) {
                expandedSessionIds.remove(session.id)
            } else {
                expandedSessionIds.add(session.id)
            }
            renderAllSessions()
        }

        // --- Timeline items (RecyclerView with drag-and-drop) ---
        if (isExpanded) {
            setupTimelineRecyclerView(rvTimeline, session)
        }

        // --- Edit bar ---
        layoutEditBar.visibility = if (isEditMode && isExpanded) View.VISIBLE else View.GONE

        btnRename.setOnClickListener { showRenameDialog(session) }
        btnDelete.setOnClickListener { showDeleteSessionDialog(session) }
        btnMoveUp.setOnClickListener { moveSession(index, -1) }
        btnMoveDown.setOnClickListener { moveSession(index, 1) }
        btnMoveUp.visibility = if (index > 0) View.VISIBLE else View.INVISIBLE
        btnMoveDown.visibility = if (index < sessions.size - 1) View.VISIBLE else View.INVISIBLE

        return view
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTimelineRecyclerView(
        recyclerView: RecyclerView,
        session: DayCustomizationStore.CustomizedSession
    ) {
        val completedSessionIds = getCompletedSessionIds()
        val isSessionCompleted = session.id in completedSessionIds

        val adapter = TimelineItemAdapter(
            items = session.items.toMutableList(),
            sessionId = session.id,
            isSessionCompleted = isSessionCompleted,
            totalCount = session.items.size
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        recyclerView.isNestedScrollingEnabled = false

        // Drag-and-drop via ItemTouchHelper
        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun isLongPressDragEnabled() = false // drag only from handle

            override fun onMove(
                rv: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                adapter.moveItem(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun clearView(rv: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(rv, viewHolder)
                // Persist after drag ends — sortOrder is normalized inside persistCustomizations()
                val sessionIndex = sessions.indexOfFirst { it.id == session.id }
                if (sessionIndex >= 0) {
                    val reorderedItems = adapter.items.toList()
                    Log.d(TAG, "Drag ended: session=${session.name.en}, items reordered (${reorderedItems.size})")
                    sessions[sessionIndex] = sessions[sessionIndex].copy(items = reorderedItems)
                    persistCustomizations()
                }
            }
        })
        touchHelper.attachToRecyclerView(recyclerView)
        adapter.touchHelper = touchHelper
    }

    // ═══════════════════════════════════════════════════════════
    // Timeline Item Adapter (with drag-and-drop support)
    // ═══════════════════════════════════════════════════════════

    @SuppressLint("ClickableViewAccessibility")
    private inner class TimelineItemAdapter(
        val items: MutableList<ProgramSessionItem>,
        private val sessionId: String,
        private val isSessionCompleted: Boolean,
        private val totalCount: Int
    ) : RecyclerView.Adapter<TimelineItemAdapter.VH>() {

        var touchHelper: ItemTouchHelper? = null

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val viewLineTop: View = view.findViewById(R.id.viewLineTop)
            val viewLineBottom: View = view.findViewById(R.id.viewLineBottom)
            val viewDot: View = view.findViewById(R.id.viewDot)
            val cardItem: MaterialCardView = view.findViewById(R.id.cardItem)
            val ivItemImage: ImageView = view.findViewById(R.id.ivItemImage)
            val ivIcon: ImageView = view.findViewById(R.id.ivItemIcon)
            val tvName: TextView = view.findViewById(R.id.tvItemName)
            val tvDetail: TextView = view.findViewById(R.id.tvItemDetail)
            val layoutAlwaysActions: LinearLayout = view.findViewById(R.id.layoutAlwaysActions)
            val btnSwapExercise: ImageButton = view.findViewById(R.id.btnSwapExercise)
            val layoutEditControls: LinearLayout = view.findViewById(R.id.layoutItemEditControls)
            val btnEditItem: ImageButton = view.findViewById(R.id.btnEditItem)
            val btnDeleteItem: ImageButton = view.findViewById(R.id.btnDeleteItem)
            val btnDragHandle: ImageButton = view.findViewById(R.id.btnDragHandle)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_session_timeline_item, parent, false)
            return VH(view)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            val isRest = item.type == "rest"

            // --- Timeline connector lines ---
            val lineTopParams = holder.viewLineTop.layoutParams as FrameLayout.LayoutParams
            val lineBottomParams = holder.viewLineBottom.layoutParams as FrameLayout.LayoutParams
            lineTopParams.height = if (position == 0) 0 else dpToPx(24)
            lineBottomParams.height = if (position == items.lastIndex) 0 else dpToPx(24)
            holder.viewLineTop.layoutParams = lineTopParams
            holder.viewLineBottom.layoutParams = lineBottomParams

            // --- Status colors ---
            val dotColor: Int
            val itemBgColor: Int
            if (isSessionCompleted) {
                dotColor = ContextCompat.getColor(this@ProgramSessionActivity, R.color.success)
                itemBgColor = ContextCompat.getColor(this@ProgramSessionActivity, R.color.surface)
            } else if (position == 0) {
                dotColor = ContextCompat.getColor(this@ProgramSessionActivity, R.color.primary)
                itemBgColor = ContextCompat.getColor(this@ProgramSessionActivity, R.color.surface)
            } else {
                dotColor = ContextCompat.getColor(this@ProgramSessionActivity, R.color.text_hint)
                itemBgColor = ContextCompat.getColor(this@ProgramSessionActivity, R.color.surface_variant)
            }
            (holder.viewDot.background as? GradientDrawable)?.setColor(dotColor)
            holder.cardItem.setCardBackgroundColor(itemBgColor)

            // --- Image / Icon ---
            if (isRest) {
                holder.ivItemImage.visibility = View.GONE
                holder.ivIcon.visibility = View.VISIBLE
                holder.ivIcon.setImageResource(R.drawable.ic_rest)
                holder.ivIcon.setColorFilter(ContextCompat.getColor(this@ProgramSessionActivity, R.color.text_hint))
            } else {
                val slug = item.exerciseSlug ?: ""
                val config = exerciseConfigMap[slug]
                val imageUrl = config?.imageUrl
                if (!imageUrl.isNullOrBlank()) {
                    holder.ivItemImage.visibility = View.VISIBLE
                    holder.ivIcon.visibility = View.GONE
                    holder.ivItemImage.load(imageUrl) {
                        placeholder(R.drawable.ic_exercise)
                        error(R.drawable.ic_exercise)
                        crossfade(true)
                    }
                } else {
                    holder.ivItemImage.visibility = View.GONE
                    holder.ivIcon.visibility = View.VISIBLE
                    holder.ivIcon.setImageResource(R.drawable.ic_exercise)
                    holder.ivIcon.setColorFilter(ContextCompat.getColor(this@ProgramSessionActivity, R.color.primary))
                }
            }

            // --- Name & Detail ---
            if (isRest) {
                holder.tvName.text = getString(R.string.rest_time)
                val sec = (item.restDurationMs ?: 0L) / 1000L
                holder.tvDetail.text = getString(R.string.ds_rest_format, sec)
            } else {
                val slug = item.exerciseSlug ?: ""
                holder.tvName.text = exerciseNameMap[slug] ?: slug
                holder.tvDetail.text = buildExerciseDetailText(item)
            }

            // --- Swap icon (always visible for exercises, not in edit mode) ---
            if (!isRest && !isEditMode) {
                holder.layoutAlwaysActions.visibility = View.VISIBLE
                holder.btnSwapExercise.setOnClickListener {
                    val pos = holder.bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        showReplaceExerciseSheet(sessionId, pos, items[pos])
                    }
                }
            } else {
                holder.layoutAlwaysActions.visibility = View.GONE
            }

            // --- Edit mode: edit, delete, drag handle ---
            if (isEditMode) {
                holder.layoutEditControls.visibility = View.VISIBLE
                holder.btnDragHandle.visibility = View.VISIBLE
                holder.layoutAlwaysActions.visibility = View.GONE

                holder.btnEditItem.setOnClickListener {
                    val pos = holder.bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        val currentItem = items[pos]
                        if (currentItem.type == "rest") showEditRestDialog(sessionId, pos, currentItem)
                        else showEditExerciseDialog(sessionId, pos, currentItem)
                    }
                }
                holder.btnDeleteItem.setOnClickListener {
                    val pos = holder.bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        showDeleteItemDialog(sessionId, pos)
                    }
                }

                // Start drag on touch of handle
                holder.btnDragHandle.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        touchHelper?.startDrag(holder)
                    }
                    false
                }
            } else {
                holder.layoutEditControls.visibility = View.GONE
                holder.btnDragHandle.visibility = View.GONE
            }
        }

        fun moveItem(from: Int, to: Int) {
            val moved = items.removeAt(from)
            items.add(to, moved)
            notifyItemMoved(from, to)
        }
    }

    private fun buildExerciseDetailText(item: ProgramSessionItem): String {
        val sets = item.sets ?: 1
        val weight = item.weightKg

        return when {
            item.targetDuration != null && weight != null && weight > 0 ->
                getString(R.string.ds_sets_hold_weight_format, sets, item.targetDuration, weight)
            item.targetDuration != null ->
                getString(R.string.ds_sets_hold_format, sets, item.targetDuration)
            item.targetReps != null && weight != null && weight > 0 ->
                getString(R.string.ds_sets_reps_weight_format, sets, item.targetReps, weight)
            item.targetReps != null ->
                getString(R.string.ds_sets_reps_format, sets, item.targetReps)
            else ->
                getString(R.string.ds_sets_only_format, sets)
        }
    }

    private fun updateBottomBar() {
        val completedSessionIds = getCompletedSessionIds()
        val hasNextSession = sessions.any { it.id !in completedSessionIds }

        if (hasNextSession) {
            binding.btnStartSession.text = getString(R.string.ds_start_session)
            binding.btnStartSession.isEnabled = true
            // Always use startNextSession() to read from the CURRENT sessions list.
            // Never capture a session reference here — it would become stale after edits.
            binding.btnStartSession.setOnClickListener { startNextSession() }
        } else {
            binding.btnStartSession.text = getString(R.string.ds_session_completed)
            binding.btnStartSession.isEnabled = false
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Session Actions
    // ═══════════════════════════════════════════════════════════

    private fun startNextSession() {
        val completedSessionIds = getCompletedSessionIds()
        val nextSession = sessions.firstOrNull { it.id !in completedSessionIds }
        if (nextSession != null) {
            startSession(nextSession)
        }
    }

    private fun startSession(session: DayCustomizationStore.CustomizedSession) {
        if (session.items.isEmpty()) {
            Toast.makeText(this, "No exercises in this session", Toast.LENGTH_SHORT).show()
            return
        }

        val hasInvalid = session.items.any {
            it.type == "exercise" && it.exerciseSlug.isNullOrBlank()
        }
        if (hasInvalid) {
            Toast.makeText(this, getString(R.string.session_invalid_payload), Toast.LENGTH_LONG).show()
            return
        }

        // Save customizations before starting
        persistCustomizations()

        launchedSessionId = session.id
        sendSessionStart(session.id)

        val gson = com.google.gson.Gson()
        val itemsJson = gson.toJson(session.items)

        val intent = Intent(this, TrainingActivity::class.java).apply {
            putExtra(TrainingActivity.EXTRA_IS_SESSION_MODE, true)
            putExtra(TrainingActivity.EXTRA_SESSION_ITEMS_JSON, itemsJson)
            putExtra(TrainingActivity.EXTRA_TRAINING_MODE, TrainingActivity.MODE_CAMERA)
        }

        sessionLauncher.launch(intent)
    }

    private fun handleSessionResult(resultCode: Int, data: Intent?) {
        Log.d(TAG, "Session result code: $resultCode")

        if (resultCode == RESULT_OK && data != null) {
            val completedSets = data.getIntExtra(TrainingActivity.RESULT_SESSION_SETS_COMPLETED, 0)
            val totalSets = data.getIntExtra(TrainingActivity.RESULT_SESSION_SETS_PLANNED, 0)
            val durationMs = data.getLongExtra(TrainingActivity.RESULT_DURATION_MS, 0L)
            val totalReps = data.getIntExtra(TrainingActivity.RESULT_SESSION_TOTAL_REPS, 0)
            val avgAccuracy = data.getFloatExtra(TrainingActivity.RESULT_SESSION_AVG_ACCURACY, 0f)
            val avgFormScore = data.getFloatExtra(TrainingActivity.RESULT_SESSION_AVG_FORM_SCORE, 0f)
            val reportJson = data.getStringExtra(TrainingActivity.RESULT_SESSION_REPORT_JSON)
            val reportIds = data.getStringArrayListExtra(TrainingActivity.RESULT_SESSION_REPORT_IDS)
            val sessionId = launchedSessionId

            // Save local report (with form score)
            saveLocalSessionReport(sessionId, durationMs, totalSets, completedSets, totalReps, avgAccuracy, avgFormScore, reportJson)

            // Send to backend (single call, with offline queue)
            sendSessionComplete(
                sessionId ?: "",
                durationMs,
                sessions.firstOrNull { it.id == sessionId }?.items?.size ?: 0,
                totalSets,
                completedSets,
                totalReps,
                avgAccuracy,
                avgFormScore,
                reportJson
            )

            // Refresh: re-determine expanded sessions and re-render
            determineExpandedSessions()
            renderAllSessions()
            updateBottomBar()

            // Navigate to session report after EACH session (rich per-exercise reports)
            val session = sessions.firstOrNull { it.id == sessionId }
            val reportIntent = Intent(this, ProgramSessionReportActivity::class.java).apply {
                putExtra(ProgramSessionReportActivity.EXTRA_TOTAL_ITEMS, session?.items?.size ?: 0)
                putExtra(ProgramSessionReportActivity.EXTRA_TOTAL_SETS, totalSets)
                putExtra(ProgramSessionReportActivity.EXTRA_COMPLETED_SETS, completedSets)
                putExtra(ProgramSessionReportActivity.EXTRA_DURATION_MS, durationMs)
                putExtra(ProgramSessionReportActivity.EXTRA_AVG_ACCURACY, avgAccuracy)
                if (!reportJson.isNullOrBlank()) {
                    putExtra(ProgramSessionReportActivity.EXTRA_SESSION_REPORT_JSON, reportJson)
                }
                if (!reportIds.isNullOrEmpty()) {
                    putStringArrayListExtra(ProgramSessionReportActivity.EXTRA_REPORT_IDS, reportIds)
                }
            }
            startActivity(reportIntent)
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Customization: Session Operations
    // ═══════════════════════════════════════════════════════════

    private fun showRenameDialog(session: DayCustomizationStore.CustomizedSession) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_rename_session, null)
        val inputEn = dialogView.findViewById<EditText>(R.id.inputNameEn)
        val inputAr = dialogView.findViewById<EditText>(R.id.inputNameAr)

        inputEn.setText(session.name.en)
        inputAr.setText(session.name.ar)

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.ds_rename_session_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.ds_save)) { _, _ ->
                val newName = LocalizedText(
                    en = inputEn.text.toString().ifBlank { session.name.en },
                    ar = inputAr.text.toString().ifBlank { session.name.ar }
                )
                val updatedSessions = sessions.map {
                    if (it.id == session.id) it.copy(name = newName) else it
                }
                sessions.clear()
                sessions.addAll(updatedSessions)
                persistCustomizations()
                renderAllSessions()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showDeleteSessionDialog(session: DayCustomizationStore.CustomizedSession) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.ds_delete_session_title))
            .setMessage(getString(R.string.ds_delete_session_message))
            .setPositiveButton(getString(R.string.ds_delete)) { _, _ ->
                sessions.removeAll { it.id == session.id }
                expandedSessionIds.remove(session.id)
                persistCustomizations()
                renderHeader()
                renderAllSessions()
                updateBottomBar()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun moveSession(fromIndex: Int, direction: Int) {
        val toIndex = fromIndex + direction
        if (toIndex < 0 || toIndex >= sessions.size) return

        val temp = sessions[fromIndex]
        sessions[fromIndex] = sessions[toIndex]
        sessions[toIndex] = temp

        // Update sort orders
        sessions.forEachIndexed { index, session ->
            sessions[index] = session.copy(sortOrder = index)
        }

        persistCustomizations()
        renderAllSessions()
    }

    // ═══════════════════════════════════════════════════════════
    // Customization: Item Operations
    // ═══════════════════════════════════════════════════════════

    private fun updateItemInSession(sessionId: String, itemIndex: Int, updatedItem: ProgramSessionItem) {
        val sessionIndex = sessions.indexOfFirst { it.id == sessionId }
        if (sessionIndex < 0) return

        val session = sessions[sessionIndex]
        val updatedItems = session.items.toMutableList()
        if (itemIndex >= 0 && itemIndex < updatedItems.size) {
            updatedItems[itemIndex] = updatedItem
        }
        sessions[sessionIndex] = session.copy(items = updatedItems)
        persistCustomizations()
        renderAllSessions()
    }

    private fun removeItemFromSession(sessionId: String, itemIndex: Int) {
        val sessionIndex = sessions.indexOfFirst { it.id == sessionId }
        if (sessionIndex < 0) return

        val session = sessions[sessionIndex]
        val updatedItems = session.items.toMutableList()
        if (itemIndex >= 0 && itemIndex < updatedItems.size) {
            updatedItems.removeAt(itemIndex)
        }
        sessions[sessionIndex] = session.copy(items = updatedItems)
        persistCustomizations()
        renderAllSessions()
    }

    private fun addItemToSession(sessionId: String, item: ProgramSessionItem) {
        val sessionIndex = sessions.indexOfFirst { it.id == sessionId }
        if (sessionIndex < 0) return

        val session = sessions[sessionIndex]
        val updatedItems = session.items.toMutableList()
        updatedItems.add(item.copy(sortOrder = updatedItems.size))
        sessions[sessionIndex] = session.copy(items = updatedItems)
        persistCustomizations()
        renderAllSessions()
    }

    // ═══════════════════════════════════════════════════════════
    // Replace Exercise (swap icon — always visible)
    // ═══════════════════════════════════════════════════════════

    private fun showReplaceExerciseSheet(sessionId: String, itemIndex: Int, item: ProgramSessionItem) {
        if (item.type != "exercise") return

        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val sheet = layoutInflater.inflate(R.layout.bottom_sheet_replace_exercise, null)
        dialog.setContentView(sheet)

        val inputSearch = sheet.findViewById<EditText>(R.id.inputReplaceSearch)
        val layoutEasier = sheet.findViewById<LinearLayout>(R.id.layoutReplaceEasier)
        val layoutSimilar = sheet.findViewById<LinearLayout>(R.id.layoutReplaceSimilar)
        val layoutHarder = sheet.findViewById<LinearLayout>(R.id.layoutReplaceHarder)

        val language = getCurrentLanguage()
        val currentSlug = item.exerciseSlug
        val currentConfig = currentSlug?.let { exerciseConfigMap[it] }
        val baseList = allExercises.filter { it.fileName != currentSlug }
        val sameCategory = currentConfig?.category?.code?.let { code ->
            baseList.filter { it.category.code == code }
        } ?: baseList

        fun buildLists(query: String) {
            val filtered = if (query.isBlank()) sameCategory else {
                val q = query.trim().lowercase()
                sameCategory.filter { it.name.get(language).ifBlank { it.name.en }.lowercase().contains(q) }
            }

            layoutEasier.removeAllViews()
            layoutSimilar.removeAllViews()
            layoutHarder.removeAllViews()

            filtered.forEach { ex ->
                val row = layoutInflater.inflate(R.layout.item_simple_exercise_row, null)
                row.findViewById<TextView>(R.id.tvSimpleExerciseName).text =
                    ex.name.get(language).ifBlank { ex.name.en }
                row.findViewById<TextView>(R.id.tvSimpleExerciseMeta).text =
                    if (ex.supportsWeight) getString(R.string.replace_hint_weighted)
                    else getString(R.string.replace_hint_bodyweight)
                row.setOnClickListener {
                    val updated = item.copy(exerciseSlug = ex.fileName)
                    exerciseNameMap[ex.fileName] = ex.name.get(language).ifBlank { ex.name.en }
                    updateItemInSession(sessionId, itemIndex, updated)
                    dialog.dismiss()
                }
                layoutSimilar.addView(row)
            }
        }

        inputSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                buildLists(s?.toString().orEmpty())
            }
        })
        buildLists("")
        dialog.show()
    }

    // ═══════════════════════════════════════════════════════════
    // Edit Dialogs
    // ═══════════════════════════════════════════════════════════

    private fun showEditExerciseDialog(sessionId: String, itemIndex: Int, item: ProgramSessionItem) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_session_item_edit, null)
        val spinnerExercise = dialogView.findViewById<android.widget.Spinner>(R.id.spinnerExercise)
        val inputSets = dialogView.findViewById<EditText>(R.id.inputSets)
        val inputReps = dialogView.findViewById<EditText>(R.id.inputTargetReps)
        val inputDuration = dialogView.findViewById<EditText>(R.id.inputTargetDuration)
        val inputRestBetween = dialogView.findViewById<EditText>(R.id.inputRestBetweenSets)
        val inputWeight = dialogView.findViewById<EditText>(R.id.inputWeightKg)

        val language = getCurrentLanguage()
        val options = allExercises.map { ex ->
            val name = ex.name.get(language).ifBlank { ex.name.en }
            name to ex.fileName
        }
        val labels = options.map { it.first }
        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        spinnerExercise.adapter = adapter
        val currentIndex = options.indexOfFirst { it.second == item.exerciseSlug }
        if (currentIndex >= 0) spinnerExercise.setSelection(currentIndex)

        inputSets.setText((item.sets ?: 1).toString())
        inputReps.setText(item.targetReps?.toString() ?: "")
        inputDuration.setText(item.targetDuration?.toString() ?: "")
        inputRestBetween.setText(item.restBetweenSetsMs?.let { (it / 1000).toString() } ?: "")
        inputWeight.setText(item.weightKg?.toString() ?: "")

        val bottomSheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        bottomSheet.setContentView(dialogView)

        dialogView.findViewById<MaterialButton>(R.id.btnSave).setOnClickListener {
            val selectedSlug = options.getOrNull(spinnerExercise.selectedItemPosition)?.second
            val updated = item.copy(
                exerciseSlug = selectedSlug ?: item.exerciseSlug,
                sets = inputSets.text.toString().toIntOrNull() ?: item.sets,
                targetReps = inputReps.text.toString().toIntOrNull(),
                targetDuration = inputDuration.text.toString().toIntOrNull(),
                restBetweenSetsMs = inputRestBetween.text.toString().toLongOrNull()?.times(1000),
                weightKg = inputWeight.text.toString().toFloatOrNull()
            )
            updateItemInSession(sessionId, itemIndex, updated)
            bottomSheet.dismiss()
        }
        dialogView.findViewById<MaterialButton>(R.id.btnCancel).setOnClickListener {
            bottomSheet.dismiss()
        }
        bottomSheet.show()
    }

    private fun showEditRestDialog(sessionId: String, itemIndex: Int, item: ProgramSessionItem) {
        val editText = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(((item.restDurationMs ?: 0L) / 1000).toString())
            hint = getString(R.string.ds_rest_duration_label)
            setPadding(dpToPx(24), dpToPx(16), dpToPx(24), dpToPx(8))
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.ds_edit_rest_title))
            .setView(editText)
            .setPositiveButton(getString(R.string.ds_save)) { _, _ ->
                val seconds = editText.text.toString().toLongOrNull() ?: 0
                if (seconds > 0) {
                    updateItemInSession(sessionId, itemIndex, item.copy(restDurationMs = seconds * 1000))
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showDeleteItemDialog(sessionId: String, itemIndex: Int) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.ds_delete_item_title))
            .setMessage(getString(R.string.ds_delete_item_message))
            .setPositiveButton(getString(R.string.ds_delete)) { _, _ ->
                removeItemFromSession(sessionId, itemIndex)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showAddExerciseSheet() {
        // Determine which session to add to (the expanded one in edit mode)
        val targetSession = sessions.firstOrNull { it.id in expandedSessionIds }
        if (targetSession == null) {
            Toast.makeText(this, "Expand a session first", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val sheet = layoutInflater.inflate(R.layout.bottom_sheet_add_exercise, null)
        dialog.setContentView(sheet)

        val inputSearch = sheet.findViewById<EditText>(R.id.inputAddExerciseSearch)
        val listContainer = sheet.findViewById<LinearLayout>(R.id.layoutAddExerciseList)
        val language = getCurrentLanguage()

        fun renderList(query: String) {
            listContainer.removeAllViews()
            val filtered = if (query.isBlank()) allExercises else {
                val q = query.trim().lowercase()
                allExercises.filter { ex ->
                    ex.name.get(language).ifBlank { ex.name.en }.lowercase().contains(q)
                }
            }
            filtered.forEach { ex ->
                val row = layoutInflater.inflate(R.layout.item_simple_exercise_row, listContainer, false)
                row.findViewById<TextView>(R.id.tvSimpleExerciseName).text =
                    ex.name.get(language).ifBlank { ex.name.en }
                row.findViewById<TextView>(R.id.tvSimpleExerciseMeta).text =
                    if (ex.supportsWeight) getString(R.string.replace_hint_weighted)
                    else getString(R.string.replace_hint_bodyweight)
                row.setOnClickListener {
                    val reps = ex.repCountingConfig.reps
                    val duration = ex.repCountingConfig.duration
                    val newItem = ProgramSessionItem(
                        type = "exercise",
                        exerciseSlug = ex.fileName,
                        sets = 3,
                        targetReps = if (duration == null) reps else null,
                        targetDuration = duration,
                        restBetweenSetsMs = 30000L
                    )
                    addItemToSession(targetSession.id, newItem)
                    dialog.dismiss()
                }
                listContainer.addView(row)
            }
        }

        inputSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                renderList(s?.toString().orEmpty())
            }
        })
        renderList("")
        dialog.show()
    }

    private fun showAddRestSheet() {
        val targetSession = sessions.firstOrNull { it.id in expandedSessionIds }
        if (targetSession == null) {
            Toast.makeText(this, "Expand a session first", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val sheet = layoutInflater.inflate(R.layout.bottom_sheet_add_rest, null)
        dialog.setContentView(sheet)

        val inputSeconds = sheet.findViewById<EditText>(R.id.inputRestSeconds)
        sheet.findViewById<MaterialButton>(R.id.btnRest30).setOnClickListener { inputSeconds.setText("30") }
        sheet.findViewById<MaterialButton>(R.id.btnRest45).setOnClickListener { inputSeconds.setText("45") }
        sheet.findViewById<MaterialButton>(R.id.btnRest60).setOnClickListener { inputSeconds.setText("60") }

        sheet.findViewById<MaterialButton>(R.id.btnAddRest).setOnClickListener {
            val seconds = inputSeconds.text.toString().toLongOrNull() ?: 0L
            if (seconds > 0) {
                val newItem = ProgramSessionItem(
                    type = "rest",
                    restDurationMs = seconds * 1000L
                )
                addItemToSession(targetSession.id, newItem)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    // ═══════════════════════════════════════════════════════════
    // Persistence
    // ═══════════════════════════════════════════════════════════

    private fun persistCustomizations() {
        val pid = programId ?: return

        // Normalize sortOrder for sessions AND their items to match current list positions.
        // This ensures sortedBy { it.sortOrder } always produces the correct order everywhere.
        val normalized = sessions.mapIndexed { sIdx, session ->
            session.copy(
                sortOrder = sIdx,
                items = session.items.mapIndexed { iIdx, item ->
                    item.copy(sortOrder = iIdx)
                }
            )
        }
        sessions.clear()
        sessions.addAll(normalized)

        Log.d(TAG, "persistCustomizations: programId=$pid, week=$weekNumber, day=$dayNumber, sessions=${normalized.size}")
        customizationStore.saveSessions(pid, weekNumber, dayNumber, normalized)

        // Sync to backend
        syncCustomizationsToBackend()
    }

    /**
     * Send current customizations to the backend via PUT /api/mobile/user-programs/:id
     * Builds a structured customizations map keyed by "day_{weekNumber}_{dayNumber}"
     */
    private fun syncCustomizationsToBackend() {
        val token = AuthManager.getAccessToken(this)
        if (token.isNullOrBlank()) {
            Log.w(TAG, "Skip customization sync: missing access token")
            return
        }
        val programRepo = ProgramRepository.getInstance(this)
        val userProgramId = programRepo.getActiveUserProgramId()
        if (userProgramId.isNullOrBlank()) {
            Log.w(TAG, "Skip customization sync: missing active userProgramId")
            return
        }

        // Build the customizations payload with the current day's sessions
        val dayKey = "day_${weekNumber}_${dayNumber}"
        val sessionsPayload = sessions.map { session ->
            mapOf(
                "id" to session.id,
                "name" to mapOf("en" to session.name.en, "ar" to session.name.ar),
                "sortOrder" to session.sortOrder,
                "isDeleted" to session.isDeleted,
                "items" to session.items.map { item ->
                    val itemMap = mutableMapOf<String, Any?>(
                        "type" to item.type,
                        "sortOrder" to item.sortOrder
                    )
                    item.exerciseSlug?.let { itemMap["exerciseSlug"] = it }
                    item.sets?.let { itemMap["sets"] = it }
                    item.targetReps?.let { itemMap["targetReps"] = it }
                    item.targetDuration?.let { itemMap["targetDuration"] = it }
                    item.restBetweenSetsMs?.let { itemMap["restBetweenSetsMs"] = it }
                    item.restDurationMs?.let { itemMap["restDurationMs"] = it }
                    item.weightKg?.let { itemMap["weightKg"] = it }
                    itemMap.filterValues { it != null }
                }
            )
        }

        val payload = mapOf<String, Any>(
            "customizations" to mapOf(dayKey to sessionsPayload)
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = ApiClient.mobileSyncApi.updateUserProgram(
                    userProgramId, "Bearer $token", payload
                )
                if (response.isSuccessful) {
                    Log.d(TAG, "Synced customizations to backend for $dayKey")
                } else {
                    Log.w(TAG, "Failed to sync customizations: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync customizations to backend: ${e.message}")
            }
        }
    }

    private fun saveLocalSessionReport(
        sessionId: String?,
        durationMs: Long,
        totalSets: Int,
        completedSets: Int,
        totalReps: Int,
        avgAccuracy: Float,
        avgFormScore: Float,
        reportJson: String?
    ) {
        val pid = programId ?: return
        val sid = sessionId ?: return
        val report = if (!reportJson.isNullOrBlank()) {
            val gson = com.google.gson.Gson()
            val type = object :
                com.google.gson.reflect.TypeToken<com.trainingvalidator.poc.training.session.SessionTrainingEngine.SessionReport>() {}.type
            runCatching {
                gson.fromJson<com.trainingvalidator.poc.training.session.SessionTrainingEngine.SessionReport>(reportJson, type)
            }.getOrNull()
        } else null

        reportStore.save(
            ProgramSessionReportStore.ProgramSessionLocalReport(
                sessionId = sid,
                programId = pid,
                weekNumber = weekNumber,
                dayNumber = dayNumber,
                completedAt = System.currentTimeMillis(),
                totalSetsPlanned = totalSets,
                totalSetsCompleted = completedSets,
                totalReps = totalReps,
                averageAccuracy = avgAccuracy,
                averageFormScore = avgFormScore,
                totalDurationMs = durationMs,
                report = report
            )
        )
    }

    // ═══════════════════════════════════════════════════════════
    // Network
    // ═══════════════════════════════════════════════════════════

    private fun sendSessionStart(sessionId: String) {
        val token = AuthManager.getAccessToken(this) ?: return
        val payload = mapOf<String, Any>(
            "programId" to (programId ?: ""),
            "weekNumber" to weekNumber,
            "dayNumber" to dayNumber,
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
        sessionId: String,
        durationMs: Long,
        totalExercises: Int,
        totalSets: Int,
        completedSets: Int,
        totalReps: Int,
        avgAccuracy: Float,
        avgFormScore: Float,
        reportJson: String?
    ) {
        val token = AuthManager.getAccessToken(this)
        val payloadMap = mutableMapOf<String, Any>(
            "completedAt" to System.currentTimeMillis(),
            "totalDurationMs" to durationMs,
            "totalExercises" to totalExercises,
            "totalSets" to totalSets,
            "completedSets" to completedSets,
            "totalReps" to totalReps,
            "avgAccuracy" to avgAccuracy,
            "avgFormScore" to avgFormScore
        )
        if (!reportJson.isNullOrBlank()) {
            val gson = com.google.gson.Gson()
            payloadMap["report"] = gson.fromJson(reportJson, com.google.gson.JsonElement::class.java)
        }

        // Always save to offline queue first (offline-first approach)
        reportStore.addPendingSync(
            ProgramSessionReportStore.PendingSyncEntry(
                sessionId = sessionId,
                programId = programId ?: "",
                weekNumber = weekNumber,
                dayNumber = dayNumber,
                payload = payloadMap
            )
        )

        // Attempt to sync immediately if online
        if (token == null) {
            Log.w(TAG, "No auth token — report queued for later sync")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Single call to /complete (backend handles report + progress in one call)
                val response = ApiClient.mobileSyncApi.completeSession(sessionId, "Bearer $token", payloadMap)
                if (response.isSuccessful) {
                    // Remove from offline queue on success
                    reportStore.removePendingSync(sessionId)
                    Log.d(TAG, "Session complete synced successfully")
                } else {
                    Log.w(TAG, "Session complete sync failed (${response.code()}) — will retry")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync session complete: ${e.message} — queued for retry")
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════

    private fun getCompletedSessionIds(): Set<String> {
        val pid = programId ?: return emptySet()
        val reports = reportStore.getByDay(pid, weekNumber, dayNumber)
        return reports.map { it.sessionId }.toSet()
    }

    private fun getLocalizedName(text: LocalizedText): String {
        val language = getCurrentLanguage()
        return when (language) {
            "ar" -> text.ar.ifBlank { text.en }
            else -> text.en.ifBlank { text.ar }
        }
    }

    private fun getCurrentLanguage(): String {
        return java.util.Locale.getDefault().language
    }

    private fun estimateSessionDuration(items: List<ProgramSessionItem>): Int {
        var totalSeconds = 0
        items.forEach { item ->
            if (item.type == "exercise") {
                val sets = item.sets ?: 1
                val perSet = (item.targetDuration ?: 30) + ((item.restBetweenSetsMs ?: 30000L) / 1000).toInt()
                totalSeconds += sets * perSet
            } else if (item.type == "rest") {
                totalSeconds += ((item.restDurationMs ?: 0L) / 1000).toInt()
            }
        }
        return (totalSeconds / 60).coerceAtLeast(1)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
