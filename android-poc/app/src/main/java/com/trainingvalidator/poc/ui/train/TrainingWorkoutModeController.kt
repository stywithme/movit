package com.trainingvalidator.poc.ui.train

import android.app.Activity
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import coil.load
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.storage.ExerciseRepository
import com.trainingvalidator.poc.training.models.ExerciseConfig
import com.trainingvalidator.poc.ui.components.GlassmorphicMessageView
import com.trainingvalidator.poc.ui.utils.currentLanguage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.trainingvalidator.poc.training.models.PerSetValues
import com.trainingvalidator.poc.training.models.PlannedWorkoutItemType
import com.trainingvalidator.poc.training.models.WorkoutLineItem
import com.trainingvalidator.poc.training.report.ReportGenerator
import com.trainingvalidator.poc.training.workout.PhaseResumeAction
import com.trainingvalidator.poc.training.workout.WorkoutRestContext
import com.trainingvalidator.poc.training.workout.WorkoutTrainingEngine
import com.trainingvalidator.poc.training.workout.WorkoutTrainingEngine.SetMetrics
import com.trainingvalidator.poc.training.workout.WorkoutTrainingEngine.RepDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

/**
 * Planned-workout run mode: [WorkoutTrainingEngine] lifecycle, overlay panels, rest between sets,
 * per-exercise report generation, and workout [Activity.setResult] payload.
 */
class TrainingWorkoutModeController(
    private val host: TrainingActivity,
    private val preferences: TrainingPreferenceDialogs
) {
    private val tag: String
        get() = TrainingActivity.TAG

    var workoutEngine: WorkoutTrainingEngine? = null
    private var workoutRestTimer: android.os.CountDownTimer? = null
    private var workoutRestRemainingMs: Long = 0L
    /** While prep rest is shown, identifies the set so timer completion can auto-start safely. */
    private var pendingPrepRestKey: Pair<Int, Int>? = null
    private var workoutPrepRestPaused: Boolean = false
    private var workoutSetStartTimeMs: Long = 0L
    private val pendingReportJobs = mutableListOf<Job>()

    @Suppress("MemberVisibilityCanBePrivate")
    val workoutExerciseConfigMap = mutableMapOf<String, ExerciseConfig>()

    private var currentWorkoutSetRunId: Long = 0L
    private var lastCompletedWorkoutSetRunId: Long = -1L

    fun onDestroy() {
        workoutRestTimer?.cancel()
        pendingPrepRestKey = null
        workoutPrepRestPaused = false
    }

    fun onActivityPaused() {
        workoutEngine?.onActivityPaused()
    }

    fun onActivityResumed() {
        when (workoutEngine?.onActivityResumed()) {
            PhaseResumeAction.PHASE_RESTARTED_NO_CONTINUE -> Toast.makeText(
                host,
                host.getString(R.string.workout_phase_restarted_no_continue),
                Toast.LENGTH_LONG
            ).show()
            PhaseResumeAction.PHASE_RESTARTED_TIMEOUT -> Toast.makeText(
                host,
                host.getString(R.string.workout_phase_continue_expired),
                Toast.LENGTH_LONG
            ).show()
            else -> Unit
        }
    }

    fun cancelWorkoutRestOnClose() {
        workoutRestTimer?.cancel()
        pendingPrepRestKey = null
        workoutPrepRestPaused = false
    }

    /**
     * Parse [TrainingActivity.EXTRA_WORKOUT_ITEMS_JSON] and start the workout run engine.
     */
    fun initializeFromIntent() {
        val json = host.intent.getStringExtra(TrainingActivity.EXTRA_WORKOUT_ITEMS_JSON)
        if (json.isNullOrBlank()) {
            Log.e(tag, "Workout mode but no items JSON provided")
            host.finish()
            return
        }
        val gson = Gson()
        val itemsType = object : TypeToken<List<WorkoutLineItem>>() {}.type
        val items: List<WorkoutLineItem> = try {
            gson.fromJson(json, itemsType)
        } catch (e: Exception) {
            Log.e(tag, "Failed to parse workout items JSON", e)
            host.finish()
            return
        }
        if (items.isEmpty()) {
            Log.w(tag, "Workout items list is empty")
            host.finish()
            return
        }
        val workoutRole = host.intent.getStringExtra(TrainingActivity.EXTRA_WORKOUT_ROLE) ?: "MAIN"
        val invalidExerciseItem = items.firstOrNull { it.type == PlannedWorkoutItemType.EXERCISE && it.exerciseSlug.isNullOrBlank() }
        if (invalidExerciseItem != null) {
            Log.e(tag, "Invalid workout payload: exercise item without exerciseSlug")
            Toast.makeText(
                host,
                host.getString(R.string.workout_invalid_payload),
                Toast.LENGTH_LONG
            ).show()
            host.setResult(Activity.RESULT_CANCELED)
            host.finish()
            return
        }
        val engine = WorkoutTrainingEngine(items, workoutRole)
        val exerciseRepo = ExerciseRepository.getInstance(host)
        val language = host.currentLanguage
        workoutExerciseConfigMap.clear()
        items.filter { it.type == PlannedWorkoutItemType.EXERCISE && it.exerciseSlug != null }.forEach { item ->
            val slug = item.exerciseSlug ?: return@forEach
            val config = exerciseRepo.getExercise(slug)
            if (config != null) {
                workoutExerciseConfigMap[slug] = config
                val name = config.name.get(language).ifBlank { config.name.en }
                engine.setExerciseName(slug, name)
            }
        }
        workoutEngine = engine
        engine.onExerciseCompletedListener = host
        observeWorkoutEngineState()
        engine.start()
    }

    private fun localizedInstruction(config: ExerciseConfig?, language: String): String? {
        if (config == null) return null
        val primary = config.instructions?.get(language)?.trim().orEmpty()
        val text = primary.ifBlank {
            config.instructions?.en?.trim().orEmpty()
        }.ifBlank {
            config.instructions?.ar?.trim().orEmpty()
        }
        return text.takeIf { it.isNotEmpty() }
    }

    private fun bindExercisePreviewImage(slug: String?, imageView: ImageView, fallbackView: ImageView) {
        val url = slug?.takeIf { it.isNotBlank() }
            ?.let { workoutExerciseConfigMap[it]?.imageUrl }
            ?.trim()
        if (url.isNullOrEmpty()) {
            imageView.setImageDrawable(null)
            fallbackView.visibility = View.VISIBLE
            return
        }
        imageView.load(url) {
            placeholder(R.drawable.gradient_report_hero)
            error(R.drawable.gradient_report_hero)
            crossfade(true)
            listener(
                onStart = { fallbackView.visibility = View.GONE },
                onSuccess = { _, _ -> fallbackView.visibility = View.GONE },
                onError = { _, _ ->
                    imageView.setImageDrawable(null)
                    fallbackView.visibility = View.VISIBLE
                }
            )
        }
    }

    private fun buildWorkoutTargetSummaryLine(item: WorkoutLineItem): String {
        val sets = item.sets?.coerceAtLeast(1) ?: 1
        val reps = PerSetValues.intAt(item.targetRepsPerSet, 1, sets, item.targetReps)
        val base = when {
            item.targetDuration != null && item.targetDuration > 0 ->
                host.getString(R.string.workout_target_hold_format, sets, item.targetDuration)
            reps != null && reps > 0 ->
                host.getString(R.string.workout_target_reps_format, sets, reps)
            else ->
                host.getString(R.string.workout_target_sets_only_format, sets)
        }
        val w = PerSetValues.floatAt(item.weightPerSet, 1, sets)?.takeIf { it > 0f }
        return if (w != null) {
            "$base · ${host.getString(R.string.workout_weight_format, w)}"
        } else {
            base
        }
    }

    private fun observeWorkoutEngineState() {
        val engine = workoutEngine ?: return
        host.lifecycleScope.launch {
            engine.state.collectLatest { workoutState ->
                when (workoutState) {
                    is WorkoutTrainingEngine.State.Idle -> { }
                    is WorkoutTrainingEngine.State.PreExercise -> {
                        showWorkoutPreExercise(workoutState)
                    }
                    is WorkoutTrainingEngine.State.Training -> { }
                    is WorkoutTrainingEngine.State.WorkoutComplete -> {
                        showCelebrationMessage(host.getString(R.string.workout_celebrate_workout))
                        showWorkoutComplete(workoutState.report)
                    }
                }
            }
        }
    }

    private fun showWorkoutPreExercise(
        state: WorkoutTrainingEngine.State.PreExercise
    ) {
        hideWorkoutPanels()
        val b = host.binding
        b.WorkoutPreExercisePanel.visibility = View.VISIBLE
        b.setupPosePanel.visibility = View.GONE
        b.setupIndicatorBar.visibility = View.GONE
        b.countdownPanel.visibility = View.GONE
        b.heroCounterContainer.visibility = View.GONE
        b.completedPanel.visibility = View.GONE
        b.bottomStatsBar.visibility = View.GONE
        b.progressContainer.visibility = View.GONE
        b.vignetteOverlay.clear()
        b.skeletonOverlay.setTrainingMode(false)

        val engine = workoutEngine ?: return
        val item = state.item
        val inRest = state.pendingRestMs > 0L

        if (inRest) {
            when (state.restContext) {
                WorkoutRestContext.BETWEEN_SETS ->
                    showCelebrationMessage(host.getString(R.string.workout_celebrate_set))
                WorkoutRestContext.BETWEEN_EXERCISES ->
                    showCelebrationMessage(host.getString(R.string.workout_celebrate_exercise))
                else -> { }
            }
            b.tvWorkoutExerciseLabel.visibility = View.GONE
            b.layoutWorkoutPrepTimerRow.visibility = View.VISIBLE
        } else {
            b.tvWorkoutExerciseLabel.visibility = View.VISIBLE
            b.layoutWorkoutPrepTimerRow.visibility = View.GONE
            b.tvWorkoutExerciseLabel.text = host.getString(
                R.string.workout_exercise_label,
                state.exerciseIndex + 1,
                engine.getExerciseCount()
            )
        }

        b.tvWorkoutExerciseName.text = state.exerciseName
        hideAlternatingLabels()
        updateWorkoutSetIndicator(state.setNumber, state.totalSets)

        if (inRest && state.restContext == WorkoutRestContext.BETWEEN_SETS) {
            b.tvWorkoutSetInfo.text = host.getString(
                R.string.workout_rest_next_set_format,
                state.exerciseName,
                state.setNumber,
                state.totalSets
            )
        } else if (inRest && state.restContext == WorkoutRestContext.BETWEEN_EXERCISES) {
            b.tvWorkoutSetInfo.text = buildWorkoutTargetSummaryLine(item)
        } else {
            val targetReps = engine.getCurrentSetReps()
            val targetDuration = item.targetDuration
            b.tvWorkoutSetInfo.text = when {
                targetReps != null && targetReps > 0 -> host.getString(
                    R.string.workout_set_reps_format,
                    state.setNumber, state.totalSets, targetReps
                )
                targetDuration != null && targetDuration > 0 -> host.getString(
                    R.string.workout_set_duration_format,
                    state.setNumber, state.totalSets, targetDuration
                )
                else -> host.getString(
                    R.string.workout_set_only_format,
                    state.setNumber, state.totalSets
                )
            }
        }

        val weight = engine.getCurrentSetWeight()
        if (weight != null && weight > 0f) {
            b.tvWorkoutWeightInfo.text = host.getString(R.string.workout_weight_format, weight)
            b.tvWorkoutWeightInfo.visibility = View.VISIBLE
        } else {
            b.tvWorkoutWeightInfo.visibility = View.GONE
        }
        val lang = host.currentLanguage
        val slug = item.exerciseSlug
        bindExercisePreviewImage(slug, b.ivWorkoutPreExercisePreview, b.ivWorkoutPreExerciseFallbackIcon)
        val cfg = slug?.let { workoutExerciseConfigMap[it] }
        val instruction = localizedInstruction(cfg, lang)
        val tip = if (inRest) getRestTip(state.restContext) else null
        when {
            inRest && !instruction.isNullOrBlank() && !tip.isNullOrBlank() -> {
                b.tvWorkoutInstruction.text = "$instruction\n\n$tip"
                b.tvWorkoutInstruction.visibility = View.VISIBLE
            }
            inRest && !tip.isNullOrBlank() -> {
                b.tvWorkoutInstruction.text = tip
                b.tvWorkoutInstruction.visibility = View.VISIBLE
            }
            !instruction.isNullOrBlank() -> {
                b.tvWorkoutInstruction.text = instruction
                b.tvWorkoutInstruction.visibility = View.VISIBLE
            }
            else -> {
                b.tvWorkoutInstruction.visibility = View.GONE
            }
        }

        // Settings card (reps/duration/weight) - visible for both rest and pre-start
        bindWorkoutSettings(state, engine, cfg)
        val canSkipPhase = !inRest && engine.canSkipCurrentPhase()
        b.btnWorkoutSkipPhase.visibility = if (canSkipPhase) View.VISIBLE else View.GONE
        b.btnWorkoutSkipPhase.setOnClickListener {
            if (workoutEngine?.skipCurrentPhase() == true) {
                Toast.makeText(host, host.getString(R.string.workout_phase_skipped), Toast.LENGTH_SHORT).show()
            }
        }

        if (inRest) {
            pendingPrepRestKey = state.exerciseIndex to state.setNumber
            workoutPrepRestPaused = false
            b.tvWorkoutPrepCountdown.visibility = View.VISIBLE
            b.layoutWorkoutPrepRestControls.visibility = View.GONE
            b.btnWorkoutStartSet.visibility = View.GONE
            b.btnWorkoutPrepPauseRestIcon.setImageResource(R.drawable.ic_pause)
            b.btnWorkoutPrepPauseRestIcon.contentDescription = host.getString(R.string.pause)
            b.btnWorkoutPrepPauseRestIcon.visibility = View.VISIBLE
            b.btnWorkoutPrepSkipRestIcon.visibility = View.VISIBLE
            b.btnWorkoutPrepSkipRestIcon.setOnClickListener {
                workoutRestTimer?.cancel()
                workoutPrepRestPaused = false
                b.btnWorkoutPrepPauseRestIcon.setImageResource(R.drawable.ic_pause)
                b.btnWorkoutPrepPauseRestIcon.contentDescription = host.getString(R.string.pause)
                val cur = workoutEngine?.state?.value as? WorkoutTrainingEngine.State.PreExercise
                    ?: return@setOnClickListener
                if (cur.pendingRestMs > 0L) {
                    requestWorkoutStartFromPreExercise(cur)
                }
            }

            val prepCountdown = b.tvWorkoutPrepCountdown
            startPlannedWorkoutPrepRestCountdown(state.pendingRestMs, prepCountdown)
            b.btnWorkoutPrepPauseRestIcon.setOnClickListener {
                if (workoutPrepRestPaused) {
                    workoutPrepRestPaused = false
                    b.btnWorkoutPrepPauseRestIcon.setImageResource(R.drawable.ic_pause)
                    b.btnWorkoutPrepPauseRestIcon.contentDescription = host.getString(R.string.pause)
                    startPlannedWorkoutPrepRestCountdown(
                        workoutRestRemainingMs.coerceAtLeast(1000L),
                        prepCountdown
                    )
                } else {
                    workoutRestTimer?.cancel()
                    workoutPrepRestPaused = true
                    b.btnWorkoutPrepPauseRestIcon.setImageResource(R.drawable.ic_play)
                    b.btnWorkoutPrepPauseRestIcon.contentDescription = host.getString(R.string.resume)
                }
            }
        } else {
            pendingPrepRestKey = null
            workoutPrepRestPaused = false
            b.tvWorkoutPrepCountdown.visibility = View.GONE
            b.layoutWorkoutPrepRestControls.visibility = View.GONE
            b.btnWorkoutStartSet.visibility = View.VISIBLE
            b.btnWorkoutPrepPauseRestIcon.visibility = View.GONE
            b.btnWorkoutPrepSkipRestIcon.visibility = View.GONE
            b.btnWorkoutStartSet.text = when {
                state.exerciseIndex == 0 && state.setNumber == 1 ->
                    host.getString(R.string.workout_start_first_exercise)
                else ->
                    host.getString(R.string.workout_start_set_numbered, state.setNumber)
            }
            b.btnWorkoutStartSet.setOnClickListener {
                requestWorkoutStartFromPreExercise(state)
            }
        }
    }

    private fun bindWorkoutSettings(
        state: WorkoutTrainingEngine.State.PreExercise,
        engine: WorkoutTrainingEngine,
        cfg: ExerciseConfig?
    ) {
        val b = host.binding
        val item = engine.getCurrentExerciseItem() ?: state.item

        val hasReps = (engine.getCurrentSetReps() ?: item.targetReps ?: 0) > 0
            || !item.targetRepsPerSet.isNullOrEmpty()
        val hasDuration = (item.targetDuration ?: 0) > 0
        val weightCfg = cfg?.takeIf { it.supportsWeight }
        val supportsWeight = weightCfg != null

        val showCard = hasReps || hasDuration || supportsWeight
        b.cardWorkoutSettings.visibility = if (showCard) View.VISIBLE else View.GONE

        // Reps row
        b.layoutWorkoutSettingReps.visibility = if (hasReps) View.VISIBLE else View.GONE
        if (hasReps) {
            val currentReps = engine.getCurrentSetReps() ?: item.targetReps ?: 10
            val maxReps = maxOf(100, currentReps)
            b.tvWorkoutRepsValue.text = currentReps.toString()
            b.sliderWorkoutReps.clearOnChangeListeners()
            b.sliderWorkoutReps.valueFrom = 1f
            b.sliderWorkoutReps.valueTo = maxReps.toFloat()
            b.sliderWorkoutReps.stepSize = 1f
            b.sliderWorkoutReps.value = currentReps.toFloat().coerceIn(1f, maxReps.toFloat())
            b.sliderWorkoutReps.addOnChangeListener { _, value, fromUser ->
                if (!fromUser) return@addOnChangeListener
                val reps = value.toInt().coerceAtLeast(1)
                engine.updateCurrentTargetReps(reps)
                b.tvWorkoutRepsValue.text = reps.toString()
                updateExerciseWorkoutSummaryFromEngine(state, engine)
            }
        }

        // Duration row
        b.layoutWorkoutSettingDuration.visibility = if (hasDuration) View.VISIBLE else View.GONE
        if (hasDuration) {
            val currentSec = item.targetDuration ?: 30
            b.tvWorkoutDurationValue.text = formatDurationClock(currentSec)
            b.btnWorkoutDurationPicker.setOnClickListener {
                showWorkoutDurationPicker(state, engine)
            }
        }

        // Weight row
        b.layoutWorkoutSettingWeight.visibility = if (supportsWeight) View.VISIBLE else View.GONE
        if (weightCfg != null) {
            val (minWeight, maxWeight) = resolveWeightRange(weightCfg)
            val currentWeight = engine.getCurrentSetWeight() ?: weightCfg.defaultWeight ?: minWeight
            val clampedWeight = currentWeight.coerceIn(minWeight, maxWeight)
            b.tvWorkoutWeightValue.text = formatWeightDisplay(clampedWeight)
            b.tvWorkoutWeightRange.text = when {
                weightCfg.minWeight != null && weightCfg.maxWeight != null ->
                    host.getString(R.string.weight_min_max_format, minWeight, maxWeight)
                weightCfg.minWeight != null ->
                    host.getString(R.string.weight_min_only_format, minWeight)
                weightCfg.maxWeight != null ->
                    host.getString(R.string.weight_max_only_format, maxWeight)
                else ->
                    host.getString(R.string.weight_min_max_format, minWeight, maxWeight)
            }
            b.sliderWorkoutWeight.clearOnChangeListeners()
            b.sliderWorkoutWeight.valueFrom = minWeight
            b.sliderWorkoutWeight.valueTo = maxWeight
            b.sliderWorkoutWeight.value = clampedWeight
            b.sliderWorkoutWeight.addOnChangeListener { _, value, fromUser ->
                if (!fromUser) return@addOnChangeListener
                val weight = value.coerceIn(minWeight, maxWeight)
                engine.updateCurrentSetWeight(weight)
                b.tvWorkoutWeightValue.text = formatWeightDisplay(weight)
                updateExerciseWorkoutSummaryFromEngine(state, engine)
            }
        }
    }

    private fun tryAutoStartAfterPrepRest() {
        val (exIdx, setNum) = pendingPrepRestKey ?: return
        val latest = workoutEngine?.state?.value as? WorkoutTrainingEngine.State.PreExercise ?: return
        if (latest.pendingRestMs <= 0L) return
        if (latest.exerciseIndex != exIdx || latest.setNumber != setNum) return
        requestWorkoutStartFromPreExercise(latest)
    }

    private fun startPlannedWorkoutPrepRestCountdown(durationMs: Long, countdownView: TextView) {
        workoutRestTimer?.cancel()
        workoutPrepRestPaused = false
        host.binding.btnWorkoutPrepPauseRestIcon.setImageResource(R.drawable.ic_pause)
        host.binding.btnWorkoutPrepPauseRestIcon.contentDescription = host.getString(R.string.pause)
        val duration = durationMs.coerceAtLeast(1000L)
        workoutRestRemainingMs = duration
        val initialSecs = (duration / 1000).toInt()
        countdownView.text = String.format(
            "%02d:%02d",
            initialSecs / 60,
            initialSecs % 60
        )
        workoutRestTimer = object : android.os.CountDownTimer(duration, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val secs = (millisUntilFinished / 1000).toInt()
                val m = secs / 60
                val s = secs % 60
                countdownView.text = String.format("%02d:%02d", m, s)
                workoutRestRemainingMs = millisUntilFinished
            }

            override fun onFinish() {
                countdownView.text = "00:00"
                workoutRestRemainingMs = 0L
                workoutPrepRestPaused = false
                playRestEndAlert()
                tryAutoStartAfterPrepRest()
            }
        }.start()
    }

    private fun requestWorkoutStartFromPreExercise(
        state: WorkoutTrainingEngine.State.PreExercise
    ) {
        val engine = workoutEngine ?: return
        val item = state.item
        val slug = item.exerciseSlug ?: return
        val cfg = workoutExerciseConfigMap[slug]
        applyWorkoutInputOverrides(state, engine, cfg)
        proceedWorkoutStartFromPreExercise(state, slug)
    }

    private fun applyWorkoutInputOverrides(
        state: WorkoutTrainingEngine.State.PreExercise,
        engine: WorkoutTrainingEngine,
        cfg: ExerciseConfig?
    ) {
        val b = host.binding
        val currentItem = engine.getCurrentExerciseItem() ?: state.item

        val hasReps = (engine.getCurrentSetReps() ?: currentItem.targetReps ?: 0) > 0
        if (hasReps) {
            val reps = b.sliderWorkoutReps.value.toInt().coerceAtLeast(1)
            engine.updateCurrentTargetReps(reps)
            b.tvWorkoutRepsValue.text = reps.toString()
        }

        if (cfg?.supportsWeight == true) {
            val (minWeight, maxWeight) = resolveWeightRange(cfg)
            val weight = b.sliderWorkoutWeight.value.coerceIn(minWeight, maxWeight)
            engine.updateCurrentSetWeight(weight)
            b.tvWorkoutWeightValue.text = formatWeightDisplay(weight)
        }

        updateExerciseWorkoutSummaryFromEngine(state, engine)
    }

    private fun resolveWeightRange(cfg: ExerciseConfig): Pair<Float, Float> {
        val minWeight = cfg.minWeight ?: 0f
        val configuredMax = cfg.maxWeight
        val maxWeight = when {
            configuredMax != null && configuredMax > minWeight -> configuredMax
            cfg.minWeight == null && configuredMax == null -> 100f
            minWeight < 100f -> 100f
            else -> minWeight + 100f
        }
        return minWeight to maxWeight
    }

    private fun showWorkoutDurationPicker(
        state: WorkoutTrainingEngine.State.PreExercise,
        engine: WorkoutTrainingEngine
    ) {
        val currentSeconds = (engine.getCurrentExerciseItem() ?: state.item)
            .targetDuration
            ?.coerceAtLeast(1)
            ?: 30
        val minutePicker = android.widget.NumberPicker(host).apply {
            minValue = 0
            maxValue = 59
            value = (currentSeconds / 60).coerceIn(0, 59)
            displayedValues = Array(60) { String.format("%02d", it) }
            wrapSelectorWheel = false
        }
        val secondPicker = android.widget.NumberPicker(host).apply {
            minValue = 0
            maxValue = 59
            value = (currentSeconds % 60).coerceIn(0, 59)
            displayedValues = Array(60) { String.format("%02d", it) }
            wrapSelectorWheel = false
        }
        fun pickerColumn(label: String, picker: android.widget.NumberPicker): android.widget.LinearLayout {
            return android.widget.LinearLayout(host).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                addView(picker)
                addView(TextView(host).apply {
                    text = label
                    textSize = 12f
                    setTextColor(android.graphics.Color.GRAY)
                })
            }
        }
        val pickerLayout = android.widget.LinearLayout(host).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(24, 8, 24, 8)
            addView(pickerColumn("min", minutePicker))
            addView(pickerColumn("sec", secondPicker))
        }

        android.app.AlertDialog.Builder(host)
            .setTitle(host.getString(R.string.workout_duration_label))
            .setView(pickerLayout)
            .setPositiveButton(host.getString(R.string.save)) { dialog, _ ->
                val seconds = (minutePicker.value * 60 + secondPicker.value).coerceAtLeast(1)
                engine.updateCurrentTargetDuration(seconds)
                host.binding.tvWorkoutDurationValue.text = formatDurationClock(seconds)
                updateExerciseWorkoutSummaryFromEngine(state, engine)
                dialog.dismiss()
            }
            .setNegativeButton(host.getString(R.string.cancel)) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun updateExerciseWorkoutSummaryFromEngine(
        state: WorkoutTrainingEngine.State.PreExercise,
        engine: WorkoutTrainingEngine
    ) {
        val b = host.binding
        val item = engine.getCurrentExerciseItem() ?: state.item
        val inRest = state.pendingRestMs > 0L
        b.tvWorkoutSetInfo.text = when {
            inRest && state.restContext == WorkoutRestContext.BETWEEN_SETS -> host.getString(
                R.string.workout_rest_next_set_format,
                state.exerciseName,
                state.setNumber,
                state.totalSets
            )
            inRest && state.restContext == WorkoutRestContext.BETWEEN_EXERCISES ->
                buildWorkoutTargetSummaryLine(item)
            item.targetReps != null && item.targetReps > 0 -> host.getString(
                R.string.workout_set_reps_format,
                state.setNumber,
                state.totalSets,
                item.targetReps
            )
            item.targetDuration != null && item.targetDuration > 0 -> host.getString(
                R.string.workout_set_duration_format,
                state.setNumber,
                state.totalSets,
                item.targetDuration
            )
            else -> host.getString(
                R.string.workout_set_only_format,
                state.setNumber,
                state.totalSets
            )
        }

        val weight = engine.getCurrentSetWeight()
        if (weight != null && weight > 0f) {
            b.tvWorkoutWeightInfo.text = host.getString(R.string.workout_weight_format, weight)
            b.tvWorkoutWeightInfo.visibility = View.VISIBLE
        } else {
            b.tvWorkoutWeightInfo.visibility = View.GONE
        }
    }

    private fun formatWeightDisplay(weight: Float): String {
        val value = if (weight % 1f == 0f) {
            weight.toInt().toString()
        } else {
            String.format("%.1f", weight)
        }
        return "$value ${host.getString(R.string.workout_kg_unit)}"
    }

    private fun formatDurationClock(seconds: Int): String {
        val safeSeconds = seconds.coerceAtLeast(1)
        return String.format("%02d:%02d", safeSeconds / 60, safeSeconds % 60)
    }

    private fun proceedWorkoutStartFromPreExercise(
        state: WorkoutTrainingEngine.State.PreExercise,
        slug: String
    ) {
        val engine = workoutEngine ?: return
        val item = engine.getCurrentExerciseItem() ?: state.item
        hideWorkoutPanels()
        val b = host.binding
        b.bottomStatsBar.visibility = View.VISIBLE
        updateWorkoutSetIndicator(state.setNumber, state.totalSets)
        host.viewModel.supervisor.reset()
        preferences.hasShownWeightDialog = false
        val targetReps = engine.getCurrentSetReps()
        val targetDurationMs = item.targetDuration?.takeIf { it > 0 }?.let { it * 1000L }
        val weight = engine.getCurrentSetWeight()
        val poseVariantIndex = item.variantIndex ?: 0
        if (!host.viewModel.loadExercise(
                exerciseName = slug,
                poseVariantIndex = poseVariantIndex,
                targetRepsOverride = targetReps,
                targetDurationMsOverride = targetDurationMs,
                context = host,
                weightKg = weight,
                weightUnit = "kg"
            )) {
            Log.e(tag, "Failed to load exercise for workout: $slug")
            Toast.makeText(
                host,
                host.getString(R.string.workout_exercise_load_failed),
                Toast.LENGTH_LONG
            ).show()
            host.setResult(Activity.RESULT_CANCELED)
            host.finish()
            return
        }
        host.updateCounterLabelForCurrentExercise()
        engine.startTraining()
        currentWorkoutSetRunId += 1L
        workoutSetStartTimeMs = System.currentTimeMillis()
        if (host.viewModel.feedbackManager == null) {
            host.viewModel.initializeFeedback(host, host.isVideoMode)
        }
        host.feedbackBinder.rebindVisualMessageFlow()
    }

    private fun hideAlternatingLabels() {
        val b = host.binding
        b.tvWorkoutAlternatingLabel.visibility = View.GONE
        b.tvAlternatingLabel.visibility = View.GONE
    }

    private fun updateWorkoutSetIndicator(setNumber: Int, totalSets: Int) {
        val b = host.binding
        b.tvWorkoutSetIndicator.text = host.getString(
            R.string.workout_set_indicator_format,
            setNumber, totalSets
        )
        b.tvWorkoutSetIndicator.visibility = View.VISIBLE
    }

    private fun showCelebrationMessage(message: String) {
        host.binding.glassmorphicMessage.showMessage(
            message,
            GlassmorphicMessageView.TYPE_MOTIVATION, 800
        )
        vibrateShort()
    }

    @Suppress("DEPRECATION")
    private fun vibrateShort() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = host.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE)
                as android.os.VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            host.getSystemService(android.content.Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(80)
        }
    }

    private fun getRestTip(restContext: WorkoutRestContext): String {
        val tips = if (restContext == WorkoutRestContext.BETWEEN_SETS) {
            listOf(
                host.getString(R.string.rest_tip_breathe),
                host.getString(R.string.rest_tip_quality),
                host.getString(R.string.rest_tip_focus)
            )
        } else {
            listOf(
                host.getString(R.string.rest_tip_next),
                host.getString(R.string.rest_tip_recovery),
                host.getString(R.string.rest_tip_consistency)
            )
        }
        return tips.random()
    }

    private fun playRestEndAlert() {
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
        tone.release()
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = host.getSystemService(android.os.VibratorManager::class.java)
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            host.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator?.hasVibrator() == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(200)
            }
        }
    }

    private fun showWorkoutComplete(report: WorkoutTrainingEngine.WorkoutReport) {
        hideWorkoutPanels()
        val b = host.binding
        b.WorkoutCompletePanel.visibility = View.VISIBLE
        b.setupPosePanel.visibility = View.GONE
        b.setupIndicatorBar.visibility = View.GONE
        b.countdownPanel.visibility = View.GONE
        b.heroCounterContainer.visibility = View.GONE
        b.completedPanel.visibility = View.GONE
        b.bottomStatsBar.visibility = View.GONE
        b.progressContainer.visibility = View.GONE
        b.vignetteOverlay.clear()
        b.skeletonOverlay.setTrainingMode(false)
        host.stopElapsedTimeTimer()
        val minutes = (report.totalDurationMs / 60000).toInt()
        val seconds = ((report.totalDurationMs % 60000) / 1000).toInt()
        b.tvWorkoutCompleteSets.text = host.getString(
            R.string.workout_complete_sets_format,
            report.totalSetsCompleted,
            report.totalSetsPlanned
        )
        b.tvWorkoutCompleteReps.text = host.getString(
            R.string.workout_complete_reps_format,
            report.totalReps
        )
        b.tvWorkoutCompleteDuration.text = host.getString(
            R.string.workout_complete_duration_format,
            minutes, seconds
        )
        b.tvWorkoutCompleteAccuracy.text = host.getString(
            R.string.workout_complete_accuracy_format,
            report.averageAccuracy.toInt()
        )
        b.tvWorkoutCompleteSubtitle.visibility = View.VISIBLE
        b.btnWorkoutDone.setOnClickListener { finishWorkoutWithResult(report) }
    }

    private fun onWorkoutSetCompleted() {
        val engine = workoutEngine ?: return
        val currentItem = engine.getCurrentExerciseItem() ?: return
        val trainingEng = host.viewModel.trainingEngine
        val reps = trainingEng?.getCurrentRep() ?: 0
        val accuracy = trainingEng?.getAccuracy() ?: 0f
        val durationMs = System.currentTimeMillis() - workoutSetStartTimeMs
        val weight = engine.getCurrentSetWeight()
        val targetReps = engine.getCurrentSetReps() ?: currentItem.targetReps ?: reps
        trainingEng?.stop()
        host.stopElapsedTimeTimer()
        val repDetails = trainingEng?.getRepResults()?.map { repResult ->
            val repDurationMs = repResult.phaseTimings.values.sum()
            RepDetail(
                repNumber = repResult.repNumber,
                score = repResult.score,
                worstState = repResult.worstState.ordinal,
                isCounted = repResult.isCounted,
                durationMs = repDurationMs
            )
        } ?: emptyList()
        val formScore = if (repDetails.isNotEmpty()) {
            repDetails.map { it.score }.average().toFloat()
        } else {
            accuracy
        }
        val metrics = SetMetrics(
            exerciseSlug = currentItem.exerciseSlug ?: "",
            exerciseIndex = engine.getCurrentExerciseIndex(),
            setNumber = engine.getCurrentSetNumber(),
            repsCompleted = reps,
            repsTarget = targetReps,
            durationMs = durationMs,
            accuracy = accuracy,
            formScore = formScore,
            weightKg = weight,
            repDetails = repDetails
        )
        Log.d(tag, "Workout set completed: ${metrics.exerciseSlug} " +
            "set ${metrics.setNumber}, reps=$reps, formScore=${formScore.toInt()}")
        engine.onSetCompleted(metrics)
    }

    private fun finishWorkoutWithResult(
        @Suppress("UNUSED_PARAMETER") initialReport: WorkoutTrainingEngine.WorkoutReport
    ) {
        host.lifecycleScope.launch {
            val jobs = synchronized(pendingReportJobs) { pendingReportJobs.toList() }
            if (jobs.isNotEmpty()) {
                Log.d(tag, "Waiting for ${jobs.size} pending report job(s) to complete...")
                jobs.forEach { it.join() }
            }
            val report = workoutEngine?.getCurrentReport() ?: initialReport
            val reportJson = Gson().toJson(report)
            val resultIntent = android.content.Intent().apply {
                putExtra(TrainingActivity.RESULT_IS_COMPLETED, true)
                putExtra(TrainingActivity.RESULT_DURATION_MS, report.totalDurationMs)
                putExtra(TrainingActivity.RESULT_WORKOUT_SETS_COMPLETED, report.totalSetsCompleted)
                putExtra(TrainingActivity.RESULT_WORKOUT_SETS_PLANNED, report.totalSetsPlanned)
                putExtra(TrainingActivity.RESULT_WORKOUT_TOTAL_REPS, report.totalReps)
                putExtra(TrainingActivity.RESULT_WORKOUT_AVG_ACCURACY, report.averageAccuracy)
                putExtra(TrainingActivity.RESULT_WORKOUT_AVG_FORM_SCORE, report.averageFormScore)
                putExtra(TrainingActivity.RESULT_WORKOUT_REPORT_JSON, reportJson)
                putStringArrayListExtra(
                    TrainingActivity.RESULT_WORKOUT_REPORT_IDS,
                    ArrayList(report.reportIds)
                )
                putStringArrayListExtra(
                    TrainingActivity.RESULT_WORKOUT_EXECUTION_IDS,
                    ArrayList(report.executionIds)
                )
            }
            host.setResult(Activity.RESULT_OK, resultIntent)
            host.finish()
        }
    }

    /**
     * [com.trainingvalidator.poc.training.workout.WorkoutTrainingEngine.OnExerciseCompletedListener] implementation
     * body (per-exercise report, sync, frame-capture handoff).
     */
    fun onExerciseCompletedFromEngine(
        exerciseIndex: Int,
        exerciseSlug: String,
        sets: List<SetMetrics>
    ) {
        val engine = host.viewModel.trainingEngine ?: run {
            Log.w(tag, "onExerciseCompleted: TrainingEngine is null, skipping report generation")
            return
        }
        val exerciseConfig = workoutExerciseConfigMap[exerciseSlug] ?: run {
            Log.w(tag, "onExerciseCompleted: ExerciseConfig not found for $exerciseSlug")
            return
        }
        val frameCaptures = host.frameCaptureManager?.getAllCaptures() ?: emptyList()
        val replayClips = host.frameCaptureManager?.getAllReplayClips() ?: emptyList()
        val executionDurationMs = sets.sumOf { it.durationMs }
        Log.d(tag, "onExerciseCompleted: Generating rich report for $exerciseSlug " +
            "(${sets.size} sets, ${frameCaptures.size} frames, ${replayClips.size} clips)")
        val job = host.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val workoutUpload = host.viewModel.finalizeAndGetWorkoutUpload()
                val executionMetrics = workoutUpload?.executionMetrics
                if (workoutUpload != null) {
                    workoutEngine?.setExerciseExecutionId(exerciseIndex, workoutUpload.id)
                    try {
                        host.reportCoordinator.syncWorkoutExecutionToBackend(workoutUpload.id, workoutUpload)
                    } catch (e: Exception) {
                        Log.w(tag, "Per-exercise sync failed for $exerciseSlug, will retry later", e)
                    }
                }
                val report = ReportGenerator.generateFromEngine(
                    engine = engine,
                    exerciseConfig = exerciseConfig,
                    executionDurationMs = executionDurationMs,
                    frameCaptures = frameCaptures,
                    replayClips = replayClips,
                    executionMetrics = executionMetrics,
                    weightKg = host.viewModel.getWeightKg(),
                    weightUnit = host.viewModel.getWeightUnit(),
                    allSets = sets
                )
                val saved = host.reportStorage?.save(report) ?: false
                Log.d(tag, "onExerciseCompleted: Report saved for $exerciseSlug: " +
                    "id=${report.id}, saved=$saved")
                if (saved) {
                    workoutEngine?.setExerciseReportId(exerciseIndex, report.id)
                }
            } catch (e: Exception) {
                Log.e(tag, "onExerciseCompleted: Failed to generate report for $exerciseSlug", e)
            }
        }
        synchronized(pendingReportJobs) { pendingReportJobs.add(job) }
        job.invokeOnCompletion { synchronized(pendingReportJobs) { pendingReportJobs.remove(job) } }
        host.frameCaptureController.resetForNextExercise()
    }

    private fun hideWorkoutPanels() {
        val b = host.binding
        b.WorkoutPreExercisePanel.visibility = View.GONE
        b.WorkoutRestPanel.visibility = View.GONE
        b.WorkoutCompletePanel.visibility = View.GONE
        b.tvWorkoutAlternatingLabel.visibility = View.GONE
        b.tvAlternatingLabel.visibility = View.GONE
        b.tvWorkoutSetIndicator.visibility = View.GONE
        b.tvWorkoutPrepCountdown.visibility = View.GONE
        b.btnWorkoutSkipPhase.visibility = View.GONE
        b.layoutWorkoutPrepRestControls.visibility = View.GONE
        workoutRestTimer?.cancel()
        pendingPrepRestKey = null
        workoutPrepRestPaused = false
    }

    fun showExitWorkoutDialog() {
        android.app.AlertDialog.Builder(host)
            .setTitle(host.getString(R.string.workout_exit_title))
            .setMessage(host.getString(R.string.workout_exit_message))
            .setPositiveButton(host.getString(R.string.workout_exit_keep)) { dialog, _ -> dialog.dismiss() }
            .setNegativeButton(host.getString(R.string.workout_exit_exit)) { dialog, _ ->
                dialog.dismiss()
                host.finish()
            }
            .setCancelable(true)
            .show()
    }

    fun tryHandleWorkoutSetCompleted() {
        if (currentWorkoutSetRunId <= 0L) {
            Log.w(tag, "Ignoring completion event without active set run id")
            return
        }
        if (lastCompletedWorkoutSetRunId == currentWorkoutSetRunId) {
            Log.d(
                tag,
                "Ignoring duplicate workout set completion event for runId=$currentWorkoutSetRunId"
            )
            return
        }
        lastCompletedWorkoutSetRunId = currentWorkoutSetRunId
        onWorkoutSetCompleted()
    }
}
