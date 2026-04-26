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
import com.trainingvalidator.poc.training.models.ProgramSessionItem
import com.trainingvalidator.poc.training.report.ReportGenerator
import com.trainingvalidator.poc.training.session.SessionTrainingEngine
import com.trainingvalidator.poc.training.session.SessionTrainingEngine.SetMetrics
import com.trainingvalidator.poc.training.session.SessionTrainingEngine.RepDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

/**
 * Program/session mode: [SessionTrainingEngine] lifecycle, overlay panels, rest between sets,
 * per-exercise report generation, and session [Activity.setResult] payload.
 */
class TrainingSessionModeController(
    private val host: TrainingActivity,
    private val preferences: TrainingPreferenceDialogs
) {
    private val tag: String
        get() = TrainingActivity.TAG

    var sessionEngine: SessionTrainingEngine? = null
    private var sessionRestTimer: android.os.CountDownTimer? = null
    private var sessionRestRemainingMs: Long = 0L
    private var sessionSetStartTimeMs: Long = 0L
    private val pendingReportJobs = mutableListOf<Job>()

    @Suppress("MemberVisibilityCanBePrivate")
    val sessionExerciseConfigMap = mutableMapOf<String, ExerciseConfig>()

    private var currentSessionSetRunId: Long = 0L
    private var lastCompletedSessionSetRunId: Long = -1L

    fun onDestroy() {
        sessionRestTimer?.cancel()
    }

    fun cancelSessionRestOnClose() {
        sessionRestTimer?.cancel()
    }

    /**
     * Parse [TrainingActivity.EXTRA_SESSION_ITEMS_JSON] and start the program engine.
     */
    fun initializeFromIntent() {
        val json = host.intent.getStringExtra(TrainingActivity.EXTRA_SESSION_ITEMS_JSON)
        if (json.isNullOrBlank()) {
            Log.e(tag, "Session mode but no items JSON provided")
            host.finish()
            return
        }
        val gson = Gson()
        val itemsType = object : TypeToken<List<ProgramSessionItem>>() {}.type
        val items: List<ProgramSessionItem> = try {
            gson.fromJson(json, itemsType)
        } catch (e: Exception) {
            Log.e(tag, "Failed to parse session items JSON", e)
            host.finish()
            return
        }
        if (items.isEmpty()) {
            Log.w(tag, "Session items list is empty")
            host.finish()
            return
        }
        val invalidExerciseItem = items.firstOrNull { it.type == "exercise" && it.exerciseSlug.isNullOrBlank() }
        if (invalidExerciseItem != null) {
            Log.e(tag, "Invalid session payload: exercise item without exerciseSlug")
            Toast.makeText(
                host,
                host.getString(R.string.session_invalid_payload),
                Toast.LENGTH_LONG
            ).show()
            host.setResult(Activity.RESULT_CANCELED)
            host.finish()
            return
        }
        val engine = SessionTrainingEngine(items)
        val exerciseRepo = ExerciseRepository.getInstance(host)
        val language = host.currentLanguage
        sessionExerciseConfigMap.clear()
        items.filter { it.type == "exercise" && it.exerciseSlug != null }.forEach { item ->
            val slug = item.exerciseSlug ?: return@forEach
            val config = exerciseRepo.getExercise(slug)
            if (config != null) {
                sessionExerciseConfigMap[slug] = config
                val name = config.name.get(language).ifBlank { config.name.en }
                engine.setExerciseName(slug, name)
            }
        }
        sessionEngine = engine
        engine.onExerciseCompletedListener = host
        observeSessionEngineState()
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
            ?.let { sessionExerciseConfigMap[it]?.imageUrl }
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

    private fun buildSessionTargetSummaryLine(item: ProgramSessionItem): String {
        val sets = item.sets?.coerceAtLeast(1) ?: 1
        val base = when {
            item.targetDuration != null && item.targetDuration > 0 ->
                host.getString(R.string.session_target_hold_format, sets, item.targetDuration)
            item.targetReps != null && item.targetReps > 0 ->
                host.getString(R.string.session_target_reps_format, sets, item.targetReps)
            else ->
                host.getString(R.string.session_target_sets_only_format, sets)
        }
        val w = item.weightPerSet?.firstOrNull()?.takeIf { it > 0f }
            ?: item.weightKg?.takeIf { it > 0f }
        return if (w != null) {
            "$base · ${host.getString(R.string.session_weight_format, w)}"
        } else {
            base
        }
    }

    private fun observeSessionEngineState() {
        val engine = sessionEngine ?: return
        host.lifecycleScope.launch {
            engine.state.collectLatest { sessionState ->
                when (sessionState) {
                    is SessionTrainingEngine.State.Idle -> { }
                    is SessionTrainingEngine.State.PreExercise -> {
                        showSessionPreExercise(sessionState)
                    }
                    is SessionTrainingEngine.State.Training -> { }
                    is SessionTrainingEngine.State.SetRest -> {
                        showCelebrationMessage(host.getString(R.string.session_celebrate_set))
                        val setsTitle = host.getString(R.string.session_rest_between_sets)
                        showSessionRest(
                            durationMs = sessionState.durationMs,
                            title = setsTitle,
                            headline = sessionState.exerciseName,
                            detailLine = host.getString(
                                R.string.session_rest_next_set_format,
                                sessionState.exerciseName,
                                sessionState.nextSetNumber,
                                sessionState.totalSets
                            ),
                            previewSlug = sessionState.exerciseSlug,
                            instructionText = null,
                            tipTitleKey = setsTitle
                        )
                    }
                    is SessionTrainingEngine.State.ExerciseRest -> {
                        showCelebrationMessage(host.getString(R.string.session_celebrate_exercise))
                        val restTitle = host.getString(R.string.session_rest_between_exercises)
                        val lang = host.currentLanguage
                        val nextSlug = sessionState.nextExerciseSlug
                        val nextCfg = sessionExerciseConfigMap[nextSlug]
                        showSessionRest(
                            durationMs = sessionState.durationMs,
                            title = restTitle,
                            headline = sessionState.nextExerciseName,
                            detailLine = buildSessionTargetSummaryLine(sessionState.nextExerciseItem),
                            previewSlug = nextSlug,
                            instructionText = localizedInstruction(nextCfg, lang),
                            tipTitleKey = restTitle
                        )
                    }
                    is SessionTrainingEngine.State.SessionComplete -> {
                        showCelebrationMessage(host.getString(R.string.session_celebrate_session))
                        showSessionComplete(sessionState.report)
                    }
                }
            }
        }
    }

    private fun showSessionPreExercise(
        state: SessionTrainingEngine.State.PreExercise
    ) {
        hideSessionPanels()
        val b = host.binding
        b.sessionPreExercisePanel.visibility = View.VISIBLE
        b.setupPosePanel.visibility = View.GONE
        b.setupIndicatorBar.visibility = View.GONE
        b.countdownPanel.visibility = View.GONE
        b.heroCounterContainer.visibility = View.GONE
        b.completedPanel.visibility = View.GONE
        b.bottomStatsBar.visibility = View.GONE
        b.progressContainer.visibility = View.GONE
        val engine = sessionEngine ?: return
        val item = state.item
        b.tvSessionExerciseLabel.text = host.getString(
            R.string.session_exercise_label,
            state.exerciseIndex + 1,
            engine.getExerciseCount()
        )
        b.tvSessionExerciseName.text = state.exerciseName
        hideAlternatingLabels()
        updateSessionSetIndicator(state.setNumber, state.totalSets)
        val targetReps = item.targetReps
        val targetDuration = item.targetDuration
        b.tvSessionSetInfo.text = when {
            targetReps != null && targetReps > 0 -> host.getString(
                R.string.session_set_reps_format,
                state.setNumber, state.totalSets, targetReps
            )
            targetDuration != null && targetDuration > 0 -> host.getString(
                R.string.session_set_duration_format,
                state.setNumber, state.totalSets, targetDuration
            )
            else -> host.getString(
                R.string.session_set_only_format,
                state.setNumber, state.totalSets
            )
        }
        val weight = engine.getCurrentSetWeight()
        if (weight != null && weight > 0f) {
            b.tvSessionWeightInfo.text = host.getString(R.string.session_weight_format, weight)
            b.tvSessionWeightInfo.visibility = View.VISIBLE
        } else {
            b.tvSessionWeightInfo.visibility = View.GONE
        }
        val lang = host.currentLanguage
        val slug = item.exerciseSlug
        bindExercisePreviewImage(slug, b.ivSessionPreExercisePreview, b.ivSessionPreExerciseFallbackIcon)
        val cfg = slug?.let { sessionExerciseConfigMap[it] }
        val instruction = localizedInstruction(cfg, lang)
        if (!instruction.isNullOrBlank()) {
            b.tvSessionInstruction.text = instruction
            b.tvSessionInstruction.visibility = View.VISIBLE
        } else {
            b.tvSessionInstruction.visibility = View.GONE
        }
        b.btnSessionStartSet.text = when {
            state.exerciseIndex == 0 && state.setNumber == 1 ->
                host.getString(R.string.session_start_first_exercise)
            else ->
                host.getString(R.string.session_start_set_numbered, state.setNumber)
        }
        b.btnSessionStartSet.setOnClickListener {
            onSessionStartSetClicked(state)
        }
    }

    private fun onSessionStartSetClicked(
        state: SessionTrainingEngine.State.PreExercise
    ) {
        val engine = sessionEngine ?: return
        val item = state.item
        val slug = item.exerciseSlug ?: return
        hideSessionPanels()
        val b = host.binding
        b.bottomStatsBar.visibility = View.VISIBLE
        updateSessionSetIndicator(state.setNumber, state.totalSets)
        host.viewModel.supervisor.reset()
        preferences.hasShownWeightDialog = false
        val targetReps = item.targetReps?.takeIf { it > 0 }
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
            Log.e(tag, "Failed to load exercise for session: $slug")
            Toast.makeText(
                host,
                host.getString(R.string.session_exercise_load_failed),
                Toast.LENGTH_LONG
            ).show()
            host.setResult(Activity.RESULT_CANCELED)
            host.finish()
            return
        }
        host.updateCounterLabelForCurrentExercise()
        engine.startTraining()
        currentSessionSetRunId += 1L
        sessionSetStartTimeMs = System.currentTimeMillis()
        preferences.maybeShowSessionWeightDialog()
        if (host.viewModel.feedbackManager == null) {
            host.viewModel.initializeFeedback(host, host.isVideoMode)
        }
        host.feedbackBinder.rebindVisualMessageFlow()
    }

    private fun hideAlternatingLabels() {
        val b = host.binding
        b.tvSessionAlternatingLabel.visibility = View.GONE
        b.tvAlternatingLabel.visibility = View.GONE
    }

    private fun updateSessionSetIndicator(setNumber: Int, totalSets: Int) {
        val b = host.binding
        b.tvSessionSetIndicator.text = host.getString(
            R.string.session_set_indicator_format,
            setNumber, totalSets
        )
        b.tvSessionSetIndicator.visibility = View.VISIBLE
    }

    private fun showSessionRest(
        durationMs: Long,
        title: String,
        headline: String,
        detailLine: String,
        previewSlug: String,
        instructionText: String?,
        tipTitleKey: String
    ) {
        hideSessionPanels()
        val b = host.binding
        b.sessionRestPanel.visibility = View.VISIBLE
        b.setupPosePanel.visibility = View.GONE
        b.setupIndicatorBar.visibility = View.GONE
        b.countdownPanel.visibility = View.GONE
        b.heroCounterContainer.visibility = View.GONE
        b.completedPanel.visibility = View.GONE
        b.bottomStatsBar.visibility = View.GONE
        b.progressContainer.visibility = View.GONE
        b.vignetteOverlay.clear()
        b.skeletonOverlay.setTrainingMode(false)
        b.tvSessionRestTitle.text = title
        b.tvSessionRestHeadline.text = headline
        b.tvSessionRestNext.text = detailLine
        bindExercisePreviewImage(
            previewSlug.takeIf { it.isNotBlank() },
            b.ivSessionRestPreview,
            b.ivSessionRestFallbackIcon
        )
        if (!instructionText.isNullOrBlank()) {
            b.tvSessionRestInstruction.text = instructionText
            b.tvSessionRestInstruction.visibility = View.VISIBLE
        } else {
            b.tvSessionRestInstruction.visibility = View.GONE
        }
        b.tvSessionRestTip.text = getRestTip(tipTitleKey)
        startSessionRestTimer(durationMs)
        b.btnSessionAddTime.setOnClickListener {
            startSessionRestTimer(sessionRestRemainingMs + 20000L)
        }
        b.btnSessionEditRest.setOnClickListener { showEditRestDialog() }
        b.btnSessionSkipRest.setOnClickListener {
            sessionRestTimer?.cancel()
            sessionEngine?.onRestCompleted()
        }
    }

    private fun startSessionRestTimer(durationMs: Long) {
        sessionRestTimer?.cancel()
        sessionRestRemainingMs = durationMs
        val b = host.binding
        sessionRestTimer = object : android.os.CountDownTimer(durationMs, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val secs = (millisUntilFinished / 1000).toInt()
                val m = secs / 60
                val s = secs % 60
                b.tvSessionRestCountdown.text = String.format("%02d:%02d", m, s)
                sessionRestRemainingMs = millisUntilFinished
            }
            override fun onFinish() {
                b.tvSessionRestCountdown.text = "00:00"
                sessionRestRemainingMs = 0L
                playRestEndAlert()
                sessionEngine?.onRestCompleted()
            }
        }.start()
    }

    private fun showEditRestDialog() {
        val input = android.widget.EditText(host).apply {
            hint = host.getString(R.string.rest_seconds_hint)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText((sessionRestRemainingMs / 1000L).toString())
        }
        android.app.AlertDialog.Builder(host)
            .setTitle(host.getString(R.string.edit_rest))
            .setView(input)
            .setPositiveButton(host.getString(R.string.save)) { dialog, _ ->
                val seconds = input.text.toString().toLongOrNull()
                if (seconds != null && seconds > 0) {
                    startSessionRestTimer(seconds * 1000L)
                }
                dialog.dismiss()
            }
            .setNegativeButton(host.getString(R.string.cancel)) { dialog, _ -> dialog.dismiss() }
            .show()
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

    private fun getRestTip(title: String): String {
        val tips = if (title.contains(host.getString(R.string.session_rest_between_sets), true)) {
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

    private fun showSessionComplete(report: SessionTrainingEngine.SessionReport) {
        hideSessionPanels()
        val b = host.binding
        b.sessionCompletePanel.visibility = View.VISIBLE
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
        b.tvSessionCompleteSets.text = host.getString(
            R.string.session_complete_sets_format,
            report.totalSetsCompleted,
            report.totalSetsPlanned
        )
        b.tvSessionCompleteReps.text = host.getString(
            R.string.session_complete_reps_format,
            report.totalReps
        )
        b.tvSessionCompleteDuration.text = host.getString(
            R.string.session_complete_duration_format,
            minutes, seconds
        )
        b.tvSessionCompleteAccuracy.text = host.getString(
            R.string.session_complete_accuracy_format,
            report.averageAccuracy.toInt()
        )
        b.tvSessionCompleteSubtitle.visibility = View.VISIBLE
        b.btnSessionDone.setOnClickListener { finishSessionWithResult(report) }
    }

    private fun onSessionSetCompleted() {
        val engine = sessionEngine ?: return
        val currentItem = engine.getCurrentExerciseItem() ?: return
        val trainingEng = host.viewModel.trainingEngine
        val reps = trainingEng?.getCurrentRep() ?: 0
        val accuracy = trainingEng?.getAccuracy() ?: 0f
        val durationMs = System.currentTimeMillis() - sessionSetStartTimeMs
        val weight = engine.getCurrentSetWeight()
        val targetReps = currentItem.targetReps ?: reps
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
        Log.d(tag, "Session set completed: ${metrics.exerciseSlug} " +
            "set ${metrics.setNumber}, reps=$reps, formScore=${formScore.toInt()}")
        engine.onSetCompleted(metrics)
    }

    private fun finishSessionWithResult(
        @Suppress("UNUSED_PARAMETER") initialReport: SessionTrainingEngine.SessionReport
    ) {
        host.lifecycleScope.launch {
            val jobs = synchronized(pendingReportJobs) { pendingReportJobs.toList() }
            if (jobs.isNotEmpty()) {
                Log.d(tag, "Waiting for ${jobs.size} pending report job(s) to complete...")
                jobs.forEach { it.join() }
            }
            val report = sessionEngine?.getCurrentReport() ?: initialReport
            val reportJson = Gson().toJson(report)
            val resultIntent = android.content.Intent().apply {
                putExtra(TrainingActivity.RESULT_IS_COMPLETED, true)
                putExtra(TrainingActivity.RESULT_DURATION_MS, report.totalDurationMs)
                putExtra(TrainingActivity.RESULT_SESSION_SETS_COMPLETED, report.totalSetsCompleted)
                putExtra(TrainingActivity.RESULT_SESSION_SETS_PLANNED, report.totalSetsPlanned)
                putExtra(TrainingActivity.RESULT_SESSION_TOTAL_REPS, report.totalReps)
                putExtra(TrainingActivity.RESULT_SESSION_AVG_ACCURACY, report.averageAccuracy)
                putExtra(TrainingActivity.RESULT_SESSION_AVG_FORM_SCORE, report.averageFormScore)
                putExtra(TrainingActivity.RESULT_SESSION_REPORT_JSON, reportJson)
                putStringArrayListExtra(
                    TrainingActivity.RESULT_SESSION_REPORT_IDS,
                    ArrayList(report.reportIds)
                )
                putStringArrayListExtra(
                    TrainingActivity.RESULT_SESSION_SESSION_IDS,
                    ArrayList(report.sessionIds)
                )
            }
            host.setResult(Activity.RESULT_OK, resultIntent)
            host.finish()
        }
    }

    /**
     * [com.trainingvalidator.poc.training.session.SessionTrainingEngine.OnExerciseCompletedListener] implementation
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
        val exerciseConfig = sessionExerciseConfigMap[exerciseSlug] ?: run {
            Log.w(tag, "onExerciseCompleted: ExerciseConfig not found for $exerciseSlug")
            return
        }
        val frameCaptures = host.frameCaptureManager?.getAllCaptures() ?: emptyList()
        val replayClips = host.frameCaptureManager?.getAllReplayClips() ?: emptyList()
        val sessionDurationMs = sets.sumOf { it.durationMs }
        Log.d(tag, "onExerciseCompleted: Generating rich report for $exerciseSlug " +
            "(${sets.size} sets, ${frameCaptures.size} frames, ${replayClips.size} clips)")
        val job = host.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val sessionUpload = host.viewModel.finalizeAndGetSessionUpload()
                val sessionMetrics = sessionUpload?.sessionMetrics
                if (sessionUpload != null) {
                    sessionEngine?.setExerciseSessionId(exerciseIndex, sessionUpload.id)
                    try {
                        host.reportCoordinator.syncSessionToBackendStandalone(sessionUpload.id, sessionUpload)
                    } catch (e: Exception) {
                        Log.w(tag, "Per-exercise sync failed for $exerciseSlug, will retry later", e)
                    }
                }
                val report = ReportGenerator.generateFromEngine(
                    engine = engine,
                    exerciseConfig = exerciseConfig,
                    sessionDurationMs = sessionDurationMs,
                    frameCaptures = frameCaptures,
                    replayClips = replayClips,
                    sessionMetrics = sessionMetrics,
                    weightKg = host.viewModel.getWeightKg(),
                    weightUnit = host.viewModel.getWeightUnit(),
                    allSets = sets
                )
                val saved = host.reportStorage?.save(report) ?: false
                Log.d(tag, "onExerciseCompleted: Report saved for $exerciseSlug: " +
                    "id=${report.id}, saved=$saved")
                if (saved) {
                    sessionEngine?.setExerciseReportId(exerciseIndex, report.id)
                }
            } catch (e: Exception) {
                Log.e(tag, "onExerciseCompleted: Failed to generate report for $exerciseSlug", e)
            }
        }
        synchronized(pendingReportJobs) { pendingReportJobs.add(job) }
        job.invokeOnCompletion { synchronized(pendingReportJobs) { pendingReportJobs.remove(job) } }
        host.frameCaptureController.resetForNextExercise()
    }

    private fun hideSessionPanels() {
        val b = host.binding
        b.sessionPreExercisePanel.visibility = View.GONE
        b.sessionRestPanel.visibility = View.GONE
        b.sessionCompletePanel.visibility = View.GONE
        b.tvSessionAlternatingLabel.visibility = View.GONE
        b.tvAlternatingLabel.visibility = View.GONE
        b.tvSessionSetIndicator.visibility = View.GONE
        sessionRestTimer?.cancel()
    }

    fun showExitSessionDialog() {
        android.app.AlertDialog.Builder(host)
            .setTitle(host.getString(R.string.session_exit_title))
            .setMessage(host.getString(R.string.session_exit_message))
            .setPositiveButton(host.getString(R.string.session_exit_keep)) { dialog, _ -> dialog.dismiss() }
            .setNegativeButton(host.getString(R.string.session_exit_exit)) { dialog, _ ->
                dialog.dismiss()
                host.finish()
            }
            .setCancelable(true)
            .show()
    }

    fun tryHandleSessionSetCompleted() {
        if (currentSessionSetRunId <= 0L) {
            Log.w(tag, "Ignoring completion event without active set run id")
            return
        }
        if (lastCompletedSessionSetRunId == currentSessionSetRunId) {
            Log.d(
                tag,
                "Ignoring duplicate session completion event for runId=$currentSessionSetRunId"
            )
            return
        }
        lastCompletedSessionSetRunId = currentSessionSetRunId
        onSessionSetCompleted()
    }
}
