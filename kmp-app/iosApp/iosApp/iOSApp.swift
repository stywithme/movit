import UIKit
import MovitApp

/// UIKit entry — bridge install at launch; UI window owned by `SceneDelegate`.
@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        installMovitBridges()
        return true
    }

    func application(
        _ application: UIApplication,
        configurationForConnecting connectingSceneSession: UISceneSession,
        options: UIScene.ConnectionOptions
    ) -> UISceneConfiguration {
        UISceneConfiguration(
            name: "Default Configuration",
            sessionRole: connectingSceneSession.role
        )
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
