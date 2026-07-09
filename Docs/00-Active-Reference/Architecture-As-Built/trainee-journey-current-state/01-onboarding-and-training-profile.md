# Onboarding وجمع سمات المتدرب (Training Profile)

| | |
|---|---|
| **Status** | `ACTIVE` |
| **SSOT for** | `TrainingProfile` schema + mobile onboarding flow |
| **Code** | `backend/prisma/schema.prisma`, `kmp-app/feature/account/MovitOnboardingScreen.kt`, `backend/src/modules/training-profile/` |
| **Verified** | 2026-06-22 |

---

## Onboarding في KMP (منفّذ)

| العنصر | الملف |
|--------|--------|
| شاشة 7 خطوات | `kmp-app/feature/account/.../MovitOnboardingScreen.kt` |
| ViewModel | `MovitOnboardingViewModel.kt` |
| Route | `MovitOnboardingRoute.kt` → `MovitInnerRoute.ProfileOnboarding` |
| حفظ | `SharedOnboardingRepository.putTrainingProfile` |

الخطوات (`OnboardingData.STEP_COUNT = 7`): عمر/جنس · مقاييس · خبرة وأيام/أسبوع · هدف · أيام التدريب · مكان/معدات · ملخص + `healthDisclaimerAccepted`.

يُدفع تلقائيًا عند أول جلسة إذا الملف ناقص (`MovitAppShellViewModel` + `OnboardingCompletion`).

---

## مخطط البيانات (Backend)

المصدر: `backend/prisma/schema.prisma` — `TrainingProfile` (1:1 مع `User`).

حقول رئيسية: `heightCm`, `weightKg`, `dateOfBirth`, `biologicalSex`, `currentActivityLevel`, `trainingExperienceMonths`, `resistanceExperience`, `availableDaysPerWeek`, `trainingWeekdays`, `maxWorkoutMinutes`, `availableEquipment`, `trainingLocation`, `knownInjuries`, `healthDisclaimerAccepted`.

`User.trainingGoal` (`TrainingGoal` enum) يُحدَّث مع نفس الحمولة.

---

## API

| الطريقة | المسار | الملف |
|--------|--------|--------|
| GET | `/mobile/training-profile` | `backend/src/modules/training-profile/mobile-training-profile.controller.ts` |
| PUT | `/mobile/training-profile` | `training-profile.service.ts` → `upsertTrainingProfile` |

أنواع الحمولة: `training-profile.types.ts` — `TrainingProfilePayload`.

عميل KMP: `MovitMobileApi` + `AccountSyncRepository` · DTO: `TrainingProfilePutRequest` في `core/network/dto/`.

---

## مطابقة السمات

`buildUserAttributeSet` في `backend/src/lib/attribute-matching.ts` — هدف التدريب، معدات، جنس، مكان، تلميحات التقييم عند توفرها.

---

## Admin-Dashboard

لا صفحة إدارة لملف تدريب المستخدم النهائي؛ التعديل من تطبيق الموبايل فقط.
