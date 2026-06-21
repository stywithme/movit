package com.movit.core.posecapture.di

import com.movit.core.data.local.MovitAndroidRuntime
import com.movit.core.posecapture.android.AndroidDeviceTiltPort
import com.movit.core.posecapture.android.AndroidPoseModelTypePort
import com.movit.core.posecapture.android.AndroidPoseRefiner
import com.movit.core.posecapture.android.CameraXFrameSource
import com.movit.core.posecapture.android.MediaPipePoseDetector
import com.movit.core.posecapture.android.MediaPipeSyncPoseDetector
import com.movit.core.posecapture.boundary.PoseRefiner
import com.movit.core.posecapture.boundary.trainingdebug.PoseModelTypePort
import com.movit.core.training.boundary.AcquirableDeviceTiltPort
import com.movit.core.training.boundary.CameraFrameSource
import com.movit.core.training.boundary.PoseDetector
import org.koin.core.module.Module
import org.koin.dsl.module

fun movitPoseCaptureAndroidModule(): Module = module {
    single<AcquirableDeviceTiltPort> {
        AndroidDeviceTiltPort(MovitAndroidRuntime.applicationContext)
    }
    single<PoseRefiner> { AndroidPoseRefiner() }
    single<PoseModelTypePort> {
        AndroidPoseModelTypePort(MovitAndroidRuntime.applicationContext)
    }
    single {
        MediaPipePoseDetector(
            context = MovitAndroidRuntime.applicationContext,
            poseRefiner = get(),
            modelPort = get(),
        )
    }
    factory {
        MediaPipeSyncPoseDetector(
            context = MovitAndroidRuntime.applicationContext,
            modelPort = get(),
        )
    }
    single<CameraFrameSource> {
        CameraXFrameSource(
            context = MovitAndroidRuntime.applicationContext,
            poseDetector = get(),
        )
    }
    single<PoseDetector> { get<MediaPipePoseDetector>() }
}
