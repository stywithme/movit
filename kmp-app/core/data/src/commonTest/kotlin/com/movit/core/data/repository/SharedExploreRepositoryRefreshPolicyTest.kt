package com.movit.core.data.repository

import com.movit.core.network.MovitJson
import com.movit.core.network.dto.ExploreApiResponse
import com.movit.core.network.dto.ExploreDataDto
import com.movit.shared.AppResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * P2.3: routine refresh paths use explore delta; full repair stays explicit.
 */
class SharedExploreRepositoryRefreshPolicyTest {

  private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

  @Test
  fun programFlowSyncExplore_usesDeltaAndPreservesWatermark() {
    runBlocking {
      val platform = FakeMovitPlatformBindings()
      val localStore = testLocalStore(platform)
      localStore.writeJsonCache(
        MovitCacheKeys.EXPLORE_STORE,
        MovitCacheKeys.EXPLORE_LAST_SYNC,
        "2026-06-10T12:00:00Z",
      )

      var requestedUpdatedAfter: String? = "not-called"
      val engine = MockEngine { request ->
        requestedUpdatedAfter = request.url.parameters["updatedAfter"]
        respond(
          content = MovitJson.encodeToString(
            ExploreApiResponse.serializer(),
            ExploreApiResponse(
              success = true,
              timestamp = "2026-06-11T00:00:00Z",
              data = ExploreDataDto(),
            ),
          ),
          status = HttpStatusCode.OK,
          headers = jsonHeaders,
        )
      }
      val api = testMobileApi(engine, platform)
      val exploreSync = ExploreSyncRepository(api, { platform }, { localStore })
      val repo = ProgramFlowSyncRepository(
        api = api,
        platform = { platform },
        localStore = { localStore },
        exploreSync = exploreSync,
        homeSync = HomeSyncRepository(api, { platform }, { localStore }),
        planSync = testPlanSyncRepository(api, platform, localStore),
      )

      val result = repo.syncExplore()

      assertTrue(result is AppResult.Success)
      assertEquals("2026-06-10T12:00:00Z", requestedUpdatedAfter)
      assertEquals(
        "2026-06-11T00:00:00Z",
        localStore.readJsonCache(MovitCacheKeys.EXPLORE_STORE, MovitCacheKeys.EXPLORE_LAST_SYNC),
      )
    }
  }

  @Test
  fun repairExploreCatalog_clearsWatermarkForFullFetch() {
    runBlocking {
      val platform = FakeMovitPlatformBindings()
      val localStore = testLocalStore(platform)
      localStore.writeJsonCache(
        MovitCacheKeys.EXPLORE_STORE,
        MovitCacheKeys.EXPLORE_LAST_SYNC,
        "2026-06-10T12:00:00Z",
      )

      var requestedUpdatedAfter: String? = "not-called"
      val engine = MockEngine { request ->
        requestedUpdatedAfter = request.url.parameters["updatedAfter"]
        respond(
          content = MovitJson.encodeToString(
            ExploreApiResponse.serializer(),
            ExploreApiResponse(
              success = true,
              timestamp = "2026-06-11T00:00:00Z",
              data = ExploreDataDto(),
            ),
          ),
          status = HttpStatusCode.OK,
          headers = jsonHeaders,
        )
      }
      val api = testMobileApi(engine, platform)
      val exploreSync = ExploreSyncRepository(api, { platform }, { localStore })
      val repo = ProgramFlowSyncRepository(
        api = api,
        platform = { platform },
        localStore = { localStore },
        exploreSync = exploreSync,
        homeSync = HomeSyncRepository(api, { platform }, { localStore }),
        planSync = testPlanSyncRepository(api, platform, localStore),
      )

      repo.repairExploreCatalog()

      assertNull(requestedUpdatedAfter)
    }
  }
}
