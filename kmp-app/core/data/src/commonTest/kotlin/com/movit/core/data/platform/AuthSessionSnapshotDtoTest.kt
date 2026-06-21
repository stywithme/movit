package com.movit.core.data.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class AuthSessionSnapshotDtoTest {

    @Test
    fun toAuthDataDto_mapsSnapshotFields() {
        val snapshot = AuthSessionSnapshot(
            accessToken = "access",
            refreshToken = "refresh",
            expiresInSeconds = 3600,
            userId = "user-1",
            name = "Athlete",
            email = "a@test.com",
            avatarUrl = "https://cdn.test/avatar.png",
            preferredLanguage = "ar",
            voiceFeedback = false,
            notifications = true,
            isPro = true,
            subscriptionExpiry = "2026-12-31",
            totalWorkouts = 12,
            totalMinutes = 90,
        )

        val dto = snapshot.toAuthDataDto()

        assertEquals("access", dto.tokens.accessToken)
        assertEquals("refresh", dto.tokens.refreshToken)
        assertEquals(3600, dto.tokens.expiresIn)
        assertEquals("user-1", dto.user.id)
        assertEquals("Athlete", dto.user.name)
        assertEquals("a@test.com", dto.user.email)
        assertEquals("ar", dto.user.preferredLanguage)
        assertEquals(12, dto.user.totalWorkoutExecutions)
        assertEquals(90, dto.user.totalMinutes)
        assertEquals(true, dto.user.isPro)
    }
}
