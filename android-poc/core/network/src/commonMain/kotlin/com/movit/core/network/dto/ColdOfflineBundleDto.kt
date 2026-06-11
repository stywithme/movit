package com.movit.core.network.dto

import kotlinx.serialization.Serializable

/** Bundled first-install offline seed shipped in composeResources. */
@Serializable
data class BundledColdOfflineDto(
    val home: HomeDataDto? = null,
    val explore: ExploreDataDto? = null,
    val systemMessages: List<SyncSystemMessageDto> = emptyList(),
)
