# الشاشة الرئيسية: `GET /mobile/home` و`trainMode`

| | |
|---|---|
| **Status** | `ACTIVE` |
| **SSOT for** | Mobile home payload and trainMode states |
| **Code** | `backend/src/modules/mobile-sync/mobile-home.controller.ts`, `kmp-app/feature/home/`, `kmp-app/feature/train/TrainApiMapper.kt` |
| **Verified** | 2026-06-22 |

---

## Backend

المتحكم: `mobile-home.controller.ts` — `buildHomeData`, `buildTrainMode`, `buildAlerts`.

**بيانات المستخدم:** `UserLevelProfile`، `bodyScore`، إحصاءات، `recentWorkoutExecutions`.

---

## حالات `trainMode.status`

| الحالة | الشرط |
|--------|--------|
| `no_assessment` | لا `BodyScanResult` |
| `reassessment_due` | `ReassessmentSchedule` `pending` و`scheduledDate <= now` |
| `no_plan` | تقييم موجود لكن لا فتحة/برنامج نشط |
| `program_complete` | `position.isProgramComplete` |
| `rest_day` | يوم راحة أو خارج الجدول أو `day_complete` |
| `active` | تمرين مخطط غير مكتمل |

---

## kmp-app

| الطبقة | الملف |
|--------|--------|
| HTTP | `MovitMobileApi.fetchHome` → `GET api/mobile/home` |
| Cache + sync | `HomeSyncRepository.kt` · `MovitSyncOrchestrator` |
| Home UI | `SharedHomeRepository` · `MovitHomeViewModel` |
| Train UI | `MovitTrainViewModel` · `TrainApiMapper` (يحوّل `trainMode` إلى `TrainDashboardStatus`) |
| إصلاح trainMode | `HomeTrainModeHydrator.kt` عند تعارض assessment/no-plan |

التبويبان Home وTrain يستهلكان نفس مصدر البيانات؛ التوجيه إلى Assessment/Workout عبر `MovitAppShellViewModel` effects.

---

## تنبيهات

`buildAlerts` — `reassessment_due`, `progression_applied`, `level_up`, `streak_at_risk`.
