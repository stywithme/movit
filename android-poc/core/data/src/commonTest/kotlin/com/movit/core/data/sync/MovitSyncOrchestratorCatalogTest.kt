package com.movit.core.data.sync

import com.movit.core.data.audio.AudioPrefetchRunner
import com.movit.core.data.audio.FakeAudioFileDownloader
import com.movit.core.data.cache.AudioManifestCache
import com.movit.core.data.cache.MessageLibraryCache
import com.movit.core.data.cache.MovitSyncMetadataStore
import com.movit.core.data.cache.SystemMessageCache
import com.movit.core.data.local.MovitLocalStore
import com.movit.core.data.outbox.OfflineWriteQueue
import com.movit.core.data.repository.DayCustomizationLocalStore
import com.movit.core.data.repository.ExercisePreferenceLocalStore
import com.movit.core.data.repository.ExploreSyncRepository
import com.movit.core.data.repository.FakeMovitPlatformBindings
import com.movit.core.data.repository.HomeSyncRepository
import com.movit.core.data.repository.MovitCacheKeys
import com.movit.core.data.repository.PlanSyncRepository
import com.movit.core.data.repository.ReportsSyncRepository
import com.movit.core.data.repository.TrainingConfigRepository
import com.movit.core.data.repository.testLocalStore
import com.movit.core.data.repository.testMobileApi
import com.movit.core.network.MovitJson
import com.movit.core.network.dto.ExploreDataDto
import com.movit.core.network.dto.HomeApiResponse
import com.movit.core.network.dto.HomeDataDto
import com.movit.core.network.dto.MobileSyncApiResponse
import com.movit.core.network.dto.MobileSyncDataDto
import com.movit.core.network.dto.SyncMetaDto
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MovitSyncOrchestratorCatalogTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun runSyncCycle_populatesExploreCacheFromSync_withoutExploreFetch() {
        runBlocking {
            val platform = FakeMovitPlatformBindings()
            val localStore = testLocalStore(platform)
            var exploreFetchCount = 0
            val engine = MockEngine { request ->
                when {
                    request.url.encodedPath.contains("sync") ->
                        respond(syncWithCatalogBody(), HttpStatusCode.OK, jsonHeaders)
                    request.url.encodedPath.contains("explore") -> {
                        exploreFetchCount++
                        respond("{}", HttpStatusCode.NotFound)
                    }
                    request.url.encodedPath.contains("home") ->
                        respond(homeOkBody(), HttpStatusCode.OK, jsonHeaders)
                    else -> respond("{}", HttpStatusCode.NotFound)
                }
            }
            val explore = ExploreSyncRepository(testMobileApi(engine, platform), { platform }, { localStore })
            val orchestrator = buildOrchestrator(testMobileApi(engine, platform), platform, localStore, explore)

            val outcome = orchestrator.syncIfNeeded(forceCheck = true)

            assertIs<MovitSyncOrchestrator.SyncOutcome.Success>(outcome)
            assertEquals(0, exploreFetchCount)
            val cached = explore.readCached()
            assertEquals("wt-sync-1", cached?.workoutTemplates?.single()?.id)
            assertEquals("prog-sync-1", cached?.programs?.single()?.id)
            assertEquals("squat-sync", cached?.exercises?.single()?.slug)
            assertEquals("wt-sync-1", outcome.explore?.workoutTemplates?.single()?.id)
        }
    }

    @Test
    fun runSyncCycle_tombstonesRemoveCatalogItemsFromCache() {
        runBlocking {
            val platform = FakeMovitPlatformBindings()
            val localStore = testLocalStore(platform)
            localStore.writeJsonCache(
                MovitCacheKeys.EXPLORE_STORE,
                MovitCacheKeys.EXPLORE_DATA,
                MovitJson.encodeToString(
                    ExploreDataDto.serializer(),
                    ExploreDataDto(
                        workoutTemplates = listOf(
                            com.movit.core.network.dto.ExploreWorkoutDto(
                                id = "wt-remove",
                                slug = "remove",
                                updatedAt = "2026-01-01",
                            ),
                        ),
                    ),
                ),
            )

            val engine = MockEngine { request ->
                when {
                    request.url.encodedPath.contains("sync") ->
                        respond(syncTombstoneBody(), HttpStatusCode.OK, jsonHeaders)
                    request.url.encodedPath.contains("home") ->
                        respond(homeOkBody(), HttpStatusCode.OK, jsonHeaders)
                    else -> respond("{}", HttpStatusCode.NotFound)
                }
            }
            val explore = ExploreSyncRepository(testMobileApi(engine, platform), { platform }, { localStore })
            val orchestrator = buildOrchestrator(testMobileApi(engine, platform), platform, localStore, explore)

            orchestrator.syncIfNeeded(forceCheck = true)

            assertTrue(explore.readCached()?.workoutTemplates.isNullOrEmpty())
        }
    }

    private fun buildOrchestrator(
        api: com.movit.core.network.MovitMobileApi,
        platform: FakeMovitPlatformBindings,
        localStore: MovitLocalStore,
        explore: ExploreSyncRepository,
    ): MovitSyncOrchestrator {
        val home = HomeSyncRepository(api, { platform }, { localStore })
        val reports = ReportsSyncRepository(api, { platform }, { localStore })
        val plan = PlanSyncRepository(api, { platform }, home)
        val audioManifestCache = AudioManifestCache(localStore)
        return MovitSyncOrchestrator(
            api = api,
            platform = { platform },
            localStore = localStore,
            homeSync = home,
            exploreSync = explore,
            reportsSync = reports,
            planSync = plan,
            metadataStore = MovitSyncMetadataStore(localStore),
            audioManifestCache = audioManifestCache,
            audioPrefetchRunner = AudioPrefetchRunner(audioManifestCache, FakeAudioFileDownloader()),
            offlineWrites = OfflineWriteQueue(localStore, api) { platform },
            trainingConfig = TrainingConfigRepository(localStore),
            systemMessageCache = SystemMessageCache(localStore),
            exercisePreferenceLocalStore = ExercisePreferenceLocalStore(localStore),
            dayCustomizationLocalStore = DayCustomizationLocalStore(localStore),
            messageLibraryCache = MessageLibraryCache(localStore),
        )
    }

    private fun syncWithCatalogBody(): String {
        val workout = buildJsonObject {
            put("id", "wt-sync-1")
            put("slug", "quick-legs")
            put("updatedAt", "2026-06-11T00:00:00Z")
            putJsonObject("name") { put("en", "Quick Legs") }
            putJsonArray("exercises") { }
        }
        val program = buildJsonObject {
            put("id", "prog-sync-1")
            put("slug", "strength")
            put("durationWeeks", 8)
            put("updatedAt", "2026-06-11T00:00:00Z")
            putJsonObject("name") { put("en", "Strength") }
        }
        val exercise = buildJsonObject {
            put("id", "ex-sync-1")
            put("slug", "squat-sync")
            put("updatedAt", "2026-06-11T00:00:00Z")
            putJsonObject("name") { put("en", "Squat") }
        }
        return MovitJson.encodeToString(
            MobileSyncApiResponse.serializer(),
            MobileSyncApiResponse(
                success = true,
                timestamp = "2026-06-11T00:00:00Z",
                data = MobileSyncDataDto(
                    workoutTemplates = listOf(workout),
                    programs = listOf(program),
                    exercises = listOf(exercise),
                ),
                meta = SyncMetaDto(isFullSync = true),
            ),
        )
    }

    private fun syncTombstoneBody(): String = MovitJson.encodeToString(
        MobileSyncApiResponse.serializer(),
        MobileSyncApiResponse(
            success = true,
            timestamp = "2026-06-11T00:00:00Z",
            data = MobileSyncDataDto(
                deletedWorkoutTemplateIds = listOf("wt-remove"),
            ),
            meta = SyncMetaDto(isFullSync = false),
        ),
    )

    private fun homeOkBody(): String = MovitJson.encodeToString(
        HomeApiResponse.serializer(),
        HomeApiResponse(success = true, data = HomeDataDto()),
    )
}
