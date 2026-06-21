package com.movit.core.data.sync

import com.movit.core.data.audio.AudioPrefetchRunner
import com.movit.core.data.audio.FakeAudioFileDownloader
import com.movit.core.data.cache.AudioManifestCache
import com.movit.core.data.cache.MessageLibraryCache
import com.movit.core.data.cache.MovitSyncMetadataStore
import com.movit.core.data.cache.SystemMessageCache
import com.movit.core.data.local.InMemoryMovitLocalStore
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
import com.movit.core.data.repository.testPlanSyncRepository
import com.movit.core.data.repository.testLocalStore
import com.movit.core.data.repository.testMobileApi
import com.movit.core.network.MovitJson
import com.movit.core.network.dto.AudioFileInfoDto
import com.movit.core.network.dto.AudioManifestDto
import com.movit.core.network.dto.ExploreApiResponse
import com.movit.core.network.dto.ExploreDataDto
import com.movit.core.network.dto.HomeApiResponse
import com.movit.core.network.dto.HomeDataDto
import com.movit.core.network.dto.MobileSyncApiResponse
import com.movit.core.network.dto.MobileSyncDataDto
import com.movit.core.network.dto.SyncMetaDto
import com.movit.core.training.config.ExerciseConfigParser
import com.movit.core.training.config.ExerciseConfigRecord
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MovitSyncOrchestratorTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun readColdOfflineBundle_returnsStructuredCachedData() {
        runBlocking {
            val platform = FakeMovitPlatformBindings(auth = null)
            val localStore = testLocalStore(platform)
            localStore.writeJsonCache(
                MovitCacheKeys.HOME_STORE,
                MovitCacheKeys.HOME_DATA,
                MovitJson.encodeToString(HomeDataDto.serializer(), HomeDataDto()),
            )
            localStore.writeJsonCache(
                MovitCacheKeys.EXPLORE_STORE,
                MovitCacheKeys.EXPLORE_DATA,
                MovitJson.encodeToString(ExploreDataDto.serializer(), ExploreDataDto()),
            )

            val engine = MockEngine { respond("{}", HttpStatusCode.ServiceUnavailable) }
            val api = testMobileApi(engine, platform)
            val orchestrator = buildOrchestrator(api, platform, localStore)

            val bundle = orchestrator.readColdOfflineBundle()

            assertNotNull(bundle.home)
            assertNotNull(bundle.explore)
        }
    }

    @Test
    fun syncIfNeeded_withoutNetwork_returnsOfflineBundle() {
        runBlocking {
            val platform = FakeMovitPlatformBindings()
            val localStore = testLocalStore(platform)
            localStore.writeJsonCache(
                MovitCacheKeys.EXPLORE_STORE,
                MovitCacheKeys.EXPLORE_DATA,
                MovitJson.encodeToString(ExploreDataDto.serializer(), ExploreDataDto()),
            )

            val engine = MockEngine { respond("down", HttpStatusCode.BadGateway) }
            val api = testMobileApi(engine, platform)
            val orchestrator = buildOrchestrator(api, platform, localStore)

            val outcome = orchestrator.syncIfNeeded(forceCheck = true)

            assertIs<MovitSyncOrchestrator.SyncOutcome.Offline>(outcome)
            assertNotNull(outcome.bundle.explore)
        }
    }

    @Test
    fun syncIfNeeded_withAudioManifest_triggersPrefetchDownload() {
        runBlocking {
            val platform = FakeMovitPlatformBindings()
            val localStore = testLocalStore(platform)
            val downloader = FakeAudioFileDownloader()
            val engine = MockEngine { request ->
                when {
                    request.url.encodedPath.contains("sync") ->
                        respond(syncWithAudioBody(), HttpStatusCode.OK, jsonHeaders)
                    request.url.encodedPath.contains("explore") ->
                        respond(exploreOkBody(), HttpStatusCode.OK, jsonHeaders)
                    request.url.encodedPath.contains("home") ->
                        respond(homeOkBody(), HttpStatusCode.OK, jsonHeaders)
                    else -> respond("{}", HttpStatusCode.NotFound)
                }
            }
            val api = testMobileApi(engine, platform)
            val orchestrator = buildOrchestrator(
                api = api,
                platform = platform,
                localStore = localStore,
                downloader = downloader,
            )

            orchestrator.syncIfNeeded(forceCheck = true)

            assertEquals(1, downloader.downloadedBatches.size)
            assertEquals("tts_en_1.wav", downloader.downloadedBatches.single().second.single().filename)
        }
    }

    @Test
    fun syncIfNeeded_sparseTrainingConfig_escalatesToFullRefresh() {
        runBlocking {
            val platform = FakeMovitPlatformBindings()
            val localStore = testLocalStore(platform)
            val metadataStore = MovitSyncMetadataStore(localStore)
            metadataStore.writeLastSyncTimestamp("2026-01-01T00:00:00Z")
            metadataStore.writeEntityCounts(
                com.movit.core.data.cache.MovitCacheDriftDetector.EntityCounts(
                    exercises = 50,
                    workouts = 5,
                    programs = 2,
                ),
            )

            val trainingConfig = TrainingConfigRepository(localStore)
            val squatJson = readSquatFixture()
            val squatConfig = ExerciseConfigParser.parseConfigJson(squatJson)
            trainingConfig.seedRecord(
                ExerciseConfigRecord.fromConfig(
                    id = "ex-squat",
                    slug = "bodyweight-squat",
                    updatedAt = "2026-06-11",
                    config = squatConfig,
                ),
            )

            val syncForceRefreshFlags = mutableListOf<Boolean>()
            val engine = MockEngine { request ->
                when {
                    request.url.encodedPath.contains("sync") -> {
                        val forceRefresh = request.url.parameters["forceRefresh"] == "true"
                        syncForceRefreshFlags += forceRefresh
                        val body = if (forceRefresh) {
                            syncBody(isFullSync = true)
                        } else {
                            syncDeltaEmptyMeta(totalExercises = 10)
                        }
                        respond(body, HttpStatusCode.OK, jsonHeaders)
                    }
                    request.url.encodedPath.contains("explore") ->
                        respond(exploreOkBody(), HttpStatusCode.OK, jsonHeaders)
                    request.url.encodedPath.contains("home") ->
                        respond(homeOkBody(), HttpStatusCode.OK, jsonHeaders)
                    else -> respond("{}", HttpStatusCode.NotFound)
                }
            }
            val api = testMobileApi(engine, platform)
            val orchestrator = buildOrchestrator(api, platform, localStore)

            orchestrator.syncIfNeeded(forceCheck = true)

            // delta → backfill full refresh → planSync.refreshActiveUserProgramId() (fetchSyncUserPrograms)
            assertEquals(listOf(false, true, false), syncForceRefreshFlags)
            assertEquals(
                trainingConfig.allCachedSlugs().size,
                metadataStore.readEntityCounts().exercises,
            )
        }
    }

    @Test
    fun syncIfNeeded_replaysPendingOutboxAfterSuccessfulSync() {
        runBlocking {
            val platform = object : FakeMovitPlatformBindings() {
                override fun isNetworkAvailable(): Boolean = false
            }
            val localStore = InMemoryMovitLocalStore()
            val engine = MockEngine { request ->
                when {
                    request.url.encodedPath.contains("sync") ->
                        respond(syncBody(), HttpStatusCode.OK, jsonHeaders)
                    request.url.encodedPath.contains("explore") ->
                        respond(exploreOkBody(), HttpStatusCode.OK, jsonHeaders)
                    request.url.encodedPath.contains("home") ->
                        respond(homeOkBody(), HttpStatusCode.OK, jsonHeaders)
                    request.url.encodedPath.contains("plan/complete") ->
                        respond("""{"success":true}""", HttpStatusCode.OK, jsonHeaders)
                    else -> respond("{}", HttpStatusCode.NotFound)
                }
            }
            val onlinePlatform = object : FakeMovitPlatformBindings() {
                override fun isNetworkAvailable(): Boolean = true
            }
            val api = testMobileApi(engine, onlinePlatform)
            val offlineWrites = OfflineWriteQueue(localStore, api) { onlinePlatform }
            val orchestrator = buildOrchestrator(
                api = api,
                platform = onlinePlatform,
                localStore = localStore,
                offlineWrites = offlineWrites,
            )

            OfflineWriteQueue(localStore, api) { platform }.enqueuePlanComplete("op-pending-before-sync")
            assertEquals(1L, offlineWrites.pendingCount())

            orchestrator.syncIfNeeded(forceCheck = true)

            assertEquals(0L, offlineWrites.pendingCount())
        }
    }

    @Test
    fun syncIfNeeded_replaysPendingOutboxBeforeFetchingSync() {
        runBlocking {
            val offlinePlatform = object : FakeMovitPlatformBindings() {
                override fun isNetworkAvailable(): Boolean = false
            }
            val onlinePlatform = object : FakeMovitPlatformBindings() {
                override fun isNetworkAvailable(): Boolean = true
            }
            val localStore = InMemoryMovitLocalStore()
            val requestPaths = mutableListOf<String>()
            val engine = MockEngine { request ->
                val path = request.url.encodedPath
                requestPaths += path
                when {
                    path.contains("plan/complete") ->
                        respond("""{"success":true}""", HttpStatusCode.OK, jsonHeaders)
                    path.contains("sync") ->
                        respond(syncBody(), HttpStatusCode.OK, jsonHeaders)
                    path.contains("explore") ->
                        respond(exploreOkBody(), HttpStatusCode.OK, jsonHeaders)
                    path.contains("home") ->
                        respond(homeOkBody(), HttpStatusCode.OK, jsonHeaders)
                    else -> respond("{}", HttpStatusCode.NotFound)
                }
            }
            val api = testMobileApi(engine, onlinePlatform)
            val offlineWrites = OfflineWriteQueue(localStore, api) { onlinePlatform }
            val orchestrator = buildOrchestrator(
                api = api,
                platform = onlinePlatform,
                localStore = localStore,
                offlineWrites = offlineWrites,
            )

            OfflineWriteQueue(localStore, api) { offlinePlatform }.enqueuePlanComplete("op-before-sync")

            orchestrator.syncIfNeeded(forceCheck = true)

            val outboxIndex = requestPaths.indexOfFirst { it.contains("plan/complete") }
            val syncIndex = requestPaths.indexOfFirst { it.contains("sync") }
            assertTrue(outboxIndex >= 0, "Expected outbox replay request.")
            assertTrue(syncIndex >= 0, "Expected sync request.")
            assertTrue(outboxIndex < syncIndex, "Outbox replay must happen before fetching sync.")
        }
    }

    private fun buildOrchestrator(
        api: com.movit.core.network.MovitMobileApi,
        platform: FakeMovitPlatformBindings,
        localStore: MovitLocalStore,
        downloader: FakeAudioFileDownloader = FakeAudioFileDownloader(),
        offlineWrites: OfflineWriteQueue? = null,
    ): MovitSyncOrchestrator {
        val home = HomeSyncRepository(api, { platform }, { localStore })
        val explore = ExploreSyncRepository(api, { platform }, { localStore })
        val reports = ReportsSyncRepository(api, { platform }, { localStore })
        val plan = testPlanSyncRepository(api, platform, localStore, home)
        val audioManifestCache = AudioManifestCache(localStore)
        val queue = offlineWrites ?: OfflineWriteQueue(localStore, api) { platform }
        val trainingConfig = TrainingConfigRepository(localStore, MessageLibraryCache(localStore))
        val catalogOffline = SyncCatalogOfflineRepository(localStore, trainingConfig)
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
            audioPrefetchRunner = AudioPrefetchRunner(audioManifestCache, downloader),
            offlineWrites = queue,
            trainingConfig = trainingConfig,
            catalogOffline = catalogOffline,
            systemMessageCache = SystemMessageCache(localStore),
            exercisePreferenceLocalStore = ExercisePreferenceLocalStore(localStore),
            dayCustomizationLocalStore = DayCustomizationLocalStore(localStore),
            messageLibraryCache = MessageLibraryCache(localStore),
            userProgramEnrollmentLocalStore = UserProgramEnrollmentLocalStore(localStore),
        )
    }

    private fun syncBody(isFullSync: Boolean = true): String = MovitJson.encodeToString(
        MobileSyncApiResponse.serializer(),
        MobileSyncApiResponse(
            success = true,
            timestamp = "2026-06-11T00:00:00Z",
            data = MobileSyncDataDto(),
            meta = SyncMetaDto(isFullSync = isFullSync),
        ),
    )

    private fun syncDeltaEmptyMeta(totalExercises: Int): String = MovitJson.encodeToString(
        MobileSyncApiResponse.serializer(),
        MobileSyncApiResponse(
            success = true,
            timestamp = "2026-06-11T00:00:00Z",
            data = MobileSyncDataDto(),
            meta = SyncMetaDto(
                totalExercises = totalExercises,
                totalWorkoutTemplates = 5,
                totalPrograms = 2,
                isFullSync = false,
                exercisesInResponse = 0,
            ),
        ),
    )

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

    private fun syncWithAudioBody(): String = MovitJson.encodeToString(
        MobileSyncApiResponse.serializer(),
        MobileSyncApiResponse(
            success = true,
            timestamp = "2026-06-11T00:00:00Z",
            data = MobileSyncDataDto(
                audioManifest = AudioManifestDto(
                    baseUrl = "https://cdn.test/audio",
                    files = listOf(
                        AudioFileInfoDto(filename = "tts_en_1.wav", url = "/a.wav", language = "en"),
                    ),
                ),
            ),
            meta = SyncMetaDto(isFullSync = true),
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
