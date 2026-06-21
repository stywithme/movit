# إكمال البرنامج، إعادة التقييم، والبرنامج التالي

## `programCompletionService.evaluate`

المصدر: `backend/src/modules/programs/program-completion.service.ts`.

### المدخلات

- `userProgramId` و`userId` — يُحمّل `UserProgram` مع `program.weeks` و`progress`.
- أحدث `BodyScanResult`, أحدث `UserLevelProfile`, وأقدم `ReassessmentSchedule` بحالة `pending` أو `overdue`.

### احتساب الأسابيع المكتملة

- `countCompletedCalendarWeeks`: عدّ الأسابيع من 1 إلى `durationWeeks` حيث كل أيام التدريب (غير الراحة وذات تمارين مخططة) لها إدخال تقدم `status === 'completed'` و`plannedWorkoutId === '__day__'`.

### مراجعة خروج البرنامج (`exitRecommendations`)

- `collectExitReviewFindings` يقرأ من `program.exitRecommendations` حقولًا اختيارية مثل عتبات `{ min, max }` لـ `bodyScore`, `mobilityScore`, …, `overallLevel`, و`minWeeksCompleted`.
- إذا وُجدت مخالفات → `needsReassessment = true`.

### قرار الإخراج (`ProgramCompletionDecision`)

| الحالة | `nextAction` | `nextProgramId` | `reassessmentTemplateId` |
|--------|--------------|-----------------|---------------------------|
| `needsReassessment` | `reassess` | `null` | نتيجة `assessmentTemplateService.resolveForUser(userId, 'progression')` حقل `templateId` أو `null` |
| لا حاجة لإعادة تقييم و`program.nextProgramId` مُعرَّف | `next_program` | قيمة `nextProgramId` | `null` |
| غير ذلك | `journey_summary` | `null` | `null` |

عند `needsReassessment` وعدم وجود جدولة معلّقة مسبقًا، يُنشأ `ReassessmentSchedule` بـ `reason: 'program_complete'`, `scheduledDate: new Date()`, `status: 'pending'`, و`notes` تصف الحقول غير المستوفاة.

## `activePlanService.completeActiveProgram`

المصدر: `backend/src/modules/active-plan/active-plan.service.ts`.

1. `completion = programCompletionService.evaluate(userId, activeSlot.userProgramId)`.
2. تحديث فتحة الخطة النشطة إلى `completed` مع `completedAt`.
3. `UserProgram` المرتبط → `isActive: false`.
4. إن وُجدت فتحة لاحقة `status: 'upcoming'` في نفس الخطة → تُفعَّل `active` وتُحدَّث تواريخ البدء؛ وإلا `ActivePlan.status` → `completed`.
5. يُعاد `{ plan: getOrCreate(...), completion }`.

**ملاحظة:** هذا المسار **لا** يستدعي `enrollProgram` لـ `nextProgramId` من قرار الإكمال؛ الانتقال إلى برنامج جديد يتطلب إما فتحة `upcoming` مسبقة في الخطة أو تسجيلًا يدويًا من العميل.

## واجهة الموبايل

| الطريقة | المسار | الملف |
|--------|--------|--------|
| POST | `/mobile/plan/complete` | `active-plan.controller.ts` — يعيد `data` (الخطة) و`completion` (قرار الإكمال) |

| الطريقة | المسار | الملف |
|--------|--------|--------|
| POST | `/mobile/user-programs/:id/complete` | `mobile-user-programs.controller.ts` — يعيد `programCompletionService.evaluate` فقط **دون** تعديل الخطة |

## خدمة إعادة التقييم

المصدر: `backend/src/modules/reassessment/reassessment.service.ts`.

- أسباب موثّقة في تعليق الملف: `program_complete`, `periodic`, `progression_trigger`, `manual`.
- `checkPeriodicReassessment`: إن مرّت 6 أسابيع منذ آخر تقييم ولا يوجد معلّق، يُنشأ جدول `periodic` بجدولة بعد 24 ساعة.
- `markCompleted`: عند رفع تقييم، يُحدَّث أقدم جدول `pending`/`overdue` إلى `completed` مع `assessmentId`.

واجهات الموبايل: `reassessment.controller.ts` — GET للجدول القادم والسجل، POST للجدولة اليدوية.

## kmp-app

- `TrainFragment` بعد إكمال الأسبوع/البرنامج يستدعي `completeActiveProgram` ثم يفرع حسب `completion.nextAction` (انظر `TrainFragment.kt`): `reassess` → `PreScreeningActivity`؛ `next_program` → فتح `ProgramDetailActivity` بالـ slug بعد جلب البرنامج؛ `journey_summary` → تقرير أسبوعي؛ غير ذلك قائمة البرامج.
