package com.movit.core.data.audio

import com.movit.core.data.cache.AudioManifestCache
import com.movit.core.data.local.InMemoryMovitLocalStore
import com.movit.core.network.dto.AudioFileInfoDto
import com.movit.core.network.dto.AudioManifestDto
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
}
