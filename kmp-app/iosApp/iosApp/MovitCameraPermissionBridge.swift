import AVFoundation
import Foundation
import MovitApp

/// AVFoundation camera authorization for KMP pose capture.
/// Registers via `IosCameraPermissionBridgeInstallKt.installIosCameraPermissionBridge` from `iOSApp` init.
final class MovitCameraPermissionBridge: NSObject, IosCameraPermissionBridge {
    func authorizationStatus() -> Int32 {
        Int32(AVCaptureDevice.authorizationStatus(for: .video).rawValue)
    }

    func requestAccess(handler: IosCameraPermissionResultHandler) {
        AVCaptureDevice.requestAccess(for: .video) { granted in
            handler.onCompleted(granted: granted)
        }
    }
}
