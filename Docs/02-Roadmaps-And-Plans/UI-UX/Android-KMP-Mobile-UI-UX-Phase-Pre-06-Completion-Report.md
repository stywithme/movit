# Android / KMP Mobile UI/UX — Phase Pre-06: تقرير الإكمال

آخر تحديث: **2026-06-09**  
الحالة: **مغلقة (CLOSED)** — جميع مسارات العمل WS-A→WS-G مُنجَزة؛ بوابة الخروج Pre-06 **تمر** مع ديون موثّقة أدناه.  
المرجع التنفيذي: [`Android-KMP-Mobile-UI-UX-Phase-Pre-06-Architecture-And-Production-Readiness-Plan.md`](Android-KMP-Mobile-UI-UX-Phase-Pre-06-Architecture-And-Production-Readiness-Plan.md)

---

## ملخص تنفيذي للمدير

**Phase Pre-06** لم تضف شاشات جديدة؛ هدفها تحويل أساس KMP من **debug pilot** إلى **مسار إنتاج قابل للثقة** قبل توسيع Program/Workout flow (صفحات 15/16).

| ما أُنجز | الأثر |
|----------|--------|
| زر الرجوع في Shell | لا يغلق التطبيق أثناء المسارات الداخلية |
| محرك تدريب مشترك (`:core:training-engine`) | iOS يشارك RepCounter / PhaseStateMachine / ScoreCalculator مع Android legacy |
| تخزين جلسة آمن | Tokens في EncryptedSharedPreferences (Android) و Keychain (iOS) — ليس في prefs عادي |
| Launcher Gate | قرار مكتوب + هيكل جاهز — **بدون flip إنتاجي** (مقصود) |
| Scorecards + Visual QA | نسب الإنجاز قابلة للمحاسبة؛ checklist يدوي للشاشات الأساسية |

**التحقق الآلي (2026-06-09):** كل أوامر Gradle المطلوبة **BUILD SUCCESSFUL** — 66 اختبار وحدة عبر shell / training-engine / data / account، + `assembleDebug` + `compileKotlinIosSimulatorArm64` للـ shell.

**الخطوة التالية الموصى بها:** استئناف **Phase 05** على صفحات **15 (Program flow)** و **16 (Workout flow)**، مع الالتزام بـ scorecards و Visual QA checklist قبل رفع أي نسبة.

---

## جدول مسارات العمل (WS-A → WS-G)

| WS | المخرج المتوقع | الحالة | التحقق من الكود |
|----|----------------|--------|-----------------|
| **WS-A** | `BackHandler` · `handleSystemBack()` · اختبارات shell | ✅ | `MovitAppShell.kt` (BackHandler) · `MovitAppShellViewModel.handleSystemBack()` · **20** اختبار في `MovitAppShellStateTest` (3 سيناريوهات back مباشرة) |
| **WS-G** | لا placeholders hardcoded في مسار الإنتاج؛ WorkoutSession من بيانات الجلسة | ✅ | لا `ex-squat-warm` في `feature/shell/commonMain` · `WorkoutSessionKeys.encode` + `firstExerciseSlug()` في `feature:library` · Report share → `shell_report_share_coming_soon` من resources |
| **WS-B** | `:core:training-engine` — RepCounter · PhaseStateMachine · ScoreCalculator · legacy bridge · 18 اختبار · iOS compile | ✅ | 13 ملف `commonMain` · wrappers في `app/.../training/engine/` تفوّض إلى KMP · **18** اختبار · `compileKotlinIosSimulatorArm64` أخضر |
| **WS-C** | Launcher Gate doc · `MovitMainActivity` · feature flag · `LegacyTrainingLauncher` · **لا flip إنتاجي** | ✅ | [`Android-KMP-Mobile-UI-UX-Launcher-Gate.md`](Android-KMP-Mobile-UI-UX-Launcher-Gate.md) · `movit.shell.launcher.enabled=false` · `SplashActivity` LAUNCHER |
| **WS-D** | `SecureSessionStore` · Android encrypted · iOS Keychain · ترحيل · اختبارات | ✅ | `AndroidSecureSessionStore` · `IosKeychainSecureSessionStore` · `SecureSessionMigrationTest` · `AccountSyncRepositorySecureSessionTest` · `AuthManager` يحفظ tokens عبر secure store فقط |
| **WS-E** | `Page-Scorecards.md` · تحديث `Sync-App-Pages` · status doc | ✅ | 12 صفحة Phase 05 بمنهجية 7 مجالات · جدول scorecards في `Sync-App-Pages.md` متطابق |
| **WS-F** | Visual QA checklist · theme boundary tests | ✅ | [`Android-KMP-Mobile-Visual-QA-Checklist.md`](Android-KMP-Mobile-Visual-QA-Checklist.md) · `*ThemeBoundaryTest` في account · reports · home · train · explore |

---

## القرارات المعمارية المهمة

### 1) Navigation (WS-A)

- **القرار:** الاستمرار على router داخلي (`innerStack` + `MovitAppShellViewModel`) مع `BackHandler` من `ui-backhandler` حتى صفحات 15/16.
- **سلوك الرجوع:** inner stack → pop · تبويب غير Home → Home · Home فارغ → لا يستهلك الحدث (يغلق النشاط).
- **إعادة التقييم:** Navigation Compose Multiplatform قبل Program/Workout flow إذا زاد تعقيد الـ stack.
- **لم يُنفَّذ (مدى متوسط):** saved state عند process death · deep links.

### 2) Launcher timing (WS-C)

- **القرار (2026-06-09):** لا flip إنتاجي في Pre-06. `SplashActivity` يبقى LAUNCHER.
- **جاهز للمستقبل:** `MovitMainActivity` + `BuildConfig.MOVIT_SHELL_LAUNCHER_ENABLED` + `LegacyTrainingLauncher` للكاميرا.
- **Flip المقترح:** بعد WS-D + visual smoke؛ **مثالي بعد صفحات 15/16**.

### 3) Secure storage (WS-D)

- **القرار:** `SecureSessionStore` عبر `MovitPlatformBindings` — `commonMain` لا يعرف Keychain ولا EncryptedSharedPreferences.
- **ترحيل:** قراءة مرة واحدة من مفاتيح legacy (`access_token` / `refresh_token`) ثم حذفها.
- **مسار الإنتاج:** `AuthManager.saveAuthData` → `AndroidSecureSessionStore` فقط؛ logout يمسح secure + مفاتيح legacy.

### 4) Training engine boundary (WS-B)

- **القرار:** موديول `:core:training-engine` في `commonMain` — geometry · filter · RepCounter · PhaseStateMachine · ScoreCalculator.
- **Legacy:** wrappers في `:app` تفوّض إلى KMP (لا نسخ مزدوج للمنطق).
- **خارج النطاق (متعمد):** AssessmentEngine · CameraX · MediaPipe · LiteRT — Phase 07.

### 5) Scorecards methodology (WS-E)

- **7 مجالات بأوزان ثابتة** (Functional 25% · Visual 20% · DS 15% · i18n 15% · A11y 10% · Tests 10% · iOS 5%).
- **لا تُرفع نسبة** بدون إغلاق فجوات محددة في [`Page-Scorecards.md`](Page-Scorecards.md).
- **مصدر الحقيقة للنسب:** Page-Scorecards (وليس تقديرات قديمة في Professional Plan).

### 6) Visual QA gate (WS-F)

- **قرار Pre-06:** manual checklist + theme boundary unit tests؛ screenshot automation لاحقاً.
- **بوابة إغلاق صفحة:** لا تُعلَم صفحة «مكتملة» بدون مرور الحالات في Visual QA checklist (light / dark / ar / loading / empty / error حسب الانطباق).

---

## أوامر التحقق ونتائجها (2026-06-09)

```powershell
cd android-poc
.\gradlew.bat --console=plain `
  :feature:shell:testDebugUnitTest `
  :core:training-engine:testDebugUnitTest `
  :core:data:testDebugUnitTest `
  :feature:account:testDebugUnitTest `
  :app:assembleDebug `
  :feature:shell:compileKotlinIosSimulatorArm64
```

| الأمر | النتيجة | التفاصيل |
|-------|---------|----------|
| `:feature:shell:testDebugUnitTest` | ✅ | **20** tests · 0 failures |
| `:core:training-engine:testDebugUnitTest` | ✅ | **18** tests · 0 failures |
| `:core:data:testDebugUnitTest` | ✅ | **17** tests · 0 failures |
| `:feature:account:testDebugUnitTest` | ✅ | **11** tests · 0 failures |
| `:app:assembleDebug` | ✅ | BUILD SUCCESSFUL |
| `:feature:shell:compileKotlinIosSimulatorArm64` | ✅ | BUILD SUCCESSFUL (تحذير deprecation على `BackHandler`) |

**الخلاصة:** `BUILD SUCCESSFUL in ~1m 3s` — 412 actionable tasks.

### فحوص نصية (grep)

| الفحص | النتيجة |
|-------|---------|
| `^import android\.` / `^import java\.` في `training-engine` و `shell` commonMain | ✅ صفر |
| `ex-squat-warm` / `Share report` / `Export report` في shell commonMain | ✅ صفر |
| `access_token` في مسار كتابة prefs عادي (إنتاج) | ✅ لا كتابة — فقط قراءة/حذف legacy + تخزين آمن (`secure_access_token`) |

---

## بوابة خروج Pre-06 — حالة البنود

| البند | الحالة | ملاحظة |
|-------|--------|--------|
| زر الرجوع مع inner routes | ✅ | `handleSystemBack` + BackHandler |
| اختبارات back stack | ✅ | 3 اختبارات back + 20 إجمالي shell |
| لا ids/رسائل مؤقتة في مسار الإنتاج | ✅ | Report share من resources؛ انظر دين WS-G أدناه |
| pure training engine في commonMain + iOS | ✅ | `:core:training-engine` |
| tokens ليست في storage عادي (إنتاج) | ✅ | WS-D |
| scorecard لكل صفحة Phase 05 | ✅ | 12 صفحة في Page-Scorecards |
| Visual QA checklist للشاشات الأساسية | ✅ | manual checklist موثّق |
| قرار launcher مكتوب | ✅ | Launcher Gate — لا flip في Pre-06 |

---

## فجوات / ديون متبقية (صادقة)

| # | البند | الخطورة | الملاحظة |
|---|-------|---------|----------|
| 1 | `MovitPlaceholderScreen.kt` | منخفضة | ملف **غير مستخدم** (dead code) يحتوي `"Coming soon"` / `"Notify me"` hardcoded — ليس في مسار الإنتاج؛ يُفضّل حذفه أو نقله لـ resources عند التنظيف |
| 2 | `AssessmentEngine` | متوسطة (Phase 07) | لم يُنقل إلى `:core:training-engine` — خارج نطاق «أول حزمة» WS-B |
| 3 | Navigation saved state / deep links | متوسطة | مؤجّل لما قبل/أثناء 15/16 |
| 4 | Visual QA | متوسطة | checklist **يدوي** — لا screenshot automation بعد |
| 5 | Launcher flip | مقصود | `MovitMainActivity` يعيد التوجيه لـ `SplashActivity` — release classpath خالٍ من Movit |
| 6 | Accessibility | متوسطة | معظم الصفحات 20–30% في scorecard — فجوة مشتركة Phase 05 |
| 7 | `BackHandler` deprecated | منخفضة | تحذير compiler — الترحيل لـ `NavigationEventHandler` لاحقاً |
| 8 | iOS token keys sync | متوسطة | Status doc: مزامنة `access_token` مع تطبيق iOS الأصلي ما زالت مطلوبة للبيانات الحية |

**لا blockers** تمنع إغلاق Pre-06 أو بدء صفحات 15/16.

---

## الخطوة التالية الموصى بها

1. **Phase 05 — صفحة 15 (Program flow):** تدفق برنامج كامل داخل shell مع الاستفادة من WS-A back stack.
2. **Phase 05 — صفحة 16 (Workout flow):** ربط `LegacyTrainingLauncher` عند `ExercisePrepareRoute.onStart` حيث ينطبق.
3. **قبل رفع scorecards:** مرور Visual QA checklist + إغلاق فجوات A11y تدريجياً.
4. **بعد 15/16:** مراجعة Launcher Gate go/no-go للـ soft launch.

---

## مراجع

- [خطة Pre-06](Android-KMP-Mobile-UI-UX-Phase-Pre-06-Architecture-And-Production-Readiness-Plan.md)
- [Launcher Gate](Android-KMP-Mobile-UI-UX-Launcher-Gate.md)
- [Page Scorecards](Page-Scorecards.md)
- [Visual QA Checklist](Android-KMP-Mobile-Visual-QA-Checklist.md)
- [حالة التحديث](Android-UI-UX-Modernization-Status.md)
- [الخطة المهنية](Android-KMP-Mobile-UI-UX-Professional-Plan.md)
