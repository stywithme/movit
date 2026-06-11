package com.movit.core.data.cache

import com.movit.core.network.dto.LocalizedNameDto
import com.movit.core.network.dto.SyncSystemMessageDto

/**
 * In-memory registry of system messages synced from the backend or cold-start bundle.
 * Falls back to caller defaults when a key is missing or text is blank.
 */
object SystemMessageRegistry {
    private val byCode = mutableMapOf<String, LocalizedNameDto>()

    fun replaceAll(templates: List<SyncSystemMessageDto>) {
        byCode.clear()
        for (template in templates) {
            byCode[template.code] = template.content
        }
    }

    fun snapshot(): List<SyncSystemMessageDto> =
        byCode.map { (code, content) ->
            SyncSystemMessageDto(code = code, content = content)
        }

    fun getAllSyncedContents(): Collection<LocalizedNameDto> = byCode.values

    fun get(key: String, defaultAr: String, defaultEn: String): LocalizedNameDto {
        val remote = byCode[key] ?: return LocalizedNameDto(ar = defaultAr, en = defaultEn)
        return LocalizedNameDto(
            ar = remote.ar.ifBlank { defaultAr },
            en = remote.en.ifBlank { defaultEn },
        )
    }

    fun substitute(content: LocalizedNameDto, vars: Map<String, String>): LocalizedNameDto {
        fun rep(value: String): String {
            var out = value
            for ((name, replacement) in vars) {
                out = out.replace("{$name}", replacement)
            }
            return out
        }
        return LocalizedNameDto(ar = rep(content.ar), en = rep(content.en))
    }
}
