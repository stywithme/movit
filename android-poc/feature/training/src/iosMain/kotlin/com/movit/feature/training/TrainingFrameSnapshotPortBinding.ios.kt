package com.movit.feature.training

import com.movit.core.posecapture.IosPoseLandmarkerBridgeRegistry
import com.movit.core.training.boundary.NoOpTrainingFrameSnapshotPort
import com.movit.core.training.boundary.TrainingFrameSnapshotPort

/**
 * Uses [IosTrainingFrameSnapshotPort] when Swift registered a MediaPipe bridge with snapshots.
 * Otherwise [NoOpTrainingFrameSnapshotPort] — report evidence is metadata-only until Mac device validation.
 */
actual fun defaultTrainingFrameSnapshotPort(): TrainingFrameSnapshotPort {
    val bridge = IosPoseLandmarkerBridgeRegistry.current()
    return if (bridge?.isAvailable == true) {
        IosTrainingFrameSnapshotPort(filesRoot = IosApplicationFiles.documentsRoot())
    } else {
        NoOpTrainingFrameSnapshotPort
    }
}
