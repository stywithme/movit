package com.movit.core.data.image

class FakeImageFileDownloader(
    var networkFails: Boolean = false,
) : ImageFileDownloadPort {
    val stored = mutableMapOf<String, Long>()
    val downloadCalls = mutableListOf<List<ImageAsset>>()
    var cleanupCalls = 0
    var enforceCalls = 0
    var lastBaseUrl: String? = null

    fun seed(url: String, sizeBytes: Long = 1_024L) {
        stored[imageFilenameFor(url)] = sizeBytes
    }

    override fun hasImage(filename: String): Boolean = stored.containsKey(filename)

    override fun localPath(filename: String): String? =
        if (stored.containsKey(filename)) "/fake/images/$filename" else null

    override suspend fun downloadImages(assets: List<ImageAsset>, baseUrl: String): Int {
        downloadCalls += assets
        lastBaseUrl = baseUrl
        if (networkFails) return 0
        var count = 0
        assets.forEach { asset ->
            if (asset.filename.isNotBlank() && !stored.containsKey(asset.filename)) {
                stored[asset.filename] = 1_024L
                count++
            }
        }
        return count
    }

    override fun cleanupOrphanedFiles(validFilenames: Set<String>): Int {
        cleanupCalls++
        val orphans = stored.keys.filterNot { it in validFilenames }
        orphans.forEach { stored.remove(it) }
        return orphans.size
    }

    override fun enforceCacheLimit(maxBytes: Long) {
        enforceCalls++
    }

    override fun totalSizeBytes(): Long = stored.values.sum()
}
