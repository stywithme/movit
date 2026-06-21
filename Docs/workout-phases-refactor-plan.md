# Workout Template Refactor — Phases, Components & 2-Pane Editor

> **Status:** Plan / Proposal · **Date:** 2026-06-07 · **Owner:** Mahmoud
> **Scope:** DB (Prisma/Postgres) · Backend (NestJS) · Admin-Dashboard (Next.js) · Android (Kotlin)

---

## 0. خلاصة تنفيذية (Executive Summary)

الهدف: إعادة هيكلة كيان الـ **Workout Template** بحيث يتكوّن من **Phases** (مراحل: Warm-up / Main / Cool-down …)، وكل Phase تحتوي **مكوّنات بالترتيب** (Exercises + Rest)، مع **صفحة محرّر جديدة بقسمين** (قائمة المكوّنات + محرّر للمكوّن المُختار)، و **صفحة CRUD مستقلة** لإدارة الـ Phases كـ **catalog قابل لإعادة الاستخدام**.

### القرارات المحسومة
| القرار | الاختيار | الأثر |
|---|---|---|
| **نطاق الـ Phase** | **Reusable catalog** | جدول `WorkoutPhase` مركزي + جدول ربط `WorkoutTemplatePhase` بـ overrides اختيارية |
| **تخزين الـ Rest** | **Keep fields** | نسيب `restBetweenSetsMs` / `restAfterExerciseMs` على التمرين زي ما هي؛ الـ Rest يظهر كـ "كتلة افتراضية" في المحرّر فقط |
| **ترحيل الـ 7 templates** | **Auto-wrap in Main** | كل تمارين كل workout تتلفّ أوتوماتيك في phase واحدة `MAIN` |

### الاكتشاف المعماري الأهم
الـ **infrastructure موجودة بالفعل** في طبقة الـ **Programs** ويمكن إعادة استخدامها:
- `WorkoutBlockRole` enum (`WARMUP, ACTIVATION, MAIN, ACCESSORY, CORRECTIVE, COOLDOWN, TEST`) — هنعيد استخدامه كـ `role` للـ Phase بدل اختراع enum جديد.
- `PlannedWorkout` + `PlannedWorkoutItem` (بـ `type`) — نمط الـ heterogeneous line items.
- الموبايل: `WorkoutTrainingEngine` بيستهلك `WorkoutLineItem(type=exercise|rest)` + `plannedWorkoutRole` **بالفعل**، وبيتعامل مع warmup/cooldown roles.

**الخلاصة:** الجزء الجديد فعلاً هو **كيان الـ Phase بإعداداته** (`canSkip` / `canContinue` / `maxContinueTime`) + **الـ 2-pane editor**. باقي البنية امتداد لنمط قائم.

---

## 1. الوضع الحالي (Current State Audit)

### 1.1 قاعدة البيانات
```
WorkoutTemplate (flat)
  └─ WorkoutTemplateExercise[]   ← تمارين فقط، rest مخبّأ كحقول
       • variantIndex, difficulty, targetReps/targetDuration, sets
       • restBetweenSetsMs, restAfterExerciseMs   ← الـ rest الحالي
       • weightKg, weightPerSet, notes, sortOrder
```
- **مفيش phases.** الترتيب عبر `sortOrder` مستوي واحد.
- **الـ Rest ليس كياناً** — مجرّد حقول على التمرين.

### 1.2 الباك إند (`backend/src/modules/workout-templates/`)
- `workout-templates.service.ts` — CRUD + `buildWorkoutExport` (للموبايل) + `getTrainingConfig`. الـ create/update مغلّفين بـ `$transaction` (تم سابقاً).
- `workout-templates.controller.ts` — REST endpoints + CASL `WorkoutTemplate` + audit (`createdBy/updatedBy`).
- `workout-templates.types.ts` — `CreateWorkoutInput`, `WorkoutExerciseInput`, `WorkoutExport`, `WorkoutExerciseExport`.
- `workout-templates.validation.ts` — تحقّق يدوي للحقول.

### 1.3 الأدمن (`Admin-Dashboard/src/app/admin/workout-templates/`)
- `new/page.tsx` و `[id]/edit/page.tsx` — **~95% كود مكرّر**، نموذج طويل عمودي (Basic info → cover/difficulty → قائمة تمارين قابلة للتوسعة). دي الصفحة "السيئة" المطلوب refactor لها.

### 1.4 الموبايل (`kmp-app/.../training/`)
- `WorkoutConfig` → `WorkoutExercise[]` (flat). `WorkoutRunActivity.buildWorkoutLineItems()` بيحوّلها لـ `WorkoutLineItem[]` ويـ interleave الـ rest من `restAfterExerciseMs`.
- `WorkoutTrainingEngine(items, plannedWorkoutRole="MAIN")` — بياخد **role واحد للـ run كله**، وبيـ look-ahead على `type=="rest"`.
- `MobileSyncModels.kt`: `WorkoutConfigWithMeta` بيقرأ `exercises[]` (مع alternate `workouts`).

---

## 2. المعمارية المستهدفة (Target Architecture)

### 2.1 الهرمية الجديدة
```
WorkoutTemplate
  └─ WorkoutTemplatePhase[]  (مرتّبة بـ sortOrder)
        │   ── يشير إلى ──>  WorkoutPhase  (catalog قابل لإعادة الاستخدام + إعدادات)
        │   + overrides اختيارية (name / canSkip / canContinue / maxContinueTime)
        └─ WorkoutTemplateExercise[]  (مرتّبة بـ sortOrder داخل الـ phase)
              └─ rest fields  (restBetweenSetsMs, restAfterExerciseMs)  ← محفوظة كما هي
```

### 2.2 أين يظهر كل "كيان" من الـ 3
| الكيان (طلب المستخدم) | التمثيل في التخزين | في المحرّر |
|---|---|---|
| **Phase** | جدول جديد `WorkoutPhase` (catalog) + ربط `WorkoutTemplatePhase` | حاوية قابلة للطي تحوي مكوّنات؛ تُضاف من مكتبة presets |
| **Exercise** | `WorkoutTemplateExercise` (موجود، + `workoutTemplatePhaseId`) | كتلة قابلة للاختيار/التعديل |
| **Rest** | **لا كيان جديد** — حقول على التمرين (قرار "Keep fields") | كتلة افتراضية بين التمارين تعدّل `restAfterExerciseMs` للتمرين السابق |

> **ملاحظة توافُق:** لمّا اخترت "Keep fields" للـ Rest، الـ Rest **مش هيبقى صف مستقل قابل لإعادة الترتيب** في قاعدة البيانات. هيظهر في المحرّر ككتلة بصرية بين التمارين، وتعديلها بيكتب على `restAfterExerciseMs` بتاع التمرين اللي قبلها (ورست بين المراحل = `restAfterExerciseMs` لآخر تمرين في الـ phase). ده متوافق 100% مع `buildWorkoutLineItems` في الموبايل اللي بيبني الـ rest من نفس الحقل.

---

## 3. تغييرات قاعدة البيانات (Prisma Schema)

### 3.1 كيان جديد: `WorkoutPhase` (الـ catalog)
```prisma
/// WorkoutPhase — Reusable phase definition (Warm-up / Main / Cool-down ...).
/// Managed on its own admin CRUD page; referenced by workouts via WorkoutTemplatePhase.
model WorkoutPhase {
  id                String                 @id @default(uuid())
  slug              String                 @unique
  name              Json                                  // LocalizedText {en, ar}
  description       Json?
  role              WorkoutBlockRole       @default(MAIN) // reuse existing enum
  /// يقدر المستخدم يتخطّى المرحلة ويُحتسب الـ workout مكتملاً
  canSkip           Boolean                @default(false)
  /// يقدر يكمل من حيث انتهى لو خرج/وقف وسط المرحلة
  canContinue       Boolean                @default(true)
  /// أقصى وقت (ms) مسموح بعده بالاستكمال؛ null = بدون حد
  maxContinueTimeMs Int?
  /// تلميحات للواجهة فقط
  color             String?
  icon              String?
  isActive          Boolean                @default(true)
  sortOrder         Int                    @default(0)
  createdBy         String?
  updatedBy         String?
  createdAt         DateTime               @default(now())
  updatedAt         DateTime               @updatedAt
  deletedAt         DateTime?
  templatePhases    WorkoutTemplatePhase[]

  @@index([role])
  @@index([deletedAt])
  @@map("workout_phases")
}
```

### 3.2 كيان جديد: `WorkoutTemplatePhase` (instance/ربط)
```prisma
/// WorkoutTemplatePhase — A phase instance inside a specific workout (ordered),
/// with optional per-workout overrides over the WorkoutPhase definition.
model WorkoutTemplatePhase {
  id                        String                    @id @default(uuid())
  workoutTemplateId         String
  phaseId                   String
  sortOrder                 Int                       @default(0)
  // Overrides (null = inherit from WorkoutPhase)
  nameOverride              Json?
  canSkipOverride           Boolean?
  canContinueOverride       Boolean?
  maxContinueTimeMsOverride Int?
  createdAt                 DateTime                  @default(now())
  updatedAt                 DateTime                  @updatedAt
  workoutTemplate           WorkoutTemplate           @relation(fields: [workoutTemplateId], references: [id], onDelete: Cascade)
  phase                     WorkoutPhase              @relation(fields: [phaseId], references: [id])  // onDelete: Restrict (لا تحذف phase مستخدمة)
  exercises                 WorkoutTemplateExercise[]

  @@index([workoutTemplateId])
  @@index([phaseId])
  @@map("workout_template_phases")
}
```
> الـ phase نفسها ممكن تتكرّر في نفس الـ workout (مثلاً warm-up مرتين) — مفيش `@@unique` على `[workoutTemplateId, phaseId]` عمداً.

### 3.3 تعديل `WorkoutTemplateExercise`
```prisma
model WorkoutTemplateExercise {
  id                     String                @id @default(uuid())
  workoutTemplateId      String                                       // KEEP (denormalized convenience)
  workoutTemplatePhaseId String                                       // NEW — الأب الهيكلي
  exerciseId             String
  // ... باقي الحقول كما هي بالكامل (variantIndex, difficulty, targets, sets,
  //     restBetweenSetsMs, restAfterExerciseMs, weightKg, weightPerSet, notes, sortOrder)
  workoutTemplatePhase   WorkoutTemplatePhase  @relation(fields: [workoutTemplatePhaseId], references: [id], onDelete: Cascade)
  workoutTemplate        WorkoutTemplate       @relation(fields: [workoutTemplateId], references: [id], onDelete: Cascade)
  exercise               Exercise              @relation(fields: [exerciseId], references: [id])

  @@index([workoutTemplateId])
  @@index([workoutTemplatePhaseId])                                   // NEW
  @@index([exerciseId])
  @@map("workout_template_exercises")
}
```
- نخلّي `workoutTemplateId` (denormalized) عشان:
  1. علاقة `WorkoutTemplate.exercises` تفضل شغّالة (مفيش كسر).
  2. mobile-sync query للتمارين المسطّحة يفضل بسيط.
  - **قاعدة الاتساق:** `workoutTemplateId` لازم = `workoutTemplatePhase.workoutTemplateId` (نضمنها في الـ service layer).
- `sortOrder` يبقى **نسبي داخل الـ phase**.

### 3.4 إضافة العلاقة العكسية على `WorkoutTemplate`
```prisma
model WorkoutTemplate {
  // ... كما هو
  phases     WorkoutTemplatePhase[]      // NEW
  exercises  WorkoutTemplateExercise[]   // KEEP
}
```

> **`WorkoutBlockRole`** موجود بالفعل (سطر 1259) — **لا تضف enum جديد**.

---

## 4. خطة الترحيل (Migration Plan)

ترحيل **آمن وإضافي (additive)** — لا حذف بيانات. على 3 خطوات في migration واحد:

### 4.1 إنشاء الجداول الجديدة + العمود الجديد (nullable مؤقتاً)
```sql
CREATE TABLE "workout_phases" (...);
CREATE TABLE "workout_template_phases" (...);
ALTER TABLE "workout_template_exercises" ADD COLUMN "workoutTemplatePhaseId" TEXT;  -- nullable مؤقتاً
```

### 4.2 Seed phases افتراضية في الـ catalog
نزرع 3 على الأقل (تتوسّع لاحقاً) — تُنفّذ داخل الـ migration أو seeder:
```
MAIN     → "Main Workout" / "التمرين الأساسي"   canSkip=false canContinue=true  maxContinueTimeMs=null
WARMUP   → "Warm-up"      / "إحماء"             canSkip=true  canContinue=true  maxContinueTimeMs=120000
COOLDOWN → "Cool-down"    / "تهدئة"             canSkip=true  canContinue=true  maxContinueTimeMs=120000
```

### 4.3 Auto-wrap للبيانات الحالية (data backfill)
لكل `WorkoutTemplate` موجود:
1. أنشئ `WorkoutTemplatePhase` واحد يشير لـ phase الـ `MAIN`، `sortOrder=0`.
2. اربط كل `WorkoutTemplateExercise` بتاعه بالـ phase instance ده (`workoutTemplatePhaseId = <new id>`).

```sql
-- Pseudocode داخل الـ migration (يُنفّذ بـ DO block أو في seeder بـ Prisma):
-- 1) لكل workout: INSERT INTO workout_template_phases (id, workoutTemplateId, phaseId=MAIN, sortOrder=0)
-- 2) UPDATE workout_template_exercises e
--      SET workoutTemplatePhaseId = (phase instance لنفس الـ workout)
--    WHERE e.workoutTemplateId = ...
```

### 4.4 فرض NOT NULL بعد التعبئة
```sql
ALTER TABLE "workout_template_exercises" ALTER COLUMN "workoutTemplatePhaseId" SET NOT NULL;
ALTER TABLE "workout_template_exercises"
  ADD CONSTRAINT "wte_phase_fkey" FOREIGN KEY ("workoutTemplatePhaseId")
  REFERENCES "workout_template_phases"("id") ON DELETE CASCADE;
```

### 4.5 الاحتياطات
- `pg_dump -Fc` قبل التطبيق (نفس بروتوكول آخر مرة).
- التطبيق بـ `prisma migrate deploy` (production-safe).
- الترتيب: الجداول → seed → backfill → NOT NULL. لو الـ backfill اتقسم على seeder، نخلّي الـ migration للـ DDL والـ seeder للـ data، ونوثّق الترتيب.
- **توافق خلفي:** القديم بيقرأ `exercises[]` المسطّحة (هتفضل موجودة في الـ export)، فأي نسخة موبايل قديمة تفضل شغّالة بعد الترحيل مباشرة.

---

## 5. تغييرات الباك إند (NestJS)

### 5.1 موديول جديد: `workout-phases` (الـ catalog CRUD)
```
backend/src/modules/workout-phases/
  workout-phases.controller.ts   // @Controller('workout-phases') + CaslGuard
  workout-phases.service.ts      // list/getById/create/update/delete (soft) + audit
  workout-phases.types.ts        // CreatePhaseInput / UpdatePhaseInput / PhaseExport
  workout-phases.validation.ts   // name (en/ar), role ∈ enum, maxContinueTimeMs ≥ 0 ...
  workout-phases.module.ts
```
- نمط مطابق لـ `workout-templates` (CASL، audit عبر `getAdminIdFromRequest`، soft-delete).
- **CASL:** subject جديد `WorkoutPhase`:
  - `backend/src/lib/casl/casl.types.ts` — تسجيل `WorkoutPhase`.
  - `backend/prisma/seeders/permissions.ts` — 4 صلاحيات (read/create/update/delete).
- تسجيل الموديول في `app.module.ts`.

### 5.2 تحديث `workout-templates` للهيكل المتداخل
**`workout-templates.types.ts`:**
```ts
export interface WorkoutPhaseInput {
  id?: string;                 // WorkoutTemplatePhase id (للتعديل)
  phaseId: string;             // FK → WorkoutPhase
  sortOrder?: number;
  nameOverride?: LocalizedText;
  canSkipOverride?: boolean;
  canContinueOverride?: boolean;
  maxContinueTimeMsOverride?: number;
  exercises: WorkoutExerciseInput[];   // مكوّنات الـ phase
}
export interface CreateWorkoutInput {
  // ... كما هو (name, difficulty, ...)
  phases?: WorkoutPhaseInput[];        // NEW — يحل محل/يكمّل exercises المسطّحة
}
```
- نبقّي `exercises?` المسطّحة مقبولة في الـ input للتوافق الخلفي (لو موجودة → تتلفّ في phase MAIN تلقائياً داخل الـ service).

**`workout-templates.service.ts`:**
- `getById` / `workoutFullInclude` → `include` متداخل: `phases → phase + exercises → exercise (+ poseVariants)`.
- `create` / `update` داخل `$transaction`:
  - أنشئ/استبدل `WorkoutTemplatePhase[]` بالترتيب.
  - لكل phase أنشئ `WorkoutTemplateExercise[]` بـ `workoutTemplatePhaseId` + `workoutTemplateId`.
  - `update` = delete-then-recreate للـ phases والـ exercises (نفس النمط الحالي، ذرّياً).
- `duplicate` → ينسخ الـ phases + الـ exercises.
- **resolver للإعدادات الفعّالة:** `effective = override ?? definition` (لـ canSkip/canContinue/maxContinueTime/name).

**`workout-templates.validation.ts`:**
- تحقّق `phases[]`: كل phase لها `phaseId` صالح، `exercises` غير فارغة (أو مسموح فارغة لو الـ phase مجرّد rest؟ — حسب القاعدة)، نفس تحقّق التمرين الحالي لكل عنصر.
- `validateCanPublish`: على الأقل phase واحدة بها تمرين واحد.

### 5.3 الـ Mobile Export (توافق خلفي إلزامي)
**`buildWorkoutExport` (في `workout-templates.service.ts`):**
- **يبقّي** `exercises[]` المسطّحة (مرتّبة: phase.sortOrder ثم exercise.sortOrder) — للنسخ القديمة.
- **يضيف** `phases[]` بإعدادات **محلولة inline** (مفيش حاجة الموبايل يسحب الـ catalog منفصل):
```ts
phases: [{
  role, name,                       // resolved (override ?? definition)
  canSkip, canContinue, maxContinueTimeMs,
  sortOrder,
  exercises: WorkoutExerciseExport[]
}]
```

**`mobile-sync.service.ts`:**
- الـ query بتاعة `workoutTemplate.findMany` (سطر ~387) تضيف `phases` للـ include.
- `WorkoutExport` type يضيف `phases?`.

**`getTrainingConfig`:** يضيف تجميع الـ exercises حسب phases (نفس الإعدادات المحلولة) عشان محرّك التدريب.

### 5.4 ملفات الباك المتأثرة (checklist)
- [ ] `prisma/schema.prisma` (+models, +relations)
- [ ] `prisma/migrations/<ts>_workout_phases/migration.sql`
- [ ] `prisma/seeders/` (default phases + backfill)
- [ ] `src/modules/workout-phases/*` (جديد)
- [ ] `src/modules/workout-templates/{service,controller,types,validation}.ts`
- [ ] `src/modules/mobile-sync/mobile-sync.service.ts` (+phases في الـ export)
- [ ] `src/modules/mobile-sync/mobile-sync.types.ts`
- [ ] `src/lib/casl/casl.types.ts` (+WorkoutPhase)
- [ ] `prisma/seeders/permissions.ts` (+4 perms)
- [ ] `src/app.module.ts` (+WorkoutPhasesModule)

---

## 6. تغييرات الأدمن (Next.js)

### 6.1 صفحة جديدة: Phases Catalog CRUD
```
Admin-Dashboard/src/app/admin/workout-phases/
  page.tsx              // جدول: name, role(badge), canSkip/canContinue, maxContinueTime, الحالة
  new/page.tsx          // فورم إنشاء phase
  [id]/edit/page.tsx    // فورم تعديل
```
- حقول الفورم: name (en/ar), description, role (Select من enum), canSkip (Checkbox), canContinue (Checkbox), maxContinueTime (ثوانٍ → تتحوّل لـ ms), color/icon اختياري.
- **Nav + routing:**
  - `src/lib/navigation.ts` — عنصر "Workout Phases" (subject `WorkoutPhase`).
  - `src/lib/admin-routes.ts` — prefix → subject.
  - `src/hooks/usePermissions.ts` — لو محتاج alias.

### 6.2 إعادة تصميم محرّر الـ Workout (2-Pane)
**هدف:** استبدال الفورم العمودي الطويل بـ **محرّر بقسمين** + **مكوّن مشترك** يلغي تكرار new/edit.

```
Admin-Dashboard/src/app/admin/workout-templates/
  _components/
    WorkoutEditor.tsx          // الحاوية الرئيسية (state + save) — مشترك بين new/edit
    PhaseList.tsx              // (يسار) المكوّنات بالترتيب
    PhaseSettingsPanel.tsx    // (يمين) محرّر العنصر المُختار
    AddPhaseDialog.tsx        // اختيار phase من الـ catalog
    ExerciseBlock.tsx / RestBlock.tsx
  new/page.tsx                // → <WorkoutEditor mode="create" />
  [id]/edit/page.tsx          // → <WorkoutEditor mode="edit" id=... />
```

#### القسم الأيسر — Outline (المكوّنات بالترتيب)
```
┌─ Workout meta (name, difficulty, cover) — header مطوي ─┐
│                                                        │
│ ▸ Phase: Warm-up        [role badge] [⚙ skip/continue]│
│     1. Jumping Jacks      3×10        ⋮⋮ (drag)        │
│     ⟿ rest 30s                                         │
│     2. Arm Circles        2×15                          │
│ ▸ Phase: Main Workout                                   │
│     1. Squat              4×12  60kg                    │
│     ...                                                 │
│ [+ Add phase]  [+ Add exercise to phase]               │
└────────────────────────────────────────────────────────┘
```
- اختيار أي عنصر (phase / exercise / rest-block) → يفتح في القسم الأيمن.
- Drag-and-drop لإعادة الترتيب داخل الـ phase وبين الـ phases (مكتبة: `@dnd-kit` أو `react-beautiful-dnd`).
- الـ **Rest** يظهر ككتلة `⟿ rest Ns` بين التمارين (تعدّل `restAfterExerciseMs` للتمرين السابق) — **مش صف مستقل** (قرار Keep fields).

#### القسم الأيمن — Inspector (المكوّن المُختار)
- **Phase مُختارة:** الـ name override، role، canSkip/canContinue/maxContinueTime overrides (مع عرض القيمة الموروثة من الـ catalog كـ placeholder)، حذف الـ phase.
- **Exercise مُختار:** كل حقول التمرين الحالية (exercise select, variant, difficulty, target reps/duration, sets, rest between sets, rest after, weight, weightPerSet, notes).
- **Rest block مُختار:** input واحد (rest after previous exercise, بالثواني).

#### الحالة (state shape) في `WorkoutEditor`
```ts
type EditorState = {
  meta: { name, description, coverImageUrl, difficulty, estimatedDurationMin, tags, isFeatured };
  phases: Array<{
    key: string;                  // local id
    phaseId: string;              // FK catalog
    overrides: { name?, canSkip?, canContinue?, maxContinueTimeMs? };
    exercises: WorkoutTemplateExerciseDraft[];
  }>;
  selected: { kind: 'phase'|'exercise'|'rest'; phaseKey: string; exerciseKey?: string } | null;
};
```
- `POST/PUT /api/workout-templates` بالـ payload المتداخل (`phases[]`).
- يجيب الـ catalog من `GET /api/workout-phases?active=true` لقائمة الإضافة.

### 6.3 ملفات الأدمن المتأثرة (checklist)
- [ ] `src/app/admin/workout-phases/**` (جديد — CRUD)
- [ ] `src/app/admin/workout-templates/_components/**` (محرّر مشترك جديد)
- [ ] `src/app/admin/workout-templates/new/page.tsx` (→ يستخدم المحرّر)
- [ ] `src/app/admin/workout-templates/[id]/edit/page.tsx` (→ يستخدم المحرّر)
- [ ] `src/lib/navigation.ts`, `src/lib/admin-routes.ts`, `src/hooks/usePermissions.ts`
- [ ] (اختياري) إضافة dnd library للـ `package.json`

---

## 7. تغييرات الموبايل (Android / Kotlin)

> **استراتيجية التوافق:** الموبايل يقرأ `phases[]` لو موجودة، ويـ fallback لـ `exercises[]` المسطّحة لو مش موجودة. النسخ القديمة تتجاهل `phases[]` وتكمّل على `exercises[]`.

### 7.1 الموديلات
**`training/models/WorkoutConfig.kt`:**
```kotlin
data class WorkoutConfig(
    val name: LocalizedText,
    // ... كما هو
    val exercises: List<WorkoutExercise> = emptyList(),   // KEEP (back-compat)
    val phases: List<WorkoutPhaseConfig> = emptyList(),    // NEW
    @Transient var fileName: String = ""
)

data class WorkoutPhaseConfig(
    val role: String = "MAIN",
    val name: LocalizedText,
    val canSkip: Boolean = false,
    val canContinue: Boolean = true,
    val maxContinueTimeMs: Long? = null,
    val sortOrder: Int = 0,
    val exercises: List<WorkoutExercise> = emptyList()
)
```
**`network/MobileSyncModels.kt`:** `WorkoutConfigWithMeta` يضيف `phases: List<WorkoutPhaseConfig> = emptyList()` ويمرّرها في `toWorkoutConfig()`.

### 7.2 الـ Line-item builder + الـ Engine
**`WorkoutLineItem` (في `ProgramConfig.kt`)** — يضيف حقول phase-context:
```kotlin
data class WorkoutLineItem(
    val type: String,
    // ... كما هو
    val phaseIndex: Int? = null,
    val phaseRole: String? = null,
    val phaseCanSkip: Boolean? = null,
    val phaseCanContinue: Boolean? = null,
    val phaseMaxContinueTimeMs: Long? = null,
)
```
**`WorkoutRunActivity.buildWorkoutLineItems()`:**
- لو `phases` موجودة → loop على الـ phases بالترتيب، إصدار items لكل تمرين بـ phase-context، + rest items من `restAfterExerciseMs` (نفس المنطق الحالي لكن لكل phase).
- لو فاضية → السلوك الحالي (flat).

**`WorkoutTrainingEngine`:**
- حالياً `plannedWorkoutRole` واحد للـ run. **التغيير الجوهري:** الـ role دلوقتي per-phase (من `phaseRole` على الـ item) بدل قيمة ثابتة.
  - `countsTowardWorkoutProgress` يقرأ من `item.phaseRole` بدل `plannedWorkoutRole`.
- **`canSkip`:** عند بداية كل phase، لو `phaseCanSkip==true` → زر "Skip phase" يقفز لأول item في الـ phase التالية؛ الـ phase المتخطّاة لا تُحتسب في `totalSetsPlanned`/completion.
- **`canContinue` + `maxContinueTimeMs`:** عند pause/مغادرة وسط phase:
  - `canContinue==true` → خزّن `phasePausedAt`؛ عند العودة لو `(now - phasePausedAt) ≤ maxContinueTimeMs` (أو null) → استكمل من نفس الموضع.
  - تجاوز الحد أو `canContinue==false` → إعادة الـ phase من أولها (أو إنهاء حسب `canSkip`).
  - يتكامل مع `WorkoutRunSupervisor` (AUTO_PAUSED / NO_POSE) لكن على **مستوى الـ phase** (طبقة أعلى من الـ per-set pause الحالية).

### 7.3 ملفات الموبايل المتأثرة (checklist)
- [ ] `training/models/WorkoutConfig.kt` (+WorkoutPhaseConfig, +phases)
- [ ] `network/MobileSyncModels.kt` (+phases في WorkoutConfigWithMeta)
- [ ] `training/models/ProgramConfig.kt` (+phase fields على WorkoutLineItem)
- [ ] `ui/workouts/WorkoutRunActivity.kt` (buildWorkoutLineItems يحترم phases)
- [ ] `training/workout/WorkoutTrainingEngine.kt` (per-phase role + skip + continue)
- [ ] `ui/train/TrainingWorkoutModeController.kt` (UI: زر skip، رسائل بداية phase)
- [ ] strings.xml (skip phase, phase labels, continue-expired …)
- [ ] (اختياري) WorkoutDetailActivity/preview لعرض الـ phases

---

## 8. دلالات الـ Runtime بدقّة (Settings Semantics)

| الإعداد | المعنى الدقيق | السلوك في الموبايل |
|---|---|---|
| **canSkip** | تخطّي المرحلة بالكامل مع اعتبار الـ workout مكتملاً | زر "Skip phase"؛ المرحلة المتخطّاة تُستبعد من متطلبات الإكمال والإحصائيات |
| **canContinue** | الاستكمال من نفس الموضع بعد التوقّف/المغادرة وسط المرحلة | حفظ موضع (exercise+set) داخل المرحلة؛ false = إعادة المرحلة من أولها |
| **maxContinueTimeMs** | نافذة السماح بالاستكمال (ms)؛ null = بلا حد | لو الغياب > الحد → انتهت صلاحية الاستكمال (إعادة/إنهاء) |

> **ملاحظة:** `canContinue/maxContinueTime` طبقة **أعلى** من الـ NoPose auto-pause الحالي (4s) — دي بتتعامل مع مغادرة المرحلة فعلياً (backgrounding/exit)، مش فقدان الـ pose اللحظي.

---

## 9. ترتيب التنفيذ (Implementation Phases)

| # | المرحلة | المخرجات | يعتمد على |
|---|---|---|---|
| **P0** | Schema + migration + seed + backfill | جداول جديدة، 7 templates ملفوفة في MAIN | — |
| **P1** | Backend: workout-phases module + CASL + perms | CRUD للـ catalog | P0 |
| **P2** | Backend: workout-templates nested phases | create/update/getById/validation متداخلة | P0,P1 |
| **P3** | Backend: mobile-sync + training-config export | phases[] + exercises[] (back-compat) | P2 |
| **P4** | Admin: Phases catalog CRUD page | صفحة مستقلة | P1 |
| **P5** | Admin: 2-pane workout editor (مشترك) | المحرّر الجديد + إلغاء التكرار | P2,P4 |
| **P6** | Mobile: models (back-compat parse) | يقرأ phases أو يـ fallback | P3 |
| **P7** | Mobile: builder + engine (role/skip/continue) | runtime كامل للـ phases | P6 |
| **P8** | QA: typechecks + smoke + parity | الكل أخضر | الكل |

**استراتيجية الإصدار:** P0→P3 (backend) قابلة للنشر منفردة (الموبايل القديم يفضل شغّال على `exercises[]`). الأدمن (P4,P5) بعدها. الموبايل (P6,P7) آخراً وبشكل تدريجي.

---

## 10. المخاطر والنقاط المفتوحة (Risks & Open Questions)

| # | المخاطرة / السؤال | التخفيف / الاقتراح |
|---|---|---|
| R1 | **denormalized `workoutTemplateId`** قد يتعارض مع `phase.workoutTemplateId` | فرض الاتساق في الـ service layer + (اختياري) DB CHECK/trigger |
| R2 | محرّك الموبايل حالياً **role واحد** للـ run | تحويل الـ role لـ per-item (phaseRole)؛ اختبار parity (`TrainingEngineParityTest`) |
| R3 | الـ **continue/maxContinueTime** تحتاج تتبّع حالة عبر backgrounding | persist `phasePausedAt` + موضع المرحلة في الـ ViewModel/state |
| R4 | تكرار نفس الـ phase في workout | مسموح؛ الـ UI يوضّح، الـ sortOrder يحسم الترتيب |
| Q1 | لو الـ phase = rest فقط (بدون تمارين) — مسموح؟ | **اقتراح:** نعم (مرحلة راحة)، لكن `validateCanPublish` يتطلب تمرين واحد على الأقل في الـ workout كله |
| Q2 | rest **بين المراحل** يُخزَّن فين؟ | `restAfterExerciseMs` لآخر تمرين في الـ phase (متّسق مع Keep fields) |
| Q3 | هل نوحّد لاحقاً مع `PlannedWorkout` (programs)؟ | خارج النطاق الآن؛ لكن إعادة استخدام `WorkoutBlockRole` تمهّد لذلك |

---

## 11. خلاصة الملفات (Master Checklist)

<details>
<summary><b>DB / Prisma</b></summary>

- [ ] `backend/prisma/schema.prisma` — +`WorkoutPhase`, +`WorkoutTemplatePhase`, تعديل `WorkoutTemplateExercise` + `WorkoutTemplate`
- [ ] `backend/prisma/migrations/<ts>_workout_phases/migration.sql`
- [ ] `backend/prisma/seeders/*` — default phases + backfill
</details>

<details>
<summary><b>Backend (NestJS)</b></summary>

- [ ] `src/modules/workout-phases/{controller,service,types,validation,module}.ts` (جديد)
- [ ] `src/modules/workout-templates/{service,controller,types,validation}.ts`
- [ ] `src/modules/mobile-sync/{mobile-sync.service,mobile-sync.types}.ts`
- [ ] `src/lib/casl/casl.types.ts`
- [ ] `src/app.module.ts`
</details>

<details>
<summary><b>Admin (Next.js)</b></summary>

- [ ] `src/app/admin/workout-phases/**` (جديد)
- [ ] `src/app/admin/workout-templates/_components/**` (محرّر مشترك)
- [ ] `src/app/admin/workout-templates/new/page.tsx` + `[id]/edit/page.tsx`
- [ ] `src/lib/navigation.ts`, `src/lib/admin-routes.ts`, `src/hooks/usePermissions.ts`
</details>

<details>
<summary><b>Mobile (Android)</b></summary>

- [ ] `training/models/WorkoutConfig.kt`, `training/models/ProgramConfig.kt`
- [ ] `network/MobileSyncModels.kt`
- [ ] `ui/workouts/WorkoutRunActivity.kt`
- [ ] `training/workout/WorkoutTrainingEngine.kt`
- [ ] `ui/train/TrainingWorkoutModeController.kt`
- [ ] `res/values*/strings.xml`
</details>

---

## 12. خطوة بعد الموافقة

بعد مراجعتك للخطة، الاقتراح نبدأ بـ **P0 (Schema + migration + backfill)** لأنها الأساس وقابلة للاختبار منفردة (smoke + التأكد إن الـ 7 templates اتلفّت صح في MAIN)، وبعدها نتدرّج حسب الجدول. أقدر أبدأ التنفيذ فور موافقتك أو تعديلك لأي قرار.
