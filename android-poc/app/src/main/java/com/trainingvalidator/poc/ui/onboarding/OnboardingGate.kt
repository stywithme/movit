package com.trainingvalidator.poc.ui.onboarding

import android.content.Context
import com.trainingvalidator.poc.network.ApiClient
import com.trainingvalidator.poc.storage.AuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decides whether the signed-in user still needs to complete profile onboarding.
 *
 * Source of truth is the backend TrainingProfile (core demographic fields). A local
 * [AuthManager] flag short-circuits repeat checks once completion is confirmed.
 */
object OnboardingGate {

    private val CORE_FIELDS = listOf("dateOfBirth", "biologicalSex", "heightCm", "weightKg")

    /**
     * Returns true when the trainee profile already has the core onboarding fields.
     * On network errors returns true (fail-open) so users are never wrongly forced into onboarding.
     */
    suspend fun isProfileComplete(context: Context): Boolean {
        if (AuthManager.isOnboardingCompleted(context)) return true
        val authHeader = AuthManager.getAuthHeader(context) ?: return false

        return try {
            val response = withContext(Dispatchers.IO) {
                ApiClient.mobileSyncApi.getTrainingProfile(authHeader)
            }
            val body = response.body()
            val profile = body?.takeIf { response.isSuccessful && it.success }?.data?.profile
            val complete = profile != null && CORE_FIELDS.all { profile[it] != null }
            if (complete) AuthManager.setOnboardingCompleted(context, true)
            complete
        } catch (_: Exception) {
            true
        }
    }
}
