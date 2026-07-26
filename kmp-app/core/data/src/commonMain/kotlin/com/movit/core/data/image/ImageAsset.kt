package com.movit.core.data.image

/**
 * Where an image is used. Drives prefetch priority: training-critical assets
 * (pose position frames, exercise thumbnails) download before catalog covers.
 */
enum class ImageAssetKind(val priority: Int) {
    /** Pose position reference frame shown during a set — must be offline. */
    PosePosition(0),

    /** Exercise thumbnail in library / session cards. */
    ExerciseThumbnail(1),

    /** Workout template cover. */
    WorkoutCover(2),

    /** Program cover. */
    ProgramCover(3),
}

data class ImageAsset(
    val url: String,
    val kind: ImageAssetKind,
) {
    /** Stable on-disk name derived from the URL — safe for every filesystem. */
    val filename: String get() = imageFilenameFor(url)
}

/**
 * Full set of catalog images the app needs to render every exercise, workout and program
 * without network. Built locally from synced JSON — no backend manifest endpoint required.
 */
data class ImageAssetManifest(
    val assets: List<ImageAsset> = emptyList(),
) {
    val urls: List<String> get() = assets.map { it.url }

    val isEmpty: Boolean get() = assets.isEmpty()

    /** Training-critical first, so an interrupted prefetch still leaves a usable gym session. */
    fun sortedByPriority(): List<ImageAsset> = assets.sortedBy { it.kind.priority }

    fun ofKind(kind: ImageAssetKind): List<ImageAsset> = assets.filter { it.kind == kind }
}

/** Coverage of [ImageAssetManifest] on disk — surfaced in offline-readiness diagnostics. */
data class ImageCacheCoverage(
    val total: Int,
    val cached: Int,
) {
    val missing: Int get() = (total - cached).coerceAtLeast(0)
    val isComplete: Boolean get() = total == 0 || cached >= total
    val percent: Int get() = if (total == 0) 100 else (cached * 100) / total
}
