import Foundation
import MovitApp
import UIKit

#if canImport(GoogleSignIn)
import GoogleSignIn
#endif

/// Swift Google Sign-In bridge for KMP `GoogleSignInHost`.
/// Registers via `IosGoogleSignInBridgeInstallKt.installIosGoogleSignInBridge` from `iOSApp` init.
///
/// Requires `GoogleSignIn` CocoaPod (`iosApp/Podfile`) + iOS OAuth client in Google Cloud Console.
/// Without a configured client ID, compiles as an honest stub (`isAvailable == false`).
final class MovitGoogleSignInBridge: NSObject, IosGoogleSignInBridge {
    var isAvailable: Bool {
        #if canImport(GoogleSignIn)
        return MovitGoogleSignInConfig.isConfigured
        #else
        return false
        #endif
    }

    func signIn(handler: IosGoogleSignInResultHandler) {
        #if canImport(GoogleSignIn)
        guard MovitGoogleSignInConfig.isConfigured else {
            handler.onCompleted(credentials: nil)
            return
        }
        guard let presenter = Self.topViewController() else {
            handler.onCompleted(credentials: nil)
            return
        }
        GIDSignIn.sharedInstance.signIn(withPresenting: presenter) { result, error in
            if error != nil {
                handler.onCompleted(credentials: nil)
                return
            }
            guard let user = result?.user,
                  let idToken = user.idToken?.tokenString,
                  !idToken.isEmpty else {
                handler.onCompleted(credentials: nil)
                return
            }
            let profile = user.profile
            let googleId = user.userID ?? profile?.email ?? ""
            let email = profile?.email ?? googleId
            let name = profile?.name ?? "User"
            let avatarUrl = profile?.imageURL(withDimension: 256)?.absoluteString
            handler.onCompleted(
                credentials: IosGoogleSignInCredentials(
                    idToken: idToken,
                    googleId: googleId,
                    email: email,
                    name: name,
                    avatarUrl: avatarUrl
                )
            )
        }
        #else
        handler.onCompleted(credentials: nil)
        #endif
    }

    private static func topViewController() -> UIViewController? {
        let scenes = UIApplication.shared.connectedScenes
        let windowScene = scenes.compactMap { $0 as? UIWindowScene }.first
        let window = windowScene?.windows.first { $0.isKeyWindow } ?? windowScene?.windows.first
        var top = window?.rootViewController
        while let presented = top?.presentedViewController {
            top = presented
        }
        return top
    }
}

#if canImport(GoogleSignIn)
enum MovitGoogleSignInBootstrap {
    static func configure() {
        guard MovitGoogleSignInConfig.isConfigured else { return }
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(
            clientID: MovitGoogleSignInConfig.iosClientID,
            serverClientID: MovitGoogleSignInConfig.webClientID
        )
    }

    @discardableResult
    static func handle(url: URL) -> Bool {
        GIDSignIn.sharedInstance.handle(url)
    }
}
#endif
