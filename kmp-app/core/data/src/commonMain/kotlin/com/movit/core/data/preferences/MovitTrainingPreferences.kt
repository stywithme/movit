package com.movit.core.data.preferences

import com.movit.core.data.cache.MovitCachePolicy
import com.movit.core.data.local.MovitLocalStore
import com.movit.core.data.repository.MovitCacheKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

/**
 * Cross-platform training session preferences (replaces legacy SettingsManager runtime prefs).
 */
@Serializable
data class MovitTrainingPreferencesState(
    val modelType: String = "full",
    val indicatorType: String = "arc",
    val voiceFeedbackEnabled: Boolean = true,
    val coachIntensity: String = "standard",
    val smoothingPreset: String = "custom",
    val smoothingMinCutoff: Float = 1.0f,
    val smoothingBeta: Float = 1.5f,
    val useLegacySmoothing: Boolean = false,
    val legacySmoothingAlpha: Float = 1.0f,
    val trainingDisplayMode: String = "beginner",
)

class MovitTrainingPreferences(
    private val localStore: MovitLocalStore,
) {
    private val _state = MutableStateFlow(readPersisted())
    val state: Flow<MovitTrainingPreferencesState> = _state.asStateFlow()

    fun snapshot(): MovitTrainingPreferencesState = _state.value

    fun setModelType(type: String) = update { it.copy(modelType = normalizeModelType(type)) }

    fun setIndicatorType(type: String) = update { it.copy(indicatorType = normalizeIndicatorType(type)) }

    fun setVoiceFeedbackEnabled(enabled: Boolean) = update { it.copy(voiceFeedbackEnabled = enabled) }

    fun setCoachIntensity(intensity: String) = update { it.copy(coachIntensity = normalizeCoachIntensity(intensity)) }

    fun setTrainingDisplayMode(mode: String) = update {
        it.copy(trainingDisplayMode = if (mode == "advanced") "advanced" else "beginner")
    }

    private fun update(transform: (MovitTrainingPreferencesState) -> MovitTrainingPreferencesState) {
        _state.update { current ->
            val next = transform(current)
            persist(next)
            next
        }
    }

    private fun readPersisted(): MovitTrainingPreferencesState =
        MovitCachePolicy.readJson(
            localStore,
            MovitCacheKeys.TRAINING_PREFERENCES_STORE,
            MovitCacheKeys.TRAINING_PREFERENCES_JSON,
            MovitTrainingPreferencesState.serializer(),
        ) ?: MovitTrainingPreferencesState()

    private fun persist(state: MovitTrainingPreferencesState) {
        MovitCachePolicy.writeJson(
            localStore,
            MovitCacheKeys.TRAINING_PREFERENCES_STORE,
            MovitCacheKeys.TRAINING_PREFERENCES_JSON,
            state,
            MovitTrainingPreferencesState.serializer(),
        )
    }

    companion object {
        fun normalizeModelType(raw: String): String =
            if (raw.equals("heavy", ignoreCase = true)) "heavy" else "full"

        fun normalizeIndicatorType(raw: String): String =
            if (raw.equals("line", ignoreCase = true)) "line" else "arc"

        fun normalizeCoachIntensity(raw: String?): String = when (raw?.trim()?.lowercase()) {
            "calm" -> "calm"
            "strict" -> "strict"
            else -> "standard"
        }
    }
}
