package com.movit.core.training.boundary

import kotlin.test.Test
import kotlin.test.assertEquals

class TrainingThroughputProfilesTest {
    @Test
    fun resolve_defaultsToStableWhenNullOrUnknown() {
        assertEquals(TrainingThroughputProfiles.STABLE, TrainingThroughputProfiles.resolve(null))
        assertEquals(TrainingThroughputProfiles.STABLE, TrainingThroughputProfiles.resolve(""))
        assertEquals(TrainingThroughputProfiles.STABLE, TrainingThroughputProfiles.resolve("unknown"))
    }

    @Test
    fun resolve_mapsNamedProfiles() {
        assertEquals(TrainingThroughputProfiles.MEDIUM, TrainingThroughputProfiles.resolve("medium"))
        assertEquals(TrainingThroughputProfiles.HIGH, TrainingThroughputProfiles.resolve("high"))
        assertEquals(TrainingThroughputProfiles.LEGACY_PARITY, TrainingThroughputProfiles.resolve("legacy"))
        assertEquals(TrainingThroughputProfiles.MEDIUM, TrainingThroughputProfiles.resolve("boost_15"))
    }

    @Test
    fun stableProfile_matchesCurrentProductionDefaults() {
        val stable = TrainingThroughputProfiles.STABLE
        assertEquals(320, stable.analysisWidth)
        assertEquals(240, stable.analysisHeight)
        assertEquals(10, stable.targetFps)
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
