package com.movit.core.data.sync

import com.movit.core.data.cache.AudioManifestCache
import com.movit.core.data.cache.MovitSyncMetadataStore
import com.movit.core.data.local.MovitLocalStore
import com.movit.core.data.repository.ExploreSyncRepository
import com.movit.core.data.repository.FakeMovitPlatformBindings
import com.movit.core.data.repository.HomeSyncRepository
import com.movit.core.data.repository.MovitCacheKeys
import com.movit.core.data.repository.PlanSyncRepository
import com.movit.core.data.repository.ReportsSyncRepository
import com.movit.core.data.repository.testLocalStore
import com.movit.core.data.repository.testMobileApi
import com.movit.core.network.MovitJson
import com.movit.core.network.dto.ExploreDataDto
import com.movit.core.network.dto.HomeDataDto
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class MovitSyncOrchestratorTest {

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

    private fun buildOrchestrator(
        api: com.movit.core.network.MovitMobileApi,
        platform: FakeMovitPlatformBindings,
        localStore: MovitLocalStore,
    ): MovitSyncOrchestrator {
        val home = HomeSyncRepository(api, { platform }, { localStore })
        val explore = ExploreSyncRepository(api, { platform }, { localStore })
        val reports = ReportsSyncRepository(api, { platform }, { localStore })
        val plan = PlanSyncRepository(api, { platform }, home)
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
        )
    }
}
