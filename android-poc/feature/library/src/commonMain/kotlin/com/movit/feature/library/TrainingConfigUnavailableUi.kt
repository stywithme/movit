package com.movit.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.movit.designsystem.components.MovitErrorState
import com.movit.designsystem.components.MovitLoadingState
import com.movit.resources.movitText

@Composable
fun TrainingConfigEnsureOverlay(
    isEnsuring: Boolean,
    unavailable: TrainingConfigUnavailableUi?,
    onSyncNow: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        isEnsuring -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                MovitLoadingState(message = movitText("training_config_ensuring"))
            }
        }
        unavailable != null -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                MovitErrorState(
                    title = movitText("training_config_unavailable_title"),
                    message = movitText(unavailable.messageKey),
                    actionLabel = if (unavailable.canSync) {
                        movitText("training_config_sync_now")
                    } else {
                        movitText("common_retry")
                    },
                    onRetry = if (unavailable.canSync) onSyncNow else onDismiss,
                    modifier = Modifier,
                )
            }
        }
    }
}
