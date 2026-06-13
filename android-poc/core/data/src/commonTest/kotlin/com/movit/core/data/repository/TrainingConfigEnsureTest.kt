package com.movit.core.data.repository

import com.movit.core.data.local.InMemoryMovitLocalStore
import com.movit.core.training.config.ExerciseConfigParser
import com.movit.core.training.config.ExerciseConfigRecord
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TrainingConfigEnsureTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun ensure_returnsAvailable_whenSlugAlreadyCached() {
        runBlocking {
            val store = InMemoryMovitLocalStore()
            val repo = TrainingConfigRepository(store)
            seedSquat(repo)

            val platform = FakeMovitPlatformBindings()
            val result = repo.ensure(
                slug = "bodyweight-squat",
                sync = syncMustNotRunOrchestrator(store, platform),
                api = testMobileApi(MockEngine { respond("{}", HttpStatusCode.NotFound) }),
                platform = platform,
            )

            assertIs<TrainingConfigEnsureResult.Available>(result)
        }
    }

    @Test
    fun ensure_returnsOffline_whenUnknownSlugMissingAndNoNetwork() {
        runBlocking {
            val store = InMemoryMovitLocalStore()
            val repo = TrainingConfigRepository(store)
            seedSquat(repo)
            val platform = object : FakeMovitPlatformBindings() {
                override fun isNetworkAvailable(): Boolean = false
            }
            val result = repo.ensure(
                slug = "walking-lunge",
                sync = syncMustNotRunOrchestrator(store, platform),
                api = testMobileApi(MockEngine { respond("{}", HttpStatusCode.NotFound) }),
                platform = platform,
            )

            assertEquals(
                TrainingConfigEnsureResult.Unavailable.Reason.Offline,
                (result as TrainingConfigEnsureResult.Unavailable).reason,
            )
        }
    }

    @Test
    fun ensure_appliesWorkoutTemplateConfig_whenSyncMisses() {
        runBlocking {
            val store = InMemoryMovitLocalStore()
            val repo = TrainingConfigRepository(store)
            val squatJson = readSquatFixture().withTrainingSlug("bodyweight-squat")
            val engine = MockEngine { request ->
                when {
                    request.url.encodedPath.contains("sync") ->
                        respond(syncEmptyBody(), HttpStatusCode.OK, jsonHeaders)
                    request.url.encodedPath.contains("explore") ->
                        respond(exploreOkBody(), HttpStatusCode.OK, jsonHeaders)
                    request.url.encodedPath.contains("home") ->
                        respond(homeOkBody(), HttpStatusCode.OK, jsonHeaders)
                    request.url.encodedPath.contains("training-config") -> respond(
                        """
                        {
                          "success": true,
                          "data": {
                            "workoutTemplateId": "wt-1",
                            "exercises": [$squatJson]
                          }
                        }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                    else -> respond("{}", HttpStatusCode.NotFound)
                }
            }
            val platform = FakeMovitPlatformBindings()
            val api = testMobileApi(engine, platform)
            val orchestrator = buildTestOrchestrator(api, platform, store, repo)

            val result = repo.ensure(
                slug = "bodyweight-squat",
                workoutTemplateId = "wt-1",
                sync = orchestrator,
                api = api,
                platform = platform,
            )

            assertIs<TrainingConfigEnsureResult.Available>(result)
            assertTrue(repo.supports("bodyweight-squat"))
        }
    }

    @Test
    fun extractTrainingConfigExercises_readsExercisesArray() {
        val response = com.movit.core.network.dto.TrainingConfigApiResponse(
            success = true,
            data = buildJsonObject {
                put("workoutTemplateId", JsonPrimitive("wt-1"))
                put(
                    "exercises",
                    buildJsonArray {
                        add(buildJsonObject { put("slug", JsonPrimitive("bodyweight-squat")) })
                    },
                )
            },
        )

        val exercises = extractTrainingConfigExercises(response)

        assertEquals(1, exercises?.size)
    }

    private fun seedSquat(repo: TrainingConfigRepository) {
        val json = readSquatFixture()
        val config = ExerciseConfigParser.parseConfigJson(json)
        repo.seedRecord(
            ExerciseConfigRecord.fromConfig(
                id = "ex-squat",
                slug = "bodyweight-squat",
                updatedAt = "2026-06-11",
                config = config,
            ),
        )
    }

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

    private fun String.withTrainingSlug(slug: String): String =
        trim().removePrefix("{").removeSuffix("}").let { body ->
            """{"slug":"$slug",$body}"""
        }

    private fun syncMustNotRunOrchestrator(
        localStore: com.movit.core.data.local.MovitLocalStore,
        platform: FakeMovitPlatformBindings,
    ): com.movit.core.data.sync.MovitSyncOrchestrator {
        val engine = MockEngine { error("sync should not run") }
        return buildTestOrchestrator(
            api = testMobileApi(engine, platform),
            platform = platform,
            localStore = localStore,
            trainingConfig = TrainingConfigRepository(localStore),
        )
    }

    private fun buildTestOrchestrator(
        api: com.movit.core.network.MovitMobileApi,
        platform: FakeMovitPlatformBindings,
        localStore: com.movit.core.data.local.MovitLocalStore,
        trainingConfig: TrainingConfigRepository,
    ): com.movit.core.data.sync.MovitSyncOrchestrator {
        val home = HomeSyncRepository(api, { platform }, { localStore })
        val explore = ExploreSyncRepository(api, { platform }, { localStore })
        val reports = ReportsSyncRepository(api, { platform }, { localStore })
        val plan = PlanSyncRepository(api, { platform }, home)
        val audioManifestCache = com.movit.core.data.cache.AudioManifestCache(localStore)
        val offlineWrites = com.movit.core.data.outbox.OfflineWriteQueue(localStore, api) { platform }
        return com.movit.core.data.sync.MovitSyncOrchestrator(
            api = api,
            platform = { platform },
            localStore = localStore,
            homeSync = home,
            exploreSync = explore,
            reportsSync = reports,
            planSync = plan,
            metadataStore = com.movit.core.data.cache.MovitSyncMetadataStore(localStore),
            audioManifestCache = audioManifestCache,
            audioPrefetchRunner = com.movit.core.data.audio.AudioPrefetchRunner(
                audioManifestCache,
                com.movit.core.data.audio.FakeAudioFileDownloader(),
            ),
            offlineWrites = offlineWrites,
            trainingConfig = trainingConfig,
            systemMessageCache = com.movit.core.data.cache.SystemMessageCache(localStore),
            exercisePreferenceLocalStore = com.movit.core.data.repository.ExercisePreferenceLocalStore(localStore),
            dayCustomizationLocalStore = com.movit.core.data.repository.DayCustomizationLocalStore(localStore),
            messageLibraryCache = com.movit.core.data.cache.MessageLibraryCache(localStore),
        )
    }

    private fun syncEmptyBody(): String =
        com.movit.core.network.MovitJson.encodeToString(
            com.movit.core.network.dto.MobileSyncApiResponse.serializer(),
            com.movit.core.network.dto.MobileSyncApiResponse(
                success = true,
                timestamp = "2026-06-11T00:00:00Z",
                data = com.movit.core.network.dto.MobileSyncDataDto(),
                meta = com.movit.core.network.dto.SyncMetaDto(isFullSync = true),
            ),
        )

    private fun exploreOkBody(): String =
        com.movit.core.network.MovitJson.encodeToString(
            com.movit.core.network.dto.ExploreApiResponse.serializer(),
            com.movit.core.network.dto.ExploreApiResponse(
                success = true,
                data = com.movit.core.network.dto.ExploreDataDto(),
            ),
        )

    private fun homeOkBody(): String =
        com.movit.core.network.MovitJson.encodeToString(
            com.movit.core.network.dto.HomeApiResponse.serializer(),
            com.movit.core.network.dto.HomeApiResponse(
                success = true,
                data = com.movit.core.network.dto.HomeDataDto(),
            ),
        )
}
