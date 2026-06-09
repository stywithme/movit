package com.movit.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class LocalizedNameDto(
    val en: String = "",
    val ar: String = "",
) {
    fun display(language: String): String = when (language.lowercase()) {
        "ar" -> ar.ifBlank { en }
        else -> en.ifBlank { ar }
    }
}
