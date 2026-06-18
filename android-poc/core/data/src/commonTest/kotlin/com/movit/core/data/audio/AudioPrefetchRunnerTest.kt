package com.movit.core.data.audio

import com.movit.core.data.cache.AudioManifestCache
import com.movit.core.data.local.InMemoryMovitLocalStore
import com.movit.core.data.repository.ExploreSyncRepository
import com.movit.core.data.repository.FakeMovitPlatformBindings
import com.movit.core.data.repository.testMobileApi
import com.movit.core.network.dto.AudioFileInfoDto
import com.movit.core.network.dto.AudioManifestDto
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioPrefetchRunnerTest {

    @Test
    fun afterManifestApplied_fullSync_cleansOrphansAndDownloadsPending() {
        runBlocking {
            val cache = AudioManifestCache(InMemoryMovitLocalStore())
            val downloader = FakeAudioFileDownloader()
            val runner = AudioPrefetchRunner(cache, downloader)

            cache.replaceFull(
                effectiveBaseUrl = "https://cdn.test",
                manifest = AudioManifestDto(
                    files = listOf(
                        AudioFileInfoDto(filename = "tts_en_1.wav", url = "/a.wav", language = "en"),
                    ),
                ),
            )

            runner.afterManifestApplied(isFullSync = true)

            assertEquals(1, downloader.orphanCleanupCalls)
            assertEquals(1, downloader.downloadedBatches.size)
            assertEquals(1, downloader.enforceLimitCalls)
            assertTrue(downloader.downloadedBatches.single().second.isNotEmpty())
        }
    }

    @Test
    fun prefetchForTargets_fetchesEntityManifestsThenDownloads() {
        runBlocking {
            val store = InMemoryMovitLocalStore()
            val cache = AudioManifestCache(store)
            val downloader = FakeAudioFileDownloader()
            val client = FakeEntityAudioManifestClient()
            client.exerciseResponse = FakeEntityAudioManifestClient.manifestResponse(
                slug = "squat",
                entityType = "exercise",
                filename = "entity_cue.wav",
            )
            val platform = FakeMovitPlatformBindings()
            val explore = ExploreSyncRepository(
                api = testMobileApi(MockEngine { respond("{}", HttpStatusCode.OK) }, platform),
                platform = { platform },
                localStore = { store },
            )
            val entityFetcher = EntityAudioManifestFetcher(
                client = client,
                manifestCache = cache,
                platform = { platform },
                exploreSync = explore,
            )
            val runner = AudioPrefetchRunner(cache, downloader, entityFetcher)

            runner.prefetchForTargets(
                EntityAudioManifestFetcher.Targets(exerciseSlugs = listOf("squat")),
            )

            assertEquals(listOf("squat"), client.exerciseCalls)
            assertEquals(1, downloader.downloadedBatches.size)
            assertEquals("entity_cue.wav", downloader.downloadedBatches.single().second.single().filename)
        }
    }
}
