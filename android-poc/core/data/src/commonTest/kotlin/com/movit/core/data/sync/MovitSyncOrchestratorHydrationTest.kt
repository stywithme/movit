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
import com.movit.core.data.repository.PlanSyncRepository
import com.movit.core.data.repository.ReportsSyncRepository
import com.movit.core.data.repository.TrainingConfigRepository
import com.movit.core.data.repository.testLocalStore
import com.movit.core.data.repository.testMobileApi
import com.movit.core.network.MovitJson
import com.movit.core.network.dto.ExploreApiResponse
import com.movit.core.network.dto.ExploreDataDto
import com.movit.core.network.dto.HomeApiResponse
import com.movit.core.network.dto.HomeDataDto
import com.movit.core.network.dto.LocalizedNameDto
import com.movit.core.network.dto.MessageLibraryStatsDto
import com.movit.core.network.dto.MobileSyncApiResponse
import com.movit.core.network.dto.MobileSyncDataDto
import com.movit.core.network.dto.PlannedWorkoutReportExportDto
import com.movit.core.network.dto.SyncMessageTemplateDto
import com.movit.core.network.dto.SyncMetaDto
import com.movit.core.network.dto.SyncSystemMessageDto
import com.movit.core.network.dto.UserExercisePreferenceSyncDto
import com.movit.core.network.dto.UserProgramExportDto
import com.movit.core.training.config.ExerciseConfigParser
import com.movit.core.training.config.ExerciseConfigRecord
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.test.Test
import kotlin.test.assertEquals

class MovitSyncOrchestratorHydrationTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun runSyncCycle_invokesAllFivePayloadHydrations() {
        runBlocking {
            val platform = FakeMovitPlatformBindings()
            val localStore = testLocalStore(platform)
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

            val systemMessageCache = RecordingSystemMessageCache(localStore)
            val exercisePreferenceLocalStore = RecordingExercisePreferenceLocalStore(localStore)
            val reportsSync = RecordingReportsSyncRepository(
                api = testMobileApi(MockEngine { respond("{}", HttpStatusCode.NotFound) }, platform),
                platform = { platform },
                localStore = { localStore },
            )
            val dayCustomizationLocalStore = RecordingDayCustomizationLocalStore(localStore)
            val messageLibraryCache = MessageLibraryCache(localStore)

            val engine = MockEngine { request ->
                when {
                    request.url.encodedPath.contains("sync") ->
                        respond(fullPayloadSyncBody(), HttpStatusCode.OK, jsonHeaders)
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
                trainingConfig = trainingConfig,
                systemMessageCache = systemMessageCache,
                exercisePreferenceLocalStore = exercisePreferenceLocalStore,
                reportsSync = reportsSync,
                dayCustomizationLocalStore = dayCustomizationLocalStore,
                messageLibraryCache = messageLibraryCache,
            )

            orchestrator.syncIfNeeded(forceCheck = true)

            assertEquals(1, systemMessageCache.saveCalls.size)
            assertEquals("SYS_WELCOME", systemMessageCache.saveCalls.single().single().code)
            assertEquals(1, systemMessageCache.loadIntoRegistryCalls)

            assertEquals(1, exercisePreferenceLocalStore.hydrateCalls.size)
            assertEquals("ex-1", exercisePreferenceLocalStore.hydrateCalls.single().single().exerciseId)

            assertEquals(1, reportsSync.hydrateCalls.size)
            assertEquals("pw-1", reportsSync.hydrateCalls.single().single().plannedWorkoutId)

            assertEquals(1, dayCustomizationLocalStore.hydrateCalls.size)
            assertEquals("up-1", dayCustomizationLocalStore.hydrateCalls.single().userProgramId)

            assertEquals(1, messageLibraryCache.read().size)
            assertEquals("msg-1", messageLibraryCache.read().single().id)
        }
    }

    @Test
    fun runSyncCycle_messageLibraryUpdatesStoredMessageStats() {
        runBlocking {
            val platform = FakeMovitPlatformBindings()
            val localStore = testLocalStore(platform)
            val metadataStore = MovitSyncMetadataStore(localStore)
            metadataStore.writeMessageStats(
                MessageLibraryStatsDto(
                    totalMessages = 1,
                    totalWithAudio = 0,
                    totalAssignments = 0,
                    fingerprint = "old",
                ),
            )

            val trainingConfig = TrainingConfigRepository(localStore)
            trainingConfig.seedRecord(
                ExerciseConfigRecord.fromConfig(
                    id = "ex-squat",
                    slug = "bodyweight-squat",
                    updatedAt = "2026-06-11",
                    config = ExerciseConfigParser.parseConfigJson(readSquatFixture()),
                ),
            )

            val engine = MockEngine { request ->
                when {
                    request.url.encodedPath.contains("sync") ->
                        respond(messageLibraryOnlySyncBody(), HttpStatusCode.OK, jsonHeaders)
                    request.url.encodedPath.contains("explore") ->
                        respond(exploreOkBody(), HttpStatusCode.OK, jsonHeaders)
                    request.url.encodedPath.contains("home") ->
                        respond(homeOkBody(), HttpStatusCode.OK, jsonHeaders)
                    else -> respond("{}", HttpStatusCode.NotFound)
                }
            }
            val api = testMobileApi(engine, platform)
            val orchestrator = buildOrchestrator(api, platform, localStore, trainingConfig)

            orchestrator.syncIfNeeded(forceCheck = true)

            val stats = metadataStore.readMessageStats()
            assertEquals("msg-1:tip", stats?.fingerprint)
            assertEquals(1, stats?.totalMessages)
        }
    }

    private class RecordingSystemMessageCache(store: MovitLocalStore) : SystemMessageCache(store) {
        val saveCalls = mutableListOf<List<SyncSystemMessageDto>>()
        var loadIntoRegistryCalls = 0

        override fun save(messages: List<SyncSystemMessageDto>) {
            saveCalls += messages
            super.save(messages)
        }

        override fun loadIntoRegistry() {
            loadIntoRegistryCalls++
            super.loadIntoRegistry()
        }
    }

    private class RecordingExercisePreferenceLocalStore(
        localStore: MovitLocalStore,
    ) : ExercisePreferenceLocalStore(localStore) {
        val hydrateCalls = mutableListOf<List<UserExercisePreferenceSyncDto>>()

        override suspend fun hydrateFromSync(
            rows: List<UserExercisePreferenceSyncDto>,
            pendingExerciseIds: Set<String>,
        ) {
            hydrateCalls += rows
            super.hydrateFromSync(rows, pendingExerciseIds)
        }
    }

    private class RecordingReportsSyncRepository(
        api: com.movit.core.network.MovitMobileApi,
        platform: () -> FakeMovitPlatformBindings,
        localStore: () -> MovitLocalStore,
    ) : ReportsSyncRepository(api, platform, localStore) {
        val hydrateCalls = mutableListOf<List<PlannedWorkoutReportExportDto>>()

        override fun hydrateFromSync(exports: List<PlannedWorkoutReportExportDto>) {
            hydrateCalls += exports
            super.hydrateFromSync(exports)
        }
    }

    private class RecordingDayCustomizationLocalStore(
        localStore: MovitLocalStore,
    ) : DayCustomizationLocalStore(localStore) {
        data class HydrateCall(
            val userProgramId: String,
            val customizationsPresent: Boolean,
        )

        val hydrateCalls = mutableListOf<HydrateCall>()

        override fun hydrateFromBackend(
            userProgramId: String,
            customizations: kotlinx.serialization.json.JsonElement?,
            serverCustomizationsUpdatedAt: String?,
        ) {
            hydrateCalls += HydrateCall(userProgramId, customizations != null)
            super.hydrateFromBackend(userProgramId, customizations, serverCustomizationsUpdatedAt)
        }
    }

    private fun buildOrchestrator(
        api: com.movit.core.network.MovitMobileApi,
        platform: FakeMovitPlatformBindings,
        localStore: MovitLocalStore,
        trainingConfig: TrainingConfigRepository,
        systemMessageCache: SystemMessageCache = SystemMessageCache(localStore),
        exercisePreferenceLocalStore: ExercisePreferenceLocalStore = ExercisePreferenceLocalStore(localStore),
        reportsSync: ReportsSyncRepository = ReportsSyncRepository(api, { platform }, { localStore }),
        dayCustomizationLocalStore: DayCustomizationLocalStore = DayCustomizationLocalStore(localStore),
        messageLibraryCache: MessageLibraryCache = MessageLibraryCache(localStore),
    ): MovitSyncOrchestrator {
        val home = HomeSyncRepository(api, { platform }, { localStore })
        val explore = ExploreSyncRepository(api, { platform }, { localStore })
        val plan = PlanSyncRepository(api, { platform }, home)
        val audioManifestCache = AudioManifestCache(localStore)
        return MovitSyncOrchestrator(
            api = api,
            platform = { platform },
            localStore = localStore,
            homeSync = home,
            exploreSync = explore,
            reportsSync = reportsSync,
            planSync = plan,
            metadataStore = MovitSyncMetadataStore(localStore),
            audioManifestCache = audioManifestCache,
            audioPrefetchRunner = AudioPrefetchRunner(audioManifestCache, FakeAudioFileDownloader()),
            offlineWrites = OfflineWriteQueue(localStore, api) { platform },
            trainingConfig = trainingConfig,
            systemMessageCache = systemMessageCache,
            exercisePreferenceLocalStore = exercisePreferenceLocalStore,
            dayCustomizationLocalStore = dayCustomizationLocalStore,
            messageLibraryCache = messageLibraryCache,
        )
    }

    private fun fullPayloadSyncBody(): String {
        val customizations = buildJsonObject {
            putJsonArray("day_1_1") {
                add(
                    buildJsonObject {
                        put("id", "pw-server")
                        putJsonArray("items") { }
                    },
                )
            }
        }
        return MovitJson.encodeToString(
            MobileSyncApiResponse.serializer(),
            MobileSyncApiResponse(
                success = true,
                timestamp = "2026-06-11T00:00:00Z",
                data = MobileSyncDataDto(
                    systemMessages = listOf(
                        SyncSystemMessageDto(code = "SYS_WELCOME", content = LocalizedNameDto(en = "Hi")),
                    ),
                    userExercisePreferences = listOf(
                        UserExercisePreferenceSyncDto(
                            exerciseId = "ex-1",
                            customReps = 10,
                        ),
                    ),
                    plannedWorkoutReports = listOf(
                        PlannedWorkoutReportExportDto(
                            plannedWorkoutId = "pw-1",
                            programId = "prog-1",
                        ),
                    ),
                    userPrograms = listOf(
                        UserProgramExportDto(
                            id = "up-1",
                            programId = "prog-1",
                            customizations = customizations,
                        ),
                    ),
                    messageLibrary = listOf(
                        SyncMessageTemplateDto(
                            id = "msg-1",
                            code = "tip",
                            content = LocalizedNameDto(en = "Keep going"),
                        ),
                    ),
                ),
                meta = SyncMetaDto(
                    isFullSync = true,
                    messageLibraryStats = MessageLibraryStatsDto(
                        totalMessages = 1,
                        totalWithAudio = 1,
                        totalAssignments = 2,
                        fingerprint = "msg-1:tip",
                    ),
                ),
            ),
        )
    }

    private fun messageLibraryOnlySyncBody(): String = MovitJson.encodeToString(
        MobileSyncApiResponse.serializer(),
        MobileSyncApiResponse(
            success = true,
            timestamp = "2026-06-11T00:00:00Z",
            data = MobileSyncDataDto(
                messageLibrary = listOf(
                    SyncMessageTemplateDto(
                        id = "msg-1",
                        code = "tip",
                        content = LocalizedNameDto(en = "Brace core"),
                    ),
                ),
            ),
            meta = SyncMetaDto(
                isFullSync = true,
                messageLibraryStats = MessageLibraryStatsDto(
                    totalMessages = 1,
                    totalWithAudio = 1,
                    totalAssignments = 0,
                    fingerprint = "msg-1:tip",
                ),
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
        ).forEach { relative ->
            val file = java.io.File(relative)
            if (file.isFile) return file.readText()
        }
        error("Missing fixture: $resourcePath")
    }

    private fun exploreOkBody(): String = MovitJson.encodeToString(
        ExploreApiResponse.serializer(),
        ExploreApiResponse(success = true, data = ExploreDataDto()),
    )

    private fun homeOkBody(): String = MovitJson.encodeToString(
        HomeApiResponse.serializer(),
        HomeApiResponse(success = true, data = HomeDataDto()),
    )
}
