package com.movit.core.data.repository

import com.movit.core.data.cache.MessageLibraryCache
import com.movit.core.data.local.InMemoryMovitLocalStore
import com.movit.core.network.MovitJson
import com.movit.core.network.dto.SyncMessageContentDto
import com.movit.core.network.dto.SyncMessageTemplateDto
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrainingConfigMessageMergeTest {

    @Test
    fun resolveBySlug_mergesMessageLibraryBeforeRead() {
        val store = InMemoryMovitLocalStore()
        val messageLibrary = MessageLibraryCache(store)
        val repo = TrainingConfigRepository(store, messageLibrary)

        val exercise = buildExerciseWithAssignment()
        repo.applySyncExercises(exercises = listOf(exercise))

        messageLibrary.replaceFull(
            listOf(
                SyncMessageTemplateDto(
                    id = "msg-1",
                    code = "keep_going",
                    content = SyncMessageContentDto(en = "Keep going!"),
                ),
            ),
        )

        val config = repo.getExercise("merge-test")
        assertNotNullMotivational(config)
    }

    @Test
    fun applySyncExercises_mergesMessageLibraryOnPersist() {
        val store = InMemoryMovitLocalStore()
        val messageLibrary = MessageLibraryCache(store)
        val repo = TrainingConfigRepository(store, messageLibrary)

        messageLibrary.replaceFull(
            listOf(
                SyncMessageTemplateDto(
                    id = "msg-1",
                    code = "keep_going",
                    content = SyncMessageContentDto(en = "Keep going!"),
                ),
            ),
        )

        repo.applySyncExercises(exercises = listOf(buildExerciseWithAssignment()))

        val config = repo.getExercise("merge-test")
        assertNotNullMotivational(config)
    }

    private fun assertNotNullMotivational(config: com.movit.core.training.config.ExerciseConfig?) {
        val motivational = config?.poseVariants?.single()?.feedbackMessages?.motivational.orEmpty()
        assertTrue(motivational.any { it.en.contains("Keep going") })
    }

    private fun buildExerciseWithAssignment(): JsonObject {
        val base = MovitJson.parseToJsonElement(readSquatFixture()).jsonObject
        val withAssignments = JsonObject(
            base + mapOf(
                "id" to MovitJson.parseToJsonElement("\"ex-merge\""),
                "slug" to MovitJson.parseToJsonElement("\"merge-test\""),
                "updatedAt" to MovitJson.parseToJsonElement("\"2026-06-11\""),
            ),
        )
        return buildJsonObject {
            withAssignments.forEach { (key, value) -> put(key, value) }
            putJsonArray("poseVariants") {
                add(
                    buildJsonObject {
                        putJsonObject("name") { put("en", "Default") }
                        putJsonArray("trackedJoints") {
                            add(
                                buildJsonObject {
                                    put("joint", "knee")
                                    put("role", "primary")
                                    putJsonObject("startPose") {
                                        put("min", 0)
                                        put("max", 180)
                                    }
                                },
                            )
                        }
                        putJsonArray("messageAssignments") {
                            add(
                                buildJsonObject {
                                    put("messageId", "msg-1")
                                    put("target", "feedback")
                                    put("context", "motivational")
                                    put("sortOrder", 0)
                                },
                            )
                        }
                    },
                )
            }
        }
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
