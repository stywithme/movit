package com.movit.core.data.repository

import com.movit.core.network.MovitJson
import com.movit.core.network.dto.ExploreDataDto
import com.movit.core.network.dto.ExploreExerciseDto
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
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
}
