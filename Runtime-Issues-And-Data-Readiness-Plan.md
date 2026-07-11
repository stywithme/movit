# مشكلات التشغيل وخطة «جاهزية البيانات» — Movit Mobile

**التاريخ:** 2026-07-11 · **الإصدار:** v1.1 (أُضيف تحليل لوج الباك اند §1.ب و§7)
**المصدر:** `run-log.md` (جهاز Android) + `Backend-log.md` (تيرمنال الباك) + تتبع كامل في الكود + فحص حي لقاعدة البيانات
**الهدف:** تشخيص كل مشكلة ظهرت في التشغيل بجذرها، ثم تصميم الوضع الجديد: **لا شاشة تُفتح بلا بيانات** — Splash تحميل أولي، مؤشر حالة المزامنة حول صورة البروفايل، وتحديث مع كل فتح.

---

## 1. ماذا حدث في جلسة التشغيل (قراءة اللوج)

الحقائق من اللوج قبل أي تفسير:

| الوقت | الحدث |
|---|---|
| 20:21:59 | **Crash كامل** (`StackOverflowError`) عند فتح تفاصيل برنامج — تكرار لانهائي بين `AndroidMovitPlatform.activeUserProgramId` و`PlanSyncRepository.readCachedActiveUserProgramId` |
| 20:22:09 | إعادة تشغيل التطبيق (process جديد 17467) |
| 20:22:11 | Home يُعرض من الكاش (`[MovitFirstFrame] screen=home state=Cached 380ms`) |
| 20:22:13→20:23:34 | تنقل بين Explore / workout session / prepare — **كل الشاشات `state=Cached`** |
| 20:23:09 | دخول شاشة تجهيز تمرين Bicep Curl: `[TrainingConfigCache] enter_prepare status=MISSING … inIndex=false onDisk=false` |
| 20:23:11→20:23:25 | المستخدم يضغط Start **8 مرات** — كلها `start_pressed → start_blocked reason=no_cached_config` |
| طوال الجلسة | **صفر طلبات HTTP لسيرفر التطبيق** (الطلب الوحيد في اللوج: Firebase logging). لا سطر واحد من sync أو outbox أو أي fetch |

**الخلاصة التشخيصية (المُحدَّثة بعد لوج الباك):** في نافذة `run-log.md` لم يصل أي طلب للسيرفر. لكن لوج الباك كشف الصورة الأكمل — في جلسة أسبق بنفس اليوم **السيرفر كان يعمل والهاتف وصله فعلًا**، والنتيجة كانت أسوأ من الانقطاع: المزامنة نفسها مكسورة على السيرفر. التطبيق تعامل مع الحالتين **بصمت كامل** — وهذا ما جعل كل الأعراض تبدو «التطبيق بايظ».

### 1.ب — ماذا كشف لوج الباك اند (`Backend-log.md`)

إحصاء كل الطلبات في اللوج:

| الطلب | النتيجة | العدد |
|---|---|---|
| `GET /api/mobile/sync?includeReports=summary` | **500 FAIL — كل مرة** | 15 |
| `GET /api/mobile/workout-templates/triple_alternating/training-config` | **404 FAIL — كل مرة** (طلب بالـ slug) | 23 |
| `GET /api/mobile/workout-templates/800d3683-…/training-config` | 200 OK (نفس الـ endpoint بالـ UUID) | 3 |
| `POST /auth/login`, `/training-profile`, `/plan`, `/home`, `/explore` (+دلتا) | 200/201 OK | 8 |
| `GET /api/mobile/home` (ETag) | **304 مسجلة "FAIL"** — تعمل صح لكن اللوجر يصنفها فشلًا | 2 |
| سطر متكرر مع كل sync | `messageLibrary: total=2662, **withAudio=0**` | — |

**التشخيص الحاسم لكل بند:**

1. **سبب الـ 500 (مؤكد بفحص حي):** `PrismaClientKnownRequestError P2022 — The column does not exist` على `plannedWorkoutReport.findMany`. نفّذت `npx prisma migrate status` على قاعدة `pose_db`:
   ```
   Following migration have not yet been applied:
   20260709220000_planned_workout_report_idempotency
   ```
   أي أن **migration عمود `idempotencyKey` (إصلاح P1.3) لم تُطبَّق على القاعدة**، بينما Prisma client مُولَّد من الـ schema الجديدة ويطلب العمود في كل SELECT → كل نداء `/mobile/sync` ينفجر 500. (كانت مُدرجة صراحة في تقرير التنفيذ كـ«متبقٍ تشغيلي: `prisma migrate deploy`» — ولم تُنفَّذ.)
2. **نتيجتها على الموبايل تفسر M2 بدقة أكبر:** `/mobile/explore` يعمل (لذلك كتالوج Explore ظاهر) لكن **كل ما يأتي حصريًا من `/mobile/sync` غائب**: exercise configs، مكتبة الرسائل، exports البرامج والقوالب، الـ enrollments — فـ Bicep Curl ظاهر في الكتالوج وconfig تشغيله غير موجود.
3. **الـ 404 × 23:** الموبايل يطلب template config بالـ **slug** (`triple_alternating`) وendpoint الباك يحلّ بالـ **id فقط** — ففشل مسار ensure البديل أيضًا. (نفس الطلب بالـ UUID نجح 3 مرات.)
4. **العدّ المرتفع للتكرارات** (23×404 + 15×500 خلال ~دقيقة ونصف): لا يوجد debounce ولا negative-cache في مسار ensure على الموبايل، و`forceCheck=true` يتجاوز throttle المزامنة في كل محاولة — قصف للسيرفر والبطارية بلا فائدة.
5. **`withAudio=0` من 2662 رسالة:** قاعدة التطوير لا تحتوي أي ملفات صوت مولّدة — حتى بعد إصلاح الـ sync، الـ prefetch الصوتي سيجد صفر ملفات والتدريب سيكون صامتًا حتى توليد TTS.
6. **اللوجر يعتبر 304 فشلًا:** مسار ETag للـ Home يعمل بشكل صحيح، والتصنيف «FAIL» مضلل في المراقبة.

---

## 2. المشكلات — الجذر والحل لكل واحدة

### M1 — 💥 فتح تفاصيل البرنامج = Crash مضمون (StackOverflow)

**العرض:** «لما جيت أدخل على program عشان أشترك فيه — فصل تمامًا».

**الجذر (مؤكد من الـ stack والكود):** تكرار لانهائي متبادل:

```
AndroidMovitPlatform.activeUserProgramId()        ← AndroidMovitPlatform.kt:74
  └→ MovitData.plan.readCachedActiveUserProgramId()
       └→ enrollments.resolveActiveUserProgramId()   ← فارغ (لا يوجد اشتراك مزامَن)
       └→ platform().activeUserProgramId()           ← PlanSyncRepository.kt:17 يرجع لنفس الدالة ∞
```

نفس النمط موجود في iOS ([IosMovitPlatform.kt:55-60](kmp-app/core/data/src/iosMain/kotlin/com/movit/core/data/platform/IosMovitPlatform.kt)). يحدث لأي مستخدم **مخزن الاشتراكات عنده فارغ** — أي كل مستخدم جديد يفتح أي برنامج قبل أول مزامنة ناجحة. أُدخل هذا أثناء توصيل P2.x عندما جُعل الـ platform binding يفوّض للـ repository الذي كان أصلًا يفوّض له.

**الحل:** كسر الحلقة — الـ platform binding يقرأ **مفتاحه الخام فقط**:
```kotlin
// AndroidMovitPlatform + IosMovitPlatform
override fun activeUserProgramId(): String? =
    readCache(PROGRAM_STORE, ACTIVE_USER_PROGRAM_ID)?.takeIf { it.isNotBlank() }
```
و`PlanSyncRepository.readCachedActiveUserProgramId` يبقى نقطة التركيب الوحيدة (enrollments أولًا ثم الخام). + اختبار unit يستدعي `readCachedActiveUserProgramId` بمخزن فارغ (كان سيكشف الحلقة فورًا) واختبار UI لفتح ProgramDetail بلا اشتراك.

---

### M2 — 🚫 زر Start لا يعمل (8 ضغطات محجوبة)

**العرض:** «لما جيت أشغل تمرين معين بدوس start مش بيبدأ».

**الجذر (أربع طبقات — اكتملت بلوج الباك):**
1. config التمرين `bicep_curl` غير موجود محليًا: **البذرة الباردة تحتوي 10 تمارين فقط وليس منها bicep_curl** (تحققت من `cold_offline_bundle.json`) — بينما كتالوج Explore المعروض يحتويه (لأن `/mobile/explore` يعمل)، فالمستخدم يرى تمرينًا لا يستطيع الجهاز تشغيله.
2. **المصدر الوحيد للـ configs (`/mobile/sync`) يرجع 500 دائمًا** بسبب migration غير مطبّقة (§1.ب-1) — فلا ensure ولا full refresh كان يمكن أن ينجح أصلًا.
3. **المسار البديل (template training-config) يرجع 404** لأن الموبايل يمرر slug والباك يحلّ بالـ id فقط (§1.ب-3).
4. **الضغطات المتكررة لا تعيد المحاولة**: `requestTrainingStart` لا يستدعي ensure مطلقًا — يفحص الكاش ويحجب فقط. المستخدم يكرر الضغط بلا أي شيء يحدث (وعندما كان الـ preflight يعيد المحاولة، كان يقصف السيرفر بلا debounce — §1.ب-4).

**ثغرة إضافية اكتشفتها بالتتبع:** لتمرين مستقل (بدون workout template)، `ensure` يعتمد على **دلتا sync فقط** — لو التمرين موجود على السيرفر لكن الدلتا لا تحمله (لم يتغيّر منذ الـ watermark) وconfigs أخرى موجودة (فلا يحدث backfill-full)، يظل Start محجوبًا **حتى مع اتصال سليم**. لا يوجد endpoint لجلب config تمرين واحد في عقد الموبايل.

**الحل:**
1. **زر Start عند الحجب يتحول لفعل**: «تنزيل إعداد التمرين» → يستدعي `ensure` (مع مؤشر تقدم) بدل ضغطات ميتة.
2. **endpoint خفيف** `GET /api/mobile/exercises/:slug/training-config` (الباك عنده الـ builder جاهز — `buildExerciseConfig`) ليعمل ensure للتمرين المستقل مباشرة، ويُضاف كخطوة ثالثة في `TrainingConfigEnsure` قبل الاستسلام.
3. الحل الجذري هو **M6 (الجاهزية الشاملة)**: بعد Splash التحميل الأولي لا يوجد أصلًا config مفقود.

---

### M3 — 📝 «رسالة طويلة غير مفهومة بجانب الزرار» + زر Sync الموعود غير موجود

**العرض:** رسالة إنجليزية طويلة بجانب زر تشغيل الـ workout عن تحميل البيانات أول مرة.

**الجذر:** النص `training_config_first_use_online`: *"First use needs a one-time connection to download exercise setup. **Tap Sync now**, then try again."* — يُعرض كنص صغير عادي في [ExercisePrepareScreen.kt:146-152](kmp-app/feature/library/src/commonMain/kotlin/com/movit/feature/library/ExercisePrepareScreen.kt) بينما **زر "Sync now" (`training_config_sync_now`) غير موصول في أي شاشة** (grep كامل = صفر استخدام). أي أن الرسالة تحيل المستخدم لزر غير موجود، وبالإنجليزية رغم أن لغة الجهاز كانت ar/en (اللوكال المُفعّل en — تُعرض الإنجليزية).

**الحل:**
1. استبدال النص+الزر الميت بمكوّن حالة واحد واضح: أيقونة + سطر قصير مترجم («إعداد التمرين يحتاج اتصالًا لمرة واحدة») + **زر فعل واحد** «تنزيل الآن» يستدعي ensure ويعرض التقدم، ويتحول Start تلقائيًا عند النجاح.
2. حذف strings الميتة أو توصيلها فعليًا.
3. مع M6، هذه الحالة تصبح استثناءً نادرًا وليست أول تجربة للمستخدم.

---

### M4 — ⚙️ أزرار Sync في البروفايل «مش شغالة»

**العرض:** «لقيت اختيارات لـ sync Data مش شغالة تمامًا».

**الجذر (من [MovitProfileViewModel.kt:150-195](kmp-app/feature/account/src/commonMain/kotlin/com/movit/feature/account/MovitProfileViewModel.kt)):**
1. **لا يوجد زر «مزامنة كل البيانات الآن» أصلًا** — القسم يعرض قائمة outbox + «إعادة محاولة الفاشل» + «إصلاح الكتالوج» فقط. مع outbox فارغ و سيرفر مطفي: القائمة فارغة والأزرار تبدو بلا أثر.
2. `repairCatalog` يستدعي `repairExploreCatalog()` الذي يجلب **ملخصات Explore فقط** — لا يجلب exercise configs ولا programs exports، أي أنه لا يصلح فعليًا مشكلة M2 حتى مع شبكة سليمة. تسمية «إصلاح الكتالوج» مضللة.
3. رسائل الحالة **أكواد خام**: `"retry:0"`, `"repair:ok"`, أو نص خطأ إنجليزي خام — تظهر كما هي للمستخدم.
4. مع فشل الشبكة، النتيجة صمت أو كود غريب — فيبدو القسم «مش شغال».

**الحل:**
1. زر رئيسي **«مزامنة الكل الآن»** = `MovitData.sync.fullRefresh()` (أو `syncIfNeeded(forceCheck=true)` + زر full منفصل) مع progress + نتيجة مترجمة واضحة (نجح: «آخر مزامنة الآن ✓» / فشل: سبب مفهوم «تعذر الوصول للخادم»).
2. «إصلاح الكتالوج» يستدعي **full refresh الكامل** (كتالوج + configs + رسائل + أصوات manifest) وليس explore summaries.
3. استبدال أكواد الحالة الخام بنصوص مترجمة (`profile_sync_retry_done` بعدد، `profile_sync_repair_done`، إلخ) + عرض «آخر مزامنة ناجحة: منذ x دقيقة» من `MovitSyncTelemetry` (موجودة أصلًا وغير معروضة).

---

### M5 — 🔇 فشل الاتصال صامت تمامًا (الجذر المشترك لكل ما سبق)

**العرض غير المباشر:** كل الأعراض أعلاه بدت «التطبيق بايظ» لأن لا شيء أخبر المستخدم أن السيرفر غير متاح.

**الجذر:** فلسفة `SyncOutcome.Offline` الحالية صحيحة للأوفلاين الحقيقي لكنها **بلا أي سطح UI عام**: الـ shell يستقبل النتيجة ويتجاهل غير `Success`. حتى `SyncOutcome.Error` (تصنيف P0.3 الجديد) لا يُعرض. زد عليها: الجهاز كان **متصلًا بالإنترنت** (WiFi validated في اللوج) لكن السيرفر نفسه غير متاح — حالة «متصل لكن الخادم لا يستجيب» تحتاج تمييزًا خاصًا في العرض.

**الحل:** هو بالضبط اقتراحك — **مؤشر حالة المزامنة حول صورة البروفايل في الهيدر** (التفصيل في §4.2) + Splash الجاهزية (§4.1). بهذا يصبح لكل فشل أثر مرئي فوري ومفهوم. **إضافة من لوج الباك:** المؤشر يجب أن يميز ثلاث حالات وليس اثنتين — لا شبكة (رمادي)، الخادم لا يُوصل إليه (أحمر)، **الخادم يرد بأخطاء 5xx** (أحمر + رسالة «الخادم يواجه مشكلة» في ورقة الحالة) — لأن حالة هذه الجلسة كانت الثالثة.

---

### B — أعطال الباك اند والربط (من `Backend-log.md` — يجب إصلاحها قبل أي إعادة تشغيل)

#### B1 — 🔴 `/mobile/sync` = 500 دائم: migration غير مطبّقة + هشاشة تصميمية

**الجذر:** migration `20260709220000_planned_workout_report_idempotency` غير مطبّقة على `pose_db` (مؤكد بـ `prisma migrate status`) بينما Prisma client مولّد من الـ schema الجديدة → P2022 على كل `plannedWorkoutReport.findMany` → **الـ endpoint المحوري للتطبيق كله معطل بالكامل**.

**الإصلاح (ثلاث مستويات):**
1. **فوري (تشغيلي):** `cd backend && npx prisma migrate deploy` ثم إعادة تشغيل السيرفر. (كلتا الـ migrations: nullable-metrics مطبقة، idempotency هي المعلقة.)
2. **حارس إقلاع (كود):** عند بدء السيرفر، فحص المهاجرات المعلقة (`prisma migrate status` برمجيًا أو استعلام `_prisma_migrations`) — إن وُجدت معلقة: log صارخ + إنهاء العملية في dev (fail-fast) بدل تقديم 500 صامتة لكل العملاء. يُضاف كسطر في `main.ts`/bootstrap وسكربت `npm run start` عبر `prisma migrate deploy &&`.
3. **مرونة الـ endpoint (كود — درس أعمق):** فشل شريحة المستخدم يجب ألا يقتل الكتالوج كله. لفّ استعلامات user-slices (`plannedWorkoutReports`, `userPrograms`, `userExercisePreferences`) في try/catch داخل `mobile-sync.service.sync()`: عند فشلها أرجع الحمولة **بدون** الشريحة + `meta.userSlicesDegraded=true` + log خطأ واضح — الكتالوج وconfigs التمارين يصلون للموبايل مهما حدث. الموبايل عند رؤية العلم يعرض حالة 🔴 جزئية ولا يفشل الدورة.

#### B2 — 🔴 `training-config` بالـ slug = 404: عقد غير متطابق

**الجذر:** `GET /mobile/workout-templates/:id/training-config` يحلّ بالـ UUID فقط؛ الموبايل يمرر أحيانًا slug (`triple_alternating`) — 23 فشلًا في اللوج.
**الإصلاح:** في service الباك: `findUnique({ id })` ثم fallback `findUnique({ slug })` (نفس نمط `saveWorkoutExecution` مع التمارين) + contract test `training-config-by-slug.spec.ts`. (اختياريًا على الموبايل: تمرير UUID عند توفره — لكن إصلاح الباك هو الضامن.)

#### B3 — 🟠 قصف السيرفر: لا debounce ولا negative-cache في ensure

23×404 + 15×500 خلال ~90 ثانية من جهاز واحد. **الإصلاح (موبايل):**
1. **Single-flight لكل slug/template** في `TrainingConfigEnsure` (Mutex per key) — الضغطات المتوازية تنتظر نفس المحاولة.
2. **Negative-cache قصير:** فشل 404/5xx لنفس المفتاح لا يُعاد قبل 60 ثانية.
3. **Cooldown للدورة بعد 5xx:** فشل `Error(Http)` من `/mobile/sync` يمنع محاولات `forceCheck` جديدة لمدة 30–60 ثانية (باستثناء الضغط اليدوي على «مزامنة الآن»).

#### B4 — 🟡 توليد الصوت: `withAudio=0` من 2662 رسالة

قاعدة التطوير بلا أي TTS — الـ prefetch الصوتي (R7) سيجد صفر ملفات والتدريب صامت حتى بعد إصلاح كل شيء. **الإصلاح (تشغيلي):** توليد الصوت عبر أداة الأدمن `POST /messages/bulk-audio` (موجودة) للغتين، ثم التحقق من السطر في اللوج (`withAudio` > 0). يُضاف كخطوة في checklist بيئة التطوير.

#### B5 — 🟢 اللوجر يصنف 304 كـ FAIL

مسار ETag يعمل؛ التصنيف فقط مضلل. **الإصلاح:** middleware اللوج يعتبر 2xx/3xx نجاحًا (`304 NOT_MODIFIED`)، والفشل من 400 فأعلى.

---

## 3. الوضع الحالي: كيف يُتعامل مع كل نوع بيانات (كما هو مبني الآن)

> هذا هو «العقد الحالي» بعد إصلاحات المراحل P0–P3 — سليم في بنيته، ومشكلته الوحيدة الكبرى: **لا توجد بوابة جاهزية ولا سطح حالة**.

| نوع البيانات | مصدره | متى يُجلب | أين يُخزن | الفجوة الظاهرة في التشغيل |
|---|---|---|---|---|
| **كتالوج Explore** (ملخصات تمارين/قوالب/برامج) | `/mobile/sync` + `/mobile/explore` دلتا | فتح/resume (throttle 5د)، pull، login | بلوب `explore_data_json` | يُعرض من البذرة حتى لو السيرفر ميت — المستخدم يرى محتوى «متاح» وهو غير قابل للتشغيل |
| **Exercise configs** (قلب التدريب) | `/mobile/sync` (دلتا/full) | مع دورة الـ sync فقط + ensure عند فتح تمرين | `exercise_config_cache` لكل slug | البذرة = 10 فقط؛ لا endpoint لتمرين مفرد؛ Start يُحجب بلا فعل |
| **Workout templates + Programs exports** | `/mobile/sync` | مع الدورة | `workout_template_cache` / `program_cache` + فهارس | سليم — لكن يعتمد على نجاح الدورة |
| **رسائل التدريب + الأصوات (manifest)** | `/mobile/sync` (بوابة fingerprint) | مع الدورة + prefetch بعد full | `message_library_cache` + `audio_cache/` على القرص | prefetch الأصوات لا يبدأ إلا بعد full ناجح |
| **بيانات المستخدم** (enrollments، تفضيلات، تقارير) | `/mobile/sync` (دلتا) + `/mobile/user-programs` | مع الدورة، enroll | مخازن مخصصة | فارغة على جهاز لم يزامن → كانت سبب crash الحلقة |
| **Home dashboard** | `/mobile/home` + ETag | مع الدورة + فتح التبويب (SWR) | بلوب `home_data_json` | يُعرض قديمًا بلا مؤشر قِدم |
| **Effective plans** (أيام البرنامج) | `/mobile/user-programs/:id/effective-plan` | عند فتح اليوم/الجلسة + week pack | `session_cache` | يعتمد على الطلب وقت الفتح |
| **كتابات المستخدم** (تدريبات، إكمالات، تخصيصات) | outbox → replay | فوري عند اتصال + محفزات | `outbox_entry` (متين ✓) | سليم — لا فقدان |
| **الصور** | Coil عن URL مباشرة | وقت العرض | كاش Coil | لا prefetch منظم |

**تدفق الفتح الحالي:** login/فتح → tabs فورًا (كاش أو بذرة) → sync في الخلفية (صامت النتيجة) → الشاشات تتحدث عند `cacheInvalidated`. **لا يوجد**: بوابة «البيانات الأساسية جاهزة»، ولا مؤشر حالة عام، ولا تمييز «متصل لكن الخادم غير متاح».

---

## 4. الوضع الجديد المقترح: «جاهزية البيانات» Data Readiness

### 4.1 Splash تحميل أولي بعد الدخول (Bootstrap Gate)

**القاعدة:** المستخدم لا يرى الـ tabs إلا وكل **بيانات النواة** موجودة محليًا. النواة = كل شيء **عدا** ملفات الصوت والصور.

**تعريف الجاهزية (مكوّن جديد `DataReadinessGate` في `core/data`):**

```
CoreReady = exercise configs (كل slugs الفهرس) ✓
          + explore catalog ✓
          + workout/program exports ✓
          + message library + system messages ✓
          + home ✓ + enrollments/preferences/reports (للمسجل) ✓
          + audio manifest (الـ metadata فقط) ✓
```

**تدفق الفتح الجديد:**

```
فتح التطبيق / login
 ├─ جلسة نشطة + CoreReady=true  → tabs فورًا + دلتا sync بالخلفية (fresh-on-open)
 ├─ جلسة نشطة + CoreReady=false → SplashLoading:
 │    fullRefresh() مع تقدم مرحلي («تحميل التمارين… البرامج… الرسائل…»)
 │    ├─ نجح → tabs + بدء prefetch الصوت/الصور بالخلفية
 │    └─ فشل → شاشة الخطأ الصريحة: «تعذر الوصول للخادم»
 │         [إعادة المحاولة] [متابعة بما هو متاح*] — *تظهر فقط لو كاش جزئي موجود،
 │         ومع دخول وضع Degraded المعلن (مؤشر أحمر بالهيدر + حجب شاشات التدريب الناقصة بفعل «تنزيل»)
 └─ ضيف → tabs من البذرة + شارة «وضع محدود» (البذرة تُوسَّع لتشمل كل التمارين المنشورة — سكربت CI موجود أصلًا)
```

- الـ Splash يظهر **فقط** عند النقص (أول تثبيت، بعد logout/expiry، بعد full-clear) — الفتح اليومي المعتاد يظل فوريًا.
- timeout للـ Splash (20–30ث) → شاشة الخطأ؛ لا انتظار أبدي.
- **مع كل فتح للتطبيق:** `syncIfNeeded(forceCheck=true)` عند cold start (يتجاوز throttle الخمس دقائق مرة واحدة عند الفتح، ويحترمه في resume المتكرر) — يحقق «طلب بيانات حديثة مع كل فتح» بلا عواصف.

**التنفيذ:** `DataReadinessGate.evaluate(): Ready | Missing(parts)` يقرأ الفهارس الموجودة (`allCachedSlugs`, indexes, blobs) — قراءات خفيفة؛ الـ shell يعرض `MovitInnerRoute.BootstrapSplash` بدل الـ tabs حتى `Ready`؛ تقدم المراحل من orchestrator hooks الموجودة.

### 4.2 مؤشر حالة المزامنة حول صورة البروفايل (كما طلبت بالضبط)

مكوّن جديد `MovitSyncStatusAvatar` في `core/designsystem` يلف صورة البروفايل في `MovitAppHeader` بحلقة حالة:

| الحالة | الشكل | متى |
|---|---|---|
| 🟢 **Synced** | حلقة خضراء ثابتة (تتلاشى لهدوء بعد ثوانٍ) | آخر دورة Success + لا pending outbox |
| 🟡 **Syncing** | قوس أصفر يدور حول الصورة (sweep animation) | دورة sync جارية أو outbox replay جارٍ أو prefetch نشط |
| 🔴 **Problem** | حلقة حمراء + نقطة تنبيه | آخر دورة Error/Offline-مع-إنترنت، أو `failedCount>0`، أو Degraded |
| ⚪ Offline هادئ | حلقة رمادية | لا إنترنت أصلًا (وضع الجيم الطبيعي — ليس خطأ) |

- **المصدر:** `SyncStatusBus` جديد صغير في `core/data`: `StateFlow<SyncUiStatus>` يتغذى من orchestrator (بداية/نهاية الدورة + النتيجة — الhooks موجودة من P0.3)، `OfflineWriteQueue` (pending/failed/in-flight)، `AudioPrefetchRunner`، و`isNetworkAvailable`. التمييز المهم: **Offline (لا شبكة) ≠ Problem (شبكة موجودة والخادم لا يرد)** — هذا كان لبّ غموض هذه الجلسة.
- الضغط على الصورة يظل يفتح البروفايل؛ **الضغط المطوّل** (أو نقرة على النقطة الحمراء) يفتح ورقة حالة مختصرة: آخر مزامنة، عناصر معلقة/فاشلة، زر «مزامنة الآن».
- يُطبق في الهيدر الموحد فيظهر تلقائيًا في كل الشاشات الأساسية (Home/Train/Explore/Reports).

### 4.3 الصوت والصور بالخلفية + التحميل عند الفتح

- بعد الـ Splash: `AudioPrefetchRunner` يعمل فورًا (اليوم الحالي + التمارين الظاهرة أولًا، ثم الباقي)، وprefetch للصور (Coil) لصور كتالوج Explore وبرنامج المستخدم النشط — كلاهما يرفع حالة 🟡 أثناء العمل.
- **عند فتح عنصر ناقص** (صوت تمرين غير منزّل / صورة): تحميل فوري متوازٍ مع العرض — الشاشة تفتح والتشغيل يعمل، والصوت يُفعَّل لحظة اكتماله (fallback TTS/صمت مرحلي كما هو اليوم). القاعدة: **النقص في الوسائط لا يحجب أبدًا؛ النقص في الـ config يحجب بفعل تنزيل واضح** (M2/M3).

### 4.4 قواعد «لا شاشة بلا بيانات»

| الشاشة | البوابة |
|---|---|
| Program Detail | يفتح من الكاش دائمًا (export موجود بعد الـ Splash) — وM1 مُصلح |
| Workout session | كل الـ configs موجودة (Splash) — لو نقص طارئ: زر «تنزيل» بدل رسالة ميتة |
| Exercise prepare/Start | نفس القاعدة — Start إما يعمل أو زر تنزيل بتقدم |
| Reports | كاش فوري + تحديث خلفي (كما اليوم) + «آخر تحديث: …» |
| أي قائمة فارغة بسبب فشل جلب | Empty-state صريح بالسبب + زر إعادة محاولة — لا شاشات بيضاء صامتة |

---

## 5. خطة التنفيذ (بالترتيب)

**الترتيب المُحدَّث بعد لوج الباك — B0 قبل كل شيء:**

| # | البند | الملفات الرئيسية | جهد |
|---|---|---|---|
| **B0** 🔴 الآن | `npx prisma migrate deploy` على `pose_db` + إعادة تشغيل الباك + توليد TTS (B4) — **بدونها كل ما بعدها بلا معنى** | تشغيلي | دقائق |
| **R0-ب** 🔴 فوري | B1-2 حارس المهاجرات عند الإقلاع + B1-3 مرونة user-slices (الكتالوج لا يموت بفشل شريحة) + B2 حلّ slug-or-id في training-config + B5 تصنيف 304 | `main.ts`/bootstrap, `mobile-sync.service.ts`, `workout-templates.service.ts`, logger middleware | M |
| **R1** 🔴 فوري | كسر حلقة `activeUserProgramId` (Android+iOS) + اختباران regression | `AndroidMovitPlatform.kt`, `IosMovitPlatform.kt`, `PlanSyncRepositoryTest` | S |
| **R2** 🔴 فوري | زر «مزامنة الكل الآن» في البروفايل = full refresh حقيقي؛ «إصلاح الكتالوج» يصير full أيضًا؛ رسائل مترجمة بدل الأكواد | `MovitProfileViewModel/Screen`, strings | S–M |
| **R3** 🔴 فوري | بوابة Start تفاعلية: زر «تنزيل إعداد التمرين» + حذف نص Sync-now الميت + **B3: single-flight + negative-cache 60ث + cooldown بعد 5xx** | `ExercisePrepareViewModel/Screen`, `TrainingConfigEnsure.kt`, strings | M |
| **R4** | endpoint `GET /mobile/exercises/:slug/training-config` + خطوة ثالثة في ensure | باك (builder جاهز) + `TrainingConfigEnsure.kt` + contract test | M |
| **R5** | `SyncStatusBus` + `MovitSyncStatusAvatar` (الحلقة الملونة المتحركة) في الهيدر + ورقة الحالة — **بثلاث حالات خطأ: لا شبكة / لا وصول للخادم / الخادم يرد 5xx** | `core/data` جديد, `MovitAppHeader.kt`, shell | M |
| **R6** | `DataReadinessGate` + شاشة `BootstrapSplash` بتقدم مرحلي + شاشة فشل صريحة + fresh-on-open (تجاوز throttle عند الفتح البارد) | `core/data` جديد, shell routes, orchestrator hook | M–L |
| **R7** | Prefetch خلفي للصوت (فوري بعد Splash) والصور (كتالوج + برنامج نشط) + التحميل المتوازي عند فتح ناقص | `AudioPrefetchRunner`, Image prefetch port, session hooks | M |
| **R8** | توسيع البذرة الباردة لكل التمارين المنشورة (سكربت CI) + empty-states موحدة بالسبب | `scripts/cold-offline-bundle`, screens | S–M |

**اختبار القبول (يعيد سيناريوهات الجلستين حرفيًا):**
1. **سيناريو لوج الباك:** بعد B0/R0-ب — فتح التطبيق → `/mobile/sync` يرجع 200 (وليس 500)، الـ configs تصل، وBicep Curl يبدأ من أول ضغطة؛ طلب template بالـ slug يرجع 200؛ تعمّد ترك migration معلقة → السيرفر يرفض الإقلاع برسالة واضحة (وليس 500 صامتة).
2. **سيناريو run-log:** جهاز بكاش فارغ + سيرفر مطفي → Splash يفشل برسالة صريحة وزر إعادة، الهيدر أحمر، لا crash عند فتح أي برنامج، البروفايل يعرض السبب، وزر Start يعرض «تنزيل» بدل الصمت. ثم تشغيل السيرفر → إعادة المحاولة → Splash يكمل → 🟡 أثناء prefetch → 🟢.
3. **سيناريو 5xx:** سيرفر يعمل بقاعدة مكسورة عمدًا → الهيدر أحمر برسالة «الخادم يواجه مشكلة»، الكتالوج القديم يظل يعمل، ولا قصف طلبات (cooldown يعمل — ليس 15 طلبًا في دقيقة).

---

## 6. Checklist بيئة التطوير (تُنفَّذ الآن قبل أي إعادة تشغيل)

1. ☐ **`cd backend && npx prisma migrate deploy`** — تطبيق migration الـ idempotency المعلقة (سبب كل الـ 500). ثم إعادة تشغيل السيرفر والتحقق أن `GET /api/mobile/sync` يرجع 200.
2. ☐ **توليد صوت الرسائل** (`POST /messages/bulk-audio` من الأدمن للغتين) — حتى يصبح `withAudio > 0` بدل 2662/0.
3. ☐ تأكد أن الباك يعمل وقت تجربة الهاتف وأن `api.physical_device_ip` (حاليًا `192.168.68.136`) هو IP جهاز التطوير الحالي — في نافذة run-log لم يصل أي طلب إطلاقًا (السيرفر كان متوقفًا وقتها على الأرجح).
4. ☐ سطر `Configuration locales [en_GB,ar_EG] → [en]` — التطبيق ثبّت الإنجليزية؛ راجع تفضيل اللغة المخزن إن كنت تتوقع العربية.
5. ☐ بعد B0 + R0-ب + R1–R3: إعادة تشغيل سيناريوهات القبول الثلاثة (§5) وتحديث هذا الملف بالنتائج.
