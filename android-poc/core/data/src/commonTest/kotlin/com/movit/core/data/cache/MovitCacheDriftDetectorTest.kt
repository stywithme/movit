package com.movit.core.data.cache

import com.movit.core.network.dto.MessageLibraryStatsDto
import com.movit.core.network.dto.SyncMetaDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MovitCacheDriftDetectorTest {

    @Test
    fun entityDrift_detectsLocalOverflow() {
        val verdict = MovitCacheDriftDetector.detectEntityDrift(
            local = MovitCacheDriftDetector.EntityCounts(12, 5, 2),
            meta = SyncMetaDto(totalExercises = 10, totalWorkoutTemplates = 5, totalPrograms = 2),
            hasNoEntityDelta = true,
        )
        assertIs<MovitCacheDriftDetector.DriftVerdict.NeedsFullRefresh>(verdict)
    }

    @Test
    fun entityDrift_okWhenCountsMatch() {
        val verdict = MovitCacheDriftDetector.detectEntityDrift(
            local = MovitCacheDriftDetector.EntityCounts(10, 5, 2),
            meta = SyncMetaDto(totalExercises = 10, totalWorkoutTemplates = 5, totalPrograms = 2),
            hasNoEntityDelta = true,
        )
        assertEquals(MovitCacheDriftDetector.DriftVerdict.Ok, verdict)
    }

    @Test
    fun messageStatsDrift_detectsFingerprintMismatch() {
        val verdict = MovitCacheDriftDetector.detectMessageStatsDrift(
            cached = MovitCacheDriftDetector.MessageStatsSnapshot(10, 8, 4, "old"),
            server = MessageLibraryStatsDto(10, 8, 4, "new"),
        )
        assertIs<MovitCacheDriftDetector.DriftVerdict.MessageStatsMismatch>(verdict)
    }
}
