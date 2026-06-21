package com.movit.feature.training

import com.movit.core.data.MovitData
import com.movit.core.data.audio.AudioFileDownloadPort
import com.movit.core.data.audio.CachedAudioFeedbackPlayer
import com.movit.core.data.audio.MovitAudioPlayer
import com.movit.core.training.boundary.AcquirableDeviceTiltPort
import com.movit.core.training.boundary.DeviceTiltPort

actual fun resolveTrainingDeviceTiltPort(): DeviceTiltPort? =
    runCatching { MovitData.koin().get<AcquirableDeviceTiltPort>() }.getOrNull()
