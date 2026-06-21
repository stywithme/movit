# Workout run UX flow (kmp-app)

Multi-exercise **workout run** inside a single `TrainingActivity` (distinct from a **planned workout** block on the program calendar).

## Entry

- **Program**: `ProgramWorkoutActivity` builds `WorkoutLineItem` list → `TrainingActivity` with workout-mode extras + JSON.
- **Explore / quick start**: `WorkoutRunActivity` builds the same list from `WorkoutConfig` → `TrainingActivity`.

## In `TrainingActivity`

1. **`WorkoutTrainingEngine`** parses JSON, maps exercises, orchestrates the run.
2. **State machine** (`WorkoutTrainingEngine`):
   - `PreExercise` → user taps start → load exercise + `startTraining()`.
   - `Training` → set completes → `SetRest` or `ExerciseRest` or next `PreExercise` / `WorkoutComplete`.
   - `SetRest` / `ExerciseRest` → timer or skip → `PreExercise`.
   - `WorkoutComplete` → in-activity summary → `finish` with report extras; host opens `WorkoutReportActivity`.

## UX surfaces

- **Pre-exercise**: Full-screen overlay with exercise preview image (`imageUrl`), targets, optional instruction snippet, CTA.
- **Rest**: Countdown + preview (same exercise for set rest, next exercise for exercise rest) + tips + controls.
- **Active training**: Camera + skeleton; reference pose uses `positionImageUrl` in setup (`SetupCountdownBinder`).
- **Complete**: Short summary + CTA to open full report (`WorkoutReportActivity`).

## Backend sync

- Each exercise run → `POST /mobile/workout-executions` (`WorkoutExecution` + `executionMetrics`).
- Program block completion → `POST /mobile/planned-workouts/:plannedWorkoutId/complete` (`PlannedWorkoutReport`).

See [Workout-Domain-Naming.md](../Contracts/Workout-Domain-Naming.md).
