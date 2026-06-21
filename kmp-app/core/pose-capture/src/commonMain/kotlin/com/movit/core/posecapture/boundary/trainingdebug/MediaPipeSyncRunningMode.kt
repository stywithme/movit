package com.movit.core.posecapture.boundary.trainingdebug

/**
 * MediaPipe running modes used by Training Debug Lab for still / sequential media.
 * Live camera continues to use `LIVE_STREAM` via [com.movit.core.posecapture.android.MediaPipePoseDetector].
 */
enum class MediaPipeSyncRunningMode {
    IMAGE,
    VIDEO,
}
