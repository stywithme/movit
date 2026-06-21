# Launcher Gate — Movit KMP Shell vs Legacy Android

آخر تحديث: **2026-06-09** (Pre-06 WS-C)

## القرار (2026-06-09)

> **لا يُحوَّل launcher الإنتاجي إلى Movit shell في Pre-06.**  
> يبقى `SplashActivity` هو نقطة الدخول الرسمية حتى إغلاق **WS-D** (تخزين الجلسة الآمن) و**WS-F** (visual smoke) على الأقل، مع تفضيل الانتظار حتى **صفحات Phase 05 (15/16 — Program / Workout flow)** إذا كان المسار يعتمد على تدفقات برنامج كاملة قبل الإطلاق الناعم.

| البند | القرار |
|-------|--------|
| Launcher إنتاجي اليوم | Legacy: `SplashActivity` → auth/onboarding → `MainContainerActivity` |
| Shell KMP اليوم | `MovitShellPilotActivity` — **debug فقط** (أيقونة «Movit KMP Shell») |
| Launcher المستهدف | `MovitMainActivity` — **هيكل جاهز، معطّل افتراضياً** (`movit.shell.launcher.enabled=false`) |
| Flip الإنتاجي | **بعد WS-D** + smoke بصري؛ **مثالي بعد 15/16** إن لم يكن soft launch مقبولاً |
| `releaseRuntimeClasspath` | يبقى **خالياً من موديولات Movit** حتى يوم الـ flip |

## نقاط الدخول الثلاث

| الاسم في الخطة | الصنف الفعلي | المصدر | LAUNCHER | الدور |
|----------------|-------------|--------|----------|-------|
| **LegacyMainActivity** | `SplashActivity` + `MainContainerActivity` | `app/src/main` | `SplashActivity` فقط | إنتاج حالي — XML + bottom nav |
| **MovitShellPilotActivity** | `com.movit.debug.MovitShellPilotActivity` | `app/src/debug` | نعم (debug manifest فقط) | QA بصري / تطوير shell |
| **MovitMainActivity** | `com.movit.MovitMainActivity` | `app/src/main` | لا (حتى الـ flip) | launcher إنتاجي مستهدف — Compose shell |

> **ملاحظة:** `com.trainingvalidator.poc.ui.MainActivity` ليست launcher إنتاجية؛ هي شاشة camera demo قديمة. لا تخلطها مع «LegacyMainActivity» في الوثائق.

### MovitShellPilotActivity (debug)

```kotlin
// app/src/debug/.../MovitShellPilotActivity.kt
MovitDataInstall.install(applicationContext)
setContent { MovitTheme { MovitAppShellRoute() } }
```

### MovitMainActivity (مستهدف — معطّل)

- مسجّلة في `main/AndroidManifest.xml` **بدون** `MAIN`/`LAUNCHER`.
- `BuildConfig.MOVIT_SHELL_LAUNCHER_ENABLED == false` (افتراضي) ⇒ تُحوّل فوراً إلى `SplashActivity` وتُنهي نفسها.
- عند الـ flip: نقل جسم الـ pilot إلى هنا، تبديل `debugImplementation` → `implementation`، ونقل `LAUNCHER` من `SplashActivity` إلى `MovitMainActivity`.

## Feature flag

| الآلية | المفتاح | الافتراضي |
|--------|---------|-----------|
| Gradle property | `movit.shell.launcher.enabled` في `kmp-app/gradle.properties` | `false` |
| BuildConfig | `BuildConfig.MOVIT_SHELL_LAUNCHER_ENABLED` | `false` |

```properties
# kmp-app/gradle.properties — للتجربة المحلية فقط (لا ترفع true إلى CI/release قبل الـ flip)
# movit.shell.launcher.enabled=true
```

عند `true` محلياً: تتحول تبعيات Movit في `:app` من `debugImplementation` إلى `implementation` (انظر أدناه).

## الجسر إلى legacy عند الحاجة للكاميرا

Shell KMP **لا** يشغّل CameraX/MediaPipe في Pre-06. أي «ابدأ التمرين» من shell يمر عبر boundary واضح إلى legacy:

```kotlin
// app/src/main/java/com/movit/legacy/LegacyTrainingLauncher.kt
LegacyTrainingLauncher.startCameraExercise(
    context = context,
    exerciseFileName = "squat",  // slug الملف من الـ repository
    poseVariant = 0,
)
```

**Intent extras** (مرآة لـ `PreWorkoutActivity` / `ExerciseDetailActivity`):

| Extra | ثابت | مطلوب |
|-------|------|-------|
| اسم التمرين (slug) | `TrainingActivity.EXTRA_EXERCISE_NAME` | نعم |
| variant | `TrainingActivity.EXTRA_POSE_VARIANT` | افتراضي `0` |
| وضع الكاميرا | `TrainingActivity.EXTRA_TRAINING_MODE` = `MODE_CAMERA` | نعم |
| نوع المؤشر | `TrainingActivity.EXTRA_INDICATOR_TYPE` | اختياري |

**مواضع الربط في shell (مستقبلية):**

- `MovitInnerHost` → `ExercisePrepareRoute.onStart` → `LaunchLegacyCameraTraining` → `LegacyTrainingLauncher` (Android pilot).
- تقييم body scan / assessment ⇒ `TrainingActivity` + `EXTRA_ASSESSMENT_MODE=true` (انظر `AssessmentSessionActivity`).

**قاعدة:** لا تعديل UI legacy إلا لـ bug حرج أو compatibility unblock.

## متى يصبح Shell launcher إنتاجياً؟

### معايير إلزامية (كلها)

- [x] WS-A: back stack + اختبارات shell
- [x] WS-G: لا placeholders hardcoded في مسار shell الإنتاجي
- [x] Auth + Profile usable في KMP
- [x] **WS-D:** tokens في تخزين آمن (ليس `SharedPreferences` عادي في المسار الإنتاجي) — Pre-06 مُغلق
- [x] **WS-F:** visual smoke checklist لـ Home / Train / Auth / Profile / Assessment / Reports — [`Android-KMP-Mobile-Visual-QA-Checklist.md`](Android-KMP-Mobile-Visual-QA-Checklist.md)
- [ ] قرار منتج: soft launch بـ Home+Auth+Profile مع camera عبر legacy **أو** انتظار 15/16 (مؤجّل — **مفضل بعد 15/16**)

### الجدول الزمني المقترح

| المرحلة | المخرج |
|---------|--------|
| **الآن (Pre-06 WS-C)** | قرار مكتوب + هيكل + flag — **بدون flip** |
| **بعد WS-D** | اختبار `MovitMainActivity` عبر adb مع `movit.shell.launcher.enabled=true` محلياً |
| **بعد WS-F** | مراجعة go/no-go للـ soft launch |
| **بعد صفحات 15/16** (مفضل) | flip `LAUNCHER` + `releaseRuntimeClasspath` + إزالة ازدواجية الصيانة تدريجياً |

## استراتيجية `releaseRuntimeClasspath`

### الوضع الحالي (pilot / Pre-06)

في `app/build.gradle.kts`، موديولات Movit **`debugImplementation` فقط**:

- `:shared`, `:core:network`, `:core:data`, `:core:designsystem`
- `:feature:explore`, `:home`, `:train`, `:library`, `:reports`, `:account`, `:shell`
- `androidx.activity:compose`, Compose tooling

**تحقق:**

```powershell
cd kmp-app
.\gradlew.bat --console=plain :app:dependencies --configuration releaseRuntimeClasspath
```

يجب ألا تظهر `project :feature:shell` ولا `activity-compose` في release runtime.

### عند الـ flip (قائمة تنفيذ)

1. ضبط `movit.shell.launcher.enabled=true` في `gradle.properties` (أو CI flavor مخصص).
2. التأكد أن `:app` يستخدم `implementation(...)` لموديولات Movit (يحدث تلقائياً عند الـ property أعلاه).
3. نقل جسم `MovitShellPilotActivity` إلى `MovitMainActivity`.
4. في `main/AndroidManifest.xml`:
   - إزالة `LAUNCHER` من `SplashActivity` (أبقِ activity للـ deep links إن لزم).
   - إضافة `MAIN`/`LAUNCHER` إلى `MovitMainActivity`.
5. في `debug/AndroidManifest.xml`: إزالة `LAUNCHER` من `MovitShellPilotActivity` (أو الإبقاء كاختصار QA بدون launcher مزدوج).
6. إعادة فحص `releaseRuntimeClasspath` — **يجب** أن يحتوي Movit shell **عمداً**.
7. `assembleRelease` + smoke على جهاز حقيقي.

### لماذا لم نُقل classpath الآن؟

Release APK legacy يجب أن يبقى خفيفاً وخالياً من Compose/KMP حتى لا نرفع حجم التطبيق ولا نُدخل مساراً غير مختبر في الإنتاج. الـ flip متعمد وليس تراكم `debugImplementation` بالخطأ.

## أوامر مفيدة

```powershell
# بناء debug (shell عبر pilot launcher)
cd kmp-app
.\gradlew.bat --console=plain :app:assembleDebug

# فتح shell بدون launcher debug (بعد تفعيل flag محلياً)
adb shell am start -n com.trainingvalidator.poc/com.movit.MovitMainActivity

# فحص release classpath
.\gradlew.bat --console=plain :app:dependencies --configuration releaseRuntimeClasspath
```

## مراجع

- [Phase Pre-06 — WS-C](Android-KMP-Mobile-UI-UX-Phase-Pre-06-Architecture-And-Production-Readiness-Plan.md#ws-c--بوابة-launcher-وتخفيض-الصيانة-المزدوجة)
- [Phase Pre-05 — WS-G (معيار أولي)](Android-KMP-Mobile-UI-UX-Phase-Pre-05-Stabilization-And-Debt-Closure-Plan.md)
- [Phase 03 — release hygiene](Android-KMP-Mobile-UI-UX-Phase-03-Movit-App-Shell-Plan.md)
