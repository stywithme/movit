# Onboarding وجمع سمات المتدرب (Training Profile)

## مصطلحات

- **Onboarding في android-poc:** يشير إلى `OnboardingActivity` — شاشات تعريفية (3 صفحات) ثم الانتقال إلى `SignInActivity`. لا يوجد في هذا النشاط استدعاء لـ API لملف التدريب.
- **سمات المتدرب في الخادم:** النموذج `TrainingProfile` في Prisma، علاقة 1:1 مع `User`، مع حقل `trainingGoal` على `User`.

## مخطط البيانات (Backend)

المصدر: `backend/prisma/schema.prisma` — نموذج `TrainingProfile`.

حقول مخزّنة (غير حصرية للعرض في الواجهة):

- `heightCm`, `weightKg`, `dateOfBirth`, `biologicalSex`
- `currentActivityLevel`, `trainingExperienceMonths`, `resistanceExperience`
- `availableDaysPerWeek`, `trainingWeekdays` (مصفوفة أرقام أيام 0=الأحد … 6=السبت)
- `maxSessionMinutes`, `availableEquipment` (Json), `trainingLocation`, `knownInjuries` (Json)
- `healthDisclaimerAccepted` (boolean)

`User.trainingGoal` من enum `TrainingGoal` ويُحدَّث من نفس حمولة ملف التدريب عند الإرسال.

## أنواع الـ API (Backend)

| الطريقة | المسار | الملف |
|--------|--------|--------|
| GET | `/mobile/training-profile` | `backend/src/modules/training-profile/mobile-training-profile.controller.ts` |
| PUT | `/mobile/training-profile` | نفس الملف — يستدعي `upsertTrainingProfile` |

الخدمة: `backend/src/modules/training-profile/training-profile.service.ts` — `upsertTrainingProfile` يحدّث `User.trainingGoal` داخل معاملة ثم `upsert` على `TrainingProfile`.

أنواع الحمولة: `backend/src/modules/training-profile/training-profile.types.ts` — `TrainingProfilePayload`.

## بناء مجموعة السمات لمطابقة البرامج/القوالب

`buildUserAttributeSet` في `backend/src/lib/attribute-matching.ts` يبني `Set<string>` من:

- `requiredType` من التلميحات (افتراضيًا `training`) → رمز نطاق
- هدف التدريب → رمز
- عناصر `availableEquipment` من الملف الشخصي
- `focusHint`, `regionHints` عند تمريرها (تُستخدم في وصفات البرنامج بعد التقييم)
- `biologicalSex`, `trainingLocation`

## android-poc

- **Onboarding:** `android-poc/.../ui/auth/OnboardingActivity.kt` — تفضيل `is_first_launch` فقط.
- **ملف التدريب:** `PUT api/mobile/training-profile` من `ProfileActivity` (بحث عن `putTrainingProfile` في `ProfileActivity.kt`).
- تعريف الـ Retrofit: `MobileSyncApi.kt` — `getTrainingProfile`, `putTrainingProfile`.

## Admin-Dashboard

لا توجد في المستودع (حسب البحث في المسارات الشائعة) صفحة إدارة مخصّصة لملف تدريب المستخدم النهائي؛ الإدارة تركز على البرامج، قوالب التقييم، المستويات، إلخ. تعديل سمات المتدرب يتم من تطبيق الموبايل أو أدوات أخرى خارج هذا المسار الموثّق هنا.
