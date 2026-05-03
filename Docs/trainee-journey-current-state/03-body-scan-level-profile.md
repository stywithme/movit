# Body Scan (التقييم) وملف المستوى (User Level Profile)

## إنشاء نتيجة التقييم

| الطريقة | المسار | الملف |
|--------|--------|--------|
| POST | `/api/assessment` | `backend/src/modules/assessment/assessment.controller.ts` → `assessmentService.create` |

النوع المطلوب في الطلب (حسب الأنواع في الكود): `BodyScanResultCreate` في `backend/src/modules/assessment/assessment.types.ts` — منها `bodyScore` (إلزامي للتحقق في المتحكم)، `type`, درجات النطاقات، `fitnessLevel`, `regions`, إلخ.

### ما يحدث داخل `assessmentService.create`

المصدر: `backend/src/modules/assessment/assessment.service.ts`.

1. `prisma.bodyScanResult.create` — الحقول المرسلة في `data` **لا** تتضمن `templateId` في هذا المسار (حقل `templateId` موجود في المخطط لكن غير مُعبأ هنا).
2. استدعاء `levelProfileService.calculateFromAssessment(result.id)`.
3. استدعاء `reassessmentService.markCompleted(userId, result.id)` لربط جدول `ReassessmentSchedule` المعلّق إن وُجد.
4. **التسجيل التلقائي في برنامج:** إذا لم يكن للمستخدم فتحة `ActivePlanProgram` بحالة `active` ضمن `ActivePlan`:
   - `prescriptionService.recommend(userId)`
   - عند وجود `recommendedProgram`: `activePlanService.enrollProgram` مع `assignmentReason` من النتيجة.
5. الاستجابة قد تتضمن `autoPrescription` عند نجاح الخطوة 4.

## حساب `UserLevelProfile`

المصدر: `backend/src/modules/level-profile/level-profile.service.ts` — `calculateFromAssessment`.

- يقرأ `BodyScanResult` بالمعرف.
- `overallLevel = scoreToLevel(bodyScore)`.
- مستويات النطاقات: `mobility`, `control`, `safety`، و`symmetry` إن وُجد `symmetryScore`.
- مستويات الأقاليم: تجميع عناصر `regions` حسب `region`، متوسط `regionalScore`، ثم `scoreToLevel`؛ `isLimiting: level < overallLevel - 1`.
- `limitingFactors`: نطاقات أو أقاليم بمستوى أقل من `overallLevel - 1` بمقدار فجوة، مرتبة حسب `gap`.
- `upsert` على `UserLevelProfile` بمفتاح فريد `assessmentId` (سجل واحد لكل تقييم).

## تحويل الدرجة إلى رقم مستوى

المصدر: `backend/src/lib/metrics/metrics-contract.ts`.

- `scoreToLevel` (sync): يستخدم ذاكرة تخزين مؤقت لمستويات من جدول `Level` في قاعدة البيانات؛ TTL تقريبًا 5 دقائق؛ عند الفراغ أو الفشل تُستخدم عتبات افتراضية (0–25 مستوى 1، …، ≥85 مستوى 5).
- `scoreToLevelAsync` يحمّل من قاعدة البيانات بشكل صريح.

## أنواع التقييم في المخطط

`AssessmentType` في `assessment.types.ts`: `'initial' | 'periodic' | 'post_program' | 'progression' | 'level_specific'`.

قيمة العمود `type` في `BodyScanResult` تُخزَّن كما أرسلها العميل؛ منطق الخادم أعلاه لا يغيّرها تلقائيًا عند الإنشاء في `assessment.service`.

## android-poc

- رفع التقييم: `MobileSyncApi.uploadAssessment` → `POST api/assessment`.
- القالب: `AssessmentTemplateManager.resolve` يستدعي `resolveAssessmentTemplate` (انظر ملف قوالب التقييم).
