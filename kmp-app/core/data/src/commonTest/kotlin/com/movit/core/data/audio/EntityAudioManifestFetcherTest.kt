package com.movit.core.data.audio

import com.movit.core.data.cache.AudioManifestCache
import com.movit.core.data.local.InMemoryMovitLocalStore
import com.movit.core.data.repository.ExploreSyncRepository
import com.movit.core.data.repository.FakeMovitPlatformBindings
import com.movit.core.data.repository.MovitCacheKeys
import com.movit.core.data.repository.testMobileApi
import com.movit.core.data.cache.MovitCachePolicy
import com.movit.core.network.dto.ExploreDataDto
import com.movit.core.network.dto.ExploreWorkoutDto
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EntityAudioManifestFetcherTest {

    @Test
    fun fetchAndMerge_callsEntityManifestsAndMergesIntoCache() {
        runBlocking {
            val store = InMemoryMovitLocalStore()
            val cache = AudioManifestCache(store)
            val client = FakeEntityAudioManifestClient()
            client.workoutResponse = FakeEntityAudioManifestClient.manifestResponse(
                slug = "legs-day",
                entityType = "workout",
                filename = "workout_intro.wav",
            )
            client.exerciseResponse = FakeEntityAudioManifestClient.manifestResponse(
                slug = "squat",
                entityType = "exercise",
                filename = "squat_cue.wav",
            )

            val platform = FakeMovitPlatformBindings()
            val explore = ExploreSyncRepository(
                api = testMobileApi(MockEngine { respond("{}", HttpStatusCode.OK) }, platform),
                platform = { platform },
                localStore = { store },
            )
            MovitCachePolicy.writeJson(
                store,
                MovitCacheKeys.EXPLORE_STORE,
                MovitCacheKeys.EXPLORE_DATA,
                ExploreDataDto(
                    workoutTemplates = listOf(
                        ExploreWorkoutDto(id = "wt-1", slug = "legs-day"),
                    ),
                ),
                ExploreDataDto.serializer(),
            )

            val fetcher = EntityAudioManifestFetcher(
                client = client,
                manifestCache = cache,
                platform = { platform },
                exploreSync = explore,
            )

            val merged = fetcher.fetchAndMerge(
                EntityAudioManifestFetcher.Targets(
                    exerciseSlugs = listOf("squat"),
                    workoutTemplateIds = listOf("wt-1"),
                ),
            )

            assertEquals(2, merged)
            assertEquals(listOf("legs-day"), client.workoutCalls)
            assertEquals(listOf("squat"), client.exerciseCalls)

            val persisted = cache.read()
            assertTrue(persisted != null)
            assertEquals(
                setOf("workout_intro.wav", "squat_cue.wav"),
                persisted.manifest.files.map { it.filename }.toSet(),
            )
        }
    }

    @Test
    fun fetchAndMerge_withoutAuth_skipsNetwork() {
        runBlocking {
            val store = InMemoryMovitLocalStore()
            val cache = AudioManifestCache(store)
            val client = FakeEntityAudioManifestClient()
            val platform = FakeMovitPlatformBindings(auth = null)
            val explore = ExploreSyncRepository(
                api = testMobileApi(MockEngine { respond("{}", HttpStatusCode.OK) }, platform),
                platform = { platform },
                localStore = { store },
            )

            val fetcher = EntityAudioManifestFetcher(
                client = client,
                manifestCache = cache,
                platform = { platform },
                exploreSync = explore,
            )

            val merged = fetcher.fetchAndMerge(
                EntityAudioManifestFetcher.Targets(exerciseSlugs = listOf("squat")),
            )

            assertEquals(0, merged)
            assertTrue(client.exerciseCalls.isEmpty())
        }
    }
}
