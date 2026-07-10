package com.movit.core.data.sync

import com.movit.core.data.audio.AudioPrefetchRunner
import com.movit.core.data.audio.FakeAudioFileDownloader
import com.movit.core.data.cache.AudioManifestCache
import com.movit.core.data.cache.MessageLibraryCache
import com.movit.core.data.cache.MovitSyncMetadataStore
import com.movit.core.data.cache.SystemMessageCache
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
import com.movit.core.data.repository.testLocalStore
import com.movit.core.data.repository.testMobileApi
import com.movit.core.data.repository.testPlanSyncRepository
import com.movit.core.network.MovitJson
import com.movit.core.network.dto.ExploreApiResponse
import com.movit.core.network.dto.ExploreDataDto
import com.movit.core.network.dto.HomeApiResponse
import com.movit.core.network.dto.HomeDataDto
import com.movit.core.network.dto.MobileSyncApiResponse
import com.movit.core.network.dto.MobileSyncDataDto
import com.movit.core.network.dto.SyncMetaDto
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/** P2.3: general sync catalog apply must advance the explore delta watermark. */
class ExploreSyncWatermarkParityTest {

  private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

  @Test
  fun orchestratorSync_writesExploreLastSyncFromSyncTimestamp() {
    runBlocking {
      val platform = FakeMovitPlatformBindings()
      val localStore = testLocalStore(platform)
      val syncTimestamp = "2026-06-18T15:30:00Z"

      val engine = MockEngine { request ->
        when {
          request.url.encodedPath.contains("sync") ->
            respond(syncBody(syncTimestamp), HttpStatusCode.OK, jsonHeaders)
          request.url.encodedPath.contains("user-programs") ->
            respond("""{"success":true,"userPrograms":[]}""", HttpStatusCode.OK, jsonHeaders)
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

      assertEquals(
        syncTimestamp,
        localStore.readJsonCache(MovitCacheKeys.EXPLORE_STORE, MovitCacheKeys.EXPLORE_LAST_SYNC),
      )
      assertEquals(
        syncTimestamp,
        MovitSyncMetadataStore(localStore).readLastSyncTimestamp(),
      )
    }
  }

  private fun buildOrchestrator(
    api: com.movit.core.network.MovitMobileApi,
    platform: FakeMovitPlatformBindings,
    localStore: com.movit.core.data.local.MovitLocalStore,
  ): MovitSyncOrchestrator {
    val home = HomeSyncRepository(api, { platform }, { localStore })
    val explore = ExploreSyncRepository(api, { platform }, { localStore })
    val reports = ReportsSyncRepository(api, { platform }, { localStore })
    val plan = testPlanSyncRepository(api, platform, localStore, home)
    val audioManifestCache = AudioManifestCache(localStore)
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
      audioPrefetchRunner = AudioPrefetchRunner(audioManifestCache, FakeAudioFileDownloader()),
      offlineWrites = OfflineWriteQueue(localStore, api, { platform }),
      trainingConfig = trainingConfig,
      catalogOffline = catalogOffline,
      systemMessageCache = SystemMessageCache(localStore),
      exercisePreferenceLocalStore = ExercisePreferenceLocalStore(localStore),
      dayCustomizationLocalStore = DayCustomizationLocalStore(localStore),
      messageLibraryCache = MessageLibraryCache(localStore),
      userProgramEnrollmentLocalStore = UserProgramEnrollmentLocalStore(localStore),
    )
  }

  private fun syncBody(timestamp: String): String = MovitJson.encodeToString(
    MobileSyncApiResponse.serializer(),
    MobileSyncApiResponse(
      success = true,
      timestamp = timestamp,
      data = MobileSyncDataDto(),
      meta = SyncMetaDto(isFullSync = true),
    ),
  )

  private fun exploreOkBody(): String = MovitJson.encodeToString(
    ExploreApiResponse.serializer(),
    ExploreApiResponse(
      success = true,
      timestamp = "2026-06-11T00:00:00Z",
      data = ExploreDataDto(),
    ),
  )

  private fun homeOkBody(): String = MovitJson.encodeToString(
    HomeApiResponse.serializer(),
    HomeApiResponse(
      success = true,
      timestamp = "2026-06-11T00:00:00Z",
      data = HomeDataDto(),
    ),
  )
}
