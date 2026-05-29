# قوالب التقييم (Assessment Templates) واختيار القالب المناسب

## نماذج البيانات

- `AssessmentTemplate` — حقول منها: `type`, `targetLevelId`, `levelRangeMin`/`Max`, `domainWeights`, `isDefault`, `isPublished`, `sortOrder`, `deletedAt`.
- `AssessmentTemplateExercise` — تمارين القالب مع `entryType`, `sortOrder`, عتبات، إلخ.
- `AssessmentAttribute` — صفوف مطابقة (نمط مشابه لـ `ProgramAttribute`) مع `mode` (REQUIRED / OPTIONAL / EXCLUDED).

المصدر: `backend/prisma/schema.prisma`.

## استدعاء الحل للمستخدم (Mobile)

| الطريقة | المسار | الملف |
|--------|--------|--------|
| GET | `/mobile/assessment-templates/resolve?mode=...` | `backend/src/modules/assessment-templates/assessment-templates-mobile.controller.ts` |

- Query `mode`: إذا كانت القيمة `progression` يُستخدم وضع التقدم، وإلا يُستخدم `initial` (الافتراضي).

## تسلسل `resolveForUser`

المصدر: `assessmentTemplateService.resolveForUser` في `backend/src/modules/assessment-templates/assessment-templates-admin.service.ts`.

1. إذا `mode === 'progression'`:
   - يُقرأ أحدث `UserLevelProfile` للمستخدم (حسب `classifiedAt` تنازليًا).
   - يُستخدم `overallLevel` منه (أو `1` إن لم يوجد سجل).
   - يُستدعى `assessmentMatchingService.matchProgression(userId, userLevel)`.
2. وإلا (`initial`):
   - يُستدعى `assessmentMatchingService.matchInitial(userId)`.
3. إذا كانت النتيجة `null`:
   - يُستدعى `legacyResolveAssessmentTemplate` — قالب منشور `isDefault: true`؛ لـ `initial` النوع `initial`؛ لـ `progression` أنواع ضمن `['progression','post_program','level_specific']`.
4. يُعاد الحمولة عبر `mapTemplateToResolvePayload` (تتضمن `templateId`, `name`, `type`, `domainWeights`, قائمة تمارين مرتّبة: `core` أولًا ثم الباقي حسب `sortOrder`).

## مطابقة السمات (assessmentMatchingService)

المصدر: `backend/src/modules/assessments/assessment-matching.service.ts`.

### `matchInitial`

- يتطلب وجود `trainingProfile` للمستخدم؛ وإلا يعيد `null`.
- `userCodes = buildUserAttributeSet(profile, trainingGoal, null)` — **لا** تُمرَّر تلميحات من نتيجة تقييم سابقة.
- قوالب: `deletedAt: null`, `isPublished: true`, `type: 'initial'`, مرتبة بـ `sortOrder` ثم `createdAt`.
- التصفية: `passesAttributeFilter` على صفوف `AssessmentAttribute` للقالب.
- الاختيار: بين المؤهلين، ترتيب تنازلي حسب `countOptionalMatches` ثم `sortOrder`.

### `matchProgression`

- نفس شرط وجود `trainingProfile`.
- `userCodes` بنفس الطريقة (تلميحات `null`).
- قوالب: منشورة، الأنواع في `PROGRESSION_TYPES` = `progression`, `post_program`, `level_specific`، و **`targetLevel.number` يساوي `currentLevel`** الممرَّر من أحدث ملف مستوى.
- نفس منطق `pickBestTemplate`.

## ملاحظة سلوك العميل (android-poc)

`MobileSyncApi.resolveAssessmentTemplate` يستدعي المسار **بدون** معامل `mode`، فيُطبَّق على الخادم الوضع `initial` دائمًا ما لم يُضف الاستعلام لاحقًا في العميل.

## Admin-Dashboard

- قائمة وإنشاء/تعديل القوالب: مسارات تحت `/admin/assessment-templates` (صفحات في `Admin-Dashboard/src/app/admin/assessment-templates/`).
- تستدعي واجهات `/api/admin/assessment-templates` (طبقة Next API أمام الـ backend حسب إعداد المشروع).
