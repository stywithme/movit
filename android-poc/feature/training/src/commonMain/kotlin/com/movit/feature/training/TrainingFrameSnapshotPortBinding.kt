package com.movit.feature.training

import com.movit.core.training.boundary.NoOpTrainingFrameSnapshotPort
import com.movit.core.training.boundary.TrainingFrameSnapshotPort

expect fun defaultTrainingFrameSnapshotPort(): TrainingFrameSnapshotPort

fun noopTrainingFrameSnapshotPort(): TrainingFrameSnapshotPort = NoOpTrainingFrameSnapshotPort
