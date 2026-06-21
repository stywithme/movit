# UI/UX Maintenance Plan — مهام روتينية (للتنفيذ اليدوي)

هذه المهام **متكررة / منخفضة المخاطر / ميكانيكية**. مناسبة للتنفيذ اليدوي من الفريق. المهام المعقّدة (تفكيك الـ god classes إلى MVVM) تُنفَّذ بشكل منفصل وتدريجي مع التحقق بالـ build.

> قاعدة عامة: بعد كل تعديل، اعمل build في Android Studio. لا توجد gradlew كاملة في المشروع، فالتحقق من جهتك.

---

## P0 — قبل الإطلاق العام (مش حرج للبيتا الداخلية)

### 1. تفعيل R8 / minify في الـ release
الوضع الحالي: `isMinifyEnabled = false` في `app/build.gradle.kts`.

- فعّل: `isMinifyEnabled = true` و `isShrinkResources = true` في `buildTypes.release`.
- اختبر بناء release كامل، وراجع `proguard-rules.pro` (خصوصاً لـ Gson models، Retrofit، MediaPipe، LiteRT — أضِف keep rules عند الحاجة).
- الأثر: حجم APK أقل + obfuscation.

### 2. تباين الـ Status Bar
الوضع: `android:windowLightStatusBar=false` ثابت في `themes.xml` مع status bar شفاف → في اللايت مود أيقونات الستاتس بار قد تبهت.

- الحل: اضبط `windowLightStatusBar` لكل وضع — `values/themes.xml` (light = true) و `values-night/` (false)، أو برمجياً عبر `WindowInsetsControllerCompat(window, root).isAppearanceLightStatusBars = !isNightMode`.

---

## P1 — جودة الكود (تدريجي)

### 3. توحيد ViewBinding بدل findViewById
الوضع: `viewBinding = true` مفعّل، لكن **389 استخدام لـ findViewById عبر 31 ملف**. تناقض.

أولوية حسب العدد (Activities/Fragments أولاً):

| ملف | عدد findViewById | ملاحظة |
| --- | --- | --- |
| `programs/ProgramSessionActivity` | 60 | — تم اعادة هيكلته|
| `train/TrainFragment` | 34 | |
| `profile/ProfileActivity` | 25 | |
| `train/TrainingPreferenceDialogs` | 17 | |
| `report/SessionSummaryFragment` | 18 | |
| `programs/ProgramSessionReportActivity` | 12 | |
| `programs/ProgramDayActivity` | 11 | |
| `programs/ProgramDetailActivity` | 11 | |
| `exercises/ExercisesFragment` | 11 | |
| `workouts/WorkoutListActivity` / `WorkoutDetailActivity` | 8 / 8 | |
| `reports/ReportsOverviewFragment` | 8 | |
| `report/components/*` (HeroSection, PerformanceCard, ErrorComparisonCard, KeyMomentsSection...) | 8-18 | أقل أولوية (Custom Views) |

طريقة العمل لكل ملف:
- Activity: استخدم `binding` المتولّد (`Activity<Name>Binding.inflate`) وبدّل كل `findViewById(R.id.x)` بـ `binding.x`.
- Fragment: استخدم نمط `_binding`/`binding` مع تنظيف في `onDestroyView` (الـ fragments الأساسية تعمله صح بالفعل — اتبع نفس النمط).
- Custom View (مكوّنات `report/components`): إمّا `bind(this)` بعد inflate، أو اتركها (findViewById مقبول في Custom Views) — أولوية أخيرة.

ملاحظة: استبعد `programs/ProgramSessionActivity` من هذه الموجة لتجنّب تعارض مع إعادة الهيكلة الجارية.

### 4. تنظيف force-unwraps (`!!.`)
أثناء موجة الـ binding، استبدل كل `x!!.` بـ:
- `x?.` (safe call) + معالجة null، أو
- `?: return` / `?: return@…`، أو
- `requireNotNull(x) { "سبب" }` لو null فعلاً خطأ برمجي.
ركّز على الملفات عالية العدد (نفس قائمة 3).

### 5. حذف استدعاءات `ApiClient.init` الزائدة
`PoseApp.onCreate` بينادي `ApiClient.init(this)` بالفعل (مرة واحدة لعمر العملية). الاستدعاءات دي زائدة:
- `ui/main/MainContainerActivity.kt:44` → احذف السطر.
- `ui/subscription/SubscriptionActivity.kt:64` → احذف السطر.

### 6. حذف الكود الميّت
- `ui/MainActivity.kt`: تأكد إنه غير مُستخدم (الـ launcher هو `SplashActivity`، والـ home هو `MainContainerActivity`). لو مفيش مراجع له في الكود ولا الـ manifest → احذفه.
- ابحث عن أي شاشات/دوال legacy غير مرجعية.

### 7. توحيد `estimateSessionDuration` المكرّر
نسختان بتوقيعين مختلفين:
- `ui/programs/ProgramSessionFormatting.kt` (List<ProgramSessionItem>) — تم استخراجها.
- `ui/train/TrainFragment.kt:1124` (ProgramSession) — منطق مشابه.
وحّدهما في util مشترك لو أمكن (أو وثّق سبب الاختلاف).

---

## P2 — تنظيف الهوية (Movit — تم 2026-06-17)

### 8. إعادة تسمية الثيم والبراند الميت ✅
- `Theme.WayToFix` → `Theme.Movit` (+ Splash/Training/Dialog).
- `TextAppearance.WayToFix.*` / `Widget.WayToFix.*` → `*.Movit.*`.
- Deep links: `waytofix://` → `movit://`.
- احذف `Theme.TrainingValidatorPoC` (legacy alias) بعد التأكد من عدم استخدامه — **متبقٍ**.

---

## ما يتولّاه كلود (لا تكرّره يدوياً)

- تفكيك `ProgramSessionActivity` (1714 سطر) إلى MVVM/controllers — **جارٍ**، شريحة بشريحة مع تحقّق build من جهتك.
- نفس النهج لاحقاً لـ `TrainingActivity` (1067) و `TrainFragment` (1028).

## قائمة فحص

- [ ] 1. minify + shrinkResources في release (قبل الإطلاق العام)
- [x] 2. تباين status bar (light/night)
- [ ] 3. findViewById → ViewBinding (حسب الأولوية، عدا ProgramSessionActivity) — **TrainFragment تم**
- [ ] 4. تنظيف `!!.`
- [x] 5. حذف ApiClient.init الزائد (مكانين)
- [x] 6. حذف MainActivity الميّت (بعد التأكد)
- [x] 7. توحيد estimateSessionDuration (overload لـ ProgramSession في ProgramSessionFormatting)
- [x] 8. إعادة تسمية الثيم/البراند (WayToFix → Movit)
