package com.movit.feature.trainingdebug

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import com.movit.designsystem.components.MovitBackButton
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun PlatformTrainingDebugScreen(
    viewModel: TrainingDebugViewModel,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Training Debug Lab") },
                navigationIcon = {
                    MovitBackButton(
                        onClick = onBack,
                        contentDescription = "Back",
                        modifier = Modifier.padding(start = 8.dp),
                    )
                },
                actions = {
                    Text(
                        text = "S${state.fps.sourceFps} I${state.fps.inferenceFps} A${state.fps.analysisFps}",
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    if (state.config.inputMode == TrainingDebugInputMode.CAMERA) {
                        IconButton(onClick = { viewModel.onEvent(TrainingDebugEvent.FlipCameraClicked) }) {
                            Icon(Icons.Default.Cameraswitch, contentDescription = null)
                        }
                    }
                    IconButton(onClick = { viewModel.onEvent(TrainingDebugEvent.CopyDebugInfoClicked) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            InputModeRow(state, viewModel)
            TabRow(state, viewModel)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state.config.inputMode) {
                    TrainingDebugInputMode.CAMERA -> {
                        TrainingDebugCameraHost(
                            isFrontCamera = state.isFrontCamera,
                            onFrame = { viewModel.onEvent(TrainingDebugEvent.FrameReceived(it)) },
                            onError = { viewModel.onEvent(TrainingDebugEvent.ErrorReceived(it)) },
                            onSourceFps = { viewModel.onEvent(TrainingDebugEvent.SourceFpsUpdated(it)) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    TrainingDebugInputMode.IMAGE,
                    TrainingDebugInputMode.VIDEO,
                    -> DisabledInputModePlaceholder(mode = state.config.inputMode)
                }
                StatusBand(
                    liveValue = state.analysis.liveValueText,
                    status = state.analysis.statusText,
                    modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
                )
            }
            if (state.config.infoPanelVisible) {
                InfoPanel(state.analysis.infoPanelText)
            }
        }
    }
}

@Composable
private fun InputModeRow(state: TrainingDebugUiState, viewModel: TrainingDebugViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TrainingDebugInputMode.entries.forEach { mode ->
            val supported = isInputModeSupportedOnPlatform(mode)
            FilterChip(
                selected = state.config.inputMode == mode,
                onClick = {
                    if (supported) viewModel.onEvent(TrainingDebugEvent.InputModeSelected(mode))
                },
                label = {
                    Text(
                        when (mode) {
                            TrainingDebugInputMode.CAMERA -> "Camera"
                            TrainingDebugInputMode.VIDEO -> if (supported) "Video" else "Video (iOS soon)"
                            TrainingDebugInputMode.IMAGE -> if (supported) "Image" else "Image (iOS soon)"
                        },
                    )
                },
                enabled = supported || state.config.inputMode == mode,
            )
        }
    }
}

@Composable
private fun TabRow(state: TrainingDebugUiState, viewModel: TrainingDebugViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TrainingDebugTab.entries.forEach { tab ->
            FilterChip(
                selected = state.config.activeTab == tab,
                onClick = { viewModel.onEvent(TrainingDebugEvent.TabSelected(tab)) },
                label = { Text(tab.name.replace('_', ' ')) },
            )
        }
    }
}

@Composable
private fun DisabledInputModePlaceholder(mode: TrainingDebugInputMode) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF101010)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "${mode.name} mode is not available on iOS yet.",
            color = Color.White.copy(alpha = 0.85f),
        )
    }
}

@Composable
private fun StatusBand(liveValue: String, status: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(liveValue, color = Color.White, style = MaterialTheme.typography.titleMedium)
        Text(status, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun InfoPanel(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A))
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        color = Color(0xFFE0E0E0),
        style = MaterialTheme.typography.bodySmall,
    )
}
