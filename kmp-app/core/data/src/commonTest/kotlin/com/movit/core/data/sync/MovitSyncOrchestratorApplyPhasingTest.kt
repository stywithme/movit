package com.movit.core.data.sync

import com.movit.core.data.audio.AudioPrefetchRunner
import com.movit.core.data.audio.FakeAudioFileDownloader
import com.movit.core.data.cache.AudioManifestCache
import com.movit.core.data.cache.MessageLibraryCache
import com.movit.core.data.cache.MovitCacheDriftDetector
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
import com.movit.core.data.repository.SyncCatalogOfflineRepository
import com.movit.core.data.repository.TrainingConfigRepository
import com.movit.core.data.repository.UserProgramEnrollmentLocalStore
import com.movit.core.data.repository.testLocalStore
import com.movit.core.data.repository.testMobileApi
import com.movit.core.data.repository.testPlanSyncRepository
import com.movit.core.network.MovitJson
import com.movit.core.network.dto.ExploreApiResponse
import com.movit.core.network.dto.ExploreDataDto
import com.movit.core.network.dto.HomeApiResponse
import com.movit.core.network.dto.HomeDataDto
import com.movit.core.network.dto.MessageLibraryStatsDto
import com.movit.core.network.dto.MobileSyncApiResponse
import com.movit.core.network.dto.MobileSyncDataDto
import com.movit.core.network.dto.SyncMessageContentDto
import com.movit.core.network.dto.SyncMessageTemplateDto
import com.movit.core.network.dto.SyncMetaDto
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * P2 / Option 2: message library apply is outside the critical-path transaction;
 * delta merges may defer until after [MovitSyncOrchestrator.SyncOutcome.Success].
 */
class MovitSyncOrchestratorApplyPhasingTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun fullSync_persistsMessageLibraryBeforeSuccess() {
        runBlocking {
            val platform = FakeMovitPlatformBindings()
            val localStore = testLocalStore(platform)
            val messageLibraryCache = MessageLibraryCache(localStore)
            val orchestrator = buildOrchestrator(
                api = testMobileApi(
                    MockEngine { request ->
                        when {
                            request.url.encodedPath.contains("sync") ->
                                respond(fullSyncWithLibraryBody(), HttpStatusCode.OK, jsonHeaders)
                            request.url.encodedPath.contains("explore") ->
                                respond(exploreOkBody(), HttpStatusCode.OK, jsonHeaders)
                            request.url.encodedPath.contains("home") ->
                                respond(homeOkBody(), HttpStatusCode.OK, jsonHeaders)
                            else -> respond("{}", HttpStatusCode.NotFound)
                        }
                    },
                    platform,
                ),
                platform = platform,
                localStore = localStore,
                messageLibraryCache = messageLibraryCache,
                deferredApplyScope = this,
            )

            val outcome = orchestrator.fullRefresh()
            assertIs<MovitSyncOrchestrator.SyncOutcome.Success>(outcome)
            assertEquals(1, messageLibraryCache.read().size)
            assertEquals("msg-full", messageLibraryCache.read().single().id)
        }
    }

    @Test
    fun deltaSync_withWarmLibrary_defersMessageLibraryMerge() {
        runBlocking {
            val platform = FakeMovitPlatformBindings()
            val localStore = testLocalStore(platform)
            val metadataStore = MovitSyncMetadataStore(localStore)
            metadataStore.writeLastSyncTimestamp("2026-01-01T00:00:00Z")
            metadataStore.writeEntityCounts(
                MovitCacheDriftDetector.EntityCounts(
                    exercises = 1,
                    workouts = 1,
                    programs = 1,
                ),
            )
            metadataStore.writeMessageStats(
                MessageLibraryStatsDto(
                    totalMessages = 1,
                    totalWithAudio = 0,
                    totalAssignments = 0,
                    fingerprint = "seed",
                ),
            )

            val messageLibraryCache = MessageLibraryCache(localStore)
            messageLibraryCache.replaceFull(listOf(seedMessage("msg-seed")))

            val trainingConfig = TrainingConfigRepository(localStore, messageLibraryCache)
            trainingConfig.applySyncExercises(
                exercises = listOf(
                    buildJsonObject {
                        put("slug", "bodyweight-squat")
                        put("configVersion", 1)
                    },
                ),
                isFullSync = true,
            )

            val orchestrator = buildOrchestrator(
                api = testMobileApi(
                    MockEngine { request ->
                        when {
                            request.url.encodedPath.contains("sync") ->
                                respond(deltaLibraryBody(), HttpStatusCode.OK, jsonHeaders)
                            request.url.encodedPath.contains("explore") ->
                                respond(exploreOkBody(), HttpStatusCode.OK, jsonHeaders)
                            request.url.encodedPath.contains("home") ->
                                respond(homeOkBody(), HttpStatusCode.OK, jsonHeaders)
                            else -> respond("{}", HttpStatusCode.NotFound)
                        }
                    },
                    platform,
                ),
                platform = platform,
                localStore = localStore,
                messageLibraryCache = messageLibraryCache,
                trainingConfig = trainingConfig,
                deferredApplyScope = this,
            )

            val outcome = orchestrator.syncIfNeeded(forceCheck = true)
            assertIs<MovitSyncOrchestrator.SyncOutcome.Success>(outcome)

            val ids = messageLibraryCache.read().map { it.id }.toSet()
            assertTrue("msg-seed" in ids, "seed message retained")
            assertTrue("msg-delta" in ids, "delta message merged after deferred apply")
        }
    }

    private fun buildOrchestrator(
        api: com.movit.core.network.MovitMobileApi,
        platform: FakeMovitPlatformBindings,
        localStore: MovitLocalStore,
        messageLibraryCache: MessageLibraryCache,
        trainingConfig: TrainingConfigRepository = TrainingConfigRepository(localStore, messageLibraryCache),
        deferredApplyScope: kotlinx.coroutines.CoroutineScope,
    ): MovitSyncOrchestrator {
        val home = HomeSyncRepository(api, { platform }, { localStore })
        val explore = ExploreSyncRepository(api, { platform }, { localStore })
        val reports = ReportsSyncRepository(api, { platform }, { localStore })
        val plan = testPlanSyncRepository(api, platform, localStore, home)
        return MovitSyncOrchestrator(
            api = api,
            platform = { platform },
            localStore = localStore,
            homeSync = home,
            exploreSync = explore,
            reportsSync = reports,
            planSync = plan,
            metadataStore = MovitSyncMetadataStore(localStore),
            audioManifestCache = AudioManifestCache(localStore),
            audioPrefetchRunner = AudioPrefetchRunner(AudioManifestCache(localStore), FakeAudioFileDownloader()),
            offlineWrites = OfflineWriteQueue(localStore, api, { platform }),
            trainingConfig = trainingConfig,
            catalogOffline = SyncCatalogOfflineRepository(localStore, trainingConfig),
            systemMessageCache = SystemMessageCache(localStore),
            exercisePreferenceLocalStore = ExercisePreferenceLocalStore(localStore),
            dayCustomizationLocalStore = DayCustomizationLocalStore(localStore),
            messageLibraryCache = messageLibraryCache,
            userProgramEnrollmentLocalStore = UserProgramEnrollmentLocalStore(localStore),
            deferredApplyScope = deferredApplyScope,
        )
    }

    private fun seedMessage(id: String) = SyncMessageTemplateDto(
        id = id,
        code = id,
        category = "general",
        content = SyncMessageContentDto(en = "hello"),
    )

    private fun fullSyncWithLibraryBody(): String = MovitJson.encodeToString(
        MobileSyncApiResponse.serializer(),
        MobileSyncApiResponse(
            success = true,
            timestamp = "2026-06-11T00:00:00Z",
            data = MobileSyncDataDto(
                messageLibrary = listOf(seedMessage("msg-full")),
            ),
            meta = SyncMetaDto(isFullSync = true),
        ),
    )

    private fun deltaLibraryBody(): String = MovitJson.encodeToString(
        MobileSyncApiResponse.serializer(),
        MobileSyncApiResponse(
            success = true,
            timestamp = "2026-06-11T01:00:00Z",
            data = MobileSyncDataDto(
                messageLibrary = listOf(seedMessage("msg-delta")),
            ),
            meta = SyncMetaDto(
                isFullSync = false,
                totalExercises = 1,
                totalWorkoutTemplates = 1,
                totalPrograms = 1,
                exercisesInResponse = 0,
            ),
        ),
    )

    private fun exploreOkBody(): String = MovitJson.encodeToString(
        ExploreApiResponse.serializer(),
        ExploreApiResponse(success = true, data = ExploreDataDto()),
    )

    private fun homeOkBody(): String = MovitJson.encodeToString(
        HomeApiResponse.serializer(),
        HomeApiResponse(success = true, data = HomeDataDto()),
    )
}
