import UIKit
import MovitApp

/// UIKit entry — hosts Compose `MainViewController` directly (avoids SwiftUI lifecycle gaps).
@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        installMovitBridges()

        let window = UIWindow(frame: UIScreen.main.bounds)
        window.rootViewController = MainViewControllerKt.MainViewController()
        window.makeKeyAndVisible()
        self.window = window
        return true
    }

    func application(
        _ app: UIApplication,
        open url: URL,
        options: [UIApplication.OpenURLOptionsKey: Any] = [:]
    ) -> Bool {
        #if canImport(GoogleSignIn)
        return MovitGoogleSignInBootstrap.handle(url: url)
        #else
        return false
        #endif
    }

    private func installMovitBridges() {
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
        BackgroundSyncScheduler_iosKt.registerIosBackgroundSyncAtLaunch()
    }
}
