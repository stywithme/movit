package com.trainingvalidator.poc.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StateMessageKeyNormalizationTest {
    @Test
    fun mapsBackendAliasesToMobileJointStates() {
        assertEquals("normal", normalizeStateMessageKey("normal"))
        assertEquals("normal", normalizeStateMessageKey("good"))
        assertEquals("pad", normalizeStateMessageKey("pad"))
        assertEquals("pad", normalizeStateMessageKey("accept"))
        assertEquals("pad", normalizeStateMessageKey("acceptable"))
    }

    @Test
    fun keepsKnownStateKeysAndRejectsUnknownOnes() {
        assertEquals("perfect", normalizeStateMessageKey(" perfect "))
        assertEquals("warning", normalizeStateMessageKey("WARNING"))
        assertEquals("danger", normalizeStateMessageKey("danger"))
        assertNull(normalizeStateMessageKey("almost_good"))
    }
}
