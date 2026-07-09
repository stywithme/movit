# kmp-app — غلاف KMP ومسار المتدرب

| | |
|---|---|
| **Status** | `ACTIVE` |
| **SSOT for** | تدفق الموبايل الحالي (Movit KMP shell) |
| **Code** | `kmp-app/app/.../MovitMainActivity.kt`, `kmp-app/feature/shell/`, `kmp-app/core/network/MovitMobileApi.kt` |
| **Supersedes** | [08-android-poc.md](./08-android-poc.md) (أرشيف) |
| **Verified** | 2026-06-22 |

---

## نقطة الدخول

| العنصر | الملف |
|--------|--------|
| LAUNCHER الإنتاج | `kmp-app/app/src/movitShellEnabled/java/com/movit/MovitMainActivity.kt` |
| ربط Compose + DI | `kmp-app/app/src/movitShellHost/java/com/movit/host/MovitShellHost.kt` → `MovitAppShellHost` |
| ما بعد تسجيل الدخول | `kmp-app/app/src/main/java/com/movit/navigation/MovitPostLoginNavigator.kt` → دائمًا `MovitMainActivity` |

لا يوجد `SplashActivity` ولا `OnboardingActivity` في المسار الإنتاجي الحالي.

---

## الغلاف (Shell)

### تبويبات عائمة

المصدر: `kmp-app/feature/shell/.../MovitAppDestination.kt` · `MovitAppShell.kt`

| التبويب | المسار | الشاشة |
|---------|--------|--------|
| Home | `home` | `MovitHomeRoute` |
| Train | `train` | `MovitTrainRoute` |
| Explore | `explore` | `MovitExploreRoute` |
| Reports | `reports` | `MovitReportsRoute` |
| Profile | `profile` | `MovitProfileRoute` (من التبويب أو inner route) |

التنقل الداخلي (فوق التبويبات): `MovitInnerRoute` — Auth، ProfileOnboarding، Assessment، ProgramDetail، WorkoutSession، LevelProfile، إلخ. المنسّق: `MovitAppShellViewModel`.

### Bootstrap بعد الجلسة

`MovitAppShellViewModel` يفحص اكتمال ملف التدريب؛ إن كان ناقصًا يدفع `MovitInnerRoute.ProfileOnboarding` تلقائيًا (`OnboardingCompletion` + `GET api/mobile/training-profile`).

---

## Onboarding (7 خطوات)

| العنصر | الملف |
|--------|--------|
| UI | `kmp-app/feature/account/.../MovitOnboardingScreen.kt` |
| ViewModel | `MovitOnboardingViewModel.kt` |
| الحفظ | `SharedOnboardingRepository.putTrainingProfile` → `PUT api/mobile/training-profile` |
| نموذج الخطوات | `OnboardingData.kt` — `STEP_COUNT = 7` |

الخطوات: عمر/جنس → مقاييس الجسم → خبرة وأيام/أسبوع → هدف → أيام التدريب → مكان/معدات → ملخص + إقرار صحي.

---

## التقييم (Assessment)

| العنصر | الملف |
|--------|--------|
| UI | `MovitAssessmentScreen.kt` — PAR-Q → Body scan → Results |
| ViewModel | `MovitAssessmentViewModel.kt` |
| قالب | `SharedAssessmentRepository.resolveTemplate(mode)` → `GET api/mobile/assessment-templates/resolve?mode=...` |
| كاميرا حية | `assessment/AssessmentCameraHost.android.kt` (CameraX) · `AssessmentCameraHost.ios.kt` |
| محرك المسح | `AssessmentBodyScanEngine.kt` |
| رفع النتيجة | `uploadAssessment` → `POST api/assessment` |

وضع `progression` يُمرَّر عند فتح التقييم من إعادة التقييم (`MovitInnerRoute.Assessment(mode = ...)`).

---

## Home و trainMode

| العنصر | الملف |
|--------|--------|
| API | `MovitMobileApi.fetchHome` → `GET api/mobile/home` |
| Repository | `HomeSyncRepository.kt` · `SharedHomeRepository.kt` |
| Home UI | `MovitHomeViewModel.kt` |
| Train UI | `MovitTrainViewModel.kt` + `TrainApiMapper.kt` (يترجم `trainMode.status`) |

تفاصيل حالات `trainMode`: [06-mobile-home-train-mode.md](./06-mobile-home-train-mode.md).

---

## التدريب والخطة

| العملية | العميل | API |
|---------|--------|-----|
| تسجيل برنامج | `AccountSyncRepository` / شاشات البرنامج | `POST api/mobile/plan/enroll` |
| إكمال تمرين مخطط | `TrainingSessionWriteHooks.completePlannedDay` | `POST api/mobile/planned-workouts/{id}/complete` |
| إكمال الخطة/البرنامج | **غير مربوط من UI** | `POST api/mobile/plan/complete` — موجود في `MobileWriteSyncRepository.completePlan` والـ outbox فقط |

**فجوة:** لا يستدعي أي ViewModel في `feature/*` اليوم `completePlan`؛ إكمال البرنامج و`nextAction` من الخادم غير موصولين بواجهة Train.

---

## طبقة الشبكة

العميل الموحّد: `kmp-app/core/network/src/commonMain/kotlin/com/movit/core/network/MovitMobileApi.kt`  
سجل العقد: `MobileApiContractRegistry.kt` (اختبارات التغطية).

---

## فجوات صريحة (غير منفّذة في KMP)

| الفجوة | ملاحظة |
|--------|--------|
| `completePlan` UI | API + outbox جاهزان؛ لا استدعاء من شاشات المستخدم |
| أحداث البقاء (events) | لا تتبع أحداث منتج في العميل — انظر [17-Events-Implementation-Ticket.md](../../../01-Business-Planning/17-Events-Implementation-Ticket.md) |
| لحظة Aha | لا مسار «حركة واحدة» قبل التقييم — انظر [19-Onboarding-and-Aha-Spec.md](../../../01-Business-Planning/19-Onboarding-and-Aha-Spec.md) |
| حجز المواعيد (Booking) | **ملغى** — أُزيل من Backend وAdmin-Dashboard |
