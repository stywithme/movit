# Backend Contract Matrix — Legacy · KMP · الحالة

آخر تحديث: **2026-06-10**  
النطاق: Phase Pre-07 · **WS-1**  
المبدأ: **لا تغيير عقد** — KMP يستهلك نفس المسارات/الحقول التي يستهلكها legacy.

## ملخص

| المصدر | العدد |
|--------|------:|
| Legacy Retrofit (`MobileSyncApi` + `AuthApi` + `SubscriptionApi` + `BookingApi`) | **67** |
| KMP `MovitMobileApi` (`MobileApiContractRegistry.kmpCoveredEndpoints`) | **38** |
| مغطّى من legacy في KMP | **36** (+ **1** KMP-only `GET programs/{id}` · + **1** KMP-only `POST workout-executions`) |
| متروك عمداً (ضمن deferred) | **12** |
| مؤجَّل / phantom / parity قراءة | **31** (`deferredEndpoints` — يشمل pause/resume phantom) |

### تصنيف الـ45 الناقصة (قبل WS-1)

| التصنيف | العدد | الوصف |
|---------|------:|-------|
| 🔴 **نُقل الآن (WS-1)** | **7** | مدخلات Phase 07 المباشرة |
| 🟠 **Phase 07 + WS-2** | **9** | كتابات تحتاج Outbox |
| 🟡 **Phase 07 parity قراءة** | **16** | قراءات legacy — تُنقل عند الحاجة في UI |
| ⚪ **متروك عمداً** | **12** | خارج نطاق المنتج الحالي |
| 🔶 **KMP فقط (ليس في legacy Retrofit)** | **1** | `GET programs/{id}` export كامل |

---

## مصفوفة Endpoints

> **Backend controller**: وحدة NestJS الرئيسية · **Legacy**: Retrofit interface · **KMP**: `MovitMobileApi` · **DTO**: تغطية حقول الاستجابة في KMP

### Auth (`AuthApi` · `mobile-auth.controller`)

| Method | Path | Legacy | KMP | DTO | الحالة |
|--------|------|:------:|:---:|-----|--------|
| POST | `api/mobile/auth/register` | ✅ | ✅ `register` | ✅ | ✅ مغطّى |
| POST | `api/mobile/auth/login` | ✅ | ✅ `login` | ✅ | ✅ مغطّى |
| POST | `api/mobile/auth/google` | ✅ | ❌ | — | ⚪ متروك — Google bridge مؤجَّل |
| POST | `api/mobile/auth/refresh` | ✅ | ✅ `refresh` | ✅ | ✅ مغطّى |
| POST | `api/mobile/auth/logout` | ✅ | ✅ `logout` | ✅ | ✅ مغطّى |
| POST | `api/mobile/auth/forgot-password` | ✅ | ✅ `forgotPassword` | ✅ | ✅ مغطّى |
| POST | `api/mobile/auth/reset-password` | ✅ | ❌ | — | ⚪ متروك — لا شاشة reset في KMP بعد |
| GET | `api/mobile/auth/profile` | ✅ | ✅ `fetchAuthProfile` | ✅ | ✅ مغطّى |
| PATCH | `api/mobile/auth/profile` | ✅ | ❌ | — | ⚪ متروك — تعديل الاسم/الصورة مؤجَّل |
| PATCH | `api/mobile/auth/settings` | ✅ | ✅ `updateAuthSettings` | ✅ | ✅ مغطّى |

### Home & Explore

| Method | Path | Legacy | KMP | DTO | الحالة |
|--------|------|:------:|:---:|-----|--------|
| GET | `api/mobile/home` | ✅ | ✅ `fetchHome` | ✅ incl. `workoutTemplateId` · `isTrainingDay` · `catchUpSuggestion` | ✅ مغطّى (WS-1) |
| GET | `api/mobile/explore` | ✅ | ✅ `fetchExplore` | ✅ | ✅ مغطّى |

### Sync (`mobile-sync`)

| Method | Path | Legacy | KMP | DTO | الحالة |
|--------|------|:------:|:---:|-----|--------|
| GET | `api/mobile/sync` | ✅ | ✅ `fetchSync` · `fetchSyncUserPrograms` | ✅ payload كامل + `meta` (WS-1) | ✅ عقد مغطّى — استهلاك عميق WS-3 |
| GET | `@Url` audio download | ✅ streaming | ❌ | — | 🟡 Phase 07 — تنزيل ملفات عبر platform |

### Training — كتلة 🔴 (WS-1)

| Method | Path | Legacy | KMP | DTO | الحالة |
|--------|------|:------:|:---:|-----|--------|
| GET | `api/mobile/workout-templates/{id}/training-config` | ✅ | ✅ `fetchWorkoutTrainingConfig` | ✅ `TrainingConfigApiResponse` (`data`: JsonElement) | ✅ **أُضيف WS-1** |
| GET | `api/mobile/workout-templates/{slug}/audio-manifest` | ✅ | ✅ `fetchWorkoutAudioManifest` | ✅ | ✅ **أُضيف WS-1** |
| GET | `api/mobile/exercises/{slug}/audio-manifest` | ✅ | ✅ `fetchExerciseAudioManifest` | ✅ | ✅ **أُضيف WS-1** |
| POST | `api/mobile/planned-workouts/{id}/start` | ✅ | ✅ `startPlannedWorkout` | ✅ | ✅ **أُضيف WS-1** |
| POST | `api/mobile/planned-workouts/{id}/complete` | ✅ | ✅ `completePlannedWorkout` | ✅ | ✅ **أُضيف WS-1** |
| POST | `api/mobile/planned-workouts/{id}/report` | ✅ | ✅ `reportPlannedWorkout` | ✅ | ✅ **أُضيف WS-1** (legacy compat) |
| POST | `api/mobile/workout-executions` | ✅ OkHttp `WorkoutSyncService` | ✅ `uploadWorkoutExecution` | ✅ `WorkoutExecutionUploadRequestDto` | ✅ **P0** — تمرين مفرد + `executionMetrics` مطلوب |
| POST | `api/mobile/workout-executions/explore` | ✅ | ✅ `uploadExploreWorkout` | ✅ | ✅ **أُضيف WS-1** |

### Plan & Programs

| Method | Path | Legacy | KMP | DTO | الحالة |
|--------|------|:------:|:---:|-----|--------|
| GET | `api/mobile/plan` | ✅ | ✅ `fetchActivePlan` | ✅ | ✅ مغطّى |
| GET | `api/mobile/plan/today` | ✅ | ❌ | — | 🟡 مؤجَّل — `home` يغطي معظم الحالة |
| POST | `api/mobile/plan/enroll` | ✅ | ✅ `enrollProgram` | ✅ | ✅ مغطّى |
| POST | `api/mobile/plan/complete` | ✅ | ✅ `completePlan` | ✅ | ✅ Outbox `PLAN_COMPLETE` |
| GET | `api/mobile/plan/enrollment-check` | ✅ | ❌ | — | 🟡 مؤجَّل — قبل التسجيل في البرنامج |
| POST | `api/mobile/plan/pause` | ✅ Retrofit فقط | ❌ | — | 🔴 **لا route في الباك اند** — مؤجَّل |
| POST | `api/mobile/plan/resume` | ✅ Retrofit فقط | ❌ | — | 🔴 **لا route في الباك اند** — مؤجَّل |
| GET | `api/mobile/programs/{id}` | ❌ Retrofit | ✅ `fetchProgram` | ✅ | 🔶 KMP — يأتي من sync في legacy |
| GET | `api/mobile/programs/{id}/preview` | ✅ | ❌ | — | 🟡 مؤجَّل — شاشة تفاصيل البرنامج |

### User Programs & Customization

| Method | Path | Legacy | KMP | DTO | الحالة |
|--------|------|:------:|:---:|-----|--------|
| PUT | `api/mobile/user-programs/{id}` | ✅ | ✅ `updateUserProgramCustomizations` | ✅ | ✅ مغطّى — Outbox WS-2 |
| GET | `api/mobile/user-programs/{id}/effective-plan` | ✅ | ✅ `fetchEffectivePlan` | ✅ | ✅ مغطّى |
| GET | `api/mobile/user-programs/{id}/overrides` | ✅ | ❌ | — | 🟠 WS-2 |
| POST | `api/mobile/user-programs/{id}/overrides` | ✅ | ✅ `createUserProgramOverride` | ✅ `weekNumber`/`dayNumber`/`overrideType` في جذر body | ✅ **P0** — أُصلح شكل الطلب |
| DELETE | `api/mobile/user-programs/{id}/overrides/{overrideId}` | ✅ | ❌ | — | 🟠 WS-2 |
| GET | `api/mobile/user-programs/{id}/progress-metrics` | ✅ | ✅ `fetchProgramProgressMetrics` | ✅ | ✅ مغطّى |

### Exercise preferences & substitutions

| Method | Path | Legacy | KMP | DTO | الحالة |
|--------|------|:------:|:---:|-----|--------|
| PUT | `api/mobile/exercise-preferences/{exerciseId}` | ✅ | ❌ | — | 🟠 WS-2 Outbox |
| DELETE | `api/mobile/exercise-preferences/{exerciseId}` | ✅ | ❌ | — | 🟠 WS-2 |
| GET | `api/mobile/exercises/substitutions` | ✅ | ✅ `fetchSubstitutionExercises` | ✅ | ✅ مغطّى |
| GET | `api/exercises/{id}/substitutions` | ✅ | ❌ | — | 🟡 مؤجَّل — legacy يستخدم slug endpoint |

### Training profile

| Method | Path | Legacy | KMP | DTO | الحالة |
|--------|------|:------:|:---:|-----|--------|
| GET | `api/mobile/training-profile` | ✅ | ❌ | — | 🟡 مؤجَّل — KMP عنده PUT فقط |
| PUT | `api/mobile/training-profile` | ✅ | ✅ `putTrainingProfile` | ✅ | ✅ مغطّى |

### Assessment & Level

| Method | Path | Legacy | KMP | DTO | الحالة |
|--------|------|:------:|:---:|-----|--------|
| POST | `api/assessment` | ✅ | ❌ | — | 🟡 Phase 07 — Body Scan upload |
| GET | `api/assessment/latest` | ✅ | ❌ | — | 🟡 مؤجَّل |
| GET | `api/assessment/progress` | ✅ | ❌ | — | 🟡 مؤجَّل |
| GET | `api/mobile/assessment-templates/resolve` | ✅ | ❌ | — | 🟡 Phase 07 assessment flow |
| GET | `api/mobile/level-profile` | ✅ | ✅ `fetchLevelProfile` | ✅ | ✅ مغطّى |
| GET | `api/mobile/level-profile/history` | ✅ | ❌ | — | 🟡 مؤجَّل |
| GET | `api/mobile/level-profile/levels` | ✅ | ❌ | — | 🟡 مؤجَّل |
| POST | `api/mobile/prescription/recommend` | ✅ | ❌ | — | 🟡 مؤجَّل — onboarding |
| GET | `api/mobile/reassessment/upcoming` | ✅ | ✅ `fetchUpcomingReassessments` | ✅ | ✅ مغطّى |
| POST | `api/mobile/reassessment/request` | ✅ | ❌ | — | 🟡 مؤجَّل |

### Progression & Stats

| Method | Path | Legacy | KMP | DTO | الحالة |
|--------|------|:------:|:---:|-----|--------|
| GET | `api/mobile/progression/history` | ✅ | ❌ | — | 🟡 مؤجَّل |
| GET | `api/mobile/progression/recent` | ✅ | ❌ | — | 🟡 مؤجَّل |
| GET | `api/mobile/progression/session/{sessionId}` | ✅ | ❌ | — | 🟡 مؤجَّل |
| POST | `api/mobile/progression/mark-seen` | ✅ | ❌ | — | 🟠 WS-2 |
| GET | `api/mobile/workout-executions/stats` | ✅ | ❌ | — | 🟡 مؤجَّل — `home.stats` يغطي جزئياً |

### Reports

| Method | Path | Legacy | KMP | DTO | الحالة |
|--------|------|:------:|:---:|-----|--------|
| GET | `api/mobile/reports/dashboard` | ✅ | ✅ `fetchReportsDashboard` | ✅ | ✅ مغطّى |
| GET | `api/mobile/reports/metrics` | ✅ | ✅ `fetchExerciseMetrics` | ✅ جزئي (scope=exercise) | ✅ مغطّى للاستخدام الحالي |

### Subscriptions (`SubscriptionApi`)

| Method | Path | Legacy | KMP | DTO | الحالة |
|--------|------|:------:|:---:|-----|--------|
| GET | `api/mobile/plans` | ✅ | ❌ | — | ⚪ متروك — StoreKit مخفى |
| GET | `api/mobile/subscriptions/status` | ✅ | ❌ | — | ⚪ متروك |
| GET | `api/mobile/subscriptions/mine` | ✅ | ❌ | — | ⚪ متروك |
| POST | `api/mobile/subscriptions/checkout` | ✅ | ❌ | — | ⚪ متروك |
| GET | `api/mobile/subscriptions/checkout/{id}` | ✅ | ❌ | — | ⚪ متروك |
| POST | `api/mobile/subscriptions/google-play/verify` | ✅ | ❌ | — | ⚪ متروك |
| POST | `api/mobile/subscriptions/cancel` | ✅ | ❌ | — | ⚪ متروك |

### Bookings (`BookingApi`)

| Method | Path | Legacy | KMP | DTO | الحالة |
|--------|------|:------:|:---:|-----|--------|
| GET | `api/bookings/rules` | ✅ | ❌ | — | ⚪ متروك — BookingApi كامل خارج النطاق |

---

## فجوات DTO المغلقة في WS-1

### `GET api/mobile/home`

| الحقل | Backend | Legacy | KMP (قبل) | KMP (بعد WS-1) |
|-------|---------|--------|-----------|----------------|
| `trainMode.todayWorkout.workoutTemplateId` | ✅ | جزئي | ❌ | ✅ `TrainTodayWorkoutDto` |
| `trainMode.isTrainingDay` | ✅ | عبر `home` backend | ❌ | ✅ `TrainModeDto` |
| `trainMode.catchUpSuggestion` | ✅ | ✅ `CatchUpSuggestionData` | ❌ | ✅ `CatchUpSuggestionDto` |

### `GET api/mobile/sync`

| الحقل | Legacy `SyncData` | KMP (قبل) | KMP (بعد WS-1) |
|-------|-------------------|-----------|----------------|
| `exercises` | ✅ | ❌ | ✅ `List<JsonElement>` |
| `messageLibrary` / `systemMessages` | ✅ | ❌ | ✅ typed |
| `workoutTemplates` / `workouts` alias | ✅ | ❌ | ✅ `@JsonNames` |
| `programs` | ✅ | ❌ | ✅ `List<JsonElement>` |
| `deleted*` ids | ✅ | ❌ | ✅ |
| `userPrograms` (حقول كاملة) | ✅ | جزئي (3 حقول) | ✅ 10 حقول |
| `userExercisePreferences` | ✅ | ❌ | ✅ |
| `plannedWorkoutReports` | ✅ | ❌ | ✅ `@SerialName` |
| `audioManifest` | ✅ | ❌ | ✅ |
| `meta` + `timestamp` | ✅ | ❌ | ✅ `SyncMetaDto` |

> **ملاحظة:** `exercises` / `programs` / `workoutTemplates` كـ `JsonElement` — يفكّك العقد الكامل بلا فقد؛ typed models للمحرك تُعرَّف في WS-5/Phase 07.

---

## قرارات مهمة

1. **لا تغيير عقد:** كل المسارات والحقول تطابق backend + legacy؛ لا حقول جديدة على الباك اند.
2. **`training-config`:** الاستجابة معقّدة (pose variants · checks · messages) — `data` كـ `JsonElement` حتى يستهلكها `core:training-engine` في Phase 07 بلا تكرار schema.
3. **`/report` vs `/complete`:** legacy يستخدم `/complete` أساساً؛ `/report` موجود للتوافق — كلاهما في KMP.
4. **Sync consumption:** DTO كامل في WS-1؛ orchestration (`SyncManager` parity) يبقى WS-3.
5. **Outbox:** كتابات `planned-workouts` · `plan/complete` · `preferences` — WS-2. **`plan/pause|resume` خارج Outbox** (لا backend route).
6. **pause/resume phantom:** `MobileSyncApi` (legacy) يحتفظ بالتوقيع؛ KMP + Outbox لا يستدعيان مساراً غير موجود في NestJS.

---

## مراجع الكود

- Legacy: `android-poc/app/.../network/MobileSyncApi.kt` · `AuthApi.kt` · `SubscriptionApi.kt` · `BookingApi.kt`
- KMP: `android-poc/core/network/.../MovitMobileApi.kt`
- DTOs: `dto/HomeDto.kt` · `dto/PlanSyncDto.kt` · `dto/TrainingApiDto.kt`
- اختبارات: `MovitMobileApiContractTest.kt`
