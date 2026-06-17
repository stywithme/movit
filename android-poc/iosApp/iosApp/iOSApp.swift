import SwiftUI
import MovitApp

@main
struct iOSApp: App {
    init() {
        // Register before Compose MainViewController starts the camera pipeline.
        IosPoseLandmarkerBridgeInstallKt.installIosPoseLandmarkerBridge(
            bridge: MovitPoseLandmarkerBridge()
        )
        IosStoreKitBridgeInstallKt.installIosStoreKitBridge(
            bridge: MovitStoreKitBridge()
        )
        IosGoogleSignInBridgeInstallKt.installIosGoogleSignInBridge(
            bridge: MovitGoogleSignInBridge()
        )
        IosCameraPermissionBridgeInstallKt.installIosCameraPermissionBridge(
            bridge: MovitCameraPermissionBridge()
        )
        #if canImport(GoogleSignIn)
        MovitGoogleSignInBootstrap.configure()
        #endif
        BackgroundSyncSchedulerKt.registerIosBackgroundSyncAtLaunch()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea()
                .onOpenURL { url in
                    #if canImport(GoogleSignIn)
                    _ = MovitGoogleSignInBootstrap.handle(url: url)
                    #endif
                }
        }
    }
}
