package com.movit.core.network.contract

import com.movit.core.network.MovitJson
import com.movit.core.network.dto.MobileSyncApiResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * P2.2 golden budget: authenticated empty-delta sync must stay lean
 * (no systemMessages dump, no audioManifest files, no full reports).
 */
class SyncPayloadBudgetContractTest {

    @Test
    fun emptyAuthenticatedDelta_underBudgetAndOmitsHeavySections() {
        val raw = readFixture("sync-delta-empty-authenticated.json")
        val parsed = MovitJson.decodeFromString(MobileSyncApiResponse.serializer(), raw)
        val data = parsed.data ?: error("data missing")

        assertEquals(false, parsed.meta?.isFullSync)
        assertTrue(data.systemMessages.isEmpty())
        assertTrue(data.audioManifest.files.isEmpty())
        assertTrue(data.plannedWorkoutReports.isEmpty())
        assertTrue(data.userPrograms.isEmpty())
        assertTrue(data.exercises.isEmpty())
        assertTrue(data.messageLibrary.isEmpty())

        // Baseline P0.4: empty delta was hundreds of KB–MB; target < 30 KB gzip.
        // Fixture is the structural floor — keep under 2 KB raw JSON.
        assertTrue(raw.length < 2_048, "fixture budget exceeded: ${raw.length} bytes")
    }

    private fun readFixture(name: String): String {
        javaClass.classLoader?.getResource("fixtures/$name")?.readText()?.let { return it }
        val candidates = listOf(
            "src/commonTest/resources/fixtures/$name",
            "core/network/src/commonTest/resources/fixtures/$name",
        )
        for (relative in candidates) {
            val file = java.io.File(relative)
            if (file.isFile) return file.readText()
        }
        error("Missing fixture: fixtures/$name")
    }
}
