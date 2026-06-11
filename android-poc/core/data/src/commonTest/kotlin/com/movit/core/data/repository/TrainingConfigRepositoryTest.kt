package com.movit.core.data.repository

import com.movit.core.data.local.InMemoryMovitLocalStore
import com.movit.core.training.config.ExerciseConfigParser
import com.movit.core.training.config.ExerciseConfigRecord
import com.movit.core.training.engine.CountingMethod
import kotlin.test.Test
import kotlin.test.assertEquals
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
