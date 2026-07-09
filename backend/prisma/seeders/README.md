# Exercise seeders

## Production runbook (fresh database)

```bash
npm run prisma:generate
npm run prisma:migrate:deploy
npm run seed:base
npm run seed:full
```

Set `ADMIN_SEED_EMAIL` and `ADMIN_SEED_PASSWORD` before `seed:base` to bootstrap the super admin.

## Seed commands

| Command | Purpose |
|---------|---------|
| `npm run seed:base` | Reference data only: attributes, levels, pose positions, workout phases, permissions, system config |
| `npm run seed:full` | `seed:base` + exercises, workouts, programs, assessments, progression |
| `npm run seed:demo` | `seed:full` + demo users and synthetic history (`SEED_DEMO=true`) |
| `npm run seed:missing-exercises` | Partial upsert for missing-exercises JSON only (no DB clear) |
| `npm run seed:reset:full` | Local dev: wipe + full reseed (replaces legacy `prisma:seed`) |

### Flags (`prisma/seed.ts`)

- `--mode=base|full`
- `--reset` — clear content tables before seed (use `--reset=all` for full wipe)
- `--wipe-message-templates` — also delete TTS/audio message templates
- `--demo` — include demo users and user programs

## Layout

- **`Exercise-json/exercises-from-db/*.json`** — Canonical exercise library (mobile contract validated).
- **`Exercise-json/missing-exercises/exercises-from-db/*.json`** — Incremental batch from CSV.
- **`Exercise-json/workouts/*.json`** — Sample workout templates.
- **`seeders/exercise-json-batch.ts`** — Shared validated import for full and partial seeds.
- **`seeders/workout-phases.ts`** — Warm-up / main / cool-down catalog (moved out of migrations).
- **`seeders/seed-orchestrator.ts`** — Ordered pipeline for base and full modes.

## When to add exercises

1. Add `Exercise-json/exercises-from-db/<slug>.json` and run `seed:full` or `seed:missing-exercises` for a batch.
2. All JSON must pass `validateExerciseConfig()` from `src/lib/types/android-schema.ts`.
3. Optional: add `exercise-manifest.ts` override when inference is wrong.

## Migrations policy

- Migrations are **DDL only** (single production baseline).
- Reference/catalog data lives in seeders, not in `migration.sql` files.
