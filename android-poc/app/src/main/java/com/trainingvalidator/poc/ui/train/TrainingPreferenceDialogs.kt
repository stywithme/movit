package com.trainingvalidator.poc.ui.train

import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.network.ApiClient
import com.trainingvalidator.poc.network.UserExercisePreferenceUpsertRequest
import com.trainingvalidator.poc.storage.AuthManager
import com.trainingvalidator.poc.storage.ExerciseRepository
import com.trainingvalidator.poc.storage.UserExercisePreferenceStore
import com.trainingvalidator.poc.training.config.SettingsManager
import com.trainingvalidator.poc.training.models.CountingMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Pre-training and session weight dialogs, plus one-way sync of local exercise preferences
 * to the mobile API (best-effort).
 */
class TrainingPreferenceDialogs(
    private val host: TrainingActivity
) {
    @Volatile
    var isWeightDialogVisible: Boolean = false

    var hasShownWeightDialog: Boolean = false
    var hasShownPreTrainingDialog: Boolean = false

    /**
     * Session mode: new exercise/set — allow weight dialog again.
     */
    fun resetSessionWeightDialogFlag() {
        hasShownWeightDialog = false
    }

    /**
     * Session mode only: confirm weight before set (legacy dialog).
     */
    fun maybeShowSessionWeightDialog() {
        val config = host.viewModel.exerciseConfig.value ?: return
        if (!config.supportsWeight || hasShownWeightDialog) return

        hasShownWeightDialog = true
        isWeightDialogVisible = true

        val dialogView = host.layoutInflater.inflate(R.layout.dialog_weight_confirm, null)
        val dialog = android.app.AlertDialog.Builder(host, R.style.Theme_WayToFix_Dialog)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        val inputLayout = dialogView.findViewById<TextInputLayout>(R.id.inputWeightLayout)
        val inputField = dialogView.findViewById<TextInputEditText>(R.id.inputWeight)
        val tvRange = dialogView.findViewById<TextView>(R.id.tvWeightRange)
        val btnStart = dialogView.findViewById<MaterialButton>(R.id.btnWeightStart)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnWeightCancel)

        val defaultWeight = host.viewModel.getWeightKg() ?: config.defaultWeight
        val minWeight = config.minWeight
        val maxWeight = config.maxWeight

        inputField.setText(defaultWeight?.toString().orEmpty())

        val rangeText = when {
            minWeight != null && maxWeight != null ->
                host.getString(R.string.weight_min_max_format, minWeight, maxWeight)
            minWeight != null ->
                host.getString(R.string.weight_min_only_format, minWeight)
            maxWeight != null ->
                host.getString(R.string.weight_max_only_format, maxWeight)
            else -> ""
        }
        tvRange.text = rangeText
        tvRange.visibility = if (rangeText.isNotEmpty()) View.VISIBLE else View.GONE

        btnStart.setOnClickListener {
            val rawInput = inputField.text?.toString()?.trim().orEmpty()
            val parsed = rawInput.toFloatOrNull() ?: defaultWeight

            if (parsed != null) {
                if (minWeight != null && parsed < minWeight) {
                    inputLayout.error = host.getString(R.string.weight_min_error, minWeight)
                    return@setOnClickListener
                }
                if (maxWeight != null && parsed > maxWeight) {
                    inputLayout.error = host.getString(R.string.weight_max_error, maxWeight)
                    return@setOnClickListener
                }
            }

            inputLayout.error = null
            host.viewModel.updateSessionWeight(parsed, "kg")
            isWeightDialogVisible = false
            dialog.dismiss()
        }

        btnCancel.setOnClickListener {
            host.viewModel.updateSessionWeight(null, "kg")
            isWeightDialogVisible = false
            dialog.dismiss()
        }

        dialog.setOnDismissListener { isWeightDialogVisible = false }

        dialog.show()
    }

    /**
     * Single-exercise mode: reps / hold duration / weight before training starts.
     */
    fun maybeShowPreTrainingDialog() {
        if (hasShownPreTrainingDialog) return
        val config = host.viewModel.exerciseConfig.value ?: return
        hasShownPreTrainingDialog = true
        isWeightDialogVisible = true

        val slug = config.fileName.ifEmpty { host.viewModel.exerciseName.value }
        val store = UserExercisePreferenceStore(host)
        val saved = store.get(slug)

        val dialogView = host.layoutInflater.inflate(R.layout.dialog_pre_training, null)
        val dialog = android.app.AlertDialog.Builder(host, R.style.Theme_WayToFix_Dialog)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        val sectionReps = dialogView.findViewById<LinearLayout>(R.id.sectionReps)
        val sectionDuration = dialogView.findViewById<LinearLayout>(R.id.sectionDuration)
        val sectionWeight = dialogView.findViewById<LinearLayout>(R.id.sectionWeight)
        val inputRepsLayout = dialogView.findViewById<TextInputLayout>(R.id.inputRepsLayout)
        val inputReps = dialogView.findViewById<TextInputEditText>(R.id.inputReps)
        val inputDurationLayout = dialogView.findViewById<TextInputLayout>(R.id.inputDurationLayout)
        val inputDuration = dialogView.findViewById<TextInputEditText>(R.id.inputDuration)
        val inputWeightLayout = dialogView.findViewById<TextInputLayout>(R.id.inputWeightLayout)
        val inputWeight = dialogView.findViewById<TextInputEditText>(R.id.inputWeight)
        val tvWeightRange = dialogView.findViewById<TextView>(R.id.tvWeightRange)
        val btnStart = dialogView.findViewById<MaterialButton>(R.id.btnPreTrainingStart)
        val btnReset = dialogView.findViewById<MaterialButton>(R.id.btnPreTrainingReset)

        when (config.countingMethod) {
            CountingMethod.HOLD -> {
                sectionDuration.visibility = View.VISIBLE
                sectionReps.visibility = View.GONE
            }
            else -> {
                sectionReps.visibility = View.VISIBLE
                sectionDuration.visibility = View.GONE
            }
        }
        sectionWeight.visibility = if (config.supportsWeight) View.VISIBLE else View.GONE

        val defaultReps = config.repCountingConfig.reps
        val defaultDurationSec = config.repCountingConfig.duration
            ?: SettingsManager.getDefaultHoldDuration()

        val initialReps = saved?.customReps ?: defaultReps
        val initialDurationSec = saved?.customDurationSec ?: defaultDurationSec
        val initialWeight = saved?.customWeightKg ?: host.viewModel.getWeightKg() ?: config.defaultWeight

        inputReps.setText(initialReps.toString())
        inputDuration.setText(initialDurationSec.toString())
        inputWeight.setText(initialWeight?.toString().orEmpty())

        if (config.supportsWeight) {
            val minW = config.minWeight
            val maxW = config.maxWeight
            val rangeText = when {
                minW != null && maxW != null ->
                    host.getString(R.string.weight_min_max_format, minW, maxW)
                minW != null ->
                    host.getString(R.string.weight_min_only_format, minW)
                maxW != null ->
                    host.getString(R.string.weight_max_only_format, maxW)
                else -> ""
            }
            tvWeightRange.text = rangeText
            tvWeightRange.visibility = if (rangeText.isNotEmpty()) View.VISIBLE else View.GONE
        }

        fun applyAndDismiss(reps: Int?, durationSec: Int?, weight: Float?) {
            val repsOverride = if (config.countingMethod != CountingMethod.HOLD) reps else null
            val durationMs = if (config.countingMethod == CountingMethod.HOLD) {
                durationSec?.takeIf { it > 0 }?.times(1000L)
            } else {
                null
            }

            val stored = UserExercisePreferenceStore.Stored(
                customReps = repsOverride,
                customDurationSec = if (config.countingMethod == CountingMethod.HOLD) {
                    durationSec?.takeIf { it > 0 }
                } else {
                    null
                },
                customWeightKg = if (config.supportsWeight) weight else null,
                updatedAt = null
            )
            val hasAny = stored.customReps != null || stored.customDurationSec != null || stored.customWeightKg != null
            if (hasAny) {
                store.save(slug, stored)
            } else {
                store.remove(slug)
            }

            host.viewModel.rebuildTrainingEngineWithOverrides(
                targetRepsOverride = repsOverride,
                targetDurationMsOverride = durationMs,
                weightKg = if (config.supportsWeight) weight else null,
                weightUnit = "kg"
            )
            pushUserExercisePreferenceToBackend(slug, stored, clearOnServer = !hasAny)
            isWeightDialogVisible = false
            dialog.dismiss()
        }

        btnStart.setOnClickListener {
            inputRepsLayout.error = null
            inputDurationLayout.error = null
            inputWeightLayout.error = null

            val repsParsed = inputReps.text?.toString()?.trim()?.toIntOrNull()
            val durParsed = inputDuration.text?.toString()?.trim()?.toIntOrNull()
            val weightParsed = inputWeight.text?.toString()?.trim().orEmpty().toFloatOrNull()

            if (config.countingMethod != CountingMethod.HOLD) {
                val tr = repsParsed ?: defaultReps
                if (tr <= 0) {
                    inputRepsLayout.error = "Invalid reps"
                    return@setOnClickListener
                }
            } else {
                val td = durParsed ?: defaultDurationSec
                if (td <= 0) {
                    inputDurationLayout.error = "Invalid duration"
                    return@setOnClickListener
                }
            }

            if (config.supportsWeight && weightParsed != null) {
                val minW = config.minWeight
                val maxW = config.maxWeight
                if (minW != null && weightParsed < minW) {
                    inputWeightLayout.error = host.getString(R.string.weight_min_error, minW)
                    return@setOnClickListener
                }
                if (maxW != null && weightParsed > maxW) {
                    inputWeightLayout.error = host.getString(R.string.weight_max_error, maxW)
                    return@setOnClickListener
                }
            }

            val finalReps = if (config.countingMethod != CountingMethod.HOLD) {
                (repsParsed ?: defaultReps).coerceAtLeast(1)
            } else {
                null
            }
            val finalDur = if (config.countingMethod == CountingMethod.HOLD) {
                (durParsed ?: defaultDurationSec).coerceAtLeast(1)
            } else {
                null
            }
            val finalWeight = if (config.supportsWeight) weightParsed else null

            applyAndDismiss(finalReps, finalDur, finalWeight)
        }

        btnReset.setOnClickListener {
            store.remove(slug)
            host.viewModel.rebuildTrainingEngineWithOverrides(
                targetRepsOverride = null,
                targetDurationMsOverride = null,
                weightKg = if (config.supportsWeight) config.defaultWeight else null,
                weightUnit = "kg"
            )
            pushUserExercisePreferenceDelete(slug)
            inputReps.setText(config.repCountingConfig.reps.toString())
            inputDuration.setText(
                (config.repCountingConfig.duration ?: SettingsManager.getDefaultHoldDuration()).toString()
            )
            inputWeight.setText(config.defaultWeight?.toString().orEmpty())
        }

        dialog.setOnDismissListener { isWeightDialogVisible = false }

        dialog.show()
    }

    fun pushUserExercisePreferenceToBackend(
        slug: String,
        stored: UserExercisePreferenceStore.Stored,
        clearOnServer: Boolean
    ) {
        host.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val auth = AuthManager.getAuthHeader(host) ?: return@launch
                val exerciseId = ExerciseRepository.getInstance(host.applicationContext).getExerciseServerId(slug)
                    ?: return@launch
                if (clearOnServer) {
                    ApiClient.mobileSyncApi.deleteExercisePreference(exerciseId, auth)
                } else {
                    ApiClient.mobileSyncApi.upsertExercisePreference(
                        exerciseId,
                        auth,
                        UserExercisePreferenceUpsertRequest(
                            customReps = stored.customReps,
                            customDurationSec = stored.customDurationSec,
                            customWeightKg = stored.customWeightKg
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TrainingActivity.TAG, "pushUserExercisePreferenceToBackend failed", e)
            }
        }
    }

    fun pushUserExercisePreferenceDelete(slug: String) {
        host.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val auth = AuthManager.getAuthHeader(host) ?: return@launch
                val exerciseId = ExerciseRepository.getInstance(host.applicationContext).getExerciseServerId(slug)
                    ?: return@launch
                ApiClient.mobileSyncApi.deleteExercisePreference(exerciseId, auth)
            } catch (e: Exception) {
                Log.w(TrainingActivity.TAG, "pushUserExercisePreferenceDelete failed", e)
            }
        }
    }

    /**
     * Multi-set weighted exercise: collect per-set weights (or same for all) before starting the first set.
     */
    fun showSessionPerSetWeightDialogIfNeeded(
        totalSets: Int,
        suggestedWeight: Float?,
        minWeight: Float?,
        maxWeight: Float?,
        onApply: (List<Float>) -> Unit,
        onCancel: () -> Unit
    ) {
        if (totalSets <= 1) return

        val pad = (16 * host.resources.displayMetrics.density).toInt()
        val scroll = android.widget.ScrollView(host)
        val root = android.widget.LinearLayout(host).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        scroll.addView(root)

        val sameSwitch = SwitchCompat(host).apply {
            text = host.getString(R.string.weight_same_for_all_sets)
            isChecked = true
        }
        root.addView(sameSwitch)

        val singleLayout = com.google.android.material.textfield.TextInputLayout(host).apply {
            hint = host.getString(R.string.weight_input_hint)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = pad / 2 }
        }
        val singleInput = com.google.android.material.textfield.TextInputEditText(host).apply {
            if (suggestedWeight != null && suggestedWeight > 0f) {
                setText(suggestedWeight.toString())
            }
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        singleLayout.addView(singleInput)
        root.addView(singleLayout)

        val perContainer = android.widget.LinearLayout(host).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            visibility = android.view.View.GONE
        }
        repeat(totalSets) { idx ->
            val til = com.google.android.material.textfield.TextInputLayout(host).apply {
                hint = host.getString(R.string.weight_set_number_hint, idx + 1)
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = pad / 4 }
            }
            val et = com.google.android.material.textfield.TextInputEditText(host).apply {
                setText(singleInput.text?.toString().orEmpty())
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            }
            til.addView(et)
            perContainer.addView(til)
        }
        root.addView(perContainer)

        fun syncVisibility() {
            val same = sameSwitch.isChecked
            singleLayout.visibility = if (same) android.view.View.VISIBLE else android.view.View.GONE
            perContainer.visibility = if (same) android.view.View.GONE else android.view.View.VISIBLE
        }
        sameSwitch.setOnCheckedChangeListener { _, _ -> syncVisibility() }
        syncVisibility()

        fun validateOne(w: Float): String? {
            if (w <= 0f) return host.getString(R.string.weight_invalid)
            if (minWeight != null && w < minWeight) return host.getString(R.string.weight_min_error, minWeight)
            if (maxWeight != null && w > maxWeight) return host.getString(R.string.weight_max_error, maxWeight)
            return null
        }

        val dialog = android.app.AlertDialog.Builder(host, R.style.Theme_WayToFix_Dialog)
            .setTitle(host.getString(R.string.weight_per_set_dialog_title))
            .setView(scroll)
            .setCancelable(true)
            .setNegativeButton(android.R.string.cancel) { _, _ -> onCancel() }
            .setPositiveButton(R.string.weight_start, null)
            .create()

        dialog.setOnShowListener {
            val btn = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            btn.setOnClickListener {
                if (sameSwitch.isChecked) {
                    val w = singleInput.text?.toString()?.trim()?.toFloatOrNull()
                    if (w == null) {
                        singleLayout.error = host.getString(R.string.weight_invalid)
                        return@setOnClickListener
                    }
                    val err = validateOne(w)
                    if (err != null) {
                        singleLayout.error = err
                        return@setOnClickListener
                    }
                    singleLayout.error = null
                    onApply(List(totalSets) { w })
                    dialog.dismiss()
                } else {
                    val weights = mutableListOf<Float>()
                    for (i in 0 until totalSets) {
                        val til = perContainer.getChildAt(i) as com.google.android.material.textfield.TextInputLayout
                        val et = til.editText ?: return@setOnClickListener
                        val w = et.text?.toString()?.trim()?.toFloatOrNull()
                        if (w == null) {
                            til.error = host.getString(R.string.weight_invalid)
                            return@setOnClickListener
                        }
                        val err = validateOne(w)
                        if (err != null) {
                            til.error = err
                            return@setOnClickListener
                        }
                        til.error = null
                        weights.add(w)
                    }
                    onApply(weights)
                    dialog.dismiss()
                }
            }
        }
        dialog.setOnCancelListener { onCancel() }
        dialog.show()
    }
}
