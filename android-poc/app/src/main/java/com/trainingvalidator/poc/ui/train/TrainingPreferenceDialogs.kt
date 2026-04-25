package com.trainingvalidator.poc.ui.train

import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
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
}
