# خطة التخلّص الكامل من النظام القديم والاعتماد 100% على KMP (Production Cutover)

**تاريخ الإصدار:** 2026-06-14
**الفرع:** `codex/kmp-mobile-foundation`
**النطاق:** إزالة legacy `:app` (`com.trainingvalidator.poc`) بالكامل، وجعل صدفة KMP (`MovitMainActivity`) هي التطبيق الوحيد — جاهز للإنتاج.
**المرجعية:** هذا المستند يبنى على الأدلة المقروءة من الشجرة الحالية، ويصحّح ما تجاوزه الواقع في [`Migration-Reality-Audit`](Android-KMP-Mobile-Migration-Reality-Audit.md) (مؤرّخ 2026-06-10) و[`KMP-vs-Legacy-Gap-Audit`](KMP-vs-Legacy-Gap-Audit.md) (2026-06-12).

---

## 0) الخلاصة في سطور (TL;DR)

1. **اللانشر انقلب فعلاً.** `MovitMainActivity` (صدفة KMP) أصبح `LAUNCHER` الإنتاجي في `app/src/main/AndroidManifest.xml`؛ `SplashActivity` legacy لم يعد `LAUNCHER`. الأعلام `movit.shell.launcher.enabled` و`movit.training.kmp.enabled` كلاهما `true` افتراضياً.
2. **اتجاه الاعتماد انعكس.** `MovitTrainingEntryNavigator` صار يوجّه مداخل legacy **إلى** صدفة KMP، لا العكس. **لا يوجد** أي استيراد حيّ من المسار الإنتاجي إلى `poc.training` / `poc.assessment` / `poc.pose` (استثناء وحيد: `WorkoutConfig` model في `MovitShellDeepLinkParser`).
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
| **G4 — قطع الحبال B1–B5,B7** | `MovitDataInstall` لا يستورد أي `com.trainingvalidator.poc.*`؛ كل bindings على تطبيقات KMP-native؛ `SecureSessionMigrationTest` يمرّ على ترقية مستخدم قائم. | بدونها الحذف يكسر الإنتاج |
| **G5 — قرار/تنفيذ الفوترة (B6)** | إمّا `SubscriptionActivity` مُعزول كمكوّن منصّة محتفظ به بوعي، أو منقول. **لا غموض.** | الفوترة لا تتحمّل «نصف هجرة» |
| **G6 — هجرة بيانات المستخدم القائم** | `LegacyWorkoutSyncDrain` + `SecureSessionMigration` يُثبتان على جهاز فيه تثبيت legacy سابق (توكنات + executions معلّقة) — صفر فقد بيانات. | المستخدمون الحاليون |
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

- [ ] `app/src/main/java/com/trainingvalidator/poc/` لا يحوي أي UI أو محرّك تدريب أو assessment (الفئة A = صفر).
- [ ] `MovitDataInstall.kt` و`MovitShellHost.kt` بلا أي `import com.trainingvalidator.poc.*` (عدا مكوّن الفوترة المحتفظ به بوعي إن اختير الخيار 1).
- [ ] `AndroidManifest.xml`: نشاط واحد LAUNCHER (`MovitMainActivity`) + ما تطلّبته الفوترة/FileProvider فقط.
- [ ] لا أعلام `movit.shell.launcher.enabled` / `movit.training.kmp.enabled` (أو ثابتة نهائياً).
- [ ] `SecureSessionMigrationTest` + سيناريو ترقية فعلي = أخضر (صفر فقد جلسات/executions).
- [ ] كل parity من fixtures؛ كل تمرين مدعوم أو مُعلَن خارج النطاق.
- [ ] `assembleRelease` + كل اختبارات الوحدة + iOS compile = أخضر؛ Smoke release موثّق بلقطات.
- [ ] حجم AAB موثّق ومُبرَّر؛ ABIs/نماذج مضبوطة.
- [ ] قرار iOS صريح: «Android 100% KMP-only الآن، iOS عبر WS-9» — لا ادّعاء عابر للمنصّات قبل وصل البوز.

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
