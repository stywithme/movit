# Movit iOS shell (Compose Multiplatform)

## Local build

1. Install **JDK 17** (Temurin or `brew install openjdk@17`).
2. Install **Xcode** and **XcodeGen** (`brew install xcodegen`).
3. From this directory: `xcodegen generate`
4. Open `iosApp.xcodeproj` in Xcode, or:

```bash
xcodebuild -project iosApp.xcodeproj -scheme iosApp \
  -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 15' build
```

The pre-build script in `project.yml` discovers `JAVA_HOME` via `/usr/libexec/java_home`, `brew --prefix`, or `which java` — no hardcoded Homebrew paths.

## CI

GitHub Actions workflow `.github/workflows/movit-kmp-ios.yml` runs:

- `compileKotlinIosArm64` / `iosSimulatorArm64` on key modules
- `linkDebugFrameworkIosArm64` / `IosSimulatorArm64` for `:feature:shell`
- `iosSimulatorArm64Test` on KMP modules with `commonTest`
- `xcodebuild` for this target (after `xcodegen`)

## MediaPipe pose bridge (P1 iOS live training)

1. Add CocoaPods to `iosApp` (`pod 'MediaPipeTasksVision'`) on a Mac.
2. Copy `pose_landmarker_full.task` into the iosApp bundle (same asset as Android).
3. `MovitPoseLandmarkerBridge.swift` auto-enables when the pod + model are present.
4. Bridge registers via `IosPoseLandmarkerBridgeInstallKt.installIosPoseLandmarkerBridge` in `iOSApp` init.

Without the pod, the bridge compiles as a stub (`isAvailable == false`) — camera preview works, pose inference does not.

## StoreKit / subscriptions (TODO)

In-app purchase is **not implemented** on iOS yet. The KMP profile screen hides purchase CTAs when `PlatformInfo.supportsInAppSubscription` is `false` and shows `profile_subscription_ios_unavailable` instead of a silent no-op.

**Next steps for a real billing bridge:**

1. Add a small Swift `StoreKitManager` (products, purchase, restore, transaction listener).
2. Expose it to Kotlin/Native via a thin cinterop or callback interface on `IosMovitPlatform`.
3. Wire `MovitSubscriptionScreen` + backend verify endpoint (mirror `SubscriptionActivity` on Android).
4. Set `PlatformInfo.supportsInAppSubscription = true` on iOS once the bridge is production-ready.

Until then, Pro status on iOS follows the backend `isPro` flag from auth/profile sync only.
