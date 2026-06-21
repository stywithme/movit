package com.movit.core.data

import com.movit.core.data.local.InMemoryMovitLocalStore
import com.movit.core.data.local.MovitLocalStoreFactory
import com.movit.core.data.outbox.OutboxEntry
import com.movit.core.data.outbox.OutboxOperationType
import com.movit.core.data.outbox.OutboxStatus
import com.movit.core.data.repository.FakeMovitPlatformBindings
import com.movit.core.data.repository.MovitCacheKeys
import com.movit.core.network.MovitClock
import com.movit.core.network.MovitJson
import com.movit.core.network.dto.AudioFileInfoDto
import com.movit.core.network.dto.AudioManifestDto
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MovitDataClearAllUserDataTest {

    private val localStore = InMemoryMovitLocalStore()

    @AfterTest
    fun tearDown() {
        MovitData.onSessionExpired = null
    }

    @Test
    fun clearAllUserData_wipesSqlDelightOutboxAudioAndMetadata() = runBlocking {
        var legacyCachesCleared = false
        val platform = object : FakeMovitPlatformBindings() {
            override fun clearLegacyUserCaches() {
                legacyCachesCleared = true
            }
        }
        MovitData.install(
            platform = platform,
            localStoreFactory = MovitLocalStoreFactory { localStore },
        )

        localStore.writeJsonCache(MovitCacheKeys.HOME_STORE, MovitCacheKeys.HOME_DATA, """{"user":"a"}""")
        localStore.writeSyncMetadata(
            scope = MovitCacheKeys.SYNC_STORE,
            version = "v1",
            lastSyncAt = "2026-01-01T00:00:00Z",
        )
        localStore.insertOutbox(
            OutboxEntry(
                id = "logout-test-op",
                type = OutboxOperationType.SAVE_DAY_CUSTOMIZATIONS,
                payload = """{"userProgramId":"up-1"}""",
                createdAt = MovitClock.nowEpochMs(),
                attempts = 0,
                status = OutboxStatus.PENDING,
            ),
        )
        val manifestJson = MovitJson.encodeToString(
            AudioManifestDto.serializer(),
            AudioManifestDto(
                baseUrl = "https://cdn.test/audio",
                files = listOf(AudioFileInfoDto(filename = "cue.mp3", size = 1L)),
            ),
        )
        localStore.writeJsonCache(MovitCacheKeys.AUDIO_STORE, MovitCacheKeys.AUDIO_BASE_URL, "https://cdn.test/audio")
        localStore.writeJsonCache(MovitCacheKeys.AUDIO_STORE, MovitCacheKeys.AUDIO_MANIFEST_JSON, manifestJson)

        MovitData.clearAllUserData()

        assertNull(localStore.readJsonCache(MovitCacheKeys.HOME_STORE, MovitCacheKeys.HOME_DATA))
        assertNull(localStore.readSyncMetadata(MovitCacheKeys.SYNC_STORE))
        assertEquals(0L, localStore.countOutboxByStatus(OutboxStatus.PENDING))
        assertNull(localStore.readJsonCache(MovitCacheKeys.AUDIO_STORE, MovitCacheKeys.AUDIO_BASE_URL))
        assertNull(localStore.readJsonCache(MovitCacheKeys.AUDIO_STORE, MovitCacheKeys.AUDIO_MANIFEST_JSON))
        assertTrue(legacyCachesCleared)
    }
}
