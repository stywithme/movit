package com.movit.core.data.cache

import com.movit.core.data.local.MovitLocalStore
import com.movit.core.data.repository.MovitCacheKeys
import com.movit.core.network.MovitJson
import com.movit.core.network.dto.SyncSystemMessageDto
import kotlinx.serialization.builtins.ListSerializer

/**
 * Persists system messages from sync or the cold-start bundle so they are available offline.
 */
class SystemMessageCache(
    private val store: MovitLocalStore,
) {
    fun read(): List<SyncSystemMessageDto> {
        val raw = store.readJsonCache(
            MovitCacheKeys.SYSTEM_MESSAGE_STORE,
            MovitCacheKeys.SYSTEM_MESSAGES_JSON,
        ) ?: return emptyList()
        return runCatching {
            MovitJson.decodeFromString(ListSerializer(SyncSystemMessageDto.serializer()), raw)
        }.getOrElse { emptyList() }
    }

    fun save(messages: List<SyncSystemMessageDto>) {
        if (messages.isEmpty()) return
        store.writeJsonCache(
            MovitCacheKeys.SYSTEM_MESSAGE_STORE,
            MovitCacheKeys.SYSTEM_MESSAGES_JSON,
            MovitJson.encodeToString(ListSerializer(SyncSystemMessageDto.serializer()), messages),
        )
        SystemMessageRegistry.replaceAll(messages)
    }

    /** Load persisted messages into [SystemMessageRegistry] (e.g. on app start). */
    fun loadIntoRegistry() {
        val messages = read()
        if (messages.isNotEmpty()) {
            SystemMessageRegistry.replaceAll(messages)
        }
    }
}
