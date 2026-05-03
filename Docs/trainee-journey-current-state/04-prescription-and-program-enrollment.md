# التوصية بالبرنامج (Prescription) والتسجيل في الخطة النشطة

## `prescriptionService.recommend`

المصدر: `backend/src/modules/prescription/prescription.service.ts`.

### مدخلات تُجلب من قاعدة البيانات

- أحدث `BodyScanResult` للمستخدم.
- `User` مع `trainingGoal` و`trainingProfile` (معدات، جنس، مكان، `healthDisclaimerAccepted`, `trainingWeekdays`, `availableDaysPerWeek`).

### حظر التوصية

إذا `healthDisclaimerAccepted === false` على الملف الشخصي:

- يُعاد `classification` بفئة `SAFETY_BLOCK` و`recommendedProgram: null` و`fallbackUsed: true`.

### حالة: لا يوجد تقييم (`!assessment`)

- `UserAttributeHints`: `{ requiredType: 'training', focusHint: null, regionHints: [] }`.
- `overallLevel` ثابت = `1`.
- استعلام برامج: منشورة، غير محذوفة، `levelRangeMin/Max` تحتوي المستوى 1، ووجود `ProgramAttribute` بـ `mode` REQUIRED أو OPTIONAL يطابق رمز النطاق المشتق من `classification.requiredType`.
- الاختيار: `rankAndPick` (انظر أدناه).

### حالة: يوجد تقييم

- `UserLevelProfile` الأحدث؛ `overallLevel` منه أو `scoreToLevel(assessment.bodyScore)` كاحتياط.
- `hints = deriveUserAttributeHintsFromAssessment({ symmetryScore }, { overallLevel, domainLevels, regionLevels })` — المصدر: `backend/src/lib/attribute-matching.ts` (منطق النطاقات الضعيفة، التماثل، النطاقات الأضعف من المستوى العام).
- `classification = buildClassificationFromHints` — فئات مثل `CORRECTION_NEED`, `IMBALANCE`, `WEAKNESS`, `NORMAL`.
- `limitingFactor = determineLimitingFactor(safetyGateCodes, domainLevels)`.
- `userCodes = buildUserAttributeSet(profile, goal, hints)`.
- استعلام برامج مشابه مع `levelRange` حول `overallLevel` وشرط نطاق المطلوب.
- `best = rankAndPick(...)`؛ إن لم يوجد، جولة `fallbackPrograms` بدون شرط نطاق المطلوب في `some` ثم `rankAndPick` مرة أخرى.

### `rankAndPick` (ملخص)

- يصفّي برامج لها `programAttributes.length > 0` و`isProgramEligibleForAutoAssignment` و`passesAttributeFilter`.
- `narrowByFocusAndRegions` حسب التلميحات.
- الترتيب: تطابق `weeklySessionTarget` مع عدد أيام التدريب للمستخدم إن وُجد؛ ثم `scoreProgramForLimitingFactor`؛ ثم `countOptionalMatches`؛ ثم `prescriptionPriority` تصاعديًا (الأصغر أولًا في المصفوفة بعد الفرز — الرجوع للكود للتفصيل الدقيق).

## واجهة التوصية للموبايل

| الطريقة | المسار | الملف |
|--------|--------|--------|
| POST | `/mobile/prescription/recommend` | `backend/src/modules/prescription/prescription.controller.ts` |

## التسجيل في البرنامج (Active Plan)

| الطريقة | المسار | الملف |
|--------|--------|--------|
| POST | `/mobile/plan/enroll` — جسم `{ programId }` | `backend/src/modules/active-plan/active-plan.controller.ts` |

- يستدعي `activePlanService.enrollProgram` مع `assignmentReason` من `buildAssignmentReason('manual_selection', ['user_choice'], null)`.

### سلوك `enrollProgram` (ملخص)

المصدر: `backend/src/modules/active-plan/active-plan.service.ts`.

- `activePlan` upsert للمستخدم.
- إيجاد أو إنشاء `UserProgram` للمستخدم و`programId` مع `isActive: true`؛ تعطيل بقية `UserProgram` النشطة لنفس المستخدم.
- إنهاء فتحات `activePlanProgram` النشطة الأخرى (وضعها `completed` مع `completedAt`).
- إنشاء فتحة `active` جديدة إن لم تكن موجودة لنفس `userProgramId`، مع `sortOrder` تالي.

## متى تُستدعى التوصية تلقائيًا

- داخل `assessmentService.create` فقط عند **عدم** وجود برنامج نشط في الخطة (كما في ملف التقييم).
- لا تُستدعى تلقائيًا عند إكمال البرنامج في `completeActiveProgram` (انظر ملف إكمال البرنامج).

## android-poc

- تسجيل يدوي: `MobileSyncApi.enrollProgram` → `POST api/mobile/plan/enroll`؛ يمر عبر `enrollment-check` في `ProgramDetailViewModel`.
- بعد التقييم: `AssessmentResultActivity` يستدعي `enrollInProgram` عند عرض برنامج موصى به (انظر `AssessmentResultActivity.kt`).
