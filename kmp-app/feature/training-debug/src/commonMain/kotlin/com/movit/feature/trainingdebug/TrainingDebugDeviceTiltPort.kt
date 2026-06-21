package com.movit.feature.trainingdebug

import com.movit.core.training.boundary.DeviceTiltPort

/** Platform tilt sensor for Debug Lab position checks (Android Koin; iOS null stub). */
expect fun resolveTrainingDebugDeviceTiltPort(): DeviceTiltPort?
