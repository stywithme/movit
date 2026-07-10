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
import com.movit.core.data.repository.testMobileApi
import com.movit.core.network.MovitJson
import com.movit.core.network.dto.ExploreApiResponse
import com.movit.core.network.dto.ExploreDataDto
import com.movit.core.network.dto.HomeApiResponse
import com.movit.core.network.dto.HomeDataDto
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MovitSyncOrchestratorErrorClassificationTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun malformedSyncJson_returnsDecodeError_notOffline() {
        runBlocking {
            val platform = FakeMovitPlatformBindings()
            val localStore = InMemoryMovitLocalStore()
            localStore.writeJsonCache(
                MovitCacheKeys.EXPLORE_STORE,
                MovitCacheKeys.EXPLORE_DATA,
                MovitJson.encodeToString(ExploreDataDto.serializer(), ExploreDataDto()),
            )

            val engine = MockEngine { request ->
                when {
                    request.url.encodedPath.contains("sync") ->
                        respond("{not-json", HttpStatusCode.OK, jsonHeaders)
                    else -> respond("{}", HttpStatusCode.NotFound)
                }
            }
            val orchestrator = buildOrchestrator(testMobileApi(engine, platform), platform, localStore)

            val outcome = orchestrator.syncIfNeeded(forceCheck = true)

            val error = assertIs<MovitSyncOrchestrator.SyncOutcome.Error>(outcome)
            assertEquals(MovitSyncOrchestrator.SyncOutcome.ErrorKind.Decode, error.kind)

            val telemetry = MovitSyncTelemetry(localStore)
            assertEquals("error_decode", telemetry.readLastSyncCycle()?.outcome)
            assertEquals(1, telemetry.readCounters().syncErrorDecode)
        }
    }

    @Test
    fun networkFailure_withoutCachedBundle_returnsNetworkError() {
        runBlocking {
            val platform = FakeMovitPlatformBindings()
            val localStore = InMemoryMovitLocalStore()
            val engine = MockEngine { respond("down", HttpStatusCode.BadGateway) }
            val orchestrator = buildOrchestrator(testMobileApi(engine, platform), platform, localStore)

            val outcome = orchestrator.syncIfNeeded(forceCheck = true)

            val error = assertIs<MovitSyncOrchestrator.SyncOutcome.Error>(outcome)
            assertEquals(MovitSyncOrchestrator.SyncOutcome.ErrorKind.Network, error.kind)
            assertEquals("error_network", MovitSyncTelemetry(localStore).readLastSyncCycle()?.outcome)
        }
    }

    @Test
    fun networkFailure_withCachedBundle_returnsOffline() {
        runBlocking {
            val platform = FakeMovitPlatformBindings()
            val localStore = InMemoryMovitLocalStore()
            localStore.writeJsonCache(
                MovitCacheKeys.HOME_STORE,
                MovitCacheKeys.HOME_DATA,
                MovitJson.encodeToString(HomeDataDto.serializer(), HomeDataDto()),
            )

            val engine = MockEngine { respond("down", HttpStatusCode.BadGateway) }
            val orchestrator = buildOrchestrator(testMobileApi(engine, platform), platform, localStore)

            val outcome = orchestrator.syncIfNeeded(forceCheck = true)

            assertIs<MovitSyncOrchestrator.SyncOutcome.Offline>(outcome)
            assertEquals("offline_network", MovitSyncTelemetry(localStore).readLastSyncCycle()?.outcome)
        }
    }

    @Test
    fun httpSyncFailure_withoutCache_returnsHttpError() {
        runBlocking {
            val platform = FakeMovitPlatformBindings()
            val localStore = InMemoryMovitLocalStore()
            val engine = MockEngine { request ->
                when {
                    request.url.encodedPath.contains("sync") ->
                        respond("denied", HttpStatusCode.Forbidden, jsonHeaders)
                    else -> respond("{}", HttpStatusCode.NotFound)
                }
            }
            val orchestrator = buildOrchestrator(testMobileApi(engine, platform), platform, localStore)

            val outcome = orchestrator.syncIfNeeded(forceCheck = true)

            val error = assertIs<MovitSyncOrchestrator.SyncOutcome.Error>(outcome)
            assertEquals(MovitSyncOrchestrator.SyncOutcome.ErrorKind.Http, error.kind)
            assertEquals("error_http", MovitSyncTelemetry(localStore).readLastSyncCycle()?.outcome)
        }
    }

    @Test
    fun cancellationException_isRethrown() {
        runBlocking {
            val platform = FakeMovitPlatformBindings()
            val localStore = InMemoryMovitLocalStore()
            val engine = MockEngine {
                awaitCancellation()
            }
            val orchestrator = buildOrchestrator(testMobileApi(engine, platform), platform, localStore)

            val deferred = async { orchestrator.fullRefresh() }
            delay(50)
            deferred.cancel()

            assertFailsWith<CancellationException> { deferred.await() }
        }
    }

    @Test
    fun successfulSync_recordsCycleDiagnostics() {
        runBlocking {
            val platform = FakeMovitPlatformBindings()
            val localStore = InMemoryMovitLocalStore()
            val engine = MockEngine { request ->
                when {
                    request.url.encodedPath.contains("sync") ->
                        respond(syncBody(), HttpStatusCode.OK, jsonHeaders)
                    request.url.encodedPath.contains("explore") ->
                        respond(exploreOkBody(), HttpStatusCode.OK, jsonHeaders)
                    request.url.encodedPath.contains("home") ->
                        respond(homeOkBody(), HttpStatusCode.OK, jsonHeaders)
                    else -> respond("{}", HttpStatusCode.NotFound)
                }
            }
            val orchestrator = buildOrchestrator(testMobileApi(engine, platform), platform, localStore)

            val outcome = orchestrator.syncIfNeeded(forceCheck = true)
            assertIs<MovitSyncOrchestrator.SyncOutcome.Success>(outcome)

            val cycle = MovitSyncTelemetry(localStore).readLastSyncCycle()
            assertNotNull(cycle)
            assertEquals("syncIfNeeded", cycle.reason)
            assertEquals("success", cycle.outcome)
            assertEquals(false, cycle.escalatedToFull)
            assertTrue(cycle.isFull == true)
        }
    }

    private fun buildOrchestrator(
        api: com.movit.core.network.MovitMobileApi,
        platform: FakeMovitPlatformBindings,
        localStore: MovitLocalStore,
    ): MovitSyncOrchestrator {
        val home = HomeSyncRepository(api, { platform }, { localStore })
        val explore = ExploreSyncRepository(api, { platform }, { localStore })
        val reports = ReportsSyncRepository(api, { platform }, { localStore })
        val plan = testPlanSyncRepository(api, platform, localStore, home)
        val trainingConfig = TrainingConfigRepository(localStore, MessageLibraryCache(localStore))
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
            messageLibraryCache = MessageLibraryCache(localStore),
            userProgramEnrollmentLocalStore = UserProgramEnrollmentLocalStore(localStore),
        )
    }

    private fun syncBody(): String = MovitJson.encodeToString(
        com.movit.core.network.dto.MobileSyncApiResponse.serializer(),
        com.movit.core.network.dto.MobileSyncApiResponse(
            success = true,
            timestamp = "2026-06-11T00:00:00Z",
            data = com.movit.core.network.dto.MobileSyncDataDto(),
            meta = com.movit.core.network.dto.SyncMetaDto(isFullSync = true),
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
