package com.movit.core.data.cache

import com.movit.core.data.repository.ExploreSyncRepository
import com.movit.core.data.repository.FakeMovitPlatformBindings
import com.movit.core.data.repository.HomeSyncRepository
import com.movit.core.data.repository.MovitCacheKeys
import com.movit.core.data.repository.TrainingConfigRepository
import com.movit.core.data.repository.testLocalStore
import com.movit.core.data.repository.testMobileApi
import com.movit.core.network.MovitJson
import com.movit.core.network.dto.BundledColdOfflineDto
import com.movit.core.network.dto.ExploreDataDto
import com.movit.core.network.dto.HomeDataDto
import com.movit.core.network.dto.LocalizedNameDto
import com.movit.core.network.dto.SyncMessageTemplateDto
import com.movit.core.network.dto.SyncSystemMessageDto
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ColdOfflineBundleSeederTest {

    private val bundledJson = MovitJson.encodeToString(
        BundledColdOfflineDto.serializer(),
        BundledColdOfflineDto(
            home = HomeDataDto(
                user = com.movit.core.network.dto.HomeUserDto(name = "Guest"),
            ),
            explore = ExploreDataDto(
                exercises = listOf(
                    com.movit.core.network.dto.ExploreExerciseDto(
                        id = "ex-seed",
                        slug = "squat",
                        name = LocalizedNameDto(en = "Squat", ar = "قرفصاء"),
                    ),
                ),
            ),
            exercises = listOf(seedExerciseConfig()),
            messageLibrary = listOf(
                SyncMessageTemplateDto(
                    id = "msg-1",
                    code = "knee_depth_tip",
                    category = "form",
                    content = LocalizedNameDto(ar = "انزل أكثر", en = "Go deeper"),
                ),
            ),
            systemMessages = listOf(
                SyncSystemMessageDto(
                    code = "training_countdown_go",
                    content = LocalizedNameDto(ar = "ابدأ!", en = "Go!"),
                ),
            ),
        ),
    )

    @Test
    fun seedIfNeeded_writesCachesWhenEmpty() {
        runBlocking {
            val platform = FakeMovitPlatformBindings()
            val localStore = testLocalStore(platform)
            val api = testMobileApi(MockEngine { respond("{}", HttpStatusCode.OK) }, platform)
            val home = HomeSyncRepository(api, { platform }, { localStore })
            val explore = ExploreSyncRepository(api, { platform }, { localStore })
            val systemMessages = SystemMessageCache(localStore)
            val trainingConfig = TrainingConfigRepository(localStore)
            val messageLibrary = MessageLibraryCache(localStore)
            val seeder = ColdOfflineBundleSeeder(
                localStore = localStore,
                homeSync = home,
                exploreSync = explore,
                systemMessageCache = systemMessages,
                trainingConfig = trainingConfig,
                messageLibraryCache = messageLibrary,
                bundleJsonProvider = { bundledJson },
            )

            val seeded = seeder.seedIfNeeded()

            assertTrue(seeded)
            assertNotNull(home.readCached())
            assertNotNull(explore.readCached())
            assertTrue(trainingConfig.supports("squat"))
            assertEquals(1, messageLibrary.read().size)
            assertEquals(1, systemMessages.read().size)
            assertEquals("Go!", SystemMessageRegistry.get("training_countdown_go", "", "").en)
        }
    }

    @Test
    fun seedIfNeeded_skipsHomeWhenBundledHomeIsNull() {
        runBlocking {
            val platform = FakeMovitPlatformBindings()
            val localStore = testLocalStore(platform)
            val api = testMobileApi(MockEngine { respond("{}", HttpStatusCode.OK) }, platform)
            val home = HomeSyncRepository(api, { platform }, { localStore })
            val explore = ExploreSyncRepository(api, { platform }, { localStore })
            val systemMessages = SystemMessageCache(localStore)
            val trainingConfig = TrainingConfigRepository(localStore)
            val messageLibrary = MessageLibraryCache(localStore)
            val bundledJson = MovitJson.encodeToString(
                BundledColdOfflineDto.serializer(),
                BundledColdOfflineDto(
                    home = null,
                    explore = ExploreDataDto(
                        programs = listOf(
                            com.movit.core.network.dto.ExploreProgramDto(
                                id = "real-program-id",
                                slug = "real-slug",
                                name = LocalizedNameDto(en = "Real Program"),
                            ),
                        ),
                    ),
                    systemMessages = listOf(
                        SyncSystemMessageDto(
                            code = "training_countdown_go",
                            content = LocalizedNameDto(ar = "ابدأ!", en = "Go!"),
                        ),
                    ),
                ),
            )
            val seeder = ColdOfflineBundleSeeder(
                localStore = localStore,
                homeSync = home,
                exploreSync = explore,
                systemMessageCache = systemMessages,
                trainingConfig = trainingConfig,
                messageLibraryCache = messageLibrary,
                bundleJsonProvider = { bundledJson },
            )

            val seeded = seeder.seedIfNeeded()

            assertTrue(seeded)
            assertEquals(null, home.readCached())
            assertNotNull(explore.readCached())
            assertEquals(1, explore.readCached()?.programs?.size)
            assertEquals(1, systemMessages.read().size)
        }
    }

    @Test
    fun seedIfNeeded_skipsWhenCachesAlreadyPopulated() {
        runBlocking {
            val platform = FakeMovitPlatformBindings()
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
            SystemMessageCache(localStore).save(
                listOf(SyncSystemMessageDto(code = "existing", content = LocalizedNameDto(en = "x"))),
            )

            val api = testMobileApi(MockEngine { respond("{}", HttpStatusCode.OK) }, platform)
            val home = HomeSyncRepository(api, { platform }, { localStore })
            val explore = ExploreSyncRepository(api, { platform }, { localStore })
            val trainingConfig = TrainingConfigRepository(localStore)
            trainingConfig.applySyncExercises(listOf(seedExerciseConfig()))
            val messageLibrary = MessageLibraryCache(localStore)
            messageLibrary.replaceFull(
                listOf(
                    SyncMessageTemplateDto(
                        id = "existing-msg",
                        code = "existing",
                        content = LocalizedNameDto(en = "x"),
                    ),
                ),
            )
            val seeder = ColdOfflineBundleSeeder(
                localStore = localStore,
                homeSync = home,
                exploreSync = explore,
                systemMessageCache = SystemMessageCache(localStore),
                trainingConfig = trainingConfig,
                messageLibraryCache = messageLibrary,
                bundleJsonProvider = { bundledJson },
            )

            assertEquals(false, seeder.seedIfNeeded())
        }
    }

    @Test
    fun bundledColdOfflineFile_containsTrainingCore() {
        val bundle = MovitJson.decodeFromString(
            BundledColdOfflineDto.serializer(),
            readBundledColdOfflineFile(),
        )
        val bundledSlugs = bundle.exercises.mapNotNull { element ->
            element.jsonObject["slug"]?.jsonPrimitive?.content
        }
        val trainingConfig = TrainingConfigRepository(testLocalStore(FakeMovitPlatformBindings()))
        trainingConfig.applySyncExercises(bundle.exercises, isFullSync = true)

        assertTrue(bundle.exercises.isNotEmpty())
        assertTrue(bundle.messageLibrary.isNotEmpty())
        assertTrue(bundle.systemMessages.isNotEmpty())
        assertTrue("bicebs-mo8j7gg1" in bundledSlugs)
        assertTrue(trainingConfig.supports("bicebs-mo8j7gg1"))
    }

    private fun seedExerciseConfig(): JsonObject =
        MovitJson.parseToJsonElement(
            """
            {
              "id": "ex-seed",
              "slug": "squat",
              "updatedAt": "2026-06-14T00:00:00.000Z",
              "name": {
                "ar": "القرفصاء",
                "en": "Squat"
              },
              "instructions": {
                "ar": "انزل ببطء ثم اصعد",
                "en": "Lower slowly, then stand up"
              },
              "poseVariants": [
                {
                  "name": {
                    "ar": "زاوية جانبية",
                    "en": "Side View"
                  },
                  "trackedJoints": [
                    {
                      "joint": "left_knee",
                      "role": "primary",
                      "startPose": {
                        "min": 120,
                        "max": 180
                      },
                      "upRange": {
                        "perfect": {
                          "min": 150,
                          "max": 180
                        }
                      },
                      "downRange": {
                        "perfect": {
                          "min": 60,
                          "max": 90
                        }
                      }
                    }
                  ]
                }
              ]
            }
            """.trimIndent(),
        ).jsonObject

    private fun readBundledColdOfflineFile(): String {
        val relativePaths = listOf(
            "core/resources/src/commonMain/composeResources/files/cold_offline_bundle.json",
            "android-poc/core/resources/src/commonMain/composeResources/files/cold_offline_bundle.json",
            "../resources/src/commonMain/composeResources/files/cold_offline_bundle.json",
            "../core/resources/src/commonMain/composeResources/files/cold_offline_bundle.json",
        )
        relativePaths.forEach { relative ->
            val file = java.io.File(relative)
            if (file.isFile) return file.readText()
        }
        error("Missing cold_offline_bundle.json")
    }
}
