package com.movit.core.training.boundary

import kotlin.test.Test
import kotlin.test.assertEquals

class TrainingThroughputProfilesTest {
    @Test
    fun resolve_defaultsToHighWhenNullOrUnknown() {
        assertEquals(TrainingThroughputProfiles.HIGH, TrainingThroughputProfiles.resolve(null))
        assertEquals(TrainingThroughputProfiles.HIGH, TrainingThroughputProfiles.resolve(""))
        assertEquals(TrainingThroughputProfiles.HIGH, TrainingThroughputProfiles.resolve("unknown"))
    }

    @Test
    fun resolve_mapsNamedProfiles() {
        assertEquals(TrainingThroughputProfiles.MEDIUM, TrainingThroughputProfiles.resolve("medium"))
        assertEquals(TrainingThroughputProfiles.HIGH, TrainingThroughputProfiles.resolve("high"))
        assertEquals(TrainingThroughputProfiles.LEGACY_PARITY, TrainingThroughputProfiles.resolve("legacy"))
        assertEquals(TrainingThroughputProfiles.MEDIUM, TrainingThroughputProfiles.resolve("boost_15"))
        assertEquals(TrainingThroughputProfiles.STABLE, TrainingThroughputProfiles.resolve("stable"))
    }

    @Test
    fun highProfile_matchesFlagshipDefaults() {
        val high = TrainingThroughputProfiles.HIGH
        assertEquals(640, high.analysisWidth)
        assertEquals(480, high.analysisHeight)
        assertEquals(30, high.targetFps)
    }

    @Test
    fun toCameraConfiguration_carriesProfileMetadata() {
        val config = TrainingThroughputProfiles.toCameraConfiguration(
            profile = TrainingThroughputProfiles.MEDIUM,
            useFrontCamera = true,
        )
        assertEquals(true, config.useFrontCamera)
        assertEquals(15, config.targetFps)
        assertEquals(480, config.analysisWidth)
        assertEquals(360, config.analysisHeight)
        assertEquals("medium", config.throughputProfileId)
    }
}
