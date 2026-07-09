| | |
|---|---|
| **Status** | `ACTIVE` |
| **SSOT for** | In-session training settings dialog |
| **Code** | `kmp-app/feature/training/TrainingSessionSettingsDialog.kt` |
| **Verified** | 2026-07-04 |

# Training settings UI

The in-session **Training Session Settings** dialog lets users adjust camera-training preferences without leaving the workout. Opened from the gear icon on `TrainingSessionScreen` top chrome.

---

## Component

**File:** `feature/training/TrainingSessionSettingsDialog.kt`

- Compose `AlertDialog` with scrollable body
- Confirm **Apply** → `TrainingSessionSettingsSelection` callback
- Cancel dismisses without applying

---

## Controls

| Section | Control type | Options | Preference key |
|---------|--------------|---------|----------------|
| **Visual indicator** | Two-way choice | Line \| Arc | `indicatorType` |
| **Voice feedback** | Switch | on/off | `voiceFeedbackEnabled` |
| **Coach intensity** | Three-way choice | Calm \| Standard \| Strict | `coachIntensity` |
| **Detection model** | Two-way choice | Full \| Heavy | `modelType` |
| **Camera position** | Button (if flip available) | Switch front/back | platform camera state |

String resources: `movitText("indicator_line")`, `"coach_calm"`, `"model_full"`, etc.

---

## Selection DTO

```kotlin
data class TrainingSessionSettingsSelection(
    val indicatorType: String,
    val voiceFeedbackEnabled: Boolean,
    val coachIntensity: String,
    val modelType: String,
)
```

Note: `trainingDisplayMode` and smoothing fields are **not** exposed in this dialog (remain at persisted defaults).

---

## Apply flow

**`TrainingSessionScreen`** → `onApplyTrainingSettings(selection)`  
**`MovitTrainingRoutes.kt`** (typical handler):

1. `MovitData.trainingPreferences.setIndicatorType(...)`
2. `setVoiceFeedbackEnabled(...)`
3. `setCoachIntensity(...)`
4. `setModelType(...)` — may re-init pose detector on next frame / session
5. ViewModel updates `FeedbackRouter.coachIntensity` and `voiceEnabled`

Changes to ROM style (`indicatorType`) take effect immediately on recomposition (new `romIndicators` remember block).

---

## Effects matrix

| Setting | Immediate effect | Engine impact |
|---------|------------------|---------------|
| Line / Arc | Skeleton overlay style changes | **UI only** — see [06-Arc-And-Line-Checks.md](06-Arc-And-Line-Checks.md) |
| Voice off | `FeedbackRouter` silences audible | No TTS/clips |
| Coach calm/strict | `FeedbackScheduler` cooldowns | More/less frequent coaching |
| Full / Heavy model | Detector swap | Latency vs accuracy tradeoff |
| Switch camera | `onFlipCamera` | Clears smoother state; mirror flip |

---

## What's NOT in the dialog

| Missing control | Where it lives |
|-----------------|----------------|
| Smoothing preset / One-Euro params | `MovitTrainingPreferencesState` — unwired |
| Training display mode (beginner/advanced) | Preferences only — no UI here |
| Target reps / weight | Workout flow screens |
| Exercise pose variant | Flow args |

---

## Related docs

- [08-Engine-Settings.md](08-Engine-Settings.md) — full preferences model
- [09-Camera-Training-UI-UX.md](09-Camera-Training-UI-UX.md) — gear icon entry point
- [10-Voice-Feedback.md](10-Voice-Feedback.md) — coach intensity behavior
