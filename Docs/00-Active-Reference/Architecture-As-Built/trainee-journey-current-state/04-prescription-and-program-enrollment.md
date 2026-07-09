# التوصية بالبرنامج (Prescription) والتسجيل في الخطة

| | |
|---|---|
| **Status** | `ACTIVE` |
| **SSOT for** | Prescription engine + plan enrollment |
| **Code** | `backend/src/modules/prescription/`, `backend/src/modules/active-plan/`, `kmp-app/core/data/repository/AccountSyncRepository.kt` |
| **Verified** | 2026-06-22 |

---

## `prescriptionService.recommend`

المصدر: `prescription.service.ts`.

**مدخلات:** أحدث `BodyScanResult`، `User.trainingGoal`، `TrainingProfile` (معدات، جنس، مكان، إقرار صحي، أيام).

**حظر:** `healthDisclaimerAccepted === false` → `SAFETY_BLOCK`، لا برنامج.

**بدون تقييم:** مستوى 1 + `requiredType: training`.

**مع تقييم:** `UserLevelProfile` + `deriveUserAttributeHintsFromAssessment` → `rankAndPick`.

---

## واجهات الموبايل

| الطريقة | المسار |
|--------|--------|
| POST | `/mobile/prescription/recommend` |
| POST | `/mobile/plan/enroll` — `{ programId }` |

`enrollProgram`: upsert خطة، `UserProgram` نشط واحد، فتحة `activePlanProgram` جديدة.

---

## تلقائي vs يدوي

| المحفّز | السلوك |
|---------|--------|
| أول `POST /api/assessment` بدون برنامج نشط | توصية + `enrollProgram` تلقائي |
| اختيار المستخدم | `POST /mobile/plan/enroll` من شاشات البرنامج في KMP |
| إكمال البرنامج | **لا** توصية تلقائية في `completeActiveProgram` |

---

## kmp-app

| العملية | الملف |
|---------|--------|
| تسجيل يدوي | `AccountSyncRepository.enrollProgram` · `MovitInnerRoute.ProgramDetail` |
| بعد التقييم | التسجيل التلقائي من الخادم؛ UI تعرض النتائج في `MovitAssessmentScreen` Results |
| فحص التسجيل | enrollment-check عبر API الخطة قبل التسجيل اليدوي |
