package com.movit.core.data.repository

import com.movit.core.data.outbox.ExercisePreferenceUpsertOutboxPayload
import com.movit.core.data.outbox.OutboxStatus
import com.movit.core.network.MovitJson
import com.movit.core.network.dto.UserExercisePreferenceUpsertRequest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExercisePreferenceOutboxCanonicalIdTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun upsertExercisePreference_enqueuesCanonicalExerciseIdForSlugAlias() {
        runBlocking {
            val engine = MockEngine { respond("""{"success":true}""", HttpStatusCode.OK, jsonHeaders) }
            val platform = object : FakeMovitPlatformBindings() {
                override fun isNetworkAvailable(): Boolean = false
            }
            val localStore = testLocalStore(platform)
            localStore.writeJsonCache(
                MovitCacheKeys.EXERCISE_CONFIG_STORE,
                MovitCacheKeys.EXERCISE_CONFIG_SLUG_ALIASES,
                MovitJson.encodeToString(
                    MapSerializer(String.serializer(), String.serializer()),
                    mapOf("ex-001" to "bodyweight-squat"),
                ),
            )
            val repo = testMobileWriteRepository(engine, platform, localStore)

            val result = repo.upsertExercisePreference(
                exerciseId = "bodyweight-squat",
                request = UserExercisePreferenceUpsertRequest(customReps = 10),
                operationId = "op-pref-slug",
            )

            assertTrue(result is com.movit.shared.AppResult.Success)
            val outbox = localStore.getOutboxById("op-pref-slug")
            assertEquals(OutboxStatus.PENDING, outbox?.status)
            val payload = MovitJson.decodeFromString<ExercisePreferenceUpsertOutboxPayload>(
                outbox?.payload.orEmpty(),
            )
            assertEquals("ex-001", payload.exerciseId)
        }
    }
}
