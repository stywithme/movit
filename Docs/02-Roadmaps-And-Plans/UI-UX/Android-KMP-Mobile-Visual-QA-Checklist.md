# Android KMP Mobile — Visual QA Checklist (WS-F)

آخر تحديث: 2026-06-09  
النطاق: `kmp-app` · Movit KMP Shell (`MovitShellPilotActivity`)  
المرجع البصري: [`prototypes/`](prototypes/) (HTML prototypes Phase 05)

---

## الهدف

حماية الشاشات الأساسية من **الانحدار البصري** (spacing، hierarchy، ألوان، RTL، dark mode) قبل إغلاق أي صفحة جديدة أو رفع نسبة الإنجاز في Phase 05 / Pre-06.

> **قرار Pre-06:** manual checklist أولاً؛ screenshot automation لاحقاً عندما يصبح Compose screenshot test مجدياً — لا نؤخر WS-F بانتظار أدوات ثقيلة.

---

## تشغيل Shell للفحص اليدوي

### 1) بناء وتثبيت (debug)

```powershell
cd kmp-app
.\gradlew.bat --console=plain :app:installDebug
```

### 2) فتح Movit KMP Shell

```powershell
adb shell am start -n com.trainingvalidator.poc/com.movit.debug.MovitShellPilotActivity
```

> في **debug** builds، `MovitShellPilotActivity` مسجّلة كـ launcher. يمكن أيضاً فتحها من قائمة التطبيقات تحت **«Movit KMP Shell»**.

### 3) أوامر مساعدة للحالات

| الحالة | الأمر / الإجراء |
|--------|------------------|
| **Dark mode** | `adb shell cmd uimode night yes` — للعودة: `adb shell cmd uimode night no` |
| **Light mode** | `adb shell cmd uimode night no` |
| **عربي / RTL** | إعدادات الجهاز → اللغة → العربية، أو `adb shell "settings put system system_locales ar-SA"` — للعودة: `en-US` |
| **لقطة شاشة** | `adb exec-out screencap -p > screenshot-home-light.png` |
| **شبكة معطّلة (error)** | وضع الطيران ON ثم فتح التبويب أو Retry |
| **بطء التحميل (loading)** | فتح التبويب مباشرة بعد cold start أو مع شبكة بطيئة (Developer → Network throttling) |

### 4) التنقل السريع بين الشاشات

| الشاشة | المسار داخل Shell |
|--------|-------------------|
| **Home** | التبويب السفلي الأول (Home) — الافتراضي عند الفتح |
| **Train** | التبويب Train |
| **Profile** | التبويب Profile |
| **Reports** | التبويب Reports |
| **Auth** | Profile (غير مسجّل) → «تسجيل الدخول»، أو Profile → Sign in / Auth CTA |
| **Assessment** | Home → CTA التقييم، أو Profile → Assessment |

زر **الرجوع** (Android): يخرج من inner route (Auth، Assessment، Report detail…) قبل إغلاق التطبيق.

---

## مرجع Prototypes

افتح [`prototypes/index.html`](prototypes/index.html) في المتصفح للمقارنة جنباً إلى جنب مع الجهاز.

| الشاشة | Prototype | ملف KMP رئيسي |
|--------|-----------|----------------|
| Home | [`08-home.html`](prototypes/08-home.html) | `feature/home/.../MovitHomeScreen.kt` |
| Train | [`01-train.html`](prototypes/01-train.html) | `feature/train/.../MovitTrainScreen.kt` |
| Auth | [`10-auth.html`](prototypes/10-auth.html) | `feature/account/.../MovitAuthScreen.kt` |
| Profile | [`11-profile.html`](prototypes/11-profile.html) | `feature/account/.../MovitProfileScreen.kt` |
| Assessment | [`13-assessment.html`](prototypes/13-assessment.html) | `feature/account/.../MovitAssessmentScreen.kt` |
| Reports | [`09-reports.html`](prototypes/09-reports.html) | `feature/reports/.../MovitReportsScreen.kt` |

مكوّنات مشتركة: [`00-components.html`](prototypes/00-components.html) · [`app.css`](prototypes/app.css)

---

## معايير الفحص المشتركة (كل شاشة)

طبّق هذه النقاط مع كل checkbox أدناه:

- [ ] **Hierarchy:** العنوان / eyebrow / CTA واضحون كما في prototype
- [ ] **Spacing:** `MovitSpacing` — لا تكدّس ولا فراغات عشوائية
- [ ] **Colors:** أدوار `MaterialTheme` / `movitColors` — لا raw hex في الشاشة
- [ ] **Typography:** أوزان وعناوين متسقة مع design system
- [ ] **Touch targets:** أزرار وصفوف قابلة للنقر ≥ 48dp تقريباً
- [ ] **Bottom nav:** لا يحجب المحتوى؛ safe area / edge-to-edge صحيح
- [ ] **i18n:** نصوص من `core:resources` / `movitText` — لا إنجليزية hardcoded ظاهرة للمستخدم في المسار الإنتاجي

---

## Definition of Done — Visual QA (بوابة إغلاق الصفحة)

**لا تُغلق صفحة Phase 05 ولا تُرفع نسبتها في scorecard (WS-E) قبل اكتمال البنود التالية:**

### إلزامي

1. [ ] **Prototype parity:** مراجعة بصرية مع ملف HTML المقابل في `prototypes/` — الفروقات موثّقة (مقبولة أو مُسجّلة كدين)
2. [ ] **Light + Dark:** الحالة الافتراضية (محتوى ناجح) في الوضعين
3. [ ] **Arabic RTL:** نفس الحالة الافتراضية بالعربية — اتجاه التخطيط، محاذاة الأيقونات، عدم قصّ النص الطويل
4. [ ] **Loading:** `MovitLoadingState` أو skeleton ظاهر ومتّسق (لا شاشة بيضاء)
5. [ ] **Empty أو Error (حسب الصفحة):** على الأقل حالة واحدة منهما مُختبرة؛ وإن وُجدتا في UI فيجب اختبارهما معاً
6. [ ] **Error + Retry:** إن وُجد `MovitErrorState` — زر Retry يعمل
7. [ ] **Theme boundary:** شاشة الـ feature لا تلفّ نفسها بـ `MovitTheme` (الـ theme من `MovitShellPilotActivity` فقط) — يُتحقق عبر `*ThemeBoundaryTest` إن وُجد
8. [ ] **تسجيل النتيجة:** اسم المُراجع، التاريخ، build/git ref، وجهاز/محاكي — في PR أو تعليق scorecard

### موصى به (لا يعيق الإغلاق الأول)

- [ ] لقطة شاشة واحدة على الأقل لكل من: light / dark / ar (مجلد الفريق أو مرفق PR)
- [ ] Font scale 1.3x (إعدادات إمكانية الوصول) — لا كسر layout حرج
- [ ] iOS smoke compile للموديول المشترك: `:feature:<name>:compileKotlinIosSimulatorArm64`

### رفض تلقائي (لا merge)

- انكسار layout في RTL أو dark
- نصوص مقطوعة في العناوين الرئيسية
- ألوان خام خارج design system في `commonMain`
- regression واضح مقارنة بآخر مراجعة موثّقة

---

## Checklist — Home

**Prototype:** [`08-home.html`](prototypes/08-home.html)  
**الوصول:** تبويب Home  
**حالات UI:** loading · error · empty (لا برنامج) · success (dashboard كامل)

### Light

- [ ] Hero + greeting + metric tiles
- [ ] Today plan card (أو empty plan)
- [ ] Quick actions + journey / recent activities
- [ ] Level / assessment CTAs إن ظهرت

### Dark

- [ ] نفس نقاط Light — تباين النص والبطاقات مقبول
- [ ] Accent blocks / insight cards مقروءة

### Arabic / RTL

- [ ] محاذاة Hero وtiles مع RTL
- [ ] أيقونات القوائم في الجهة الصحيحة
- [ ] نصوص `home_*` من `values-ar` دون overflow حرج

### Loading

- [ ] **كيف:** cold start على Home أو بطء شبكة عند أول تحميل
- [ ] `MovitLoadingState` مع `home_loading` — لا محتوى وهمي مختلط

### Empty

- [ ] **كيف:** حساب بدون برنامج نشط (`showNoProgramEmpty` / today plan فارغ)
- [ ] `MovitEmptyState` أو Today plan empty — CTA واضح

### Error

- [ ] **كيف:** وضع الطيران ON → فتح Home → Retry
- [ ] `MovitErrorState` + Retry يعيد التحميل

---

## Checklist — Train

**Prototype:** [`01-train.html`](prototypes/01-train.html)  
**الوصول:** تبويب Train  
**حالات UI:** loading · error · no-plan / rest-day (بديل empty) · active plan

### Light

- [ ] Status banner + today card
- [ ] Week preview (عند وجود خطة)
- [ ] Readiness + report section
- [ ] Primary CTA حسب `TrainDashboardStatus`

### Dark

- [ ] Banner وtoday card — ألوان الحالة (active / rest / complete)
- [ ] Charts / week chips مقروءة

### Arabic / RTL

- [ ] عنوان `train_title` وsubtitle
- [ ] ترتيب أيام الأسبوع / chips منطقي في RTL
- [ ] أزرار Explore / Start محاذاة صحيحة

### Loading

- [ ] **كيف:** فتح Train مباشرة بعد kill process
- [ ] `train_loading` — لا dashboard جزئي

### Empty (No plan / Rest day)

- [ ] **كيف:** حساب بدون خطة أو يوم راحة (حسب بيانات API / fallback)
- [ ] `TrainNoPlanSection` أو banner rest-day — CTA إلى Explore

### Error

- [ ] **كيف:** وضع الطيران ON على Train → Retry
- [ ] رسالة خطأ + Retry

---

## Checklist — Auth

**Prototype:** [`10-auth.html`](prototypes/10-auth.html)  
**الوصول:** Profile → Sign in (inner route `MovitInnerRoute.Auth`)  
**حالات UI:** Sign in · Sign up · Forgot password · loading · error

### Light

- [ ] Sign in: حقول email/password + روابط forgot / sign up
- [ ] Sign up: حقول إضافية + submit
- [ ] Forgot: تعليمات + submit
- [ ] زر الرجوع يغلق Auth ويعود Profile/Home stack

### Dark

- [ ] حقول النماذج وحدود الحقول واضحة
- [ ] رسالة الخطأ (`error` color) مقروءة

### Arabic / RTL

- [ ] تسميات الحقول ومحاذاة الإدخال RTL
- [ ] أزرار primary/full-width بعرض كامل
- [ ] نصوص `auth_*` عربية كاملة

### Loading

- [ ] **كيف:** تسجيل دخول ببيانات صحيحة (شبكة بطيئة) أو sign up
- [ ] الأزرار `enabled = false` أثناء التحميل — لا double submit

### Empty

- [ ] **N/A** — لا empty state مستقل؛ استخدم نموذج فارغ (حقول blank) للتحقق من validation messages

### Error

- [ ] **كيف:** بريد/كلمة مرور خاطئة، أو شبكة معطّلة عند submit
- [ ] `errorMessage` فوق النموذج — لون `MaterialTheme.colorScheme.error`

---

## Checklist — Profile

**Prototype:** [`11-profile.html`](prototypes/11-profile.html)  
**الوصول:** تبويب Profile  
**حالات UI:** loading · signed-out empty · error · signed-in success

### Light

- [ ] Signed out: empty + CTA تسجيل الدخول
- [ ] Signed in: avatar/header، stats، settings rows، toggles
- [ ] روابط Assessment / Level / Onboarding تعمل

### Dark

- [ ] Cards وlist rows — فواصل واضحة
- [ ] Switch / accent colors

### Arabic / RTL

- [ ] `profile_title` وصفوف الإعدادات RTL
- [ ] أيقونة trailing/leading في `MovitListRow` معكوسة منطقياً

### Loading

- [ ] **كيف:** فتح Profile بعد cold start
- [ ] `profile_loading`

### Empty (Signed out)

- [ ] **كيف:** logout أو جلسة غير موجودة
- [ ] `profile_sign_in_prompt` + زر Auth

### Error

- [ ] **كيف:** signed-in + شبكة معطّلة + Retry
- [ ] `MovitErrorState` — لا خلط مع empty signed-out

---

## Checklist — Assessment

**Prototype:** [`13-assessment.html`](prototypes/13-assessment.html)  
**الوصول:** Home → Assessment، أو Profile → Assessment (`MovitInnerRoute.Assessment`)  
**حالات UI:** PreScreening (PAR-Q) · BodyScan · Results

### Light

- [ ] PreScreening: أسئلة PAR-Q + تقدم
- [ ] BodyScan: progress + movement label
- [ ] Results: scores + recommendations + CTAs

### Dark

- [ ] بطاقات الأسئلة والنتائج — تباين كافٍ
- [ ] Progress indicators مرئية

### Arabic / RTL

- [ ] نصوص الأسئلة والنتائج من resources
- [ ] أزرار التنقل (التالي / إنهاء) بمحاذاة RTL

### Loading

- [ ] **كيف:** انتقال بين phases إن وُجد تأخير شبكة (أو مراقبة أول فتح)
- [ ] لا قفز مفاجئ بين phases بدون feedback

### Empty

- [ ] **N/A** للـ flow الكامل؛ تحقق من PAR-Q بلا إجابات (الحالة الافتراضية) — لا crash

### Error

- [ ] **كيف:** `ShowMessage` من ViewModel أو فشل شبكة عند حفظ (إن مُفعّل)
- [ ] رسالة خطأ/snackbar واضحة — المستخدم لا يُحاصر بدون back

---

## Checklist — Reports

**Prototype:** [`09-reports.html`](prototypes/09-reports.html)  
**الوصول:** تبويب Reports  
**حالات UI:** loading · locked empty · data empty · error · success (charts + KPIs)

### Light

- [ ] Tab bar (Overview / Exercises / … حسب التنفيذ)
- [ ] KPI grid + charts عند `ReportsHubState.Success`
- [ ] Report list / detail navigation إن وُجد

### Dark

- [ ] Charts (bar/line) — محاور وتسميات مقروءة
- [ ] Segmented control / tabs

### Arabic / RTL

- [ ] `reports_title` ومحاور الرسوم
- [ ] ترتيب KPI grid في RTL

### Loading

- [ ] **كيف:** فتح Reports بعد cold start
- [ ] `reports_loading`

### Empty

- [ ] **Locked:** مستخدم غير مؤهل — `MovitEmptyState` + CTA
- [ ] **Empty data:** لا تقارير بعد — empty copy من resources

### Error

- [ ] **كيف:** وضع الطيران ON → Retry
- [ ] `hubState == Error` + `MovitErrorState`

---

## حماية آلية خفيفة (Theme Boundary Tests)

اختبارات JVM تمنع لفّ شاشات الـ feature بـ `MovitTheme` (يجب أن يبقى الـ theme عند جذر Shell فقط):

| الموديول | ملف الاختبار |
|----------|----------------|
| `feature:home` | `MovitHomeThemeBoundaryTest` |
| `feature:train` | `MovitTrainThemeBoundaryTest` |
| `feature:explore` | `ExploreThemeBoundaryTest` |
| `feature:account` | `MovitAccountThemeBoundaryTest` |
| `feature:reports` | `MovitReportsThemeBoundaryTest` |

```powershell
cd kmp-app
.\gradlew.bat --console=plain :feature:home:testDebugUnitTest :feature:train:testDebugUnitTest :feature:account:testDebugUnitTest :feature:reports:testDebugUnitTest
```

> **لاحقاً (اختياري):** Compose screenshot tests (`compose-ui-test` + golden images) للشاشات أعلاه — خارج نطاق Pre-06 WS-F الأولي.

---

## سجل المراجعة (نموذج)

| التاريخ | المراجع | Git ref / build | الشاشات | Light | Dark | AR | Loading | Empty | Error | ملاحظات |
|---------|---------|-----------------|---------|-------|------|-----|---------|-------|-------|---------|
| | | | | | | | | | | |

---

## الربط مع Pre-06

- **WS-F:** هذا المستند هو المخرج الرسمي لـ Visual smoke.
- **WS-E:** لا تُحدَّث نسبة Visual parity في scorecard بدون إشارة إلى صفوف checklist مكتملة.
- **بوابة خروج Pre-06:** Home / Train / Auth / Profile + Reports / Assessment مغطاة هنا.
