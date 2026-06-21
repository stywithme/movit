# توثيق المسار الحالي للمتدرب (POSE)

| | |
|---|---|
| **Status** | `ACTIVE` |
| **SSOT for** | Trainee journey as implemented in code |
| **Planned journey** | [Journey-Index.md](../../Product-Master/Journey-Index.md) |
| **Verified** | 2026-05-29 |

هذا المجلد يصف **الوضع الحالي في الكود** (Backend، Admin-Dashboard، kmp-app) دون تفسيرات تشغيلية أو توصيات.

## الملفات

| الملف | المحتوى |
|--------|---------|
| [01-onboarding-and-training-profile.md](./01-onboarding-and-training-profile.md) | جمع سمات المتدرب، نموذج `TrainingProfile`، واجهات الموبايل |
| [02-assessment-templates-and-resolve.md](./02-assessment-templates-and-resolve.md) | اختيار قالب الاختبار (initial / progression)، المطابقة بالسمات |
| [03-body-scan-level-profile.md](./03-body-scan-level-profile.md) | رفع نتيجة الـ Body Scan، `UserLevelProfile`، ربط المستوى بالدرجات |
| [04-prescription-and-program-enrollment.md](./04-prescription-and-program-enrollment.md) | محرك التوصية بالبرنامج، التسجيل في الخطة النشطة |
| [05-program-completion-reassessment.md](./05-program-completion-reassessment.md) | إكمال البرنامج، جدولة إعادة التقييم، الانتقال للبرنامج التالي |
| [06-mobile-home-train-mode.md](./06-mobile-home-train-mode.md) | `GET /mobile/home` وحالات `trainMode` |
| [07-admin-dashboard.md](./07-admin-dashboard.md) | صفحات الإدارة ذات الصلة والـ API المستهلكة |
| [08-kmp-app.md](./08-kmp-app.md) | تدفق التطبيق التجريبي ونقاط الاتصال بالخادم |

## مسار مبسّط (كما يظهر في التنفيذ الحالي)

1. **تعريف المتدرب (سمات ثابتة/مفضّلة):** يُخزَّن في `TrainingProfile` و`User.trainingGoal` عبر واجهات الموبايل؛ محتوى شاشة `OnboardingActivity` في kmp-app **لا** يملأ هذا النموذج.
2. **قالب الاختبار:** `GET /mobile/assessment-templates/resolve` يختار قالبًا منشورًا حسب `mode` (افتراضيًا `initial`) ومطابقة السمات ثم احتياطي `isDefault`.
3. **تنفيذ الاختبار ورفع النتيجة:** `POST /api/assessment` ينشئ `BodyScanResult` ثم يحسب `UserLevelProfile` ويحدّث جداول إعادة التقييم عند الاقتضاء؛ وقد يُسجَّل المستخدم تلقائيًا في برنامج إذا لم يكن لديه برنامج نشط في الخطة.
4. **المستوى:** يُشتق من درجات التقييم عبر `scoreToLevel` ومستويات قاعدة البيانات (أو عتبات افتراضية).
5. **البرنامج:** التوصية عبر `prescriptionService.recommend`؛ التسجيل اليدوي عبر `POST /mobile/plan/enroll` أو التلقائي بعد أول تقييم عند عدم وجود برنامج نشط.
6. **إعادة التقييم والانتقال:** تقييم إكمال البرنامج (`programCompletionService.evaluate`) يحدد `nextAction`؛ `POST /mobile/plan/complete` يُكمل الفتحة النشطة ويُفعّل الفتحة `upcoming` التالية في نفس الخطة إن وُجدت.

للتفاصيل والملفات والمسارات، افتح كل ملف أعلاه.
