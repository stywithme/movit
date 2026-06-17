package com.movit.feature.account

import com.movit.core.network.dto.UserPublicDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProfileApiMapperTest {

    @Test
    fun proUser_mapsRenewalLabel() {
        val user = sampleUser(
            isPro = true,
            subscriptionExpiry = "2026-06-15T12:00:00Z",
        )

        val profile = ProfileApiMapper.map(user)

        assertTrue(profile.isPro)
        assertEquals("Movit Pro", profile.subscriptionLabel)
        assertEquals("Renews Jun 15, 2026 · Monthly", profile.subscriptionRenewal)
    }

    @Test
    fun freeUser_hasNoRenewal() {
        val profile = ProfileApiMapper.map(sampleUser(isPro = false))

        assertEquals(false, profile.isPro)
        assertEquals(null, profile.subscriptionRenewal)
    }

    private fun sampleUser(
        isPro: Boolean,
        subscriptionExpiry: String? = null,
    ) = UserPublicDto(
        id = "u1",
        email = "test@movit.app",
        name = "Test User",
        avatarUrl = null,
        provider = "email",
        preferredLanguage = "en",
        voiceFeedback = true,
        notifications = true,
        isPro = isPro,
        subscriptionExpiry = subscriptionExpiry,
        totalWorkoutExecutions = 0,
        totalMinutes = 0,
        emailVerified = true,
        createdAt = "",
    )
}
