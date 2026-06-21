package com.movit.core.data.audio

import com.movit.core.data.audio.FakeAudioFileDownloader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AudioClipResolverTest {
    @Test
    fun filenameFromUrl_extractsTrailingSegment() {
        assertEquals(
            "tts_en_42.wav",
            AudioClipResolver.filenameFromUrl("https://cdn.example/audio/tts/tts_en_42.wav"),
        )
        assertEquals("tts_ar_1.wav", AudioClipResolver.filenameFromUrl("/audio/tts_ar_1.wav"))
    }

    @Test
    fun filenameFromUrl_rejectsBlankOrExtensionless() {
        assertNull(AudioClipResolver.filenameFromUrl(null))
        assertNull(AudioClipResolver.filenameFromUrl(""))
        assertNull(AudioClipResolver.filenameFromUrl("/audio/noext"))
    }

    @Test
    fun resolveLocalPath_usesDownloaderCache() {
        val downloader = FakeAudioFileDownloader()
        downloader.seedFile("tts_en_1.wav", "/tmp/tts_en_1.wav")
        assertEquals(
            "/tmp/tts_en_1.wav",
            AudioClipResolver.resolveLocalPath(downloader, "/manifest/tts_en_1.wav"),
        )
        assertNull(AudioClipResolver.resolveLocalPath(downloader, "/manifest/missing.wav"))
    }
}
