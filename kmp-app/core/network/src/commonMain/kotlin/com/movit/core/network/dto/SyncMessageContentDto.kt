package com.movit.core.network.dto

import kotlinx.serialization.Serializable

/** Message template body from sync; includes optional TTS audio URLs (not on [LocalizedNameDto]). */
@Serializable
data class SyncMessageContentDto(
    val en: String = "",
    val ar: String = "",
    val audioEn: String? = null,
    val audioAr: String? = null,
)
