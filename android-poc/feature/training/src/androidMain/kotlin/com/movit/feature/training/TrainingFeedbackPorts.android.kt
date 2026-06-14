package com.movit.feature.training

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.movit.core.data.MovitData
import com.movit.core.data.audio.AudioFileDownloadPort
import com.movit.core.data.audio.CachedAudioFeedbackPlayer
import com.movit.core.data.audio.MovitAudioPlayer
import com.movit.core.training.boundary.AndroidHapticsPort
import com.movit.core.training.boundary.SpeechSynthesizer

@Composable
actual fun rememberTrainingFeedbackPorts(language: String): TrainingFeedbackPorts {
    val context = LocalContext.current.applicationContext
    return remember(context, language) {
        val speech = SpeechSynthesizer(context)
        val audioCache = runCatching { MovitData.koin().get<AudioFileDownloadPort>() }.getOrNull()
        val audioPlayer = audioCache?.let { cache ->
            CachedAudioFeedbackPlayer(
                audioCache = cache,
                mediaPlayer = MovitAudioPlayer(),
                speech = speech,
            )
        }
        TrainingFeedbackPorts(
            speech = speech,
            haptics = AndroidHapticsPort(context),
            audioPlayer = audioPlayer,
        )
    }
}
