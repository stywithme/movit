package com.movit.core.data.image

/**
 * Absolute URL for an image reference that may be relative to the API host
 * (backend stores `/uploads/...` for locally hosted media).
 */
fun resolveImageDownloadUrl(baseUrl: String, url: String): String {
    val trimmed = url.trim()
    if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
        return trimmed
    }
    val base = baseUrl.trimEnd('/')
    return if (trimmed.startsWith("/")) base + trimmed else "$base/$trimmed"
}

/**
 * Stable, collision-resistant filename for a URL.
 *
 * The readable tail keeps cache dirs debuggable; the FNV-1a hash prefix guarantees uniqueness
 * across hosts and query strings (two `image.png` from different folders must not collide).
 */
fun imageFilenameFor(url: String): String {
    val normalized = url.trim()
    if (normalized.isEmpty()) return ""
    val hash = fnv1a64Hex(normalized)
    val extension = imageExtensionOf(normalized)
    val readable = readableTail(normalized)
    return if (readable.isEmpty()) "$hash$extension" else "${hash}_$readable$extension"
}

/** Cached image files carry the `<hash>[_<tail>].<ext>` shape; `.part` marks in-flight downloads. */
fun isCachedImageFilename(name: String): Boolean =
    !name.endsWith(".part") && name.contains('.') && name.length > 1

private fun imageExtensionOf(url: String): String {
    val withoutQuery = url.substringBefore('?').substringBefore('#')
    val lastSegment = withoutQuery.substringAfterLast('/')
    val dot = lastSegment.lastIndexOf('.')
    if (dot <= 0 || dot == lastSegment.lastIndex) return DEFAULT_IMAGE_EXTENSION
    val extension = lastSegment.substring(dot + 1).lowercase()
    return if (extension in SUPPORTED_IMAGE_EXTENSIONS) ".$extension" else DEFAULT_IMAGE_EXTENSION
}

private fun readableTail(url: String): String {
    val withoutQuery = url.substringBefore('?').substringBefore('#')
    val lastSegment = withoutQuery.substringAfterLast('/').substringBeforeLast('.')
    return lastSegment
        .lowercase()
        .map { char -> if (char.isLetterOrDigit()) char else '-' }
        .joinToString("")
        .trim('-')
        .take(32)
}

private fun fnv1a64Hex(value: String): String {
    var hash = FNV_OFFSET_BASIS
    for (byte in value.encodeToByteArray()) {
        hash = hash xor (byte.toLong() and 0xFF)
        hash *= FNV_PRIME
    }
    val unsigned = hash.toULong()
    return unsigned.toString(16).padStart(16, '0')
}

private const val FNV_OFFSET_BASIS = -3750763034362895579L // 0xcbf29ce484222325
private const val FNV_PRIME = 1099511628211L
private const val DEFAULT_IMAGE_EXTENSION = ".img"
private val SUPPORTED_IMAGE_EXTENSIONS =
    setOf("png", "jpg", "jpeg", "webp", "gif", "heic", "avif", "bmp", "svg")
