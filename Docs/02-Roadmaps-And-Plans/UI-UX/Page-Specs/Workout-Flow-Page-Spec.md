# Workout Flow Page Spec (16)

آخر تحديث: 2026-06-09 · Phase 05 (~45% scorecard target)

## Implementation Status

- **Customize:** `WorkoutCustomizeScreen` + `WorkoutCustomizeViewModel` في `feature:library`.
- **Run sequencer (UI shell):** `WorkoutRunScreen` + `WorkoutRunViewModel` — بدون كاميرا/pose (Phase 07).
- **Shell routes:** `MovitInnerRoute.WorkoutCustomize` · `WorkoutRun` · `ExercisePrepare(workoutId?)`.
- **Legacy boundary:** `Start exercise` و Explore prepare → `MovitAppShellEffect.LaunchLegacyCameraTraining` → `LegacyTrainingLauncher` (Android debug pilot).

## Legacy Reference

| الشاشة | الصنف |
|--------|-------|
| Customize | `WorkoutCustomizeActivity` |
| Run orchestrator | `WorkoutRunActivity` |
| Camera training | `TrainingActivity` (خارج نطاق KMP Phase 05) |

## User Flow (KMP)

```
WorkoutSession → Start → WorkoutCustomize → Start → WorkoutRun → Start exercise → LegacyTrainingLauncher
WorkoutSession → Exercise card → ExercisePrepare(workoutId) → Start → WorkoutRun
Explore → ExercisePrepare → Start → LegacyTrainingLauncher (تمرين منفرد)
```

## Prototype Mapping (`16-workout-flow.html`)

| Prototype state | KMP |
|-----------------|-----|
| `customize` | `WorkoutCustomizeScreen` — عنوان، stepper sets، segmented rest 45/60/90، action dock |
| `run` | `WorkoutRunScreen` — EXERCISE n/m، progress bar، seq list (done/active/pending)، insight، Start exercise |

## Content Inventory

- Workout title + exercise count + estimated duration
- Per-exercise sets stepper (1–10)
- Rest between sets: 45s / 60s / 90s
- Run: current exercise, set/reps line, sequence, optional previous-form insight (preview)
- `WorkoutFlowCache` — handoff in-memory customize → run

## Out of Scope (Phase 05 / Pre-06)

- Camera overlay، pose setup، countdown حي
- `WorkoutTrainingEngine` / multi-exercise workout mode intent (legacy `WorkoutRunActivity` full orchestration)
- Persist customized plan to backend
- Drag-reorder في customize (legacy only)

## Tests

- `WorkoutFlowStateTest` — stepper، rest، cache، run sequence
- `MovitAppShellStateTest` — route stack customize/run

## Gradle

- `:feature:library` — screens + VMs + tests
- `:feature:shell` — `MovitInnerHost` wiring
- `:core:resources` — `workout_flow_*` keys (en + ar)

## Checklist

- [x] Customize screen (sets + rest segmented)
- [x] Run sequencer UI shell
- [x] Session Start → Customize
- [x] Prepare (with workoutId) → Run
- [x] Run Start exercise → legacy launcher
- [x] i18n `workout_flow_*`
- [ ] Camera overlay (Phase 07)
- [ ] Full workout-mode TrainingActivity intent (Phase 07)
- [ ] Persist customization API
