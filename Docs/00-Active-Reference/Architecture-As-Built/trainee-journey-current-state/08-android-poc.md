# android-poc — تدفق المتدرب ونقاط الاتصال

## المصادقة والإقلاع

- `SplashActivity` يوجّه المستخدم الجديد إلى `OnboardingActivity` عند `isFirstLaunch` (تفضيلات `app_prefs`).
- `OnboardingActivity`: شاشات تعريفية فقط ثم `SignInActivity` — **لا** يرسل بيانات `TrainingProfile`.

## جمع السمات (ملف التدريب)

- عرض/تحديث جزئي من `ProfileActivity` عبر `GET/PUT api/mobile/training-profile`.
- حقول إضافية للتفضيلات قد تظهر في حوارات أخرى (مثل `TrainingPreferenceDialogs.kt` حسب الملفات المعدّلة في المستودع).

## الاستعداد للتقييم

- `PreScreeningActivity`: وصف الملف يشير إلى استبيان PAR-Q+ — مسار واجهة قبل التقييم من `HomeFragment`, `PlanOverviewActivity`, `TrainFragment`, إلخ.

## القالب والجلسة

- `AssessmentTemplateManager` (`assessment/engine/AssessmentTemplateManager.kt`): `resolve` يستدعي `GET api/mobile/assessment-templates/resolve` **بدون** `mode` → الخادم يستخدم `initial`.
- قائمة تمارير افتراضية ثابتة في الكلاس عند فشل الشبكة (`DEFAULT_CORE_EXERCISES`).
- `AssessmentSessionActivity` — تنفيذ الجلسة؛ `AssessmentResultActivity` — النتائج والتسجيل في برنامج عند العرض.

## رفع التقييم

- `POST api/assessment` عبر `uploadAssessment` في `MobileSyncApi.kt`.

## الصفحة الرئيسية والتدريب

- `GET api/mobile/home` — `HomeFragment` يستخدم `trainMode.status` للتوجيه (مثلاً فتح `PreScreeningActivity` عند الحاجة).
- `HomeRepository.syncFromServer` بعد العمليات المهمة.

## البرامج والخطة

- `GET api/mobile/plan`, `POST api/mobile/plan/enroll`, `POST api/mobile/plan/complete` — تعريف في `MobileSyncApi.kt`.
- `ProgramDetailActivity` + `ProgramDetailViewModel` — فحص `enrollment-check` ثم `enroll`.
- `TrainFragment` — عند إكمال البرنامج: `completeActiveProgram` ثم التفرع حسب `completion` (انظر ملف إكمال البرنامج في التوثيق).

## مستوى المتدرب

- `GET api/mobile/level-profile` و`history` — تعريف في `MobileSyncApi.kt`؛ شاشة `LevelProfileActivity` تتضمن مسارات لـ `PreScreeningActivity`.

## ملف الشبكة المركزي

`android-poc/app/src/main/java/com/trainingvalidator/poc/network/MobileSyncApi.kt` يجمع معظم مسارات REST أعلاه.
