package com.movit.core.posecapture.di

import com.movit.core.data.local.MovitAndroidRuntime
import com.movit.core.posecapture.android.CameraXFrameSource
import com.movit.core.posecapture.android.MediaPipePoseDetector
import com.movit.core.training.boundary.CameraFrameSource
import com.movit.core.training.boundary.PoseDetector
import org.koin.core.module.Module
import org.koin.dsl.module

fun movitPoseCaptureAndroidModule(): Module = module {
    single { MediaPipePoseDetector(MovitAndroidRuntime.applicationContext) }
    single<CameraFrameSource> {
        CameraXFrameSource(
            context = MovitAndroidRuntime.applicationContext,
            poseDetector = get(),
        )
    }
    single<PoseDetector> { get<MediaPipePoseDetector>() }
}
