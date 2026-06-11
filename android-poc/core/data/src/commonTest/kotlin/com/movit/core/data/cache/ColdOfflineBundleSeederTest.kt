package com.movit.core.data.cache

import com.movit.core.data.repository.ExploreSyncRepository
import com.movit.core.data.repository.FakeMovitPlatformBindings
import com.movit.core.data.repository.HomeSyncRepository
import com.movit.core.data.repository.MovitCacheKeys
import com.movit.core.data.repository.testLocalStore
import com.movit.core.data.repository.testMobileApi
import com.movit.core.network.MovitJson
import com.movit.core.network.dto.BundledColdOfflineDto
import com.movit.core.network.dto.ExploreDataDto
import com.movit.core.network.dto.HomeDataDto
import com.movit.core.network.dto.LocalizedNameDto
import com.movit.core.network.dto.SyncSystemMessageDto
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
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
            val seeder = ColdOfflineBundleSeeder(
                localStore = localStore,
                homeSync = home,
                exploreSync = explore,
                systemMessageCache = systemMessages,
                bundleJsonProvider = { bundledJson },
            )

            val seeded = seeder.seedIfNeeded()

            assertTrue(seeded)
            assertNotNull(home.readCached())
            assertNotNull(explore.readCached())
            assertEquals(1, systemMessages.read().size)
            assertEquals("Go!", SystemMessageRegistry.get("training_countdown_go", "", "").en)
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
            val seeder = ColdOfflineBundleSeeder(
                localStore = localStore,
                homeSync = home,
                exploreSync = explore,
                systemMessageCache = SystemMessageCache(localStore),
                bundleJsonProvider = { bundledJson },
            )

            assertEquals(false, seeder.seedIfNeeded())
        }
    }
}
