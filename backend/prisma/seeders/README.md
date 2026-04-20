# Exercise seeders

## Layout

- **`Exercise-json/exercises/*.json`** — Core exercises (hand-authored). Use for stable products and detailed pose config.
- **`curated-extension-rows.ts` + `curated-catalog-extension.ts`** — Curated `lib_*` library rows (metadata + blueprint fields).
- **`curated-pose-blueprints.ts`** — Default pose variants by `movementPattern` when no override exists.
- **`curated-pose-overrides.ts`** — Per-slug pose variants (joints, checks, feedback) for `lib_*` exercises that must not use the generic blueprint.
- **`phase-range-builders.ts`** — Reusable `phaseRanges` / `phaseStateMessages` helpers for secondary joints (see file header for conventions).
- **`pose-variant-seed-helper.ts`** — Writes `poseVariant`, `positionCheck`, message templates, and assignments from JSON-shaped data.
- **`messages.ts`** — Stable `code` message templates (supports preserve-audio re-seed).
- **`clear.ts` / `seed.ts`** — Database clear modes and entrypoint (`npm run prisma:seed` vs `prisma:seed:full`).

## When to add what

1. **New core exercise** — Add JSON under `Exercise-json/exercises/` and ensure slug is picked up by `exercises-workouts.ts`.
2. **New `lib_*` row** — Add tuple to `curated-extension-rows.ts`; if the default blueprint is wrong, add an override in `curated-pose-overrides.ts`.
3. **Angles change by phase** — Use `phaseRanges` (and optionally `phaseStateMessages`) on **secondary** joints; keep `range` populated for the Admin UI template.

## Commands

- `npm run prisma:seed` — Reseed while preserving `feedback_message_templates` audio when possible.
- `npm run prisma:seed:full` — Full clear including message templates.

## Classification notes (`familyKey` vs `movementPattern`)

- **`movementPattern`** drives rep logic and default pose blueprints.
- **`familyKey`** groups analytics / UI; it may differ when the exercise is **hybrid** (e.g. wall sit + press, thruster) — those use `conditioning_family` while the pattern stays `PUSH_VERTICAL` / `SQUAT` for counting.
- **Jefferson curl** uses `HINGE` + `hinge_pattern_family` (loaded hinge) even when tagged mobility.

## Feedback templates

- **`joint_state`** — primary / secondary non-phase `stateMessages`.
- **`joint_state_phase`** — secondary `phaseStateMessages`; assignment `context` = `phase:state` (e.g. `bottom:warning`), optional `zone` = `up` | `down` for zone-based values. Seeder creates these from `phaseStateMessages` in JSON / overrides so TTS audio can attach per phase in the dashboard.

## Cooldown / `minErrorFrames` (seed defaults)

- Holds & slow mobility: higher `cooldownMs` (2500–3500), `minErrorFrames` 5–8.
- Dynamic strength: moderate cooldown (1800–2500), `minErrorFrames` 3–5.
- Power / jump / swing: lower cooldown (1500–2000), `minErrorFrames` 3–4; `severity: tip` uses slightly higher frames than `error` to reduce noise.
