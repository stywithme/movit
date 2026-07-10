package com.movit.core.data.cache

import com.movit.core.data.repository.FakeMovitPlatformBindings
import com.movit.core.data.repository.testLocalStore
import com.movit.core.network.dto.LocalizedNameDto
import com.movit.core.network.dto.SyncSystemMessageDto
import kotlin.test.Test
import kotlin.test.assertEquals

class SystemMessageCacheTest {

    @Test
    fun saveAndLoadIntoRegistry_roundTrips() {
        val store = testLocalStore(FakeMovitPlatformBindings())
        val cache = SystemMessageCache(store)
        val messages = listOf(
            SyncSystemMessageDto(
                code = "training_go_overlay",
                content = LocalizedNameDto(ar = "انطلق!", en = "GO!"),
            ),
        )

        cache.save(messages)
        SystemMessageRegistry.replaceAll(emptyList())
        cache.loadIntoRegistry()

        assertEquals("GO!", SystemMessageRegistry.get("training_go_overlay", "", "").en)
        assertEquals(1, cache.read().size)
    }

    @Test
    fun saveEmpty_clearsPersistedMessages() {
        val store = testLocalStore(FakeMovitPlatformBindings())
        val cache = SystemMessageCache(store)
        cache.save(
            listOf(
                SyncSystemMessageDto(
                    code = "training_go_overlay",
                    content = LocalizedNameDto(ar = "انطلق!", en = "GO!"),
                ),
            ),
        )
        assertEquals(1, cache.read().size)

        cache.save(emptyList())

        assertEquals(0, cache.read().size)
        assertEquals("", SystemMessageRegistry.get("training_go_overlay", "", "").en)
    }
}
