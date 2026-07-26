package com.movit.core.data.image

import com.movit.core.data.platform.MovitPlatformBindings

/**
 * Downloads the full catalog image set for offline use, training-critical assets first.
 *
 * Called after every successful sync (delta-cheap: already-cached files are skipped) and from the
 * post-bootstrap media warmup. Unlike the previous Coil-only warmup this persists files under app
 * storage and exposes [coverage] so the UI can report real offline readiness.
 */
class ImagePrefetchRunner(
    private val manifestBuilder: ImageAssetManifestBuilder,
    private val downloader: ImageFileDownloadPort,
    private val platform: () -> MovitPlatformBindings,
    /** Warms the in-app image loader so already-downloaded files decode instantly. */
    private val warmLoader: (List<String>) -> Unit = { urls -> MovitImageWarmup.warmup(urls) },
) {
    data class PrefetchReport(
        val requested: Int,
        val downloaded: Int,
        val coverage: ImageCacheCoverage,
    )

    fun buildManifest(): ImageAssetManifest = manifestBuilder.build()

    /** Scoped manifest for one program week / workout — used by the «حزمة الأسبوع» prefetch. */
    fun manifestFor(
        exerciseSlugs: Collection<String>,
        extraUrls: Collection<String> = emptyList(),
    ): ImageAssetManifest = manifestBuilder.buildForExercises(exerciseSlugs, extraUrls)

    fun coverage(manifest: ImageAssetManifest = buildManifest()): ImageCacheCoverage =
        ImageCacheCoverage(
            total = manifest.assets.size,
            cached = manifest.assets.count { downloader.hasImage(it.filename) },
        )

    /**
     * Local file for a catalog URL, or null when it is not downloaded yet.
     *
     * Called from image composition. Deliberately unmemoized: the result flips as soon as a
     * background prefetch lands, and a shared mutable cache read from both the UI thread and the
     * IO prefetch would need synchronization for a saving smaller than one image decode.
     */
    fun localPathFor(url: String): String? {
        if (url.isBlank()) return null
        return downloader.localPath(imageFilenameFor(url.trim()))
    }

    /**
     * @param removeOrphans full sync only — drops files no longer referenced by the catalog.
     */
    suspend fun prefetchCatalog(removeOrphans: Boolean = false): PrefetchReport {
        val manifest = buildManifest()
        return prefetch(manifest, removeOrphans = removeOrphans)
    }

    suspend fun prefetch(
        manifest: ImageAssetManifest,
        removeOrphans: Boolean = false,
    ): PrefetchReport {
        if (manifest.isEmpty) {
            return PrefetchReport(requested = 0, downloaded = 0, coverage = ImageCacheCoverage(0, 0))
        }

        if (removeOrphans) {
            downloader.cleanupOrphanedFiles(manifest.assets.map { it.filename }.toSet())
        }

        val bindings = platform()
        val pending = manifest.sortedByPriority().filterNot { downloader.hasImage(it.filename) }
        val downloaded = if (pending.isEmpty() || !bindings.isNetworkAvailable()) {
            0
        } else {
            downloader.downloadImages(pending, bindings.apiBaseUrl())
        }
        if (downloader.totalSizeBytes() > ImageFileDownloadPort.MAX_CACHE_BYTES) {
            downloader.enforceCacheLimit()
        }

        warmLoader(manifest.assets.mapNotNull { localPathFor(it.url) })

        return PrefetchReport(
            requested = pending.size,
            downloaded = downloaded,
            coverage = coverage(manifest),
        )
    }
}

/**
 * Shell/platform installs the loader warmup once; the default no-op keeps commonMain tests simple.
 * Mirrors `MovitDataImageWarmup` but takes **local file paths**, not remote URLs.
 */
object MovitImageWarmup {
    var warmup: (List<String>) -> Unit = {}
}
