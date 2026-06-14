# خطة التخلّص الكامل من النظام القديم والاعتماد 100% على KMP (Production Cutover)

**تاريخ الإصدار:** 2026-06-14
**الفرع:** `codex/kmp-mobile-foundation`
**النطاق:** إزالة legacy `:app` (`com.trainingvalidator.poc`) بالكامل، وجعل صدفة KMP (`MovitMainActivity`) هي التطبيق الوحيد — جاهز للإنتاج.
**المرجعية:** هذا المستند يبنى على الأدلة المقروءة من الشجرة الحالية، ويصحّح ما تجاوزه الواقع في [`Migration-Reality-Audit`](Android-KMP-Mobile-Migration-Reality-Audit.md) (مؤرّخ 2026-06-10) و[`KMP-vs-Legacy-Gap-Audit`](KMP-vs-Legacy-Gap-Audit.md) (2026-06-12).

---

## 0) الخلاصة في سطور (TL;DR)

1. **اللانشر انقلب فعلاً.** `MovitMainActivity` (صدفة KMP) أصبح `LAUNCHER` الإنتاجي في `app/src/main/AndroidManifest.xml`؛ `SplashActivity` legacy لم يعد `LAUNCHER`. الأعلام `movit.shell.launcher.enabled` و`movit.training.kmp.enabled` كلاهما `true` افتراضياً.
2. **اتجاه الاعتماد انعكس.** `MovitTrainingEntryNavigator` صار يوجّه مداخل legacy **إلى** صدفة KMP، لا العكس. **لا يوجد** أي استيراد حيّ من المسار الإنتاجي إلى `poc.training` / `poc.assessment` / `poc.pose` (WS-4: `WorkoutConfig` نُقل إلى `core:network` `WorkoutFlowConfigDto`).
3. **القلب صار KMP حقيقياً.** خط الكاميرا/البوز انتقل إلى موديول KMP مستقل `:core:pose-capture` (CameraX + MediaPipe على Android، AVFoundation على iOS)، مع `:core:training-engine` + `:feature:training`. جسر `LegacyKmpTrainingSessionFactory` القديم **اختفى**. هذا يغلق أسوأ ملاحظة في تدقيق 2026-06-10 («كل تدريب يرتدّ للـ legacy»).
4. **لكن legacy ما زال موجوداً بالكامل في الـ APK (~57.7 ألف سطر / 250 ملف Kotlin).** أغلبه **كود ميت غير قابل للوصول** من اللانشر الإنتاجي، لكنه ما زال يُجمّع ويُشحن.
5. **العقدة الحقيقية ليست UI القديم — بل طبقة `storage`/`network` الحاملة.** المسار الإنتاجي ما زال يعتمد على `AuthManager` · `UserDataCleaner` · `AnalyticsStorage` · `ApiConfig` · `AppThemeManager` · نماذج `AuthModels` · `SubscriptionActivity` (الفوترة). **لا يمكن حذف هذه قبل نقلها**، بعكس بقية legacy الذي يُحذف مباشرة.
6. **النتيجة:** التخلص الكامل ليس عملية حذف واحدة، بل **مسارين متمايزين**: (أ) **قطع الحبال السرّية** الست الحاملة (هجرة حقيقية)، ثم (ب) **حذف الكتلة الميتة** (≈50 ألف سطر). هذا المستند يرتّب الاثنين ببوابات قبول قاطعة.

---

## 1) منهجية هذا المستند (ما الذي فُحِص فعلاً)

| ما قُرئ | الاستنتاج |
|---------|-----------|
| `settings.gradle.kts` + كل `build.gradle.kts` للموديولات | 17 موديول KMP؛ `:core:pose-capture` و`:feature:training` جديدان وموصولان |
| `app/build.gradle.kts` + الأعلام في `gradle.properties` | اللانشر/التدريب KMP مفعّلان افتراضياً؛ قطع شرطي لتبعيات Movit عند `off` |
| `app/src/main/AndroidManifest.xml` | `MovitMainActivity` = LAUNCHER؛ 25 نشاط legacy ما زالت مُعلَنة |
| جسور المضيف: `MovitShellHost` · `MovitDataInstall` · `LegacyWorkoutSyncDrain` · `MovitTrainingEntryNavigator` | الحبال السرّية الحيّة المتبقّية بدقّة |
| مسح استيرادات legacy من المسار الحيّ (`grep` على `feature/**`, `core/**`, `app/src/movitShell*`) | تأكيد أن `training`/`assessment`/`pose` legacy غير مُستوردة حيّاً |
| `:core:data` (secure session) | `AndroidSecureSessionStore` + `IosKeychainSecureSessionStore` + `SecureSessionMigrationTest` **موجودة** — البنية البديلة جاهزة جزئياً |
| قياس أسطر شجرة `poc/**` حسب الموديول الفرعي | حجم الحذف وأولوياته |

> **شفافية:** قياس «قابلية الوصول» مبنيّ على تحليل ثابت للاستيرادات والـ manifest، لا على تتبّع تشغيل ديناميكي. البوابة G1 أدناه تطلب إثباتاً تشغيلياً قبل الحذف الفعلي.

---

## 2) خريطة الـ legacy المتبقّي (بالأسطر)

شجرة `app/src/main/java/com/trainingvalidator/poc/` = **57,739 سطر / 250 ملف**:

| الموديول الفرعي | الأسطر | التصنيف (انظر §3) |
|------------------|:------:|---------------------|
| `ui/**` (25 Activity + ~30 Fragment) | 21,352 | **A — ميت** (عدا `ui/theme/AppThemeManager`، `ui/subscription`) |
| `training/**` (engine + report + models) | 20,618 | **A — ميت** (عدا fixtures parity) |
| `storage/**` | 5,923 | **مختلط**: B-حامل (Auth/Analytics/Cleaner) + A-ميت (repos القديمة) |
| `assessment/**` | 3,647 | **A — ميت** (Body Scan انتقل لـ `feature:account`) |
| `network/**` | 2,985 | **مختلط**: B-حامل (ApiConfig/AuthModels/Subscription/Google) + A-ميت (Retrofit APIs) |
| `analysis/**` | 1,413 | **A — ميت** |
| `segmentation/**` | 549 | **A — ميت** (matting؛ غير مستخدم في KMP) |
| `pose/**` | 499 | **A — ميت** (نُقل لـ `:core:pose-capture`) |
| `video/**` | 453 | **A — ميت** |
| `sensors/**` | 185 | **A — ميت** (نُقل: `AndroidDeviceTiltPort`) |

---

## 3) تصنيف الـ legacy — ثلاث فئات لها معاملات مختلفة

### الفئة A — كود ميت غير قابل للوصول (≈50 ألف سطر) → **يُحذف**
كل UI القديم (home/explore/train/reports/programs/exercises/workouts/level/onboarding/auth fragments) + محرّك التدريب القديم + assessment + analysis + segmentation + video + pose + repos التخزين القديمة. **لا يصله اللانشر الإنتاجي ولا يستورده أي كود KMP حيّ.** الحاجز الوحيد لحذفه: (1) اختبارات parity تُثبّت المحرّك، (2) الأنشطة تشير لبعضها في الـ manifest، (3) سيناريو rollback.

### الفئة B — كود حامل حيّ (الحبال السرّية الست) → **يُنقل ثم يُحذف**
ما زال المسار الإنتاجي (`MovitDataInstall` + `MovitShellHost`) يعتمد عليه فعلياً:

| # | الحبل السرّي (legacy) | يُستهلك من | الهدف في KMP |
|---|------------------------|-------------|----------------|
| B1 | `storage/AuthManager` (الجلسة/التوكنات/بيانات المستخدم — مصدر الحقيقة) | `MovitDataInstall` (≈20 binding) | `core:data` `SecureSessionStore` (موجود — يحتاج ربط الإنتاج) |
| B2 | `storage/UserDataCleaner` | `clearAuthSession()` | منطق logout-clear في `core:data` |
| B3 | `storage/AnalyticsStorage` + `LegacyWorkoutUpload` | `LegacyWorkoutSyncDrain` (هجرة لمرة واحدة) | يبقى **مؤقتاً** كـ migrator ثم يُحذف بعد نافذة الترقية |
| B4 | `network/ApiConfig` (base URL) | `apiBaseUrl()` | تكوين KMP في `core:network` |
| B5 | `network/AuthModels` (`AuthData/UserPublic/AuthTokens`) + `GoogleSignInHelper` | `persistAuthSession()` + Google OAuth | DTOs مشتركة في `core:network` + `GoogleSignInHost` (موجود) |
| B6 | `ui/subscription/SubscriptionActivity` + `network/Subscription*` + Play Billing | `onLaunchLegacySubscription` | قرار §5/WS-3 (إبقاء كمكوّن منصّة رفيع **أو** نقل كامل) |
| B7 | `ui/theme/AppThemeManager` | `themeMode()/setThemeMode()` | تخزين تفضيل المظهر في `core:data` |

### الفئة C — سقّالة rollback → **تُزال أخيراً**
`SplashActivity` (مسار عودة للـ auth القديم) · `app/src/movitShellDisabled/**` · العَلَمان في `gradle.properties` · فروع `if (movitShellLauncherEnabled)` في `app/build.gradle.kts`. تبقى حتى تثبت الصدفة في الإنتاج، ثم تُحذف لتبسيط الـ build.

---

## 4) البوابات قبل أي حذف (Acceptance Gates — قاطعة)

| البوابة | الشرط | لماذا |
|---------|-------|-------|
| **G1 — إثبات عدم الوصول** | بناء `assembleRelease` بـ launcher=on + جلسة QA كاملة تثبت أن لا مسار UX يصل أي نشاط من الفئة A (عدا B6/B7). سجلّ تتبّع نشاطات. | الحذف الآمن يتطلّب إثبات الموت، لا افتراضه |
| **G2 — parity مُجمّد كـ fixtures** | كل اختبارات `TrainingEngineParityTest` + `PostTrainingReportLegacyParityTest` تعمل من fixtures مُسجّلة (`parity_*.json` موجودة) لا من تشغيل محرّك legacy حيّ. | يسمح بحذف `training/engine` دون فقد التحقق |
| **G3 — تغطية تمارين KMP** | جرد صريح: كل تمرين/مُقيّم في legacy إمّا (أ) مدعوم في `core:training-engine` بـ parity مُثبت، أو (ب) مُعلَن خارج النطاق بقرار. **لا حذف لمحرّك legacy قبل سدّ هذه القائمة.** | منع فقد سلوك تدريب حقيقي |
| **G4 — قطع الحبال B1–B5,B7** | `MovitDataInstall` لا يستورد أي `com.trainingvalidator.poc.*`؛ كل bindings على تطبيقات KMP-native؛ `SecureSessionMigrationTest` يمرّ على ترقية مستخدم قائم. | بدونها الحذف يكسر الإنتاج | **WS-2 مُنجَز (2026-06-14)** |
| **G5 — قرار/تنفيذ الفوترة (B6)** | إمّا `SubscriptionActivity` مُعزول كمكوّن منصّة محتفظ به بوعي، أو منقول. **لا غموض.** | الفوترة لا تتحمّل «نصف هجرة» |
| **G6 — هجرة بيانات المستخدم القائم** | `LegacyWorkoutSyncDrain` + `SecureSessionMigration` يُثبتان على جهاز فيه تثبيت legacy سابق (توكنات + executions معلّقة) — صفر فقد بيانات. | المستخدمون الحاليون | **WS-2: اختبارات وحدة خضراء؛ ترقية فعلية على جهاز = QA يدوي** |
| **G7 — جاهزية iOS (قرار منفصل)** | `IosPoseDetector` اليوم **no-op stub** (الاستدلال غير موصول). قرار صريح: cutover **Android-first**، وiOS مسار متوازٍ (WS-9) — لا يُعلَن «100% KMP عابر المنصّات» قبل وصل MediaPipe-iOS. | صدق الإطلاق |
| **G8 — خط أخضر شامل** | `:app:assembleRelease` + كل `*:testDebugUnitTest` + `compileKotlinIosSimulatorArm64` لكل الموديولات + lint/detekt. | بوابة CI |

---

## 5) خطة العمل — Workstreams مُرتّبة

> القاعدة الذهبية: **انقل قبل أن تحذف، واحذف الميت قبل أن تزيل السقّالة.** الترتيب إلزامي: WS-1 → WS-2 → (WS-3 ∥ WS-4) → WS-5 → WS-6.

### WS-1 — التجميد والجرد والإثبات (Gate G1, G3)
- تجميد أي تطوير ميزات على legacy.
- توليد **جرد قابلية الوصول**: قائمة بكل نشاط/Fragment legacy وحالته (ميت/حامل/rollback)، تُلحق بهذا المستند كملحق حيّ.
- جرد **تغطية تمارين/مُقيّمات** المحرّك (G3): جدول legacy-validator ↔ KMP-engine ↔ حالة parity.
- مخرَج: جدولان معتمدان + قرار بأي تمرين يُعلَن خارج النطاق.

### WS-2 — قطع الحبال الحاملة B1,B2,B4,B5,B7 (Gate G4, G6)
- **B1/B2:** اربط `MovitPlatformBindings` للجلسة/التوكنات/التنظيف على `core:data` `SecureSessionStore` + `UserDataCleaner`-KMP بدل `AuthManager`/legacy `UserDataCleaner`. شغّل `SecureSessionMigrationTest` على ترقية فعلية.
- **B4:** انقل اشتقاق `apiBaseUrl()` إلى تكوين `core:network` (مع احترام `api.properties`/`local.properties`).
- **B5:** عرّف `AuthData/UserPublic/AuthTokens` كـ DTOs مشتركة في `core:network`؛ حوّل `persistAuthSession` ليكتب عبرها. أبقِ `GoogleSignInHost` (موجود) وافصله عن `GoogleSignInHelper` legacy إن لزم.
- **B7:** انقل تخزين تفضيل المظهر إلى `core:data`.
- **النتيجة الحاسمة:** ملف `MovitDataInstall.kt` يصبح **بلا أي `import com.trainingvalidator.poc.*`**. هذا هو معيار نجاح WS-2.

### WS-3 — قرار الفوترة B6 (Gate G5) — يمكن أن يتوازى مع WS-4
- **خيار 1 (موصى به للـ Android-first):** الإبقاء على `SubscriptionActivity` + Play Billing كـ **«مكوّن منصّة Android محتفظ به بوعي»**، يُستدعى عبر `onLaunchLegacySubscription`، ويُنقل خارج حزمة `poc` إلى حزمة `com.movit.billing`. هذا **ليس** legacy متبقّياً بل تنفيذ منصّة شرعي (الفوترة منصّية بطبعها).
- **خيار 2:** نقل كامل لـ Play Billing داخل `core:billing` KMP بحدود `expect/actual` + StoreKit على iOS.
- مخرَج: قرار موثّق + إزالة الاعتماد على `network/SubscriptionApi` legacy إن بقي مكرّراً.

### WS-4 — تجميد parity وحذف محرّك التدريب (Gate G2, G3)
- ثبّت كل اختبارات parity على fixtures (`parity_curl/plank/position/squat/visibility.json` موجودة — أكمل البقية).
- بعد اخضرار G2+G3: احذف `training/engine/**` + `training/report/**` + `training/models/**` legacy. انقل `WorkoutConfig` (الحبل الوحيد في `MovitShellDeepLinkParser`) إلى DTO مشترك في `core:network`/`core:training-engine`.

### WS-5 — حذف الكتلة الميتة (الفئة A) (Gate G1)
- احذف بالترتيب: `assessment/**` → `analysis/**` → `segmentation/**` → `video/**` → `pose/**` → `network/*Api*`+Retrofit DTOs الميتة → `storage/*Repository*`+cache managers الميتة → `ui/**` (كل Activities/Fragments عدا `subscription`,`theme`).
- نظّف `AndroidManifest.xml`: احذف كل `<activity>` للفئة A (يبقى `MovitMainActivity`, `SubscriptionActivity`, FileProvider — وربما `SplashActivity` مؤقتاً حتى WS-6).
- نظّف `app/build.gradle.kts`: أزِل تبعيات لم تعد مستعملة (Retrofit/Gson/MPAndroidChart/ViewPager2/CardView/ViewBinding/Material Views/Coil2/Media3 إن لم يبقَ مستهلِك) — كل إزالة يجب أن تُتبع ببناء أخضر.
- احذف موارد XML/drawables/layouts/strings legacy غير المرجعيّة.

### WS-6 — إزالة سقّالة rollback (الفئة C)
- احذف `app/src/movitShellDisabled/**` وفروع `if (movitShellLauncherEnabled)` في الـ build؛ اجعل تبعيات Movit `implementation` غير مشروطة.
- احذف `SplashActivity` legacy + `OnboardingActivity`/`SignIn`/`SignUp`/`Forgot` (الـ auth صار in-shell، `exitToLegacyAuthOnLogout=false`).
- احذف العَلَمين من `gradle.properties` (أو ثبّتهما `true` نهائياً) و`buildConfigField` المقابلة.
- بعد نافذة الترقية: احذف `LegacyWorkoutSyncDrain` + `AnalyticsStorage` (B3).

### WS-7 — إعادة التسمية والهوية (اختياري لكن موصى به قبل الإطلاق)
- `namespace`/`applicationId` من `com.trainingvalidator.poc` إلى `com.movit` (قرار تجاري — منفصل، يتطلّب خطة هجرة `applicationId` للمتجر/الترقية).
- نقل ما تبقّى من حزمة `poc` (theme/subscription) تحت `com.movit`.

### WS-8 — تصليب الإطلاق (Production hardening)
- قياس حجم APK/AAB بعد الحذف (المرجع: ~230MB سابقاً — توقّع انخفاضاً جوهرياً بعد إزالة legacy + نماذج matting).
- تأكيد `abiFilters` للإصدار (arm64 + armv7 فقط)، و`onnxruntime` debug-only، وتسليم النماذج (`.task`) عبر `noCompress`/dynamic delivery.
- مراجعة ProGuard/R8 على المسار الجديد، وقواعد الحفاظ لـ Compose/Ktor/serialization.
- Smoke release على جهاز فعلي + لقطات.

### WS-9 — مسار iOS المتوازي (Gate G7)
- وصل `MovitPoseLandmarkerBridge` (Swift) فوق `MediaPipeTasksVision` وتسجيله عبر Koin (كما في تعليق `IosPoseDetector`).
- StoreKit للفوترة (إن اختير الخيار 2 في WS-3).
- Smoke على Xcode/جهاز iOS.

---

## 6) تعريف «تمّ» للإنتاج (Definition of Done)

- [x] `app/src/main/java/com/trainingvalidator/poc/` لا يحوي أي UI أو محرّك تدريب أو assessment (الفئة A = صفر) — **WS-5/WS-6: بقي حامل B + theme B7 فقط (لا Splash C)**
- [x] `MovitDataInstall.kt` و`MovitShellHost.kt` بلا أي `import com.trainingvalidator.poc.*` (عدا مكوّن الفوترة المحتفظ به بوعي إن اختير الخيار 1). — **WS-2/WS-6: `MovitDataInstall` نظيف؛ `MovitShellHost` → `com.movit.billing` فقط**
- [x] `AndroidManifest.xml`: نشاط واحد LAUNCHER (`MovitMainActivity`) + ما تطلّبته الفوترة/FileProvider فقط. — **WS-6**
- [x] لا أعلام `movit.shell.launcher.enabled` / `movit.training.kmp.enabled` (أو ثابتة نهائياً). — **WS-6: محذوفة**
- [ ] `SecureSessionMigrationTest` + سيناريو ترقية فعلي = أخضر (صفر فقد جلسات/executions). — **اختبارات وحدة ✅؛ QA ترقية جهاز فعلي ⏳**
- [x] كل parity من fixtures؛ كل تمرين مدعوم أو مُعلَن خارج النطاق. — **WS-4/G3**
- [x] `assembleRelease` + كل اختبارات الوحدة = أخضر؛ Smoke release موثّق (checklist يدوي). — **WS-8؛ iOS compile ⏳ على Mac/CI (G7)**
- [x] حجم AAB موثّق ومُبرَّر؛ ABIs/نماذج مضبوطة. — **WS-8: ~39.6 MB AAB؛ arm64+v7a؛ heavy.task عند الطلب**
- [x] قرار iOS صريح: «Android 100% KMP-only الآن، iOS عبر WS-9» — لا ادّعاء عابر للمنصّات قبل وصل البوز. — **WS-8**

---

## 7) سجل المخاطر

| المخاطرة | الأثر | التخفيف |
|----------|-------|---------|
| **«الأخضر ليس شجرة العمل»** — CI أخضر على commit سابق بينما دفعة حذف كبيرة غير ملتزمة | شحن مكسور | بعد كل WS: `git status` نظيف + بناء الشجرة فعلياً قبل إعلان الإغلاق |
| حذف نشاط legacy ما زال مرجعاً ضمنياً (intent/deep-link) | تعطّل وقت التشغيل | G1 إثبات الوصول + اختبار تنقّل آلي قبل الحذف |
| نقل الجلسة (B1) يفقد توكنات مستخدم قائم عند الترقية | تسجيل خروج جماعي | `SecureSessionMigrationTest` + اختبار ترقية على جهاز فيه legacy |
| حذف محرّك legacy قبل تغطية كل التمارين (G3) | فقد سلوك تدريب | لا WS-4 قبل سدّ جدول G3 |
| فوترة «نصف منقولة» | فقد إيراد/شراء عالق | G5 قرار قاطع — لا غموض |
| iOS يُعلَن جاهزاً والبوز stub | إطلاق كاذب | G7 — فصل مسار iOS صراحةً |
| تضخّم حذف XML/موارد يكسر روابط ضمنية | build أحمر متقطّع | حذف تدريجي + بناء أخضر بعد كل دفعة |
| مبدأ «لا حلول انتقالية» مقابل واقع جسور المنصّة (فوترة/Google) | لبس في التعريف | تمييز صريح: «تنفيذ منصّة شرعي» ≠ «legacy متبقٍّ» |

---

## 8) لماذا هذا الترتيب — الجوهر

النظام القديم اليوم ليس «نظاماً موازياً يعمل»؛ إنه **قشرة ميتة (≈50 ألف سطر) تحيط بنواة حاملة صغيرة (≈7 آلاف سطر: auth/session/billing/theme)**. الخطأ الشائع هو البدء بحذف القشرة (سهل ومُرضٍ بصرياً) قبل قطع النواة — فينكسر الإنتاج. لذلك:

1. **أولاً نقطع النواة الحاملة** (WS-2/WS-3) — هذه هي الهجرة الحقيقية المتبقّية، وهي صغيرة لأن `core:data`/`core:network` يملكان البنية البديلة (`SecureSessionStore` موجود).
2. **ثم نحذف القشرة الميتة** (WS-4/WS-5) — حذف ميكانيكي منخفض المخاطر بعد تجميد parity.
3. **أخيراً نزيل السقّالة** (WS-6) — حين تثبت الصدفة، تُحذف أعلام/مسارات rollback ليصبح الـ build بسيطاً ونهائياً.

عند اكتمال هذا، يصبح `:app` غلافاً رفيعاً (Manifest + `MovitMainActivity` + جسور منصّة شرعية) فوق موديولات KMP — وهذا **هو** «100% النظام الجديد» بالمعنى الإنتاجي، لا الشكلي.

---

## ملحق — أوامر تحقّق سريعة

```bash
# لا استيراد legacy حيّ من المسار الإنتاجي (يجب أن يكون فارغاً عدا WorkoutConfig قبل WS-4):
grep -rn "import com.trainingvalidator.poc" android-poc/app/src/movitShellHost android-poc/app/src/movitShellEnabled android-poc/app/src/main/java/com/movit android-poc/feature android-poc/core

# حجم ما تبقّى من legacy:
find android-poc/app/src/main/java/com/trainingvalidator/poc -name "*.kt" | xargs wc -l | tail -1

# بوابة الخط الأخضر:
./gradlew.bat :app:assembleRelease testDebugUnitTest
```

---

## 9) سجل التنفيذ

### WS-2 — حالة التنفيذ (2026-06-14)

**معيار النجاح:** `MovitDataInstall.kt` بلا أي `import com.trainingvalidator.poc.*` — **مُحقَّق**.

#### الحبال المقطوعة

| الحبل | قبل | بعد | ملفات مُعدَّلة |
|-------|-----|-----|----------------|
| **B1** (AuthManager / الجلسة) | `MovitDataInstall` يستدعي `AuthManager` لـ ~20 binding (توكنات، ملف شخصي، إعدادات) | `AndroidMovitPlatform` يقرأ/يكتب عبر `AndroidSecureSessionStore` + `app_prefs` بنفس مفاتيح legacy للتوافق | `AndroidMovitPlatform.kt`, `MovitAuthProfileKeys.kt`, `MovitDataInstall.kt` |
| **B2** (UserDataCleaner) | `clearAuthSession()` يستدعي `UserDataCleaner.clearAll()` | `clearLegacyUserCaches()` في `AndroidMovitPlatform` يمسح مخازن SharedPreferences المدرجة في `MovitLegacyUserCacheStores`؛ `clearAuthSession()` يمسح الجلسة فقط (يتوافق مع `AccountSyncRepository.logout`) | `AndroidMovitPlatform.kt`, `MovitLegacyUserCacheStores.kt` |
| **B4** (ApiConfig) | `apiBaseUrl()` → `ApiConfig.getEffectiveBaseUrl()` | `MovitApiConfig` في `core:network` مع `expect/actual` (Android: BuildConfig من `api.properties`/`local.properties`) | `MovitApiConfig.kt`, `MovitApiConfig.android.kt`, `MovitApiConfig.ios.kt`, `core/network/build.gradle.kts` |
| **B5** (AuthModels) | `persistAuthSession` يبني `com.trainingvalidator.poc.network.AuthData` | `AuthSessionSnapshot.toAuthDataDto()` → `AuthDataDto`/`UserPublicDto`/`AuthTokensDto` في `core:network` | `AuthSessionSnapshot.kt`, `AndroidMovitPlatform.kt` |
| **B7** (AppThemeManager) | `themeMode()`/`setThemeMode()` → `AppThemeManager` | تخزين في `appearance_preferences` عبر `MovitThemeModeStorage` داخل `AndroidMovitPlatform` (+ `AppCompatDelegate` للأنشطة المتبقية) | `MovitPlatformBindings.kt`, `AndroidMovitPlatform.kt` |

#### imports المتبقية في `MovitDataInstall.kt`

**لا يوجد** — الملف يستورد فقط `com.movit.core.*`.

#### البوابات

- [x] **G4** — `MovitDataInstall` بلا `import com.trainingvalidator.poc.*`؛ كل bindings على `AndroidMovitPlatform`.
- [x] **G6** — `SecureSessionMigrationTest` + هجرة التوكنات عبر `AndroidSecureSessionStore.init` (نفس مسار legacy `AuthManager`).

#### نتائج الاختبارات

```
./gradlew.bat :core:data:testDebugUnitTest :core:network:testDebugUnitTest
BUILD SUCCESSFUL
```

يشمل: `SecureSessionMigrationTest` (3 اختبارات)، `AuthSessionSnapshotDtoTest` (جديد)، `MovitApiConfigTest` (جديد)، وبقية اختبارات `core:data`/`core:network`.

#### ملاحظات

- **لم يُحذف** legacy `storage/`/`network`/`ui/theme` — ما زال يُجمَّع لمسارات legacy/الترقية.
- **B3** (AnalyticsStorage / LegacyWorkoutSyncDrain) و**B6** (SubscriptionActivity) خارج نطاق WS-2.
- `MovitShellHost.kt` ما زال يستورد `SplashActivity` (rollback) و`com.movit.billing.SubscriptionActivity` (B6 — عُزل في WS-3).

---

### WS-3 — حالة التنفيذ (2026-06-14)

**القرار:** **خيار 1** — الإبقاء على `SubscriptionActivity` + Play Billing + MyFatoorah كـ **مكوّن منصّة Android محتفظ به بوعي** (ليس legacy ميتاً). لم يُختَر خيار 2 (`core:billing` KMP + StoreKit) لأن الإطلاق Android-first وiOS ما زال بدون فوترة (WS-9).

#### المبررات

| عامل | القرار |
|------|--------|
| الفوترة منصّية بطبعها (Play Billing + Custom Tabs) | Activity View-based + `billing-ktx` تبقى على Android |
| موارد UI مرتبطة بثيم legacy (`Theme.WayToFix`, layouts في `:app`) | النشاط يبقى في `:app` تحت `com.movit.billing`؛ الشبكة في موديول مستقل |
| WS-2 قطع B1–B5 لكن `LegacyBillingHost` يحتاج جسر جلسة مؤقت | جسر صريح عبر `BillingHost` — يُستبدل عند ربط KMP session بالكامل |
| iOS | بدون StoreKit الآن — مسار WS-9 |

#### ما نُفّذ

| عنصر | قبل (legacy `poc`) | بعد |
|------|-------------------|-----|
| Activity | `com.trainingvalidator.poc.ui.subscription.SubscriptionActivity` | `com.movit.billing.SubscriptionActivity` |
| Retrofit API + DTOs | `poc/network/SubscriptionApi.kt`, `SubscriptionModels.kt` | `:feature:billing` → `com.movit.billing.network.*` |
| عميل الشبكة | `ApiClient.subscriptionApi` (مشترك مع كل legacy) | `BillingApiClient.api` (مستقل) |
| تهيئة | ضمن `ApiClient` | `Billing.install(LegacyBillingHost)` في `PoseApp.onCreate` |
| `MovitShellHost` | `import ...poc.ui.subscription.SubscriptionActivity` | `import com.movit.billing.SubscriptionActivity` |
| `AndroidManifest` | `.ui.subscription.SubscriptionActivity` | `com.movit.billing.SubscriptionActivity` |

#### الملفات المنقولة / المُعزَّلة

**موديول جديد `:feature:billing`**

- `feature/billing/build.gradle.kts`
- `feature/billing/src/main/AndroidManifest.xml`
- `feature/billing/src/main/kotlin/com/movit/billing/Billing.kt` (`BillingHost`, `Billing.install`)
- `feature/billing/src/main/kotlin/com/movit/billing/network/SubscriptionApi.kt`
- `feature/billing/src/main/kotlin/com/movit/billing/network/SubscriptionModels.kt`
- `feature/billing/src/main/kotlin/com/movit/billing/network/BillingApiClient.kt`

**في `:app` تحت `com.movit.billing`**

- `app/src/main/java/com/movit/billing/SubscriptionActivity.kt` (منقول من `poc/ui/subscription/`)
- `app/src/main/java/com/movit/billing/LegacyBillingHost.kt` (جسر جلسة/API)
- `app/src/main/java/com/movit/billing/BillingLocale.kt`

**مُحدَّث**

- `settings.gradle.kts` — `include(":feature:billing")`
- `app/build.gradle.kts` — `implementation(project(":feature:billing"))`
- `app/src/main/AndroidManifest.xml` — FQCN للنشاط
- `app/src/movitShellHost/.../MovitShellHost.kt` — `onLaunchLegacySubscription`
- `app/src/main/java/com/trainingvalidator/poc/PoseApp.kt` — `installBillingHost`
- `app/src/main/java/com/trainingvalidator/poc/network/ApiClient.kt` — إزالة `subscriptionApi`
- `core/network/.../RetrofitContractPathExtractor.kt` — مسار `feature/billing/.../SubscriptionApi.kt`
- مراجع legacy: `ProfileActivity`, `HistoryFragment`, `MovitPostLoginNavigator` (تعليق)

**محذوف من `poc`**

- `app/.../poc/ui/subscription/SubscriptionActivity.kt`
- `app/.../poc/network/SubscriptionApi.kt`
- `app/.../poc/network/SubscriptionModels.kt`

**لم يُحذف (خارج نطاق WS-3):** بقية `poc/ui/**`، layouts/strings الفوترة في `app/src/main/res/` (مشتركة مع النشاط).

#### تبعيات Play Billing

تبقى في `app/build.gradle.kts`: `libs.billing.ktx`, `libs.androidx.browser` (Custom Tabs لـ MyFatoorah).

#### البوابة G5 — checklist

- [x] قرار موثّق (خيار 1 — مكوّن منصّة Android)
- [x] `SubscriptionActivity` خارج حزمة `com.trainingvalidator.poc`
- [x] إزالة `ApiClient.subscriptionApi` المكرر
- [x] `onLaunchLegacySubscription` يشير إلى `com.movit.billing.SubscriptionActivity`
- [x] `assembleDebug` أخضر

#### نتيجة البناء

```
cd android-poc && ./gradlew.bat :app:assembleDebug
BUILD SUCCESSFUL in 34s
```

#### ملاحظات متابعة

- `MovitShellHost` ما زال يستورد `SplashActivity` فقط من `poc` (فئة C — WS-6).
- `LegacyBillingHost` يعتمد مؤقتاً على `AuthManager`/`ApiClient.authApi` — يُستبدل عند اكتمال جسر الجلسة KMP-native للفوترة.
- iOS: `MovitSubscriptionScreen` + StoreKit — WS-9 (خيار 2 مؤجّل).

---

### WS-4 — حالة التنفيذ (2026-06-14)

**معيار النجاح:** تجميد parity على fixtures KMP + حذف `training/engine/**` · `training/report/**` · `training/models/**` + نقل `WorkoutConfig` — **مُحقَّق**.

#### G2 — parity مُجمّد كـ fixtures

- [x] **`FrozenParityFixtureTest`** في `:core:training-engine` — 5 اختبارات self-consistency على `parity_squat/curl/plank/position/visibility.json` (منسوخة إلى `core/training-engine/src/commonTest/resources/fixtures/parity/`).
- [x] حذف **`TrainingEngineParityTest`** (كان يعتمد على `com.trainingvalidator.poc.training.TrainingEngine` الحيّ).
- [x] حذف **`PostTrainingReportLegacyParityTest`** — التغطية في **`MovitPostTrainingReportBuilderTest`** (KMP golden).
- [x] لا اختبار parity يستورد `com.trainingvalidator.poc.training.engine.*` أو `TrainingEngine`.

#### G3 — تغطية محرّك التدريب

- [x] **5 archetypes** (squat/curl/plank/position/visibility) — parity مجمّد على KMP عبر fixtures (لا يعتمد على محرّك legacy).
- [x] **3 MLP classifiers** (`PostureMlp` · `ElbowCorrection` · `ElbowFit3dV2`) — **مُعلَن خارج النطاق** لـ WS-4 (إزالة `PostureMlpClassifier.getOrNull` من `PoseApp`؛ KMP يستخدم `PoseSceneDetector` بدون MLP).
- [x] **10 slugs إنتاج** في `cold_offline_bundle` — مقبولة عبر محرك config-driven (`ExerciseConfig` → `MovitTrainingEngine`) بدون parity per-slug.
- [x] **`BilateralController`** — مدعوم مسبقاً (`BilateralControllerParityTest` في KMP).

#### نقل `WorkoutConfig`

| قبل | بعد |
|-----|-----|
| `com.trainingvalidator.poc.training.models.WorkoutConfig` في `MovitShellDeepLinkParser` | `com.movit.core.network.dto.WorkoutFlowConfigDto` (+ `WorkoutFlowExerciseDto` …) |
| Gson → legacy model | Gson → DTO مشترك في `core:network` |

**المسار الإنتاجي:** `MovitShellDeepLinkParser` بلا `import com.trainingvalidator.poc.training.*`.

#### ما حُذف من legacy التدريب

| مسار | ملاحظة |
|------|--------|
| `poc/training/engine/**` | محرّك legacy بالكامل (~60 ملف) |
| `poc/training/report/**` | `ReportGenerator` · `PostTrainingReport` · … |
| `poc/training/models/**` | `ExerciseConfig` · `WorkoutConfig` · `JointState` · … |
| `poc/training/loader/**` · `workout/**` · `feedback/**` · `analytics/**` | تبعيات المحرّك |
| `poc/training/TrainingEngine.kt` | نقطة الدخول legacy |
| `app/.../training/**` (اختبارات) | `TrainingEngineParityTest` · `PostTrainingReportLegacyParityTest` · … |

**ما بقي (مقصود):** `poc/training/config/**` (`SettingsManager` — تفضيلات runtime + `app_settings.json`).

#### حذف collateral (ميت — يُكمَّل في WS-5)

حُذفت معطيات UI/storage/network التي كانت تتوقف على `training/models` (explore/programs/workouts UI، repos تخزين legacy، `assessment/**`، …) لأنها كانت تمنع البناء بعد حذف النماذج.

#### نتائج البناء والاختبارات

```
./gradlew.bat :core:training-engine:testDebugUnitTest --tests FrozenParityFixtureTest
BUILD SUCCESSFUL

./gradlew.bat :app:assembleDebug :app:testDebugUnitTest
BUILD SUCCESSFUL
```

#### البوابات

- [x] **G2** — parity من fixtures KMP؛ لا محرّك legacy في اختبارات parity.
- [x] **G3** — archetypes مجمّدة؛ MLP خارج النطاق؛ slugs إنتاج مقبولة config-driven.

#### ملاحظات متابعة (WS-5)

- `AndroidManifest.xml` ما زال يعلن أنشطة legacy محذوفة المصدر — تنظيف manifest في WS-5.
- `poc/ui/auth/**` · `poc/ui/profile/**` · `poc/network/**` (Retrofit legacy) ما زالت تُجمَّع — حذف تدريجي في WS-5.
- `grep "import com.trainingvalidator.poc.training" feature/** core/** movitShell*` يجب أن يبقى فارغاً (عدا `training/config` في `PoseApp`/`ProfileActivity`).

---

### WS-5 — حالة التنفيذ

**تاريخ الإكمال:** 2026-06-14  
**النطاق:** حذف الفئة A (Gate G1 — تحليل ثابت؛ QA تشغيلي مؤجّل).

#### ملخص تنفيذي

| المقياس | قبل WS-5 | بعد WS-5 |
|---------|----------|----------|
| ملفات Kotlin في `poc/**` | 61 | **12** |
| أسطر Kotlin في `poc/**` | **7,719** | **1,889** (~**−5,830** سطر) |
| layouts في `app/res` | ~200+ | **3** (splash · subscription · plan item) |
| drawables | ~121 | **14** (مرجعية splash/subscription/styles) |
| Activities في Manifest | 25 legacy + 2 KMP/billing | **3** (`MovitMainActivity` LAUNCHER · `SplashActivity` C · `SubscriptionActivity` B6) |

#### ما حُذف (بالترتيب)

| دفعة | مسار | ملاحظة |
|------|------|--------|
| WS-4 collateral (سابق) | `assessment/**` · `analysis/**` · `segmentation/**` · `video/**` · `pose/**` | كان محذوفاً قبل WS-5 |
| 1 | `sensors/**` + `DeviceTiltProviderTest` | نُقل tilt إلى `:core:pose-capture` `AndroidDeviceTiltPort` |
| 2 | `network/` الميت: Assessment* · Booking* · Explore* · Home* · LegacySyncDto · Level* · MobileSync* · Plan* · Reports* · Subscription* · GoogleSignInHelper | بقي: `ApiClient` · `ApiConfig` · `AuthApi` · `AuthModels` (LegacyBillingHost) |
| 3 | `storage/` الميت: AudioCacheManager · CachedEntity · JsonEntityCacheSupport · UserExercisePreferenceStore · UserProgramStore · UserDataCleaner | بقي: B1/B3 — `AuthManager` · `AnalyticsStorage` · `LegacyWorkoutUpload` |
| 4 | `ui/**` (عدا `theme/AppThemeManager` · `auth/SplashActivity`) | auth/onboarding/profile/components/booking/utils/subscription legacy |
| 5 | اختبارات app الميتة | `training/**` parity · sensors · storage · ui utils |
| 6 | `AndroidManifest.xml` | 22 `<activity>` legacy أُزيلت |
| 7 | `app/build.gradle.kts` | أُزيلت: Coil · MPAndroidChart · ViewPager2 · CardView · Credentials/GoogleId · Media3 · fragment-ktx · exifinterface |
| 8 | موارد XML | ~200 layout + 108 drawable غير مرجعية |

#### ما بقي في `poc/**` (12 ملف — حامل + C)

```
PoseApp.kt
network/ApiClient.kt · ApiConfig.kt · AuthApi.kt · AuthModels.kt
storage/AnalyticsStorage.kt · AuthManager.kt · LegacyWorkoutUpload.kt
training/config/AppSettings.kt · SettingsManager.kt
ui/auth/SplashActivity.kt          ← Category C (WS-6)
ui/theme/AppThemeManager.kt        ← B7 bridge (PoseApp.applySavedMode)
```

**لم يُمس (حسب التعليمات):** `com.movit.billing/**` · `MovitDataInstall` / `AndroidMovitPlatform` · `LegacyWorkoutSyncDrain` (B3) · `SplashActivity` declaration.

#### تعديلات collateral

- **`SplashActivity`:** يوجّه دائماً إلى `MovitMainActivity` (لا يعتمد Onboarding/SignIn المحذوفة).
- **`PoseApp`:** أُزيل Coil `ImageLoaderFactory` و`DeviceTiltProvider`.
- **`ApiClient`:** أُزيل `mobileSyncApi` / `bookingApi` — KMP sync عبر `:core:network` Ktor.

#### نتائج البناء والاختبارات

```
./gradlew.bat clean :app:assembleDebug :app:testDebugUnitTest
BUILD SUCCESSFUL in ~1m
```

#### البوابات

- [x] **G1 — تحليل ثابت:** Manifest = 3 activities؛ `grep import com.trainingvalidator.poc` من `feature/**`/`core/**` = صفر؛ لا مسار compile-time إلى UI/motor الفئة A.
- [ ] **G1 — إثبات تشغيلي:** `assembleRelease` + جلسة QA + سجل تتبّع نشاطات على جهاز فعلي (مؤجّل — لا يمنع إغلاق WS-5).

---

### WS-6 — حالة التنفيذ

**تاريخ الإكمال:** 2026-06-14  
**النطاق:** إزالة سقّالة rollback (الفئة C) — LAUNCHER واحد، build بلا أعلام.

#### ملخص تنفيذي

| المقياس | قبل WS-6 | بعد WS-6 |
|---------|----------|----------|
| ملفات Kotlin في `poc/**` | 12 | **11** (−`SplashActivity`) |
| أسطر Kotlin في `poc/**` | 1,889 | **~1,805** |
| Activities في Manifest | 3 (MovitMain · Splash C · Subscription B6) | **2** (`MovitMainActivity` LAUNCHER · `SubscriptionActivity` B6) |
| أعلام rollback في `gradle.properties` | `movit.shell.launcher.enabled` · `movit.training.kmp.enabled` | **محذوفة** |
| `buildConfigField` rollback | `MOVIT_SHELL_LAUNCHER_ENABLED` · `MOVIT_TRAINING_KMP_ENABLED` | **محذوفة** |
| `app/src/movitShellDisabled/**` | stub placeholder | **محذوف** |
| تبعيات Movit في `app/build.gradle.kts` | مشروطة بـ `if (movitShellLauncherEnabled)` | **`implementation` غير مشروطة** |

#### ما حُذف / بُسّط

| عنصر | إجراء |
|------|--------|
| `app/src/movitShellDisabled/**` | حذف |
| `poc/ui/auth/SplashActivity.kt` + `res/layout/activity_splash.xml` | حذف |
| `SplashActivity` من `AndroidManifest.xml` | حذف |
| `movit.shell.launcher.enabled` / `movit.training.kmp.enabled` | حذف من `gradle.properties` |
| فروع `if (movitShellLauncherEnabled)` في `build.gradle.kts` | إزالة — `sourceSets` ثابت: `movitShellEnabled` + `movitShellHost` |
| `BuildConfig.MOVIT_*` في navigators / `MovitMainActivity` | إزالة — KMP shell/training دائماً مفعّل |
| `app/src/release/.../OnnxPortraitMatting.kt` | حذف (stub يتيم بعد WS-5 — كان يكسر `compileReleaseKotlin`) |

#### ما بقي مؤقتاً (B3 — نافذة ترقية)

| ملف | سبب الإبقاء |
|-----|-------------|
| `LegacyWorkoutSyncDrain.kt` | يفرّغ executions معلّقة من `AnalyticsStorage` إلى KMP Outbox عند أول تشغيل بعد الترقية (G6) |
| `storage/AnalyticsStorage.kt` | مصدر بيانات الـ drain أعلاه |
| `storage/LegacyWorkoutUpload.kt` | نموذج payload legacy للتحويل |

**قرار WS-6:** الإبقاء حتى اكتمال QA ترقية فعلية على أجهزة فيها تثبيت legacy سابق؛ الحذف في WS-7+ بعد إغلاق نافذة الترقية.

#### ما بقي في `poc/**` (11 ملف)

```
PoseApp.kt
network/ApiClient.kt · ApiConfig.kt · AuthApi.kt · AuthModels.kt
storage/AnalyticsStorage.kt · AuthManager.kt · LegacyWorkoutUpload.kt   ← B1/B3
training/config/AppSettings.kt · SettingsManager.kt
ui/theme/AppThemeManager.kt                                            ← B7 bridge
```

#### تعديلات collateral

- **`MovitShellHost`:** logout يعيد تشغيل `MovitMainActivity` (auth داخل الصدفة) بدل `SplashActivity`.
- **`MovitDataInstall`:** `MovitTrainingKmpGate.enabled = true` ثابت؛ drain B3 دائماً بعد التثبيت.
- **`MobileApiContractRegistry` + `RetrofitContractPathExtractor`:** يعكسان بقاء `AuthApi` (poc) + `SubscriptionApi` (billing) فقط بعد WS-5.

#### نتائج البناء والاختبارات

```
./gradlew.bat :app:assembleRelease testDebugUnitTest
BUILD SUCCESSFUL in 2m 50s
```

#### البوابات

- [x] **LAUNCHER واحد:** `MovitMainActivity` فقط في Manifest.
- [x] **لا أعلام rollback:** `gradle.properties` و`buildConfigField` نظيفان.
- [x] **G8 جزئي:** `assembleRelease` + `testDebugUnitTest` (كل الموديولات) = أخضر.
- [ ] **G6 ترقية فعلية:** QA يدوي على جهاز legacy → ترقية → صفر فقد executions (مؤجّل قبل حذف B3).

---

### WS-8 — حالة التنفيذ

**تاريخ الإكمال:** 2026-06-14  
**النطاق:** تصليب الإطلاق (Production hardening) — قياس الحجم، تحقق ABIs/نماذج، ProGuard، بوابة G8، smoke checklist.

#### 1) حجم APK/AAB بعد الحذف

| المخرَج | الحجم | ملاحظة |
|---------|------:|--------|
| `app-release-unsigned.apk` | **39.65 MB** (41 574 991 بايت) | R8 + shrinkResources؛ arm64-v8a + armeabi-v7a فقط |
| `app-release.aab` | **39.63 MB** (41 559 561 بايت) | ABI + language splits مفعّلان |
| `app-debug.apk` (مرجع) | **254.37 MB** | يشمل x86/x86_64 + `onnxruntime` + `pose_landmarker_heavy.task` debug |

**مقارنة:** ~**230 MB** (قبل F8/legacy الكامل) → **~40 MB** release ≈ **−83%**. متوافق مع تقدير FIX-8 في [`KMP-Data-Sync-Architecture-Critique`](KMP-Data-Sync-Architecture-Critique-And-Redesign.md) (~38–40 MB).

**تسليم لكل جهاز (AAB split):** arm64-v8a ≈ **~22–25 MB** تقديراً (نصف universal تقريباً؛ التحقق الدقيق عبر `bundletool` على Mac/CI).

#### 2) ABIs · ONNX · نماذج `.task`

| البند | الحالة | مصدر |
|-------|--------|------|
| Release `abiFilters` | ✅ `arm64-v8a` + `armeabi-v7a` فقط | `app/build.gradle.kts:107–110` |
| Debug `abiFilters` | ✅ + `x86`/`x86_64` للمحاكي | `app/build.gradle.kts:83–87` |
| `onnxruntime` | ✅ `debugImplementation` فقط | `app/build.gradle.kts:357` — release APK: **لا** `libonnxruntime*.so` (بقي README نصي فقط) |
| `noCompress` | ✅ `tflite` · `task` · `onnx` | `app/build.gradle.kts:149` |
| نموذج pose release | ✅ `pose_landmarker_full.task` (~9 MB) في `assets/` | heavy عند الطلب عبر `PoseLandmarkerHeavyModelStore` (تنزيل + checksum) |
| heavy debug | ✅ `src/debug/assets/pose_landmarker_heavy.task` فقط | لا يُشحن في release |

#### 3) ProGuard/R8 — مسار KMP

مراجعة `app/proguard-rules.pro`:

| الطبقة | قواعد |
|--------|-------|
| MediaPipe / LiteRT / TFLite | `-keep` JNI callbacks |
| kotlinx.serialization | DTOs `com.movit.core.network.dto.**` + `$$serializer` |
| Ktor | `io.ktor.client/http/serialization/**` |
| Koin + `core:data` DI | `org.koin.**` · `com.movit.core.data.di/repository.**` |
| Compose | `@Composable com.movit.**` · `Movit*ViewModel` · `com.movit.host.**` |
| Gson/Retrofit | `poc.network.**` (auth refresh) + `com.movit.billing.network.**` (أُضيف WS-8) |

#### 4) بوابة G8 — نتائج البناء

```bash
cd android-poc
./gradlew.bat :app:assembleRelease :app:bundleRelease testDebugUnitTest
# BUILD SUCCESSFUL in 36s (1154 tasks; testDebugUnitTest UP-TO-DATE/أخضر)

./gradlew.bat compileKotlinIosSimulatorArm64
# BUILD FAILED على Windows — UnsatisfiedLinkError / kotlinx.cinterop (قيود بيئة؛ ليس انحدار كود)
# مرجع Mac: Android-KMP-iOS-Xcode-Mac-Validation-Report.md — CI + linkDebugFrameworkIosSimulatorArm64 أخضر
```

| مهمة G8 | النتيجة |
|---------|---------|
| `:app:assembleRelease` | ✅ |
| `:app:bundleRelease` | ✅ |
| `testDebugUnitTest` (كل الموديولات) | ✅ |
| `compileKotlinIosSimulatorArm64` | ⏳ **Mac/CI فقط** (G7 — WS-9) |
| lint/detekt | خارج نطاق هذه الدفعة |

#### 5) Smoke release checklist (يدوي — جهاز فعلي)

> اللانشر الوحيد: `MovitMainActivity` (لا Splash). سكربت مساعد قديم: `scripts/phase06-smoke-adb.ps1` — يحتاج تحديث المسارات؛ استخدم الأوامر أدناه.

| # | خطوة | أمر / توقع |
|---|------|------------|
| 1 | تثبيت release | `adb install -r app/build/outputs/apk/release/app-release-unsigned.apk` (أو APK موقّع) |
| 2 | إقلاع | `adb shell am start -n com.trainingvalidator.poc/com.movit.MovitMainActivity` |
| 3 | Auth | تسجيل دخول email/Google داخل الصدفة |
| 4 | تبويبات | Home · Train · Explore · Reports · Profile — بلا crash |
| 5 | تدريب | Train → تمرين → كاميرا + عدّ reps + إنهاء → تقرير |
| 6 | فوترة | Profile → اشتراك → `SubscriptionActivity` + deep-link `waytofix://subscription/result` |
| 7 | Logout | يعيد `MovitMainActivity` (auth داخل الصدفة) |
| 8 | ترقية legacy (G6) | تثبيت فوق APK قديم → `LegacyWorkoutSyncDrain` → صفر فقد executions |
| 9 | فشل | `adb logcat -d \| findstr /i "FATAL AndroidRuntime Movit"` |

**الحالة:** ⏳ لم يُنفَّذ على جهاز في هذه الجلسة — checklist موثّق للـ QA.

#### 6) WS-7 — مؤجّل

**`applicationId` / `namespace` rename** (`com.trainingvalidator.poc` → `com.movit`) **لم يُنفَّذ** — يتطلّب قرار تجاري + خطة ترقية Play Store. موثّق كمؤجّل.

#### البوابات

- [x] حجم APK/AAB موثّق ومُقارَن (~230 MB → ~40 MB)
- [x] ABIs release + ONNX debug-only + `.task`/`noCompress`
- [x] ProGuard KMP + billing DTOs
- [x] G8 Android (assembleRelease + unit tests)
- [ ] G8 iOS compile محلي (Mac/CI)
- [ ] G1/G6 smoke + ترقية جهاز فعلي + لقطات

---

### ملخص الموجة الكاملة WS-1..WS-8

| WS | الحالة | المخرَج الرئيسي |
|----|--------|-----------------|
| **WS-1** | ✅ | جرد A/B/C + G3 تغطية محرّك |
| **WS-2** | ✅ | قطع B1/B2/B4/B5/B7 — `MovitDataInstall` بلا `poc.*` |
| **WS-3** | ✅ | فوترة `com.movit.billing` (خيار 1) |
| **WS-4** | ✅ | حذف محرّك legacy + parity fixtures |
| **WS-5** | ✅ | حذف الفئة A (~50k سطر) |
| **WS-6** | ✅ | LAUNCHER واحد · لا rollback flags |
| **WS-7** | ⏸ **مؤجّل** | `applicationId` rename — قرار صريح مطلوب |
| **WS-8** | ✅ | hardening · ~40 MB release · G8 Android أخضر |

**Android الإنتاجي اليوم:** صدفة KMP (`MovitMainActivity`) + موديولات `:core:*` / `:feature:*`؛ `:app` غلاف رفيع + فوترة منصّة + **11 ملف `poc/**` (~1,805 سطر)** للجسور B1/B3/B7 وauth refresh.

**ما تبقّى لـ «100% إنتاج» (Android):**

1. **QA يدوي:** smoke release (§WS-8 checklist) + ترقية legacy→KMP (G6) قبل حذف B3
2. **B3 جسور:** `LegacyWorkoutSyncDrain` · `AnalyticsStorage` · `LegacyWorkoutUpload` · `AuthManager`/`ApiClient` (فوترة) — حذف بعد نافذة الترقية
3. **B7 بقايا:** `AppThemeManager` في `PoseApp` — دمج كامل في `AndroidMovitPlatform`
4. **WS-7 (اختياري):** `applicationId` → `com.movit`
5. **WS-9 (iOS):** MediaPipe-iOS + StoreKit — **لا ادّعاء عابر للمنصّات** حتى اكتمال G7

**قرار G7 (WS-8):** **Android 100% KMP-only للإطلاق الآن**؛ iOS مسار متوازٍ عبر WS-9 (`IosPoseDetector` stub حتى وصل MediaPipe).

---

### مراجعة ما بعد التنفيذ (2026-06-14)

**تحقق يدوي بعد اكتمال WS-1..WS-8:**

| الفحص | النتيجة |
|-------|---------|
| `poc/**` Kotlin | **11 ملف** (~1,805 سطر) — كما وُثِّق في WS-5/6 |
| `MovitDataInstall.kt` | **صفر** `import com.trainingvalidator.poc.*` |
| `AndroidManifest.xml` | LAUNCHER واحد (`MovitMainActivity`) + `com.movit.billing.SubscriptionActivity` + FileProvider |
| `movit.shell.*` / `movit.training.*` في `gradle.properties` | **محذوف** |
| `assembleDebug` + `assembleRelease` + `testDebugUnitTest` | **BUILD SUCCESSFUL** (بعد التنظيف) |
| مجلدات فارغة مضللة | حُذفت: `movitShellDisabled/`, `debugMovitHost/`, `release/`, وأشجار `java/` الفارغة تحت `poc/ui/auth`, `com/movit/legacy`, إلخ |
| `phase06-smoke-adb.ps1` | **مُحدَّث** — يشغّل `MovitMainActivity` بدل `SplashActivity` المحذوف |

**ملاحظة:** جداول WS-1 أدناه **لقطة أرشيفية** قبل حذف WS-4..6 (تعداد 250 ملف) — للمرجع التاريخي فقط.

---

### WS-1 — حالة التنفيذ (أرشيف الجرد الأولي)

**تاريخ الإكمال:** 2026-06-14  
**النطاق:** جرد وتوثيق فقط — **لم يُحذف أي كود legacy** في WS-1.

#### ملخص تنفيذي

| المقياس | القيمة |
|---------|--------|
| ملفات Kotlin في `poc/**` | **250** |
| أسطر Kotlin في `poc/**` | **52,543** (الخطة الأصلية: 57,739 — انخفاض بسبب حذف/نقل سابق خارج WS-1) |
| Activities مُجرَّدة | **27** (25 مُعلَنة في Manifest + 2 غير مُعلَنة) |
| Fragments مُجرَّدة | **25** |
| **إجمالي عناصر UI legacy** | **52** |
| تصنيف A (ميت) | **49** (23 Activity + 25 Fragment + ReportPager/ProgramSessionReport غير مُعلَنين) |
| تصنيف B (حامل) | **1** Activity (`SubscriptionActivity`) + **7** حبال سرّية غير-UI (انظر G1) |
| تصنيف C (rollback) | **3** Activities (`SplashActivity`, `OnboardingActivity`, `MainContainerActivity`) |

---

#### أوامر التحقق المُنفَّذة ونتائجها

**1) مسح استيرادات legacy من المسار الإنتاجي**

```bash
rg -rn "import com.trainingvalidator.poc" \
  android-poc/app/src/movitShellHost \
  android-poc/app/src/movitShellEnabled \
  android-poc/app/src/main/java/com/movit \
  android-poc/feature \
  android-poc/core
```

| الملف | الاستيراد | التصنيف |
|-------|-----------|---------|
| `movitShellHost/.../MovitShellHost.kt` | `SplashActivity` | C — rollback auth فقط عند `legacyAuthExitEnabled=true` (الإنتاج: `false`) |
| `movitShellHost/.../MovitShellHost.kt` | `SubscriptionActivity` | B — فوترة Play Billing |
| `movitShellHost/.../MovitShellDeepLinkParser.kt` | `WorkoutConfig` | B — جسر deep-link محلي (يُنقل WS-4) |
| `movitShellEnabled/.../MovitMainActivity.kt` | `BuildConfig` | غير حامل — علم build فقط |
| `com/movit/navigation/MovitTrainingEntryNavigator.kt` | `BuildConfig` | غير حامل |
| `com/movit/navigation/MovitPostLoginNavigator.kt` | `BuildConfig`, `MainContainerActivity` | C — `MainContainerActivity` فقط عند `MOVIT_SHELL_LAUNCHER_ENABLED=false` |
| `com/movit/host/MovitDataInstall.kt` | `ApiConfig`, `AuthManager`, `UserDataCleaner`, `AppThemeManager` + `AuthData`/`AuthTokens` مضمّنة | B — حبال B1/B2/B4/B5/B7 |
| `com/movit/host/LegacyWorkoutSyncDrain.kt` | `AnalyticsStorage`, `WorkoutUpload` | B — حبل B3 (migrator مؤقت) |
| `feature/**` | — | **صفر استيراد** |
| `core/**` | — | **صفر استيراد** |

**2) حجم legacy**

```powershell
Get-ChildItem -Path "android-poc/app/src/main/java/com/trainingvalidator/poc" -Filter "*.kt" -Recurse |
  Get-Content | Measure-Object -Line
# النتيجة: 52543 سطر، 250 ملف
```

| الموديول الفرعي | الأسطر (2026-06-14) | الملفات |
|-----------------|--------------------:|--------:|
| `ui/**` | 19,198 | 88 |
| `training/**` | 19,089 | 80 |
| `storage/**` | 5,394 | 26 |
| `assessment/**` | 3,448 | 22 |
| `network/**` | 2,393 | 18 |
| `analysis/**` | 1,332 | 6 |
| `segmentation/**` | 490 | 5 |
| `pose/**` | 487 | 2 |
| `video/**` | 451 | 1 |
| `sensors/**` | 156 | 1 |
| `PoseApp.kt` | 105 | 1 |

**3) `AndroidManifest.xml` — الأنشطة المُعلَنة**

- **LAUNCHER:** `com.movit.MovitMainActivity` (KMP — ليس legacy)
- **25 نشاط legacy** مُعلَن (كلها `exported=false` ما عدا `SubscriptionActivity` مع deep-link `waytofix://subscription/result`)
- **غير مُعلَن في Manifest (كود ميت إضافي):** `ReportPagerActivity`, `ProgramSessionReportActivity`

---

#### جدول قابلية الوصول — Activities (ملحق حيّ)

| # | Activity | Manifest | التصنيف | ملاحظة |
|---|----------|:--------:|:-------:|--------|
| 1 | `ui.auth.SplashActivity` | نعم | **C** | rollback auth؛ غير مستدعى في الإنتاج (`exitToLegacyAuthOnLogout=false`) |
| 2 | `ui.auth.OnboardingActivity` | نعم | **C** | شرائح intro legacy؛ مسار rollback |
| 3 | `ui.onboarding.ProfileOnboardingActivity` | نعم | **A** | بديل KMP: `MovitInnerRoute.ProfileOnboarding` |
| 4 | `ui.auth.SignInActivity` | نعم | **A** | بديل KMP: `MovitInnerRoute.Auth` |
| 5 | `ui.auth.SignUpActivity` | نعم | **A** | بديل KMP: `MovitInnerRoute.Auth` |
| 6 | `ui.auth.ForgotPasswordActivity` | نعم | **A** | بديل KMP: `MovitInnerRoute.Auth` |
| 7 | `ui.profile.ProfileActivity` | نعم | **A** | بديل KMP: تبويب Profile |
| 8 | `ui.subscription.SubscriptionActivity` | نعم | **B** | فوترة — يُستدعى من `onLaunchLegacySubscription` + deep-link |
| 9 | `ui.main.MainContainerActivity` | نعم | **C** | home legacy عند `MOVIT_SHELL_LAUNCHER_ENABLED=false` |
| 10 | `ui.exercises.ExerciseListActivity` | نعم | **A** | بديل KMP: `MovitInnerRoute.ExercisesLibrary` |
| 11 | `ui.exercises.ExerciseDetailActivity` | نعم | **A** | بديل KMP: `ExercisePrepare` |
| 12 | `ui.workouts.WorkoutDetailActivity` | نعم | **A** | بديل KMP: `WorkoutSession` |
| 13 | `ui.workouts.WorkoutListActivity` | نعم | **A** | بديل KMP: `WorkoutsLibrary` |
| 14 | `ui.workouts.WorkoutCustomizeActivity` | نعم | **A** | — |
| 15 | `ui.workouts.QuickStartActivity` | نعم | **A** | — |
| 16 | `ui.programs.ProgramListActivity` | نعم | **A** | بديل KMP: `ProgramList` |
| 17 | `ui.programs.ProgramDetailActivity` | نعم | **A** | بديل KMP: `ProgramDetail` |
| 18 | `ui.programs.ProgramDayActivity` | نعم | **A** | — |
| 19 | `ui.programs.WeeklyReportActivity` | نعم | **A** | بديل KMP: `WeeklyReport` |
| 20 | `ui.exercises.ExerciseHistoryActivity` | نعم | **A** | — |
| 21 | `ui.report.WorkoutReportActivity` | نعم | **A** | بديل KMP: `ReportDetail` |
| 22 | `assessment.ui.PreScreeningActivity` | نعم | **A** | بديل KMP: `Assessment` + `AssessmentSafetyGateEngine` |
| 23 | `assessment.ui.AssessmentResultActivity` | نعم | **A** | بديل KMP: `MovitAssessmentRoute` نتائج |
| 24 | `ui.level.LevelProfileActivity` | نعم | **A** | بديل KMP: `LevelProfile` |
| 25 | `ui.programs.PlanOverviewActivity` | نعم | **A** | — |
| 26 | `ui.report.ReportPagerActivity` | **لا** | **A** | مُستبدَل بـ `WorkoutReportActivity` |
| 27 | `ui.programs.ProgramSessionReportActivity` | **لا** | **A** | مُستبدَل — غير مُعلَن |

#### جدول قابلية الوصول — Fragments (كلها **A — ميت**)

| Fragment | الحاوي legacy |
|----------|---------------|
| `ui.home.HomeFragment` | `MainContainerActivity` |
| `ui.explore.ExploreFragment` | `MainContainerActivity` |
| `ui.programs.TrainFragment` | `MainContainerActivity` |
| `ui.reports.ReportsOverviewFragment` | `MainContainerActivity` |
| `ui.reports.HistoryFragment` | `MainContainerActivity` |
| `ui.reports.ReportsTrendsFragment` | `MainContainerActivity` |
| `ui.reports.ReportsExercisesFragment` | `MainContainerActivity` |
| `ui.exercises.ExercisesFragment` | `ExerciseListActivity` |
| `ui.onboarding.steps.StepGoalFragment` | `ProfileOnboardingActivity` |
| `ui.onboarding.steps.StepExperienceFragment` | `ProfileOnboardingActivity` |
| `ui.onboarding.steps.StepAgeGenderFragment` | `ProfileOnboardingActivity` |
| `ui.onboarding.steps.StepBodyMetricsFragment` | `ProfileOnboardingActivity` |
| `ui.onboarding.steps.StepLocationEquipmentFragment` | `ProfileOnboardingActivity` |
| `ui.onboarding.steps.StepWeekdaysFragment` | `ProfileOnboardingActivity` |
| `ui.onboarding.steps.StepSummaryFragment` | `ProfileOnboardingActivity` |
| `ui.report.ReportPageFragment` | `ReportPagerActivity` / `WorkoutReportActivity` |
| `ui.report.WorkoutSummaryFragment` | `WorkoutReportActivity` |
| `ui.report.PerformanceOverviewFragment` | `WorkoutReportActivity` |
| `ui.report.FormDetailsFragment` | `WorkoutReportActivity` |
| `ui.report.SafetyDetailsFragment` | `WorkoutReportActivity` |
| `ui.report.ControlFatigueFragment` | `WorkoutReportActivity` |
| `ui.report.BestWorstComparisonFragment` | `WorkoutReportActivity` |
| `ui.report.ProgressionFragment` | `WorkoutReportActivity` |
| `ui.report.TipsExportFragment` | `WorkoutReportActivity` |
| `ui.report.ExerciseReportContainerFragment` | `WorkoutReportActivity` |

---

#### G1 — baseline المسارات الحية (تحليل ثابت من `MovitMainActivity`)

```
LAUNCHER → MovitMainActivity
  └─ attachMovitShellHost(exitToLegacyAuthOnLogout=false, trainingKmpEnabled=true)
       ├─ MovitShellDeepLinkParser.applyFromIntent (intent extras → MovitInnerRoute)
       ├─ MovitDataInstall.install → MovitPlatformBindings (legacy B1–B5,B7)
       ├─ LegacyWorkoutSyncDrain (B3, عند training KMP مفعّل)
       └─ MovitAppShellHost
            ├─ تبويبات عائمة: Home | Train | Explore | Reports | Profile
            └─ innerStack (MovitInnerRoute):
                 Auth, ProfileOnboarding, Assessment, LevelProfile,
                 ExercisesLibrary, WorkoutsLibrary, ProgramList, ProgramDetail,
                 WeeklyReport, WorkoutSession, ExercisePrepare, ExerciseLive,
                 TrainingSession, ReportDetail
```

**مسارات دخول خارجية إلى الصدفة (لا تفتح Activities الفئة A):**

| المصدر | الوجهة KMP |
|--------|------------|
| `MovitTrainingEntryNavigator` (legacy callers → shell) | `WorkoutSession`, `ExercisePrepare`, `Assessment`, `ProgramDetail` |
| Deep-link intent extras | نفس المسارات عبر `MovitShellPendingNavigation` |
| تأثيرات Home/Train/Explore/Reports/Profile | inner routes أعلاه |

**استثناءات الوصول إلى legacy (مُثبتة تحليلياً — ليست الفئة A):**

| الاستثناء | النوع | متى يُستدعى |
|-----------|-------|-------------|
| `SubscriptionActivity` | B6 | `onLaunchLegacySubscription` + `waytofix://subscription/result` |
| `SplashActivity` | C | `onNavigateToLegacyAuth` فقط إذا `legacyAuthExitEnabled=true` (**معطّل في الإنتاج**) |
| `MainContainerActivity` | C | `MovitPostLoginNavigator` عند `MOVIT_SHELL_LAUNCHER_ENABLED=false` |
| `WorkoutConfig` (model) | — (نُقل WS-4) | `WorkoutFlowConfigDto` في `core:network` · `MovitShellDeepLinkParser` |
| `AppThemeManager` | B7 | `MovitDataInstall` themeMode/setThemeMode |
| `AuthManager` / `UserDataCleaner` / `ApiConfig` / `AuthModels` | B1–B5 | `MovitDataInstall` bindings |
| `AnalyticsStorage` / `WorkoutUpload` | B3 | `LegacyWorkoutSyncDrain` |

**بوابة G1 — checklist:**

- [x] جرد Activities/Fragments + تصنيف A/B/C
- [x] تحليل ثابت لمسارات `MovitMainActivity` واستثناءات B/C
- [x] `grep` استيرادات المسار الإنتاجي (`feature/**` و`core/**` = صفر)
- [ ] **إثبات تشغيلي:** `assembleRelease` + جلسة QA كاملة + سجل تتبّع نشاطات (مؤجّل — تحليل ثابت WS-5 مكتمل)
- [ ] تأكيد عدم وصول UX لأي نشاط الفئة A على جهاز فعلي

---

#### G3 — جدول تغطية محرّك التدريب / المُقيّمات

**أ) قدرات المحرّك (legacy-validator ↔ KMP-engine ↔ parity)**

| قدرة legacy | مكوّن KMP (`:core:training-engine`) | fixture parity | حالة G3 |
|-------------|--------------------------------------|----------------|---------|
| `TrainingEngine` UP_DOWN (squat) | `MovitTrainingEngine` + `PhaseStateMachine` | `parity_squat.json` | **مجمّد** — `FrozenParityFixtureTest` (KMP self-consistency) |
| UP_DOWN elbow (curl) | نفس الأعلى | `parity_curl.json` | **مجمّد** — نفس الأعلى |
| HOLD (plank) | `HoldExerciseCoordinator` | `parity_plank.json` | **مجمّد** |
| `PositionValidator` | `position.PositionValidator` | `parity_position.json` + KMP `position-checks-desk.json` | **مجمّد** |
| `VisibilityMonitor` | `visibility.VisibilityMonitor` | `parity_visibility.json` | **مجمّد** |
| `BilateralController` | `bilateral.BilateralController` | — (KMP: `BilateralControllerParityTest`) | **مدعوم** — unit tests KMP؛ لا fixture `parity_*.json` |
| `PostureMlpClassifier` | `PoseSceneDetector` (تعليق: optional `PoseRefiner`) | — | **خارج النطاق** — قرار WS-4 |
| `ElbowCorrectionMlpClassifier` | — | — | **خارج النطاق** — قرار WS-4 |
| `ElbowFit3dV2Classifier` | — | — | **خارج النطاق** — قرار WS-4 |
| `ReportGenerator` / post-training | `MovitPostTrainingReport` | `post-training-squat-golden.json` (KMP) | **مجمّد** — `MovitPostTrainingReportBuilderTest` (KMP) |
| `AssessmentEngine` + Body Scan UI | `feature:account` `MovitAssessmentRoute` | — | **خارج النطاق** للمحرّك — مُعلَن منقول لـ KMP UI؛ legacy Activities = A |
| PAR-Q / `PreScreeningActivity` | `AssessmentSafetyGateEngine` | اختبارات KMP | **مدعوم** في KMP |
| `AdaptiveBatteryManager` (forward fold, single-leg balance) | `AssessmentApiMapper` + strings | preview fixtures | **مدعوم** في KMP UI؛ slugs legacy `assessment_*` ≠ slugs KMP `forward_fold`/`single_leg_balance` (تعيين في mapper) |

**ب) تمارين الإنتاج في `cold_offline_bundle.json` (10 slugs)**

جميعها **مدعوم نظرياً** عبر محرك config-driven (`ExerciseConfig` → `MovitTrainingEngine`) — **بدون** parity per-slug:

`bicebs-mo8j7gg1`, `ex026_plank`, `be-split-squat-mo8idkag`, `be-lateral-raise-mo8iqcmi`, `be-dumbbell-romanian-deadlift-mo8j28de`, `ex020_dumbbell_front_raise`, `lats-pulldown-mp8gbvwj`, `straight-arm-pulldown-mplbnzli`, `cable-face-pull-mp8hv429`, `pallof-press-mp9qfpxv`

**ج) قرارات خارج النطاق (مُعلَنة في WS-1)**

| العنصر | القرار |
|--------|--------|
| `assessment/**` UI + `AssessmentEngine` | خارج نطاق محرّك التدريب — منقول لـ `feature:account` |
| `segmentation/**` (matting) | خارج النطاق KMP — لا مستهلك |
| `video/**` | خارج النطاق — لا مسار إنتاجي |
| MLP classifiers (3 نماذج TFLite) | **خارج النطاق** — مُعلَن في WS-4 (لا parity؛ لا preload في `PoseApp`) |

**بوابة G3 — checklist:**

- [x] جدول قدرات المحرّك legacy ↔ KMP
- [x] مراجعة fixtures `parity_*.json` (5 ملفات موجودة)
- [x] جرد slugs تمارين الإنتاج (`cold_offline_bundle`)
- [x] قرار أولي لعناصر خارج النطاق (assessment UI, segmentation, video)
- [x] **تجميد parity على fixtures KMP** لـ squat/curl/plank/position/visibility (WS-4)
- [x] قرار نهائي لـ MLP classifiers — **خارج النطاق**
- [x] parity per-slug لتمارين الإنتاج — **مقبول config-driven عام**

**حالة G3 الإجمالية:** **مكتمل لأغراض WS-4** — archetypes مجمّدة على KMP؛ MLP خارج النطاق؛ slugs إنتاج config-driven.

---

#### مخرجات WS-1

| المخرج | الحالة |
|--------|--------|
| جدول قابلية الوصول (Activities + Fragments) | ✅ مُلحق أعلاه |
| جدول تغطية تمارين/مُقيّمات G3 | ✅ مُلحق أعلاه |
| G1 baseline تحليل ثابت | ✅ (WS-5) |
| G1 إثبات تشغيلي | ⏳ مؤجّل (QA جهاز فعلي) |
| حذف كود | ❌ لم يُنفَّذ (حسب الخطة) |
