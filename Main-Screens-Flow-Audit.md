# خطة توحيد تجربة Explore وTrain ومسار التدريب

تاريخ المراجعة: 2026-07-10

النطاق: الوضع الحالي في تطبيق KMP/Compose فقط، من اختيار Exercise أو Workout أو Program وحتى التحضير، التدريب، الراحة، التقرير، والعودة إلى الشاشة التي بدأ منها المستخدم.

## 1. النتيجة التنفيذية

المسارات الأساسية موجودة، و`Train` يعيد استخدام جزء مهم من مسار `Explore` بالفعل:

- Exercise: `ExercisePrepareRoute -> TrainingSessionRoute -> ReportDetailRoute`.
- Workout: `WorkoutSessionRoute -> ExercisePrepareRoute -> TrainingSessionRoute -> ReportDetailRoute`.
- Program workout: `ProgramDetailRoute/Train -> WorkoutSessionRoute` ثم نفس المسار السابق.

لكن التجربة لا تعتبر مكتملة أو موحدة بعد. المشكلة ليست في شكل شاشة منفردة، بل في عقد التشغيل بين الشاشات:

- نموذج الـ workout يفقد بيانات مؤثرة قبل الوصول إلى محرك التدريب.
- بدء الجلسة يمكن أن ينفذ sync/fetch بعد ضغط Start، من دون حالة جاهزية موحدة.
- الإكمال والتقرير والعودة تعتمد على push/pop عام، ولا تمثل رحلة تدريب لها بداية ونهاية معروفتان.
- بعض إجراءات `Train` لا تستهدف العنصر الذي ضغطه المستخدم، وبعض عناصر التحكم تغير الواجهة فقط ولا تغير الجلسة الفعلية.
- توجد آليتان متوازيتان لتقدم الـ workout: `TrainingSessionFlowCoordinator` و`WorkoutRunProgressStore`.

الحكم الحالي: البنية قابلة للتطوير، لكن يلزم تنفيذ P0 وP1 أدناه قبل اعتبار التدفق احترافيا أو آمنا للإطلاق العام.

## 2. القرارات المعتمدة

هذه القرارات هي المرجع عند التنفيذ، ولا ينبغي إنشاء مسار بديل خاص بـ `Train`.

1. `Explore` هو مصدر الاكتشاف والتفاصيل، و`Train` هو مصدر الخطة الشخصية والأولوية اليومية.
2. كلاهما يطلقان نفس `WorkoutSessionRoute` ونفس `ExercisePrepareRoute` ونفس `TrainingSessionRoute` ونفس التقرير.
3. `WorkoutSessionRoute` هو المصدر الوحيد لمراجعة وتعديل workout قبل التشغيل.
4. بطاقة اليوم في `Train` تعرض preview فقط؛ لا تحتوي تعديلات محلية لا تصل إلى الجلسة.
5. الضغط على Exercise داخل workout يفتح preview للتمرين، ولا يغير نقطة بدء workout ضمنيا.
6. Start workout يبدأ أو يستأنف run واضحا من الجلسة، وليس من آخر route index أو كاش قديم.
7. كل run له `runId` مستقل، حتى عند تشغيل نفس workout أكثر من مرة.
8. زر Start النهائي لا يبدأ network setup. التحضير يحدث عند دخول التفاصيل، وتظهر حالة الجاهزية قبل الضغط.
9. التقرير النهائي للـ workout هو تقرير الجلسة كلها. تقرير التمرين المفرد يستخدم فقط في solo exercise أو drill مقصود.
10. التقرير يستبدل رحلة التدريب المكتملة في الـ navigation stack، ولا يضاف فوق TrainingSession مكتملة.
11. Back أثناء جلسة نشطة يعرض قرارا واضحا: متابعة التدريب، حفظ والخروج، أو إنهاء الجلسة.
12. شاشة Program تعرض ملخص كل الأسابيع مع أسبوع واحد محدد بالتفصيل. لا تعرض كل الأسابيع ممدودة في صفحة طويلة.

## 3. المسار الموحد المستهدف

### 3.1 طلب تشغيل واحد

يجب أن تحول كل نقاط الدخول نية المستخدم إلى عقد واحد، مثل:

```kotlin
data class WorkoutLaunchRequest(
    val source: LaunchSource,
    val workoutRef: WorkoutRef,
    val returnTarget: ReturnTarget,
    val requestedStart: RequestedStart = RequestedStart.BeginOrResume,
)
```

ويقوم Launch Coordinator واحد بالآتي:

1. حل workout/session identity.
2. فتح `WorkoutSessionRoute` مرة واحدة فور صلاحية المرجع، من دون انتظار الشبكة.
3. قراءة النسخة المحلية فورا إن وجدت وعرضها.
4. تجهيز effective plan وtraining configs في الخلفية داخل نفس الرحلة.
5. إصدار `LaunchReadiness` موحد يتحكم في CTA، لا في ظهور شاشة التفاصيل.

الحالات المطلوبة:

- `LoadingContent`: لا توجد نسخة قابلة للعرض.
- `Refreshing`: توجد نسخة قابلة للاستخدام ويجري تحديثها.
- `Preparing`: تفاصيل workout موجودة لكن ملفات التشغيل لم تجهز بعد.
- `Ready`: يمكن البدء محليا من snapshot محددة.
- `OfflineReady`: يمكن البدء من cache مع توضيح أن الرفع سيتم لاحقا.
- `Blocked(reason)`: لا يمكن التشغيل، مع Retry وإجراء بديل واضح.
- `Launching`: يمنع الضغط المكرر حتى يتم الانتقال.

### 3.2 المسارات حسب المصدر

| المصدر | أول شاشة | المسار بعد ذلك | العودة بعد الإكمال |
|---|---|---|---|
| Explore Exercise | `ExercisePrepareRoute` بوضع Solo | `TrainingSessionRoute -> ReportDetailRoute` | Explore مع الحفاظ على البحث والفلتر |
| Explore Workout | `WorkoutSessionRoute` | `ExercisePrepareRoute -> TrainingSessionRoute -> ReportDetailRoute` | تفاصيل workout أو Explore حسب زر Done |
| Explore Program | `ProgramDetailRoute` | enrollment عند الحاجة ثم `WorkoutSessionRoute` الموحد | Program Detail/Train بعد التقرير |
| Train Today | `WorkoutSessionRoute` لنفس session key | نفس مسار Explore Workout بالكامل | Train بعد تحديث اليوم والتقرير |
| Train Calendar Day | `WorkoutSessionRoute` للـ day/workout المحدد | نفس المسار بالكامل | Train مع بقاء الأسبوع واليوم محددين |
| Resume | `WorkoutSessionRoute` بحالة Resume | نفس الجلسة من run snapshot | المصدر الأصلي المحفوظ في run |

## 4. الإصلاحات الحرجة P0

### P0.1 - الحفاظ على عقد workout كاملة حتى محرك التدريب

الحالة الحالية:

- `WorkoutTemplateSessionMapper` ينشئ Rest blocks من `restAfterExerciseMs`.
- `WorkoutFlowMapper` يحتفظ بـ Exercise blocks فقط، فتختفي Rest blocks.
- `restBetweenSetsMs` و`restAfterExerciseMs` يتحولان إلى نفس `restSeconds`.
- `WorkoutSessionBlockUi.Exercise` لا يحمل `variantIndex`، و`WorkoutFlowMapper` لا يمرره.
- phase role المحسوب للـ section لا يصل إلى exercise لأن `mapExercise` يضع `phaseRole = "MAIN"`.
- `durationSeconds` موجود في نماذج المكتبة، لكن `toTrainingFlowItems()` يحوله عمليا إلى `targetReps = 12` عند غياب reps.
- target weight ظاهر في تفاصيل الجلسة لكنه ليس جزءا من عقد التشغيل.

الأثر:

- workout الذي يراه المستخدم يمكن أن يختلف عن الذي ينفذه المحرك.
- تمارين الوقت قد تعمل كتمارين عدات.
- الراحة، warmup/cooldown، والـ pose variant قد تكون خاطئة.
- progress والتقرير قد يحسبان عناصر غير صحيحة.

الإصلاح المطلوب:

1. إنشاء نموذج canonical واحد للـ run، يحافظ على ترتيب كل blocks:

```kotlin
sealed interface WorkoutRunBlock {
    data class Exercise(
        val exerciseId: String,
        val slug: String,
        val phaseRole: String,
        val target: ExerciseTarget,
        val sets: Int,
        val restBetweenSetsMs: Long,
        val restAfterExerciseMs: Long,
        val poseVariantIndex: Int,
        val weightPerSetKg: List<Float>?,
    ) : WorkoutRunBlock

    data class Rest(val durationMs: Long) : WorkoutRunBlock
}
```

2. استخدام `ExerciseTarget.Reps` و`ExerciseTarget.Duration` بدلا من default reps.
3. تمرير phase وvariant والراحتين من DTO إلى session UI ثم run snapshot دون إعادة تفسير.
4. منع Start لو snapshot لا يمكن تمثيلها بأمان في المحرك.
5. جعل تقدير الوقت، العرض، المحرك، والتقرير يقرأون نفس snapshot.

أدلة الكود:

- `kmp-app/feature/library/src/commonMain/kotlin/com/movit/feature/library/WorkoutTemplateSessionMapper.kt:122-191`
- `kmp-app/feature/library/src/commonMain/kotlin/com/movit/feature/library/WorkoutFlowModels.kt:163-181`
- `kmp-app/feature/library/src/commonMain/kotlin/com/movit/feature/library/WorkoutTrainingLaunch.kt:15-33`
- `kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/session/TrainingSessionFlowCoordinator.kt:226-240`

### P0.2 - تقرير workout والإكمال والعودة

الحالة الحالية:

- upload context أصبح موجودا للـ Explore workout العادي، وهذا الجزء صحيح حاليا.
- حفظ session report الكامل ما زال داخل `finalizePlannedWorkoutDay()` ويشترط `plannedWorkout`.
- workout عادي قد ينتهي بـ report id لآخر exercise بدلا من تقرير الجلسة.
- `TrainingSessionRoute` يفتح التقرير تلقائيا عند `isComplete + reportDetailId`.
- Shell يعمل push لـ `ReportDetail`; Back من التقرير يعيد المستخدم إلى TrainingSession المكتملة، وقد يعاد فتح التقرير تلقائيا.
- `Train` يستخدم Weekly Report كوجهة عامة لعدة أزرار تحمل معنى day/session report.

الإصلاح المطلوب:

1. إضافة `finalizeWorkoutRun()` عام لكل workout sources.
2. بناء `SessionReport` واحد من accumulated executions وربطه بـ `runId`.
3. إنشاء `ReportTarget` typed: `Exercise`, `WorkoutRun`, `ProgramDay`, `ProgramWeek`.
4. استبدال stack الرحلة عند الإكمال:
   - إزالة `ExercisePrepare` و`TrainingSession` الخاصة بالـ run.
   - وضع `ReportDetail(runReportId, returnTarget)` كوجهة نهائية.
5. زر Back داخل التقرير يعود إلى المصدر المحدد، وزر Done يعود مباشرة إلى Train/Explore بعد refresh.
6. منع auto-navigation المتكرر باستخدام navigation effect one-shot أو completion state consumed.

أدلة الكود:

- `kmp-app/feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingSessionViewModel.kt:885-980`
- `kmp-app/feature/training/src/commonMain/kotlin/com/movit/feature/training/MovitTrainingRoutes.kt:122-134`
- `kmp-app/feature/shell/src/commonMain/kotlin/com/movit/feature/shell/MovitInnerHost.kt:159-170`
- `kmp-app/feature/reports/src/commonMain/kotlin/com/movit/feature/reports/MovitReportsRoute.kt:51-82`

### P0.3 - هوية run وعزل الحالة بين الجلسات

الحالة الحالية:

- `WorkoutFlowCache.ensureWorkoutGroupId(workoutId)` يعيد نفس group id حتى يتم استدعاء clear.
- مسار الإنتاج لا يمسح `WorkoutFlowCache` عند اكتمال run.
- `TrainingSessionViewModel` و`WorkoutSessionViewModel` و`ReportDetailViewModel` وبعض التقارير بلا ViewModel keys مبنية من route identity.
- Shell يعرض كل inner routes في نفس موضع composition.

الأثر:

- تشغيل نفس workout مرتين قد يجمع uploads من محاولتين مختلفتين.
- فتح workout/report جديد من نفس النوع قد يعرض state قديمة.
- resume journal والتقرير قد يرتبطان بهوية workout الثابتة بدلا من محاولة التشغيل.

الإصلاح المطلوب:

1. إنشاء `WorkoutRunId` عند Start الفعلي، لا عند فتح التفاصيل.
2. تخزين `runId`, `source`, `returnTarget`, snapshot version، وprogress في `WorkoutRunStore`.
3. استخدام `runId` في upload grouping وreport id وjournal identity.
4. إغلاق run صراحة كـ completed/abandoned، ثم تنظيف الذاكرة مع الاحتفاظ بسجل الاستئناف اللازم.
5. إضافة keys لكل route يعتمد على args:
   - `workout-session:<sessionKey>`
   - `training-session:<runId>`
   - `report-detail:<reportId>`
   - `weekly-report:<programId>:<week>`
6. إضافة اختبارات A -> B -> Back -> A للتأكد من عدم تسرب state.

أدلة الكود:

- `kmp-app/feature/library/src/commonMain/kotlin/com/movit/feature/library/WorkoutFlowModels.kt:128-160`
- `kmp-app/feature/library/src/commonMain/kotlin/com/movit/feature/library/MovitLibraryRoutes.kt:141-172`
- `kmp-app/feature/training/src/commonMain/kotlin/com/movit/feature/training/MovitTrainingRoutes.kt:72-106`
- `kmp-app/feature/reports/src/commonMain/kotlin/com/movit/feature/reports/MovitReportsRoute.kt:51-56`

## 5. إصلاحات التدفق P1

### P1.1 - جاهزية Start ومنع الانتظار غير المتوقع

الحالة الحالية:

- `Train` يعمل prefetch للجلسة الأساسية، لكنه لا يعرض نتيجة الجاهزية في UI.
- Shell يعيد `syncEffectivePlan` عند الضغط من `Train`.
- `ExercisePrepareViewModel.requestTrainingStart()` يمكن أن ينفذ fetch + sync + full refresh.
- شاشة التحضير تختفي بالكامل لصالح loading state أثناء ensure.
- لا توجد حالة `Launching` مشتركة تمنع double tap وpush مكرر.

الإصلاح المطلوب:

1. نقل preflight إلى `WorkoutSessionViewModel`/Launch Coordinator عند فتح الجلسة.
2. تجهيز configs لكل exercises في snapshot، وليس أول exercise فقط.
3. عرض CTA بالحالات: Preparing, Ready, Offline ready, Retry.
4. عند Start استخدم snapshot جاهزة cache-only وأنشئ run مرة واحدة.
5. الحفاظ على محتوى الشاشة أثناء refresh/preparing مع progress صغير داخل dock.
6. منع أي launch مكرر حتى نجاح أو فشل الطلب الحالي.

أدلة الكود:

- `kmp-app/feature/train/src/commonMain/kotlin/com/movit/feature/train/MovitTrainViewModel.kt:145-181`
- `kmp-app/feature/shell/src/commonMain/kotlin/com/movit/feature/shell/MovitAppShellViewModel.kt:368-417`
- `kmp-app/feature/library/src/commonMain/kotlin/com/movit/feature/library/ExercisePrepareViewModel.kt:188-292`
- `kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/repository/TrainingConfigEnsure.kt:30-79`

### P1.2 - إزالة مسار التقدم القديم وتحديد معنى ضغط Exercise

الحالة الحالية:

- `TrainingSessionFlowCoordinator` يدير workout كاملة داخل TrainingSession واحدة.
- عند Finish ما زال Shell يستدعي `WorkoutRunProgressStore` باستخدام `route.startExerciseIndex`.
- هذا index لا يمثل بالضرورة exercise الحالية عند الإنهاء اليدوي.
- الضغط على Exercise داخل `WorkoutSessionRoute` يفتح `ExercisePrepareRoute` بنفس workoutId، ثم Start يقرأ progress القديم أو يبدأ من 0؛ لا توجد دلالة صريحة هل هذا preview أم تغيير لنقطة البداية.

الإصلاح المطلوب:

1. جعل `WorkoutRunStore + TrainingSessionFlowCoordinator` المصدر الوحيد للتقدم.
2. إزالة `WorkoutRunProgressStore` من المسار الحديث بعد ترحيل اختبارات الاستئناف.
3. تعريف `ExercisePrepareMode` typed:
   - `SoloStart`
   - `WorkoutPreview(runDraftId, exerciseId)`
   - `WorkoutFirstExercise(runId)`
4. في `WorkoutPreview`، Back يرجع للتفاصيل وStart workout يرجع إلى CTA الجلسة، ولا يبدأ من exercise المضغوطة ضمنيا.
5. لو كان Start from here مطلوبا مستقبلا، يكون command مستقل وواضح ويعيد بناء snapshot من ذلك الموضع.

أدلة الكود:

- `kmp-app/feature/shell/src/commonMain/kotlin/com/movit/feature/shell/MovitInnerHost.kt:291-324`
- `kmp-app/feature/library/src/commonMain/kotlin/com/movit/feature/library/MovitLibraryRoutes.kt:162-174`
- `kmp-app/feature/library/src/commonMain/kotlin/com/movit/feature/library/ExercisePrepareViewModel.kt:244-257`

### P1.3 - تعديلات workout تكون حقيقية وواضحة

الحالة الحالية:

- steppers داخل `TrainTodayCard` تحفظ sets في `remember` فقط ولا تمررها إلى ViewModel أو session.
- كل session card في `Train` تستخدم نفس `onPrimaryAction` بدلا من target خاص بها.
- `WorkoutSessionViewModel` يحفظ تعديلات program فقط؛ Explore workout يرجع Success دون persistence حقيقي.
- الحفظ يبدأ في `persistScope` منفصل، ويمكن إظهار Start أثناء `isSaving`.
- فشل الحفظ يحول الشاشة ذات المحتوى إلى full error state.

القرار:

- إزالة steppers من `Train`; بطاقة اليوم read-only.
- كل التعديل يتم داخل `WorkoutSessionRoute` المشتركة.
- تخصيص Explore workout يكون `Customize this run` ويحفظ في run draft محلي، ولا يوحي بتعديل template الأصلية.
- تخصيص Program day يستمر عبر repository الحالي، مع انتظار النتيجة قبل Start أو إطلاق نفس snapshot المحلية مع outbox واضح.

الإصلاح المطلوب:

1. استبدال `StartWorkoutClicked` العام بـ `SessionClicked(target)` أو `StartSession(target)`.
2. نقل تعديلات sets/reps/rest إلى canonical draft.
3. استخدام structured concurrency داخل ViewModel وتسلسل save operations.
4. تعطيل Start أثناء save، أو جعل save-and-start عملية ذرية واحدة.
5. إبقاء المحتوى ظاهرا عند فشل action مع banner/snackbar وRetry.

أدلة الكود:

- `kmp-app/feature/train/src/commonMain/kotlin/com/movit/feature/train/components/TrainTodayCard.kt:119-168`
- `kmp-app/feature/train/src/commonMain/kotlin/com/movit/feature/train/MovitTrainViewModel.kt:86-93`
- `kmp-app/feature/library/src/commonMain/kotlin/com/movit/feature/library/WorkoutSessionViewModel.kt:136-143`
- `kmp-app/feature/library/src/commonMain/kotlin/com/movit/feature/library/WorkoutSessionViewModel.kt:495-503`

### P1.4 - Back، الخروج، والاستئناف

الحالة الحالية:

- Back داخل TrainingSession ينفذ stop ثم NavigateBack مباشرة.
- يوجد resume journal للتمرين، لكن قرار الخروج لا يشرح للمستخدم ما سيحدث.
- run snapshot الكاملة موجودة في memory cache، لذلك استئناف workout كاملة بعد process death غير مضمون.

الإصلاح المطلوب:

1. Back أثناء setup غير النشط: رجوع عادي.
2. Back أثناء training/rest: dialog أو bottom sheet بثلاثة أفعال واضحة:
   - Continue workout.
   - Save and exit.
   - End workout.
3. حفظ progress على مستوى exercise/set/block مع run snapshot.
4. عند فتح Train أو نفس workout، إظهار Resume كفعل أساسي وRestart كفعل ثانوي.
5. استعادة الجلسة بعد background/process recreation واختبارها على Android وiOS.

أدلة الكود:

- `kmp-app/feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingSessionViewModel.kt:194-207`
- `kmp-app/feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingSessionViewModel.kt:1408-1447`
- `kmp-app/feature/library/src/commonMain/kotlin/com/movit/feature/library/WorkoutFlowModels.kt:128-142`

## 6. خطة شاشة Train

### 6.1 ترتيب المحتوى

الترتيب المقترح حسب الأهمية:

1. حالة اليوم + الإجراء الأساسي الوحيد.
2. workout اليوم كـ read-only preview مختصر.
3. week strip مع اختيار يوم.
4. readiness مختصرة، وتظهر فقط إذا كانت مبنية على بيانات حقيقية ومؤثرة.
5. آخر تقرير/التقدم.
6. إجراءات ثانوية: Explore وReports وPreferences.

لا تعرض أكثر من CTA أساسي لنفس الهدف في أول viewport.

### 6.2 حالات Train

#### ActivePlan

- CTA: Start workout أو Resume workout.
- الضغط يفتح `WorkoutSessionRoute` المحددة، ولا يقفز إلى الكاميرا.
- session preview لا يحتوي steppers محلية.
- لو المحتوى جاهز والتحديث يعمل، يظل CTA قابلا للاستخدام من النسخة المحلية.

#### InProgress

- Resume هو CTA الأساسي.
- يظهر progress حقيقي: exercise الحالية، set، والوقت التقريبي المتبقي.
- Restart فعل ثانوي مع confirmation.

#### CompletedToday

- CTA يحمل اسما دقيقا: View day report أو View workout report حسب `ReportTarget`.
- لا يفتح Weekly Report تحت تسمية عامة مثل View summary.
- يظهر Next scheduled day كpreview ثانوي.

#### RestDay / ActiveRecovery

- لا يعرض Start workout كأنه مطلوب.
- يعرض recovery guidance ثم Browse recovery workouts كفعل اختياري يمر عبر Explore Workout path.
- preview الغد يأتي من backend، ولا يستخدم نصا/رقما ثابتا.

#### NoPlan

- CTA أساسي واحد: Explore programs.
- يمكن إظهار 1-2 featured programs، وكل card تفتح `ProgramDetailRoute`.
- إزالة التكرار بين banner وToday card وQuick Actions لنفس الإجراء.

#### NoAssessment / ReassessmentDue

- CTA: Start assessment.
- بعد النجاح يتم refresh لـ Home/Train ثم عرض program recommendations.
- Back/فشل assessment يعيد Train مع state مفهومة، لا شاشة فارغة.

#### ProgramComplete

- View journey يفتح report مناسب.
- Choose next program يفتح `ProgramListRoute`.
- لا يستخدم الزران نفس callback الاحتياطي.

### 6.3 week strip واليوم المحدد

الإصلاحات المطلوبة:

1. حفظ `selectedWeekNumber` و`selectedDayNumber` لا index فقط.
2. عدم تصفير اختيار المستخدم عند وصول Fresh بعد Cached.
3. كل day action يحمل target الخاص بذلك اليوم.
4. Today وInProgress: Start/Resume.
5. Missed/Needs catch-up: Start catch-up عبر نفس `WorkoutSessionRoute`.
6. Completed: فتح day/workout report الصحيح.
7. Upcoming: preview فقط، بلا CTA مضلل.
8. Rest: recovery details فقط.
9. prefetch يبدأ عند تحديد يوم startable، وليس لليوم الأساسي فقط.

أدلة الكود:

- `kmp-app/feature/train/src/commonMain/kotlin/com/movit/feature/train/MovitTrainViewModel.kt:62-73`
- `kmp-app/feature/train/src/commonMain/kotlin/com/movit/feature/train/MovitTrainViewModel.kt:139-181`
- `kmp-app/feature/train/src/commonMain/kotlin/com/movit/feature/train/TrainApiMapper.kt:340-398`

## 7. تحسينات P2

### P2.1 - Rest وUp Next

الـ Rest الحالية تعرض timer واسم التالي وtip فقط.

المطلوب:

- التمييز بين rest بين sets وrest بين exercises.
- عرض `Set x of y` أو اسم التمرين التالي.
- عرض target القادم: reps أو duration، والوزن عند وجوده.
- thumbnail/media صغيرة اختيارية من config الجاهزة.
- إبقاء Skip و+15s/Pause متاحة ومتسقة.
- عدم إنشاء route منفصلة؛ تبقى state داخل TrainingSession.

أدلة الكود:

- `kmp-app/feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingSessionPanels.kt:271-316`
- `kmp-app/feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingSessionViewModel.kt:1197-1209`

### P2.2 - Program preview بدون تمرير زائد

القرار النهائي:

- الاحتفاظ بـ `ProgramWeekStrip` لكل الأسابيع.
- عرض تفاصيل الأسبوع المحدد فقط مع days وsessions.
- اختيار الأسبوع الحالي افتراضيا للمشترك، والأسبوع الأول لغير المشترك، مع احترام `initialWeekNumber` عند فتح رابط محدد.
- إضافة summary لكل أسبوع داخل strip أو expandable overview خفيف.
- إزالة card الأسبوع الإضافي العشوائي الناتج عن `.take(1)`.
- غير المشترك يرى محتوى البرنامج كاملا عبر الاختيار، وليس كل الأسابيع مفتوحة في نفس الوقت.
- Start/Enroll يستخدم `isStarting` فعليا، يعطل CTA، ويعرض progress داخل dock.
- فشل enrollment يبقي تفاصيل البرنامج مع رسالة action، ولا يستبدلها full-screen error.

أدلة الكود:

- `kmp-app/feature/library/src/commonMain/kotlin/com/movit/feature/library/ProgramDetailScreen.kt:225-260`
- `kmp-app/feature/library/src/commonMain/kotlin/com/movit/feature/library/ProgramDetailModels.kt:132-152`
- `kmp-app/feature/library/src/commonMain/kotlin/com/movit/feature/library/ProgramDetailViewModel.kt:869-900`

### P2.3 - freshness والأخطاء الموحدة

المطلوب:

1. استخدام stale-while-revalidate في Explore مثل Train وWorkoutSession.
2. الحفاظ على query/filter/scroll/selected day أثناء fresh updates.
3. full-screen loading/error فقط عند عدم وجود محتوى صالح.
4. عند وجود cached content، استخدام banner/snackbar للتحديث والفشل.
5. تخزين pending data revision أثناء inner route وتطبيقه عند الرجوع.
6. توحيد النصوص والإجراءات للحالات Offline, Retry, Preparing, Saving, Upload pending.

أدلة الكود:

- `kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/repository/SharedExploreRepository.kt:13-50`
- `kmp-app/feature/train/src/commonMain/kotlin/com/movit/feature/train/SharedTrainRepository.kt:13-41`
- `kmp-app/feature/shell/src/commonMain/kotlin/com/movit/feature/shell/MovitAppShellRoute.kt:87-95`

## 8. خطة التنفيذ

### المرحلة A - صحة التشغيل

- [ ] إضافة canonical `WorkoutRunSnapshot` وtyped targets.
- [ ] إصلاح mapping للـ rest/phase/variant/duration/weight.
- [ ] إضافة `runId` وعزل uploads/reports/journals.
- [ ] بناء session report عام لكل workout.
- [ ] إصلاح report navigation والعودة.
- [ ] إضافة ViewModel keys لكل routes المعتمدة على args.

شرط الإغلاق: workout المعروضة في التفاصيل تطابق snapshot والمحرك والتقرير field-by-field.

### المرحلة B - إطلاق موحد

- [ ] إنشاء Launch Coordinator و`LaunchReadiness`.
- [ ] توصيل Explore وTrain بنفس `WorkoutLaunchRequest`.
- [ ] إزالة network setup من final Start.
- [ ] منع double launch.
- [ ] إزالة `WorkoutRunProgressStore` من flow الحديث.
- [ ] فصل Workout Preview عن Start workout.

شرط الإغلاق: نفس workout من Explore وTrain تنتج نفس snapshot ونفس route sequence؛ الاختلاف فقط source وreturn target.

### المرحلة C - Train UX

- [ ] إزالة التعديلات المحلية من `TrainTodayCard`.
- [ ] جعل كل session/day action typed ومحددا.
- [ ] إضافة Resume وCatch-up وReport targets الصحيحة.
- [ ] الحفاظ على اختيار الأسبوع واليوم خلال refresh.
- [ ] تبسيط CTA hierarchy لكل dashboard status.
- [ ] prefetch عند تحديد أي يوم قابل للتشغيل.

شرط الإغلاق: كل CTA في Train يصف الوجهة الفعلية ويفتح العنصر الذي ضغطه المستخدم تحديدا.

### المرحلة D - الاستمرارية والصقل

- [ ] Save and exit / End workout confirmation.
- [ ] durable run resume عبر process recreation.
- [ ] RestPanel موسعة.
- [ ] Program selected-week experience.
- [ ] stale content/error behavior موحد.
- [ ] accessibility وanalytics للأحداث الأساسية.

## 9. الاختبارات المطلوبة

### Unit وMapping

- DTO فيه WARMUP/MAIN/COOLDOWN وexplicit Rest blocks يصل إلى snapshot بلا فقد.
- `restBetweenSetsMs != restAfterExerciseMs` يظل مختلفا.
- `variantIndex` وduration target وweightPerSet محفوظة.
- time-based exercise لا تتحول إلى 12 reps.
- run لنفس workout مرتين ينتج runId وworkoutGroupId مختلفين.

### ViewModel

- Cached ثم Fresh لا يمسح اختيار الأسبوع/اليوم في Train.
- ضغط session B يفتح target B وليس أول session.
- Missed day يفتح catch-up target.
- double tap على Start ينتج navigation واحدة.
- فشل save/enroll/sync يبقي المحتوى ويعرض retry مناسب.
- Explore workout متعدد التمارين ينتج session report.

### Navigation

- Explore Workout -> Report -> Back/Done.
- Train Today -> Report -> Done -> Train refreshed.
- Program Day -> Report -> Program/Train حسب return target.
- Back من report لا يعيد فتح report تلقائيا.
- فتح Workout A ثم B لا يعيد state A.
- فتح Report A ثم B لا يعيد report A.

### Resume وLifecycle

- background أثناء exercise/rest ثم resume.
- Save and exit ثم Resume من Train.
- process recreation في منتصف workout.
- End workout ينظف run المفتوحة ولا يضم uploads لمحاولة لاحقة.

### UI وAccessibility

- CTA واحدة أساسية فوق fold لكل حالة Train.
- النصوص تطابق الفعل: day report / week report / resume / catch-up.
- focus order وcontent descriptions للـ week strip وrest controls وdialogs.
- screenshot tests بالعربية والإنجليزية، RTL/LTR، وأحجام خط كبيرة.

## 10. Definition of Done

لا يعتبر التدفق مكتملا إلا عند تحقق الآتي:

- لا توجد بيانات workout تسقط بين API وtraining engine.
- لا يوجد network setup غير متوقع بعد الضغط على Start.
- لا توجد controls تغير الشكل فقط دون تغيير الجلسة.
- كل run معزولة بهوية مستقلة وقابلة للاستئناف.
- Train وExplore يستخدمان نفس المسارات ونفس snapshot ونفس التقرير.
- report النهائي يمثل النطاق الصحيح ويمكن الخروج منه بلا loop.
- Back والخروج والاستئناف سلوكهم واضح ومختبر.
- cached/fresh/offline/error states لا تمسح عمل المستخدم أو اختياره.
- جميع اختبارات المراحل A-D ناجحة على Android وiOS للأجزاء المشتركة.
