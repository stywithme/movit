package com.movit.core.data.cache

import com.movit.core.data.local.MovitLocalStore
import com.movit.core.data.repository.MovitCacheKeys
import com.movit.core.network.MovitJson
import com.movit.core.network.dto.SyncMessageTemplateDto
import kotlinx.serialization.builtins.ListSerializer

/**
 * Persists sync [messageLibrary] templates for offline re-merge after reinstall or delta sync.
 */
class MessageLibraryCache(
    private val store: MovitLocalStore,
) {
    fun read(): List<SyncMessageTemplateDto> {
        val raw = store.readJsonCache(
            MovitCacheKeys.MESSAGE_LIBRARY_STORE,
            MovitCacheKeys.MESSAGE_LIBRARY_JSON,
        ) ?: return emptyList()
        return runCatching {
            MovitJson.decodeFromString(ListSerializer(SyncMessageTemplateDto.serializer()), raw)
        }.getOrElse { emptyList() }
    }

    fun save(templates: List<SyncMessageTemplateDto>) {
        if (templates.isEmpty()) return
        store.writeJsonCache(
            MovitCacheKeys.MESSAGE_LIBRARY_STORE,
            MovitCacheKeys.MESSAGE_LIBRARY_JSON,
            MovitJson.encodeToString(ListSerializer(SyncMessageTemplateDto.serializer()), templates),
        )
    }

    fun mergePartial(incoming: List<SyncMessageTemplateDto>) {
        if (incoming.isEmpty()) return
        val merged = read().associateBy { it.id }.toMutableMap()
        incoming.forEach { merged[it.id] = it }
        save(merged.values.toList())
    }

    fun replaceFull(templates: List<SyncMessageTemplateDto>) {
        if (templates.isEmpty()) {
            clear()
            return
        }
        save(templates)
    }

    fun clear() {
        store.removeJsonCache(MovitCacheKeys.MESSAGE_LIBRARY_STORE, MovitCacheKeys.MESSAGE_LIBRARY_JSON)
    }
}
