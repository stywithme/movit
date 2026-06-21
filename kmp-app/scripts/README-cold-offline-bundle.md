# Cold offline bundle regeneration (FIX-4)

The file `core/resources/src/commonMain/composeResources/files/cold_offline_bundle.json` is the **first-install offline seed** read by `ColdOfflineBundleSeeder`. It must contain **real** server data only — no demo users, programs, or stock images.

## Regenerate

From `kmp-app/`:

```bash
node scripts/generate-cold-offline-bundle.mjs
```

Optional:

```bash
node scripts/generate-cold-offline-bundle.mjs --base-url https://back.mongz.online
MOVIT_API_BASE_URL=http://10.0.2.2:4000 node scripts/generate-cold-offline-bundle.mjs
```

## What the script does

1. `GET /api/mobile/sync` against the configured API base URL.
2. Maps the catalog slice into frozen `ExploreDataDto` shape (same rules as `SyncCatalogMapper.kt`).
3. Copies active `systemMessages` (`code`, `content`, `updatedAt`) from the sync payload.
4. Sets `home` to `null` — home dashboard is per-user and must not be fabricated in the bundle.

## When to run

- After publishing new programs/workouts/exercises or system message template changes on the backend.
- Before release builds when catalog content should match production.
- In CI (optional): fail the build if the committed bundle drifts from staging — not wired yet.

## Fallback (no backend)

If the API is unreachable, ship an honest empty bundle manually:

```json
{
  "home": null,
  "explore": {
    "levels": [],
    "programs": [],
    "workoutTemplates": [],
    "exercises": [],
    "deletedProgramIds": [],
    "deletedWorkoutTemplateIds": [],
    "deletedExerciseIds": []
  },
  "systemMessages": []
}
```

First-launch UI shows empty states until the user syncs once online.
