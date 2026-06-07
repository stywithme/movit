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
import com.trainingvalidator.poc.databinding.ActivityProgramWorkoutBinding
import com.trainingvalidator.poc.network.ApiClient
import com.trainingvalidator.poc.network.EffectivePlanPayload
import com.trainingvalidator.poc.storage.AuthManager
import com.trainingvalidator.poc.storage.DayCustomizationStore
import com.trainingvalidator.poc.storage.HomeRepository
import com.trainingvalidator.poc.storage.ExerciseRepository
import com.trainingvalidator.poc.storage.ProgramRepository
import com.trainingvalidator.poc.storage.ProgramWorkoutReportStore
import com.trainingvalidator.poc.training.config.SettingsManager
import com.trainingvalidator.poc.training.models.ExerciseConfig
import com.trainingvalidator.poc.training.models.LocalizedText
import com.trainingvalidator.poc.training.models.PerSetValues
import com.trainingvalidator.poc.training.models.PlannedWorkoutItemType
import com.trainingvalidator.poc.training.models.wireValue
import com.trainingvalidator.poc.training.models.ProgramConfig
import com.trainingvalidator.poc.training.models.ProgramDay
import com.trainingvalidator.poc.training.models.ProgramWorkout
import com.trainingvalidator.poc.training.models.WorkoutLineItem
import androidx.lifecycle.lifecycleScope
import com.trainingvalidator.poc.ui.train.TrainingActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.max

/**
 * ProgramWorkoutActivity ? Full Day View
 *
 * Shows ALL planned workouts for the current day with:
 * - Completed workouts collapsed with status
 * - Current planned workout expanded with full timeline
 * - Upcoming workouts collapsed, expandable for browsing
 * - Full customization: reorder/delete planned workouts, edit items
 * - Offline-first persistence via DayCustomizationStore
 */
class ProgramWorkoutActivity : AppCompatActivity() {

    private enum class OverrideSyncKind {
        ADJUST,
        REPLACE
    }

    companion object {
        const val EXTRA_PROGRAM_SLUG = "program_slug"
        const val EXTRA_PROGRAM_ID = "program_id"
        const val EXTRA_WEEK_NUMBER = "week_number"
        const val EXTRA_DAY_NUMBER = "day_number"
        const val EXTRA_TARGET_WORKOUT_ID = "target_workout_id"
        private const val TAG = "ProgramWorkoutActivity"
    }

    // -----------------------------------------------------------
    // State
    // -----------------------------------------------------------

    private lateinit var binding: ActivityProgramWorkoutBinding

    private var program: ProgramConfig? = null
    private var day: ProgramDay? = null
    private var weekNumber: Int = 1
    private var dayNumber: Int = 1
    private var programId: String? = null
    private var programSlug: String? = null
    private var targetPlannedWorkoutId: String? = null
    private var currentUserProgramId: String? = null

    /** Current list of customized planned workouts (the source of truth for display) */
    private var plannedWorkouts = mutableListOf<DayCustomizationStore.CustomizedPlannedWorkout>()

    /** Track which planned workout cards are expanded */
    private val expandedPlannedWorkoutIds = mutableSetOf<String>()

    /** Edit mode flag */
    private var isEditMode = false

    // Stores
    private val customizationStore by lazy { DayCustomizationStore(this) }
    private val reportStore by lazy { ProgramWorkoutReportStore(this) }

    // Exercise data
    private var exerciseConfigMap = mutableMapOf<String, ExerciseConfig>()
    private var exerciseNameMap = mutableMapOf<String, String>()
    private var allExercises: List<ExerciseConfig> = emptyList()

    // Workout run launcher
    private val workoutLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleWorkoutResult(result.resultCode, result.data)
    }

    /** The planned workout ID that was just launched for training */
    private var launchedPlannedWorkoutId: String? = null

    /** One catch-up hint per activity instance when this day is in missedSlots. */
    private var catchUpDayPromptShown = false

    // -----------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProgramWorkoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        loadDay()
    }

    // -----------------------------------------------------------
    // Setup
    // -----------------------------------------------------------

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnSkipWarmup.setOnClickListener { skipWarmupScrollToMainWork() }
        binding.btnEditMode.setOnClickListener { toggleEditMode() }
        binding.btnStartWorkout.setOnClickListener { startNextPlannedWorkout() }
        binding.btnAddExercise.setOnClickListener { showAddExerciseSheet() }
        binding.btnAddRest.setOnClickListener { showAddRestSheet() }
    }

    private fun toggleEditMode() {
        isEditMode = !isEditMode
        binding.btnEditMode.text = if (isEditMode) getString(R.string.ds_done) else getString(R.string.ds_edit_mode)
        binding.layoutEditActions.visibility = if (isEditMode) View.VISIBLE else View.GONE
        renderAllPlannedWorkouts()
    }

    // -----------------------------------------------------------
    // Data Loading
    // -----------------------------------------------------------

    private fun loadDay() {
        programSlug = intent.getStringExtra(EXTRA_PROGRAM_SLUG)
        programId = intent.getStringExtra(EXTRA_PROGRAM_ID)
        weekNumber = intent.getIntExtra(EXTRA_WEEK_NUMBER, 1)
        dayNumber = intent.getIntExtra(EXTRA_DAY_NUMBER, 1)
        targetPlannedWorkoutId = intent.getStringExtra(EXTRA_TARGET_WORKOUT_ID)

        if (programSlug.isNullOrBlank() && programId.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.error_program_not_found), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        lifecycleScope.launch {
            val programRepo = ProgramRepository.getInstance(this@ProgramWorkoutActivity)
            val exerciseRepo = ExerciseRepository.getInstance(this@ProgramWorkoutActivity)
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
                Toast.makeText(this@ProgramWorkoutActivity, getString(R.string.error_program_not_found), Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this@ProgramWorkoutActivity, getString(R.string.error_day_not_found), Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            day = resolvedDay

            // Load effective planned workouts: local customization wins; else server effective plan (progression + overrides)
            val basePlannedWorkouts = customizationStore.getEffectivePlannedWorkouts(
                programId = resolvedProgram.id,
                weekNumber = weekNumber,
                dayNumber = dayNumber,
                originalWorkouts = resolvedDay.workouts
            )
            var effectivePlannedWorkouts = basePlannedWorkouts

            val activeUp = programRepo.getActiveUserProgramExport()
            val token = AuthManager.getAccessToken(this@ProgramWorkoutActivity)
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
                            mapEffectivePlanToCustomizedPlannedWorkouts(body.data, resolvedDay.workouts, exerciseRepo)
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "getEffectivePlan failed", e)
                        null
                    }
                }
                if (mapped != null) {
                    effectivePlannedWorkouts = mapped
                }
            }

            plannedWorkouts.clear()
            plannedWorkouts.addAll(effectivePlannedWorkouts)

            // Auto-expand the target planned workout or the first non-completed one
            determineExpandedPlannedWorkouts()

            // Render
            renderHeader()
            renderAllPlannedWorkouts()
            updateBottomBar()
            maybeShowCatchUpForThisDay()
        }
    }

    private fun determineExpandedPlannedWorkouts() {
        expandedPlannedWorkoutIds.clear()
        val completedPlannedWorkoutIds = getCompletedPlannedWorkoutIds()

        val target = targetPlannedWorkoutId
        if (target != null && plannedWorkouts.any { it.id == target }) {
            expandedPlannedWorkoutIds.add(target)
        } else {
            // Expand the first non-completed planned workout
            val firstActive = plannedWorkouts.firstOrNull { it.id !in completedPlannedWorkoutIds }
            if (firstActive != null) {
                expandedPlannedWorkoutIds.add(firstActive.id)
            }
        }
    }

    // -----------------------------------------------------------
    // Rendering
    // -----------------------------------------------------------

    private fun renderHeader() {
        val dayName = day?.name?.let { getLocalizedName(it) } ?: ""
        binding.tvDayTitle.text = if (dayName.isNotBlank()) {
            getString(R.string.ds_day_title_format, dayNumber, dayName)
        } else {
            "Day $dayNumber"
        }
        binding.tvDaySubtitle.text = getString(R.string.ds_day_subtitle_format, weekNumber, plannedWorkouts.size)

        // Show modified indicator
        if (customizationStore.hasCustomization(programId ?: "", weekNumber, dayNumber)) {
            binding.tvDaySubtitle.text = "${binding.tvDaySubtitle.text}  ${getString(R.string.ds_modified_hint)}"
        }
    }

    private fun renderAllPlannedWorkouts() {
        binding.layoutWorkouts.removeAllViews()

        if (plannedWorkouts.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = getString(R.string.ds_no_planned_workouts_message)
                textSize = 16f
                setTextColor(ContextCompat.getColor(context, R.color.text_hint))
                setPadding(0, dpToPx(48), 0, 0)
                gravity = android.view.Gravity.CENTER
            }
            binding.layoutWorkouts.addView(emptyView)
            updateSkipWarmupButtonVisibility()
            return
        }

        val completedPlannedWorkoutIds = getCompletedPlannedWorkoutIds()

        // First non-completed planned workout is the "current" one
        val currentPlannedWorkoutId = plannedWorkouts.firstOrNull {
            it.id !in completedPlannedWorkoutIds && !it.isDeleted
        }?.id

        plannedWorkouts.forEachIndexed { index, plannedWorkout ->
            val isCompleted = plannedWorkout.id in completedPlannedWorkoutIds
            val isExpanded = plannedWorkout.id in expandedPlannedWorkoutIds
            val isCurrentPlannedWorkout = plannedWorkout.id == currentPlannedWorkoutId

            val view = renderPlannedWorkoutCard(plannedWorkout, index, isCompleted, isCurrentPlannedWorkout, isExpanded)
            binding.layoutWorkouts.addView(view)
        }
        updateSkipWarmupButtonVisibility()
    }

    private fun renderPlannedWorkoutCard(
        plannedWorkout: DayCustomizationStore.CustomizedPlannedWorkout,
        index: Int,
        isCompleted: Boolean,
        isCurrent: Boolean,
        isExpanded: Boolean
    ): View {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.item_day_workout_card, binding.layoutWorkouts, false)
        view.tag = plannedWorkout.id

        val card = view.findViewById<MaterialCardView>(R.id.cardWorkout)
        val statusDot = view.findViewById<View>(R.id.viewStatusDot)
        val tvName = view.findViewById<TextView>(R.id.tvWorkoutName)
        val tvMeta = view.findViewById<TextView>(R.id.tvWorkoutMeta)
        val tvStatus = view.findViewById<TextView>(R.id.tvWorkoutStatus)
        val ivExpand = view.findViewById<ImageView>(R.id.ivExpandIcon)
        val layoutExpanded = view.findViewById<LinearLayout>(R.id.layoutExpandedContent)
        val layoutHeader = view.findViewById<LinearLayout>(R.id.layoutWorkoutHeader)
        val rvTimeline = view.findViewById<RecyclerView>(R.id.rvTimelineItems)
        val layoutEditBar = view.findViewById<LinearLayout>(R.id.layoutWorkoutEditBar)
        val btnRename = view.findViewById<MaterialButton>(R.id.btnRenameWorkout)
        val btnDelete = view.findViewById<MaterialButton>(R.id.btnDeleteWorkout)
        val btnMoveUp = view.findViewById<ImageButton>(R.id.btnMoveWorkoutUp)
        val btnMoveDown = view.findViewById<ImageButton>(R.id.btnMoveWorkoutDown)

        // --- Planned workout name ---
        tvName.text = getLocalizedName(plannedWorkout.name)

        // --- Meta info ---
        val exerciseCount = plannedWorkout.items.count { it.type == PlannedWorkoutItemType.EXERCISE }
        val backendMin = plannedWorkout.estimatedDurationMin
        val estimatedMinutes = backendMin ?: estimatePlannedWorkoutDuration(plannedWorkout.items)
        val baseMeta = getString(R.string.ds_exercises_meta_format, exerciseCount, estimatedMinutes)
        tvMeta.text = if (backendMin != null) {
            "$baseMeta ? " + getString(R.string.workout_duration_badge, backendMin)
        } else {
            baseMeta
        }

        // --- Status dot & badge ---
        val dotColor: Int
        if (isCompleted) {
            dotColor = ContextCompat.getColor(this, R.color.success)
            tvStatus.text = getString(R.string.ds_workout_completed)
            tvStatus.visibility = View.VISIBLE
            tvStatus.backgroundTintList = ContextCompat.getColorStateList(this, R.color.success)
            card.alpha = 0.7f
        } else if (isCurrent) {
            dotColor = ContextCompat.getColor(this, R.color.primary)
            tvStatus.text = getString(R.string.ds_workout_up_next)
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
                expandedPlannedWorkoutIds.remove(plannedWorkout.id)
            } else {
                expandedPlannedWorkoutIds.add(plannedWorkout.id)
            }
            renderAllPlannedWorkouts()
        }

        // --- Timeline items (RecyclerView with drag-and-drop) ---
        if (isExpanded) {
            setupTimelineRecyclerView(rvTimeline, plannedWorkout)
        }

        // --- Edit bar ---
        layoutEditBar.visibility = if (isEditMode && isExpanded) View.VISIBLE else View.GONE

        btnRename.setOnClickListener { showRenameDialog(plannedWorkout) }
        btnDelete.setOnClickListener { showDeletePlannedWorkoutDialog(plannedWorkout) }
        btnMoveUp.setOnClickListener { movePlannedWorkout(index, -1) }
        btnMoveDown.setOnClickListener { movePlannedWorkout(index, 1) }
        btnMoveUp.visibility = if (index > 0) View.VISIBLE else View.INVISIBLE
        btnMoveDown.visibility = if (index < plannedWorkouts.size - 1) View.VISIBLE else View.INVISIBLE

        return view
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTimelineRecyclerView(
        recyclerView: RecyclerView,
        plannedWorkout: DayCustomizationStore.CustomizedPlannedWorkout
    ) {
        val completedPlannedWorkoutIds = getCompletedPlannedWorkoutIds()
        val isPlannedWorkoutCompleted = plannedWorkout.id in completedPlannedWorkoutIds

        val adapter = TimelineItemAdapter(
            items = plannedWorkout.items.toMutableList(),
            workoutId = plannedWorkout.id,
            isPlannedWorkoutCompleted = isPlannedWorkoutCompleted,
            totalCount = plannedWorkout.items.size
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
                // Persist after drag ends ? sortOrder is normalized inside persistCustomizations()
                val plannedWorkoutIndex = plannedWorkouts.indexOfFirst { it.id == plannedWorkout.id }
                if (plannedWorkoutIndex >= 0) {
                    val reorderedItems = adapter.items.toList()
                    Log.d(TAG, "Drag ended: plannedWorkout=${plannedWorkout.name.en}, items reordered (${reorderedItems.size})")
                    plannedWorkouts[plannedWorkoutIndex] = plannedWorkouts[plannedWorkoutIndex].copy(items = reorderedItems)
                    persistCustomizations()
                }
            }
        })
        touchHelper.attachToRecyclerView(recyclerView)
        adapter.touchHelper = touchHelper
    }

    // -----------------------------------------------------------
    // Timeline Item Adapter (with drag-and-drop support)
    // -----------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    private inner class TimelineItemAdapter(
        val items: MutableList<WorkoutLineItem>,
        private val workoutId: String,
        private val isPlannedWorkoutCompleted: Boolean,
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
                .inflate(R.layout.item_workout_timeline_item, parent, false)
            return VH(view)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            val isRest = item.type == PlannedWorkoutItemType.REST
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
            if (isPlannedWorkoutCompleted) {
                dotColor = ContextCompat.getColor(this@ProgramWorkoutActivity, R.color.success)
                itemBgColor = ContextCompat.getColor(this@ProgramWorkoutActivity, R.color.surface)
            } else if (position == 0) {
                dotColor = ContextCompat.getColor(this@ProgramWorkoutActivity, R.color.primary)
                itemBgColor = ContextCompat.getColor(this@ProgramWorkoutActivity, R.color.surface)
            } else {
                dotColor = ContextCompat.getColor(this@ProgramWorkoutActivity, R.color.text_hint)
                itemBgColor = ContextCompat.getColor(this@ProgramWorkoutActivity, R.color.surface_variant)
            }
            (holder.viewDot.background as? GradientDrawable)?.setColor(dotColor)
            holder.cardItem.setCardBackgroundColor(itemBgColor)

            // --- Image / Icon ---
            if (isRest) {
                holder.ivItemImage.visibility = View.GONE
                holder.ivIcon.visibility = View.VISIBLE
                holder.ivIcon.setImageResource(R.drawable.ic_rest)
                holder.ivIcon.setColorFilter(ContextCompat.getColor(this@ProgramWorkoutActivity, R.color.text_hint))
            } else {
                val slug = item.exerciseSlug ?: ""
                if (unavailable) {
                    holder.ivItemImage.visibility = View.GONE
                    holder.ivIcon.visibility = View.VISIBLE
                    holder.ivIcon.setImageResource(R.drawable.ic_exercise)
                    holder.ivIcon.setColorFilter(ContextCompat.getColor(this@ProgramWorkoutActivity, R.color.warning))
                    holder.cardItem.strokeWidth = dpToPx(2)
                    holder.cardItem.setStrokeColor(
                        ContextCompat.getColorStateList(this@ProgramWorkoutActivity, R.color.warning)
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
                            ContextCompat.getColor(this@ProgramWorkoutActivity, R.color.primary)
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
                val showSectionHeader = item.type == PlannedWorkoutItemType.EXERCISE && (
                    position == 0 ||
                        normalizeRoleKey(item.phaseRole) != normalizeRoleKey(items.getOrNull(position - 1)?.phaseRole)
                    )
                val sectionPrefix = if (showSectionHeader) {
                    formatRoleTitle(normalizeRoleKey(item.phaseRole ?: "MAIN")) + "\n"
                } else ""
                holder.tvDetail.text = sectionPrefix + buildExerciseDetailText(item)
                if (unavailable) {
                    holder.cardItem.setOnClickListener {
                        val pos = holder.bindingAdapterPosition
                        if (pos != RecyclerView.NO_POSITION) {
                            showReplaceExerciseSheet(workoutId, pos, items[pos])
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
                        showReplaceExerciseSheet(workoutId, pos, items[pos])
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
                        if (currentItem.type == PlannedWorkoutItemType.REST) showEditRestDialog(workoutId, pos, currentItem)
                        else showEditExerciseDialog(workoutId, pos, currentItem)
                    }
                }
                holder.btnDeleteItem.setOnClickListener {
                    val pos = holder.bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        showDeleteItemDialog(workoutId, pos)
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

    private fun normalizeRoleKey(role: String?): String =
        when (role?.uppercase(Locale.US)) {
            "WARMUP", "ACTIVATION" -> "WARMUP"
            "MAIN", "ACCESSORY", "CORRECTIVE" -> "MAIN"
            "COOLDOWN" -> "COOLDOWN"
            else -> "OTHER"
        }

    private fun formatRoleTitle(key: String): String = when (key) {
        "WARMUP" -> getString(R.string.section_warmup)
        "MAIN" -> getString(R.string.section_main)
        "COOLDOWN" -> getString(R.string.section_cooldown)
        else -> getString(R.string.section_other)
    }

    private fun buildExerciseDetailText(item: WorkoutLineItem): String {
        val sets = item.sets ?: 1
        val weight = PerSetValues.floatAt(item.weightPerSet, 1, sets)

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
        val completedPlannedWorkoutIds = getCompletedPlannedWorkoutIds()
        val hasNextPlannedWorkout = plannedWorkouts.any { it.id !in completedPlannedWorkoutIds }

        if (hasNextPlannedWorkout) {
            binding.btnStartWorkout.text = getString(R.string.ds_start_planned_workout)
            binding.btnStartWorkout.isEnabled = true
            // Always use startNextPlannedWorkout() to read from the current plannedWorkouts list.
            // Never capture a planned workout reference here — it would become stale after edits.
            binding.btnStartWorkout.setOnClickListener { startNextPlannedWorkout() }
        } else {
            binding.btnStartWorkout.text = getString(R.string.ds_workout_completed)
            binding.btnStartWorkout.isEnabled = false
        }
    }

    // -----------------------------------------------------------
    // Planned workout actions
    // -----------------------------------------------------------

    private fun startNextPlannedWorkout() {
        val completedPlannedWorkoutIds = getCompletedPlannedWorkoutIds()
        val nextPlannedWorkout = plannedWorkouts.firstOrNull { it.id !in completedPlannedWorkoutIds }
        if (nextPlannedWorkout != null) {
            startPlannedWorkout(nextPlannedWorkout)
        }
    }

    private fun startPlannedWorkout(plannedWorkout: DayCustomizationStore.CustomizedPlannedWorkout) {
        if (plannedWorkout.items.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_no_exercises), Toast.LENGTH_SHORT).show()
            return
        }

        val firstBadIdx = plannedWorkout.items.indexOfFirst {
            it.type == PlannedWorkoutItemType.EXERCISE && (it.deletedExercise == true || it.exerciseSlug.isNullOrBlank())
        }
        if (firstBadIdx >= 0) {
            expandedPlannedWorkoutIds.add(plannedWorkout.id)
            renderAllPlannedWorkouts()
            Toast.makeText(this, getString(R.string.exercise_unavailable_substitute), Toast.LENGTH_LONG).show()
            binding.scrollContent.post {
                showReplaceExerciseSheet(plannedWorkout.id, firstBadIdx, plannedWorkout.items[firstBadIdx])
            }
            return
        }

        // Save customizations before starting
        persistCustomizations()

        launchedPlannedWorkoutId = plannedWorkout.id
        sendPlannedWorkoutStart(plannedWorkout.id)

        val gson = com.google.gson.Gson()
        val itemsJson = gson.toJson(plannedWorkout.items)

        val intent = Intent(this, TrainingActivity::class.java).apply {
            putExtra(TrainingActivity.EXTRA_IS_WORKOUT_MODE, true)
            putExtra(TrainingActivity.EXTRA_WORKOUT_ITEMS_JSON, itemsJson)
            putExtra(TrainingActivity.EXTRA_TRAINING_MODE, TrainingActivity.MODE_CAMERA)
        }

        workoutLauncher.launch(intent)
    }

    private fun handleWorkoutResult(resultCode: Int, data: Intent?) {
        Log.d(TAG, "Workout run result code: $resultCode")

        if (resultCode == RESULT_OK && data != null) {
            val completedSets = data.getIntExtra(TrainingActivity.RESULT_WORKOUT_SETS_COMPLETED, 0)
            val totalSets = data.getIntExtra(TrainingActivity.RESULT_WORKOUT_SETS_PLANNED, 0)
            val durationMs = data.getLongExtra(TrainingActivity.RESULT_DURATION_MS, 0L)
            val totalReps = data.getIntExtra(TrainingActivity.RESULT_WORKOUT_TOTAL_REPS, 0)
            val avgAccuracy = data.getFloatExtra(TrainingActivity.RESULT_WORKOUT_AVG_ACCURACY, 0f)
            val avgFormScore = data.getFloatExtra(TrainingActivity.RESULT_WORKOUT_AVG_FORM_SCORE, 0f)
            val reportJson = data.getStringExtra(TrainingActivity.RESULT_WORKOUT_REPORT_JSON)
            val reportIds = data.getStringArrayListExtra(TrainingActivity.RESULT_WORKOUT_REPORT_IDS)
            val plannedWorkoutId = launchedPlannedWorkoutId

            // Save local report (with form score)
            saveLocalWorkoutReport(plannedWorkoutId, durationMs, totalSets, completedSets, totalReps, avgAccuracy, avgFormScore, reportJson)
            determineExpandedPlannedWorkouts()
            renderAllPlannedWorkouts()
            updateBottomBar()

            showPostWorkoutRpeDialog { rpe ->
                // Send to backend (single call, with offline queue)
                sendPlannedWorkoutComplete(
                    plannedWorkoutId ?: "",
                    durationMs,
                    plannedWorkouts.firstOrNull { it.id == plannedWorkoutId }?.items?.size ?: 0,
                    totalSets,
                    completedSets,
                    totalReps,
                    avgAccuracy,
                    avgFormScore,
                    reportJson,
                    rpe
                )

                // Refresh UI (already updated after save; repeat is harmless)
                determineExpandedPlannedWorkouts()
                renderAllPlannedWorkouts()
                updateBottomBar()

                // Navigate to unified workout report
                val reportIntent = com.trainingvalidator.poc.ui.report.WorkoutReportActivity.createWorkoutIntent(
                    context = this,
                    reportIds = reportIds ?: emptyList(),
                    workoutReportJson = reportJson
                )
                startActivity(reportIntent)
            }
        }
    }

    // -----------------------------------------------------------
    // Customization: planned workout operations
    // -----------------------------------------------------------

    private fun showRenameDialog(plannedWorkout: DayCustomizationStore.CustomizedPlannedWorkout) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_rename_planned_workout, null)
        val inputEn = dialogView.findViewById<EditText>(R.id.inputNameEn)
        val inputAr = dialogView.findViewById<EditText>(R.id.inputNameAr)

        inputEn.setText(plannedWorkout.name.en)
        inputAr.setText(plannedWorkout.name.ar)

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.ds_rename_workout_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.ds_save)) { _, _ ->
                val newName = LocalizedText(
                    en = inputEn.text.toString().ifBlank { plannedWorkout.name.en },
                    ar = inputAr.text.toString().ifBlank { plannedWorkout.name.ar }
                )
                val updatedPlannedWorkouts = plannedWorkouts.map {
                    if (it.id == plannedWorkout.id) it.copy(name = newName) else it
                }
                plannedWorkouts.clear()
                plannedWorkouts.addAll(updatedPlannedWorkouts)
                persistCustomizations()
                renderAllPlannedWorkouts()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showDeletePlannedWorkoutDialog(plannedWorkout: DayCustomizationStore.CustomizedPlannedWorkout) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.ds_delete_workout_title))
            .setMessage(getString(R.string.ds_delete_workout_message))
            .setPositiveButton(getString(R.string.ds_delete)) { _, _ ->
                plannedWorkouts.removeAll { it.id == plannedWorkout.id }
                expandedPlannedWorkoutIds.remove(plannedWorkout.id)
                persistCustomizations()
                renderHeader()
                renderAllPlannedWorkouts()
                updateBottomBar()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun movePlannedWorkout(fromIndex: Int, direction: Int) {
        val toIndex = fromIndex + direction
        if (toIndex < 0 || toIndex >= plannedWorkouts.size) return

        val temp = plannedWorkouts[fromIndex]
        plannedWorkouts[fromIndex] = plannedWorkouts[toIndex]
        plannedWorkouts[toIndex] = temp

        // Update sort orders
        plannedWorkouts.forEachIndexed { index, plannedWorkout ->
            plannedWorkouts[index] = plannedWorkout.copy(sortOrder = index)
        }

        persistCustomizations()
        renderAllPlannedWorkouts()
    }

    // -----------------------------------------------------------
    // Customization: Item Operations
    // -----------------------------------------------------------

    private fun updateItemInPlannedWorkout(
        workoutId: String,
        itemIndex: Int,
        updatedItem: WorkoutLineItem,
        overrideSyncKind: OverrideSyncKind = OverrideSyncKind.ADJUST
    ) {
        val plannedWorkoutIndex = plannedWorkouts.indexOfFirst { it.id == workoutId }
        if (plannedWorkoutIndex < 0) return

        val plannedWorkout = plannedWorkouts[plannedWorkoutIndex]
        val updatedItems = plannedWorkout.items.toMutableList()
        var originalItem: WorkoutLineItem? = null
        if (itemIndex >= 0 && itemIndex < updatedItems.size) {
            originalItem = updatedItems[itemIndex]
            updatedItems[itemIndex] = updatedItem
        }
        plannedWorkouts[plannedWorkoutIndex] = plannedWorkout.copy(items = updatedItems)
        persistCustomizations()
        when (overrideSyncKind) {
            OverrideSyncKind.REPLACE -> syncReplaceOverride(originalItem, updatedItem)
            OverrideSyncKind.ADJUST -> syncAdjustOverride(originalItem, updatedItem)
        }
        renderAllPlannedWorkouts()
    }

    private fun removeItemFromPlannedWorkout(workoutId: String, itemIndex: Int) {
        val plannedWorkoutIndex = plannedWorkouts.indexOfFirst { it.id == workoutId }
        if (plannedWorkoutIndex < 0) return

        val plannedWorkout = plannedWorkouts[plannedWorkoutIndex]
        val updatedItems = plannedWorkout.items.toMutableList()
        var removedItem: WorkoutLineItem? = null
        if (itemIndex >= 0 && itemIndex < updatedItems.size) {
            removedItem = updatedItems.removeAt(itemIndex)
        }
        plannedWorkouts[plannedWorkoutIndex] = plannedWorkout.copy(items = updatedItems)
        persistCustomizations()
        syncSkipOverride(removedItem)
        renderAllPlannedWorkouts()
    }

    private fun addItemToPlannedWorkout(workoutId: String, item: WorkoutLineItem) {
        val plannedWorkoutIndex = plannedWorkouts.indexOfFirst { it.id == workoutId }
        if (plannedWorkoutIndex < 0) return

        val plannedWorkout = plannedWorkouts[plannedWorkoutIndex]
        val updatedItems = plannedWorkout.items.toMutableList()
        val anchorItem = updatedItems.lastOrNull { !it.serverItemId.isNullOrBlank() }
        val appendedItem = item.copy(sortOrder = updatedItems.size)
        updatedItems.add(appendedItem)
        plannedWorkouts[plannedWorkoutIndex] = plannedWorkout.copy(items = updatedItems)
        persistCustomizations()
        syncAddOverride(anchorItem, appendedItem)
        renderAllPlannedWorkouts()
    }

    private data class PlannedWorkoutExerciseRef(
        val workoutId: String,
        val itemIndex: Int,
        val item: WorkoutLineItem
    )

    private fun allExerciseRefs(): List<PlannedWorkoutExerciseRef> {
        val refs = mutableListOf<PlannedWorkoutExerciseRef>()
        for (plannedWorkout in plannedWorkouts.filter { !it.isDeleted }.sortedBy { it.sortOrder }) {
            plannedWorkout.items.forEachIndexed { index, item ->
                if (item.type == PlannedWorkoutItemType.EXERCISE && !item.exerciseSlug.isNullOrBlank()) {
                    refs.add(PlannedWorkoutExerciseRef(plannedWorkout.id, index, item))
                }
            }
        }
        return refs
    }

    private fun isMainWorkRole(role: String?): Boolean {
        return when (normalizeRoleKey(role)) {
            "MAIN", "OTHER" -> true
            else -> false
        }
    }

    private fun isWarmupRole(role: String?): Boolean {
        return normalizeRoleKey(role) == "WARMUP"
    }

    private fun hasWarmupBeforeFirstMainWork(): Boolean {
        val refs = allExerciseRefs()
        val firstMainIdx = refs.indexOfFirst { isMainWorkRole(it.item.phaseRole) }
        if (firstMainIdx <= 0) return false
        return refs.take(firstMainIdx).any { isWarmupRole(it.item.phaseRole) }
    }

    private fun firstMainWorkExerciseRef(): PlannedWorkoutExerciseRef? {
        return allExerciseRefs().firstOrNull { isMainWorkRole(it.item.phaseRole) }
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
        expandedPlannedWorkoutIds.clear()
        expandedPlannedWorkoutIds.add(target.workoutId)
        renderAllPlannedWorkouts()
        binding.scrollContent.post {
            scrollPlannedWorkoutCardAndTimelineTo(target.workoutId, target.itemIndex)
        }
    }

    private fun scrollPlannedWorkoutCardAndTimelineTo(workoutId: String, itemIndex: Int) {
        for (i in 0 until binding.layoutWorkouts.childCount) {
            val card = binding.layoutWorkouts.getChildAt(i)
            if (card.tag != workoutId) continue
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

    // -----------------------------------------------------------
    // Replace Exercise (swap icon ? always visible)
    // -----------------------------------------------------------

    private fun showReplaceExerciseSheet(workoutId: String, itemIndex: Int, item: WorkoutLineItem) {
        if (item.type != PlannedWorkoutItemType.EXERCISE) return

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
                updateItemInPlannedWorkout(workoutId, itemIndex, updated, OverrideSyncKind.REPLACE)
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
                    setTextColor(ContextCompat.getColor(this@ProgramWorkoutActivity, R.color.text_secondary))
                    textSize = 13f
                }
                layoutEasier.addView(header)
                recommended.forEach { slug ->
                    val ex = allExercises.find { it.fileName == slug } ?: return@forEach
                    addExerciseRow(ex, layoutEasier)
                }
                val altHeader = TextView(this).apply {
                    text = getString(R.string.replace_alternatives)
                    setTextColor(ContextCompat.getColor(this@ProgramWorkoutActivity, R.color.text_secondary))
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

    // -----------------------------------------------------------
    // Edit Dialogs
    // -----------------------------------------------------------

    private fun showEditExerciseDialog(workoutId: String, itemIndex: Int, item: WorkoutLineItem) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_workout_line_item_edit, null)
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
        inputWeight.setText(PerSetValues.floatAt(item.weightPerSet, 1, item.sets ?: 1)?.toString() ?: "")

        val bottomSheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        bottomSheet.setContentView(dialogView)

        dialogView.findViewById<MaterialButton>(R.id.btnSave).setOnClickListener {
            val updated = item.copy(
                exerciseSlug = selectedExerciseSlug,
                sets = inputSets.text.toString().toIntOrNull() ?: item.sets,
                targetReps = if (layoutReps.visibility == View.VISIBLE) inputReps.text.toString().toIntOrNull() else null,
                targetDuration = if (layoutDuration.visibility == View.VISIBLE) inputDuration.text.toString().toIntOrNull() else null,
                restBetweenSetsMs = inputRestBetween.text.toString().toLongOrNull()?.times(1000),
                weightPerSet = if (layoutWeight.visibility == View.VISIBLE) {
                    val sets = inputSets.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 1
                    inputWeight.text.toString().toFloatOrNull()?.let { weight -> List(sets) { weight } }
                } else {
                    null
                }
            )
            updateItemInPlannedWorkout(workoutId, itemIndex, updated)
            bottomSheet.dismiss()
        }
        dialogView.findViewById<MaterialButton>(R.id.btnCancel).setOnClickListener {
            bottomSheet.dismiss()
        }
        bottomSheet.show()
    }

    private fun showEditRestDialog(workoutId: String, itemIndex: Int, item: WorkoutLineItem) {
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
                    updateItemInPlannedWorkout(workoutId, itemIndex, item.copy(restDurationMs = seconds * 1000))
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showDeleteItemDialog(workoutId: String, itemIndex: Int) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.ds_delete_item_title))
            .setMessage(getString(R.string.ds_delete_item_message))
            .setPositiveButton(getString(R.string.ds_delete)) { _, _ ->
                removeItemFromPlannedWorkout(workoutId, itemIndex)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showAddExerciseSheet() {
        // Determine which planned workout to add to (the expanded one in edit mode)
        val targetPlannedWorkout = plannedWorkouts.firstOrNull { it.id in expandedPlannedWorkoutIds }
        if (targetPlannedWorkout == null) {
            Toast.makeText(this, getString(R.string.error_expand_workout_first), Toast.LENGTH_SHORT).show()
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
                    val newItem = WorkoutLineItem(
                        type = PlannedWorkoutItemType.EXERCISE,
                        exerciseSlug = ex.fileName,
                        sets = 3,
                        targetReps = if (duration == null) reps else null,
                        targetDuration = duration,
                        restBetweenSetsMs = 30000L
                    )
                    addItemToPlannedWorkout(targetPlannedWorkout.id, newItem)
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
        val targetPlannedWorkout = plannedWorkouts.firstOrNull { it.id in expandedPlannedWorkoutIds }
        if (targetPlannedWorkout == null) {
            Toast.makeText(this, getString(R.string.error_expand_workout_first), Toast.LENGTH_SHORT).show()
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
                val newItem = WorkoutLineItem(
                    type = PlannedWorkoutItemType.REST,
                    restDurationMs = seconds * 1000L
                )
                addItemToPlannedWorkout(targetPlannedWorkout.id, newItem)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    // -----------------------------------------------------------
    // Persistence
    // -----------------------------------------------------------

    private fun persistCustomizations() {
        val pid = programId ?: return

        // Normalize sortOrder for planned workouts and their items to match current list positions.
        // This ensures sortedBy { it.sortOrder } always produces the correct order everywhere.
        val normalized = plannedWorkouts.mapIndexed { sIdx, plannedWorkout ->
            plannedWorkout.copy(
                sortOrder = sIdx,
                items = plannedWorkout.items.mapIndexed { iIdx, item ->
                    item.copy(sortOrder = iIdx)
                }
            )
        }
        plannedWorkouts.clear()
        plannedWorkouts.addAll(normalized)

        Log.d(TAG, "persistCustomizations: programId=$pid, week=$weekNumber, day=$dayNumber, plannedWorkouts=${normalized.size}")
        customizationStore.savePlannedWorkouts(pid, weekNumber, dayNumber, normalized)

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
            Snackbar.make(binding.root, getString(R.string.workout_customization_pending), Snackbar.LENGTH_SHORT).show()
            return
        }
        val userProgramId = currentUserProgramId
        if (userProgramId.isNullOrBlank()) {
            Snackbar.make(binding.root, getString(R.string.workout_customization_pending), Snackbar.LENGTH_SHORT).show()
            Log.w(TAG, "Skip customization sync: current screen is not bound to an active userProgramId")
            return
        }

        // Build the customizations payload with the current day's planned workouts
        val dayKey = "day_${weekNumber}_${dayNumber}"
        val plannedWorkoutsPayload = plannedWorkouts.map { plannedWorkout ->
            mapOf(
                "id" to plannedWorkout.id,
                "name" to mapOf("en" to plannedWorkout.name.en, "ar" to plannedWorkout.name.ar),
                "sortOrder" to plannedWorkout.sortOrder,
                "isDeleted" to plannedWorkout.isDeleted,
                "items" to plannedWorkout.items.map { item ->
                    val itemMap = mutableMapOf<String, Any?>(
                        "type" to item.type.wireValue(),
                        "sortOrder" to item.sortOrder
                    )
                    item.exerciseSlug?.let { itemMap["exerciseSlug"] = it }
                    item.sets?.let { itemMap["sets"] = it }
                    item.targetReps?.let { itemMap["targetReps"] = it }
                    item.targetDuration?.let { itemMap["targetDuration"] = it }
                    item.restBetweenSetsMs?.let { itemMap["restBetweenSetsMs"] = it }
                    item.restDurationMs?.let { itemMap["restDurationMs"] = it }
                    item.weightPerSet?.let { itemMap["weightPerSet"] = it }
                    itemMap.filterValues { it != null }
                }
            )
        }

        val payload = mapOf<String, Any>(
            "customizations" to mapOf(dayKey to plannedWorkoutsPayload)
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
                getString(R.string.workout_customization_synced)
            } else {
                getString(R.string.workout_customization_pending)
            }
            Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun saveLocalWorkoutReport(
        workoutId: String?,
        durationMs: Long,
        totalSets: Int,
        completedSets: Int,
        totalReps: Int,
        avgAccuracy: Float,
        avgFormScore: Float,
        reportJson: String?
    ) {
        val pid = programId ?: return
        val sid = workoutId ?: return
        val report = if (!reportJson.isNullOrBlank()) {
            val gson = com.google.gson.Gson()
            val type = object :
                com.google.gson.reflect.TypeToken<com.trainingvalidator.poc.training.workout.WorkoutTrainingEngine.WorkoutReport>() {}.type
            runCatching {
                gson.fromJson<com.trainingvalidator.poc.training.workout.WorkoutTrainingEngine.WorkoutReport>(reportJson, type)
            }.getOrNull()
        } else null

        reportStore.save(
            ProgramWorkoutReportStore.ProgramWorkoutLocalReport(
                workoutId = sid,
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

    // -----------------------------------------------------------
    // Network
    // -----------------------------------------------------------

    private fun sendPlannedWorkoutStart(workoutId: String) {
        val token = AuthManager.getAccessToken(this) ?: return
        val payload = mapOf<String, Any>(
            "programId" to (programId ?: ""),
            "weekNumber" to weekNumber,
            "dayNumber" to dayNumber,
            "startedAt" to System.currentTimeMillis()
        )
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                ApiClient.mobileSyncApi.startPlannedWorkout(workoutId, "Bearer $token", payload)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to post planned workout start: ${e.message}")
            }
        }
    }

    private fun showPostWorkoutRpeDialog(onPicked: (Int?) -> Unit) {
        val labels = (1..10).map { it.toString() }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.rpe_label)
            .setItems(labels) { _, which ->
                onPicked(which + 1)
            }
            .setNegativeButton(R.string.rpe_skip) { _, _ -> onPicked(null) }
            .show()
    }

    private fun sendPlannedWorkoutComplete(
        workoutId: String,
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
            ProgramWorkoutReportStore.PendingSyncEntry(
                workoutId = workoutId,
                programId = programId ?: "",
                weekNumber = weekNumber,
                dayNumber = dayNumber,
                payload = payloadMap
            )
        )

        // Attempt to sync immediately if online
        if (token == null) {
            Log.w(TAG, "No auth token ? report queued for later sync")
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Single call to /complete (backend handles report + progress in one call)
                val response = ApiClient.mobileSyncApi.completePlannedWorkout(workoutId, "Bearer $token", payloadMap)
                if (response.isSuccessful) {
                    // Remove from offline queue on success
                    reportStore.removePendingSync(workoutId)
                    Log.d(TAG, "Planned workout complete synced successfully")
                } else {
                    Log.w(TAG, "Planned workout complete sync failed (${response.code()}) — will retry")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync planned workout complete: ${e.message} — queued for retry")
            }
        }
    }

    // -----------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------

    /**
     * Maps server effective plan to [DayCustomizationStore.CustomizedPlannedWorkout] list.
     * Returns null if payload cannot be fully resolved (missing exercise id in cache).
     */
    private fun mapEffectivePlanToCustomizedPlannedWorkouts(
        payload: EffectivePlanPayload,
        originals: List<ProgramWorkout>,
        exerciseRepo: ExerciseRepository
    ): List<DayCustomizationStore.CustomizedPlannedWorkout> {
        if (payload.plannedWorkouts.isEmpty()) return emptyList()
        val origById = originals.associateBy { it.id }
        val out = mutableListOf<DayCustomizationStore.CustomizedPlannedWorkout>()
        for (serverPlannedWorkout in payload.plannedWorkouts.sortedBy { it.sortOrder }) {
            val orig = origById[serverPlannedWorkout.id]
            val name = localizedFromEffectiveName(serverPlannedWorkout.name, orig?.name)
            val items = mutableListOf<WorkoutLineItem>()
            for (serverItem in serverPlannedWorkout.items.sortedBy { it.sortOrder }) {
                if (serverItem.skipped == true) continue
                when (serverItem.type) {
                    PlannedWorkoutItemType.EXERCISE -> {
                        val ex = serverItem.exerciseId?.let { exerciseRepo.getExerciseById(it) }
                        val resolvedSets = serverItem.sets ?: serverItem.suggestion?.suggestedSets
                        val resolvedReps = serverItem.targetReps ?: serverItem.suggestion?.suggestedReps
                        val resolvedDuration = serverItem.targetDuration ?: serverItem.suggestion?.suggestedDuration
                        val resolvedWeightPerSet = serverItem.weightPerSet
                            ?.map { value -> value.toFloat() }
                            ?: serverItem.suggestion?.suggestedWeightKg?.let { weight ->
                                List((resolvedSets ?: 1).coerceAtLeast(1)) { weight.toFloat() }
                            }
                        if (ex != null) {
                            items.add(
                                WorkoutLineItem(
                                    type = PlannedWorkoutItemType.EXERCISE,
                                    serverItemId = serverItem.id,
                                    exerciseSlug = ex.fileName,
                                    sets = resolvedSets,
                                    targetReps = resolvedReps,
                                    targetDuration = resolvedDuration,
                                    restBetweenSetsMs = serverItem.restBetweenSetsMs?.toLong(),
                                    weightPerSet = resolvedWeightPerSet,
                                    notes = notesFromApi(serverItem.notes),
                                    restDurationMs = serverItem.restDurationMs?.toLong(),
                                    suggestionSource = serverItem.suggestion?.source,
                                    phaseIndex = serverItem.phaseIndex,
                                    phaseRole = serverItem.phaseRole,
                                    sortOrder = serverItem.sortOrder
                                )
                            )
                        } else {
                            val fallbackOriginal = orig
                                ?.items
                                ?.firstOrNull { item ->
                                    item.type == PlannedWorkoutItemType.EXERCISE && item.sortOrder == serverItem.sortOrder
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
                                        weightPerSet = resolvedWeightPerSet ?: fallbackOriginal.weightPerSet,
                                        notes = notesFromApi(serverItem.notes) ?: fallbackOriginal.notes,
                                        restDurationMs = serverItem.restDurationMs?.toLong()
                                            ?: fallbackOriginal.restDurationMs,
                                        suggestionSource = serverItem.suggestion?.source ?: fallbackOriginal.suggestionSource,
                                        phaseIndex = serverItem.phaseIndex ?: fallbackOriginal.phaseIndex,
                                        phaseRole = serverItem.phaseRole ?: fallbackOriginal.phaseRole,
                                        sortOrder = serverItem.sortOrder
                                    )
                                )
                            } else {
                                Log.w(
                                    TAG,
                                    "Skipping unresolved effective-plan item plannedWorkout=${serverPlannedWorkout.id} sortOrder=${serverItem.sortOrder}"
                                )
                            }
                        }
                    }
                    PlannedWorkoutItemType.REST -> {
                        items.add(
                            WorkoutLineItem(
                                type = PlannedWorkoutItemType.REST,
                                serverItemId = serverItem.id,
                                restDurationMs = serverItem.restDurationMs?.toLong()
                                    ?: ((serverItem.targetDuration ?: 60) * 1000L),
                                suggestionSource = serverItem.suggestion?.source,
                                phaseIndex = serverItem.phaseIndex,
                                phaseRole = serverItem.phaseRole,
                                sortOrder = serverItem.sortOrder
                            )
                        )
                    }
                }
            }
            out.add(
                DayCustomizationStore.CustomizedPlannedWorkout(
                    id = serverPlannedWorkout.id,
                    name = name,
                    sortOrder = serverPlannedWorkout.sortOrder,
                    workoutTemplateId = serverPlannedWorkout.workoutTemplateId ?: orig?.workoutTemplateId,
                    estimatedDurationMin = serverPlannedWorkout.estimatedDurationMin,
                    items = items
                )
            )
        }
        return out
    }

    private fun localizedFromEffectiveName(
        api: Map<String, String>?,
        fallback: LocalizedText?
    ): LocalizedText {
        if (api != null && (api["en"]?.isNotBlank() == true || api["ar"]?.isNotBlank() == true)) {
            return LocalizedText(ar = api["ar"] ?: "", en = api["en"] ?: "")
        }
        return fallback ?: LocalizedText()
    }

    private fun notesFromApi(notes: Map<String, String>?): LocalizedText? {
        if (notes == null || notes.isEmpty()) return null
        return LocalizedText(ar = notes["ar"] ?: "", en = notes["en"] ?: "")
    }

    private fun formatSuggestionSource(source: String?): String? {
        return when (source) {
            "progression_state" -> getString(R.string.training_suggestion_progression)
            "template" -> getString(R.string.training_suggestion_template)
            "goal_default" -> getString(R.string.training_suggestion_goal_default)
            else -> null
        }
    }

    private fun syncReplaceOverride(originalItem: WorkoutLineItem?, updatedItem: WorkoutLineItem) {
        val slug = updatedItem.exerciseSlug ?: return
        val exerciseRepo = ExerciseRepository.getInstance(this)
        val exerciseId = exerciseRepo.getExerciseServerId(slug) ?: return
        syncOverride(
            targetItemId = originalItem?.serverItemId,
            overrideType = "REPLACE_EXERCISE",
            data = mapOf("exerciseId" to exerciseId)
        )
    }

    private fun syncAdjustOverride(originalItem: WorkoutLineItem?, updatedItem: WorkoutLineItem) {
        if (originalItem?.serverItemId.isNullOrBlank()) return

        val data = mutableMapOf<String, Any?>()
        updatedItem.sets?.let { data["sets"] = it }
        updatedItem.targetReps?.let { data["targetReps"] = it }
        updatedItem.targetDuration?.let { data["targetDuration"] = it }
        updatedItem.restBetweenSetsMs?.let { data["restBetweenSetsMs"] = it }
        updatedItem.restDurationMs?.let { data["restDurationMs"] = it }
        updatedItem.weightPerSet?.let { data["weightPerSet"] = it }

        val slug = updatedItem.exerciseSlug
        if (!slug.isNullOrBlank()) {
            ExerciseRepository.getInstance(this).getExerciseServerId(slug)?.let { data["exerciseId"] = it }
        }

        syncOverride(
            targetItemId = originalItem.serverItemId,
            overrideType = "ADJUST_PRESCRIPTION",
            data = data
        )
    }

    private fun syncSkipOverride(item: WorkoutLineItem?) {
        syncOverride(
            targetItemId = item?.serverItemId,
            overrideType = "SKIP_ITEM",
            data = null
        )
    }

    private fun syncAddOverride(anchorItem: WorkoutLineItem?, addedItem: WorkoutLineItem) {
        val anchorId = anchorItem?.serverItemId ?: return
        val data = mutableMapOf<String, Any?>(
            "type" to addedItem.type.wireValue()
        )

        if (addedItem.type == PlannedWorkoutItemType.EXERCISE) {
            val slug = addedItem.exerciseSlug ?: return
            val exerciseId = ExerciseRepository.getInstance(this).getExerciseServerId(slug) ?: return
            data["exerciseId"] = exerciseId
            addedItem.sets?.let { data["sets"] = it }
            addedItem.targetReps?.let { data["targetReps"] = it }
            addedItem.targetDuration?.let { data["targetDuration"] = it }
            addedItem.restBetweenSetsMs?.let { data["restBetweenSetsMs"] = it }
            addedItem.weightPerSet?.let { data["weightPerSet"] = it }
        } else {
            addedItem.restDurationMs?.let { data["restDurationMs"] = it }
        }

        syncOverride(
            targetItemId = anchorId,
            overrideType = "ADD_ITEM",
            data = data
        )
    }

    private fun syncOverride(
        targetItemId: String?,
        overrideType: String,
        data: Map<String, Any?>?
    ) {
        val userProgramId = currentUserProgramId
        val token = AuthManager.getAccessToken(this)
        if (targetItemId.isNullOrBlank() || userProgramId.isNullOrBlank() || token.isNullOrBlank()) {
            return
        }

        val body = mutableMapOf<String, Any?>(
            "weekNumber" to weekNumber,
            "dayNumber" to dayNumber,
            "workoutTemplateExerciseId" to targetItemId,
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

    private fun getCompletedPlannedWorkoutIds(): Set<String> {
        val pid = programId ?: return emptySet()
        val reports = reportStore.getByDay(pid, weekNumber, dayNumber)
        return reports.map { it.workoutId }.toSet()
    }

    private fun getLocalizedName(text: LocalizedText): String {
        val language = currentLanguage
        return when (language) {
            "ar" -> text.ar.ifBlank { text.en }
            else -> text.en.ifBlank { text.ar }
        }
    }


    private fun estimatePlannedWorkoutDuration(items: List<WorkoutLineItem>): Int {
        var totalSeconds = 0
        items.forEach { item ->
            if (item.type == PlannedWorkoutItemType.EXERCISE) {
                val sets = item.sets ?: 1
                val perSet = (item.targetDuration ?: 30) + ((item.restBetweenSetsMs ?: 30000L) / 1000).toInt()
                totalSeconds += sets * perSet
            } else if (item.type == PlannedWorkoutItemType.REST) {
                totalSeconds += ((item.restDurationMs ?: 0L) / 1000).toInt()
            }
        }
        return (totalSeconds / 60).coerceAtLeast(1)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
