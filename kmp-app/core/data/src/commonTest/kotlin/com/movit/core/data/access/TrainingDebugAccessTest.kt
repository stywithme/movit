package com.movit.core.data.access

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrainingDebugAccessTest {

    @Test
    fun primaryAdminUserId_isAllowed() {
        assertTrue(TrainingDebugAccess.isPrimaryAdmin(userId = "1", email = "someone@example.com"))
    }

    @Test
    fun primaryAdminEmail_isAllowed_regardlessOfUserId() {
        assertTrue(TrainingDebugAccess.isPrimaryAdmin(userId = "9182", email = "alustadh.manager@gmail.com"))
    }

    @Test
    fun adminEmail_isMatchedCaseInsensitively_andTrimmed() {
        assertTrue(TrainingDebugAccess.isPrimaryAdmin(null, "  Alustadh.Manager@Gmail.com  "))
    }

    @Test
    fun otherUsers_areNotAdmins() {
        assertFalse(TrainingDebugAccess.isPrimaryAdmin(userId = "2", email = "athlete@example.com"))
        assertFalse(TrainingDebugAccess.isPrimaryAdmin(userId = "11", email = null))
        assertFalse(TrainingDebugAccess.isPrimaryAdmin(userId = "10", email = null))
    }

    @Test
    fun signedOut_isNotAdmin() {
        assertFalse(TrainingDebugAccess.isPrimaryAdmin(userId = null, email = null))
        assertFalse(TrainingDebugAccess.isPrimaryAdmin(userId = "", email = "   "))
    }

    /** A lookalike address must not inherit access. */
    @Test
    fun similarEmails_areRejected() {
        assertFalse(TrainingDebugAccess.isPrimaryAdmin(null, "alustadh.manager@gmail.com.attacker.io"))
        assertFalse(TrainingDebugAccess.isPrimaryAdmin(null, "alustadh.manager@gmai1.com"))
        assertFalse(TrainingDebugAccess.isPrimaryAdmin(null, "xalustadh.manager@gmail.com"))
    }

    @Test
    fun debugBuild_keepsAccess_forEveryone() {
        assertTrue(TrainingDebugAccess.isLabAvailable(platformSupportsLab = true))
    }
}
