# خطة تبسيط تصميم البرنامج الرياضي — Program Simplification Plan

> الحالة: **مسودة خطة للمراجعة قبل التنفيذ** (لا يوجد كود مكتوب بعد).
> النطاق: Database · Backend · Admin Dashboard · Mobile (Android).

---

## 1. الهدف (Arabic summary)

تبسيط بنية البرنامج بحيث:

1. **البرنامج = أسابيع مباشرة** (بدون طبقة Phases).
2. **الأسبوع**: رقمه تلقائي من ترتيبه (أسبوع 3 = رقمه 3 تلقائيًا). يُحذف من الواجهة: `Week Number`, `Week Name`, `Week type` (Normal/Deload). يبقى للأسبوع فقط:
   - **Target** (نص/اسم)
   - **Description** (اختياري)
3. **حذف Phases نهائيًا** على كل المستويات (الخاصة بالأسابيع/البرامج فقط — وليس phases قوالب الـ Workout مثل Warmup/Main/Cooldown، تلك تبقى كما هي).
4. **اليوم**: رقمه تلقائي من ترتيبه (يوم 1 = رقمه 1). يُحذف من الواجهة اسم/رقم اليوم و `Day focus`. يبقى لليوم فقط:
   - **Target Muscle** (واحدة أو أكثر) كعلاقة مع الـ System Attribute الموجود `muscle`.

> **اكتشاف مهم يقلّل العمل:** يوجد بالفعل System Attribute بالكود `muscle` واسمه `Target Muscle` (`العضلة المستهدفة`, `isSystem: true`) مع قيمه (quadriceps, hamstrings, glutes, chest_muscle, lats, biceps, triceps, …) — راجع [`backend/prisma/seeders/attributes.ts`](backend/prisma/seeders/attributes.ts:9). لذلك **لا حاجة لإنشاء attribute جديد**؛ نضيف فقط جدول ربط بين اليوم وقيم هذا الـ attribute.

---

## 2. English overview

We are flattening the program model: **Program → Weeks → Days → PlannedWorkouts → Items**, dropping the **Phase** layer entirely and stripping week/day metadata down to the essentials.

| Entity | Keep | Add | Remove (from UI **and** payload) |
|---|---|---|---|
| **Week** | `description` | `target` (the week's headline) | `weekNumber` input, `name`, `weekType` |
| **Day** | (workout content) | `targetMuscles[]` (→ `muscle` attribute values) | `dayNumber` input, `name`, `dayFocus` |
| **Phase** | — | — | **whole entity** |

> **Structural fields kept in DB but hidden in the UI:** `ProgramWeek.weekNumber` and `ProgramDay.dayNumber` are **auto-assigned from array order** and remain in the database. They are load-bearing: progress is tracked by the composite key `"{weekNumber}_{dayNumber}_{plannedWorkoutId}"`, the mobile app orders by them, and `validateCalendarProgramStructure` enforces `1..N` sequencing. We only remove the *editable inputs*, never the columns.

---

## 3. القرارات (مؤكَّدة — Confirmed decisions)

| # | القرار | ✅ القرار المعتمد |
|---|---|---|
| D1 | نوع حقل `target` للأسبوع | **`LocalizedText {en, ar}`** — متّسق مع النظام ثنائي اللغة. |
| D2 | كيف نخزّن `target` | **Migration يعيد تسمية العمود `ProgramWeek.name → target`** (مع نقل القيم). |
| D3 | `ProgramDay.isRestDay` / `dayType` | **الإبقاء على الاثنين**، **مع إضافة input لـ `dayType` في واجهة محرّر اليوم** (select: `training` / `rest` / `active_recovery`). `isRestDay` يُشتقّ من `dayType` (`isRestDay = dayType !== 'training'`) لضمان الاتساق. |
| D4 | عدد أيام الأسبوع | **الإبقاء على المرونة** (أضف/احذف يوم). |
| D5 | Backfill لعضلات اليوم | **اشتقاق اختياري** كـ migration لمرّة واحدة (تحسين غير حاجز). |
| D6 | توافق خلفي للـ API | **لا توافق خلفي — قطع نظيف.** المشروع تحت التطوير: الإخراج يحوي `target`/`targetMuscles`/`dayType` فقط (بلا تكرار `name` القديم)، وتُحدَّث كل العملاء معًا. |

> **أثر D3 على الترقيم والتحقّق (مهم):** بما أن المحرّر سيسمح الآن بأيام `rest`/`active_recovery`، فإن `dayNumber` يُسنَد تلقائيًا حسب ترتيب **كل** الأيام (1..N شامل أيام الراحة). لذلك يجب **تعديل** [`validateCalendarProgramStructure`](backend/src/modules/programs/calendar-program-structure.ts:32): يتحقّق أن **كل** الأيام مرقّمة `1..N` تسلسليًا (يضمنه الترقيم التلقائي) + وجود يوم تدريب واحد على الأقل — بدل القاعدة الحالية «أيام التدريب يجب أن تكون 1..N» التي تنكسر عند تخلّل أيام راحة.

---

## 4. الوضع الحالي (Current state — reference)

### 4.1 Database — [`backend/prisma/schema.prisma`](backend/prisma/schema.prisma)

```prisma
model ProgramPhase {            // ⛔ يُحذف بالكامل  (السطر ~468)
  id, programId, name, description, weekType, startWeek, endWeek, sortOrder
}

model ProgramWeek {            // ✏️ يُبسّط  (السطر ~502)
  id, programId, weekNumber, name, description, sortOrder, weekType, days
  @@unique([programId, weekNumber])
}

model ProgramDay {            // ✏️ يُبسّط  (السطر ~520)
  id, weekId, dayNumber, isRestDay, name, dayType, dayFocus, plannedWorkouts
  @@unique([weekId, dayNumber])
}

enum WeekType { NORMAL DELOAD }   // ⛔ يُحذف (مستخدم فقط في ProgramPhase + ProgramWeek)
```

نظام السمات (يُعاد استخدامه): `Attribute(code:'muscle')` → `AttributeValue[]` ← يربطها `ExerciseAttribute` بالتمارين، و`ProgramAttribute` بالبرنامج. سنضيف نظيرًا لليوم.

### 4.2 Backend — [`backend/src/modules/programs/`](backend/src/modules/programs)

- [`programs.types.ts`](backend/src/modules/programs/programs.types.ts): `ProgramPhaseInput`, `ProgramWeekInput.weekType/name`, `ProgramDayInput.name/dayFocus`, `validatePhaseStructure()`, `ProgramExportWeek/Day`, `CreateProgramInput.phases`.
- [`programs.service.ts`](backend/src/modules/programs/programs.service.ts): `buildWeeksFromPhaseMetadata()`, `syncProgramPhasesStructure()`، ومعالجة `weekType`/`dayFocus`/`phases` في create/update/clone، وبناء الـ export.
- [`calendar-program-structure.ts`](backend/src/modules/programs/calendar-program-structure.ts): تحقّق `1..N` للأسابيع/الأيام — **يبقى** (الترقيم التلقائي يحقّقه).

### 4.3 Admin — [`Admin-Dashboard/src/app/admin/programs/`](Admin-Dashboard/src/app/admin/programs)

- [`_lib/program-calendar.ts`](Admin-Dashboard/src/app/admin/programs/_lib/program-calendar.ts): `WeekForm`, `DayForm`, `PhaseForm`, factories/normalize.
- [`_components/ProgramCalendarBuilder.tsx`](Admin-Dashboard/src/app/admin/programs/_components/ProgramCalendarBuilder.tsx): قسم Training Phases، إعدادات الأسبوع، inspector اليوم.
- [`new/page.tsx`](Admin-Dashboard/src/app/admin/programs/new/page.tsx) · [`[id]/edit/page.tsx`](Admin-Dashboard/src/app/admin/programs/[id]/edit/page.tsx): حالة `phases`، الـ auto-align effects، `buildPayload`.
- [`_lib/build-program-phases-payload.ts`](Admin-Dashboard/src/app/admin/programs/_lib/build-program-phases-payload.ts): **يُحذف**.

### 4.4 Mobile — [`android-poc/`](android-poc)

- [`ProgramConfig.kt`](android-poc/app/src/main/java/com/trainingvalidator/poc/training/models/ProgramConfig.kt): `ProgramWeek(weekNumber, name, description)`, `ProgramDay(dayNumber, isRestDay, name)`.
- شاشات العرض: `ProgramDetailActivity`, `ProgramDayActivity` (تعرض أسماء الأسبوع/اليوم).
- الموبايل **لا** يستهلك `weekType` ولا `dayFocus` ولا program-phases (نطاق أصغر).

---

## 5. النموذج المستهدف (Target model)

### 5.1 Prisma (بعد التعديل)

```prisma
// ⛔ model ProgramPhase  -> deleted
// ⛔ enum WeekType        -> deleted

model ProgramWeek {
  id          String       @id @default(uuid())
  programId   String
  weekNumber  Int                         // auto = index+1 (hidden in UI)
  target      Json?                        // ← D2: renamed from `name`  (LocalizedText)
  description Json?
  sortOrder   Int          @default(0)
  createdAt   DateTime     @default(now())
  updatedAt   DateTime     @updatedAt
  days        ProgramDay[]
  program     Program      @relation(fields: [programId], references: [id], onDelete: Cascade)
  @@unique([programId, weekNumber])
  @@index([programId])
  @@map("program_weeks")
}

model ProgramDay {
  id        String               @id @default(uuid())
  weekId    String
  dayNumber Int                                   // auto = index+1 over ALL days (hidden in UI)
  dayType   String               @default("training")  // D3: editable in UI (training | rest | active_recovery)
  isRestDay Boolean              @default(false)        // D3: derived on save = dayType !== 'training'
  createdAt DateTime             @default(now())
  updatedAt DateTime             @updatedAt
  week      ProgramWeek          @relation(fields: [weekId], references: [id], onDelete: Cascade)
  plannedWorkouts PlannedWorkout[]
  targetMuscles   ProgramDayAttribute[]           // ← NEW
  @@unique([weekId, dayNumber])
  @@index([weekId])
  @@map("program_days")
}

// NEW — mirrors ExerciseAttribute / ProgramAttribute
model ProgramDayAttribute {
  id               String         @id @default(uuid())
  dayId            String
  attributeValueId String                         // must belong to attribute code 'muscle'
  createdAt        DateTime       @default(now())
  day              ProgramDay     @relation(fields: [dayId], references: [id], onDelete: Cascade)
  attributeValue   AttributeValue @relation(fields: [attributeValueId], references: [id], onDelete: Cascade)
  @@unique([dayId, attributeValueId])
  @@index([dayId])
  @@index([attributeValueId])
  @@map("program_day_attributes")
}
```

> ملاحظة: يجب إضافة العلاقة العكسية `programDayAttributes ProgramDayAttribute[]` داخل `model AttributeValue` و حذف حقول `name`/`dayFocus` من `ProgramDay` و `weekType` من `ProgramWeek` و علاقة `phases` من `Program`.

---

## 6. خطة التنفيذ حسب الطبقة (Layer-by-layer)

### Layer 1 — Database (Prisma)

1. تعديل [`schema.prisma`](backend/prisma/schema.prisma):
   - حذف `model ProgramPhase` + علاقة `phases ProgramPhase[]` من `model Program`.
   - حذف `enum WeekType` + حقل `weekType` من `ProgramWeek`.
   - `ProgramWeek`: إعادة تسمية `name → target` (D2)، حذف `weekType`.
   - `ProgramDay`: حذف `name`, `dayFocus`. (إبقاء `isRestDay`, `dayType` — D3.)
   - إضافة `model ProgramDayAttribute` + العلاقات العكسية.
2. إنشاء migration واحدة (`npx prisma migrate dev --name simplify_program_structure`) تتضمّن:
   - `DROP TABLE program_phases;`
   - `ALTER TABLE program_weeks RENAME COLUMN name TO target; ALTER TABLE program_weeks DROP COLUMN week_type;`
   - `ALTER TABLE program_days DROP COLUMN name; ALTER TABLE program_days DROP COLUMN day_focus;`
   - `CREATE TABLE program_day_attributes (...)` + الـ indexes والـ unique.
   - `DROP TYPE "WeekType";` (بعد إزالة كل الأعمدة التي تستخدمه).
3. تحديث الـ seeders إن وُجد أي seed لبرامج/phases.

> ⚠️ راجع تحذير الـ migration في الذاكرة: لا تشغّل `next build` أثناء عمل خادم `next dev` للـ admin. وللـ backend استخدم `prisma migrate` على قاعدة التطوير فقط أولًا.

### Layer 2 — Backend (NestJS)

[`programs.types.ts`](backend/src/modules/programs/programs.types.ts):
- حذف `ProgramPhaseInput`, `validatePhaseStructure()`, و`phases?` من `CreateProgramInput`.
- `ProgramWeekInput`: حذف `weekType`, `name` → إضافة `target?: LocalizedText`. (إبقاء `weekNumber?`, `sortOrder?` اختياريين — يُشتقّان من الترتيب.)
- `ProgramDayInput`: حذف `name`, `dayFocus` → إضافة `targetMuscleValueIds?: string[]` و `dayType?: 'training' | 'rest' | 'active_recovery'` (D3). `isRestDay` لا يُرسَل من العميل — يُشتقّ في الخدمة.
- `ProgramExportWeek`: `name → target` (قطع نظيف، بلا تكرار). `ProgramExportDay`: حذف `name` → إضافة `targetMuscles: {code, name}[]` و `dayType` (و `isRestDay` المشتقّ).

[`programs.service.ts`](backend/src/modules/programs/programs.service.ts):
- حذف `buildWeeksFromPhaseMetadata()` و`syncProgramPhasesStructure()` وكل استدعاءاتها.
- في create/update/clone: حذف كتابة `weekType`/`name(week)`/`name(day)`/`dayFocus`؛ **اشتقاق `weekNumber`/`dayNumber` من ترتيب المصفوفة** (index+1) بدل قراءتها من المدخل.
- كتابة `dayType` من المدخل، و**اشتقاق `isRestDay = dayType !== 'training'`** عند الحفظ (D3).
- إضافة منطق مزامنة `targetMuscles` لليوم: عند الحفظ، استبدال صفوف `ProgramDayAttribute` لليوم بـ `targetMuscleValueIds` (مع التحقّق أن كل id ينتمي للـ attribute `muscle`).
- بناء الـ export: إخراج `target` للأسبوع، و`targetMuscles` + `dayType` + `isRestDay` لليوم (join على `ProgramDayAttribute → AttributeValue`).
- إضافة validation: `target` للأسبوع اختياري؛ `targetMuscleValueIds` كلها من attribute `muscle`.

[`calendar-program-structure.ts`](backend/src/modules/programs/calendar-program-structure.ts): **تعديل** `validateCalendarProgramStructure` ليتحقّق أن **كل** الأيام مرقّمة `1..N` تسلسليًا + وجود يوم تدريب واحد على الأقل (بدل «أيام التدريب 1..N») — لاستيعاب أيام الراحة المتخلّلة (راجع ملاحظة D3 في القسم 3).

ابحث عن أي مستهلك آخر: `grep -r "weekType\|ProgramPhase\|dayFocus" backend/src` → النطاق محصور حاليًا في هذين الملفين فقط (تم التحقّق).

### Layer 3 — Admin Dashboard (Next.js)

[`_lib/program-calendar.ts`](Admin-Dashboard/src/app/admin/programs/_lib/program-calendar.ts):
- `WeekForm`: حذف `weekType`, `name` → إضافة `target: LocalizedText`. (إبقاء `weekNumber`/`sortOrder` داخليًا للترتيب فقط.)
- `DayForm`: حذف `name`, `dayFocus` → إضافة `targetMuscleIds: string[]` و `dayType: 'training' | 'rest' | 'active_recovery'` (افتراضي `training`). (إبقاء `dayNumber` داخليًا.)
- حذف `PhaseForm` + `createEmptyPhase` + `clonePhase`.
- `createEmptyWeek`: لا `weekType`/`name`؛ `target` فارغ.

[`_components/ProgramCalendarBuilder.tsx`](Admin-Dashboard/src/app/admin/programs/_components/ProgramCalendarBuilder.tsx):
- **حذف قسم "Training Phases" بالكامل** + كل معالِجاته (`addPhase`, `updatePhase`, `removePhase`, `applyPatternToPhase`, `applyBulkWeekTypeToPhase`, `syncPhasesToWeeks`, `getPhaseStats`).
- شريط تبويبات الأسابيع: إبقاء `Week N` (الرقم التلقائي كـ label فقط)، حذف شارة Deload.
- إعدادات الأسبوع: حذف حقول `Week Number`, `Week type`, `Week Name (EN/AR)` → إبقاء **`Target (EN/AR)`** + `Description (EN/AR)`.
- inspector اليوم (`DayInspector`): حذف `Day Number`, `Day Name (EN/AR)`, `Day focus` → استبدالها بـ: **مُنتقي Target Muscle متعدّد** (multi-select من قيم attribute `muscle`) + **مُنتقي `Day type`** (select: `training` / `rest` / `active_recovery`). عند `dayType !== 'training'` يمكن إخفاء/تعطيل محتوى الـ Planned Workouts (يوم راحة).
- مصدر العضلات: إعادة استخدام [`useAttributesCatalog()`](Admin-Dashboard/src/app/admin/programs/_components/ProgramAttributesSection.tsx:102) ثم `catalog.find(a => a.code === 'muscle').values` — لا حاجة لـ endpoint جديد.
- تمرير `target`/`targetMuscleIds` كـ props/handlers بدل القديمة.

[`new/page.tsx`](Admin-Dashboard/src/app/admin/programs/new/page.tsx) و [`[id]/edit/page.tsx`](Admin-Dashboard/src/app/admin/programs/[id]/edit/page.tsx):
- حذف حالة `phases`/`setPhases`، الـ `useEffect` الخاصة بـ auto-align الـ duration من phases، واستيراد/استخدام `buildProgramPhasesPayload`.
- `durationWeeks` تُدار مباشرة من add/remove week (موجود مسبقًا).
- `buildPayload`: حذف `phases`، حذف `weekType`/week `name`/day `name`/`dayFocus`؛ إرسال `target` للأسبوع و`targetMuscleValueIds` لليوم. عدم إرسال `weekNumber`/`dayNumber` (أو إرسالها وسيُعاد اشتقاقها).
- edit `loadProgram` mapping: قراءة `target` و`targetMuscles[]` من الاستجابة بدل `name`/`weekType`/`dayFocus`.

[`_lib/build-program-phases-payload.ts`](Admin-Dashboard/src/app/admin/programs/_lib/build-program-phases-payload.ts): **حذف الملف**.

[`_lib/week-seven-days.ts`](Admin-Dashboard/src/app/admin/programs/_lib/week-seven-days.ts): مراجعة — يبقى إن أردنا padding للأيام، أو يُبسّط.

التحقّق: `npx tsc --noEmit` (لا `next build` أثناء عمل dev — راجع الذاكرة).

### Layer 4 — Mobile (Android)

[`ProgramConfig.kt`](android-poc/app/src/main/java/com/trainingvalidator/poc/training/models/ProgramConfig.kt):
- `ProgramWeek`: **حذف `name`** → إضافة `val target: LocalizedText? = null`. إبقاء `description`, `weekNumber`. (لا fallback — قطع نظيف D6.)
- `ProgramDay`: **حذف `name`** → إضافة `val targetMuscles: List<MuscleRef> = emptyList()` (data class جديدة `MuscleRef(code, name: LocalizedText)`) و `val dayType: String = "training"`. إبقاء `dayNumber`, `isRestDay`.
- شاشات: تحديث `ProgramDetailActivity` / `ProgramDayActivity` + ملفات `activity_program_detail.xml` / `activity_program_day.xml` لعرض **Target الأسبوع** و**عضلات اليوم** بدل الأسماء.
- لا تغيير على منطق phases في الموبايل (لم يكن يستهلك program-phases أصلًا).

---

## 7. عقد الـ API (API contract — before/after)

### Create/Update payload (Admin → Backend)

**قبل:**
```jsonc
{
  "name": {"en":"...","ar":"..."}, "durationWeeks": 3,
  "phases": [{"name":{...},"weekType":"NORMAL","startWeek":1,"endWeek":3}],
  "weeks": [{
    "weekNumber": 1, "weekType": "NORMAL",
    "name": {"en":"Week 1","ar":""}, "description": {...},
    "days": [{ "dayNumber": 1, "isRestDay": false,
               "name": {...}, "dayFocus": "lower body",
               "plannedWorkouts": [ ... ] }]
  }]
}
```

**بعد:**
```jsonc
{
  "name": {"en":"...","ar":"..."}, "durationWeeks": 3,
  // لا phases
  "weeks": [{
    // لا weekNumber / weekType / name  (weekNumber يُشتقّ من الترتيب)
    "target": {"en":"Hypertrophy block","ar":"مرحلة التضخّم"},
    "description": {"en":"...","ar":"..."},
    "days": [{
      // لا dayNumber / name / dayFocus  (dayNumber يُشتقّ من الترتيب)
      // لا isRestDay  (يُشتقّ في الخدمة من dayType)
      "dayType": "training",                                  // training | rest | active_recovery
      "targetMuscleValueIds": ["<uuid-quadriceps>", "<uuid-glutes>"],
      "plannedWorkouts": [ ... ]
    }]
  }]
}
```

### Program export (Backend → Mobile)

**بعد (قطع نظيف):** كل أسبوع يحوي `target` فقط (بلا `name`/`weekType`)؛ كل يوم يحوي `targetMuscles: [{code,name}]` + `dayType` + `isRestDay` (مشتقّ) بدل `name`/`dayFocus`.

---

## 8. ترحيل البيانات (Data migration)

1. **Phases:** `DROP TABLE program_phases;` — لا حاجة لنقل (بيانات وصفية فقط).
2. **Week:** `name → target` (rename عمود يحفظ القيم). `weekType` يُحذف (لا نقل).
3. **Day:** `name`, `dayFocus` يُحذفان (لا نقل). 
4. **Target muscles (اختياري D5):** سكربت لمرّة واحدة يملأ `program_day_attributes` لكل يوم من **اتحاد عضلات تمارين اليوم** (عبر `PlannedWorkoutItem.exerciseId → ExerciseAttribute` المرتبطة بـ attribute `muscle`). يُشغّل بعد الـ migration الهيكلية.
5. نقطة تراجع (rollback): الاحتفاظ بنسخة احتياطية قبل `DROP`/`RENAME`.

---

## 9. ترتيب التنفيذ المقترح (Rollout sequence)

1. **DB migration** (rename/drop/جدول الربط الجديد).
2. **Backend** (types + service + export + validation) — قطع نظيف، بلا قبول للحقول القديمة.
3. **Admin** (المحرّر + الـ payload).
4. **Mobile** (قراءة `target`/`targetMuscles`/`dayType`).
5. الـ migration الاختيارية للـ backfill (D5).

> **لا توافق خلفي (D6):** بما أن المشروع تحت التطوير، تُنشر الطبقات 1→4 **معًا** (نشر منسّق). لا يوجد tolerant reader؛ العميل القديم لن يعمل مع الـ API الجديد — وهذا مقبول هنا.

---

## 10. المخاطر والتخفيف (Risks)

| الخطر | التخفيف |
|---|---|
| `weekNumber`/`dayNumber` مفاتيح تتبّع تقدّم (`week_day_pw`) — أي إعادة ترقيم تكسر تقدّم المستخدمين الحاليين | الإبقاء على الأعمدة؛ الترقيم التلقائي يحافظ على نفس التسلسل `1..N`؛ عدم تغيير صيغة المفتاح. |
| كسر العملاء القدامى عند حذف `name`/`weekType` (لا توافق خلفي — D6) | مقبول (المشروع تحت التطوير)؛ نشر الطبقات الأربع **معًا** + بناء/اختبار الموبايل قبل الإصدار. |
| تخلّل أيام `rest`/`active_recovery` يكسر ترقيم/تحقّق الأيام (أثر D3) | تعديل `validateCalendarProgramStructure` لترقيم `1..N` شامل + ترقيم تلقائي من الترتيب. |
| منطق `active-plan`/`completion` يعتمد على `isRestDay`/`dayType` | عدم حذفهما (D3). |
| الخلط بين Program-phases (تُحذف) و Workout-template phases (تبقى) | موثّق صراحة؛ التغييرات تمسّ `programs/*` فقط، لا `workout-templates/*` ولا `workout-phases`. |
| migration على قاعدة فيها بيانات إنتاج | نسخة احتياطية + تجربة على staging + سكربت backfill منفصل وقابل لإعادة التشغيل. |

---

## 11. قائمة اختبار (Test checklist)

- [ ] إنشاء برنامج جديد بأسبوع/أيام بدون أي رقم/اسم يدوي → يُحفظ بـ `weekNumber`/`dayNumber` تلقائية صحيحة.
- [ ] تعيين Target للأسبوع + Description → يظهران بعد إعادة التحميل.
- [ ] تعيين عدّة Target Muscles لليوم → تُحفظ وتُسترجع، وكلها من attribute `muscle`.
- [ ] تغيير `Day type` إلى `rest`/`active_recovery` → يُحفظ، و`isRestDay` يُشتقّ صحيحًا، والترقيم `1..N` يبقى سليمًا.
- [ ] أسبوع فيه أيام تدريب + راحة متخلّلة → `validateCalendarProgramStructure` يمرّ.
- [ ] برنامج قديم (به phases/weekType/أسماء) يُفتح في المحرّر بعد الـ migration دون أخطاء.
- [ ] تتبّع التقدّم لمستخدم على برنامج قائم لا ينكسر (مفاتيح `week_day_pw`).
- [ ] `validateCalendarProgramStructure` يمرّ مع الترقيم التلقائي.
- [ ] الموبايل (بعد التحديث) يعرض Target الأسبوع، عضلات اليوم، و`dayType` بشكل صحيح.
- [ ] `npx tsc --noEmit` (admin) و build الـ backend و build الموبايل — كلها خضراء.

---

## 12. ملخّص الملفات المتأثّرة (Touch list)

| الطبقة | ملفات |
|---|---|
| DB | `backend/prisma/schema.prisma` · migration جديدة · seeders (إن لزم) |
| Backend | `programs.types.ts` · `programs.service.ts` (+ مراجعة `programs.controller.ts`, `mobile-*.controller.ts` للـ export) |
| Admin | `_lib/program-calendar.ts` · `_components/ProgramCalendarBuilder.tsx` · `new/page.tsx` · `[id]/edit/page.tsx` · **حذف** `_lib/build-program-phases-payload.ts` · مراجعة `_lib/week-seven-days.ts` |
| Mobile | `ProgramConfig.kt` · `ProgramDetailActivity` · `ProgramDayActivity` · `activity_program_detail.xml` · `activity_program_day.xml` |

---

*أُعدّت هذه الخطة بعد مراجعة المخطّط، الـ backend module، محرّر الـ admin، ونماذج الموبايل. جاهزة للمراجعة — بعد تأكيد القرارات (القسم 3) أبدأ التنفيذ طبقة بطبقة.*
