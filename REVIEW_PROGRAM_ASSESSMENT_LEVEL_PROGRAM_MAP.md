# Program / Assessment / Level / Program Map Review

Date: 2026-06-07

Scope:
- `backend/`
- `Admin-Dashboard/`
- `kmp-app/`
- `backend/prisma/schema.prisma`

## Summary

The core schema and backend domain structure are generally coherent:

- `Program` has `levelMinId` / `levelMaxId` and sequence fields `nextProgramId` / `prerequisiteProgramId`.
- `AssessmentTemplate` targets a `Level`.
- `BodyScanResult` links to the resulting `Level` and the used `AssessmentTemplate`.
- `UserLevelProfile` is calculated after assessments.
- `ActivePlan` / `ActivePlanProgram` represents the actual user-specific ordered program queue.

However, there are several contract and logic issues that can make the Admin Program Map display incorrectly, cause mobile to show a different training day than the backend, or enroll users into programs that mobile cannot sync.

## Findings

### P1 - Program Map uses `level.order`, but backend returns `level.number`

Files:
- `Admin-Dashboard/src/app/admin/programs/map/page.tsx`
- `backend/src/modules/levels/levels-admin.service.ts`
- `Admin-Dashboard/src/app/admin/levels/page.tsx`

Problem:
- Backend `Level` model and `/admin/levels` API use `number`.
- The Levels page correctly sorts and displays levels by `number`.
- Program Map defines:
  - `interface Level { order: number }`
  - sorts by `a.order - b.order`
  - compares `level.order` against program level ranges
  - builds create URLs from `level.order`
- Because `order` is not returned by the backend, the Program Map can produce invalid sorting and fail to place programs/assessments into columns correctly.

Impact:
- Programs may not appear in the correct level columns.
- Assessment templates may not appear under their target level.
- `Create here` can pass `levelRangeMin=undefined`.
- Program Map cannot be trusted as a planning view until fixed.

Required fix:
- Replace `order` with `number` in Program Map.
- Update helper names if desired:
  - `targetLevelOrder` -> `targetLevelNumber`
  - `assessmentMatchesLevelColumn(... levelNumber ...)`
- Sort levels by `number`.
- Use `level.number` in:
  - `programsByLevelColumn`
  - `assessmentsByLevelColumn`
  - `createHereHref`
  - labels/fallbacks

Suggested verification:
- Open `/admin/programs/map`.
- Confirm each level column appears in numeric order.
- Confirm programs show in columns matching `levelRangeMin` / `levelRangeMax`.
- Confirm progression/post-program assessment templates show under `targetLevel.number`.
- Confirm `Create here` creates URL with valid numeric level range.

---

### P1 - Android Train screen calculates current program day differently from backend

Files:
- `kmp-app/app/src/main/java/com/trainingvalidator/poc/ui/train/TrainFragment.kt`
- `kmp-app/app/src/main/java/com/trainingvalidator/poc/storage/ProgramDayCalculator.kt`
- `backend/src/modules/active-plan/plan-position.ts`
- `backend/src/modules/mobile-sync/mobile-home.controller.ts`
- `backend/src/modules/active-plan/active-plan.service.ts`

Problem:
- Backend current-day logic is completion-based and considers:
  - completed planned workouts
  - user training weekdays
  - catch-up snap after missed days
  - template rest/off-schedule days
- Android `TrainFragment` uses `ProgramDayCalculator.getCurrentDay`, which is date-based:
  - `dayIndex = floor(today - startDate)`
  - assumes `durationWeeks * 7`
  - does not use server progress/catch-up logic
- This can diverge from `/api/mobile/home` and `/api/mobile/plan/today`.

Impact:
- Home may say the user should train week/day X, while Train tab opens week/day Y.
- Off-schedule days can still show training in Train tab.
- Programs with fewer than 7 real training slots per week can be treated incorrectly.
- Catch-up behavior from backend is not reflected locally.

Required fix options:

Option A, preferred:
- Make Train tab consume backend `trainMode` or `/mobile/plan/today` as the source of truth.
- Use server-provided:
  - current program
  - week number
  - day number
  - rest/active/program_complete state
  - planned workout id

Option B:
- Port backend `resolveTrainingPositionMeta` logic into Android exactly.
- Feed it local synced progress and training weekdays.
- Keep parity tests between TS and Kotlin.

Suggested verification:
- User with training weekdays excluding today should see rest/off-schedule in both Home and Train.
- User who missed 3+ days should see same catch-up week/day in Home and Train.
- Completed all planned workouts for a day should advance consistently.
- Completed all program slots should show program complete consistently.

---

### P1 - `nextProgramId` can enroll into draft or soft-deleted programs

Files:
- `backend/src/modules/programs/program-completion.service.ts`
- `backend/src/modules/active-plan/active-plan.service.ts`
- `backend/src/modules/programs/programs.service.ts`
- `backend/src/modules/programs/mobile-programs.controller.ts`

Problem:
- Completion service returns `program.nextProgramId` directly.
- `activePlanService.completeActiveProgram` then calls `enrollProgram` with that id.
- `activePlanService.enrollProgram` does not validate that the target program is:
  - published
  - not soft-deleted
- Mobile sync only sends programs where:
  - `isPublished: true`
  - `deletedAt: null`

Impact:
- User can become active on a program that mobile cannot download from sync.
- Active plan can reference unavailable content.
- Program chain in Program Map can look valid but break at runtime.

Required fix:
- Validate next/prerequisite program references on update/publish.
- Before enrolling from `nextProgramId`, confirm target program exists, is published, and not deleted.
- Consider rejecting publish if `nextProgramId` points to an unpublished/deleted program.
- Consider excluding self references and cycle chains.

Suggested verification:
- Create program A with `nextProgramId` pointing to draft program B.
- Try publishing A or completing A.
- Expected: backend rejects with clear validation error.
- Confirm valid published B still transitions correctly.

---

### P2 - Assessment upload sends legacy `fitnessLevel`; backend maps it with fixed 1-5 logic

Files:
- `kmp-app/app/src/main/java/com/trainingvalidator/poc/assessment/AssessmentUploadService.kt`
- `kmp-app/app/src/main/java/com/trainingvalidator/poc/assessment/engine/LevelThresholdsManager.kt`
- `backend/src/modules/assessment/assessment.service.ts`
- `backend/src/lib/metrics/metrics-contract.ts`

Problem:
- Android computes dynamic thresholds locally but uploads only `fitnessLevel`.
- Backend does:
  - if `fitnessLevel` exists, use `fitnessLevelToNumber`
  - else use `scoreToLevel(bodyScore)`
- `fitnessLevelToNumber` is hardcoded:
  - excellent -> 5
  - good -> 4
  - average -> 3
  - limited -> 2
  - needs_rehab -> 1
- If levels are configurable or thresholds differ, backend may store the wrong `levelId`.

Impact:
- `BodyScanResult.levelId` can be incorrect.
- `UserLevelProfile.overallLevel` and prescription can diverge from Android classification.
- Program recommendation can target the wrong level range.

Required fix:
- Preferred: Android should upload `levelId` or `levelNumber` from `LevelThresholdsManager.getLevelNumber(bodyScore)`.
- Backend should prefer explicit `levelId` or validated `levelNumber`.
- If no explicit level is provided, backend should use DB-backed dynamic score-to-level logic.
- Avoid letting legacy `fitnessLevel` override dynamic score thresholds.

Suggested verification:
- Change DB level thresholds away from defaults.
- Run assessment with score near boundary.
- Confirm Android, `BodyScanResult.levelId`, `UserLevelProfile.overallLevel`, and prescription all agree.

---

### P2 - Program Map displays sequence arrows but does not validate or visualize actual graph integrity

Files:
- `Admin-Dashboard/src/app/admin/programs/map/page.tsx`
- `Admin-Dashboard/src/app/admin/programs/new/page.tsx`
- `Admin-Dashboard/src/app/admin/programs/[id]/edit/page.tsx`
- `backend/src/modules/programs/programs.service.ts`

Problem:
- Program Map shows an arrow if `nextProgramId` exists.
- It does not visibly indicate:
  - target program name
  - missing/deleted target
  - draft target
  - self-loop
  - cycle
  - mismatch between prerequisite and next chain
- Program editor allows selecting published programs, but backend update accepts IDs without structural validation.

Impact:
- Admins can create graph structures that look okay but are not safe for automatic progression.
- Debugging broken transitions becomes harder.

Required fix:
- Add backend validation for:
  - no self-loop
  - target exists and not deleted
  - target is published if source is being published
  - optional cycle detection
- Improve Program Map UI:
  - show target title on arrow/tooltip
  - flag broken references
  - flag draft references
  - optionally show chain number/order

Suggested verification:
- Try self-loop.
- Try A -> B -> A cycle.
- Try A -> deleted/draft B.
- Confirm UI and backend both report clear errors.

---

### P2 - Program editor only supports a single training level even though backend/schema supports a range

Files:
- `Admin-Dashboard/src/app/admin/programs/new/page.tsx`
- `Admin-Dashboard/src/app/admin/programs/[id]/edit/page.tsx`
- `backend/prisma/schema.prisma`
- `backend/src/modules/programs/programs.types.ts`

Problem:
- Schema and API support `levelMinId` / `levelMaxId` and legacy `levelRangeMin` / `levelRangeMax`.
- Admin editor uses one `trainingLevel` and stores `min=max`.
- On edit, if a program has a real range, UI rounds midpoint and saves back as a single level.

Impact:
- Existing ranged programs can be narrowed accidentally when edited.
- Auto-assignment loses intended level range coverage.
- Program Map may show ranges from backend, but editor cannot preserve them.

Required fix:
- Add min/max level controls in Program editor.
- Preserve existing range on edit.
- Validate min <= max.
- Optionally keep a "single level" shortcut that sets min=max.

Suggested verification:
- Create or seed a program with L2-L4.
- Open edit page and save without changes.
- Confirm it remains L2-L4.

---

### P3 - Legacy endpoint `/mobile/user-programs/:id/complete` returns decision only

Files:
- `backend/src/modules/programs/mobile-user-programs.controller.ts`
- `backend/src/modules/active-plan/active-plan.controller.ts`
- `kmp-app/app/src/main/java/com/trainingvalidator/poc/network/MobileSyncApi.kt`

Problem:
- Main Android flow uses `/mobile/plan/complete`, which correctly mutates active plan.
- Legacy endpoint `/mobile/user-programs/:id/complete` still exists and only calls `programCompletionService.evaluate`.
- It does not mark the current program completed or activate the next program.

Impact:
- Older clients or future accidental usage can think completion happened when it did not.
- Duplicate complete endpoints are confusing.

Required fix:
- Deprecate/remove legacy endpoint, or make it call the same mutation path as `/mobile/plan/complete`.
- Remove unused Android API method if no longer needed.

Suggested verification:
- Search Android code after cleanup.
- Confirm only one active completion endpoint is used.

## Database Relationship Review

Current schema is mostly sound:

- `Program.levelMinId` / `Program.levelMaxId` -> `Level.id`
- `Program.nextProgramId` and `Program.prerequisiteProgramId` self-reference `Program`
- `AssessmentTemplate.targetLevelId` -> `Level.id`
- `AssessmentTemplateExercise.templateId` -> `AssessmentTemplate.id`
- `BodyScanResult.levelId` -> `Level.id`
- `BodyScanResult.templateId` -> `AssessmentTemplate.id`
- `UserLevelProfile.assessmentId` is unique, one profile per assessment
- `ActivePlan.userId` is unique, one active plan container per user
- `ActivePlanProgram.userProgramId` links active plan slots to user enrollments

Suggested DB improvements:
- Add indexes for `Program.nextProgramId` and `Program.prerequisiteProgramId` if graph traversal grows.
- Consider DB-level or service-level constraints for no self-loop.
- Keep graph validation in service layer for cycle/published/deleted checks.

## Tests Run During Review

Backend targeted tests:

```bash
npm test -- --runInBand programs/program-assignment.spec.ts programs/validate-calendar-program-structure.spec.ts active-plan/plan-position.spec.ts
```

Result:

- 3 test suites passed
- 22 tests passed

Type checks:

```bash
npm run typecheck
```

Backend result:

- Passed

Admin Dashboard:

```bash
npx tsc --noEmit
```

Result:

- Passed

Note:
- These checks do not catch runtime contract issues like `level.order` vs `level.number`, because the frontend interface is locally declared and not generated from the backend contract.

## Recommended Fix Order

1. Fix Program Map `order` -> `number`.
2. Add backend validation for `nextProgramId` / `prerequisiteProgramId`.
3. Align Android Train tab with backend current-day source of truth.
4. Fix assessment level upload/classification to use `levelId` or `levelNumber`.
5. Add min/max level controls in Program editor.
6. Remove or align the legacy `/mobile/user-programs/:id/complete` endpoint.

## Suggested Regression Checklist

- Program Map:
  - levels sorted correctly
  - programs appear under expected level range
  - assessments appear under target level
  - create-here URL has numeric range

- Program publish:
  - cannot publish with invalid calendar
  - cannot publish auto-assignable program missing level/domain/goal metadata
  - cannot publish chain pointing to deleted/draft/self target

- Assessment:
  - assessment result stores correct `levelId`
  - latest `UserLevelProfile.overallLevel` matches DB thresholds
  - prescription uses same level as assessment/profile

- Active Plan:
  - completing program A activates valid published program B
  - reassessment gate blocks next program when required
  - mobile sync contains the active program

- Android:
  - Home and Train tab agree on current week/day
  - off-schedule days show rest in both places
  - missed-day catch-up behavior matches backend
  - program complete flow calls `/mobile/plan/complete`

