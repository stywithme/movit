package com.movit.core.posecapture

import com.movit.core.training.boundary.AcquirableDeviceTiltPort
import com.movit.core.training.boundary.CameraFrameSource
import com.movit.core.training.boundary.DeviceTiltPort
import com.movit.core.training.boundary.HapticsPort
import com.movit.core.training.boundary.PoseDetector

/**
 * Factory bindings for iOS pose-capture (D6).
 *
 * **Integration decision (07.6):** Kotlin/Native actuals live here (AVFoundation, CoreMotion,
 * AVSpeech, haptics). MediaPipe Tasks Vision iOS is **not** linked via Gradle cinterop — Swift
 * `MovitPoseLandmarkerBridge` in `iosApp` (CocoaPods: `MediaPipeTasksVision` via `Podfile` + `project.yml`)
 * registers via [installIosPoseLandmarkerBridge] from `iOSApp` init **before** `MainViewController`.
 *
 * Without a ready bridge, [IosPoseDetector] reports no-pose honestly (preview + permissions work).
 */
object MovitPoseCaptureIosBindings {
    fun createPoseDetector(): IosPoseDetector = IosPoseDetector()

    fun createCameraFrameSource(poseDetector: IosPoseDetector = createPoseDetector()): IosCameraFrameSource =
        IosCameraFrameSource(poseDetector)

    fun createDeviceTiltPort(): AcquirableDeviceTiltPort = IosDeviceTiltPort()

    fun createSpeechSynthesizer(): IosSpeechSynthesizer = IosSpeechSynthesizer()

    fun createHapticsPort(): HapticsPort = IosHapticsPort()

    /** Convenience tuple for manual wiring (no Koin on iOS shell yet). */
    data class SessionPorts(
        val cameraFrameSource: CameraFrameSource,
        val poseDetector: PoseDetector,
        val deviceTiltPort: DeviceTiltPort,
        val speechSynthesizer: IosSpeechSynthesizer,
        val hapticsPort: HapticsPort,
    )

    fun createSessionPorts(): SessionPorts {
        val detector = createPoseDetector()
        return SessionPorts(
            cameraFrameSource = createCameraFrameSource(detector),
            poseDetector = detector,
            deviceTiltPort = createDeviceTiltPort(),
            speechSynthesizer = createSpeechSynthesizer(),
            hapticsPort = createHapticsPort(),
        )
    }
}
