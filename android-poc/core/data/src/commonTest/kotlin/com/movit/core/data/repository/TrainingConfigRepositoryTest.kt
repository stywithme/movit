package com.movit.core.data.repository

import com.movit.core.data.local.InMemoryMovitLocalStore
import com.movit.core.data.repository.MovitCacheKeys
import com.movit.core.training.config.ExerciseConfigParser
import com.movit.core.training.config.ExerciseConfigRecord
import com.movit.core.training.engine.CountingMethod
import com.movit.core.network.MovitJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrainingConfigRepositoryTest {

    @Test
    fun seedAndResolveSquatAliases() {
        val store = InMemoryMovitLocalStore()
        val repo = TrainingConfigRepository(store)
        val json = readSquatFixture()
        val config = ExerciseConfigParser.parseConfigJson(json)
        repo.seedRecord(
            ExerciseConfigRecord.fromConfig(
                id = "ex-squat",
                slug = "bodyweight-squat",
                updatedAt = "2026-06-11",
                config = config,
            ),
        )

        assertEquals(CountingMethod.UP_DOWN, repo.getExercise("bodyweight-squat")?.countingMethod)
        assertEquals(CountingMethod.UP_DOWN, repo.resolveBySlug("squat")?.config?.countingMethod)
        assertTrue(repo.supports("barbell-squat"))
    }

    @Test
    fun supports_usesSlugIndexWithoutParsingConfig() {
        val store = InMemoryMovitLocalStore()
        val repo = TrainingConfigRepository(store)
        store.writeJsonCache(
            MovitCacheKeys.EXERCISE_CONFIG_STORE,
            MovitCacheKeys.EXERCISE_CONFIG_SLUG_INDEX,
            """["bodyweight-squat"]""",
        )

        assertTrue(repo.supports("bodyweight-squat"))
        assertFalse(repo.supports("missing-exercise"))
    }

    @Test
    fun applySyncExercises_registersIdToSlugAlias() {
        val store = InMemoryMovitLocalStore()
        val repo = TrainingConfigRepository(store)
        val base = MovitJson.parseToJsonElement(readSquatFixture()).jsonObject
        val exercise = JsonObject(
            base + mapOf(
                "id" to MovitJson.parseToJsonElement("\"ex-001\""),
                "slug" to MovitJson.parseToJsonElement("\"bodyweight-squat\""),
                "updatedAt" to MovitJson.parseToJsonElement("\"2026-06-11\""),
            ),
        )

        repo.applySyncExercises(exercises = listOf(exercise))

        assertTrue(repo.supports("ex-001"))
        assertEquals(
            "bodyweight-squat",
            store.readString(
                MovitCacheKeys.EXERCISE_CONFIG_STORE,
                MovitCacheKeys.exerciseIdToSlugKey("ex-001"),
            ),
        )
    }

    private fun readSquatFixture(): String {
        val name = "squat.json"
        val resourcePath = "fixtures/exercises/$name"
        javaClass.classLoader?.getResource(resourcePath)?.readText()?.let { return it }
        listOf(
            "src/commonTest/resources/$resourcePath",
            "core/data/src/commonTest/resources/$resourcePath",
            "core/training-engine/src/commonTest/resources/$resourcePath",
        ).forEach { relative ->
            val file = java.io.File(relative)
            if (file.isFile) return file.readText()
        }
        error("Missing fixture: $resourcePath")
    }
}
