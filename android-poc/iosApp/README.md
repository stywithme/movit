# Movit iOS shell (Compose Multiplatform)

## Local build (Mac only)

1. Install **JDK 17** (Temurin or `brew install openjdk@17`).
2. Install **Xcode**, **XcodeGen** (`brew install xcodegen`), and **CocoaPods** (`brew install cocoapods`).
3. Fetch the MediaPipe pose model (shared with Android):

```bash
cd android-poc
chmod +x scripts/fetch-pose-landmarker-model.sh
./scripts/fetch-pose-landmarker-model.sh
```

4. Generate the Xcode workspace (runs `pod install` via `postGenCommand`):

```bash
cd iosApp
xcodegen generate
```

5. Open **`iosApp.xcworkspace`** in Xcode (not `iosApp.xcodeproj` alone — CocoaPods requires the workspace).

Or build from the terminal:

```bash
xcodebuild -workspace iosApp.xcworkspace -scheme iosApp \
  -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 16' build
```

The pre-build script in `project.yml` discovers `JAVA_HOME` via `/usr/libexec/java_home`, `brew --prefix`, or `which java` — no hardcoded Homebrew paths.

### Windows / Linux

Kotlin iOS frameworks compile via Gradle (`:feature:shell:compileKotlinIosSimulatorArm64`). Full **Xcode + MediaPipe** build and camera smoke tests require a **Mac** — there is no Windows iOS simulator toolchain.

## CI

GitHub Actions workflow `.github/workflows/movit-kmp-ios.yml` runs:

- `compileKotlinIosArm64` / `iosSimulatorArm64` on key modules
- `linkDebugFrameworkIosArm64` / `IosSimulatorArm64` for `:feature:shell`
- `iosSimulatorArm64Test` on KMP modules with `commonTest`
- `xcodegen` + `pod install` + `xcodebuild` against `iosApp.xcworkspace` (after fetching the `.task` model)

## MediaPipe pose bridge (Phase 07)

| Piece | Location |
|-------|----------|
| CocoaPod | `iosApp/Podfile` → `MediaPipeTasksVision` **0.10.33** (matches Android `libs.versions.toml`) |
| XcodeGen + pods | `iosApp/project.yml` → `postGenCommand: pod install` |
| Swift bridge | `iosApp/MovitPoseLandmarkerBridge.swift` |
| KMP registry | `IosPoseLandmarkerBridgeInstallKt.installIosPoseLandmarkerBridge` in `iOSApp` init |
| Kotlin detector | `IosPoseDetector` — `isAvailable == true` after `warmUp` when model loads |
| Model bundle | `iosApp/Models/pose_landmarker_full.task` (resource; copied from Android assets in pre-build when present) |

Without the pod or model, the bridge compiles as a stub (`isAvailable == false`) — camera preview works, pose inference does not.

### Info.plist & privacy checklist

| Item | Status | Location / notes |
|------|--------|------------------|
| `NSCameraUsageDescription` | ✅ | `Info.plist` + `project.yml` |
| `NSMotionUsageDescription` | ✅ | `Info.plist` + `project.yml` |
| `BGTaskSchedulerPermittedIdentifiers` | ✅ | `com.movit.background-sync` |
| `UIBackgroundModes` → `fetch` | ✅ | Background sync scheduler |
| `ITSAppUsesNonExemptEncryption` | ✅ | `false` — export compliance |
| `GIDClientID` + `CFBundleURLTypes` | ❌ | Placeholder `YOUR_IOS_CLIENT_ID` — needs real OAuth client |
| `PrivacyInfo.xcprivacy` | ❌ | Not in repo — required for App Store privacy labels |
| App Icon (`AppIcon.appiconset`) | ✅ | `Assets.xcassets/AppIcon.appiconset/AppIcon.png` |
| In-App Purchase capability | ✅ | `project.yml` → `com.apple.InAppPurchase` |
| `iosApp.entitlements` (IAP / push) | ⚠️ | File exists but empty — verify in Xcode Signing & Capabilities |
| Release signing / `DEVELOPMENT_TEAM` | ❌ | Not configured in `project.yml` |
| `pose_landmarker_full.task` in bundle | ⚠️ | Copied at build from Android assets or `fetch-pose-landmarker-model.sh` |

### Manual Xcode checklist

1. Run `fetch-pose-landmarker-model.sh` once.
2. `xcodegen generate` in `iosApp/` (installs pods).
3. Open **`iosApp.xcworkspace`**.
4. Select a simulator or device → Build & Run.
5. Grant camera permission → start live training → confirm landmarks (not perpetual no-pose).

## StoreKit / subscriptions (iOS)

In-app subscriptions use **StoreKit 2** via `MovitStoreKitBridge.swift`, registered in `iOSApp` init alongside the pose bridge.

| Piece | Location |
|-------|----------|
| Swift bridge | `iosApp/MovitStoreKitBridge.swift` |
| KMP registry | `IosStoreKitBridgeInstallKt.installIosStoreKitBridge` in `iOSApp` init |
| Coordinator | `IosSubscriptionCoordinator` — purchase / restore + backend verify |
| Shell wiring | `MainViewController` → `onLaunchPlatformSubscription` |
| Platform flag | `PlatformInfo.supportsInAppSubscription = true` on iOS |

Flow mirrors Android `SubscriptionActivity`: load plans → StoreKit purchase or restore → `POST api/mobile/subscriptions/app-store/verify` → refresh profile.

### Manual App Store Connect checklist

| Step | Status |
|------|--------|
| 1. App record — bundle ID `com.movit.iosApp` | ⏳ Owner |
| 2. Paid Apps agreement + tax/banking | ⏳ Owner |
| 3. Subscription group | ⏳ Owner |
| 4. Products (monthly + yearly) + Product IDs | ⏳ Owner |
| 5. Backend `Plan.monthlyAppStoreProductId` / `yearlyAppStoreProductId` | ⏳ Owner |
| 6. Sandbox testers | ⏳ Owner |
| 7. Backend `APP_STORE_BUNDLE_ID` + `ALLOW_UNMAPPED_APP_STORE_PRODUCTS` policy | ⏳ Owner |
| 8. Migration `20260616120000_app_store_subscription` deployed | ⏳ Ops |
| 9. Xcode In-App Purchase capability on target | ✅ in `project.yml` |
| 10. Sandbox purchase → `isPro` after profile sync | ❌ Not smoke-tested |

Details:

1. **App record** — Create the iOS app in [App Store Connect](https://appstoreconnect.apple.com) with bundle ID `com.movit.iosApp` (matches `iosApp/project.yml`).
2. **Paid Apps agreement** — Accept agreements and complete tax / banking in App Store Connect.
3. **Subscription group** — Create a subscription group (e.g. "Movit Pro").
4. **Products** — Add auto-renewable subscriptions (monthly + yearly). Note each **Product ID** (e.g. `com.movit.pro.monthly`).
5. **Backend plan mapping** — Set `monthlyAppStoreProductId` / `yearlyAppStoreProductId` on each `Plan` row (admin API or DB). Product IDs must match App Store Connect exactly.
6. **Sandbox testers** — Add sandbox Apple IDs under Users and Access → Sandbox → Testers.
7. **Backend env** — Set `APP_STORE_BUNDLE_ID=com.movit.iosApp`. For local dev without plan mapping, `ALLOW_UNMAPPED_APP_STORE_PRODUCTS=true`.
8. **Deploy backend** — Run migration `20260616120000_app_store_subscription` before testing verify.
9. **Xcode** — Enable **In-App Purchase** capability on the `iosApp` target (Signing & Capabilities).
10. **Test** — Sign in to a sandbox account on device/simulator → Profile → View plans / Subscribe → complete purchase → confirm `isPro` after profile sync.

Manage renewal on iOS via **Settings → Apple ID → Subscriptions** (Apple handles billing; backend `cancel` marks local entitlement only for `app_store` gateway).

**MVP note:** Server verifies the StoreKit 2 JWS payload (expiry, bundle id, product id). Production should add full Apple certificate chain verification (App Store Server API / `@apple/app-store-server-library`).

## Google Sign-In (Phase 05+)

| Piece | Location |
|-------|----------|
| CocoaPod | `iosApp/Podfile` → `GoogleSignIn` **~> 8.0** |
| OAuth IDs | `iosApp/MovitGoogleSignInConfig.swift` + `Info.plist` (`GIDClientID`, `CFBundleURLTypes`) |
| Swift bridge | `iosApp/MovitGoogleSignInBridge.swift` |
| KMP registry | `IosGoogleSignInBridgeInstallKt.installIosGoogleSignInBridge` in `iOSApp` init |
| Kotlin host | `GoogleSignInHost.ios.kt` → `POST api/mobile/auth/google` via `MovitData.account.googleAuth` |
| Platform flag | `PlatformInfo.supportsGoogleSignIn = true` on iOS |

Without a real iOS OAuth client ID, the bridge compiles as a stub (`isAvailable == false`) — the Google CTA is visible but sign-in returns no credentials until configured.

### Google Cloud Console — iOS client setup

| Step | Status |
|------|--------|
| 1. Open Google Cloud Credentials | ⏳ Owner |
| 2. Create OAuth client ID → iOS | ⏳ Owner |
| 3. Bundle ID `com.movit.iosApp` | ✅ matches `project.yml` |
| 4. Copy iOS client ID | ❌ placeholder in repo |
| 5. Update `MovitGoogleSignInConfig.swift` + `Info.plist` (3 places) | ❌ placeholder |
| 6. Keep `webClientID` unchanged (Web client for backend) | ✅ documented |
| 7. `xcodegen generate` after edits | — |
| 8. Device/simulator Google sign-in smoke | ❌ blocked by step 4–5 |

Project: **sharp-weft-398315** (same as Android/web clients in `Docs/06-Assets/Config-Samples/google-auth-json/`).

1. Open [Google Cloud Console → APIs & Services → Credentials](https://console.cloud.google.com/apis/credentials).
2. **Create credentials → OAuth client ID → iOS**.
3. **Bundle ID:** `com.movit.iosApp` (must match `PRODUCT_BUNDLE_IDENTIFIER` in `project.yml`).
4. Copy the generated **iOS client ID** (format: `NNNNNNNNNN-xxxxxxxx.apps.googleusercontent.com`).
5. Update these three places with the **same** iOS client ID:
   - `iosApp/iosApp/MovitGoogleSignInConfig.swift` → `iosClientID`
   - `iosApp/iosApp/Info.plist` → `GIDClientID`
   - `iosApp/iosApp/Info.plist` → `CFBundleURLSchemes` → reversed client ID: `com.googleusercontent.apps.NNNNNNNNNN-xxxxxxxx`
6. **Do not change** `webClientID` / `MovitGoogleSignInConfig.webClientID` — it must stay the **Web** client (`426489495025-acss2bntct6qgpc1agqif2cf9k2ha9k4…`) so the ID token audience matches Android Credential Manager and backend `GOOGLE_CLIENT_ID`.
7. Re-run `xcodegen generate` in `iosApp/` if you edit `project.yml` instead of `Info.plist` directly.
8. Build on a **physical device or simulator**, tap **Continue with Google**, complete OAuth, confirm session reaches the home screen.

### Manual Xcode checklist (Google)

1. Complete Google Cloud Console steps above.
2. `xcodegen generate` in `iosApp/` (installs `GoogleSignIn` pod).
3. Open **`iosApp.xcworkspace`**.
4. Build & Run → Sign In → **Continue with Google**.
5. Verify backend accepts the token (`POST /api/mobile/auth/google`).

