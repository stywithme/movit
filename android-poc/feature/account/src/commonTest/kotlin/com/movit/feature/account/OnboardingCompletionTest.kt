package com.movit.feature.account

import com.movit.core.data.platform.MovitPlatformBindings
import com.movit.core.network.dto.TrainingProfilePayloadDto
import com.movit.shared.AppResult
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingCompletionTest {

    @Test
    fun isTrainingProfileComplete_requiresCoreFields() {
        val incomplete = TrainingProfilePayloadDto(
            profile = mapOf(
                "dateOfBirth" to JsonPrimitive("1990-01-01"),
                "biologicalSex" to JsonPrimitive("male"),
            ),
        )
        val complete = TrainingProfilePayloadDto(
            profile = mapOf(
                "dateOfBirth" to JsonPrimitive("1990-01-01"),
                "biologicalSex" to JsonPrimitive("male"),
                "heightCm" to JsonPrimitive(175),
                "weightKg" to JsonPrimitive(70),
            ),
        )
        assertFalse(OnboardingCompletion.isTrainingProfileComplete(incomplete))
        assertTrue(OnboardingCompletion.isTrainingProfileComplete(complete))
    }

    @Test
    fun resolveNeedsOnboarding_restoresLocalFlagFromBackend() = runBlocking {
        val platform = FakeOnboardingPlatform(completed = false)
        val payload = TrainingProfilePayloadDto(
            profile = mapOf(
                "dateOfBirth" to JsonPrimitive("1990-01-01"),
                "biologicalSex" to JsonPrimitive("female"),
                "heightCm" to JsonPrimitive(165),
                "weightKg" to JsonPrimitive(60),
            ),
        )
        val needs = OnboardingCompletion.resolveNeedsOnboarding(platform) {
            AppResult.Success(payload)
        }
        assertFalse(needs)
        assertTrue(platform.isOnboardingCompleted())
    }

    @Test
    fun resolveNeedsOnboarding_failOpenOnApiError() = runBlocking {
        val platform = FakeOnboardingPlatform(completed = false)
        val needs = OnboardingCompletion.resolveNeedsOnboarding(platform) {
            AppResult.Failure("network")
        }
        assertFalse(needs)
    }

    private class FakeOnboardingPlatform(
        private var completed: Boolean,
    ) : MovitPlatformBindings {
        override fun apiBaseUrl(): String = "http://test"
        override fun authHeader(): String? = "Bearer test"
        override fun preferredLanguage(): String = "en"
        override fun userDisplayName(fallback: String): String = fallback
        override fun readCache(store: String, key: String): String? = null
        override fun writeCache(store: String, key: String, value: String) = Unit
        override fun removeCache(store: String, key: String) = Unit
        override fun isOnboardingCompleted(): Boolean = completed
        override fun setOnboardingCompleted(completed: Boolean) {
            this.completed = completed
        }
    }
}
