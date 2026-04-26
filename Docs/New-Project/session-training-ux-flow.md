# Session training UX flow (android-poc)

## Entry

- **Program**: `ProgramSessionActivity` builds `ProgramSessionItem` list → `TrainingActivity` with `EXTRA_IS_SESSION_MODE` + JSON.
- **Explore workout**: `WorkoutSessionActivity` builds the same list from `WorkoutConfig` → `TrainingActivity`.

## In `TrainingActivity`

1. **`TrainingSessionModeController`** parses JSON, fills `sessionExerciseConfigMap` (slug → `ExerciseConfig`), starts `SessionTrainingEngine`.
2. **State machine** (`SessionTrainingEngine`):
   - `PreExercise` → user taps start → load exercise + `startTraining()`.
   - `Training` → set completes → `SetRest` or `ExerciseRest` or next `PreExercise` / `SessionComplete`.
   - `SetRest` / `ExerciseRest` → timer or skip → `PreExercise`.
   - `SessionComplete` → in-activity summary → `finish` with report extras; host opens `SessionReportActivity`.

## UX surfaces

- **Pre-exercise**: Full-screen overlay with exercise preview image (`imageUrl`), targets, optional instruction snippet, CTA.
- **Rest**: Countdown + preview (same exercise for set rest, next exercise for exercise rest) + tips + controls.
- **Active training**: Camera + skeleton; reference pose uses `positionImageUrl` in setup (`SetupCountdownBinder`).
- **Complete**: Short summary + CTA to open full report (parent activity navigates to `SessionReportActivity`).

## Data for previews

- `ExerciseRest` carries `nextExerciseSlug` and `nextExerciseItem` for rich “up next” UI.
- `SetRest` carries `exerciseSlug` for the current exercise preview during between-set rest.
