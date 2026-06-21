package com.movit.core.data.cache

import com.movit.core.data.local.InMemoryMovitLocalStore
import com.movit.core.network.dto.AudioFileInfoDto
import com.movit.core.network.dto.AudioManifestDto
import kotlin.test.Test
import kotlin.test.assertEquals

class AudioManifestCacheTest {

    @Test
    fun mergePartial_accumulatesDistinctFiles() {
        val cache = AudioManifestCache(InMemoryMovitLocalStore())
        cache.replaceFull(
            "https://api.test",
            AudioManifestDto(
                files = listOf(AudioFileInfoDto(filename = "a.mp3", url = "/a", language = "en")),
            ),
        )
        cache.mergePartial(
            "https://api.test",
            AudioManifestDto(
                files = listOf(AudioFileInfoDto(filename = "b.mp3", url = "/b", language = "ar")),
            ),
        )

        val persisted = cache.read()
        assertEquals(2, persisted?.manifest?.files?.size)
    }
}
