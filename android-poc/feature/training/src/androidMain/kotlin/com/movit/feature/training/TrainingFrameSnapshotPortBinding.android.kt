package com.movit.feature.training

import com.movit.core.data.MovitData
import com.movit.core.data.local.MovitAndroidRuntime
import com.movit.core.posecapture.android.MediaPipePoseDetector
import com.movit.core.training.boundary.NoOpTrainingFrameSnapshotPort
import com.movit.core.training.boundary.TrainingFrameSnapshotPort

actual fun defaultTrainingFrameSnapshotPort(): TrainingFrameSnapshotPort {
    if (!MovitData.isInstalled) return NoOpTrainingFrameSnapshotPort
    return runCatching {
        AndroidTrainingFrameSnapshotPort(
            detector = MovitData.koin().get<MediaPipePoseDetector>(),
            filesRoot = MovitAndroidRuntime.applicationContext.filesDir,
        )
    }.getOrElse { NoOpTrainingFrameSnapshotPort }
}
