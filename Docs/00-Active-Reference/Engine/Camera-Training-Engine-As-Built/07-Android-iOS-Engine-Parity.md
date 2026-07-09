| | |
|---|---|
| **Status** | `ACTIVE` |
| **SSOT for** | Android/iOS engine parity and platform boundaries |
| **Code** | `kmp-app/core/training-engine/src/{commonMain,androidMain,iosMain}/` |
| **Verified** | 2026-07-04 |

# Android / iOS engine parity

The training engine targets **~100% commonMain** logic. Platform code is limited to **boundary adapters** (camera, audio, haptics, time). Pose ingest lives in sibling module `core/pose-capture` with the same split.

---

## Source set layout

```
kmp-app/core/training-engine/src/
├── commonMain/kotlin/com/movit/core/training/   # Engine SSOT (~200+ files)
├── commonTest/kotlin/...                         # Parity + unit tests
├── androidMain/kotlin/.../boundary/              # 8 platform files
└── iosMain/kotlin/.../boundary/                  # 8 platform files
```

**Rule:** No rep counting, phase logic, or scoring in `androidMain`/`iosMain`.

---

## Platform boundary files (8 + 8)

| Boundary | Android | iOS | Role |
|----------|---------|-----|------|
| Pose detector | `boundary/PoseDetector.android.kt` | `boundary/PoseDetector.ios.kt` | Wraps `pose-capture` |
| Camera frames | `boundary/CameraFrameSource.android.kt` | `boundary/CameraFrameSource.ios.kt` | Frame source port |
| Audio clips | `boundary/AudioFeedbackPlayer.android.kt` | `boundary/AudioFeedbackPlayer.ios.kt` | Cached MP3 playback |
| TTS fallback | `boundary/SpeechSynthesizer.android.kt` | `boundary/SpeechSynthesizer.ios.kt` | Missing clip → speak text |
| Haptics | `boundary/HapticsPort.android.kt` | `boundary/HapticsPort.ios.kt` | Vibration patterns |
| Wall clock | `engine/PlatformTime.kt` | `engine/PlatformTime.kt` | `currentTimeMillis()` expect/actual |
| Pipeline log | `diagnostics/TrainingPipelineLogger.android.kt` | `diagnostics/TrainingPipelineLogger.ios.kt` | Debug logging |
| iOS-only audio session | — | `boundary/IosTrainingAudioSession.kt` | AVAudioSession ducking |

Android-only: `boundary/TtsVoiceSelector.kt` (locale voice pick).

Pose smoothing parity is in **`core/pose-capture`** (`PoseLandmarkSmoother`, shared One-Euro params on Android `LandmarkSmoother` and iOS camera path).

---

## commonMain engine scope (parity-critical)

All of the following run identically on both platforms:

| Area | Key types |
|------|-----------|
| Session | `SessionSupervisor`, `MovitTrainingEngine`, `HoldExerciseCoordinator` |
| Counting | `PhaseStateMachine`, `RepCounter`, `RepCompletionCoordinator` |
| Evaluation | `FramePipelineExecutor`, `JointEvaluator`, `PositionValidator` |
| Feedback | `FeedbackScheduler`, `FeedbackRouter`, `TrainingFeedbackEventRouter` |
| Bilateral | `BilateralController` |
| Config | `ExerciseConfig`, serializers |
| Journal / report | `MotionRecorder`, `MovitSessionReport` |

---

## Parity test suite

**Location:** `kmp-app/core/training-engine/src/commonTest/`

| Test class | Validates |
|------------|-----------|
| `MovitTrainingEngineParityTest` | Full engine rep/phase outputs vs JSON fixtures |
| `FrozenParityFixtureTest` | Frozen snapshot regression |
| `PhaseStateMachineTest` | Phase FSM |
| `BilateralControllerParityTest` | Side switching |
| `FeedbackSchedulerParityTest` | Cooldown / coach intensity |
| `JointAngleCalculatorParityTest` | Geometry |
| `OneEuroFilterParityTest` | Filter coefficients |
| `PoseFrameAssemblerDebugParityTest` | Frame assembly |
| `PositionValidatorTest` | Position checks |
| `VisibilityMonitorTest` | Visibility policy |

**Fixtures:** `commonTest/resources/fixtures/parity/*.json` loaded via `ParityFixtures` / `ParityRunner`.

**Legacy reference:** Tests document parity with retired Android MO worktree behavior.

---

## pose-capture parity (adjacent module)

| Component | Parity note |
|-----------|-------------|
| `PoseLandmarkSmoother` | commonMain One-Euro — same params both platforms |
| `MediaPipeSyncPoseDetector` | Android CameraX + MediaPipe |
| `IosCameraFrameSource` | AVFoundation + MediaPipe |
| `DeviceTiltPort` | Android sensor vs iOS motion; both 120ms smoothing tau |

Engine receives `PoseFrame` — does not depend on platform detector type.

---

## Feature / UI layer

`feature/training` is **commonMain Compose Multiplatform** — single `TrainingSessionScreen` for Android and iOS.

Platform-specific camera preview is injected via `cameraSlot` composable from platform entry (Android `CameraX` view, iOS `UIViewRepresentable`).

---

## Verification checklist

| Check | Command / action |
|-------|------------------|
| commonTest | `./gradlew :core:training-engine:cleanAllTests :core:training-engine:allTests` |
| Contract | `MovitMobileApiContractTest` (network module) |
| Manual | Same exercise fixture on Android + iOS debug overlay |

---

## Known parity gaps

| Gap | Notes |
|-----|-------|
| TTS voice quality | Platform voices differ; clip cache shared |
| Camera FOV / mirror | Front-camera mirror handled in engine; preview aspect may differ slightly |
| iOS pose refiner | `NoOpPoseRefiner` until assets ship |
| Heavy vs full model | `modelType` preference — different MediaPipe weights per platform packaging |

---

## Related docs

- [04-Training-Engine-Core.md](04-Training-Engine-Core.md) — engine internals
- [08-Engine-Settings.md](08-Engine-Settings.md) — `modelType` preference
- [training-engine.md](../training-engine.md) — module map
