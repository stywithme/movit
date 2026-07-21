package com.movit.feature.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.movit.designsystem.MovitSpacing
import com.movit.resources.movitText

@Composable
fun BootstrapSplashScreen(
    state: DataBootstrapUiState,
    onRetry: () -> Unit,
    onContinuePartial: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is DataBootstrapUiState.Loading -> LoadingContent(stageKey = state.stageKey, modifier = modifier)
        is DataBootstrapUiState.Failed -> FailedContent(
            errorKey = state.errorKey,
            allowPartialContinue = state.allowPartialContinue,
            onRetry = onRetry,
            onContinuePartial = onContinuePartial,
            modifier = modifier,
        )
        DataBootstrapUiState.Hidden -> Unit
    }
}

@Composable
private fun LoadingContent(
    stageKey: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(MovitSpacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(MovitSpacing.lg))
        Text(
            text = movitText("bootstrap_title"),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(MovitSpacing.sm))
        Text(
            text = movitText(stageKey),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FailedContent(
    errorKey: String,
    allowPartialContinue: Boolean,
    onRetry: () -> Unit,
    onContinuePartial: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(MovitSpacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = movitText("bootstrap_failed_title"),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(MovitSpacing.sm))
        Text(
            text = movitText(errorKey),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(MovitSpacing.lg))
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Text(movitText("common_retry"))
        }
        if (allowPartialContinue) {
            Spacer(modifier = Modifier.height(MovitSpacing.sm))
            OutlinedButton(onClick = onContinuePartial, modifier = Modifier.fillMaxWidth()) {
                Text(movitText("bootstrap_continue_partial"))
            }
        }
    }
}
