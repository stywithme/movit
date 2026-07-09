# KMP Mobile — Architecture As-Built

**SSOT** for `kmp-app/` structure, navigation, and cutover status.  
**Last verified:** 2026-06-22 against `settings.gradle.kts`, `AndroidManifest.xml`, `MovitMainActivity`, feature modules, SQLDelight.

**Related (do not duplicate here):**

- Page maturity scores → [`Page-Scorecards.md`](../../02-Roadmaps-And-Plans/UI-UX/Page-Scorecards.md)
- Screen inventory (lighter) → [`Android-KMP-Mobile-Screen-Inventory.md`](../../02-Roadmaps-And-Plans/UI-UX/Android-KMP-Mobile-Screen-Inventory.md)
- 100% cutover plan → [`Android-KMP-Legacy-Decommission-And-100-Percent-Cutover-Plan.md`](../../02-Roadmaps-And-Plans/UI-UX/Android-KMP-Legacy-Decommission-And-100-Percent-Cutover-Plan.md)
- Phase 07 (open) → [`Android-KMP-Mobile-UI-UX-Phase-07-Training-Engine-Migration-Plan.md`](../../02-Roadmaps-And-Plans/UI-UX/Android-KMP-Mobile-UI-UX-Phase-07-Training-Engine-Migration-Plan.md)

---

## 1. Module map (19 Gradle modules)

Source: `kmp-app/settings.gradle.kts` (`include(...)`).

| Layer | Module | Role |
|-------|--------|------|
| **App** | `:app` | Android entry — `MovitMainActivity`, `SubscriptionActivity`, `MovitDataInstall`, CameraX/MediaPipe wiring |
| **Shared** | `:shared` | `AppResult`, small cross-cutting helpers |
| **Core** | `:core:model` | Explore/domain models |
| | `:core:resources` | i18n (en/ar), `movitText`, locale |
| | `:core:designsystem` | Movit M3 design system + catalog components |
| | `:core:network` | Ktor 3, DTOs, `MovitMobileApi` |
| | `:core:data` | `MovitData`, SQLDelight, sync, session, outbox |
| | `:core:training-engine` | Pure training logic (state machine, rep counter, scoring) |
| | `:core:pose-capture` | `expect/actual` camera + pose boundary |
| **Feature** | `:feature:shell` | App shell, navigation, iOS `MainViewController` framework host |
| | `:feature:home` | Home tab |
| | `:feature:train` | Train tab |
| | `:feature:explore` | Explore tab |
| | `:feature:reports` | Reports tab + `ReportDetail` |
| | `:feature:account` | Auth, onboarding, assessment, level, profile |
| | `:feature:library` | Exercises/workouts/programs, prepare, planned sessions |
| | `:feature:training` | Live `TrainingSession` (camera + pose) |
| | `:feature:training-debug` | Training Debug Lab (debug builds) |
| | `:feature:billing` | Play Billing + checkout helpers (platform) |

**Composite build (not counted above):** `build-logic/` — convention plugins.

---

## 2. Android entry points

| Component | Manifest | Role |
|-----------|----------|------|
| **`MovitMainActivity`** | `MAIN` / `LAUNCHER` | Production launcher → `attachMovitShellHost()` → Compose shell |
| **`SubscriptionActivity`** | `VIEW` deep link `movit://subscription/result` | Legacy XML billing UI (Play + MyFatoorah return) |
| Debug only | `app/src/debug/AndroidManifest.xml` | `MovitShellPilotActivity`, `MovitExplorePilotActivity`, `MovitDesignSystemCatalogActivity`, `TrainingDebugActivity` — **no LAUNCHER** (merged main manifest wins) |

Auth bootstrap and logout run **in-shell** via `MovitInnerRoute.Auth` — no `SplashActivity` / legacy tab container.

**iOS:** `iosApp` → `MainViewController()` → same `MovitAppShellRoute`.

---

## 3. Navigation model

Custom stack (no Jetpack `NavHost`).

```
MovitMainActivity
  └─ MovitAppShellHost
        ├─ MovitFloatingNavBar → 4 tabs (Home · Train · Explore · Reports)
        ├─ Header avatar → Profile (inner route, not in bottom bar)
        └─ innerStack: List<MovitInnerRoute>  ← back stack; bottom bar hidden
```

**5 destinations** (`MovitAppDestination`): Home, Train, Explore, Reports, Profile.  
**4 visible tabs** (`MovitShellFloatingDestinations`): Profile is reached from the header avatar, not the floating bar.

### Inner routes (`MovitInnerRoute.kt`)

| Route | Feature module | Purpose |
|-------|----------------|---------|
| `ExercisesLibrary` | library | Exercise catalog |
| `WorkoutsLibrary` | library | Workout catalog |
| `ProgramList` | library | Programs |
| `ProgramDetail` | library | Program week/day |
| `WeeklyReport` | library | Weekly summary |
| `WorkoutSession` | library | Planned session flow |
| `ExercisePrepare` | library | Prepare / rest between sets |
| `TrainingSession` | training | Live camera training |
| `ReportDetail` | reports | Exercise report drill-down |
| `Auth` | account | Sign-in / sign-up / forgot |
| `Profile` | account | Settings & account |
| `ProfileOnboarding` | account | Training profile (7 steps) |
| `Assessment` | account | Body scan + results |
| `LevelProfile` | account | Level & progression |
| `TrainingDebugLab` | training-debug | Hidden debug lab |

---

## 4. Data layer (SQLDelight)

**Module:** `:core:data` · **Plugin:** SQLDelight (`MovitDatabase`).

| Table (`.sq`) | Purpose |
|-----------------|---------|
| `outbox_entry` | Offline write queue |
| `json_cache_entry` | JSON cache by `(store, key)` |
| `sync_metadata` | Per-scope sync version / timestamp |
| `session_journal` | Training session journal |

**Access:** `MovitData.localStore` after `MovitDataInstall.install()`.  
**Decision record (archived):** [`WS-4-Storage-Layer-Report.md`](../../03-Implemented-Archive/KMP/WS-4-Storage-Layer-Report.md).

**Stack:**

```
Screen / ViewModel → Shared*Repository (feature) → MovitData → MovitMobileApi (Ktor) → MovitPlatformBindings
```

---

## 5. Phase status (honest)

| Phase | Status | Notes |
|-------|--------|-------|
| 01–03 Foundation / Explore / Shell | Closed | Design system, shell, explore pilot |
| 04–05 Home / page-by-page | Closed | Tabs + account flows in KMP |
| Pre-06 Architecture | Closed | Session storage, production readiness |
| **06 Production launcher** | **Closed** | `MovitMainActivity` = LAUNCHER |
| Pre-07 Stabilization | Closed | Outbox, engine boundaries, SQLDelight |
| **07 Training engine** | **OPEN** | Live training polish, iOS pose, Visual QA, E2E — see Phase 07 plan |

**Cutover estimate (Android):**

- **~85% structural** — single launcher, KMP shell, legacy UI/XML removed, 19 modules, in-shell auth.
- **~40% production-ready** — billing still XML `SubscriptionActivity`, legacy workout sync drain, Phase 07 product gaps.

---

## 6. Legacy remnants (intentional, shrinking)

| Remnant | Location | Why it remains |
|---------|----------|----------------|
| **`SubscriptionActivity`** | `app/.../billing/` + `activity_subscription.xml` | Play Billing + MyFatoorah UI not yet full Compose in `feature:account` |
| **`LegacyWorkoutSyncDrain`** | `app/.../host/` | Drains pre-KMP `AnalyticsStorage` pending executions into KMP outbox on upgrade |
| **`AnalyticsStorage` / `LegacyWorkoutUpload`** | `app/.../storage/` | Read by sync drain only |
| **`com.trainingvalidator.poc`** | — | **0 files** (removed) |

**Not remnants:** `MovitShellPilotActivity` (debug QA entry, not production launcher).

---

## 7. Build & local API

- Root: `kmp-app/settings.gradle.kts` applies `gradle/sync-local-api-ip.gradle.kts` so `api.physical_device_ip` tracks the dev machine when `api.mode=local`.
- `applicationId`: `com.movit.androidApp`.

---

## 8. Key source files

| Concern | Path |
|---------|------|
| Launcher | `app/src/movitShellEnabled/java/com/movit/MovitMainActivity.kt` |
| Shell host | `app/src/movitShellHost/java/com/movit/host/MovitShellHost.kt` |
| Data install | `app/src/main/java/com/movit/host/MovitDataInstall.kt` |
| Destinations | `feature/shell/.../MovitAppDestination.kt` |
| Inner routes | `feature/shell/.../MovitInnerRoute.kt` |
| Route wiring | `feature/shell/.../MovitInnerHost.kt` |
| Manifest | `app/src/main/AndroidManifest.xml` |
