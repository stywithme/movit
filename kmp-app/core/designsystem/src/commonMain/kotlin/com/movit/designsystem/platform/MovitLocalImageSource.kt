package com.movit.designsystem.platform

/**
 * Bridges the durable offline image store (core:data) into image composables without a module
 * dependency — the shell installs [resolveLocalPath] on startup, exactly like image prefetch.
 *
 * When a catalog image has been downloaded for offline use, composables load the local file
 * instead of the remote URL, so gym sessions render with no network and no Coil cache hit.
 */
object MovitLocalImageSource {
    /** Remote URL → absolute local file path, or null when the file is not cached yet. */
    var resolveLocalPath: (String) -> String? = { null }

    /** Model passed to the image loader: local `file://` path when cached, else the remote URL. */
    fun modelFor(imageUrl: String): String {
        val local = runCatching { resolveLocalPath(imageUrl) }.getOrNull()
        return if (local.isNullOrBlank()) imageUrl else asFileUri(local)
    }

    internal fun asFileUri(path: String): String =
        if (path.startsWith("file://")) path else "file://$path"
}
