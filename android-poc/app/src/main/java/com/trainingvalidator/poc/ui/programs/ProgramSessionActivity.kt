package com.trainingvalidator.poc.ui.programs

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
import com.google.android.material.snackbar.Snackbar
import com.trainingvalidator.poc.ui.utils.ExerciseSearchMatcher
import com.trainingvalidator.poc.ui.utils.currentLanguage
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.ActivityProgramSessionBinding
import com.trainingvalidator.poc.network.ApiClient
import com.trainingvalidator.poc.network.EffectivePlanPayload
import com.trainingvalidator.poc.storage.AuthManager
import com.trainingvalidator.poc.storage.DayCustomizationStore
import com.trainingvalidator.poc.storage.HomeRepository
import com.trainingvalidator.poc.storage.ExerciseRepository
import com.trainingvalidator.poc.storage.ProgramRepository
import com.trainingvalidator.poc.storage.ProgramSessionReportStore
import com.trainingvalidator.poc.training.config.SettingsManager
import com.trainingvalidator.poc.training.models.ExerciseConfig
import com.trainingvalidator.poc.training.models.LocalizedText
import com.trainingvalidator.poc.training.models.ProgramConfig
import com.trainingvalidator.poc.training.models.ProgramDay
import com.trainingvalidator.poc.training.models.ProgramSession
import com.trainingvalidator.poc.training.models.ProgramSessionItem
import androidx.lifecycle.lifecycleScope
import com.trainingvalidator.poc.ui.train.TrainingActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.max

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

    private enum class OverrideSyncKind {
        ADJUST,
        REPLACE
    }

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
    private var currentUserProgramId: String? = null

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

    /** One catch-up hint per activity instance when this day is in missedSlots. */
    private var catchUpDayPromptShown = false

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
        binding.btnSkipWarmup.setOnClickListener { skipWarmupScrollToMainWork() }
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
            Toast.makeText(this, getString(R.string.error_program_not_found), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        lifecycleScope.launch {
            val programRepo = ProgramRepository.getInstance(this@ProgramSessionActivity)
            val exerciseRepo = ExerciseRepository.getInstance(this@ProgramSessionActivity)
            currentUserProgramId = null

            withContext(Dispatchers.IO) {
                exerciseRepo.initialize(autoSync = true)
            }

            allExercises = exerciseRepo.getAllExercises()
            exerciseConfigMap.clear()
            exerciseNameMap.clear()
            val language = currentLanguage
            allExercises.forEach { ex ->
                exerciseConfigMap[ex.fileName] = ex
                exerciseNameMap[ex.fileName] = ex.name.get(language).ifBlank { ex.name.en }
            }

            val resolvedProgram = withContext(Dispatchers.IO) {
                val slug = programSlug
                val id = programId
                when {
                    !slug.isNullOrBlank() -> programRepo.getOrFetchProgram(slug)
                    !id.isNullOrBlank()   -> programRepo.getOrFetchProgramById(id)
                    else -> null
                }
            }

            if (resolvedProgram == null) {
                Toast.makeText(this@ProgramSessionActivity, getString(R.string.error_program_not_found), Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this@ProgramSessionActivity, getString(R.string.error_day_not_found), Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            day = resolvedDay

            // Load effective sessions: local customization wins; else server effective plan (progression + overrides)
            val baseSessions = customizationStore.getEffectiveSessions(
                programId = resolvedProgram.id,
                weekNumber = weekNumber,
                dayNumber = dayNumber,
                originalSessions = resolvedDay.sessions
            )
            var effectiveSessions = baseSessions

            val activeUp = programRepo.getActiveUserProgramExport()
            val token = AuthManager.getAccessToken(this@ProgramSessionActivity)
            if (activeUp != null && activeUp.isActive && activeUp.programId == resolvedProgram.id) {
                currentUserProgramId = activeUp.id
            }
            if (activeUp != null &&
                activeUp.isActive &&
                activeUp.programId == resolvedProgram.id &&
                !customizationStore.hasCustomization(resolvedProgram.id, weekNumber, dayNumber) &&
                !token.isNullOrBlank()
            ) {
                val mapped = withContext(Dispatchers.IO) {
                    try {
                        val resp = ApiClient.mobileSyncApi.getEffectivePlan(
                            currentUserProgramId ?: activeUp.id,
                            "Bearer $token",
                            weekNumber,
                            dayNumber
                        )
                        val body = resp.body()
                        if (resp.isSuccessful && body?.success == true && body.data != null) {
                            mapEffectivePlanToCustomizedSessions(body.data, resolvedDay.sessions, exerciseRepo)
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "getEffectivePlan failed", e)
                        null
                    }
                }
                if (mapped != null) {
                    effectiveSessions = mapped
                }
            }

            sessions.clear()
            sessions.addAll(effectiveSessions)

            // Auto-expand the target session or the first non-completed session
            determineExpandedSessions()

            // Render
            renderHeader()
            renderAllSessions()
            updateBottomBar()
            maybeShowCatchUpForThisDay()
        }
    }

    private fun determineExpandedSessions() {
        expandedSessionIds.clear()
        val completedSessionIds = getCompletedSessionIds()

        val target = targetSessionId
        if (target != null && sessions.any { it.id == target }) {
            expandedSessionIds.add(target)
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
            updateSkipWarmupButtonVisibility()
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
        updateSkipWarmupButtonVisibility()
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
        view.tag = session.id

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
        val backendMin = session.estimatedDurationMin
        val estimatedMinutes = backendMin ?: estimateSessionDuration(session.items)
        val baseMeta = getString(R.string.ds_exercises_meta_format, exerciseCount, estimatedMinutes)
        tvMeta.text = if (backendMin != null) {
            "$baseMeta · " + getString(R.string.session_duration_badge, backendMin)
        } else {
            baseMeta
        }

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
            sessionRole = session.role.ifBlank { "MAIN" },
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
        private val sessionRole: String,
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
            val layoutActionRow: LinearLayout = view.findViewById(R.id.layoutItemActionRow)
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
            val unavailable = !isRest && (item.deletedExercise == true || item.exerciseSlug.isNullOrBlank())

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
                if (unavailable) {
                    holder.ivItemImage.visibility = View.GONE
                    holder.ivIcon.visibility = View.VISIBLE
                    holder.ivIcon.setImageResource(R.drawable.ic_exercise)
                    holder.ivIcon.setColorFilter(ContextCompat.getColor(this@ProgramSessionActivity, R.color.warning))
                    holder.cardItem.strokeWidth = dpToPx(2)
                    holder.cardItem.setStrokeColor(
                        ContextCompat.getColorStateList(this@ProgramSessionActivity, R.color.warning)
                    )
                } else {
                    holder.cardItem.strokeWidth = 0
                    val config = exerciseConfigMap[slug]
                    val imageUrl = config?.imageUrl
                    if (!imageUrl.isNullOrBlank()) {
                        holder.ivItemImage.visibility = View.VISIBLE
                        holder.ivIcon.visibility = View.GONE
                        holder.ivItemImage.load(imageUrl) {
                            placeholder(R.drawable.ic_exercise)
                            error(R.drawable.ic_exercise)
                            crossfade(false)
                        }
                    } else {
                        holder.ivItemImage.visibility = View.GONE
                        holder.ivIcon.visibility = View.VISIBLE
                        holder.ivIcon.setImageResource(R.drawable.ic_exercise)
                        holder.ivIcon.setColorFilter(
                            ContextCompat.getColor(this@ProgramSessionActivity, R.color.primary)
                        )
                    }
                }
            }

            // --- Name & Detail ---
            if (isRest) {
                holder.tvName.text = getString(R.string.rest_time)
                val sec = (item.restDurationMs ?: 0L) / 1000L
                holder.tvDetail.text = getString(R.string.ds_rest_format, sec)
            } else {
                val slug = item.exerciseSlug ?: ""
                if (unavailable) {
                    holder.tvName.text = getString(R.string.exercise_unavailable_substitute)
                } else {
                    holder.tvName.text = exerciseNameMap[slug] ?: slug
                }
                val sectionPrefix = if (item.type == "exercise" && position == 0) {
                    formatRoleTitle(normalizeRoleKey(sessionRole)) + "\n"
                } else ""
                holder.tvDetail.text = sectionPrefix + buildExerciseDetailText(item)
                if (unavailable) {
                    holder.cardItem.setOnClickListener {
                        val pos = holder.bindingAdapterPosition
                        if (pos != RecyclerView.NO_POSITION) {
                            showReplaceExerciseSheet(sessionId, pos, items[pos])
                        }
                    }
                } else {
                    holder.cardItem.setOnClickListener(null)
                }
            }

            // --- Customization actions (edit mode only, below text so title/image keep priority) ---
            holder.layoutActionRow.visibility = if (isEditMode) View.VISIBLE else View.GONE
            if (!isRest && isEditMode) {
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

    private fun formatRoleTitle(key: String): String = when (key) {
        "WARMUP" -> getString(R.string.section_warmup)
        "MAIN" -> getString(R.string.section_main)
        "COOLDOWN" -> getString(R.string.section_cooldown)
        else -> getString(R.string.section_other)
    }

    private fun buildExerciseDetailText(item: ProgramSessionItem): String {
        val sets = item.sets ?: 1
        val weight = item.weightKg

        val base = when {
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

        if (!SettingsManager.isAdvancedTrainingDisplay()) {
            return base
        }

        val sourceLabel = formatSuggestionSource(item.suggestionSource) ?: return base
        return "$base\n$sourceLabel"
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
            Toast.makeText(this, getString(R.string.error_no_exercises), Toast.LENGTH_SHORT).show()
            return
        }

        val firstBadIdx = session.items.indexOfFirst {
            it.type == "exercise" && (it.deletedExercise == true || it.exerciseSlug.isNullOrBlank())
        }
        if (firstBadIdx >= 0) {
            expandedSessionIds.add(session.id)
            renderAllSessions()
            Toast.makeText(this, getString(R.string.exercise_unavailable_substitute), Toast.LENGTH_LONG).show()
            binding.scrollContent.post {
                showReplaceExerciseSheet(session.id, firstBadIdx, session.items[firstBadIdx])
            }
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
            putExtra(TrainingActivity.EXTRA_SESSION_ROLE, session.role.ifBlank { "MAIN" })
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
            determineExpandedSessions()
            renderAllSessions()
            updateBottomBar()

            showPostSessionRpeDialog { rpe ->
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
                    reportJson,
                    rpe
                )

                // Refresh UI (already updated after save; repeat is harmless)
                determineExpandedSessions()
                renderAllSessions()
                updateBottomBar()

                // Navigate to unified session report
                val reportIntent = com.trainingvalidator.poc.ui.report.SessionReportActivity.createSessionIntent(
                    context = this,
                    reportIds = reportIds ?: emptyList(),
                    sessionReportJson = reportJson
                )
                startActivity(reportIntent)
            }
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

    private fun updateItemInSession(
        sessionId: String,
        itemIndex: Int,
        updatedItem: ProgramSessionItem,
        overrideSyncKind: OverrideSyncKind = OverrideSyncKind.ADJUST
    ) {
        val sessionIndex = sessions.indexOfFirst { it.id == sessionId }
        if (sessionIndex < 0) return

        val session = sessions[sessionIndex]
        val updatedItems = session.items.toMutableList()
        var originalItem: ProgramSessionItem? = null
        if (itemIndex >= 0 && itemIndex < updatedItems.size) {
            originalItem = updatedItems[itemIndex]
            updatedItems[itemIndex] = updatedItem
        }
        sessions[sessionIndex] = session.copy(items = updatedItems)
        persistCustomizations()
        when (overrideSyncKind) {
            OverrideSyncKind.REPLACE -> syncReplaceOverride(originalItem, updatedItem)
            OverrideSyncKind.ADJUST -> syncAdjustOverride(originalItem, updatedItem)
        }
        renderAllSessions()
    }

    private fun removeItemFromSession(sessionId: String, itemIndex: Int) {
        val sessionIndex = sessions.indexOfFirst { it.id == sessionId }
        if (sessionIndex < 0) return

        val session = sessions[sessionIndex]
        val updatedItems = session.items.toMutableList()
        var removedItem: ProgramSessionItem? = null
        if (itemIndex >= 0 && itemIndex < updatedItems.size) {
            removedItem = updatedItems.removeAt(itemIndex)
        }
        sessions[sessionIndex] = session.copy(items = updatedItems)
        persistCustomizations()
        syncSkipOverride(removedItem)
        renderAllSessions()
    }

    private fun addItemToSession(sessionId: String, item: ProgramSessionItem) {
        val sessionIndex = sessions.indexOfFirst { it.id == sessionId }
        if (sessionIndex < 0) return

        val session = sessions[sessionIndex]
        val updatedItems = session.items.toMutableList()
        val anchorItem = updatedItems.lastOrNull { !it.serverItemId.isNullOrBlank() }
        val appendedItem = item.copy(sortOrder = updatedItems.size)
        updatedItems.add(appendedItem)
        sessions[sessionIndex] = session.copy(items = updatedItems)
        persistCustomizations()
        syncAddOverride(anchorItem, appendedItem)
        renderAllSessions()
    }

    private data class SessionExerciseRef(
        val sessionId: String,
        val itemIndex: Int,
        val item: ProgramSessionItem
    )

    private fun hasWarmupBeforeFirstMainWork(): Boolean {
        val ordered = sessions.filter { !it.isDeleted }.sortedBy { it.sortOrder }
        val firstMainIdx = ordered.indexOfFirst {
            val r = it.role.trim().uppercase(Locale.US)
            r == "MAIN" || r == "ACCESSORY" || r == "CORRECTIVE" || r == "TEST"
        }
        if (firstMainIdx <= 0) return false
        return ordered.take(firstMainIdx).any {
            val r = it.role.trim().uppercase(Locale.US)
            r == "WARMUP" || r == "ACTIVATION"
        }
    }

    private fun firstMainWorkExerciseRef(): SessionExerciseRef? {
        for (session in sessions.filter { !it.isDeleted }.sortedBy { it.sortOrder }) {
            val r = session.role.trim().uppercase(Locale.US)
            if (r == "MAIN" || r == "ACCESSORY" || r == "CORRECTIVE" || r == "TEST") {
                val idx = session.items.indexOfFirst { it.type == "exercise" && it.exerciseSlug != null }
                if (idx >= 0) return SessionExerciseRef(session.id, idx, session.items[idx])
            }
        }
        return null
    }

    private fun updateSkipWarmupButtonVisibility() {
        binding.btnSkipWarmup.visibility =
            if (hasWarmupBeforeFirstMainWork()) View.VISIBLE else View.GONE
    }

    private fun skipWarmupScrollToMainWork() {
        val target = firstMainWorkExerciseRef() ?: run {
            Toast.makeText(this, getString(R.string.skip_warmup_none), Toast.LENGTH_SHORT).show()
            return
        }
        expandedSessionIds.clear()
        expandedSessionIds.add(target.sessionId)
        renderAllSessions()
        binding.scrollContent.post {
            scrollSessionCardAndTimelineTo(target.sessionId, target.itemIndex)
        }
    }

    private fun scrollSessionCardAndTimelineTo(sessionId: String, itemIndex: Int) {
        for (i in 0 until binding.layoutSessions.childCount) {
            val card = binding.layoutSessions.getChildAt(i)
            if (card.tag != sessionId) continue
            var top = 0
            var v: View? = card
            while (v != null && v != binding.scrollContent) {
                top += v.top
                v = v.parent as? View
            }
            binding.scrollContent.smoothScrollTo(0, max(0, top - dpToPx(96)))
            val rv = card.findViewById<RecyclerView>(R.id.rvTimelineItems)
            rv.post {
                (rv.layoutManager as? LinearLayoutManager)
                    ?.scrollToPositionWithOffset(itemIndex, dpToPx(8))
            }
            break
        }
    }

    private fun maybeShowCatchUpForThisDay() {
        if (catchUpDayPromptShown) return
        val upId = currentUserProgramId ?: return
        val home = HomeRepository.getInstance(this).getCachedData() ?: return
        val train = home.trainMode ?: return
        val active = train.activeProgram ?: return
        if (active.id != upId) return
        val catch = train.catchUpSuggestion ?: return
        if (catch.missedSlots.isEmpty()) return
        val onThisDay = catch.missedSlots.any { it.weekNumber == weekNumber && it.dayNumber == dayNumber }
        if (!onThisDay) return
        catchUpDayPromptShown = true
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.catch_up_day_dialog_title))
            .setMessage(catch.message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
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

        val language = currentLanguage
        val currentSlug = item.exerciseSlug
        val currentConfig = currentSlug?.let { exerciseConfigMap[it] }
        val baseList = allExercises.filter { it.fileName != currentSlug }
        val sameCategory = currentConfig?.category?.code?.let { code ->
            baseList.filter { it.category.code == code }
        } ?: baseList

        val apiRecommendedSlugs = mutableListOf<String>()

        fun effectiveRecommendedSlugs(): List<String> {
            val order = LinkedHashSet<String>()
            apiRecommendedSlugs.forEach { s ->
                if (s.isNotBlank() && s != currentSlug) order.add(s)
            }
            return order.toList()
        }

        fun addExerciseRow(ex: ExerciseConfig, container: LinearLayout) {
            val row = layoutInflater.inflate(R.layout.item_simple_exercise_row, null)
            row.findViewById<TextView>(R.id.tvSimpleExerciseName).text =
                ex.name.get(language).ifBlank { ex.name.en }
            row.findViewById<TextView>(R.id.tvSimpleExerciseMeta).text =
                if (ex.supportsWeight) getString(R.string.replace_hint_weighted)
                else getString(R.string.replace_hint_bodyweight)
            row.setOnClickListener {
                val updated = item.copy(exerciseSlug = ex.fileName, deletedExercise = false)
                exerciseNameMap[ex.fileName] = ex.name.get(language).ifBlank { ex.name.en }
                updateItemInSession(sessionId, itemIndex, updated, OverrideSyncKind.REPLACE)
                dialog.dismiss()
            }
            container.addView(row)
        }

        fun buildLists(query: String) {
            val filtered = if (query.isBlank()) {
                sameCategory
            } else {
                sameCategory.filter { ExerciseSearchMatcher.matches(it, query, language) }
            }

            layoutEasier.removeAllViews()
            layoutSimilar.removeAllViews()
            layoutHarder.removeAllViews()

            val recommended = effectiveRecommendedSlugs()
            if (query.isBlank() && recommended.isNotEmpty()) {
                val header = TextView(this).apply {
                    text = getString(R.string.replace_recommended)
                    setTextColor(ContextCompat.getColor(this@ProgramSessionActivity, R.color.text_secondary))
                    textSize = 13f
                }
                layoutEasier.addView(header)
                recommended.forEach { slug ->
                    val ex = allExercises.find { it.fileName == slug } ?: return@forEach
                    addExerciseRow(ex, layoutEasier)
                }
                val altHeader = TextView(this).apply {
                    text = getString(R.string.replace_alternatives)
                    setTextColor(ContextCompat.getColor(this@ProgramSessionActivity, R.color.text_secondary))
                    textSize = 13f
                }
                layoutSimilar.addView(altHeader)
                val recSet = recommended.toSet()
                filtered.filter { it.fileName !in recSet }.forEach { ex -> addExerciseRow(ex, layoutSimilar) }
            } else {
                filtered.forEach { ex -> addExerciseRow(ex, layoutSimilar) }
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

        if (!currentSlug.isNullOrBlank()) {
            val token = AuthManager.getAccessToken(this)
            if (!token.isNullOrBlank()) {
                lifecycleScope.launch {
                    val slugs = withContext(Dispatchers.IO) {
                        try {
                            val r = ApiClient.mobileSyncApi.getSubstitutionExercises(
                                "Bearer $token",
                                currentSlug,
                                24
                            )
                            if (r.isSuccessful && r.body()?.success == true) {
                                r.body()?.data?.mapNotNull { row ->
                                    row.slug.takeIf { it.isNotBlank() }
                                } ?: emptyList()
                            } else {
                                emptyList()
                            }
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }
                    if (!dialog.isShowing) return@launch
                    apiRecommendedSlugs.clear()
                    apiRecommendedSlugs.addAll(slugs)
                    buildLists(inputSearch.text?.toString().orEmpty())
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Edit Dialogs
    // ═══════════════════════════════════════════════════════════

    private fun showEditExerciseDialog(sessionId: String, itemIndex: Int, item: ProgramSessionItem) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_session_item_edit, null)
        val spinnerExercise = dialogView.findViewById<android.widget.AutoCompleteTextView>(R.id.spinnerExercise)
        
        val layoutSets = dialogView.findViewById<View>(R.id.layoutSets)
        val layoutReps = dialogView.findViewById<View>(R.id.layoutReps)
        val layoutDuration = dialogView.findViewById<View>(R.id.layoutDuration)
        val layoutRest = dialogView.findViewById<View>(R.id.layoutRest)
        val layoutWeight = dialogView.findViewById<View>(R.id.layoutWeight)

        val inputSets = dialogView.findViewById<EditText>(R.id.inputSets)
        val inputReps = dialogView.findViewById<EditText>(R.id.inputTargetReps)
        val inputDuration = dialogView.findViewById<EditText>(R.id.inputTargetDuration)
        val inputRestBetween = dialogView.findViewById<EditText>(R.id.inputRestBetweenSets)
        val inputWeight = dialogView.findViewById<EditText>(R.id.inputWeightKg)

        val language = currentLanguage
        val options = allExercises.map { ex ->
            val name = ex.name.get(language).ifBlank { ex.name.en }
            name to ex.fileName
        }
        val labels = options.map { it.first }
        
        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, labels)
        spinnerExercise.setAdapter(adapter)
        
        var selectedExerciseSlug = item.exerciseSlug

        // Helper function to update UI based on selected exercise capabilities
        fun updateVisibilityForExercise(slug: String?) {
            if (slug == null) return
            val exConfig = exerciseConfigMap[slug] ?: return
            
            // If it has duration, show duration, hide reps
            if (exConfig.repCountingConfig.duration != null) {
                layoutReps.visibility = View.GONE
                layoutDuration.visibility = View.VISIBLE
            } else {
                layoutReps.visibility = View.VISIBLE
                layoutDuration.visibility = View.GONE
            }

            // Show weight only if supported
            if (exConfig.supportsWeight) {
                layoutWeight.visibility = View.VISIBLE
            } else {
                layoutWeight.visibility = View.GONE
                inputWeight.setText("")
            }
        }

        // Set initial text and selection
        val currentIndex = options.indexOfFirst { it.second == item.exerciseSlug }
        if (currentIndex >= 0) {
            spinnerExercise.setText(labels[currentIndex], false)
            updateVisibilityForExercise(item.exerciseSlug)
        }

        spinnerExercise.setOnItemClickListener { _, _, position, _ ->
            selectedExerciseSlug = options[position].second
            updateVisibilityForExercise(selectedExerciseSlug)
        }

        inputSets.setText((item.sets ?: 1).toString())
        inputReps.setText(item.targetReps?.toString() ?: "")
        inputDuration.setText(item.targetDuration?.toString() ?: "")
        inputRestBetween.setText(item.restBetweenSetsMs?.let { (it / 1000).toString() } ?: "")
        inputWeight.setText(item.weightKg?.toString() ?: "")

        val bottomSheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        bottomSheet.setContentView(dialogView)

        dialogView.findViewById<MaterialButton>(R.id.btnSave).setOnClickListener {
            val updated = item.copy(
                exerciseSlug = selectedExerciseSlug,
                sets = inputSets.text.toString().toIntOrNull() ?: item.sets,
                targetReps = if (layoutReps.visibility == View.VISIBLE) inputReps.text.toString().toIntOrNull() else null,
                targetDuration = if (layoutDuration.visibility == View.VISIBLE) inputDuration.text.toString().toIntOrNull() else null,
                restBetweenSetsMs = inputRestBetween.text.toString().toLongOrNull()?.times(1000),
                weightKg = if (layoutWeight.visibility == View.VISIBLE) inputWeight.text.toString().toFloatOrNull() else null
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
            Toast.makeText(this, getString(R.string.error_expand_session_first), Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val sheet = layoutInflater.inflate(R.layout.bottom_sheet_add_exercise, null)
        dialog.setContentView(sheet)

        val inputSearch = sheet.findViewById<EditText>(R.id.inputAddExerciseSearch)
        val listContainer = sheet.findViewById<LinearLayout>(R.id.layoutAddExerciseList)
        val language = currentLanguage

        fun renderList(query: String) {
            listContainer.removeAllViews()
            val filtered = if (query.isBlank()) {
                allExercises
            } else {
                allExercises.filter { ex -> ExerciseSearchMatcher.matches(ex, query, language) }
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
            Toast.makeText(this, getString(R.string.error_expand_session_first), Toast.LENGTH_SHORT).show()
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
            Snackbar.make(binding.root, getString(R.string.session_customization_pending), Snackbar.LENGTH_SHORT).show()
            return
        }
        val userProgramId = currentUserProgramId
        if (userProgramId.isNullOrBlank()) {
            Snackbar.make(binding.root, getString(R.string.session_customization_pending), Snackbar.LENGTH_SHORT).show()
            Log.w(TAG, "Skip customization sync: current screen is not bound to an active userProgramId")
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

        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val response = ApiClient.mobileSyncApi.updateUserProgram(
                        userProgramId, "Bearer $token", payload
                    )
                    if (response.isSuccessful) {
                        Log.d(TAG, "Synced customizations to backend for $dayKey")
                        true
                    } else {
                        Log.w(TAG, "Failed to sync customizations: ${response.code()}")
                        false
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to sync customizations to backend: ${e.message}")
                    false
                }
            }
            val msg = if (ok) {
                getString(R.string.session_customization_synced)
            } else {
                getString(R.string.session_customization_pending)
            }
            Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
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
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                ApiClient.mobileSyncApi.startSession(sessionId, "Bearer $token", payload)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to post session start: ${e.message}")
            }
        }
    }

    private fun showPostSessionRpeDialog(onPicked: (Int?) -> Unit) {
        val labels = (1..10).map { it.toString() }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.rpe_label)
            .setItems(labels) { _, which ->
                onPicked(which + 1)
            }
            .setNegativeButton(R.string.rpe_skip) { _, _ -> onPicked(null) }
            .show()
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
        reportJson: String?,
        rpe: Int? = null
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
        if (rpe != null) {
            payloadMap["rpe"] = rpe
        }
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

        lifecycleScope.launch(Dispatchers.IO) {
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

    /**
     * Maps server effective plan to [DayCustomizationStore.CustomizedSession] list.
     * Returns null if payload cannot be fully resolved (missing exercise id in cache).
     */
    private fun mapEffectivePlanToCustomizedSessions(
        payload: EffectivePlanPayload,
        originals: List<ProgramSession>,
        exerciseRepo: ExerciseRepository
    ): List<DayCustomizationStore.CustomizedSession> {
        if (payload.sessions.isEmpty()) return emptyList()
        val origById = originals.associateBy { it.id }
        val out = mutableListOf<DayCustomizationStore.CustomizedSession>()
        for (serverSession in payload.sessions.sortedBy { it.sortOrder }) {
            val orig = origById[serverSession.id]
            val name = localizedFromEffectiveName(serverSession.name, orig?.name)
            val items = mutableListOf<ProgramSessionItem>()
            for (serverItem in serverSession.items.sortedBy { it.sortOrder }) {
                if (serverItem.skipped == true) continue
                when (serverItem.type.lowercase(Locale.US)) {
                    "exercise" -> {
                        val ex = serverItem.exerciseId?.let { exerciseRepo.getExerciseById(it) }
                        val resolvedSets = serverItem.sets ?: serverItem.suggestion?.suggestedSets
                        val resolvedReps = serverItem.targetReps ?: serverItem.suggestion?.suggestedReps
                        val resolvedDuration = serverItem.targetDuration ?: serverItem.suggestion?.suggestedDuration
                        val resolvedWeight = serverItem.weightKg ?: serverItem.suggestion?.suggestedWeightKg
                        if (ex != null) {
                            items.add(
                                ProgramSessionItem(
                                    type = "exercise",
                                    serverItemId = serverItem.id,
                                    exerciseSlug = ex.fileName,
                                    sets = resolvedSets,
                                    targetReps = resolvedReps,
                                    targetDuration = resolvedDuration,
                                    restBetweenSetsMs = serverItem.restBetweenSetsMs?.toLong(),
                                    weightKg = resolvedWeight?.toFloat(),
                                    weightPerSet = serverItem.weightPerSet?.map { v -> v.toFloat() },
                                    notes = notesFromApi(serverItem.notes),
                                    restDurationMs = serverItem.restDurationMs?.toLong(),
                                    suggestionSource = serverItem.suggestion?.source,
                                    sortOrder = serverItem.sortOrder
                                )
                            )
                        } else {
                            val fallbackOriginal = orig
                                ?.items
                                ?.firstOrNull { item ->
                                    item.type == "exercise" && item.sortOrder == serverItem.sortOrder
                                }
                            if (fallbackOriginal != null) {
                                items.add(
                                    fallbackOriginal.copy(
                                        serverItemId = serverItem.id,
                                        sets = resolvedSets ?: fallbackOriginal.sets,
                                        targetReps = resolvedReps ?: fallbackOriginal.targetReps,
                                        targetDuration = resolvedDuration ?: fallbackOriginal.targetDuration,
                                        restBetweenSetsMs = serverItem.restBetweenSetsMs?.toLong()
                                            ?: fallbackOriginal.restBetweenSetsMs,
                                        weightKg = resolvedWeight?.toFloat() ?: fallbackOriginal.weightKg,
                                        weightPerSet = serverItem.weightPerSet?.map { value -> value.toFloat() }
                                            ?: fallbackOriginal.weightPerSet,
                                        notes = notesFromApi(serverItem.notes) ?: fallbackOriginal.notes,
                                        restDurationMs = serverItem.restDurationMs?.toLong()
                                            ?: fallbackOriginal.restDurationMs,
                                        suggestionSource = serverItem.suggestion?.source ?: fallbackOriginal.suggestionSource,
                                        sortOrder = serverItem.sortOrder
                                    )
                                )
                            } else {
                                Log.w(
                                    TAG,
                                    "Skipping unresolved effective-plan item session=${serverSession.id} sortOrder=${serverItem.sortOrder}"
                                )
                            }
                        }
                    }
                    "rest" -> {
                        items.add(
                            ProgramSessionItem(
                                type = "rest",
                                serverItemId = serverItem.id,
                                restDurationMs = serverItem.restDurationMs?.toLong()
                                    ?: ((serverItem.targetDuration ?: 60) * 1000L),
                                suggestionSource = serverItem.suggestion?.source,
                                sortOrder = serverItem.sortOrder
                            )
                        )
                    }
                    else -> { /* ignore unknown line types */ }
                }
            }
            out.add(
                DayCustomizationStore.CustomizedSession(
                    id = serverSession.id,
                    name = name,
                    sortOrder = serverSession.sortOrder,
                    role = serverSession.role?.trim()?.takeIf { it.isNotEmpty() }
                        ?: orig?.role?.trim()?.takeIf { it.isNotEmpty() }
                        ?: "MAIN",
                    estimatedDurationMin = serverSession.estimatedDurationMin,
                    items = items
                )
            )
        }
        return out
    }

    private fun formatSuggestionSource(source: String?): String? {
        return when (source) {
            "progression_state" -> getString(R.string.training_suggestion_progression)
            "template" -> getString(R.string.training_suggestion_template)
            "goal_default" -> getString(R.string.training_suggestion_goal_default)
            else -> null
        }
    }

    private fun syncReplaceOverride(originalItem: ProgramSessionItem?, updatedItem: ProgramSessionItem) {
        val slug = updatedItem.exerciseSlug ?: return
        val exerciseRepo = ExerciseRepository.getInstance(this)
        val exerciseId = exerciseRepo.getExerciseServerId(slug) ?: return
        syncOverride(
            sessionItemId = originalItem?.serverItemId,
            overrideType = "REPLACE_EXERCISE",
            data = mapOf("exerciseId" to exerciseId)
        )
    }

    private fun syncAdjustOverride(originalItem: ProgramSessionItem?, updatedItem: ProgramSessionItem) {
        if (originalItem?.serverItemId.isNullOrBlank()) return

        val data = mutableMapOf<String, Any?>()
        updatedItem.sets?.let { data["sets"] = it }
        updatedItem.targetReps?.let { data["targetReps"] = it }
        updatedItem.targetDuration?.let { data["targetDuration"] = it }
        updatedItem.restBetweenSetsMs?.let { data["restBetweenSetsMs"] = it }
        updatedItem.restDurationMs?.let { data["restDurationMs"] = it }
        updatedItem.weightKg?.let { data["weightKg"] = it }

        val slug = updatedItem.exerciseSlug
        if (!slug.isNullOrBlank()) {
            ExerciseRepository.getInstance(this).getExerciseServerId(slug)?.let { data["exerciseId"] = it }
        }

        syncOverride(
            sessionItemId = originalItem.serverItemId,
            overrideType = "ADJUST_PRESCRIPTION",
            data = data
        )
    }

    private fun syncSkipOverride(item: ProgramSessionItem?) {
        syncOverride(
            sessionItemId = item?.serverItemId,
            overrideType = "SKIP_ITEM",
            data = null
        )
    }

    private fun syncAddOverride(anchorItem: ProgramSessionItem?, addedItem: ProgramSessionItem) {
        val anchorId = anchorItem?.serverItemId ?: return
        val data = mutableMapOf<String, Any?>(
            "type" to addedItem.type
        )

        if (addedItem.type == "exercise") {
            val slug = addedItem.exerciseSlug ?: return
            val exerciseId = ExerciseRepository.getInstance(this).getExerciseServerId(slug) ?: return
            data["exerciseId"] = exerciseId
            addedItem.sets?.let { data["sets"] = it }
            addedItem.targetReps?.let { data["targetReps"] = it }
            addedItem.targetDuration?.let { data["targetDuration"] = it }
            addedItem.restBetweenSetsMs?.let { data["restBetweenSetsMs"] = it }
            addedItem.weightKg?.let { data["weightKg"] = it }
        } else {
            addedItem.restDurationMs?.let { data["restDurationMs"] = it }
        }

        syncOverride(
            sessionItemId = anchorId,
            overrideType = "ADD_ITEM",
            data = data
        )
    }

    private fun syncOverride(
        sessionItemId: String?,
        overrideType: String,
        data: Map<String, Any?>?
    ) {
        val userProgramId = currentUserProgramId
        val token = AuthManager.getAccessToken(this)
        if (sessionItemId.isNullOrBlank() || userProgramId.isNullOrBlank() || token.isNullOrBlank()) {
            return
        }

        val body = mutableMapOf<String, Any?>(
            "weekNumber" to weekNumber,
            "dayNumber" to dayNumber,
            "sessionItemId" to sessionItemId,
            "overrideType" to overrideType,
            "reasonCode" to "PREFERENCE"
        )
        if (data != null) {
            body["data"] = data
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = ApiClient.mobileSyncApi.createUserProgramOverride(
                    userProgramId,
                    "Bearer $token",
                    body
                )
                if (!response.isSuccessful) {
                    Log.w(TAG, "Override sync failed type=$overrideType code=${response.code()}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Override sync error type=$overrideType", e)
            }
        }
    }

    private fun getCompletedSessionIds(): Set<String> {
        val pid = programId ?: return emptySet()
        val reports = reportStore.getByDay(pid, weekNumber, dayNumber)
        return reports.map { it.sessionId }.toSet()
    }

    private fun getLocalizedName(text: LocalizedText): String {
        val language = currentLanguage
        return when (language) {
            "ar" -> text.ar.ifBlank { text.en }
            else -> text.en.ifBlank { text.ar }
        }
    }


    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
