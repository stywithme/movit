package com.movit.feature.training



import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.runtime.Composable

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import com.movit.core.training.model.PoseFrame

import com.movit.designsystem.components.MovitErrorState

import com.movit.resources.movitText



@Composable

actual fun TrainingSessionCameraHost(

    onFrame: (PoseFrame) -> Unit,

    onCameraReady: () -> Unit,

    onError: (String) -> Unit,

    modifier: Modifier,

    useFrontCamera: Boolean,

    onDebugFps: ((Int) -> Unit)?,

) {

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {

        MovitErrorState(

            title = movitText("training_session_camera_denied_title"),

            message = movitText("training_session_camera_denied_ios"),

            modifier = Modifier,

        )

    }

}


