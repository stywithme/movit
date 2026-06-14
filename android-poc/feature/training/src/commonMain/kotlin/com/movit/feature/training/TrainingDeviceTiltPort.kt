package com.movit.feature.training

import com.movit.core.training.boundary.DeviceTiltPort

/** Resolves platform tilt sensor port for live training (Koin on Android, bindings on iOS). */
expect fun resolveTrainingDeviceTiltPort(): DeviceTiltPort?
