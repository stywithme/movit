# مراجعة تدفق البيانات بين الموبايل والباك — وخطة العمل أوف لاين بالكامل

**التاريخ:** 2026-07-26
**النطاق:** `exercises` · `workouts` · `programs` · `reports` (+ الرسائل والصوت والصور)
**الهدف:** أن يعمل التطبيق بالكامل داخل جيم بدون إنترنت.

---

## 1. الخلاصة التنفيذية

الخبر الجيد: **بنية المزامنة موجودة وسليمة**. `/api/mobile/sync` يرسل الكتالوج كاملاً (كل التمارين
المنشورة + كل الـ workout templates + كل البرامج + مكتبة الرسائل + رسائل النظام + مانيفست الصوت)،
والموبايل يخزّنها في SQLDelight عبر `MovitSyncOrchestrator`، والملفات الصوتية تُحمَّل فعلياً على القرص.

الخبر السيئ: **الطبقة الأخيرة قبل «أوف لاين كامل» كانت ناقصة في أربع نقاط**، وكلها في التنفيذ لا في
التصميم:

| # | الفجوة | الأثر في الجيم |
|---|--------|-----------------|
| 1 | **الصور لم تكن تُحمَّل فعلياً** — فقط أول 24 رابط تُدفع إلى الـ Coil disk cache (المعرَّض للحذف) | كل صور التمارين وصور أوضاع الكاميرا تظهر فارغة |
| 2 | **الخطط الفعلية (effective plans) تُخزَّن لكل (أسبوع، يوم) عند الزيارة فقط** | أي أسبوع لم يفتحه المستخدم أونلاين = جلسة فارغة |
| 3 | **استبدال التمرين (swap) أونلاين 100%** — حقول التجميع غير موجودة أصلاً في الـ payload | زر الاستبدال لا يعطي بدائل |
| 4 | **مقاييس التقارير (metrics) مخزَّنة per-query فقط** | تقرير الأسبوع/اليوم فارغ أوف لاين |

هذه المراجعة تشرح التدفق كاملاً، ثم توثّق ما تم إصلاحه في هذا الـ branch وما تبقّى.

---

## 2. خريطة التدفق الحالية

### 2.1 نقطة الدخول الوحيدة: `GET /api/mobile/sync`

`backend/src/modules/mobile-sync/mobile-sync.service.ts`

```
sync(updatedAfter?, forceRefresh?, includeReports?)
  ├── exercises[]            ← كل التمارين المنشورة (buildExerciseConfig كامل)
  ├── messageLibrary[]       ← قوالب رسائل الفيدباك المرتبطة
  ├── systemMessages[]       ← رسائل النظام
  ├── workoutTemplates[]     ← WorkoutExport كامل (phases + exercises)
  ├── programs[]             ← ProgramExport كامل (weeks → days → plannedWorkouts → items)
  ├── userPrograms[]         ← اشتراكات المستخدم + customizations
  ├── userExercisePreferences[]
  ├── plannedWorkoutReports[]← summary أو full حسب includeReports
  ├── audioManifest          ← قائمة ملفات TTS بأحجامها
  └── deleted*Ids[]          ← tombstones للحذف/إلغاء النشر
```

**نقاط قوة مؤكدة:**

- المزامنة الكاملة **لا تحتوي `take`** — أي أن كل الكتالوج ينتقل فعلاً (لا حد أعلى).
- الـ delta يعتمد `updatedAt > watermark` مع `computeSafeSyncWatermark` الذي يمنع فقدان
  التحديثات المتزامنة مع لحظة الطلب.
- الـ tombstones تغطي الحذف **وإلغاء النشر** (`status: 'draft'` / `isPublished: false`) — وهذا
  صحيح، لأن إلغاء النشر يجب أن يُزيل العنصر من الموبايل تماماً كالحذف.
- انهيار شرائح المستخدم لا يُسقِط الكتالوج (`userSlicesDegraded` في الـ meta).

### 2.2 التخزين على الموبايل

`kmp-app/core/data/src/commonMain/.../sync/MovitSyncOrchestrator.kt`

المسار الحرج داخل **معاملة واحدة** (`localStore.transaction`):
`applySyncExercises` → `systemMessageCache` → `userProgramEnrollments` → `exploreSync.applyFromSync`
→ `catalogOffline.applyFromSync` → watermark.

مكتبة الرسائل (~2.6k JSON) تُطبَّق **خارج** المعاملة (أو تُؤجَّل في الـ delta) — قرار صحيح، وهو ما
حلّ تعليق الـ Splash سابقاً (F5).

المخازن المحلية:

| المحتوى | المخزن | الطبقة |
|---------|--------|--------|
| إعدادات التمرين | `EXERCISE_CONFIG_STORE` + slug index | `TrainingConfigRepository` |
| قوالب التمرين (workout) | `WORKOUT_TEMPLATE_STORE` + id index | `SyncCatalogOfflineRepository` |
| البرامج | `PROGRAM_STORE` + id index | `SyncCatalogOfflineRepository` |
| بطاقات الاستكشاف | `EXPLORE_STORE` | `ExploreSyncRepository` |
| التقارير | `REPORTS_STORE` | `ReportsSyncRepository` |
| الخطط الفعلية | `SESSION_STORE` per (userProgramId, week, day) | `WorkoutSessionSyncRepository` |
| ملفات الصوت | `filesDir/audio_cache/{ar,en}` | `AudioFileDownloader` |
| **ملفات الصور** | `filesDir/image_cache` — **جديد** | `ImageFileDownloader` |

### 2.3 الكتابة أوف لاين

`OfflineWriteQueue` + `OutboxDispatcher` + `SessionJournalStore` تغطي رفع الجلسات والتقارير
والتفضيلات مع ترتيب التبعيات وإعادة المحاولة. **هذه الجهة سليمة** ولم تحتج تعديلاً.

---

## 3. الفجوات — بالتفصيل وما تم عمله

### 3.1 الصور: لم تكن تنتقل للموبايل إطلاقاً ✅ تم الإصلاح

**ما كان يحدث:**

`BackgroundMediaPrefetcher.collectCatalogImageUrls()` كان يجمع روابط من `explore` فقط ثم:

```kotlin
return urls.take(24).toList()   // ← سقف 24 رابط
```

ثم يمررها إلى `prefetchMovitImageUrls` التي تُنفّذ `loader.enqueue(request)` — أي دفع إلى
**Coil disk cache** فقط، وهو:

- على أندرويد في `cacheDir` → يمسحه النظام عند ضغط التخزين؛
- على iOS في `NSCachesDirectory` → نفس المشكلة؛
- محدود بـ 64 MiB مع إزاحة LRU؛
- بلا تتبّع نجاح أو إعادة محاولة.

والأخطر: **`positionImageUrl`** (صورة وضع الكاميرا المرجعية التي تظهر أثناء الأداء) لم تكن
ضمن الروابط المجمّعة أصلاً — رغم أن الباك يرسلها داخل كل `poseVariant`.

كذلك `WeekOfflinePackPrefetcher` كان يحمل تعليقاً صريحاً:

```kotlin
// TODO(N-25): platform Coil3 prefetch for [imageUrls] once a common ImagePrefetchPort exists.
```

**ما تم تنفيذه — خط أنابيب صور دائم يوازي خط الصوت:**

| ملف | الدور |
|-----|-------|
| `core/data/image/ImageAsset.kt` | نموذج الأصل + نوعه + تغطية الكاش |
| `core/data/image/ImageDownloadSupport.kt` | اسم ملف ثابت من الرابط (FNV-1a + امتداد آمن) وحل الروابط النسبية |
| `core/data/image/ImageAssetManifestBuilder.kt` | يبني المانيفست **محلياً** من الكاش (لا حاجة لـ endpoint جديد) |
| `core/data/image/ImageFileDownloadPort.kt` | `expect class ImageFileDownloader` + سقف 256 MiB |
| `.../androidMain/.../ImageFileDownloader.android.kt` | `filesDir/image_cache` عبر OkHttp، تنزيل ذرّي عبر `.part` |
| `.../iosMain/.../ImageFileDownloader.ios.kt` | `Application Support/image_cache` عبر `NSURLSession` |
| `core/data/image/ImagePrefetchRunner.kt` | التنزيل بالأولوية + `coverage()` + `localPathFor()` |
| `designsystem/platform/MovitLocalImageSource.kt` | جسر بلا اعتماد بين `core:data` والـ Compose |

**الأولوية عند التنزيل** (لأن مقاطعة التنزيل واردة):

1. `PosePosition` — صورة وضع الكاميرا (حرجة للتمرين)
2. `ExerciseThumbnail`
3. `WorkoutCover`
4. `ProgramCover`

**القراءة:** `MovitRemoteImage` صار يمرّ عبر `MovitLocalImageSource.modelFor(url)` — فإن كان الملف
منزّلاً يُقرأ من `file://` مباشرة، وإلا يعود للرابط. نقطة تحكم واحدة (تأكدت أنه لا يوجد أي
`AsyncImage` آخر في المشروع).

**التشغيل:**

- بعد كل دورة مزامنة ناجحة (`scheduleCatalogImagePrefetch`) — **مؤجَّل خارج المسار الحرج** حتى لا
  تتكرر مشكلة تعليق الـ Splash؛ وتنظيف الملفات اليتيمة في المزامنة الكاملة فقط.
- بعد الإقلاع (`BackgroundMediaPrefetcher.runAfterBootstrap`) — بلا سقف 24.
- داخل «حزمة الأسبوع» — مرحلة `CachingImages` الجديدة.

> **ملاحظة عن Coil:** تركتُ كاش Coil في مكانه كما هو. لم يعد طبقة الاعتماد على الإطلاق —
> صار كاش أداء فقط، والمصدر الدائم هو `image_cache`.

### 3.2 الخطط الفعلية: أسبوع واحد لا يكفي ✅ تم الإصلاح

`syncEffectivePlan(userProgramId, week, day)` يخزّن لكل (أسبوع، يوم) على حدة. و
`WeekOfflinePackPrefetcher.prefetchWeek` كان يغطي **أسبوعاً واحداً** فقط.

النتيجة: مستخدم في برنامج 12 أسبوعاً حمّل «حزمة الأسبوع 3» ثم دخل الجيم في الأسبوع 4 → لا خطة.

**ما تم:** `prefetchWeeks(program, weekNumbers = كل الأسابيع, skipReadyWeeks = true)` تغطي البرنامج
كاملاً في تمريرة واحدة، مع **مزامنة delta واحدة** للتشغيلة كلها (كان `prefetchWeek` يعيد المزامنة
لكل أسبوع)، و`offlineReadyWeeks(program)` لعرض الحالة في الواجهة.

### 3.3 استبدال التمرين: أونلاين 100% ✅ تم الإصلاح (باك + موبايل)

`exerciseSubstitutionsService` في الباك يرتّب البدائل بـ:

1. نفس `familyKey` مرتّبة بـ `familyOrder`
2. وإلا نفس `movementPattern` + `archetype` (بحد أقصى 6)

وهذه الحقول الأربعة **لم تكن ضمن `buildExerciseConfig` إطلاقاً** — أي أن الموبايل لا يملك المعلومة
التي تسمح له بحساب البدائل محلياً مهما فعل. كانت فجوة عقد بيانات، لا فجوة تنفيذ.

**الباك:** أُضيفت `familyKey` / `familyOrder` / `movementPattern` / `archetype` إلى
`ExerciseConfig` وإلى `buildExerciseConfig` (بلا تغيير في استعلام Prisma — `exerciseFullInclude`
يستخدم `include` فتعود الحقول العددية تلقائياً). إضافة اختيارية متوافقة مع الإصدارات القديمة.

**الموبايل:** `OfflineSubstitutionResolver` يعيد إنتاج نفس ترتيب الخادم من الكتالوج المحلي، و
`fetchSubstitutionCandidates` صار يسقط إليه عند انعدام الشبكة أو فشل الطلب أو رجوع قائمة فارغة.

> انتبه: الحقول تظهر للموبايل بعد **مزامنة كاملة** واحدة (أو تحديث `updatedAt` للتمارين). قبلها
> يرجع الـ resolver قائمة فارغة ويظل السلوك كما هو (اعتماد على الشبكة) — أي تدهور آمن.

### 3.4 مقاييس التقارير ⚠️ إصلاح جزئي

`ReportsSyncRepository.syncMetrics(query)` يخزّن الاستجابة بمفتاح مشتق من الاستعلام
(scope + programId + week + day + …). أوف لاين، أي استعلام لم يُنفَّذ أونلاين من قبل يرجع `Failure`.

**ما تم:** «حزمة الأسبوع» صارت تُسخّن مقاييس الأسبوع ومقاييس كل يوم تدريب فيه (مرحلة
`CachingReports`)، فتصبح متاحة أوف لاين.

**ما تبقّى (موصى به، لم يُنفَّذ):** لوحة التقارير الرئيسية تُرقَّع محلياً بعد كل جلسة
(`patchDashboardFromCompletion`)، لكن لا يوجد **محرك مقاييس محلي** يحسب من
`readAllCachedPlannedWorkoutReports()`. البديل الأنظف: endpoint واحد
`GET /api/mobile/reports/bundle?programId=` يعيد كل نطاقات المقاييس للبرنامج في استجابة واحدة،
فيُخزَّن كاملاً بطلب واحد بدل N طلب.

---

## 4. ملاحظات مراجعة إضافية (لم تُغيَّر — للتوثيق والقرار)

1. **`syncProgressMetrics`** في `ProgramFlowSyncRepository` هي الدالة الوحيدة في الملف بلا
   fallback للكاش — تفشل أوف لاين بينما جاراتها تنجح. عدم اتساق مقصود؟ يُنصح بإضافة كاش لها.

2. **`ensure()` أثناء التمرين يفترض الشبكة.** إذا لم يُوجد إعداد التمرين محلياً، المسار كله
   (template config → delta sync → exercise config) شبكي. هذا مقبول *بشرط* أن تكون المزامنة
   الكاملة قد نجحت مرة — وهو ما تضمنه `needsTrainingConfigBackfill`. لكن لا يوجد ما **يمنع**
   بدء جلسة بإعدادات ناقصة أوف لاين؛ `ensureAll` يرجع `Offline` والواجهة هي من تقرر.

3. **`/api/mobile/explore` صار زائداً عملياً** — `SyncCatalogMapper` يشتق بطاقات الاستكشاف من
   حمولة `/sync` نفسها. الإبقاء عليه مقبول للتوافق، لكنه سطح إضافي يحتاج صيانة موازية.

4. **الفيديو غير مُصدَّر إطلاقاً.** `exerciseFullInclude` يختار `media` بشرط
   `isPrimary: true, type: 'image'` فقط. إن كانت هناك خطة لعرض فيديو توضيحي، فهو غير موجود في
   العقد أصلاً ولا يمكن أن يعمل أوف لاين.

5. **أسماء مستعارة قديمة** (`workouts` بجانب `workoutTemplates`، و`deletedWorkoutIds`) ما زالت
   تُرسَل في كل استجابة. تكلفة صغيرة لكنها مضاعفة لحجم قوائم القوالب — تستحق إزالة بعد تأكيد
   انقراض العملاء القدامى.

6. **`totalSizeBytes()`** الجديدة تُحصي بمسح المجلد في كل نداء. مقبول بمعدل النداء الحالي
   (مرة لكل دورة مزامنة)، وليس مقبولاً لو استُدعيت في حلقة.

---

## 5. الاختبارات

**الباك (تم تشغيلها فعلياً):**

```
npx jest src/modules/mobile-sync src/modules/exercises
→ 5 suites / 19 tests  PASS
npx jest src/modules/exercises/__tests__/exercise-config-substitution-fields.spec.ts
→ 3 tests  PASS   (جديد)
npx jest   (كامل)
→ 20/21 suites PASS
```

الـ suite الفاشل الوحيد `app-store.client.spec.ts` يفشل لأنه يحمّل شهادة Apple الجذرية من
`www.apple.com` وهي محجوبة على شبكة بيئة التنفيذ — **غير متعلق بهذا التغيير** (يفشل على
`main` أيضاً في نفس البيئة).

**الموبايل (مكتوبة، لم تُشغَّل — انظر التحذير أدناه):**

- `ImageDownloadSupportTest` — 7 اختبارات: ثبات اسم الملف، منع التصادم، الامتدادات، أمان
  الأحرف، الروابط النسبية/المطلقة، تجاهل `.part`.
- `ImagePrefetchRunnerTest` — 8 اختبارات: بناء المانيفست شاملاً صور الأوضاع، ترتيب الأولوية،
  تخطي المنزَّل، عدم لمس الشبكة أوف لاين، تنظيف اليتامى في الكامل فقط، حل المسار المحلي،
  مانيفست الأسبوع.
- `OfflineSubstitutionResolverTest` — 5 اختبارات تطابق ترتيب الخادم.
- `FakeImageFileDownloader` — بديل اختباري للمنفذ.

> ⚠️ **لم أتمكّن من بناء أو تشغيل اختبارات KMP في هذه البيئة.** سياسة الشبكة تحجب
> `dl.google.com`، فلا يمكن تنزيل Android Gradle Plugin 8.13.2:
> ```
> Plugin [id: 'com.android.application', version: '8.13.2'] was not found
> CONNECT tunnel failed, response 403 → dl.google.com:443
> ```
> كود Kotlin (المصدر والاختبارات) **لم يُترجَم بعد**. يجب تشغيل
> `./gradlew :core:data:allTests :core:designsystem:compileKotlinAndroid` على بيئة بها SDK
> قبل الدمج.

---

## 6. ما ينبغي عمله بعد ذلك

مرتّب بالأثر:

1. **تشغيل بناء KMP والاختبارات** — الشرط الأول للدمج (انظر التحذير أعلاه).
2. **ربط `prefetchWeeks` بالواجهة** — الكود جاهز؛ ينقص زر «جهّز البرنامج كاملاً للأوف لاين»
   في شاشة تفاصيل البرنامج بدل زر الأسبوع الواحد، مع عرض `coverage.percent` للصور.
3. **مؤشر جاهزية أوف لاين حقيقي** — `ImagePrefetchRunner.coverage()` + تغطية الصوت +
   `offlineReadyWeeks()` في شاشة واحدة، بدلاً من علامة `ready_` الثنائية الحالية.
4. **`GET /api/mobile/reports/bundle`** لإنهاء فجوة التقارير بشكل كامل (بند 3.4).
5. **`syncProgressMetrics` fallback للكاش** (بند 4.1).
6. **حسم موضوع الفيديو** — إما إخراجه من العقد أو إضافته لخط أنابيب الوسائط (بند 4.4).
