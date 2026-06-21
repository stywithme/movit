package com.movit.core.data.repository

import com.movit.core.network.MovitJson
import com.movit.core.network.dto.ExploreApiResponse
import com.movit.core.network.dto.ExploreDataDto
import com.movit.core.network.dto.ExploreExerciseDto
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExploreSyncRepositoryTest {

    @Test
    fun exerciseImageUrl_readsFromCachedCatalog() {
        val platform = FakeMovitPlatformBindings()
        val localStore = testLocalStore(platform)
        val exploreJson = MovitJson.encodeToString(
            ExploreDataDto.serializer(),
            ExploreDataDto(
                exercises = listOf(
                    ExploreExerciseDto(slug = "squat", imageUrl = "https://cdn/squat.jpg"),
                ),
            ),
        )
        localStore.writeJsonCache(MovitCacheKeys.EXPLORE_STORE, MovitCacheKeys.EXPLORE_DATA, exploreJson)

        val repo = ExploreSyncRepository(
            api = testMobileApi(MockEngine { respond("{}") }),
            platform = { platform },
            localStore = { localStore },
        )

        assertEquals("https://cdn/squat.jpg", repo.exerciseImageUrl("squat"))
        assertNull(repo.exerciseImageUrl("missing"))
    }

    @Test
    fun sync_withoutLimitParameter_requestsFullExploreCatalog() {
        runBlocking {
            val platform = FakeMovitPlatformBindings()
            val localStore = testLocalStore(platform)
            var requestedLimit: String? = "not-called"
            val engine = MockEngine { request ->
                requestedLimit = request.url.parameters["limit"]
                respond(
                    content = MovitJson.encodeToString(
                        ExploreApiResponse.serializer(),
                        ExploreApiResponse(
                            success = true,
                            timestamp = "2026-06-20T00:00:00Z",
                            data = ExploreDataDto(
                                exercises = List(55) { index ->
                                    ExploreExerciseDto(
                                        id = "ex-$index",
                                        slug = "exercise-$index",
                                    )
                                },
                            ),
                        ),
                    ),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
            val repo = ExploreSyncRepository(
                api = testMobileApi(engine, platform),
                platform = { platform },
                localStore = { localStore },
            )

            repo.sync()

            assertNull(requestedLimit)
            assertEquals(55, repo.readCached()?.exercises?.size)
        }
    }
}
