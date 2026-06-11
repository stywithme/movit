# تدقيق واقع الهجرة (KMP) — الحالة الفعلية مقابل الموثّق

**تاريخ المراجعة:** 2026-06-10
**المُراجِع:** تدقيق مستقل (قراءة كود + prototypes + legacy + مقارنة بالوثائق)
**الفرع:** `codex/kmp-mobile-foundation`
**النطاق:** كل صفحات Phase 05 + حدود Phase 07، مقابل [`prototypes/`](prototypes/) والـ legacy `app/` والوثائق ([`Page-Scorecards.md`](Page-Scorecards.md) · [`Backend-Contract-Matrix.md`](Backend-Contract-Matrix.md) · [`Android-KMP-Mobile-Visual-QA-Checklist.md`](Android-KMP-Mobile-Visual-QA-Checklist.md)).

> **سبب هذا المستند:** بعد إغلاق أول 6 مراحل، ظهر تباعد واضح بين ما تَعِد به الوثائق (نِسب 66–92%) وما يُرى فعلياً على الشاشات. هذا المستند يفصل **ما الذي أُنجِز حقاً**، **أين الفجوة**، **لماذا الأرقام مُضخّمة**، و**كيف نُغلق الفجوة فعلياً** — بأدلة من الكود.

---

## 0) الخلاصة في سطور (TL;DR)

1. **ما أُنجِز حقيقي لكنه ليس ما يقيسه العنوان.** البنية الجديدة (KMP modules + Design System + i18n + UDF + iOS compile + طبقة Ktor) **مبنيّة وتعمل**. لكن هذا «جاهزية بنية تحتية»، وليس «إعادة إنتاج التطبيق».
2. **قلب المنتج كان غير مهاجَر، وبدأ يُغلق الآن.** بعد P0/P1 صار لدينا مسار تدريب KMP واحد مثبت + Body Scan MVP حقيقي في `feature:account`، لكن parity الكامل مع legacy ما زال غير مكتمل: بقية التمارين/المسارات، iOS camera، body map، وحفظ/feedback أعمق.
3. **الأرقام مُضخّمة بحكم المنهجية.** 35% من الوزن (Design System 15% + i18n 15% + iOS 5%) يُمنح **88–100% لكل صفحة تقريباً** لأنه يقيس البنية لا المطابقة. هذا يرفع أرضية كل صفحة بنحو **+33 نقطة** بصرف النظر عن مطابقتها للـ prototype أو الـ legacy.
4. **المطابقة البصرية جزئية، والبيانات الوهمية منتشرة.** الشاشات تنسخ **هيكل** الأقسام لا **تفاصيلها**، وتسقط على بيانات `Fake*`/`Preview` عند أي فشل/عدم تثبيت `MovitData` (كما في pilot الـ QA) — وهذا أرجح سبب لإحساسك بأن «كل الصفحات مختلفة».
5. **النتيجة:** الوثائق ليست كاذبة، لكنها تُجيب على سؤال **«هل قامت البنية؟» (نعم، بنسبة عالية)** وتعرضه كأنه إجابة على سؤال **«هل أُعيد إنتاج التطبيق؟» (لا، بنسبة منخفضة)**. الفرق بين السؤالين **هو** الفجوة التي تشعر بها.

---

## 1) منهجية هذه المراجعة (ماذا فحصتُ فعلاً)

| ما قرأته | الغرض |
|----------|-------|
| الوثائق الثلاث كاملة: Page-Scorecards · Backend-Contract-Matrix · Visual-QA-Checklist | فهم ما هو **مُدّعى** |
| Prototypes: `08-home` · `01-train` · `14-level-plan` · `13-assessment` (قراءة عميقة) + جرد كل الـ 18 | المرجع البصري **المطلوب** |
| شاشات KMP: `MovitHomeScreen` · `MovitTrainScreen` · `MovitLevelScreen` · `MovitAssessmentScreen` · `WorkoutRunScreen` (عميق) + جرد الـ 22 شاشة | ما هو **مُنفَّذ** |
| Repositories الإنتاج: `SharedProgramFlowRepository` · `SharedLevelRepository` + مسح `Fake*`/`Preview`/`fallback` في كل `feature/` | مصدر البيانات الحقيقي |
| `core/` كله + `core:training-engine` (الملفات + التبعيات في `build.gradle.kts`) | هل المحرّك موصول؟ |
| الـ legacy: شجرة `app/.../poc/` + قياس أسطر `training/engine` · `assessment` · `ui/*` | حجم المنطق غير المنقول |

> **شفافية:** هذه **عيّنة تمثيلية عميقة**، ليست تدقيقاً بكسل-بكسل لكل صفحة. لكن الأنماط التي ظهرت **منهجية** (تتكرر في كل الموديولات)، والاستنتاجات المبنية على بنية الكود وتبعياته قاطعة. خانة «النسبة الواقعية» في الجدول لاحقاً = تقديري المُسبَّب، يُحسم نهائياً بمراجعة prototype-by-prototype.

---

## 2) الوضع الحالي — ما الذي بُني فعلاً

### 2.1 ما هو حقيقي ومُنجَز (الإنجاز الفعلي — لا تقليل منه)

- **بنية KMP متعددة الموديولات:** `shared` · `core:{model,network,data,resources,designsystem,training-engine}` · `feature:{home,train,explore,reports,library,account,shell}` — قائمة وتُبنى.
- **Design System كمصدر حقيقة:** `MovitTheme` + tokens + عائلة `Movit*` مستخدمة فعلاً في كل شاشة (لا hex خام في `commonMain`). theme-boundary tests تحرسها.
- **i18n عربي/إنجليزي:** عبر `core:resources` + `movitText(...)` بكثافة (عشرات المفاتيح لكل شاشة).
- **UDF متّسق:** `State/Event/Effect/ViewModel` لكل feature.
- **طبقة بيانات Ktor:** `MovitMobileApi` + DTOs + `MovitData` + sync repositories — **موصولة فعلاً** بالشاشات (وليست مجرد عقد).
- **iOS يكمبّل:** كل الموديولات + CI أخضر.
- **اختبارات:** ViewModel/mapper/state خضراء عبر الموديولات.

> هذا الجزء **هو** ما تقيسه الأرقام العالية، وهو إنجاز معماري حقيقي. المشكلة ليست أنه «مزيّف»، بل أنه **يُقدَّم كأنه اكتمال المنتج**.

### 2.2 المحاور الثلاثة التي طلبتَها — أين كلٌّ منها فعلاً

| المحور المطلوب | الحالة الواقعية | الدليل |
|----------------|------------------|--------|
| **(أ) نقل UI الـ prototypes** | **هيكل: جيد · تفاصيل: جزئي.** الشاشات تستنسخ الأقسام لكن تُسقِط عناصر بصرية وتبسّط أخرى | scorecard يعترف Visual = 50–90% · عناصر مفقودة (أمثلة §4.5) |
| **(ب) نقل UX-Flow الـ legacy** | **مُختزَل.** كثير من تدفقات legacy الغنية غير منقولة | اعترافات «OPEN» في scorecard (catch-up · multi-workout day · level-up · region breakdown … §4.6) |
| **(ج) نقل الـ Logic القديم** | **الأضعف — قلب المنتج غير موصول.** | `core:training-engine` غير مستورد في أي feature · كل تدريب يرتدّ للـ legacy (§4.2) |

---

## 3) جدول «المُدّعى مقابل الواقعي»

> «النسبة الواقعية» = تقدير **مطابقة المنتج** (prototype + legacy + بيانات حقيقية)، بعد عزل وزن البنية التحتية. ليست حكماً على جودة الكود — الكود نظيف — بل على **«هل أُعيد إنتاج الصفحة فعلاً؟»**.

| الصفحة | المُدّعى | الواقعي (تقدير) | السبب الرئيسي للفجوة |
|--------|:-------:|:--------------:|----------------------|
| Home (08) | 92% | ~75% | الأقرب للمطابقة؛ بيانات حقيقية · لكن RTL/Dark QA لم يُنفَّذ فعلياً (مُعترف) |
| Train (01) | 86% | ~60% | الهيكل جيد لكن «Start» يرتدّ legacy · صور thumbnails placeholder |
| Explore (04) | 87% | ~65% | A11y=25% فعلياً · صور iOS placeholder · مطابقة بصرية جزئية |
| Reports (09) | 85% | ~60% | upsell/charts a11y placeholder · الاعتماد على dashboard API جزئي |
| Report Detail (17) | 92% | ~70% | Share/Export وهمي · joints من API غير متوفرة |
| Session (02) | 84% | ~50% | لا multi-workout day · **Start → legacy camera** · لا catch-up/skip-warmup |
| Prepare (03) | 80% | ~45% | صور hero placeholder (حرف أول) · **التدريب = legacy launcher** |
| Workout flow (16) | 66% | ~30% | **القلب: لا كاميرا/تنسيق · «previous form» وهمي · لا persist** |
| Library (05–06) | 78% | ~60% | صور iOS placeholder · A11y جزئي · tests 55% |
| Program detail (07) | 72% | ~45% | enrollment **محلي بلا API** · بيانات أسابيع من fixture · labels إنجليزية |
| Auth (10) | 86% (سابقاً 76%) | ~65% | Google OAuth stub · reset-password بلا شاشة · Splash legacy |
| Profile (11) | 86% | ~65% | لا edit/avatar · billing وهمي · training summary نص افتراضي |
| Onboarding (12) | 85% (سابقاً 74%) | ~70% | الأقرب · لكن خطوة Experience تخالف الـ prototype |
| Assessment (13) | 82% (سابقاً 75%) | ~45% | **Body Scan MVP حقيقي + عقود backend P1 · لا body map كامل · iOS camera placeholder** |
| Level (14) | 68% (سابقاً 58%) | ~45% | «Recommended programs» مفقود · timeline مبسّط · **fallback وهمي** |
| Program flow (15) | 77% | ~45% | **`FakeProgramFlowRepository` fallback** · صور preview · Share وهمي |

> لاحظ: نِسب الحساب/التقييم **رُفعت** بين [`Professional-Plan`](Android-KMP-Mobile-UI-UX-Professional-Plan.md) (Auth 76 · Onboarding 74 · Assessment 55 · Level 58) و[`Page-Scorecards`](Page-Scorecards.md) (85 · 85 · 75 · 68) قبل P1 دون أن يتغيّر جوهر التنفيذ (Body Scan كان وهمياً). هذا **تضخّم رقمي موثّق**، وP1 يغلق جزءاً منه بتنفيذ حقيقي لا بمجرد رفع رقم.

---

## 4) الاختلافات الجوهرية (تحليل الفجوة)

### 4.1 لماذا الأرقام مُضخّمة — آلية رياضية قابلة للإثبات

المنهجية ([`Page-Scorecards.md`](Page-Scorecards.md)) تُوزّع 100% على 7 محاور. **ثلاثة منها تقيس البنية التحتية لا المطابقة**، وتُمنح 88–100% لكل صفحة:

| المحور | الوزن | متوسط الدرجة عبر الصفحات | ماذا يقيس فعلاً |
|--------|:----:|:-----------------------:|------------------|
| Design System | 15% | **~93%** | استخدام `Movit*`/tokens — موجود دائماً |
| i18n / RTL | 15% | **~92%** | وجود مفاتيح ar/en — موجود دائماً |
| iOS readiness | 5% | **~100%** | `compileKotlinIos…` — يكمبّل دائماً |
| **المجموع** | **35%** | **~94%** | **أرضية +33 نقطة لكل صفحة مهما كانت** |

**مثال مُثبَت — Workout flow (16):**
`Functional 55%×0.25 + Visual 50%×0.20 + DS 93%×0.15 + i18n 90%×0.15 + A11y 35%×0.10 + Tests 60%×0.10 + iOS 100%×0.05 = 66%`

أي: صفحة **المحوران اللذان يراهما المستخدم فيها (Functional+Visual) متوسطهما ~52%**، تحصل على **عنوان 66%** لأن محاور البنية (35% وزناً) متوسطها ~96%. وحتى محورا «Functional/Visual» نفسهما كانا **سخيّين**: Assessment مُنح Functional 72% قبل وجود Body Scan حقيقي. بعد P1 صار المسار MVP فعلياً، لكن لا يصح اعتباره parity كاملاً بعد.

**الأثر الإداري:** المدير يقرأ «66%/85%» كـ «نسبة الإنجاز». فعلياً تعني «نسبة بنود rubric موزونة 35% نحو البنية التحتية». هذا هو لبّ سوء الفهم.

### 4.2 🔴 الأخطر: قلب المنتج (التدريب + التقييم) غير موصول

هذه أهمّ فجوة، وتُفسّر إحساسك بأن «النقل ضعيف جداً»:

- **محرّك التدريب الجديد `core:training-engine` موصول جزئياً بعد P0/P1**: `feature:library` يستهلكه لمسار سكوات مباشر، و`feature:account` يستهلك `PoseFrame`/`JointAngles` لتقييم Body Scan.
- **مسارات «Start» لم تعد كلها legacy، لكنها ليست مهاجرة كلها:** السكوات المدعوم ينتقل إلى `ExerciseLive`، بينما بقية التمارين/المداخل ما زالت ترتدّ إلى legacy.
- **`WorkoutRunScreen`** صار يملك POC تدريب مباشر لتمارين محددة فقط؛ لا يزال ينقصه تحميل config كامل، حفظ الجلسة، feedback صوتي، وتوسيع بقية التمارين.
- **`Assessment` → `BodyScanContent`** لم يعد placeholder فقط: `AssessmentCameraHost` يرسل إطارات pose إلى `AssessmentBodyScanEngine`، ثم يرفع النتيجة عبر `POST api/assessment` أو يعرض نتيجة محلية محسوبة عند فشل الشبكة. ما زال ناقصاً: parity كامل مع legacy 4k lines، body map بصري، وiOS live camera.

**الحجم الذي بقي في الـ legacy ولم يدخل التجربة الحية:**

| منطق legacy | الأسطر | الحالة في KMP |
|-------------|:------:|----------------|
| `training/engine/**` | **22,136** | جزء رقمي نُقل وموصول بمسار سكوات KMP واحد؛ بقية المنطق/المسارات لم تُنقل |
| `assessment/**` | **4,137** | P1 نقل MVP مشترك (`AssessmentBodyScanEngine`) لا parity كامل مع legacy |
| `ui/report(s)/**` | 8,790 | تقارير KMP مبسّطة |
| `ui/programs/**` | 4,248 | program detail/flow على بيانات fixture/fake |

**المعنى بعد P0/P1:** لم نعد عند 0% في القلب؛ أصبح لدينا إثبات حي لمسارين KMP. لكن هذا ليس انتهاء الهجرة: المنتج ما زال يعتمد على legacy في مسارات كثيرة، ويحتاج توسيع المحرك/الـ Assessment إلى parity أوسع قبل رفع النسبة كإنجاز منتج كامل.

### 4.3 بيانات وهمية / fallback تطغى خارج مسار الإنتاج الكامل

repositories الإنتاج (`Shared*Repository`) **تستدعي API حقيقي عبر `MovitData`** — لكنها **تسقط على `Fake*`/`Preview`** في حالات شائعة:

```kotlin
// SharedProgramFlowRepository.kt — السقوط على وهمي في 4 مسارات
private val fallback: ProgramFlowRepository = FakeProgramFlowRepository()
if (!MovitData.isInstalled) return fallback.loadPrograms()         // غير مثبّت
if (isPreviewProgram(programId)) return fallback.loadWeekPlan(...)  // معرّف preview
... else fallback.loadPrograms()                                   // API فارغ
```
```kotlin
// SharedLevelRepository.kt — السقوط على وهمي عند فشل API
is AppResult.Failure -> fallback.fetchLevelProfile()
```

**الأثر العملي على تجربتك:** إن جرّبتَ عبر **pilot الـ QA** (`MovitShellPilotActivity`) أو بدون تسجيل دخول/تثبيت `MovitData`، فكل شاشة تعرض **بيانات وهمية** لا تطابق لا الـ prototype ولا سلوك الـ legacy. هذا — على الأرجح — السبب المباشر لانطباع «كل الصفحات مختلفة بشكل ضخم». السقوط **صامت** (لا رسالة خطأ)، فيبدو كأنه التطبيق الحقيقي وهو ليس كذلك.

### 4.4 تغطية الـ Backend = 71% تقريباً (47 من 66)

من [`Backend-Contract-Matrix.md`](Backend-Contract-Matrix.md): **47/66** نقطة legacy مغطّاة في KMP بعد P1. المؤجَّل المرئي المتبقي يشمل:
- `GET plan/enrollment-check` · `GET workout-executions/stats` · `GET user-programs/{id}/overrides`.
- `POST mobile/prescription/recommend` و`POST mobile/reassessment/request`.
- `plan/pause|resume` phantom — موجودة في legacy interface بلا route backend فعلي.

### 4.5 المطابقة البصرية مع الـ prototypes — ناقصة (عناصر مفقودة فعلياً)

ليست مجرد «درجات أقل» — عناصر مُصمَّمة **غائبة**:
- **Level (14):** صف «Recommended programs» **غير مضاف** · الـ plan **timeline** بنقاط متصلة بُسِّط إلى بطاقات عادية · الـ floating glass header (back pill + tab pills) استُبدل بـ FilterChips.
- **Prepare (03) / Session (02):** صور hero/تمارين = **placeholder بحرف أول** بدل media شبكي.
- **Train (01):** thumbnails البرامج placeholder على iOS · الـ week strip وحالاته البصرية (missed/rest/today) أبسط من الـ prototype.
- **Report Detail / Reports:** الرسوم Canvas مبسّطة مقابل gradient charts في الـ prototype · لا قراءة قيم نقطة-بنقطة (a11y).
- **00-components:** scorecard يعترف بعدم اكتمال parity (macro card · coach card · difficulty dots · glass float-pill …).

### 4.6 تدفّقات UX من الـ legacy — مُختزَلة (اعترافات scorecard نفسها)

غير منقول: **multi-workout day cards** · **catch-up day dialog** · **skip-warmup** · **level-up celebration** · **region breakdown / limiting factors** · **قائمة كل أسابيع التقرير** · **drag/reorder** في customize · **Share/Export sheets** · **edit profile/avatar**. كلها سلوكيات legacy حقيقية يراها المستخدم اليوم وتغيب في الجديد.

---

## 5) المطلوب (تعريف «تمّ» الحقيقي لكل صفحة)

لا تُعتبر صفحة «منقولة» قبل تحقّق **الأربعة** معاً (وليس البنية وحدها):

1. **مطابقة بصرية موثّقة:** مقارنة جنباً-إلى-جنب مع ملف الـ prototype المقابل، بلقطات، والفروق إمّا مُغلقة أو مُسجّلة كدين صريح (لا «درجة Visual» مجرّدة).
2. **بيانات حقيقية على مسار الإنتاج:** **لا سقوط صامت على `Fake*`**. عند الفشل ⇒ `MovitErrorState`، لا بيانات وهمية. تُحصر `Fake*`/`Preview` في `@Preview` والاختبارات فقط.
3. **تدفّق legacy منقول:** جرد صريح لتدفّقات الصفحة في الـ legacy، وكل واحد إمّا منقول أو مُعلَّن مؤجَّلاً بقرار.
4. **منطق حقيقي يقود الشاشة:** خصوصاً Training/Assessment — الشاشة تُشغّل **`core:training-engine`/AssessmentEngine** فعلياً، **لا ترتدّ للـ legacy**.

**وعلى مستوى الإدارة:** scorecard يفصل عمودين — **«جاهزية البنية %»** و **«مطابقة المنتج %»** — ويُعرَض **الأدنى** كعنوان. لا رفع نسبة دون لقطة prototype + لقطة جهاز.

---

## 6) طريقة تحقيق المطلوب (خطة تنفيذية مُرتّبة بالأولوية)

### الأولوية 0 — الكَيستون: وصل محرّك التدريب (يكسر «الارتداد للـ legacy»)
- أنشئ **`feature` يستهلك `core:training-engine`** خلف حدود `expect/actual` للكاميرا/البوز:
  - Android: محوّلات `CameraFrameSource`/`PoseDetector` فوق CameraX + MediaPipe (المنطق موجود في legacy — يُغلَّف، لا يُعاد كتابته).
  - iOS: لاحقاً (نفس الحدود).
- **الهدف الأدنى القابل للإثبات:** تمرين واحد end-to-end داخل `WorkoutRunScreen` بعدّ تكرارات + تقييم form حيّ من المحرّك الجديد، **يحلّ محلّ `LaunchLegacyCameraTraining`**. هذا يحوّل القلب من 0% إلى «مُثبَت».

### الأولوية 1 — Body Scan / AssessmentEngine حقيقي
- انقل المنطق الرقمي من `assessment/engine` (4,137 سطر) إلى `commonMain` خلف نفس حدود الكاميرا.
- استبدل `BodyScanContent` placeholder بمسح فعلي + اربط `POST api/assessment` + `assessment-templates/resolve`.

### الأولوية 2 — إغلاق البيانات الوهمية الصامتة
- اجعل مسار الإنتاج **يفشل بصوت** (`MovitErrorState`) بدل عرض `Fake*`.
- احصر `FakeProgramFlowRepository`/`FakeLevelRepository`/`*PreviewData` في `commonTest` و`@Preview`.
- أصلح enrollment المحلي في `ProgramDetailViewModel` ليستخدم `plan/enroll` الحقيقي.

### الأولوية 3 — إكمال عقود الـ Backend المرئية
- القراءات المؤجَّلة المستهلَكة في UI: `training-profile GET` · `level-profile/history|levels` · `progression/*` · `programs/{id}/preview` · `plan/today`.
- كتابات **Outbox WS-2**: `exercise-preferences` · حذف `overrides` · `progression/mark-seen`.

### الأولوية 4 — تمريرة مطابقة بصرية صفحة-بصفحة
- لكل صفحة: افتح الـ prototype المقابل ونفّذ العناصر المفقودة (§4.5): Level Recommended+timeline · hero media حقيقي · gradient charts · glass headers · week-strip states …
- أضف **Compose screenshot tests** مقابل لقطات ذهبية حيث أمكن.

### الأولوية 5 — استكمال تدفّقات الـ legacy (§4.6)
- جرد + نقل: multi-workout day · catch-up · skip-warmup · level-up celebration · region breakdown · all-weeks report · drag-reorder · share/export · edit profile/avatar.

### الأولوية 6 — تصحيح المنهجية وإعادة الأساس بصدق
- أعد تصميم [`Page-Scorecards.md`](Page-Scorecards.md): عمود **«مطابقة المنتج»** (Functional + Visual + Legacy-flow + Real-data) منفصل عن **«جاهزية البنية»** (DS + i18n + iOS + Tests).
- أعد حساب كل الصفحات على الأساس الجديد (سيقترب من عمود «الواقعي» في §3).

---

## 7) إجابة مباشرة على سؤالك

> «هل النقل حصل بنسبة ضعيفة جداً، عكس ما هو موثّق؟»

**نعم وجزئياً لا — والاثنان صحيحان لأن كلاً منهما يقيس شيئاً مختلفاً:**

- مقابل **«إقامة بنية KMP + صدفة التصفّح/اللوحات»**: الإنجاز **عالٍ فعلاً**، وهذا ما تقيسه الأرقام (66–92%).
- مقابل **ما طلبتَه أنت** (إعادة إنتاج UI الـ prototype + نقل UX-Flow الـ legacy + نقل الـ Logic القديم): الإنجاز **أقل بكثير** من العنوان — خصوصاً لأن **قلب التدريب/التقييم غير مهاجَر** (يرتدّ للـ legacy)، والمطابقة البصرية وتدفّقات الـ legacy جزئية، والبيانات تسقط على وهمي بصمت.

الوثائق **ليست تزويراً**، لكنها عرضت «جاهزية البنية» بلغة «اكتمال المنتج». بمجرد فصل الرقمين (§6.6) وتنفيذ الأولوية 0–1 (وصل المحرّك)، يلتقي الموثّق بالواقع.

---

## 8) مراجع الكود (للتحقق المستقل)

- ارتداد التدريب للـ legacy (مسارات غير المدعومة في P0): [`MovitInnerHost.kt`](../../../android-poc/feature/shell/src/commonMain/kotlin/com/movit/feature/shell/MovitInnerHost.kt) · [`MovitAppShellEffect.kt`](../../../android-poc/feature/shell/src/commonMain/kotlin/com/movit/feature/shell/MovitAppShellEffect.kt)
- مسار KMP POC: [`ExerciseLiveScreen.kt`](../../../android-poc/feature/library/src/commonMain/kotlin/com/movit/feature/library/training/ExerciseLiveScreen.kt) · [`LiveExerciseRunner.kt`](../../../android-poc/core/training-engine/src/commonMain/kotlin/com/movit/core/training/session/LiveExerciseRunner.kt) · [`LegacyKmpTrainingSessionFactory.kt`](../../../android-poc/app/src/movitShellHost/java/com/movit/legacy/LegacyKmpTrainingSessionFactory.kt)
- تبعية `core:training-engine`: `feature:library/build.gradle.kts` + `app/build.gradle.kts`
- السقوط الوهمي (أُغلق في P2): [`SharedProgramFlowRepository.kt`](../../../android-poc/feature/library/src/commonMain/kotlin/com/movit/feature/library/SharedProgramFlowRepository.kt) · [`SharedLevelRepository.kt`](../../../android-poc/feature/account/src/commonMain/kotlin/com/movit/feature/account/SharedLevelRepository.kt)
- Body Scan P1: [`MovitAssessmentScreen.kt`](../../../android-poc/feature/account/src/commonMain/kotlin/com/movit/feature/account/MovitAssessmentScreen.kt) · [`AssessmentBodyScanEngine.kt`](../../../android-poc/feature/account/src/commonMain/kotlin/com/movit/feature/account/AssessmentBodyScanEngine.kt) · [`AssessmentCameraHost.kt`](../../../android-poc/feature/account/src/commonMain/kotlin/com/movit/feature/account/assessment/AssessmentCameraHost.kt)
- منطق legacy غير المنقول: `android-poc/app/src/main/java/com/trainingvalidator/poc/{training,assessment}/`
- منهجية الأوزان: [`Page-Scorecards.md`](Page-Scorecards.md) §«المنهجية»

---

## 9) سجل التنفيذ

### P0 — وصل محرك التدريب

**تاريخ:** 2026-06-10

#### ما نُفّذ

- `feature:library` يستهلك الآن `core:training-engine` (تبعية Gradle + مسار تدريب مباشر).
- طبقة مشتركة: `LiveExerciseRunner` + `ExerciseBlueprintRegistry` + `PoseFrameAssembler` — تربط `SessionOrchestrator` · `PhaseStateMachine` · `RepCounter` · `ScoreCalculator` على إطارات `PoseFrame`.
- شاشة `ExerciseLiveScreen` + مسار `MovitInnerRoute.ExerciseLive` — معاينة كاميرا + عدّ تكرارات + نسبة form حيّة.
- حدود Android: `KmpTrainingSessionBridge` + `LegacyKmpTrainingSessionFactory` (يلفّ `CameraManager` + `PoseLandmarkerHelper` + `LandmarkSmoother` من legacy)؛ يُسجَّل عبر `TrainingBoundaryInstall` في `MovitShellHost`.
- **مسار Start POC:** من `WorkoutRunScreen` (و`ExercisePrepare` للسكوات المدعوم) → `ExerciseLive` بدل `LaunchLegacyCameraTraining` عندما يكون الـ slug في القائمة (`bodyweight-squat` · `barbell-squat`).
- اختبار وحدة: `LiveExerciseRunnerTest` (دورة تكرار اصطناعية).
- البناء: `:feature:library` · `:feature:shell` · `:app:compileDebugKotlin` أخضر.

#### قرارات معمارية

- لم يُنشأ `feature:training` منفصل — التوسيع داخل `feature:library` لأن `WorkoutRun` موجودة هناك؛ يقلّل تبعيات Gradle.
- الكاميرا/ML على Android عبر **جسر تسجيل** من `app/movitShellHost` (لا تبعية عكسية من feature إلى app)؛ `expect` composable `TrainingCameraHost` في library.
- تكوين التمرين POC = `ExerciseBlueprint` ثابت في commonMain (ليس بعد parsing لـ `training-config` JSON).
- تقييم المفاصل مبسّط (نطاقات up/down) — ليس `JointEvaluator` الكامل من legacy.

#### متبقّي

- بقية التمارين + تحميل `ExerciseConfig` من API/assets بدل الـ blueprint الثابت.
- نقل `JointEvaluator` · `VisibilityMonitor` · feedback صوتي · حفظ الجلسة/outbox.
- مسارات Start الأخرى (Train tab · Session مباشرة) ما زالت ترسل legacy لغير السكوات.
- iOS: `TrainingCameraHost` placeholder فقط.
- parity بصري مع prototype تمرين الكاميرا (overlay هيكل عظمي · إعدادات).

---

### P1 — Body Scan / AssessmentEngine حقيقي

**تاريخ:** 2026-06-11

#### ما نُفّذ

- `feature:account` يستهلك الآن `core:training-engine` لاستخدام `PoseFrame`/`JointAngles` في تقييم Body Scan داخل `commonMain`.
- أُضيف `AssessmentBodyScanEngine`: يجمع عينات الحركة، يحسب mobility/control/symmetry/safety، وينتج `AssessmentResultsUi` + `BodyScanUploadRequestDto` قابل للإرسال للـ backend.
- `MovitAssessmentViewModel` صار يحمّل template حقيقي عبر repository، يدير حالة الكاميرا/اكتشاف pose/اكتمال المسح، ثم يرسل النتيجة عبر `submitBodyScan`.
- `BodyScanContent` لم يعد إطاراً ثابتاً فقط: `AssessmentCameraHost` يلتقط pose frames على Android عبر جسر legacy MediaPipe، وiOS يصرّح بفشل واضح بدل التظاهر بوجود كاميرا.
- عقود KMP أُضيفت: `resolveAssessmentTemplate` · `uploadAssessment` · `fetchLatestAssessment` · `fetchAssessmentProgress` مع DTOs في `AssessmentDto.kt` وربط في `AccountSyncRepository`.
- `SharedAssessmentRepository` أزال السقوط الصامت على `FakeAssessmentPreviewData`: عند فشل `MovitData`/API يرجع Failure، والـ ViewModel يعرض رسالة خطأ مع نتيجة محلية محسوبة عند توفر scan.
- اختبارات: `AssessmentBodyScanEngineTest` + تحديث `MovitAssessmentViewModelTest` لتغذية frames حقيقية اصطناعية بدل إكمال وهمي مباشر.
- البناء: `:core:network:testDebugUnitTest` · `:core:data:testDebugUnitTest` · `:feature:account:testDebugUnitTest` · `:feature:account:compileDebugKotlinAndroid` · `:feature:account:compileKotlinIosSimulatorArm64` · `:app:compileDebugKotlin` أخضر.

#### قرارات معمارية

- الكاميرا/MediaPipe بقيت خلف جسر Android من `app/movitShellHost`، بنفس نمط P0، لتجنب تبعية عكسية من feature إلى app.
- الـ template يأتي من backend عند الإمكان، ومعه fallback محلي محدود للحركات الآمنة فقط حتى لا يتوقف المسح بالكامل عند فشل الشبكة؛ هذا fallback تكوين لا fallback نتيجة وهمية.
- النتيجة المحلية تُستخدم كـ graceful degradation بعد scan مكتمل إذا فشل الرفع، لكنها ليست `Fake*` ثابتاً؛ مصدرها إطارات المستخدم.
- لم يُنقل legacy assessment 4,137 سطر بالكامل؛ P1 هو MVP عملي يثبت المسار الحي ويغلق الوهمي، لا parity نهائي.

#### متبقّي

- نقل/مطابقة تفاصيل legacy assessment الأعمق: قواعد thresholds الكاملة، body map، توصيات richer، وشرح المناطق الضعيفة بدقة أعلى.
- iOS live camera بدلاً من placeholder error.
- ربط `prescription/recommend` إن كان مطلوباً لمسار onboarding/assessment التالي.
- لقطات Visual QA مقابل prototype `13-assessment` بعد تشغيلها على جهاز/محاكي بكاميرا.

---

### P2 — إغلاق البيانات الوهمية

**تاريخ:** 2026-06-10

#### ما نُفّذ (ملفات + سلوك)

| المنطقة | الملفات | السلوك الجديد |
|---------|---------|----------------|
| Program flow | `SharedProgramFlowRepository.kt` | إزالة كل مسارات `FakeProgramFlowRepository`؛ `AppResult.Failure` عند عدم تثبيت `MovitData` أو غياب بيانات API/كاش |
| Level | `SharedLevelRepository.kt` | إزالة `fallback.fetchLevelProfile()`؛ فشل صريح عند عدم التثبيت أو فشل API |
| Repositories مشتركة | `SharedHomeRepository` · `SharedTrainRepository` · `SharedReportsRepository` · `SharedReportDetailRepository` · `SharedProfileRepository` · `SharedAuthRepository` · `SharedOnboardingRepository` · `SharedWorkoutSessionRepository` | نفس النمط: لا `Fake*`/`Preview` في مسار الإنتاج؛ `MovitErrorState` عبر `errorMessage` في الـ ViewModels |
| Program detail | `ProgramDetailViewModel.kt` · `ProgramDetailApiMapper.kt` · `ProgramDetailMapper.kt` | enrollment عبر `MovitData.plan.enrollProgram` (`POST plan/enroll`)؛ أسابيع الجدول من `ProgramExportDto` بدل `ProgramDetailPreviewData`؛ لا سقوط صامت على preview عند فشل التحميل |
| عزل الوهمي | نقل إلى `commonTest`: `FakeProgramFlowRepository` · `ProgramFlowPreviewData` · `FakeLevelRepository` · `FakeLevelPreviewData` · `ProgramDetailPreviewData` | الوهمي للاختبارات و`@Preview` فقط |
| اختبارات | `ProgramFlowStateTest` · `ProgramDetailViewModelTest` · `ProgramDetailMapperTest` · `MovitHomeStateTest` · `MovitTrainStateTest` · `MovitReportsStateTest` · `ReportDetailStateTest` · `MovitOnboardingViewModelTest` | حقن `Fake*` صريح في الاختبارات بدل الاعتماد على السقوط الصامت في الـ Shared repos |

#### قرارات مهمة

1. **الكاش الحقيقي ≠ وهمي:** عند فشل الشبكة، الـ repositories ما زالت تقبل `readCached*()` من `MovitData` لأنها بيانات مستخدم حقيقية مخزّنة محلياً — ليست `Fake*`.
2. **Enrollment:** `ProgramDetailViewModel` يحقن `enrollProgram` قابلاً للاختبار؛ الإنتاج يستدعي `PlanSyncRepository.enrollProgram` فقط — لا `markEnrolled` محلي بدون API.
3. **جلسة البدء:** بعد enrollment، مفتاح الجلسة يُشتق من `ProgramExportDto` + `home.trainMode`؛ إن لم تتوفر جلسة قادمة ⇒ رسالة خطأ بدل `plannedWorkoutId = "preview"`.
4. **Workout session swaps:** عند فشل API، `findSwapCandidates` يعيد قائمة فارغة (لا `SessionSwapPreviewData`) — سلوك آمن لا يُظهر بيانات وهمية.
5. **لم يُمس:** Outbox/mobile-sync/backend (P3) · training-engine/WorkoutRunScreen/MovitInnerHost (P0).

#### ما تبقى

- `FakeHomeRepository` / `FakeTrainRepository` / `FakeReportsRepository` / `FakeExploreRepository` ما زالت في `commonMain` (تُحقن يدوياً في الاختبارات فقط — الـ Shared repos لم تعد تسقط عليها).
- Assessment أُغلق في P1: لا سقوط إنتاجي على `FakeAssessmentPreviewData`؛ بقيت بيانات افتراضية محدودة كـ template محلي آمن وليس كنتيجة وهمية.
- `ExercisePrepareViewModel` ما زال يستخدم `ExercisePreparePreviewData` عند معرّفات preview (خارج نطاق program flow/level).
- نقل بقية `*PreviewData` من `commonMain` إلى `commonTest` + composables `@Preview` فقط (home/train/reports/explore).
- `GET plan/enrollment-check` (تأكيد استبدال البرنامج النشط) غير موصول بعد في KMP — enrollment مباشر بدون حوار confirm-replace كما في legacy.

---

### P3 — عقود Backend وOutbox WS-2

**تاريخ:** 2026-06-10

#### ما نُفّذ

- **قراءات KMP (`MovitMobileApi`):** `fetchTrainingProfile` · `fetchLevelProfileHistory` · `fetchLevelDefinitions` · `fetchProgressionHistory` · `fetchRecentProgression` · `fetchSessionProgression` · `fetchProgramPreview` · `fetchTodayPlan` — مع DTOs في `DeferredReadDto.kt`.
- **كتابات Outbox WS-2:** `EXERCISE_PREFERENCE_UPSERT` / `DELETE` · `USER_PROGRAM_OVERRIDE_DELETE` · `PROGRESSION_MARK_SEEN` — dispatcher + `MobileWriteSyncRepository` + enqueue في `OfflineWriteQueue`.
- **Backend:** alias `GET /mobile/progression/session/:sessionId` يوجّه لنفس منطق `planned-workout/:id` (توافق legacy Retrofit).
- **عقود:** `MobileApiContractRegistry` + [`Backend-Contract-Matrix.md`](Backend-Contract-Matrix.md) حُدّثا وقت P3 إلى **45/68** (كان 37)، ثم صارا بعد P1 **47/66** حسب registry الحالي.

#### قرارات

- **لا تغيير عقد payload:** DTOs تطابق حقول legacy/backend؛ `ProgramPreviewDto` يعكس `ProgramPreviewExport`.
- **progression/session:** المعرّف في legacy = planned-workout id؛ أُضيف alias في NestJS بدل تغيير مسار Retrofit.
- **استهلاك UI:** العقود جاهزة في `core:network`/`core:data`؛ ربط الشاشات (Level · Program detail · Profile) يبقى لمسارات P2/P4 — **لم يُلمس** `SharedProgramFlowRepository` / `SharedLevelRepository`.

#### متبقي

- `GET user-programs/{id}/overrides` (قائمة قراءة).
- prescription · enrollment-check · workout-executions/stats.
- `plan/pause|resume` phantom (لا route في NestJS).
- ربط repositories/features بالقراءات الجديدة (خارج نطاق P3 — عقود فقط).

---

### ملخص مرحلي — الجلسة 2026-06-10/11

| الأولوية | الحالة | الأثر على الفجوة (§3) |
|:--------:|:------:|------------------------|
| **P0** | POC مُثبَّت | قلب التدريب انتقل من **0%** إلى مسار KMP واحد (سكوات) — بقية المسارات legacy |
| **P1** | MVP مُثبَّت | Body Scan انتقل من placeholder وهمي إلى camera/pose/engine/upload داخل KMP — parity الكامل وiOS camera متبقيان |
| **P2** | مكتمل | السقوط الصامت على `Fake*` أُزيل من مسار الإنتاج؛ enrollment حقيقي عبر `plan/enroll` |
| **P3** | مكتمل | تغطية العقود **47/66** بعد P1/P3 — الاستهلاك في UI لم يُربط كله بعد |

**الاستنتاج:** أربع فجوات جوهرية من §4 بدأت تُغلق — (أ) محرّك التدريب موصول جزئياً، (ب) Body Scan صار مساراً حياً، (ج) البيانات الوهمية الصامتة أُزيلت من معظم الشاشات، (د) عقود backend موسّعة. ما زال مفتوحاً: **P4** (مطابقة بصرية) · **P5** (تدفقات legacy) · **P6** (فصل scorecard) · ربط قراءات P3 بالشاشات · توسيع P0/P1 إلى parity أوسع.

**الخطوة التالية المقترحة:** P4 Visual QA لصفحات القلب (`13-assessment` و`16-workout-flow`) ثم توسيع P0/P1 (بقية التمارين + persist + body map) بالتوازي مع ربط قراءات P3 في Level/Profile/Reports.
