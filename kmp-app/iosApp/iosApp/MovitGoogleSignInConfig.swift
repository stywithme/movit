import Foundation

/// Google OAuth IDs for Movit iOS — must stay in sync with `iosApp/Info.plist` (`GIDClientID` + URL scheme).
///
/// Setup (replace placeholders, do not commit real secrets to public repos):
/// 1. `iosApp/README.md` → **Google Cloud Console — iOS client setup**
/// 2. Create an iOS OAuth client for bundle ID `com.movit.iosApp`
/// 3. Copy the client ID into `iosClientID`, `Info.plist` `GIDClientID`, and reversed URL scheme
enum MovitGoogleSignInConfig {
    /// iOS OAuth client (bundle ID: `com.movit.iosApp`). See `iosApp/README.md` § Google Sign-In.
    static let iosClientID = "YOUR_IOS_CLIENT_ID.apps.googleusercontent.com"

    /// Web/server client — same audience as Android `WEB_CLIENT_ID` and backend `GOOGLE_CLIENT_ID`.
    static let webClientID = "426489495025-acss2bntct6qgpc1agqif2cf9k2ha9k4.apps.googleusercontent.com"

    static var isConfigured: Bool {
        !iosClientID.hasPrefix("YOUR_IOS_CLIENT_ID")
    }

    /// `CFBundleURLSchemes` entry — reversed iOS client ID.
    static var reversedClientID: String {
        let suffix = ".apps.googleusercontent.com"
        guard iosClientID.hasSuffix(suffix) else { return "" }
        let idPart = String(iosClientID.dropLast(suffix.count))
        return "com.googleusercontent.apps.\(idPart)"
    }
}
