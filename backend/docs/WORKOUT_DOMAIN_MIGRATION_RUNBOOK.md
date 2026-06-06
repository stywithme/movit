# Workout Domain Migration — Production Runbook

Big-bang rename removing training-domain `Session` terminology. Deploy backend, Admin-Dashboard, and Android together.

## Pre-flight (T-24h)

1. Announce maintenance window (recommended: 30–60 minutes).
2. Verify staging migration on a production snapshot:
   ```bash
   cd backend
   npx prisma migrate deploy
   npx prisma generate
   npm run build
   ```
3. Record row counts:
   ```sql
   SELECT 'workouts' AS t, COUNT(*) FROM workouts
   UNION ALL SELECT 'program_sessions', COUNT(*) FROM program_sessions
   UNION ALL SELECT 'training_sessions', COUNT(*) FROM training_sessions
   UNION ALL SELECT 'program_session_reports', COUNT(*) FROM program_session_reports;
   ```

## Maintenance window

1. **Stop writes**: `pm2 stop pose-backend` (and block mobile traffic if needed).
2. **Backup**:
   ```bash
   pg_dump -Fc -f pose_pre_workout_rename.dump "$DATABASE_URL"
   ```
3. **Apply migration**:
   ```bash
   cd backend && npx prisma migrate deploy
   ```
4. **Verify counts** (table names after migration):
   ```sql
   SELECT 'workout_templates', COUNT(*) FROM workout_templates
   UNION ALL SELECT 'planned_workouts', COUNT(*) FROM planned_workouts
   UNION ALL SELECT 'workout_executions', COUNT(*) FROM workout_executions
   UNION ALL SELECT 'planned_workout_reports', COUNT(*) FROM planned_workout_reports;
   ```
   Counts must match pre-migration equivalents.
5. **Deploy backend** → **Admin-Dashboard** → publish **Android** build.
6. **Smoke tests**:
   - Admin: create Workout Template, add Planned Workout to program, import template.
   - Android: sync, run explore workout, upload execution, complete planned workout.
   - Reports dashboard loads without legacy `session` API keys.

## Rollback

If migration fails before deploy: restore `pose_pre_workout_rename.dump` and redeploy previous app versions.

If migration succeeded but apps broken: fix-forward only — rollback rename SQL is non-trivial; prefer hotfix on new schema.

## API contract summary

| Old | New |
|-----|-----|
| `GET /workouts` | `GET /workout-templates` |
| `GET /mobile/workouts` | `GET /mobile/workout-templates` |
| `POST /mobile/sessions` | `POST /mobile/workout-executions` |
| `POST /mobile/sessions/explore` | `POST /mobile/workout-executions/explore` |
| `POST /mobile/sessions/:id/start` | `POST /mobile/planned-workouts/:id/start` |
| Program day `sessions[]` | `plannedWorkouts[]` |
| Upload `sessionMetrics` | `executionMetrics` |
| Explore payload `sessions[]` | `executions[]` |
| Sync `sessionReports` | `plannedWorkoutReports` |

See `backend/src/domain/workout-contract.ts` for full model mapping.
