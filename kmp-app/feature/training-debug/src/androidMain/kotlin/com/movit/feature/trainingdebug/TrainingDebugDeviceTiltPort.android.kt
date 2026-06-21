package com.movit.feature.trainingdebug

import com.movit.core.data.MovitData
import com.movit.core.training.boundary.AcquirableDeviceTiltPort
import com.movit.core.training.boundary.DeviceTiltPort

actual fun resolveTrainingDebugDeviceTiltPort(): DeviceTiltPort? =
    runCatching { MovitData.koin().get<AcquirableDeviceTiltPort>() }.getOrNull()
