# إكمال البرنامج، إعادة التقييم، والبرنامج التالي

| | |
|---|---|
| **Status** | `ACTIVE` |
| **SSOT for** | Program completion, reassessment, plan complete API |
| **Code** | `backend/src/modules/programs/program-completion.service.ts`, `backend/src/modules/active-plan/`, `kmp-app/core/data/repository/MobileWriteSyncRepository.kt` |
| **Verified** | 2026-06-22 |

---

## `programCompletionService.evaluate`

`program-completion.service.ts` — يحمّل `UserProgram` + تقدم، أحدث تقييم/مستوى، جدولة إعادة تقييم معلّقة.

**أسابيع مكتملة:** `countCompletedCalendarWeeks` — كل أيام التدريب بـ `status === completed`.

**قرار `nextAction`:**

| الحالة | `nextAction` |
|--------|--------------|
| `needsReassessment` | `reassess` |
| `nextProgramId` معرّف | `next_program` |
| غير ذلك | `journey_summary` |

---

## `activePlanService.completeActiveProgram`

1. `programCompletionService.evaluate`
2. فتحة نشطة → `completed`
3. `UserProgram.isActive = false`
4. تفعيل فتحة `upcoming` إن وُجدت، وإلا `ActivePlan.status = completed`

**لا** يستدعي `enrollProgram` لـ `nextProgramId` تلقائيًا.

---

## API

| الطريقة | المسار | ملاحظة |
|--------|--------|--------|
| POST | `/mobile/plan/complete` | يعيد الخطة + `completion` |
| POST | `/mobile/user-programs/:id/complete` | تقييم فقط دون تعديل الخطة |

---

## إعادة التقييم

`reassessment.service.ts` — أسباب: `program_complete`, `periodic`, `progression_trigger`, `manual`.

`buildHomeData`: `reassessment_due` عند `ReassessmentSchedule` بحالة `pending` و`scheduledDate <= now` (لا يشمل `overdue` في الاستعلام الأولي).

---

## kmp-app

| ما يعمل | ما لا يعمل |
|---------|------------|
| عرض `reassessment_due` / `program_complete` في Home وTrain (`TrainApiMapper`) | استدعاء `POST /mobile/plan/complete` من UI |
| فتح `MovitInnerRoute.Assessment(mode = progression)` من Train/Home | فرع `nextAction` (reassess / next_program / journey_summary) بعد إكمال الخطة |
| `MobileWriteSyncRepository.completePlan` + outbox `PLAN_COMPLETE` | أي ViewModel في `feature/*` يستدعي `completePlan` اليوم |

**فجوة حرجة:** إكمال البرنامج على الخادم غير موصول بزر/تدفق في `MovitTrainRoute`.
