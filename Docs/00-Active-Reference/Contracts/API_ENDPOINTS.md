# Movit REST API

| | |
|---|---|
| **Status** | `ACTIVE` |
| **SSOT for** | All NestJS REST routes registered in `app.module.ts` |
| **Code** | `backend/src/modules/**/*.controller.ts`, `backend/src/app.module.ts` |
| **Supersedes** | `backend/API_ENDPOINTS.md` (redirect stub) |
| **Verified** | 2026-06-22 |

**Base URL:** `/api` (global prefix in `main.ts`)

**Interactive docs:** Swagger UI at `/api/docs` — OpenAPI generated from controllers at runtime.

**Route count:** 249 (verified from registered controllers)

**Auth:** Admin routes use session cookies or bearer JWT + CASL permissions. Mobile routes use bearer JWT (`Authorization: Bearer <accessToken>`).

**Naming:** Training domain terms follow [Workout-Domain-Naming.md](Workout-Domain-Naming.md) (`PlannedWorkout`, `WorkoutTemplate`, `WorkoutExecution`).

---

## Contents

1. [Admin Auth](#admin-auth) (18)
2. [Admin Content](#admin-content) (116)
3. [Admin Analytics](#admin-analytics) (19)
4. [Mobile Auth](#mobile-auth) (13)
5. [Mobile Journey](#mobile-journey) (41)
6. [Mobile Training](#mobile-training) (18)
7. [Payments](#payments) (20)
8. [System](#system) (4)

---

## Admin Auth

### Admin session

| Method | Path | Purpose |
|--------|------|--------|
| POST | `/admin/auth/login` | Admin login (session cookies) |
| POST | `/admin/auth/logout` | Admin logout |
| GET | `/admin/auth/profile` | Current admin profile |
| PUT | `/admin/auth/profile` | Update admin profile |
| POST | `/admin/auth/request-reset` | Request password reset email |
| POST | `/admin/auth/reset-password` | Reset password with token |

### Admins

| Method | Path | Purpose |
|--------|------|--------|
| GET | `/admins` | List admins (search, status, pagination) |
| POST | `/admins` | Create admin |
| GET | `/admins/:id` | Admin details |
| PUT | `/admins/:id` | Update admin |
| DELETE | `/admins/:id` | Soft-delete admin |
| PUT | `/admins/:id/password` | Change admin password |

### Permissions & roles

| Method | Path | Purpose |
|--------|------|--------|
| GET | `/admin/permissions` | List all permissions |
| GET | `/admin/permissions/roles` | List roles |
| POST | `/admin/permissions/roles` | Create role |
| GET | `/admin/permissions/roles/:id` | Role with assigned permissions |
| PUT | `/admin/permissions/roles/:id` | Update role name/permissions |
| DELETE | `/admin/permissions/roles/:id` | Delete role |

## Admin Content

### Exercises

| Method | Path | Purpose |
|--------|------|--------|
| GET | `/exercises` | List exercises (search, filters, pagination) |
| POST | `/exercises` | Create exercise |
| GET | `/exercises/:id` | Exercise details |
| PUT | `/exercises/:id` | Update exercise |
| DELETE | `/exercises/:id` | Delete exercise |
| GET | `/exercises/:id/config` | Android-compatible exercise config JSON |
| PUT | `/exercises/:id/publish` | Publish exercise |
| DELETE | `/exercises/:id/publish` | Unpublish exercise |
| GET | `/exercises/:id/substitutions` | Admin substitution mappings |
| POST | `/exercises/bulk/delete` | Bulk delete exercises |
| POST | `/exercises/bulk/unpublish` | Bulk unpublish exercises |
| GET | `/exercises/published` | List published exercises only |

### Workout templates

| Method | Path | Purpose |
|--------|------|--------|
| GET | `/workout-templates` | List workout templates |
| POST | `/workout-templates` | Create workout template |
| GET | `/workout-templates/:id` | Template details with exercises |
| PUT | `/workout-templates/:id` | Update template |
| DELETE | `/workout-templates/:id` | Delete template |
| POST | `/workout-templates/:id/duplicate` | Duplicate template |
| POST | `/workout-templates/:id/publish` | Publish template |
| DELETE | `/workout-templates/:id/publish` | Unpublish template |

### Workout phases

| Method | Path | Purpose |
|--------|------|--------|
| GET | `/workout-phases` | List workout phases |
| POST | `/workout-phases` | Create workout phase |
| GET | `/workout-phases/:id` | Workout phase details |
| PUT | `/workout-phases/:id` | Update workout phase |
| DELETE | `/workout-phases/:id` | Delete workout phase |

### Programs

| Method | Path | Purpose |
|--------|------|--------|
| GET | `/programs` | List programs (admin) |
| POST | `/programs` | Create program |
| GET | `/programs/:id` | Program with weeks, days, planned workouts |
| PUT | `/programs/:id` | Update program metadata |
| DELETE | `/programs/:id` | Delete program |
| POST | `/programs/:id/duplicate` | Duplicate program |
| POST | `/programs/:id/publish` | Publish program |
| DELETE | `/programs/:id/publish` | Unpublish program |
| POST | `/programs/:id/weeks` | Add week |
| PUT | `/programs/:id/weeks/:weekId` | Update week |
| DELETE | `/programs/:id/weeks/:weekId` | Delete week |
| POST | `/programs/:id/weeks/:weekId/copy-to/:targetWeek` | Copy week structure |
| PUT | `/programs/:programId/planned-workouts/:plannedWorkoutId` | Update planned workout |
| DELETE | `/programs/:programId/planned-workouts/:plannedWorkoutId` | Delete planned workout |
| POST | `/programs/:programId/planned-workouts/:plannedWorkoutId/import-workout-template/:workoutTemplateId` | Import workout template into planned workout |
| POST | `/programs/:programId/planned-workouts/:plannedWorkoutId/items` | Add item to planned workout |
| PUT | `/programs/:programId/planned-workouts/:plannedWorkoutId/items/:itemId` | Update planned workout item |
| DELETE | `/programs/:programId/planned-workouts/:plannedWorkoutId/items/:itemId` | Delete planned workout item |
| POST | `/programs/:programId/weeks/:weekId/days` | Add day to week |
| PUT | `/programs/:programId/weeks/:weekId/days/:dayId` | Update day |
| DELETE | `/programs/:programId/weeks/:weekId/days/:dayId` | Delete day |
| POST | `/programs/:programId/weeks/:weekId/days/:dayId/planned-workouts` | Add planned workout to day |
| GET | `/programs/map` | Program ID/slug map |

### Messages & TTS

| Method | Path | Purpose |
|--------|------|--------|
| GET | `/messages` | List voice/UI messages |
| POST | `/messages` | Create message |
| GET | `/messages/:id` | Message details |
| PUT | `/messages/:id` | Update message |
| DELETE | `/messages/:id` | Delete message |
| POST | `/messages/ai/translate` | AI translate message text |
| POST | `/messages/ai/tts` | Generate TTS for message |
| DELETE | `/messages/ai/tts` | Clear TTS cache |
| POST | `/messages/bulk-audio` | Generate bulk TTS audio |
| POST | `/messages/bulk-audio/preview` | Preview bulk TTS generation |

### Attributes

| Method | Path | Purpose |
|--------|------|--------|
| GET | `/attributes` | List attribute types |
| POST | `/attributes` | Create attribute type |
| GET | `/attributes/:code/values` | Values for attribute code (e.g. muscles) |
| POST | `/attributes/:code/values` | Add value to attribute type |
| GET | `/attributes/:id` | Attribute type details |
| PUT | `/attributes/:id` | Update attribute type |
| DELETE | `/attributes/:id` | Delete attribute type |
| GET | `/attributes/lookup` | All lookup data for admin dropdowns |
| PUT | `/attributes/values/:id` | Update attribute value |
| DELETE | `/attributes/values/:id` | Delete attribute value |

### Pose positions

| Method | Path | Purpose |
|--------|------|--------|
| GET | `/pose-positions` | List pose/camera positions (`/camera-positions` alias) |
| GET | `/pose-positions/:id` | Pose position details |
| PUT | `/pose-positions/:id` | Update pose position |

### App users

| Method | Path | Purpose |
|--------|------|--------|
| GET | `/users` | List app users |
| POST | `/users` | Create user manually |
| PUT | `/users/:id` | Update user (incl. Pro status) |
| DELETE | `/users/:id` | Delete user |

### Uploads

| Method | Path | Purpose |
|--------|------|--------|
| POST | `/uploads` | Upload file (image/audio) |
| DELETE | `/uploads` | Delete uploaded file |

### AI utilities

| Method | Path | Purpose |
|--------|------|--------|
| POST | `/ai/translate` | Translate text (admin AI) |
| POST | `/ai/tts` | Generate TTS audio |
| DELETE | `/ai/tts` | Clear TTS cache |
| GET | `/ai/tts/config` | TTS provider configuration |

### Assessment templates

| Method | Path | Purpose |
|--------|------|--------|
| GET | `/admin/assessment-templates` | List assessment templates |
| POST | `/admin/assessment-templates` | Create assessment template |
| GET | `/admin/assessment-templates/:id` | Assessment template details |
| PUT | `/admin/assessment-templates/:id` | Update assessment template |
| DELETE | `/admin/assessment-templates/:id` | Delete assessment template |
| POST | `/admin/assessment-templates/:id/exercises` | Add exercise to template |
| PUT | `/admin/assessment-templates/:id/exercises/:entryId` | Update template exercise entry |
| DELETE | `/admin/assessment-templates/:id/exercises/:entryId` | Remove exercise from template |
| PUT | `/admin/assessment-templates/:id/exercises/reorder` | Reorder template exercises |
| POST | `/admin/assessment-templates/:id/publish` | Publish assessment template |
| DELETE | `/admin/assessment-templates/:id/publish` | Unpublish assessment template |

### Levels

| Method | Path | Purpose |
|--------|------|--------|
| GET | `/admin/levels` | List training levels |
| POST | `/admin/levels` | Create level |
| PUT | `/admin/levels/:id` | Update level |
| DELETE | `/admin/levels/:id` | Delete level |
| PUT | `/admin/levels/reorder` | Reorder levels |

### Exercise progression

| Method | Path | Purpose |
|--------|------|--------|
| GET | `/admin/exercise-families` | List exercise families |
| GET | `/admin/exercise-families/:familyKey` | Family details |
| PATCH | `/admin/exercise-families/:familyKey/order` | Reorder family |
| PATCH | `/admin/exercise-families/:familyKey/rename` | Rename family |
| GET | `/admin/exercise-progression/:exerciseId` | Exercise progression profile |
| PUT | `/admin/exercise-progression/:exerciseId` | Update progression profile |
| PUT | `/admin/exercise-progression/:exerciseId/archetype` | Set progression archetype |
| POST | `/admin/exercise-progression/:exerciseId/generate` | Auto-generate progression profile |
| GET | `/admin/exercise-progression/:exerciseId/validate` | Validate progression profile |
| GET | `/admin/exercise-progression/archetypes` | Progression archetypes |
| POST | `/admin/exercise-progression/bulk-generate` | Bulk generate profiles |
| GET | `/admin/exercise-progression/exercises` | Exercises with progression profiles |
| GET | `/admin/exercise-progression/profiles` | All progression profiles |

### Progression rules

| Method | Path | Purpose |
|--------|------|--------|
| GET | `/admin/progression-rules` | List progression rules |
| POST | `/admin/progression-rules` | Create progression rule |
| GET | `/admin/progression-rules/:id` | Progression rule details |
| PUT | `/admin/progression-rules/:id` | Update progression rule |
| DELETE | `/admin/progression-rules/:id` | Delete progression rule |
| PUT | `/admin/progression-rules/:id/toggle` | Enable/disable rule |

## Admin Analytics

### Dashboards & reports

| Method | Path | Purpose |
|--------|------|--------|
| GET | `/admin/analytics/activation` | Activation funnel metrics |
| GET | `/admin/analytics/assessments` | Assessment completion analytics |
| GET | `/admin/analytics/content` | Content usage analytics |
| GET | `/admin/analytics/level-transitions` | Level transition analytics |
| GET | `/admin/analytics/levels` | Level distribution analytics |
| GET | `/admin/analytics/overview` | Platform overview KPIs |
| GET | `/admin/analytics/platform` | Platform-wide summary |
| GET | `/admin/analytics/programs` | Program catalog analytics |
| GET | `/admin/analytics/programs/:id` | Single program analytics |
| GET | `/admin/analytics/progression` | Progression analytics |
| GET | `/admin/analytics/retention` | Retention cohorts |
| GET | `/admin/analytics/revenue` | Subscription revenue |
| GET | `/admin/analytics/rules` | Progression rule analytics |
| GET | `/admin/analytics/safety` | Safety incident metrics |
| GET | `/admin/analytics/training` | Training activity metrics |
| GET | `/admin/analytics/user-trends` | User trend time series |
| GET | `/admin/analytics/users` | User growth and engagement |
| GET | `/admin/analytics/users/:id/report` | Per-user analytics report |
| GET | `/admin/analytics/workout-executions/:id/report` | Workout execution detail report |

## Mobile Auth

### Mobile session

| Method | Path | Purpose |
|--------|------|--------|
| DELETE | `/mobile/auth/account` | Permanently delete account |
| POST | `/mobile/auth/change-password` | Change password |
| POST | `/mobile/auth/forgot-password` | Request password reset email |
| POST | `/mobile/auth/google` | Google social login/register |
| POST | `/mobile/auth/login` | Login with email/password |
| POST | `/mobile/auth/logout` | Logout (revoke refresh token) |
| DELETE | `/mobile/auth/logout` | Logout all devices |
| GET | `/mobile/auth/profile` | Current user profile |
| PATCH | `/mobile/auth/profile` | Update profile (name, avatar) |
| POST | `/mobile/auth/refresh` | Refresh access token |
| POST | `/mobile/auth/register` | Register new user |
| POST | `/mobile/auth/reset-password` | Reset password with token |
| PATCH | `/mobile/auth/settings` | Update app settings (language, voice) |

## Mobile Journey

### Home & sync

| Method | Path | Purpose |
|--------|------|--------|
| GET | `/mobile/explore` | Explore screen data |
| GET | `/mobile/home` | Home screen (today workout, recent executions) |
| GET | `/mobile/sync` | Full mobile sync payload |

### Programs & active plan

| Method | Path | Purpose |
|--------|------|--------|
| GET | `/mobile/plan` | Active plan summary |
| POST | `/mobile/plan/complete` | Complete active plan milestone |
| POST | `/mobile/plan/enroll` | Enroll in active plan flow |
| GET | `/mobile/plan/enrollment-check` | Check enrollment eligibility |
| GET | `/mobile/plan/today` | Today's plan slice |
| GET | `/mobile/programs` | Published programs catalog |
| GET | `/mobile/programs/:id` | Program details |
| POST | `/mobile/programs/:id/enroll` | Enroll in program |
| GET | `/mobile/programs/:id/preview` | Program preview before enroll |
| PUT | `/mobile/user-programs/:id` | Update enrollment settings |
| POST | `/mobile/user-programs/:id/complete` | Mark program complete |
| GET | `/mobile/user-programs/:id/effective-plan` | Merged plan with overrides |
| GET | `/mobile/user-programs/:id/overrides` | List day overrides |
| POST | `/mobile/user-programs/:id/overrides` | Create day override |
| DELETE | `/mobile/user-programs/:id/overrides/:overrideId` | Delete day override |
| GET | `/mobile/user-programs/:id/progress-metrics` | Enrollment progress metrics |
| GET | `/mobile/user-programs/:id/today` | Today's planned workouts |

### Assessment

| Method | Path | Purpose |
|--------|------|--------|
| POST | `/assessment` | Submit assessment session |
| GET | `/assessment/:id` | Assessment session details |
| DELETE | `/assessment/:id` | Delete assessment session |
| GET | `/assessment/history` | Assessment history |
| GET | `/assessment/latest` | Latest assessment result |
| GET | `/assessment/progress` | Assessment progress summary |
| GET | `/mobile/assessment-templates/resolve` | Resolve assessment template for user |

### Levels & reassessment

| Method | Path | Purpose |
|--------|------|--------|
| GET | `/mobile/level-profile` | Current user level profile |
| GET | `/mobile/level-profile/history` | Level change history |
| GET | `/mobile/level-profile/levels` | Available levels reference |
| GET | `/mobile/reassessment/history` | Reassessment history |
| POST | `/mobile/reassessment/request` | Request reassessment |
| GET | `/mobile/reassessment/upcoming` | Upcoming reassessment |

### Prescription

| Method | Path | Purpose |
|--------|------|--------|
| POST | `/mobile/prescription/recommend` | Recommend program from assessment |

### Profile & preferences

| Method | Path | Purpose |
|--------|------|--------|
| GET | `/mobile/exercise-preferences` | User exercise preferences |
| PUT | `/mobile/exercise-preferences/:exerciseId` | Set exercise preference |
| DELETE | `/mobile/exercise-preferences/:exerciseId` | Clear exercise preference |
| GET | `/mobile/training-profile` | User training profile |
| PUT | `/mobile/training-profile` | Update training profile |

### Reports

| Method | Path | Purpose |
|--------|------|--------|
| GET | `/mobile/reports/dashboard` | User dashboard report |
| GET | `/mobile/reports/metrics` | User metrics report |

## Mobile Training

### Workout executions & planned workouts

| Method | Path | Purpose |
|--------|------|--------|
| POST | `/mobile/planned-workouts/:id/complete` | Complete planned workout |
| POST | `/mobile/planned-workouts/:id/report` | Complete planned workout (legacy alias) |
| POST | `/mobile/planned-workouts/:id/start` | Start planned workout |
| GET | `/mobile/workout-executions` | List execution history |
| POST | `/mobile/workout-executions` | Upload single exercise execution |
| GET | `/mobile/workout-executions/:exerciseId` | Per-exercise history and aggregates |
| POST | `/mobile/workout-executions/explore` | Upload explore/quick-start workout |
| GET | `/mobile/workout-executions/stats` | Aggregate execution stats |

### Templates & exercise assets

| Method | Path | Purpose |
|--------|------|--------|
| GET | `/mobile/exercises/:slug/audio-manifest` | Exercise audio manifest |
| GET | `/mobile/exercises/substitutions` | User exercise substitutions |
| GET | `/mobile/workout-templates` | Sync workout template catalog |
| GET | `/mobile/workout-templates/:id/training-config` | Training engine config JSON |
| GET | `/mobile/workout-templates/:slug/audio-manifest` | Template audio manifest |

### Progression

| Method | Path | Purpose |
|--------|------|--------|
| GET | `/mobile/progression/history` | Progression change history |
| POST | `/mobile/progression/mark-seen` | Mark progression notification seen |
| GET | `/mobile/progression/planned-workout/:plannedWorkoutId` | Progression for completed block |
| GET | `/mobile/progression/recent` | Recent progression events |
| GET | `/mobile/progression/session/:sessionId` | Progression for assessment session |

## Payments

### Subscription plans

| Method | Path | Purpose |
|--------|------|--------|
| GET | `/admin/plans` | List subscription plans |
| POST | `/admin/plans` | Create subscription plan |
| GET | `/admin/plans/:id` | Plan details |
| PATCH | `/admin/plans/:id` | Update plan |
| DELETE | `/admin/plans/:id` | Delete plan |

### Subscriptions (admin)

| Method | Path | Purpose |
|--------|------|--------|
| GET | `/admin/subscriptions` | List subscriptions |
| POST | `/admin/subscriptions` | Create manual subscription |
| GET | `/admin/subscriptions/:id` | Subscription details |
| PATCH | `/admin/subscriptions/:id` | Update subscription |
| DELETE | `/admin/subscriptions/:id` | Delete subscription |

### Subscriptions & billing

| Method | Path | Purpose |
|--------|------|--------|
| GET | `/mobile/plans` | Active subscription plans (mobile) |
| POST | `/mobile/subscriptions/app-store/verify` | Verify App Store purchase |
| POST | `/mobile/subscriptions/cancel` | Cancel subscription |
| POST | `/mobile/subscriptions/checkout` | Start MyFatoorah checkout |
| GET | `/mobile/subscriptions/checkout/:id` | Checkout session status |
| POST | `/mobile/subscriptions/google-play/verify` | Verify Google Play purchase |
| GET | `/mobile/subscriptions/mine` | Current subscription |
| GET | `/mobile/subscriptions/status` | Subscription status check |
| GET | `/payments/myfatoorah/subscriptions/result` | MyFatoorah payment return URL |
| POST | `/payments/myfatoorah/subscriptions/webhook` | MyFatoorah webhook handler |

## System

### Health & settings

| Method | Path | Purpose |
|--------|------|--------|
| GET | `/` | Health check |
| GET | `/admin/system` | System settings |
| PUT | `/admin/system` | Bulk update settings |
| PUT | `/admin/system/:key` | Update single setting |

---

## Maintenance

Verify route inventory:

```bash
cd backend && node scripts/extract-routes.mjs
node scripts/generate-api-doc.mjs
```

When adding or removing controllers, update `app.module.ts` and re-run the generator. Cancelled features (booking, doctor schedules, Google Meet) are **not** registered — no routes documented.

