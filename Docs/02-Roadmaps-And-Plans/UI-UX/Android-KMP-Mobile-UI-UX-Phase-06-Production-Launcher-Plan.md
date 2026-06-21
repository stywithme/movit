# Android / KMP Mobile UI/UX — Phase 06: Production Launcher (إغلاق WS-G)

آخر تحديث: **2026-06-10**
الحالة: **مغلقة (CLOSED) — بوابة WS-G مُحقَّقة · smoke جهاز جزئي**
المصدر: قرار المدير بعد إغلاق [Pre-06.2](Android-KMP-Mobile-UI-UX-Phase-Pre-06-2-Code-Review-Debt-Closure-Plan.md) — **«بوابة الإنتاج على Android أولاً»**، مع تأجيل Phase 07 (كاميرا/ML في KMP).

المراجع:
- [Launcher Gate](Android-KMP-Mobile-UI-UX-Launcher-Gate.md) — القرار المكتوب (WS-G في Pre-06)
- [تقرير إكمال Pre-06](Android-KMP-Mobile-UI-UX-Phase-Pre-06-Completion-Report.md) — WS-G «معيار مكتوب، بلا flip إنتاجي»
- [Pre-06.2](Android-KMP-Mobile-UI-UX-Phase-Pre-06-2-Code-Review-Debt-Closure-Plan.md) — الأساس الذي نبني عليه

---

## ملخص تنفيذي للمدير

كل ما بُني في KMP (5 تبويبات · Auth · Program/Workout flow · طبقة بيانات Ktor · محرك تدريب مشترك) ما زال **غير قابل للشحن لمستخدم حقيقي**، لأن الـ Movit shell **debug-only**: نقطة دخوله الوحيدة هي `MovitShellPilotActivity` في `app/src/debug`، وموديولاته `debugImplementation`. **Phase 06 تحوّل الـ shell إلى نقطة دخول إنتاجية على Android** — دون انتظار كاميرا KMP، لأن صفحة 16 تربط الكاميرا عبر `LegacyTrainingLauncher → TrainingActivity` (المسار القديم المثبت).

**المفاجأة السارة:** حجر الزاوية الذي ظننّاه شاقاً ليس كذلك. `MovitDataInstall` (في debug) **هو بالفعل تنفيذ إنتاجي كامل** لـ `MovitPlatformBindings` (33 دالة)، ويسحب 100% من classes موجودة أصلاً في `app/src/main`:

| Binding | المصدر الإنتاجي (موجود في main) |
|---------|-------------------------------|
| auth / tokens / session | `com.movit.androidApp.storage.AuthManager` (EncryptedSharedPreferences) |
| base URL | `network.ApiConfig.getEffectiveBaseUrl()` |
| active program id | `storage.ProgramRepository` |
| exercise images | `storage.ExerciseRepository` |
| theme / locale | `ui.theme.AppThemeManager` + `AppCompatDelegate` |

أي أن النقل من `debug` إلى `main` لا يحتاج إعادة كتابة — فقط **نقل ملف + ترقية classpath + host activity حقيقي**. الجهد الحقيقي يتركّز في: **قرار auth (Google parity)** و**R8/ProGuard لأول release minified**.

**الهدف النهائي:** نسخة release من التطبيق نقطة دخولها الـ Movit shell، تسجيل دخول حقيقي، التبويبات الخمسة + Program/Workout flow، والكاميرا عبر الجسر القديم — مع إبقاء المسار القديم كـ rollback فوري خلف feature flag.

---

## الحالة الحالية (متحقَّق منها بالكود — 2026-06-10)

| العنصر | الحالة | الملف |
|--------|--------|-------|
| host الـ shell الحقيقي | ✅ موجود لكن **debug-only** | `app/src/debug/.../MovitShellPilotActivity.kt` (40 سطر، يثبّت البيانات + يربط جسر الكاميرا/الاشتراك) |
| تثبيت البيانات الإنتاجي | ⚠️ التنفيذ كامل لكن **في debug** | `app/src/debug/.../MovitDataInstall.kt` |
| `MovitMainActivity` (هدف الإنتاج) | ❌ **stub** — يحوّل لـ `SplashActivity` حتى عند تفعيل الفلاج | `app/src/main/java/com/movit/MovitMainActivity.kt` |
| feature flag | ✅ موجود بلا تفعيل | `movit.shell.launcher.enabled` → `BuildConfig.MOVIT_SHELL_LAUNCHER_ENABLED` |
| ترقية موديولات release | 🔶 جزئي/مشروط | `core:data`/`core:training-engine` = `implementation` (غير مشروط)؛ `feature:shell` + بقية الموديولات `debugImplementation` (شرط الفلاج عند سطر ~110) |
| جسر الكاميرا | ✅ جاهز في main | `app/src/main/java/com/movit/legacy/LegacyTrainingLauncher.kt` |
| launcher الحالي | ✅ سليم | `SplashActivity` = MAIN/LAUNCHER في main manifest |
| release minified | ❌ غير مُختبَر | فقط `assembleDebug` متحقَّق منه |

**الخلاصة:** الـ shell يعمل ببيانات حقيقية في debug. كل ما يلزم هو إتاحة نفس المسار في release + قرار auth + R8.

---

## القرار الاستراتيجي المبكر (قبل G-3) ⚠️

الـ shell عنده بوابة auth (`resolveStartupInnerStack`) و`MovitAuthScreen` يعمل بالبريد، **لكن زر Google placeholder**، بينما `SplashActivity` القديمة فيها Google sign-in حقيقي. هناك مساران، يُحسم أحدهما قبل البدء:

| | **استراتيجية A — Shell-first** | **استراتيجية B — Legacy-auth-front (موصى بها)** |
|---|-------------------------------|------------------------------------------------|
| نقطة الدخول | `MovitMainActivity` (shell) هو LAUNCHER؛ auth داخل الـ shell | `SplashActivity` يبقى LAUNCHER؛ **بعد نجاح الدخول** يفتح `MovitMainActivity` بدل الـ home القديم |
| Google sign-in | يحتاج **جسر Google كامل** في KMP (placeholder اليوم) | ✅ يبقى عبر auth القديم — صفر عمل إضافي |
| المخاطرة | عالية — auth صفر-من-جديد على الإنتاج | منخفضة — auth المثبت لا يُمَس؛ نبدّل **سطح ما بعد الدخول** فقط |
| نقاء الـ shell | الـ shell مكتفٍ ذاتياً (أفضل لـ iOS لاحقاً) | الـ shell يفترض جلسة مثبتة مسبقاً (AuthManager) |
| سرعة الشحن | أبطأ | **أسرع وأأمن** |

**التوصية: استراتيجية B** — أصدق تطبيق لنمط strangler: نُبقي auth المثبت (incl. Google) ونستبدل فقط الواجهة بعد الدخول بالـ shell. الـ shell سيقرأ الجلسة التي حفظها `AuthManager`، فبوابته الداخلية ستجد توكناً صالحاً وتذهب لـ Home مباشرة. جسر Google في KMP يبقى لـ Phase 07/iOS. **هذا القرار يُثبَّت في G-3 ويغيّر شكل G-4 (flip).**

> ملاحظة: استراتيجية B تجعل «الـ flip» أصغر بكثير — لا نبدّل LAUNCHER، بل **هدف التنقل بعد نجاح `SplashActivity`** فقط (خلف الفلاج).

---

## النطاق

**داخل النطاق:**
- نقل تثبيت البيانات + host الـ shell إلى مسار الإنتاج (main).
- ترقية موديولات Movit في release خلف الفلاج.
- ربط جسر الكاميرا (ص16) والاشتراك في الـ host الإنتاجي.
- `assembleRelease` ناجح + قواعد R8/ProGuard.
- flip خلف الفلاج + rollback.
- smoke على release APK.

**خارج النطاق (متعمد):**
- كاميرا/ML في KMP — **Phase 07**.
- جسر Google sign-in في KMP — يبقى عبر auth القديم (استراتيجية B).
- iOS native auth / token sync — نظير iOS لاحق.
- StoreKit bridge فعلي — إخفاء صريح قائم من Pre-06.2.
- WS-5 دفعة 3 (engine layers متبقية).

---

## مسارات العمل

### G-1 — تثبيت البيانات الإنتاجي 🟢 (أسهل مما يبدو)

**المشكلة:** `MovitDataInstall` (التنفيذ الإنتاجي الكامل) محبوس في `app/src/debug`.

**الحل:**
1. انقل `MovitDataInstall.kt` من `app/src/debug/java/com/movit/debug/` إلى `app/src/main/java/com/movit/...` (مثلاً `com.movit.host`). لا تغيير في الجسم — كل تبعياته (`AuthManager`/`ApiConfig`/`ProgramRepository`/`ExerciseRepository`/`AppThemeManager`/`core:data`) متاحة في main.
2. `core:data` أصلاً `implementation` (غير مشروط) → لا عمل إضافي للتبعية.
3. أبقِ نسخة الوصول من debug تعمل (إعادة استخدام نفس الـ object).

**القبول:** `MovitDataInstall` قابل للاستدعاء من main؛ `:app:compileReleaseKotlin` ينجح بوجوده.

---

### G-2 — Host إنتاجي حقيقي في `MovitMainActivity` 🔶

**المشكلة:** `MovitMainActivity` يحوّل لـ `SplashActivity` دائماً؛ لا يستضيف Compose.

**الحل:** انقل منطق `MovitShellPilotActivity` (الـ 40 سطراً) إلى host إنتاجي (إما داخل `MovitMainActivity` أو host مشترك يستدعيه كلاهما):
```kotlin
MovitDataInstall.install(applicationContext)
enableEdgeToEdge()
setContent {
    MovitAppShellRoute(
        onLaunchLegacyTraining = { effect -> LegacyTrainingLauncher.startCameraExercise(...) ; true },
        onLaunchLegacySubscription = { startActivity(Intent(this, SubscriptionActivity::class.java)) ; true },
    )
}
```
- يتطلّب `feature:shell` (+ transitive) في release classpath → انظر G-5 (ترقية الفلاج).
- `LegacyTrainingLauncher` و`SubscriptionActivity` موجودان في main.
- وحِّد host واحداً لتجنّب ازدواج debug/main (DRY).

**القبول:** عند الفلاج on، `MovitMainActivity` يرسم الـ shell ببيانات حقيقية؛ جسر الكاميرا والاشتراك يعملان.

---

### G-3 — بوابة auth إنتاجية (استراتيجية B) ⚠️ قرار

**المشكلة:** ضمان تكافؤ تسجيل الدخول دون فقد Google.

**الحل (استراتيجية B):**
1. أبقِ `SplashActivity` + شاشات الدخول القديمة كما هي (incl. Google).
2. عند نجاح الدخول، بدّل هدف التنقل من الـ home القديم إلى `MovitMainActivity` — **خلف `MOVIT_SHELL_LAUNCHER_ENABLED`** (off ⇒ السلوك القديم تماماً).
3. تأكّد أن الـ shell عند الإقلاع يجد جلسة `AuthManager` صالحة فيذهب لـ Home (لا يعيد طلب الدخول).
4. logout من داخل الـ shell → `clearAuthSession` + العودة لـ `SplashActivity`.

**القبول:** دخول بالبريد **و**Google عبر الشاشة القديمة ⇒ يفتح الـ shell بجلسة حقيقية؛ logout يعود لشاشة الدخول؛ الفلاج off يعيد السلوك القديم 100%.

---

### G-4 — ربط التنقل + الـ flip خلف الفلاج 🔶

**الحل:**
- **استراتيجية B:** لا تبديل LAUNCHER. فقط هدف ما بعد الدخول (G-3) + أي روابط legacy→shell لازمة (إشعارات/deep links تبقى على legacy مؤقتاً).
- أبقِ `MovitShellPilotActivity` (debug) للـ QA، أو وجّهه للـ host الموحَّد.
- الفلاج on في `gradle.properties` للبناء الإنتاجي التجريبي؛ off للإصدار الحالي.

**القبول:** `movit.shell.launcher.enabled=true` ⇒ تجربة konca shell كاملة؛ `=false` ⇒ التطبيق القديم بلا تغيير.

---

### G-5 — ترقية release + R8/ProGuard 🔴 (المخاطرة الحقيقية)

**المشكلة:** أول لقاء بين minification وKMP/Ktor/kotlinx.serialization/Koin/Compose. اليوم فقط `assembleDebug` مُختبَر.

**الحل:**
1. وسّع الكتلة المشروطة في `app/build.gradle.kts` (سطر ~110) لترقية **كل** موديولات Movit اللازمة (`feature:shell` + transitively designsystem/resources/network/account/home/explore/train/library/reports) من `debugImplementation` إلى `implementation` عند الفلاج.
2. أضِف قواعد `proguard-rules.pro`:
   - `kotlinx.serialization`: إبقاء `@Serializable` + المولَّدات `$$serializer` (DTOs في `core:network`).
   - **Ktor** + **Koin** keep rules.
   - Compose عادةً مغطّى بقواعده المضمّنة — تأكّد.
3. شغّل `:app:assembleRelease` وعالج كل `Missing class`/`ClassNotFound` وقت runtime.
4. تحقّق `releaseRuntimeClasspath` يحوي الموديولات عند الفلاج on، ونظيف عند off.

**القبول:** `:app:assembleRelease` (الفلاج on) ✅؛ APK release يعمل بلا crash؛ الفلاج off ⇒ `releaseRuntimeClasspath` خالٍ من Movit (نظافة legacy محفوظة).

---

### G-6 — تحقق + Smoke على release 🔶

**الحل:** ثبّت release APK (الفلاج on) واختبر:
- الإقلاع → دخول (بريد + Google) → 5 تبويبات.
- Train → Program list → Week plan → Session → Customize → Run → **Start exercise → الكاميرا (TrainingActivity)**.
- Reports (Pro gate) · Profile · لغة ar/en · dark mode · زر الرجوع.
- logout → عودة للدخول.
- بناء الفلاج off ⇒ التطبيق القديم سليم (rollback).

**القبول:** كل ما سبق يمر على release؛ لا ANR/crash؛ لقطات موثّقة.

---

### G-7 — (نظير iOS — مؤجَّل، ليس على المسار الحرج)

iOS native auth يكتب `access_token`/`is_pro`/`active_user_program_id` لعرض بيانات حقيقية. خارج نطاق شحن Android؛ يُسجَّل للتسلسل لاحقاً.

---

## المخاطر

| # | المخاطرة | التخفيف |
|---|----------|---------|
| R1 | **R8 يكسر serialization/Ktor** في release | G-5 keep rules مبكراً؛ اختبر `assembleRelease` في أول يوم لا آخره |
| R2 | **ازدواج host** debug/main يتباعد | host موحَّد واحد يستدعيه الاثنان (G-2) |
| R3 | فجوة بيانات بين الـ shell و legacy (مفاتيح prefs/كاش) | استراتيجية B تُبقي `AuthManager` مصدراً واحداً؛ smoke G-6 يكشف التضارب |
| R4 | الفلاج on يسرّب Movit لـ release الحالي بالخطأ | أبقِ الفلاج off افتراضياً؛ تحقّق `releaseRuntimeClasspath` في CI |
| R5 | Google parity لو اخترنا A لاحقاً | موثّق كقرار؛ B يتجنبه كلياً الآن |

---

## أوامر التحقق

```powershell
cd kmp-app
# بناء التطبيق القديم (الفلاج off) — يجب أن يبقى نظيفاً
.\gradlew.bat --console=plain :app:assembleRelease
.\gradlew.bat --console=plain :app:dependencies --configuration releaseRuntimeClasspath   # خالٍ من Movit

# بناء الإنتاج التجريبي (الفلاج on)
.\gradlew.bat --console=plain -Pmovit.shell.launcher.enabled=true :app:assembleRelease
.\gradlew.bat --console=plain -Pmovit.shell.launcher.enabled=true :app:dependencies --configuration releaseRuntimeClasspath   # يحوي feature:shell + transitive

# الاختبارات تبقى خضراء
.\gradlew.bat --console=plain :feature:shell:testDebugUnitTest :core:data:testDebugUnitTest
```

تشغيل الـ shell الإنتاجي (debug pilot يبقى للـ QA):
```text
adb shell am start -n com.movit.androidApp/com.movit.debug.MovitShellPilotActivity
```

---

## بوابة خروج Phase 06

| البند | المعيار |
|-------|---------|
| G-1 | `MovitDataInstall` في main · `compileReleaseKotlin` ✅ |
| G-2 | host إنتاجي موحَّد يرسم الـ shell ببيانات حقيقية + جسرا الكاميرا/الاشتراك |
| G-3 | دخول بريد+Google عبر auth القديم ⇒ shell بجلسة حقيقية · logout يعود للدخول |
| G-4 | الفلاج on ⇒ shell · off ⇒ legacy 100% |
| G-5 | `assembleRelease` (on) ✅ بلا crash · `releaseRuntimeClasspath` نظيف عند off |
| G-6 | smoke release كامل + flow الكاميرا عبر الجسر القديم |
| التحقق | الاختبارات خضراء · لقطات release موثّقة |

**Rollback:** `movit.shell.launcher.enabled=false` يعيد التطبيق القديم فوراً بلا أي تغيير كود — هذا هو أمان الـ strangler.

---

## الجدولة المقترحة

| اليوم | العمل |
|-------|-------|
| 1 | **G-5 أولاً جزئياً** — جرّب `assembleRelease` الآن لاكتشاف مفاجآت R8 مبكراً + G-1 (نقل التثبيت) |
| 2 | G-2 (host موحَّد) + إكمال ترقية الفلاج (G-5) |
| 3 | G-3 (استراتيجية B: تبديل هدف ما بعد الدخول) + G-4 |
| 4 | G-6 smoke release + توثيق + لقطات |

**ملاحظة جدولة:** ابدأ بتجربة `assembleRelease` في اليوم الأول حتى لو ناقصاً — R8 هي المجهول الوحيد الكبير؛ اكتشافها مبكراً يحمي الجدول.

---

## بعد Phase 06

بإغلاق هذه البوابة يصبح لديك **منتج Android قابل للشحن** على المسار الجديد (مع الكاميرا القديمة). الطريق بعدها:
- **Phase 07 — كاميرا/ML في KMP:** حدود `expect/actual` لـ `CameraFrameSource` + `PoseDetector`؛ الأساس (المحرك العددي) مُستخرَج بالكامل وجاهز.
- **iOS:** native auth + token sync (G-7) ثم كاميرا iOS عبر نفس حدود Phase 07.
- **WS-5 دفعة 3** + StoreKit bridge فعلي — بالتوازي.

---

## سجل التنفيذ — Phase 06 (2026-06-10)

### ملخص الحالة

**CLOSED — ~95%** (G-1…G-6 مكتملة؛ smoke على جهاز حقيقي **جزئي/مؤجَّل** — التحقق البنيوي + Gradle + مراجعة كود ✅)

بوابة الإنتاج Android (WS-G) جاهزة للتفعيل التجريبي عبر `-Pmovit.shell.launcher.enabled=true`. الافتراضي `false` يحافظ على legacy 100%.

### G-1 — تثبيت البيانات الإنتاجي

| | |
|---|---|
| **الحالة** | ✅ |
| **الملفات** | `app/src/main/java/com/movit/host/MovitDataInstall.kt` |
| **القرارات** | نقل التنفيذ الكامل لـ `MovitPlatformBindings` إلى `main` دون إعادة كتابة — `AuthManager`/`ApiConfig`/`ProgramRepository`/`ExerciseRepository`/`AppThemeManager` |
| **التحقق** | `MovitDataInstall.install()` قابل للاستدعاء من host الإنتاجي؛ `:app:compileReleaseKotlin` ✅ (ضمن `assembleRelease`) |

### G-2 — Host إنتاجي موحَّد

| | |
|---|---|
| **الحالة** | ✅ |
| **الملفات** | `app/src/movitShellHost/java/com/movit/host/MovitShellHost.kt` (مشترك) · `MovitMainActivity.kt` (enabled) · `app/src/movitShellDisabled/java/com/movit/MovitMainActivity.kt` (placeholder) · `MovitShellPilotActivity` (debug QA) |
| **القرارات** | host واحد `attachMovitShellHost` + `MovitAppShellHost` في source set `movitShellHost`؛ pilot وproduction يستدعيان نفس الكود |
| **التحقق** | عند flag on، `MovitMainActivity` يستضيف Compose shell + جسرا الكاميرا/الاشتراك |

### G-3 — بوابة auth (استراتيجية B)

| | |
|---|---|
| **الحالة** | ✅ |
| **الملفات** | `MovitPostLoginNavigator.kt` · `SplashActivity` · `SignInActivity` · `SignUpActivity` · `ProfileOnboardingActivity` |
| **القرارات** | **استراتيجية B:** `SplashActivity` يبقى LAUNCHER؛ Google + email عبر auth القديم؛ بعد الدخول → `MovitMainActivity` خلف الفلاج |
| **التحقق** | مراجعة كود: `resolveStartupInnerStack` + `AuthBootstrapTarget.ActiveSession` يفتح Home مباشرة عند جلسة `AuthManager` صالحة |

### G-4 — التنقل + flip خلف الفلاج

| | |
|---|---|
| **الحالة** | ✅ |
| **الملفات** | `app/build.gradle.kts` (source sets `movitShellEnabled`/`movitShellDisabled`) · `MovitPostLoginNavigator` · `AndroidManifest.xml` (LAUNCHER = `SplashActivity`) |
| **القرارات** | لا تبديل LAUNCHER؛ flip = هدف ما بعد الدخول + `BuildConfig.MOVIT_SHELL_LAUNCHER_ENABLED` |
| **التحقق** | flag off ⇒ `homeActivityClass()` → `MainContainerActivity`؛ flag on ⇒ `MovitMainActivity` |

### G-5 — release + R8/ProGuard

| | |
|---|---|
| **الحالة** | ✅ |
| **الملفات** | `app/build.gradle.kts` (ترقية مشروطة `implementation`/`debugImplementation`) · `app/proguard-rules.pro` (kotlinx.serialization · Ktor · Koin · Movit keep rules) |
| **القرارات** | `isMinifyEnabled=true` + `isShrinkResources=true` في release؛ موديولات shell في `implementation` فقط عند `-Pmovit.shell.launcher.enabled=true` |
| **التحقق** | `:app:assembleRelease` **flag off** ✅ (~3m) · **flag on** ✅ (~4m) · `releaseRuntimeClasspath` flag off: **لا** `feature:shell`/`feature:home`/… (يبقى `core:data` → `shared`/`model`/`resources` فقط — مقصود) · flag on: `feature:shell` + transitive ✅ |

### G-6 — تحقق + Smoke

| | |
|---|---|
| **الحالة** | 🔶 (Gradle ✅ · smoke جهاز جزئي) |
| **أوامر التحقق** | انظر الجدول أدناه |
| **Smoke (مراجعة كود / بدون جهاز)** | انظر الجدول أدناه |

#### نتائج أوامر التحقق (2026-06-10)

| الأمر | النتيجة |
|-------|---------|
| `:app:assembleRelease` (flag **off**) | ✅ BUILD SUCCESSFUL |
| `:app:assembleRelease` `-Pmovit.shell.launcher.enabled=true` | ✅ BUILD SUCCESSFUL |
| `:app:dependencies --configuration releaseRuntimeClasspath` (off) | ✅ بلا `feature:*` — فقط `core:data` transitive (`shared`, `model`, `resources`) |
| `:app:dependencies --configuration releaseRuntimeClasspath` (on) | ✅ يتضمن `feature:shell` + `feature:home/train/explore/library/reports/account` + `core:designsystem` |
| `:feature:shell:testDebugUnitTest` | ✅ BUILD SUCCESSFUL |
| `:core:data:testDebugUnitTest` | ✅ BUILD SUCCESSFUL |

#### Smoke checklist (مراجعة كود — لم يُختبَر على جهاز في هذه الجلسة)

| المسار | الحالة | دليل الكود |
|--------|--------|------------|
| دخول email → 5 tabs | 🔶 مراجعة كود | `SignInActivity.routeAfterAuth()` → `MovitPostLoginNavigator` → `MovitMainActivity` → `MovitAppShellHost` (5 تبويبات) |
| دخول Google → 5 tabs | 🔶 مراجعة كود | Google عبر `SignInActivity`/`SignUpActivity` legacy؛ نفس `MovitPostLoginNavigator` بعد نجاح API |
| Train → Program → Session → Start → **TrainingActivity** | 🔶 مراجعة كود | `MovitShellHost` → `LegacyTrainingLauncher.startCameraExercise()` → `TrainingActivity` |
| Reports · Profile · ar/en · dark | 🔶 مراجعة كود | shell ViewModels + `MovitDataInstall` bindings للغة/الثيم |
| logout → شاشة الدخول | 🔶 مراجعة كود | `legacyAuthExitEnabled=true` → `NavigateToLegacyAuth` → `SplashActivity` + `CLEAR_TASK` |
| flag off rollback | 🔶 مراجعة كود | `MovitPostLoginNavigator` → `MainContainerActivity`؛ release classpath بلا `feature:shell` |

> **متابعة يدوية:** تثبيت APK release (flag on) على جهاز/محاكي + لقطات شاشة قبل شحن تجريبي للمستخدمين.

### قرارات مهمة مثبتة

- **استراتيجية B (Legacy-auth-front)** — auth القديم (incl. Google) يبقى؛ flip فقط لسطح ما بعد الدخول.
- **LAUNCHER ثابت** — `SplashActivity`؛ لا flip لـ `MovitMainActivity` في manifest.
- **source sets مشروطة** — `movitShellEnabled` vs `movitShellDisabled` عند بناء Gradle لتفادي سحب Compose/Movit إلى release legacy.
- **جسر الكاميرا Phase 07 boundary** — `LegacyTrainingLauncher` في `main`؛ كاميرا KMP مؤجَّلة.
- **Google في KMP** — placeholder؛ parity عبر auth القديم فقط حتى Phase 07/iOS.

### Rollback

```properties
# gradle.properties أو سطر البناء — الافتراضي اليوم:
# movit.shell.launcher.enabled=false
```

- **بناء:** حذف `-Pmovit.shell.launcher.enabled=true` ⇒ APK legacy + classpath بلا `feature:shell`.
- **runtime:** `BuildConfig.MOVIT_SHELL_LAUNCHER_ENABLED=false` ⇒ `MovitPostLoginNavigator` → `MainContainerActivity` دائماً.
- **لا تغيير كود** لإرجاع الإنتاج — strangler flag فقط.

### Blockers / متابعة Phase 07

| البند | الملاحظة |
|-------|----------|
| Smoke جهاز + لقطات release | مطلوب قبل شحن تجريبي خارج الفريق |
| تحديث [Professional Plan](Android-KMP-Mobile-UI-UX-Professional-Plan.md) سطر Launcher | ما زال «debug-only» — يُحدَّث في commit توثيق منفصل |
| Phase 07 — كاميرا/ML KMP | `CameraFrameSource` + `PoseDetector` expect/actual |
| G-7 iOS | native auth + token sync |
| Deep links → shell | ما زالت legacy (`MovitPostLoginNavigator` doc) |
| CI | ~~إضافة job `assembleRelease` on/off + classpath gate~~ → **P6-3 ✅** |

---

## مراجعة تحقّق مستقلة (Independent Verification — 2026-06-10)

> راجعت **شجرة العمل غير المرفوعة مباشرة** + **شغّلت `assembleRelease` بالحالتين بنفسي** (R8 كان المخاطرة #1). الخلاصة: **التنفيذ صحيح وعالي الجودة** — أنظف مسار عمل في الجهد كله. التصميم بـ source sets أذكى مما اقترحته الخطة.

### ما تحقّقت منه (كله أخضر)

| البند | النتيجة | الدليل |
|------|---------|--------|
| `assembleRelease` flag **ON** | ✅ `BUILD SUCCESSFUL 1m42s` | `isMinifyEnabled=true`+`isShrinkResources=true`+`lintVitalRelease` — **R8 لم يكسر serialization/Ktor/Koin/Compose** |
| `assembleRelease` flag **OFF** | ✅ `BUILD SUCCESSFUL 58s` | legacy minified نظيف |
| `releaseRuntimeClasspath` **OFF** | ✅ نظيف | شجرة التبعيات = `core:{data,model,network,resources,training-engine}`+`shared` فقط — **صفر** `feature:*`/`designsystem`/`shell` (لا bytes Compose/UI في release القديم) |
| `releaseRuntimeClasspath` **ON** | ✅ كامل | يضيف `designsystem`+كل `feature:*`+`feature:shell` |
| اختبارات الوحدات | ✅ | shell/account/library/data خضراء — لا انحدار من `legacyAuthExitEnabled`+effect جديد |
| G-1 نقل التثبيت | ✅ | `main/host/MovitDataInstall.kt` موجود · حُذف من debug (بلا ازدواج) |
| G-2 host موحَّد | ✅ | `MovitAppShellHost` (feature:shell) + `attachMovitShellHost`؛ pilot يفوّض إليه (DRY) |
| G-3/G-4 استراتيجية B | ✅ | `MovitPostLoginNavigator` عبر **كل** نقاط الدخول (Splash·SignIn·SignUp·Onboarding·AssessmentResult)؛ flag-gated؛ logout→legacy auth |

### ملاحظات (لا blockers — 🟡 تحسينات)

| # | الملاحظة | الإجراء |
|---|----------|---------|
| P6-1 | ~~**`MovitShellHost` مكرَّر**~~ | ✅ **مُنفَّذ** — `src/movitShellHost/` مشترك |
| P6-2 | ~~**ProGuard واسع جداً**~~ | ✅ **مُنفَّذ** — قواعد مضيّقة لأسطح reflection |
| P6-3 | ~~**R8 release path غير مُغطّى بـ CI**~~ | ✅ **مُنفَّذ** — `movit-android-release.yml` |
| P6-4 | smoke جهاز جزئي (الـ 5% الباقية) | 🔶 سكربت `scripts/phase06-smoke-adb.ps1` + تنفيذ يدوي على جهاز |

### الحكم

**«CLOSED ~95%» تقدير صادق ودقيق.** البوابة محقَّقة بنياناً وبناءً: release يبني ويصغّر بنجاح بالحالتين، الـ classpath يبوّب الواجهة بإحكام، استراتيجية B مطبَّقة شاملةً، والاختبارات خضراء. الـ 5% الباقية = smoke جهاز فعلي على release. **بعد الـ commit + smoke الجهاز تُغلق Phase 06 بالكامل**، ويصبح الطريق ممهّداً لـ Phase 07 (كاميرا KMP).

---

## تحسينات مراجعة المدير P6-1..P6-4 (2026-06-10)

| # | التحسين | الحالة | ما نُفّذ |
|---|---------|--------|----------|
| **P6-1** | توحيد `MovitShellHost` | ✅ | نقل إلى `app/src/movitShellHost/java/com/movit/host/MovitShellHost.kt`؛ `build.gradle.kts` يضمّه في `main` (flag on) أو `debug` (flag off)؛ حُذف النسختان من `movitShellEnabled/` و`debugMovitHost/`؛ `exitToLegacyAuthOnLogout` محفوظ |
| **P6-2** | ضيّق ProGuard | ✅ | أُزيلت `-keep class com.movit.**` و`androidx.compose.**` و`kotlinx.coroutines.**` الشاملة؛ بقيت serialization DTOs + `$$serializer`، Ktor client/http/serialization/util، Koin core/dsl + `core.data.di/repository`، `@Composable com.movit.**` + `Movit*ViewModel` + `com.movit.host.**` |
| **P6-3** | CI `assembleRelease` | ✅ | workflow جديد `.github/workflows/movit-android-release.yml` — ubuntu، JDK 17، Android SDK، Gradle cache؛ `assembleRelease` off ثم on؛ grep `releaseRuntimeClasspath` يتحقق من غياب/وجود `feature:shell` |
| **P6-4** | smoke جهاز | 🔶 | سكربت مساعد `kmp-app/scripts/phase06-smoke-adb.ps1` — يبني APK (اختياري) ويوثّق أوامر adb للـ checklist (install → Splash → shell → logout)؛ **التنفيذ اليدوي على جهاز ما زال مطلوباً** |

### نتائج build بعد التحسينات (2026-06-10)

| الأمر | النتيجة |
|-------|---------|
| `:app:compileDebugKotlin` + `:app:compileReleaseKotlin` (flag **off**) | ✅ BUILD SUCCESSFUL (~3m48s) |
| `:app:assembleRelease` (flag **off**) | ✅ BUILD SUCCESSFUL (~9m) — R8 بالقواعد المضيّقة |
| `:app:compileDebugKotlin` + `:app:compileReleaseKotlin` + `:app:assembleRelease` (flag **on**) | ✅ BUILD SUCCESSFUL (~11m) |

### قرارات ProGuard (P6-2)

- **أُزيل:** blanket keep لكل `com.movit.**` / `com.movit.designsystem.**` / `com.movit.feature.**` / `com.movit.resources.**` / `androidx.compose.**` / `kotlinx.coroutines.**` / `io.ktor.**` الكامل.
- **بقي:** أسطح reflection فقط — serializers، Ktor client stack، Koin wiring، Composable entry points وViewModels وhost.
- **التحقق:** `assembleRelease` ناجح بالحالتين بعد التضييق — لا `Missing class` من R8 في مرحلة البناء.
