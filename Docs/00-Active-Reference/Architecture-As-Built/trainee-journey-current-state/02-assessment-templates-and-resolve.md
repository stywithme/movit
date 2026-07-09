# قوالب التقييم (Assessment Templates) واختيار القالب

| | |
|---|---|
| **Status** | `ACTIVE` |
| **SSOT for** | Assessment template resolve (initial / progression) |
| **Code** | `backend/src/modules/assessment-templates/`, `kmp-app/core/data/repository/AccountSyncRepository.kt` |
| **Verified** | 2026-06-22 |

---

## نماذج البيانات

`AssessmentTemplate`, `AssessmentTemplateExercise`, `AssessmentAttribute` — `backend/prisma/schema.prisma`.

---

## Mobile API

| الطريقة | المسار | الملف |
|--------|--------|--------|
| GET | `/mobile/assessment-templates/resolve?mode=...` | `assessment-templates-mobile.controller.ts` |

`mode`: `progression` أو افتراضي `initial`.

---

## `resolveForUser` (ملخص)

`assessmentTemplateService.resolveForUser` في `assessment-templates-admin.service.ts`:

1. **`progression`:** أحدث `UserLevelProfile` → `matchProgression(userId, level)`.
2. **`initial`:** `matchInitial(userId)`.
3. احتياطي: `legacyResolveAssessmentTemplate` (`isDefault: true`).

مطابقة السمات: `assessment-matching.service.ts` — يتطلب `trainingProfile`؛ `buildUserAttributeSet` بدون تلميحات تقييم سابقة.

---

## kmp-app

| العنصر | الملف |
|--------|--------|
| استدعاء | `AccountSyncRepository.resolveAssessmentTemplate(mode)` |
| HTTP | `MovitMobileApi.resolveAssessmentTemplate(mode, ...)` |
| UI | `MovitAssessmentViewModel` — `mode` من `MovitInnerRoute.Assessment(mode)` |

---

## Admin-Dashboard

`/admin/assessment-templates` — `Admin-Dashboard/src/app/admin/assessment-templates/`.
