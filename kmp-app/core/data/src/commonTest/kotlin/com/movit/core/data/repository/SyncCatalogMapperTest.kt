package com.movit.core.data.repository

import com.movit.core.data.cache.MovitCachePolicy
import com.movit.core.network.MovitJson
import com.movit.core.network.dto.ExploreDataDto
import com.movit.core.network.dto.ExploreExerciseDto
import com.movit.core.network.dto.ExploreProgramDto
import com.movit.core.network.dto.ExploreWorkoutDto
import com.movit.core.network.dto.LocalizedNameDto
import com.movit.core.network.dto.MobileSyncDataDto
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncCatalogMapperTest {

    @Test
    fun mapSyncPayload_mapsWorkoutProgramAndExerciseCards() {
        val payload = MobileSyncDataDto(
            workoutTemplates = listOf(workoutJson("wt-1", "legs-day", featured = true)),
            programs = listOf(programJson("prog-1", "strength-base")),
            exercises = listOf(exerciseJson("ex-1", "squat")),
        )

        val slice = SyncCatalogMapper.mapSyncPayloadToExploreSlice(payload)

        assertEquals("wt-1", slice.workoutTemplates.single().id)
        assertEquals("legs-day", slice.workoutTemplates.single().slug)
        assertEquals(2, slice.workoutTemplates.single().exerciseCount)
        assertEquals("prog-1", slice.programs.single().id)
        assertEquals("squat", slice.exercises.single().slug)
        assertEquals(2, slice.exercises.single().musclesCount)
        assertEquals(1, slice.levels.single().number)
    }

    @Test
    fun applyFromSync_mergeRemovesTombstonedItems() {
        val platform = FakeMovitPlatformBindings()
        val localStore = testLocalStore(platform)
        val existing = ExploreDataDto(
            programs = listOf(
                ExploreProgramDto(id = "prog-old", slug = "old", updatedAt = "2026-01-01"),
                ExploreProgramDto(id = "prog-keep", slug = "keep", updatedAt = "2026-01-01"),
            ),
            workoutTemplates = listOf(
                ExploreWorkoutDto(id = "wt-old", slug = "old-wt", updatedAt = "2026-01-01"),
            ),
            exercises = listOf(
                ExploreExerciseDto(id = "ex-old", slug = "old-ex", updatedAt = "2026-01-01"),
            ),
        )
        MovitCachePolicy.writeJson(
            localStore,
            MovitCacheKeys.EXPLORE_STORE,
            MovitCacheKeys.EXPLORE_DATA,
            existing,
            ExploreDataDto.serializer(),
        )

        val repo = ExploreSyncRepository(
            api = testMobileApi(MockEngine { respond("{}") }),
            platform = { platform },
            localStore = { localStore },
        )

        val merged = repo.applyFromSync(
            payload = MobileSyncDataDto(
                deletedProgramIds = listOf("prog-old"),
                deletedWorkoutTemplateIds = listOf("wt-old"),
                deletedExerciseIds = listOf("ex-old"),
            ),
            isFullSync = false,
        )

        assertEquals(listOf("prog-keep"), merged.programs.map { it.id })
        assertTrue(merged.workoutTemplates.isEmpty())
        assertTrue(merged.exercises.isEmpty())
    }

    private fun workoutJson(id: String, slug: String, featured: Boolean) = buildJsonObject {
        put("id", id)
        put("slug", slug)
        put("isFeatured", featured)
        put("updatedAt", "2026-06-11T00:00:00Z")
        putJsonObject("name") { put("en", "Leg Day") }
        putJsonObject("level") {
            put("number", 1)
            put("code", "L1")
            putJsonObject("name") { put("en", "Beginner") }
        }
        putJsonArray("exercises") {
            add(buildJsonObject { put("id", "e1") })
            add(buildJsonObject { put("id", "e2") })
        }
    }

    private fun programJson(id: String, slug: String) = buildJsonObject {
        put("id", id)
        put("slug", slug)
        put("durationWeeks", 8)
        put("updatedAt", "2026-06-11T00:00:00Z")
        putJsonObject("name") { put("en", "Strength Base") }
        putJsonObject("levelMin") {
            put("number", 1)
            put("code", "L1")
            putJsonObject("name") { put("en", "Beginner") }
        }
    }

    private fun exerciseJson(id: String, slug: String) = buildJsonObject {
        put("id", id)
        put("slug", slug)
        put("updatedAt", "2026-06-11T00:00:00Z")
        putJsonObject("name") { put("en", "Squat") }
        putJsonObject("category") {
            put("code", "legs")
            putJsonObject("name") { put("en", "Legs") }
        }
        putJsonArray("muscles") {
            add(MovitJson.parseToJsonElement("\"quads\""))
            add(MovitJson.parseToJsonElement("\"glutes\""))
        }
        put("imageUrl", "https://cdn/squat.jpg")
    }
}
