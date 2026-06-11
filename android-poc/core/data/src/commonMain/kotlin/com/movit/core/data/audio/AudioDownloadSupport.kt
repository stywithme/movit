package com.movit.core.data.audio

import com.movit.core.network.dto.AudioFileInfoDto

internal fun resolveAudioDownloadUrl(baseUrl: String, file: AudioFileInfoDto): String {
    val url = file.url
    if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
        return url
    }
    val base = baseUrl.trimEnd('/')
    return if (url.startsWith("/")) base + url else "$base/$url"
}

internal fun resolveLanguageSubdir(filename: String, language: String): String =
    when {
        language.isNotBlank() -> language
        filename.contains("_ar_") -> "ar"
        else -> "en"
    }

internal fun isCachedAudioFilename(name: String): Boolean =
    !name.endsWith(".part") && name.contains('.')
