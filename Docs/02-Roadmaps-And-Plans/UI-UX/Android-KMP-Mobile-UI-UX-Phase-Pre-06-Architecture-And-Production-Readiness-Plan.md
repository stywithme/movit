# Android / KMP Mobile UI/UX — Phase Pre-06: Architecture & Production Readiness

**الحالة: مغلقة (CLOSED) — 2026-06-09**  
تقرير الإكمال والتحقق: [`Android-KMP-Mobile-UI-UX-Phase-Pre-06-Completion-Report.md`](Android-KMP-Mobile-UI-UX-Phase-Pre-06-Completion-Report.md)

آخر تحديث: 2026-06-09 (WS-A→WS-G ✅ — تحقق Gradle + grep 2026-06-09)

## حالة التنفيذ (2026-06-09)

| WS | الحالة | ملاحظة |
|----|--------|--------|
| **WS-A** Navigation / Back | ✅ | `BackHandler` + `handleSystemBack()` + 4 اختبارات shell |
| **WS-G** Shell cleanup | ✅ | إزالة `ex-squat-warm` · نصوص Report Detail → `core:resources` |
| **WS-B** Training engine | ✅ | `:core:training-engine` — OneEuroFilter · JointAngleCalculator · RepCounter · PhaseStateMachine · ScoreCalculator + legacy wiring |
| **WS-C** Launcher gate | ✅ | [Launcher Gate](Android-KMP-Mobile-UI-UX-Launcher-Gate.md) — قرار 2026-06-09: flip **بعد WS-D**؛ مثالي بعد 15/16 |
| **WS-D** Secure storage | ✅ | `SecureSessionStore` · Android `EncryptedSharedPreferences` · iOS Keychain · ترحيل legacy · 4 اختبارات data |
| **WS-E** UI scorecards | ✅ | [`Page-Scorecards.md`](Page-Scorecards.md) + [`Sync-App-Pages.md`](Sync-App-Pages.md) — 12 صفحة Phase 05 |
| **WS-F** Visual smoke | ✅ | [`Android-KMP-Mobile-Visual-QA-Checklist.md`](Android-KMP-Mobile-Visual-QA-Checklist.md) + theme boundary tests |

**قرار WS-A (navigation):** الاستمرار على router داخلي (`innerStack` + `MovitAppShellViewModel`) مع `BackHandler` من `ui-backhandler` حتى صفحات 15/16؛ إعادة تقييم Navigation Compose MP عند Program flow.

**قرار WS-C (launcher, 2026-06-09):** لا flip إنتاجي في Pre-06. `SplashActivity` يبقى LAUNCHER. `MovitMainActivity` + `movit.shell.launcher.enabled` + `LegacyTrainingLauncher` جاهزة؛ التحويل بعد WS-D وvisual smoke، مع تفضيل انتظار صفحات 15/16. التفاصيل: [Launcher Gate](Android-KMP-Mobile-UI-UX-Launcher-Gate.md).

**التحقق:**
```text
:feature:shell:testDebugUnitTest              ✅ (20)
:core:training-engine:testDebugUnitTest       ✅ (18)
:core:training-engine:compileKotlinIosSimulatorArm64 ✅
:core:data:testDebugUnitTest                  ✅ (WS-D: migration + logout secure clear)
:feature:account:testDebugUnitTest            ✅
:app:assembleDebug                            ✅
:feature:shell:compileKotlinIosSimulatorArm64 ✅
```

## القرار الحاكم لهذه المرحلة

> **قبل توسيع Phase 05 إلى Program flow / Workout flow أو تحويل Movit Shell إلى launcher إنتاجي، يجب إغلاق فجوات المعمارية والتشغيل التي ظهرت بعد المراجعة المستقلة.**

Phase Pre-05 أغلقت ديون الأساس: الجسور، Koin، iOS lifecycle، النصوص المشتركة، وطبقة البيانات. Phase 05 أثبتت أن مسار KMP/Compose قابل للتوسع صفحة بصفحة. لكن المراجعة الأخيرة كشفت أن المشروع وصل إلى نقطة تحتاج **بوابة Pre-06** قبل التوسع العميق:

- التنقل الداخلي ما زال مخصصاً ويدوياً، ولا توجد معالجة واضحة لزر الرجوع.
- منطق التدريب الخالص ما زال داخل legacy Android، رغم أنه أهم جزء قابل للمشاركة مع iOS.
- الـ launcher الإنتاجي ما زال legacy، مما يخلق صيانة مزدوجة.
- تخزين الجلسة/token لا يصلح كبوابة إنتاج.
- نسب الإنجاز UI/UX ما زالت تقديرية، ولا توجد حماية بصرية كافية.

هذه المرحلة ليست لإضافة صفحات كثيرة جديدة. الهدف هو جعل ما بُني حتى الآن قابلاً للانتقال من **debug pilot** إلى **أساس منتج**.

## نطاق Pre-06

داخل النطاق:

- إصلاح/تثبيت navigation/back stack في KMP shell.
- نقل أول حزمة من منطق التدريب الخالص إلى `commonMain`.
- وضع بوابة واضحة لتحويل Movit shell إلى launcher إنتاجي.
- تأمين تخزين auth/session tokens.
- تحويل نسب إنجاز UI/UX إلى checklists قابلة للقياس.
- إضافة visual/screenshot أو smoke checks للشاشات الأساسية.
- تنظيف تسريبات التجربة المؤقتة داخل shell.

خارج النطاق:

- CameraX / MediaPipe / LiteRT / ONNX runtime.
- تدريب حي بالكاميرا.
- حذف legacy app بالكامل.
- إعادة تصميم visual شاملة جديدة خارج prototypes الحالية.
- إدخال قاعدة بيانات مشتركة مثل SQLDelight ما لم تظهر حاجة مباشرة في WS-B أو WS-C.

## الحالة المتحقَّق منها قبل Pre-06

| البند | الحالة |
|------|--------|
| KMP modules | موجودة: `shared`, `core:*`, `feature:*`, `iosApp` |
| `commonMain` purity | صفر `android.*` / `java.*` |
| Design system discipline | لا raw colors داخل feature commonMain |
| Data layer | `MovitData` + Koin + Ktor + shared repositories |
| iOS lifecycle | `collectAsStateWithLifecycle()` + `ViewModelStoreOwner` |
| Bridges | محذوفة |
| Account 10-14 | أول إصدار KMP موجود |
| Shell | ما زال debug-only |
| Navigation | stack يدوي داخل `MovitAppShellState` |
| Pure training logic | ما زال داخل `:app` legacy |

## مسارات العمل

كل مسار يستخدم نفس القالب: المشكلة، المخرج، خطوات التنفيذ، معايير القبول.

---

### WS-A — إصلاح Navigation وBack Stack

**المشكلة:**  
التنقل الحالي داخل `feature:shell` مبني على `MovitAppDestination` للتبويبات و`innerStack: List<MovitInnerRoute>` للمسارات الداخلية. هذا مقبول كإثبات، لكنه ليس كافياً قبل Program flow / Workout flow:

- لا توجد معالجة واضحة لـ Android back button.
- لا توجد deep links.
- لا يوجد saved state للـ stack عند process death.
- ازدياد المسارات الداخلية سيزيد تعقيد الـ `when` اليدوي.

**المخرج المطلوب:**  
Navigation قابل للاستمرار على Android/iOS، مع back behavior واضح ومختبر.

**قرار التنفيذ المقترح:**

1. المدى القصير داخل Pre-06:
   - إضافة `BackHandler` في shell layer.
   - `Back` يعمل كالتالي:
     - إذا يوجد `innerStack` ⇒ `popInnerRoute`.
     - إذا لا يوجد stack والتبويب ليس Home ⇒ ارجع Home.
     - إذا Home ولا stack ⇒ اترك النظام يغلق الشاشة.
   - حفظ `selectedDestination` و`innerStack` عبر state قابل للاستعادة إن أمكن.

2. المدى المتوسط:
   - تقييم `Navigation Compose Multiplatform` قبل Program flow 15/16.
   - القرار النهائي: إما اعتماد Navigation MP، أو تثبيت router داخلي رسمي باختبارات وسلوك موثق.

**خطوات:**

- مراجعة:
  - `feature/shell/src/commonMain/.../MovitAppShellRoute.kt`
  - `MovitAppShellViewModel.kt`
  - `MovitAppShellState.kt`
  - `MovitInnerRoute.kt`
  - `MovitInnerHost.kt`
- إضافة events واضحة:
  - `BackPressed`
  - `InnerRoutePushed`
  - `InnerRoutePopped`
  - `TabSelected`
- إضافة اختبارات:
  - back من `Assessment` يرجع للصفحة السابقة.
  - back من `LevelProfile` يرجع للـ Home/Profile حسب مصدر الدخول.
  - back من tab غير Home يرجع Home.
  - stack لا يضيع عند tab switch.
- إزالة أي navigation placeholder لا يمثل سلوكاً حقيقياً.

**قبول:**

- زر الرجوع في Android لا يغلق التطبيق أثناء وجود inner route.
- اختبارات shell state تغطي back stack.
- لا يوجد route داخلي hardcoded لا يمكن الوصول إليه أو لا يمكن الرجوع منه.
- قرار مكتوب: الاستمرار على router داخلي أم الانتقال لـ Navigation MP قبل صفحات 15/16.

---

### WS-B — نقل Pure Training Engine إلى `commonMain`

**المشكلة:**  
أكبر قيمة في التطبيق ليست UI فقط، بل منطق التدريب والتحليل. حالياً أجزاء كثيرة قابلة للمشاركة ما زالت داخل legacy Android:

- `OneEuroFilter`
- `AngleCalculator`
- `ScoreCalculator`
- `PhaseStateMachine`
- `RepCounter`
- `AssessmentEngine`
- confidence / scoring / feedback policy logic

هذا يعني أن iOS يملك Shell وواجهات وAPI، لكنه لم يثبت بعد أنه يستطيع تشغيل جوهر التدريب.

**المخرج المطلوب:**  
أول حزمة pure Kotlin من محرك التدريب تعمل في `commonMain` باختبارات مشتركة، بدون CameraX أو MediaPipe.

**قاعدة الفصل:**

```text
commonMain:
  Landmark
  JointAngles
  PoseFrame
  AngleCalculator
  OneEuroFilter
  ScoreCalculator
  RepCounter core
  PhaseStateMachine core
  TrainingFeedbackPolicy

androidMain:
  CameraXFrameSource
  MediaPipePoseDetector
  LiteRtClassifier
  AndroidAudioFeedback

iosMain:
  IosCameraFrameSource
  IosPoseDetectorAdapter
  IosAudioFeedback
```

**خطوات:**

- إنشاء موديول أو مساحة واضحة، حسب حجم الاعتماديات:
  - خيار مفضل: `core:training-engine`
  - خيار مؤقت: `shared/src/commonMain/.../training`
- نقل النماذج الخالصة فقط:
  - لا Android `Context`.
  - لا MediaPipe classes.
  - لا timestamps من `System.currentTimeMillis()` إلا عبر abstraction.
  - لا ملفات assets مباشرة.
- تعريف boundary types:
  - `PoseLandmark`
  - `PoseFrame`
  - `JointAngle`
  - `TrainingFrameResult`
- كتابة golden tests:
  - نفس landmarks ⇒ نفس angles.
  - smoothing deterministic.
  - rep counter ينتقل بين الحالات المتوقعة.
  - scoring لا يعتمد على platform.
- إبقاء legacy Android يستخدم المنطق الجديد تدريجياً بدل نسخه.

**قبول:**

- `:core:training-engine:test` أو `:shared:testDebugUnitTest` أخضر.
- `compileKotlinIosSimulatorArm64` يمر للموديول الجديد.
- لا imports من Android/MediaPipe في commonMain.
- أول feature أو test في iOS يستخدم نفس engine models بدون fake-only path.

---

### WS-C — بوابة Launcher وتخفيض الصيانة المزدوجة

**المشكلة:**  
Movit shell ما زال `debug-only`، بينما launcher الإنتاجي legacy. هذا صحيح أثناء الإثبات، لكنه يخلق مشكلتين:

- أي تحسين UI في legacy قد يتكرر لاحقاً في KMP.
- الفريق قد يواصل إصلاح مسارين بدلاً من نقل المنتج تدريجياً إلى مسار واحد.

**المخرج المطلوب:**  
قرار إنتاجي واضح: متى يصبح Movit Shell هو launcher، وما الذي يبقى legacy خلفه.

**القرار المقترح:**  
لا يتم تحويل launcher قبل إغلاق WS-A وWS-D ووجود Auth/Profile usable. لكن بعد ذلك يجب أن يصبح التحويل هدفاً قريباً، لا بنداً مفتوحاً.

**خطوات:**

- إنشاء مستند صغير أو قسم داخل هذه المرحلة باسم `Launcher Gate`.
- تعريف الوضعيات:
  - `LegacyMainActivity`: الإنتاج الحالي.
  - `MovitShellPilotActivity`: debug فقط.
  - `MovitMainActivity`: launcher الإنتاجي المستهدف.
- إعداد plan لتحويل الإنتاج:
  - route من Movit shell إلى legacy TrainingActivity عند الحاجة.
  - route إلى legacy camera flow مؤقتاً فقط خلف boundary واضح.
  - لا تحديث UI legacy إلا لإصلاحات حرجة أو compatibility.
- إضافة feature flag أو build variant إن لزم:
  - `movitShellLauncherEnabled`.

**قبول:**

- قرار مكتوب ومؤرخ: هل shell يتحول في هذه المرحلة أم بعد صفحات 15/16.
- لا يتم تعديل legacy UI إلا بسبب bug واضح أو unblock.
- عند فتح WS-G التنفيذية، يصبح `releaseRuntimeClasspath` يحتوي Movit shell عمداً وليس كخطأ.

---

### WS-D — Secure Auth / Session Storage

**المشكلة:**  
مسار auth أصبح حقيقياً، لكن تخزين access token/session عبر `SharedPreferences` أو `UserDefaults` ليس مناسباً كبوابة إنتاج.

**المخرج المطلوب:**  
تخزين آمن ومشترك عبر `MovitPlatformBindings`، بدون أن يعرف commonMain تفاصيل Keychain أو Android encrypted storage.

**خطوات:**

- توسيع `MovitPlatformBindings` أو إنشاء contract منفصل:
  - `SecureSessionStore`
  - `saveSession(accessToken, refreshToken, userMetadata)`
  - `readSession()`
  - `clearSession()`
- Android actual:
  - `EncryptedSharedPreferences` أو DataStore مشفر إن كان متاحاً ومناسباً.
- iOS actual:
  - Keychain.
- ترحيل المفاتيح القديمة:
  - قراءة من التخزين القديم مرة واحدة.
  - كتابة للتخزين الآمن.
  - حذف القديم بعد نجاح الترحيل.
- اختبارات:
  - common contract tests بمخزن fake.
  - Android instrumentation أو unit wrapper حسب الإمكانية.

**قبول:**

- لا يتم حفظ `access_token` في `UserDefaults` أو `SharedPreferences` العادي في المسار الإنتاجي.
- logout يمسح التخزين الآمن والقديم.
- iOS shell يعمل بتوكن حقيقي من التخزين الآمن.

---

### WS-E — تحويل نسب UI/UX إلى Definition of Done قابلة للقياس

**المشكلة:**  
النسب مثل `Auth 85%` أو `Train 72%` مفيدة للمدير، لكنها تقديرية. بدون checklist، قد تبدو الشاشة مكتملة رغم وجود فجوات accessibility أو RTL أو visual polish.

**المخرج المطلوب:**  
كل صفحة لها scorecard واضح يفسر نسبة الإنجاز.

**قالب scorecard لكل صفحة:**

| المجال | الوزن | أمثلة |
|--------|-------|-------|
| Functional flow | 25% | actions، navigation، API، errors |
| Visual parity | 20% | prototype match، hierarchy، spacing، imagery |
| Design system compliance | 15% | no raw colors، Movit components، typography |
| i18n / RTL | 15% | ar/en، layout direction، long text |
| Accessibility | 10% | content descriptions، font scale، touch targets |
| Tests | 10% | ViewModel/repository/shell tests |
| iOS readiness | 5% | compile + smoke |

**خطوات:**

- إنشاء أو تحديث `Sync-App-Pages.md` بحيث يحتوي scorecard لكل صفحة.
- ربط نسب الإنجاز الحالية بالبنود أعلاه.
- لا تُرفع شاشة من 80% إلى 90% بدون:
  - error/empty/loading states.
  - RTL review.
  - dark mode review.
  - iOS compile أو smoke إذا كانت shared.

**قبول:**

- كل صفحة Phase 05 لها checklist واضحة.
- النسبة لا تُكتب وحدها بدون سبب.
- توجد فجوات UX محددة وليست عامة.

---

### WS-F — Visual Regression / Screenshot Smoke

**المشكلة:**  
Design system والشاشات تتوسع بسرعة، لكن الحماية الحالية تركّز على ViewModels/mappers. لا توجد حماية كافية ضد كسر بصري في Train/Home/Auth/Profile.

**المخرج المطلوب:**  
مجموعة checks خفيفة تحمي أهم الشاشات، حتى لو بدأت كـ manual smoke موثق ثم تتحول إلى screenshot tests.

**خطوات:**

- تحديد الشاشات الأولى:
  - Home
  - Train
  - Auth
  - Profile
  - Assessment
  - Reports
- الحالات:
  - light
  - dark
  - Arabic/RTL
  - loading
  - empty/error
- اختيار الأداة:
  - Android screenshot testing لاحقاً.
  - في Pre-06 يمكن بدء manual screenshot checklist إن كان إدخال الأداة سيؤخر التنفيذ.
- حفظ screenshots مرجعية أو checklist في docs.

**قبول:**

- لا تُغلق صفحة جديدة بدون visual QA.
- توجد صور أو checklist واضحة لأهم الحالات.
- regressions البصرية الأساسية لا تمر بصمت.

**مُنجَز (2026-06-09):** [`Android-KMP-Mobile-Visual-QA-Checklist.md`](Android-KMP-Mobile-Visual-QA-Checklist.md) — manual checklist لـ Home/Train/Auth/Profile/Assessment/Reports (light/dark/ar/loading/empty/error) + أوامر `adb` لـ `MovitShellPilotActivity` + Definition of Done + `*ThemeBoundaryTest` في `feature:account` و`feature:reports`.

---

### WS-G — تنظيف تسريبات التجربة المؤقتة داخل Shell

**المشكلة:**  
بعض المسارات داخل shell ما زالت تحمل قيم أو رسائل مؤقتة:

- ids ثابتة مثل `ex-squat-warm`.
- رسائل إنجليزية hardcoded مثل `Share report - coming soon`.
- CTAs تفتح placeholder بدل قرار product واضح.

**المخرج المطلوب:**  
كل placeholder داخل shell يكون إما:

- feature flag موثق.
- رسالة من `core:resources`.
- route حقيقي.
- أو محذوف من المسار الإنتاجي.

**خطوات:**

- فحص `feature/shell/src/commonMain`.
- نقل أي رسالة ظاهرة للمستخدم إلى `core:resources`.
- إزالة ids ثابتة أو استبدالها ببيانات من state/repository.
- لو flow غير جاهز:
  - أظهر disabled state واضح.
  - أو coming soon من resources.
  - أو اخفِ الزر من الإنتاج.

**قبول:**

- صفر نصوص إنجليزية ظاهرة hardcoded داخل shell commonMain.
- صفر ids تمرينية ثابتة في navigation الإنتاجي.
- كل CTA له behavior موثق.

---

## ترتيب التنفيذ المقترح

1. **WS-A Navigation/back** — لأنه يؤثر على كل الصفحات القادمة.
2. **WS-G shell cleanup** — سريع ويمنع تراكم placeholders.
3. **WS-E scorecards** — يحسن متابعة الإنجاز فوراً.
4. **WS-F visual smoke** — يحمي ما تم إنجازه.
5. **WS-D secure storage** — ضروري قبل production auth.
6. **WS-B pure training engine** — أكبر قيمة استراتيجية ويبدأ قبل Phase 07.
7. **WS-C launcher gate** — القرار النهائي بعد وضوح WS-A/WS-D وAuth/Profile.

## أوامر التحقق

```powershell
cd android-poc
.\gradlew.bat --console=plain :app:assembleDebug
.\gradlew.bat --console=plain :feature:shell:testDebugUnitTest
.\gradlew.bat --console=plain :feature:account:testDebugUnitTest
.\gradlew.bat --console=plain :core:data:testDebugUnitTest
.\gradlew.bat --console=plain :feature:shell:compileKotlinIosSimulatorArm64
```

بعد إنشاء `core:training-engine`:

```powershell
cd android-poc
.\gradlew.bat --console=plain :core:training-engine:test
.\gradlew.bat --console=plain :core:training-engine:compileKotlinIosSimulatorArm64
```

فحوص نصية مطلوبة:

```powershell
rg "^import android\.|^import java\." android-poc/**/commonMain
rg "Color\(0x|#[0-9A-Fa-f]{6}" android-poc/feature/**/commonMain
rg "ex-squat-warm|coming soon|Share report|Export report" android-poc/feature/shell/src/commonMain
```

## بوابة خروج Pre-06

لا يتم تحويل Movit shell إلى launcher إنتاجي، ولا يتم فتح Program flow / Workout flow بشكل واسع، قبل:

- [x] زر الرجوع يعمل بشكل صحيح مع inner routes.
- [x] shell navigation عليه اختبارات back stack.
- [x] لا توجد ids أو رسائل مؤقتة hardcoded في shell production path.
- [x] أول حزمة من pure training engine تعمل في `commonMain` وتكمبّل لـ iOS.
- [x] access/refresh tokens لا تُحفظ في storage عادي في المسار الإنتاجي (WS-D: `SecureSessionStore`).
- [x] لكل صفحة منجزة scorecard يشرح نسبة الإنجاز.
- [x] Home/Train/Auth/Profile/Assessment/Reports لديهم visual QA checklist — [`Android-KMP-Mobile-Visual-QA-Checklist.md`](Android-KMP-Mobile-Visual-QA-Checklist.md).
- [x] قرار launcher مكتوب: متى يتحول الإنتاج من legacy إلى Movit shell — [Launcher Gate](Android-KMP-Mobile-UI-UX-Launcher-Gate.md) (بعد WS-D؛ مثالي بعد 15/16).

## العلاقة مع Phase 05 وPhase 07

- **Phase 05** تستمر في page-by-page modernization، لكن Pre-06 تمنع تضخم shell قبل إصلاح navigation/back وتحديد معايير الإنجاز.
- **Phase 07** تبقى مرحلة camera/ML/platform adapters، لكن Pre-06 يسحب منها الجزء الخالص فقط: الحسابات، الفلاتر، state machines، scoring.
- **Legacy Android** يبقى مرجع سلوك ومصدر adapters، وليس مسار UI طويل الأمد.

## ملاحظات للمدير

Pre-06 لا تضيف "شكل جديد" مباشر، لكنها تقلل مخاطر الانتقال من prototype إلى منتج:

- تجعل التطبيق لا ينهار سلوكياً عند زر الرجوع.
- تثبت أن iOS سيشارك منطق التدريب، لا الواجهة فقط.
- تمنع استمرار الصيانة المزدوجة بين legacy وKMP.
- ترفع auth من demo إلى مسار أقرب للإنتاج.
- تجعل نسب الإنجاز قابلة للمحاسبة لا مجرد تقدير.

الخلاصة: **Pre-05 ثبّت الأساس التقني. Pre-06 يحوّل الأساس إلى مسار إنتاج قابل للثقة.**
