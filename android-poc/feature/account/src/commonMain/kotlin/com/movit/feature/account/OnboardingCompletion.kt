package com.movit.feature.account

import com.movit.core.data.platform.MovitPlatformBindings
import com.movit.core.network.dto.TrainingProfilePayloadDto
import com.movit.shared.AppResult
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

/**
 * Parity with legacy [com.trainingvalidator.poc.ui.onboarding.OnboardingGate]:
 * local cache first, then backend TrainingProfile core fields.
 */
object OnboardingCompletion {
    val CORE_PROFILE_FIELDS = listOf(
        "dateOfBirth",
        "biologicalSex",
        "heightCm",
        "weightKg",
    )

    fun isTrainingProfileComplete(payload: TrainingProfilePayloadDto): Boolean {
        val profile = payload.profile ?: return false
        return CORE_PROFILE_FIELDS.all { field ->
            profile[field].isPresentJsonValue()
        }
    }

    /**
     * @return true when the user must still complete profile onboarding.
     * On network/API failure, fail-open (returns false) so users are not wrongly blocked.
     */
    suspend fun resolveNeedsOnboarding(
        platform: MovitPlatformBindings,
        fetchProfile: suspend () -> AppResult<TrainingProfilePayloadDto>,
    ): Boolean {
        if (platform.isOnboardingCompleted()) return false
        return when (val result = fetchProfile()) {
            is AppResult.Success -> {
                val complete = isTrainingProfileComplete(result.value)
                if (complete) {
                    platform.setOnboardingCompleted(true)
                }
                !complete
            }
            is AppResult.Failure -> false
        }
    }

    private fun JsonElement?.isPresentJsonValue(): Boolean =
        this != null && this !is JsonNull
}
