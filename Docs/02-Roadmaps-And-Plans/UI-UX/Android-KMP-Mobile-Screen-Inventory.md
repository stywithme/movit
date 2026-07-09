# KMP Mobile — Screen Inventory

**Last verified:** 2026-06-22 · **Scope:** `kmp-app/` KMP shell only (legacy XML UI removed).

**SSOT architecture:** [`KMP-Mobile-As-Built.md`](../../00-Active-Reference/Architecture-As-Built/KMP-Mobile-As-Built.md)  
**Page maturity scores:** [`Page-Scorecards.md`](Page-Scorecards.md) (canonical — not duplicated here)

---

## Entry points

| Platform | Entry | Notes |
|----------|-------|-------|
| Android production | `MovitMainActivity` (`MAIN`/`LAUNCHER`) | `attachMovitShellHost()` → `MovitAppShellHost` |
| Android billing return | `SubscriptionActivity` | Deep link `movit://subscription/result` only |
| Android debug | `MovitShellPilotActivity`, `MovitExplorePilotActivity`, `MovitDesignSystemCatalogActivity`, `TrainingDebugActivity` | No LAUNCHER in debug manifest |
| iOS | `MainViewController()` | Same shell composables |

Auth, onboarding gates, and session expiry are handled **inside** the shell (`MovitInnerRoute.Auth`, `resolveStartupInnerStack`).

---

## Shell layout

```
MovitAppShellHost
  ├─ Floating nav: Home · Train · Explore · Reports
  ├─ Header avatar → Profile (inner route)
  └─ innerStack (back only, hides bottom nav)
```

**Files:** `MovitAppDestination.kt` · `MovitInnerRoute.kt` · `MovitInnerHost.kt` · `MovitAppShellViewModel.kt`

---

## Tabs (5 destinations, 4 in nav bar)

| Destination | Route | Screen | Module |
|-------------|-------|--------|--------|
| Home | `home` | `MovitHomeScreen` | `:feature:home` |
| Train | `train` | `MovitTrainScreen` | `:feature:train` |
| Explore | `explore` | `MovitExploreScreen` | `:feature:explore` |
| Reports | `reports` | `MovitReportsScreen` | `:feature:reports` |
| Profile | `profile` | `MovitProfileScreen` | `:feature:account` (via avatar, not bottom bar) |

### In-tab sub-states (not inner routes)

- **Reports:** `Overview` · `Exercises` · `Trends` tabs; exercise row → `ReportDetail`
- **Profile:** language / appearance / logout overlays; subscription overlay → may launch `SubscriptionActivity` on Android

---

## Inner routes

| `MovitInnerRoute` | Screen | From (typical) |
|-------------------|--------|----------------|
| `ExercisesLibrary` | `ExercisesLibraryScreen` | Explore, shortcuts |
| `WorkoutsLibrary` | `WorkoutsLibraryScreen` | Explore |
| `ProgramList` | `ProgramListScreen` | Explore, Train |
| `ProgramDetail` | `ProgramDetailScreen` | Programs, Home |
| `WeeklyReport` | `WeeklyReportScreen` | Train, ProgramDetail |
| `WorkoutSession` | `WorkoutSessionScreen` | Train, ProgramDetail, WorkoutsLibrary |
| `ExercisePrepare` | `ExercisePrepareScreen` | Libraries, WorkoutSession |
| `TrainingSession` | `TrainingSessionScreen` | ExercisePrepare (`KmpLive`) |
| `ReportDetail` | `ReportDetailScreen` | Reports, Home, post-training |
| `Auth` | `MovitAuthScreen` | Cold start, logout, session expired |
| `Profile` | `MovitProfileScreen` | Header avatar |
| `ProfileOnboarding` | `MovitOnboardingScreen` | Post-auth gate, Profile |
| `Assessment` | `MovitAssessmentScreen` | Home, Train, Profile, Level |
| `LevelProfile` | `MovitLevelScreen` | Home, Profile |
| `TrainingDebugLab` | debug lab UI | Debug builds only |

### Training flow (happy path)

`ExercisePrepare` → `TrainingSession` → (complete) → `WorkoutSession` / `ReportDetail` / rest via `ExercisePrepare(prepareMode=rest)`

### Auth sub-screens (`AuthScreen` enum)

`Splash` → `Intro` → `SignIn` / `SignUp` / `Forgot`

### Onboarding steps (7)

Age/gender → body metrics → experience → goal → weekdays → location/equipment → summary

### Assessment phases

`PreScreening` → `BodyScan` → `Results`

---

## Android activities outside Compose shell

| Activity | Role |
|----------|------|
| `SubscriptionActivity` | XML billing (Play + MyFatoorah); opened from profile/reports upsell or payment return URL |

No `SplashActivity`, `MainContainerActivity`, or legacy training Activities remain in the shipping manifest.

---

## Deep links

| URI / mechanism | Target |
|-----------------|--------|
| `movit://subscription/result` | `SubscriptionActivity` |
| Shell intent extras | Parsed by `MovitShellDeepLinkParser` → inner routes (`workout_session`, `exercise_prepare`, `assessment`, `program_detail`, …) |

---

## Debug-only surfaces

| Surface | Access |
|---------|--------|
| `MovitShellPilotActivity` | adb / IDE — same host as production |
| `MovitExplorePilotActivity` | adb |
| `MovitDesignSystemCatalogActivity` | adb — design system catalog |
| `TrainingDebugActivity` | adb — training debug lab |
| `TrainingDebugLab` inner route | In-shell debug entry |

---

## Counts (reference)

| Category | Count |
|----------|-------|
| Tab destinations | 5 (4 in floating nav) |
| `MovitInnerRoute` types | 15 |
| Production Activities in manifest | 2 (`MovitMainActivity`, `SubscriptionActivity`) |
| Gradle modules | 19 |

---

*For cutover blockers and remaining legacy code, see [`Android-KMP-Legacy-Decommission-And-100-Percent-Cutover-Plan.md`](Android-KMP-Legacy-Decommission-And-100-Percent-Cutover-Plan.md).*
