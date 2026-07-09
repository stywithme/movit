# Body Scan وملف المستوى (User Level Profile)

| | |
|---|---|
| **Status** | `ACTIVE` |
| **SSOT for** | Assessment upload → level profile |
| **Code** | `backend/src/modules/assessment/`, `kmp-app/feature/account/MovitAssessmentViewModel.kt` |
| **Verified** | 2026-06-22 |

---

## إنشاء نتيجة التقييم

| الطريقة | المسار | الملف |
|--------|--------|--------|
| POST | `/api/assessment` | `assessment.controller.ts` → `assessmentService.create` |

الحمولة: `BodyScanResultCreate` في `assessment.types.ts` — `bodyScore` إلزامي، درجات نطاقات، `regions`, إلخ.

### داخل `assessmentService.create`

1. `bodyScanResult.create`
2. `levelProfileService.calculateFromAssessment`
3. `reassessmentService.markCompleted`
4. **تسجيل تلقائي:** إن لا فتحة `active` → `prescriptionService.recommend` + `activePlanService.enrollProgram`
5. الاستجابة قد تتضمن `autoPrescription`

---

## `UserLevelProfile`

`level-profile.service.ts` — `calculateFromAssessment`: `overallLevel = scoreToLevel(bodyScore)`، نطاقات، أقاليم، `limitingFactors`.

`scoreToLevel`: `backend/src/lib/metrics/metrics-contract.ts` (جدول `Level` أو عتبات افتراضية).

---

## kmp-app

| المرحلة | الملف |
|---------|--------|
| مسح موجّه + كاميرا | `MovitAssessmentScreen` · `AssessmentCameraHost` · `AssessmentBodyScanEngine` |
| بناء النتيجة | `MovitAssessmentViewModel.completeBodyScan` |
| رفع | `SharedAssessmentRepository.submitBodyScan` → `AccountSyncRepository.uploadAssessment` → `POST api/assessment` |
| عرض النتائج | Results phase؛ قد تستخدم `fetchLevelProfile` عند الحاجة |

قبل المسح: PAR-Q (7 أسئلة) في نفس الشاشة.

---

## أنواع التقييم

`AssessmentType`: `initial` | `periodic` | `post_program` | `progression` | `level_specific` — تُخزَّن كما يرسلها العميل.
