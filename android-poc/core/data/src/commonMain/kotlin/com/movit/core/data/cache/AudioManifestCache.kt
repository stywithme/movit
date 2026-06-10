package com.movit.core.data.cache

import com.movit.core.data.local.MovitLocalStore
import com.movit.core.data.repository.MovitCacheKeys
import com.movit.core.network.MovitJson
import com.movit.core.network.dto.AudioManifestDto

/**
 * Persists audio manifest metadata in commonMain (file downloads remain platform-specific).
 */
class AudioManifestCache(
    private val store: MovitLocalStore,
) {
    data class PersistedManifest(
        val baseUrl: String,
        val manifest: AudioManifestDto,
    )

    fun read(): PersistedManifest? {
        val baseUrl = store.readJsonCache(MovitCacheKeys.AUDIO_STORE, MovitCacheKeys.AUDIO_BASE_URL)
            ?: return null
        val raw = store.readJsonCache(MovitCacheKeys.AUDIO_STORE, MovitCacheKeys.AUDIO_MANIFEST_JSON)
            ?: return null
        val manifest = runCatching {
            MovitJson.decodeFromString(AudioManifestDto.serializer(), raw)
        }.getOrNull() ?: return null
        if (manifest.files.isEmpty()) return null
        return PersistedManifest(baseUrl = baseUrl, manifest = manifest)
    }

    fun replaceFull(effectiveBaseUrl: String, manifest: AudioManifestDto) {
        val base = effectiveBaseUrl.trimEnd('/')
        val normalized = manifest.copy(
            baseUrl = manifest.baseUrl.ifBlank { base },
            files = manifest.files,
        )
        persist(base, normalized)
    }

    fun mergePartial(effectiveBaseUrl: String, manifest: AudioManifestDto) {
        val base = effectiveBaseUrl.trimEnd('/')
        val existing = read()?.manifest?.files.orEmpty()
        val mergedFiles = (existing + manifest.files)
            .distinctBy { it.filename }
        val merged = AudioManifestDto(baseUrl = base, files = mergedFiles)
        persist(base, merged)
    }

    fun pendingFilenames(): List<String> {
        val state = read() ?: return emptyList()
        return state.manifest.files.map { it.filename }
    }

    fun clear() {
        store.removeJsonCache(MovitCacheKeys.AUDIO_STORE, MovitCacheKeys.AUDIO_BASE_URL)
        store.removeJsonCache(MovitCacheKeys.AUDIO_STORE, MovitCacheKeys.AUDIO_MANIFEST_JSON)
    }

    private fun persist(baseUrl: String, manifest: AudioManifestDto) {
        store.writeJsonCache(MovitCacheKeys.AUDIO_STORE, MovitCacheKeys.AUDIO_BASE_URL, baseUrl)
        store.writeJsonCache(
            MovitCacheKeys.AUDIO_STORE,
            MovitCacheKeys.AUDIO_MANIFEST_JSON,
            MovitJson.encodeToString(AudioManifestDto.serializer(), manifest),
        )
    }

    companion object {
        fun resolveEffectiveAudioBase(apiBaseUrl: String, manifest: AudioManifestDto): String {
            val raw = manifest.baseUrl.trim()
            val api = apiBaseUrl.trimEnd('/')
            if (raw.isEmpty()) return api
            if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
                return raw.trimEnd('/')
            }
            val path = raw.trim().trimEnd('/')
            return if (path.startsWith("/")) api + path else "$api/$path"
        }
    }
}
