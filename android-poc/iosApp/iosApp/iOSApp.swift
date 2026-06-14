import SwiftUI
import MovitApp

@main
struct iOSApp: App {
    init() {
        // Register before Compose MainViewController starts the camera pipeline.
        IosPoseLandmarkerBridgeInstallKt.installIosPoseLandmarkerBridge(
            bridge: MovitPoseLandmarkerBridge()
        )
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea()
        }
    }
}
