package com.movit.core.data.repository

import com.movit.core.data.cache.MessageLibraryCache
import com.movit.core.data.local.InMemoryMovitLocalStore
import com.movit.core.data.sync.SyncCatalogGraphValidator
import com.movit.core.network.MovitJson
import com.movit.core.network.dto.LocalizedNameDto
import com.movit.core.network.dto.MobileSyncDataDto
import com.movit.core.network.dto.SyncMessageTemplateDto
import com.movit.core.training.config.ExerciseConfigParser
import com.movit.core.training.config.ExerciseConfigRecord
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncCatalogOfflineRepositoryTest {

    @Test
    fun applyFromSync_persistsFullProgramAndWorkoutGraph() {
        val store = InMemoryMovitLocalStore()
        val messageLibrary = MessageLibraryCache(store)
        val trainingConfig = TrainingConfigRepository(store, messageLibrary)
        val catalog = SyncCatalogOfflineRepository(store, trainingConfig)

        val squat = exerciseSyncJson("ex-1", "bodyweight-squat")
        trainingConfig.applySyncExercises(exercises = listOf(squat))

        val report = catalog.applyFromSync(
            payload = MobileSyncDataDto(
                programs = listOf(programSyncJson()),
                workoutTemplates = listOf(workoutSyncJson()),
            ),
            isFullSync = true,
        )

        val program = catalog.readProgram("prog-1")
        assertNotNull(program)
        assertEquals("wt-1", program.weeks.single().days.single().plannedWorkouts.single().workoutTemplateId)

        val workout = catalog.readWorkoutTrainingConfig("wt-1")
        assertNotNull(workout)
        assertEquals("bodyweight-squat", workout.exercises.single().exercise.slug)

        assertTrue(report.isComplete)
    }

    @Test
    fun applyFromSync_tombstonesRemoveProgramAndWorkout() {
        val store = InMemoryMovitLocalStore()
        val trainingConfig = TrainingConfigRepository(store)
        val catalog = SyncCatalogOfflineRepository(store, trainingConfig)

        catalog.applyFromSync(
            payload = MobileSyncDataDto(
                programs = listOf(programSyncJson()),
                workoutTemplates = listOf(workoutSyncJson()),
            ),
            isFullSync = true,
        )

        catalog.applyFromSync(
            payload = MobileSyncDataDto(
                deletedProgramIds = listOf("prog-1"),
                deletedWorkoutTemplateIds = listOf("wt-1"),
            ),
            isFullSync = false,
        )

        assertNull(catalog.readProgram("prog-1"))
        assertNull(catalog.readWorkoutExport("wt-1"))
    }

    @Test
    fun graphValidator_flagsMissingExerciseReference() {
        val store = InMemoryMovitLocalStore()
        val trainingConfig = TrainingConfigRepository(store)
        val catalog = SyncCatalogOfflineRepository(store, trainingConfig)

        catalog.applyFromSync(
            payload = MobileSyncDataDto(
                programs = listOf(
                    buildJsonObject {
                        put("id", "prog-1")
                        put("slug", "strength")
                        putJsonObject("name") { put("en", "Strength") }
                        putJsonArray("weeks") {
                            add(
                                buildJsonObject {
                                    put("weekNumber", 1)
                                    putJsonArray("days") {
                                        add(
                                            buildJsonObject {
                                                put("dayNumber", 1)
                                                putJsonArray("plannedWorkouts") {
                                                    add(
                                                        buildJsonObject {
                                                            put("id", "pw-1")
                                                            put("workoutTemplateId", "wt-1")
                                                            putJsonObject("name") { put("en", "Day 1") }
                                                            putJsonArray("items") {
                                                                add(
                                                                    buildJsonObject {
                                                                        put("type", "exercise")
                                                                        put("exerciseSlug", "missing-exercise")
                                                                    },
                                                                )
                                                            }
                                                        },
                                                    )
                                                }
                                            },
                                        )
                                    }
                                },
                            )
                        }
                    },
                ),
                workoutTemplates = listOf(workoutSyncJson(exerciseSlug = "missing-exercise")),
            ),
            isFullSync = true,
        )

        val report = SyncCatalogGraphValidator.validate(
            programs = listOfNotNull(catalog.readProgram("prog-1")),
            workoutTemplates = listOfNotNull(catalog.readWorkoutExport("wt-1")),
            trainingConfig = trainingConfig,
        )

        assertEquals(listOf("missing-exercise"), report.missingExerciseSlugs)
    }

    private fun programSyncJson() = buildJsonObject {
        put("id", "prog-1")
        put("slug", "strength")
        putJsonObject("name") { put("en", "Strength") }
        putJsonArray("weeks") {
            add(
                buildJsonObject {
                    put("weekNumber", 1)
                    putJsonArray("days") {
                        add(
                            buildJsonObject {
                                put("dayNumber", 1)
                                putJsonArray("plannedWorkouts") {
                                    add(
                                        buildJsonObject {
                                            put("id", "pw-1")
                                            put("workoutTemplateId", "wt-1")
                                            putJsonObject("name") { put("en", "Day 1") }
                                        },
                                    )
                                }
                            },
                        )
                    }
                },
            )
        }
    }

    private fun workoutSyncJson(exerciseSlug: String = "bodyweight-squat") = buildJsonObject {
        put("id", "wt-1")
        put("slug", "quick-legs")
        putJsonObject("name") { put("en", "Quick Legs") }
        putJsonArray("exercises") {
            add(
                buildJsonObject {
                    put("exercise", exerciseSlug)
                    put("sets", 3)
                    put("variantIndex", 0)
                    put("restBetweenSetsMs", 30_000)
                    put("restAfterExerciseMs", 60_000)
                },
            )
        }
    }

    private fun exerciseSyncJson(id: String, slug: String): JsonObject {
        val base = MovitJson.parseToJsonElement(readSquatFixture()).jsonObject
        return JsonObject(
            base + mapOf(
                "id" to MovitJson.parseToJsonElement("\"$id\""),
                "slug" to MovitJson.parseToJsonElement("\"$slug\""),
                "updatedAt" to MovitJson.parseToJsonElement("\"2026-06-11\""),
            ),
        )
    }

    private fun readSquatFixture(): String {
        val resourcePath = "fixtures/exercises/squat.json"
        javaClass.classLoader?.getResource(resourcePath)?.readText()?.let { return it }
        listOf(
            "src/commonTest/resources/$resourcePath",
            "core/data/src/commonTest/resources/$resourcePath",
        ).forEach { relative ->
            val file = java.io.File(relative)
            if (file.isFile) return file.readText()
        }
        error("Missing fixture: $resourcePath")
    }
}
