package com.movit.feature.training

import com.movit.core.posecapture.MovitPoseCaptureIosBindings
import com.movit.core.training.boundary.DeviceTiltPort

actual fun resolveTrainingDeviceTiltPort(): DeviceTiltPort? =
    MovitPoseCaptureIosBindings.createDeviceTiltPort()
