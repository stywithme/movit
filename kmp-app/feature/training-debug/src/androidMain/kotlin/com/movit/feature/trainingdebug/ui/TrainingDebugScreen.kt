package com.movit.feature.trainingdebug.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FlipCameraAndroid
import com.movit.designsystem.components.MovitBackButton
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movit.core.training.config.PositionOperator
import com.movit.core.training.config.PositionCheckType
import com.movit.core.training.position.BodyPosture
import com.movit.core.training.position.ExpectedDirection
import com.movit.core.training.position.VisibleRegion
import com.movit.feature.trainingdebug.DebugPoseModelType
import com.movit.feature.trainingdebug.DebugPositionCheckConfig
import com.movit.feature.trainingdebug.DebugSceneExpectationConfig
import com.movit.feature.trainingdebug.TrainingDebugAction
import com.movit.feature.trainingdebug.TrainingDebugEvent
import com.movit.feature.trainingdebug.TrainingDebugInputMode
import com.movit.core.training.geometry.JointLandmarkMapping
import com.movit.feature.trainingdebug.TrainingDebugSourceConfig
import com.movit.feature.trainingdebug.TrainingDebugTab
import com.movit.feature.trainingdebug.TrainingDebugUiState
import com.movit.feature.trainingdebug.TrainingDebugViewModel
import com.movit.feature.trainingdebug.android.AndroidDebugCameraPoseSource
import com.movit.feature.trainingdebug.android.AndroidDebugImagePoseSource
import com.movit.feature.trainingdebug.android.AndroidDebugVideoPoseSource
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingDebugScreen(
    viewModel: TrainingDebugViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    var landmarks by remember { mutableStateOf<List<com.movit.core.training.model.Landmark>?>(null) }
    var analysisImageWidth by remember { mutableIntStateOf(0) }
    var analysisImageHeight by remember { mutableIntStateOf(0) }

    val cameraSource = remember { AndroidDebugCameraPoseSource() }
    val imageSource = remember { AndroidDebugImagePoseSource(context) }
    val videoTexture = remember { android.view.TextureView(context) }
    val videoSource = remember { AndroidDebugVideoPoseSource(context, videoTexture) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            scope.launch {
                imageSource.loadUri(it)
                imageSource.reanalyze()
            }
        }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { scope.launch { videoSource.loadUri(it) } }
    }

    LaunchedEffect(state.config.inputMode, state.config.modelType, state.isFrontCamera) {
        val cfg = TrainingDebugSourceConfig(
            modelType = state.config.modelType,
            isFrontCamera = state.isFrontCamera,
        )
        when (state.config.inputMode) {
            TrainingDebugInputMode.CAMERA -> {
                imageSource.stop()
                videoSource.stop()
                cameraSource.start(cfg)
                viewModel.setModelLabel(cameraSource.modelLabel())
            }
            TrainingDebugInputMode.IMAGE -> {
                cameraSource.stop()
                videoSource.stop()
                imageSource.start(cfg)
                imageSource.reanalyze()
                viewModel.setModelLabel(imageSource.modelLabel())
            }
            TrainingDebugInputMode.VIDEO -> {
                cameraSource.stop()
                imageSource.stop()
                videoSource.start(cfg)
                viewModel.setModelLabel(videoSource.modelLabel())
            }
        }
    }

    DisposableEffect(Unit) {
        val jobs = listOf(
            scope.launch {
                cameraSource.frames.collect { frame ->
                    if (frame == null) {
                        landmarks = null
                        viewModel.onSourceFrame()
                        viewModel.onSkippedBusyFrames(cameraSource.skippedBusyFrames())
                        viewModel.onFrame(null)
                        return@collect
                    }
                    landmarks = frame.smoothedLandmarks
                    analysisImageWidth = frame.analysisImageWidth
                    analysisImageHeight = frame.analysisImageHeight
                    viewModel.onSourceFrame()
                    viewModel.onSkippedBusyFrames(cameraSource.skippedBusyFrames())
                    viewModel.onFrame(frame)
                }
            },
            scope.launch {
                imageSource.frames.collect { frame ->
                    val resolved = frame ?: return@collect
                    landmarks = resolved.smoothedLandmarks
                    analysisImageWidth = resolved.analysisImageWidth
                    analysisImageHeight = resolved.analysisImageHeight
                    viewModel.onFrame(resolved)
                }
            },
            scope.launch {
                videoSource.frames.collect { frame ->
                    val resolved = frame ?: return@collect
                    landmarks = resolved.smoothedLandmarks
                    analysisImageWidth = resolved.analysisImageWidth
                    analysisImageHeight = resolved.analysisImageHeight
                    viewModel.onSkippedBusyFrames(videoSource.skippedBusyFrames())
                    viewModel.onFrame(resolved)
                }
            },
        )
        videoSource.setProgressListener { c, d ->
            viewModel.setVideoState(videoSource.isPlaying(), c, d, loaded = true)
        }
        videoSource.setSeekResetListener { viewModel.resetAnalysisState("video seek") }
        onDispose {
            jobs.forEach { it.cancel() }
            scope.launch {
                cameraSource.stop()
                imageSource.stop()
                videoSource.stop()
            }
        }
    }

    LaunchedEffect(state.config) {
        if (state.config.inputMode == TrainingDebugInputMode.IMAGE && imageSource.hasImage()) {
            imageSource.reanalyze()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug ${state.fps.sourceFps}/${state.fps.inferenceFps} fps") },
                navigationIcon = {
                    MovitBackButton(
                        onClick = onBack,
                        contentDescription = "Back",
                        modifier = Modifier.padding(start = 8.dp),
                    )
                },
                actions = {
                    if (state.config.inputMode == TrainingDebugInputMode.CAMERA) {
                        IconButton(onClick = {
                            viewModel.onEvent(TrainingDebugEvent.FlipCameraClicked)
                            viewModel.resetAnalysisState("camera switch")
                        }) {
                            Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Flip")
                        }
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = { viewModel.onEvent(com.movit.feature.trainingdebug.TrainingDebugEvent.CopyDebugInfoClicked) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state.config.inputMode) {
                TrainingDebugInputMode.CAMERA -> AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).also { cameraSource.bindPreview(it, lifecycleOwner) }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                TrainingDebugInputMode.IMAGE -> Box(
                    Modifier.fillMaxSize().background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (imageSource.hasImage()) "Image mode" else "Pick image in settings",
                        color = Color.White,
                    )
                }
                TrainingDebugInputMode.VIDEO -> AndroidView(
                    factory = { videoTexture },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            TrainingDebugSkeletonOverlay(
                landmarks = landmarks?.let(::landmarksToSkeletonPoints),
                debug = state.analysis.overlayState,
                modifier = Modifier.fillMaxSize(),
                isFrontCamera = state.isFrontCamera,
                analysisImageWidth = analysisImageWidth,
                analysisImageHeight = analysisImageHeight,
            )

            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Color.Black.copy(0.55f)).padding(12.dp),
            ) {
                Text("${state.analysis.liveValueText} • ${state.analysis.statusText}", color = Color.White)
                if (state.config.infoPanelVisible) {
                    Text(
                        state.analysis.infoPanelText,
                        color = Color.White.copy(0.85f),
                        modifier = Modifier.height(100.dp).verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (state.config.inputMode == TrainingDebugInputMode.VIDEO) {
                    VideoBar(state, videoSource, viewModel)
                }
            }
        }
    }

    if (showSettings) {
        SettingsSheet(
            state = state,
            onDismiss = { showSettings = false },
            onAction = { action ->
                viewModel.dispatch(action)
                val keepOpen = action is TrainingDebugAction.ToggleJoint ||
                    action is TrainingDebugAction.SetTab ||
                    action is TrainingDebugAction.SetPositionCheck ||
                    action is TrainingDebugAction.SetSceneExpectation ||
                    action is TrainingDebugAction.SetTiltCorrection ||
                    action is TrainingDebugAction.SetInfoPanelVisible
                if (!keepOpen) showSettings = false
            },
            onPickImage = { imagePicker.launch("image/*") },
            onPickVideo = { videoPicker.launch("video/*") },
        )
    }
}

@Composable
private fun VideoBar(
    state: TrainingDebugUiState,
    videoSource: AndroidDebugVideoPoseSource,
    viewModel: TrainingDebugViewModel,
) {
    val duration = state.videoDurationMs.coerceAtLeast(1L)
    Slider(
        value = state.videoCurrentMs.toFloat(),
        onValueChange = { videoSource.seekTo(it.toLong()) },
        valueRange = 0f..duration.toFloat(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { videoSource.playPause() }) {
            Text(if (state.videoPlaying) "Pause" else "Play")
        }
        Button(onClick = {
            videoSource.resetPlayback()
            viewModel.resetAnalysisState("video reset")
        }) { Text("Reset") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    state: TrainingDebugUiState,
    onDismiss: () -> Unit,
    onAction: (TrainingDebugAction) -> Unit,
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var position by remember(state.config.positionCheck) { mutableStateOf(state.config.positionCheck) }
    var threshold by remember(state.config.positionCheck.threshold) {
        mutableStateOf(state.config.positionCheck.threshold.toString())
    }
    val landmarkOptions = remember { JointLandmarkMapping.trackedJointCodes.sorted() }

    fun updatePosition(updated: DebugPositionCheckConfig) {
        position = updated
        onAction(TrainingDebugAction.SetPositionCheck(updated))
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Input mode", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TrainingDebugInputMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.config.inputMode == mode,
                        onClick = { onAction(TrainingDebugAction.SetInputMode(mode)) },
                        label = { Text(mode.name) },
                    )
                }
            }
            when (state.config.inputMode) {
                TrainingDebugInputMode.IMAGE -> Button(onClick = onPickImage) { Text("Pick image") }
                TrainingDebugInputMode.VIDEO -> Button(onClick = onPickVideo) { Text("Pick video") }
                else -> Unit
            }
            Text("Tab", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TrainingDebugTab.entries.forEach { tab ->
                    FilterChip(
                        selected = state.config.activeTab == tab,
                        onClick = { onAction(TrainingDebugAction.SetTab(tab)) },
                        label = { Text(tab.name) },
                    )
                }
            }
            Text("Model", style = MaterialTheme.typography.titleMedium)
            Text("Active: ${state.modelLabel}", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DebugPoseModelType.entries.forEach { model ->
                    FilterChip(
                        selected = state.config.modelType == model,
                        onClick = { onAction(TrainingDebugAction.SetModelType(model)) },
                        label = { Text(model.name) },
                    )
                }
            }
            if (state.config.activeTab == TrainingDebugTab.ANGLE_DIAGNOSTICS) {
                Text("Joints (multi-select)", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    JointLandmarkMapping.trackedJointCodes.sorted().forEach { joint ->
                        FilterChip(
                            selected = joint in state.config.selectedJoints,
                            onClick = { onAction(TrainingDebugAction.ToggleJoint(joint)) },
                            label = { Text(joint) },
                        )
                    }
                }
            }
            if (state.config.activeTab == TrainingDebugTab.POSITION_CHECK) {
                DebugDropdownMenu(
                    label = "Type",
                    selectedText = position.checkType.name,
                    options = PositionCheckType.entries.toList(),
                    optionText = { it.name },
                    onSelected = { updatePosition(position.copy(checkType = it)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DebugDropdownMenu(
                        label = "Primary",
                        selectedText = position.primaryLandmark,
                        options = landmarkOptions,
                        optionText = { it },
                        onSelected = { updatePosition(position.copy(primaryLandmark = it)) },
                        modifier = Modifier.weight(1f),
                    )
                    DebugDropdownMenu(
                        label = "Secondary",
                        selectedText = position.secondaryLandmark,
                        options = landmarkOptions,
                        optionText = { it },
                        onSelected = { updatePosition(position.copy(secondaryLandmark = it)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (position.checkType.usesOperator()) {
                    DebugDropdownMenu(
                        label = "Operator",
                        selectedText = position.operator.name,
                        options = PositionOperator.entries.toList(),
                        optionText = { it.name },
                        onSelected = { updatePosition(position.copy(operator = it)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = threshold,
                    onValueChange = {
                        threshold = it
                        it.toDoubleOrNull()?.let { v ->
                            updatePosition(position.copy(threshold = v))
                        }
                    },
                    label = { Text("Threshold") },
                    modifier = Modifier.fillMaxWidth(),
                )
                FilterChip(
                    selected = state.config.tiltCorrectionEnabled,
                    onClick = { onAction(TrainingDebugAction.SetTiltCorrection(!state.config.tiltCorrectionEnabled)) },
                    label = { Text("Tilt correction") },
                )
            }
            if (state.config.activeTab == TrainingDebugTab.CAMERA_SCENE) {
                FilterChip(
                    selected = BodyPosture.STANDING in state.config.sceneExpectation.postures,
                    onClick = {
                        onAction(
                            TrainingDebugAction.SetSceneExpectation(
                                DebugSceneExpectationConfig(postures = listOf(BodyPosture.STANDING)),
                            ),
                        )
                    },
                    label = { Text("Standing") },
                )
                FilterChip(
                    selected = ExpectedDirection.FRONT in state.config.sceneExpectation.directions,
                    onClick = {
                        onAction(
                            TrainingDebugAction.SetSceneExpectation(
                                state.config.sceneExpectation.copy(directions = listOf(ExpectedDirection.FRONT)),
                            ),
                        )
                    },
                    label = { Text("Front") },
                )
                FilterChip(
                    selected = VisibleRegion.FULL_BODY in state.config.sceneExpectation.regions,
                    onClick = {
                        onAction(
                            TrainingDebugAction.SetSceneExpectation(
                                state.config.sceneExpectation.copy(regions = listOf(VisibleRegion.FULL_BODY)),
                            ),
                        )
                    },
                    label = { Text("Full body") },
                )
            }
            Button(onClick = onDismiss, Modifier.fillMaxWidth()) { Text("Close") }
        }
    }
}

@Composable
private fun <T> DebugDropdownMenu(
    label: String,
    selectedText: String,
    options: List<T>,
    optionText: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(selectedText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionText(option)) },
                        onClick = {
                            expanded = false
                            onSelected(option)
                        },
                    )
                }
            }
        }
    }
}

private fun PositionCheckType.usesOperator(): Boolean = when (this) {
    PositionCheckType.HORIZONTAL_ALIGNMENT,
    PositionCheckType.VERTICAL_ALIGNMENT,
    PositionCheckType.DEPTH_ALIGNMENT,
    -> false
    else -> true
}
