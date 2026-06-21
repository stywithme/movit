package com.movit.core.network.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Bundled first-install offline seed shipped in composeResources. */
@Serializable
data class BundledColdOfflineDto(
    val home: HomeDataDto? = null,
    val explore: ExploreDataDto? = null,
    val exercises: List<JsonElement> = emptyList(),
    val messageLibrary: List<SyncMessageTemplateDto> = emptyList(),
    val systemMessages: List<SyncSystemMessageDto> = emptyList(),
)
