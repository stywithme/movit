package com.movit.core.data.sync

import com.movit.core.data.audio.AudioPrefetchRunner
import com.movit.core.data.audio.FakeAudioFileDownloader
import com.movit.core.data.cache.AudioManifestCache
import com.movit.core.data.cache.MessageLibraryCache
import com.movit.core.data.cache.MovitCacheDriftDetector
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
import com.movit.core.data.repository.ReportsSyncRepository
import com.movit.core.data.repository.SyncCatalogOfflineRepository
import com.movit.core.data.repository.TrainingConfigRepository
import com.movit.core.data.repository.UserProgramEnrollmentLocalStore
import com.movit.core.data.repository.testMobileApi
import com.movit.core.data.repository.testPlanSyncRepository
import com.movit.core.network.MovitJson
import com.movit.core.network.dto.MobileSyncApiResponse
import com.movit.core.network.dto.MobileSyncDataDto
import com.movit.core.network.dto.SyncMetaDto
import com.movit.core.training.config.ExerciseConfig
import com.movit.core.training.config.ExerciseConfigRecord
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MovitSyncOrchestratorConcurrencyTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun parallelSyncIfNeeded_secondCallSkippedWhileFirstHoldsLock() {
        runBlocking {
            val platform = FakeMovitPlatformBindings()
            val localStore = InMemoryMovitLocalStore()
            var syncHits = 0
            val engine = MockEngine { request ->
                when {
                    request.url.encodedPath.contains("sync") -> {
                        syncHits++
                        delay(200)
                        respond(syncBody(), HttpStatusCode.OK, jsonHeaders)
                    }
                    request.url.encodedPath.contains("explore") ->
                        respond(exploreOk(), HttpStatusCode.OK, jsonHeaders)
                    request.url.encodedPath.contains("home") ->
                        respond(homeOk(), HttpStatusCode.OK, jsonHeaders)
                    else -> respond("{}", HttpStatusCode.NotFound)
                }
            }
            val orchestrator = buildOrchestrator(testMobileApi(engine, platform), platform, localStore)

            val first = async { orchestrator.syncIfNeeded(forceCheck = true) }
            delay(30)
            val second = orchestrator.syncIfNeeded(forceCheck = true)
            val firstResult = first.await()

            assertIs<MovitSyncOrchestrator.SyncOutcome.Skipped>(second)
            assertIs<MovitSyncOrchestrator.SyncOutcome.Success>(firstResult)
            assertEquals(1, syncHits)
        }
    }

    @Test
    fun awaitSyncIdle_unblocksAfterCycleEnds() {
        runBlocking {
            val platform = FakeMovitPlatformBindings()
            val localStore = InMemoryMovitLocalStore()
            val engine = MockEngine { request ->
                when {
                    request.url.encodedPath.contains("sync") -> {
                        delay(150)
                        respond(syncBody(), HttpStatusCode.OK, jsonHeaders)
                    }
                    request.url.encodedPath.contains("explore") ->
                        respond(exploreOk(), HttpStatusCode.OK, jsonHeaders)
                    request.url.encodedPath.contains("home") ->
                        respond(homeOk(), HttpStatusCode.OK, jsonHeaders)
                    else -> respond("{}", HttpStatusCode.NotFound)
                }
            }
            val orchestrator = buildOrchestrator(testMobileApi(engine, platform), platform, localStore)

            val syncJob = async { orchestrator.syncIfNeeded(forceCheck = true) }
            delay(20)
            assertTrue(orchestrator.isSyncInProgress)
            orchestrator.awaitSyncIdle(timeoutMs = 5_000)
            assertTrue(!orchestrator.isSyncInProgress)
            syncJob.await()
        }
    }

    @Test
    fun transaction_wrapsBlockOnInMemoryStore() {
        val store = InMemoryMovitLocalStore()
        val result = store.transaction {
            store.writeJsonCache("s", "k", "v")
            store.readJsonCache("s", "k")
        }
        assertEquals("v", result)
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
        val audioManifestCache = AudioManifestCache(localStore)
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
            audioManifestCache = audioManifestCache,
            audioPrefetchRunner = AudioPrefetchRunner(audioManifestCache, FakeAudioFileDownloader()),
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
        MobileSyncApiResponse.serializer(),
        MobileSyncApiResponse(
            success = true,
            timestamp = "2026-07-10T00:00:00Z",
            data = MobileSyncDataDto(),
            meta = SyncMetaDto(isFullSync = true),
        ),
    )

    private fun exploreOk(): String = """{"success":true,"data":{}}"""
    private fun homeOk(): String = """{"success":true,"data":{}}"""
}

class MovitSyncOrchestratorDriftLoopTest {

    @Test
    fun catalogCounts_matchIndexes_notExploreBlobSize() {
        val store = InMemoryMovitLocalStore()
        val training = TrainingConfigRepository(store)
        training.seedRecord(
            ExerciseConfigRecord.fromConfig("e1", "a", "t", ExerciseConfig()),
        )
        training.seedRecord(
            ExerciseConfigRecord.fromConfig("e2", "b", "t", ExerciseConfig()),
        )
        val catalog = SyncCatalogOfflineRepository(store, training)
        store.writeJsonCache(
            MovitCacheKeys.CATALOG_INDEX_STORE,
            MovitCacheKeys.PROGRAM_ID_INDEX,
            MovitJson.encodeToString(ListSerializer(String.serializer()), listOf("p1")),
        )
        store.writeJsonCache(
            MovitCacheKeys.CATALOG_INDEX_STORE,
            MovitCacheKeys.WORKOUT_TEMPLATE_ID_INDEX,
            MovitJson.encodeToString(ListSerializer(String.serializer()), listOf("w1", "w2")),
        )

        val local = MovitCacheDriftDetector.EntityCounts(
            exercises = training.allCachedSlugs().size,
            workouts = catalog.allWorkoutTemplateIds().size,
            programs = catalog.allProgramIds().size,
        )
        assertEquals(2, local.exercises)
        assertEquals(2, local.workouts)
        assertEquals(1, local.programs)

        val verdict = MovitCacheDriftDetector.detectEntityDrift(
            local = local,
            meta = SyncMetaDto(
                totalExercises = 2,
                totalWorkoutTemplates = 2,
                totalPrograms = 1,
                isFullSync = false,
            ),
            hasNoEntityDelta = true,
        )
        assertEquals(MovitCacheDriftDetector.DriftVerdict.Ok, verdict)
    }
}
