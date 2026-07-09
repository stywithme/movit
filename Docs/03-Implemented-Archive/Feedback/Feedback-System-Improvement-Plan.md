> **Status:** `ARCHIVED` — implemented or superseded; not current product truth.
> **Current SSOT:** [`Docs/00-Active-Reference/Engine/training-engine.md`](../../00-Active-Reference/Engine/training-engine.md) (`FeedbackRouter`, `FrameFeedbackEmitter`)
> **Archived:** 2026-06-22

# Feedback System Improvement Plan

## الهدف من الوثيقة

هذه الوثيقة تلخص تقييمي لنظام الـ feedback الحالي في تطبيق Android، وتقترح خطة تحسين عملية للنظام بدون كسر السلوك الموجود. الهدف ليس فقط إصلاح bugs متفرقة، بل الوصول إلى نظام أوضح، أسهل في الاختبار، وأكثر عدلا في اختيار الرسائل التي يسمعها أو يراها المستخدم أثناء التدريب.

النظام الحالي فيه أفكار قوية: فصل نسبي بين الـ engine والـ delivery، دعم camera/video mode، دعم الرسائل المترجمة والصوت المخزن، ووجود scheduler يمنع spam. لكن المشكلة الأساسية أن قرار إطلاق الرسائل موزع بين أكثر من طبقة:

- `TrainingEngine` و`FrameFeedbackEmitter` يفلتران الأحداث مبكرا.
- `FeedbackManager` يحول الأحداث إلى `FeedbackSignal`.
- `FeedbackScheduler` يقرر delivery/cooldown/priority.
- بعض رسائل setup/system/countdown تتجاوز المسار الطبيعي وتستخدم TTS مباشرة.
- يوجد `MessageOrchestrator` قديم غير مستخدم لكنه يوثق قواعد مختلفة.

النتيجة: بعض الرسائل المهمة قد لا تصل، وبعض الأنواع معرفة في الكود لكنها لا تستخدم فعليا، وسلوك الأولويات صعب التنبؤ عند تزاحم الرسائل.

## حالة التنفيذ

تم تنفيذ الخطة الأساسية داخل تطبيق Android:

- تم توحيد قرار الأولوية داخل `FeedbackScheduler`.
- تم تثبيت القنوات: camera mode = صوت فقط، video mode = رسائل نصية فقط.
- تم جعل `NORMAL/Good` و`PAD/Accept` رسائل قابلة للإطلاق حسب الأولوية بدلا من بقائها صامتة.
- تم تحويل `DANGER` إلى `CRITICAL` في مسار أخطاء المفاصل.
- تم منع `REPLACE_LOWER` من استبدال رسالة أعلى منه.
- تم تمرير setup/system/countdown عبر scheduler بدلا من الاعتماد على direct TTS فقط.
- تم تعديل random messages بحيث لا يمنعها `TIP` وحده.
- تم توحيد aliases القادمة من الباك: `good -> normal` و`accept/acceptable -> pad`.
- تم تقليل throttling المبكر في الـ engine ليصبح candidate-rate limit، بينما يظل delivery cooldown داخل scheduler.
- تم حذف `MessageOrchestrator.kt` بعد التأكد من عدم استخدامه.
- تم إضافة/تحديث اختبارات للـ scheduler، state aliases، candidate throttling، وإصلاح اختبار `JointErrorCollection`.

التحقق النهائي:

- `:app:testDebugUnitTest` نجح.
- `:app:assembleDebug` نجح.
- تم تشغيل Gradle باستخدام JBR الخاص بـ Android Studio لأن Java الافتراضي على الجهاز كان Java 25 وغير متوافق مع Kotlin/Gradle في هذا المشروع.

## التقييم المختصر للنظام الحالي

### نقاط القوة

- يوجد taxonomy واضح نسبيا للأحداث: joint quality، position checks، visibility، hold، rep، target، setup، system.
- `FeedbackScheduler` يمثل خطوة صحيحة لأنه يجعل delivery mode-aware: camera يستخدم الرسائل الصوتية، وvideo يستخدم الرسائل النصية.
- النظام يدعم localization وaudio cache عبر `LocalizedText`.
- الـ engine يجمع معلومات مفيدة للتقرير، وليس فقط للرسائل اللحظية.
- position checks لها `minErrorFrames` و`cooldownMs`، وهذا يقلل flicker.

### نقاط الضعف

- يوجد مصدران للأولوية: `FeedbackEvent.priority` و`FeedbackSeverity`. القرار الحقيقي مبني على `FeedbackSeverity`، لذلك `FeedbackEvent.priority` مربك وغير مؤثر غالبا.
- أخطر حالة للمفاصل `DANGER` لا تصل كـ `CRITICAL` في المسار الحالي.
- يوجد double throttling: مرة في `FrameFeedbackEmitter` ومرة في `FeedbackScheduler`. هذا قد يجعل رسالة تتسجل cooldown قبل أن تظهر فعليا.
- `REPLACE_LOWER` حاليا يسمح لبعض الرسائل الأقل خطورة باستبدال correction نشط بسبب `forceAudible`.
- رسائل setup/system لا تستفيد بالكامل من scheduler، وقد تختفي إذا كان TTS غير مفعل.
- random motivation/tips يتم منعه عند وجود أي `positionErrors`، والقائمة تشمل errors/warnings/tips، لذلك tip بسيط قد يمنع رسائل التحفيز.

## الأخطاء أو المخاطر المنطقية المطلوبة إصلاحها

### 1. `DANGER` لا يأخذ أولوية حرجة

الوضع الحالي:

- `FrameFeedbackEmitter.emitThrottledStateMessages()` يتجاهل `DANGER` و`WARNING`.
- بعدها `JointErrorCollection` يطلع `JointQualityContent.Error`.
- `FeedbackManager.handleJointQualityError()` يحول `WARNING` إلى `WARNING`، وأي شيء آخر إلى `ERROR`.

المشكلة:

- `DANGER` يصبح `ERROR` وليس `CRITICAL`.
- هذا يخالف تعريف النظام الذي يعتبر `DANGER` safety-first.
- قد لا يقطع رسالة أقل أهمية، وقد لا يأخذ نفس نمط التنبيه.

المطلوب:

- إذا كان `error.state == JointState.DANGER` يجب تحويله إلى `FeedbackSeverity.CRITICAL`.
- يجب إضافة test صريح لهذا السلوك.

### 2. تسجيل cooldown قبل التأكد من delivery

الوضع الحالي:

- `FrameFeedbackEmitter.shouldEmitJointError()` و`shouldEmitPositionEvent()` يسجلان last time بمجرد السماح بإرسال event.
- بعد ذلك قد يرفض `FeedbackScheduler` الرسالة بسبب active signal أو cooldown أو max repeats.

المشكلة:

- الرسالة قد لا تصل للمستخدم، لكنها تعتبر "اتطلقت" داخل engine throttle.
- هذا يخلق silent gaps خصوصا عند تزاحم أكثر من correction.

المطلوب:

- جعل cooldown النهائي داخل `FeedbackScheduler` فقط.
- أو تعديل `FrameFeedbackEmitter` ليعمل كـ candidate emitter بدون تسجيل delivery time.
- إذا احتجنا frame-level throttling للأداء، يكون باسم مختلف مثل `minCandidateIntervalMs` ولا يحسب كـ user delivery.

### 3. `REPLACE_LOWER` يستبدل رسائل أعلى منه أحيانا

الوضع الحالي:

- `forceAudible + REPLACE_LOWER` يسمح بالاستبدال طالما الرسالة النشطة ليست `CRITICAL`.
- هذا قد يسمح لـ target/motivation باستبدال warning/error correction.

المطلوب:

- `REPLACE_LOWER` يجب أن يستبدل فقط إذا كانت severity الجديدة أعلى من الحالية.
- لو نحتاج سلوك قهري، نضيف policy جديدة واضحة مثل `FORCE_REPLACE_NON_CRITICAL` وتستخدم فقط في حالات session-ending أو safety.

### 4. بعض أنواع الرسائل معرفة لكنها لا تطلق فعليا

أنواع dead أو شبه dead:

- `JointQualityContent.StateMessage` لحالات `WARNING/DANGER/TRANSITION`.
- فرع `JointState.DANGER -> FeedbackSeverity.CRITICAL` داخل `handleJointStateMessage`.
- `FeedbackKind.COUNTDOWN` معرف لكن countdown لا يمر عبر scheduler.
- `MessageOrchestrator` غير مستخدم.

المطلوب:

- إما إزالة dead paths أو توحيدها داخل تصميم جديد.
- التوثيق والكود يجب أن يعكسا نفس الحقيقة.

## الأهداف من النظام الجديد المقترح

1. **لا تضيع الرسائل الحرجة:** أي safety/danger/visibility pause يجب أن يصل بأعلى أولوية.
2. **تقليل الـ spam بدون إسكات مهم:** التكرار يقل تدريجيا، لكن لا يمنع أول رسالة مهمة.
3. **قرار delivery في مكان واحد:** scheduler هو صاحب القرار النهائي للرسالة التي ستخرج، ثم يتم اختيار القناة حسب وضع الاستخدام.
4. **فصل detection عن delivery:** الـ engine يقول "ما الذي يحدث؟"، والـ feedback layer تقرر "ماذا نعرض الآن؟".
5. **سلوك واضح بين camera وvideo:** camera = voice-only أثناء التدريب، video = text-only أثناء المراجعة.
6. **اختبار كل نوع رسالة:** كل category لها test يثبت أنها تطلق أو تسكت في الحالة الصحيحة.
7. **سهولة تعديل الـ copy:** مصادر الرسائل تظل من exercise JSON/message assignments/system registry، لكن decision logic لا يعتمد على مصدر النص.
8. **عدم إسكات الرسائل منخفضة الأولوية:** `NORMAL/Good` و`PAD/Accept` يجب أن تدخلا نفس طابور الأولويات؛ إذا لم توجد رسالة أهم منهما، يتم إطلاقهما بالقناة المناسبة.

## النظام الجديد المقترح

### 1. Taxonomy موحد للرسائل

بدلا من تداخل `FeedbackEvent.priority` و`FeedbackSeverity`، نعتمد taxonomy واحد:

```text
FeedbackCandidate
├─ source
│  ├─ JOINT
│  ├─ POSITION
│  ├─ SCENE
│  ├─ VISIBILITY
│  ├─ SETUP
│  ├─ REP
│  ├─ HOLD
│  ├─ TARGET
│  ├─ RANDOM
│  └─ SYSTEM
│
├─ semantic type
│  ├─ correction
│  ├─ safety
│  ├─ setup_guidance
│  ├─ progress
│  ├─ success
│  ├─ motivation
│  └─ tip
│
├─ severity
│  ├─ CRITICAL
│  ├─ ERROR
│  ├─ WARNING
│  ├─ TIP
│  ├─ INFO
│  ├─ SUCCESS
│  └─ MOTIVATION
│
└─ lifecycle
   ├─ candidate detected
   ├─ delivered
   ├─ suppressed
   └─ resolved/reset
```

### 2. Scheduler هو صاحب القرار الوحيد

الـ engine لا يجب أن يقرر أن الرسالة "اتقالت". هو فقط يرسل candidate مؤكد:

```text
Engine
  -> detects joint/position/visibility/setup condition
  -> emits FeedbackCandidate
FeedbackScheduler
  -> checks active priority
  -> checks cooldown/repeats
  -> selects channel
  -> returns DeliveryPlan
FeedbackManager
  -> executes plan
  -> records delivered/suppressed metrics
```

### 3. فصل active issue عن delivered message

نحتاج فرق واضح بين:

- issue نشط حاليا: مثل knee warning مستمر.
- message تم توصيلها للمستخدم: صوت في camera mode أو نص في video mode وصل فعلا.

هذا يمنع المشكلة الحالية حيث يتم احتساب cooldown رغم أن الرسالة لم تصل.

### 4. ترتيب الأولويات المقترح

```text
CRITICAL  = safety / visibility paused / joint danger
ERROR     = strong correction / position error / hold failed
WARNING   = mild correction / visibility warning / setup issue
TIP       = low-risk improvement
INFO      = neutral progress
SUCCESS   = rep pulse / hold resumed
MOTIVATION = encouragement / streak / random motivation
```

ملاحظات:

- `CRITICAL` يستطيع interrupt.
- `ERROR` يستطيع replace فقط لما يكون active أقل منه.
- `WARNING` ينتظر slot ولا يقطع error/critical.
- كل رسالة مؤكدة تدخل scheduler كـ candidate، حتى لو كانت منخفضة الأولوية.
- `TIP/INFO/MOTIVATION` لا تقطع رسالة أعلى منها، لكنها تطلق عندما لا توجد رسالة أعلى أو مساوية مستحقة في نفس اللحظة.
- `NORMAL/Good` تصنف كـ `INFO`: رسالة إيجابية/تطمينية منخفضة الأولوية، لكنها قابلة للنطق إذا لم يوجد correction أو safety cue أهم.
- `PAD/Accept` تصنف كـ `TIP`: رسالة تحسين خفيفة، قابلة للنطق إذا لم يوجد warning/error/critical أو رسالة أعلى مستحقة.
- `TARGET/HOLD COMPLETED` يمكنها استخدام policy خاصة بعد نهاية التدريب، لكنها لا تزاحم safety.

### 5. قاعدة خاصة لـ `NORMAL/Good` و`PAD/Accept`

هذه الرسائل لا يجب أن تكون صامتة افتراضيا. المطلوب أن تعامل كرسائل عادية داخل نظام الأولويات:

```text
DANGER / critical safety  -> يفوز على الجميع
WARNING / ERROR           -> يفوز على PAD وNORMAL
PAD / Accept              -> ينطلق لو لا توجد رسالة أهم
NORMAL / Good             -> ينطلق لو لا توجد رسالة أهم
```

التفاصيل المقترحة:

- في camera mode: يتم نطق `NORMAL` و`PAD` صوتيا بجمل قصيرة، مع cooldown وتكرار محدود.
- في video mode: تظهر `NORMAL` و`PAD` كرسائل نصية فقط أثناء مراجعة الأداء.
- لا توجد رسائل نصية أو اهتزاز في camera mode.
- لا يوجد صوت أو اهتزاز في video mode.
- لا يتم احتسابها delivered إلا إذا خرجت فعلا من scheduler.
- إذا أرسل الباك أسماء بديلة، يجب توحيدها قبل التخزين:
  - `good` -> `normal`
  - `accept` أو `acceptable` -> `pad`

## التعديلات المطلوبة خطوة بخطوة

### المرحلة 1: إصلاحات أمان وسلوك فوري

1. تعديل `FeedbackManager.handleJointQualityError()`:
   - `DANGER -> CRITICAL`.
   - `WARNING -> WARNING`.
   - باقي الحالات غير المتوقعة -> ERROR.

2. تعديل `FeedbackScheduler.REPLACE_LOWER`:
   - لا يستبدل إلا إذا `newSeverity > activeSeverity`.
   - إضافة policy منفصلة لو مطلوب forced replacement.

3. إضافة tests:
   - danger joint produces critical delivery plan.
   - motivation cannot replace active warning/error.
   - critical can replace warning/error.

4. مراجعة voice/text mapping:
   - camera mode: كل الرسائل التي يختارها scheduler تخرج كرسائل صوتية فقط.
   - video mode: كل الرسائل التي يختارها scheduler تخرج كرسائل نصية فقط.

### المرحلة 2: إزالة double throttling

1. تعديل `FrameFeedbackEmitter`:
   - لا يسجل `lastPositionEventTimes` و`lastJointErrorTimes` على أنها delivery.
   - إما يحذف cooldown منه تماما، أو يتحول إلى candidate-rate limiter فقط.

2. جعل `FeedbackScheduler` يسجل delivery بعد التأكد أن `DeliveryPlan.shouldDeliver == true`.

3. إضافة diagnostic counters:
   - detected candidates.
   - delivered messages.
   - suppressed by active.
   - suppressed by cooldown.
   - suppressed by max repeats.

4. إضافة tests على حالة:
   - first correction active يمنع second correction.
   - second correction لا يدخل engine cooldown إذا لم يصل.

### المرحلة 3: توحيد الأولويات والـ taxonomy

1. تقليل الاعتماد على `FeedbackEvent.priority`.
   - إما إزالته تدريجيا.
   - أو تحويله إلى derived value من `FeedbackSeverity`.

2. إنشاء mapper مركزي:

```kotlin
FeedbackEvent -> FeedbackSignal
```

بدل أن تكون mapping rules متفرقة داخل handlers كثيرة في `FeedbackManager`.

3. توحيد أسماء حالات المفاصل القادمة من الباك:
   - `normal` و`good` يعاملان كحالة واحدة.
   - `pad` و`accept` و`acceptable` يعاملون كحالة واحدة.
   - أي alias غير معروف يتم تسجيل warning له بدل تجاهله بصمت.

4. توثيق كل category:
   - source.
   - severity.
   - activeKey.
   - cooldownGroup.
   - default interrupt policy.
   - allowed channels.

### المرحلة 4: إدخال setup/system/countdown في نفس المسار

1. جعل `speakSetupGuidance()` لا يخرج مبكرا قبل بناء candidate.
   - يبني signal.
   - scheduler يقرر هل الرسالة مستحقة الآن أم لا.
   - قناة الإخراج تكون voice في camera وtext في video.

2. جعل `speakSystemCue()` يعمل بنفس الطريقة.

3. استخدام `FeedbackKind.COUNTDOWN` فعليا أو إزالته.
   - الأفضل: countdown يبقى scheduled لكن له policy خاصة `INTERRUPT`.

4. video mode:
   - setup/system تظهر كرسائل نصية فقط.
   - camera mode لا يعرض نص ولا اهتزاز؛ إذا اختار scheduler الرسالة فهي تخرج صوتيا فقط.

### المرحلة 5: تحسين position/random behavior

1. فصل flows:
   - `positionErrors`
   - `positionWarnings`
   - `positionTips`

2. random messages يجب أن تمنع فقط عند:
   - active joint warning/danger.
   - active position error/warning.
   - visibility/setup/system warning.

3. لا تجعل `TIP` وحده يمنع motivation دائما.

4. مراجعة `FeedbackSeverity.INFO` و`FeedbackSeverity.TIP` في camera mode:
   - في النظام الحالي `INFO/NORMAL` لا ينطق غالبا، و`TIP/PAD` لا ينطق غالبا إلا في strict.
   - القرار المقترح: `INFO` و`TIP` ينطلقان حسب الأولوية إذا لم توجد رسالة أهم، مع cooldown وتكرار محدود.
   - `PAD/Accept` يجب أن يكون أوضح من `NORMAL/Good` لأنه يعني أن الأداء مقبول لكن على الحافة.
   - لا يتم حصر `PAD` في strict فقط؛ strict يمكن أن يقلل cooldown أو يزيد التكرار، لكنه ليس شرطا أساسيا للإطلاق.
   - في camera mode يكون الإطلاق صوتيا فقط، وفي video mode يكون نصيا فقط.

### المرحلة 6: تنظيف legacy/dead code

1. تم حذف `MessageOrchestrator.kt` بعد التأكد أنه غير مستخدم.
2. تم تقليل dead behavior الخاص بالـ tones/haptic داخل scheduler.
3. تم تحديث comments التي توضح أن `DANGER` أصبح critical فعليا.
4. تم تحديث هذه الوثيقة بحالة التنفيذ والتحقق النهائي.

### المرحلة 7: الاختبارات المطلوبة

Tests أساسية:

- `JointDangerIsCriticalTest`
- `SchedulerDoesNotCountSuppressedAsDeliveredTest`
- `ReplaceLowerOnlyReplacesLowerSeverityTest`
- `SetupCueRoutesThroughSchedulerTest`
- `PositionTipDoesNotBlockRandomMotivationTest`
- `NormalAndPadFollowPriorityDeliveryTest`
- `StateAliasMappingGoodAcceptTest`
- `CameraVoiceModeDeliveryMatrixTest`
- `VideoModeDeliveryMatrixTest`

اختبارات integration:

- تمرين فيه primary danger + position warning في نفس frame: danger يفوز.
- تمرين فيه position warning مستمر: أول رسالة تصل، التكرار يقل.
- تمرين فيه `PAD/Accept` فقط: ينطلق كـ tip إذا لا توجد رسالة أعلى.
- تمرين فيه `NORMAL/Good` فقط: ينطلق كـ info إذا لا توجد رسالة أعلى.
- video mode مع tips فقط: تظهر كرسائل نصية، ولا تختفي لمجرد أنها منخفضة الأولوية.
- camera voice mode: critical/error/warning/tip/info تخرج كرسائل صوتية حسب الأولوية وليس كنص أو اهتزاز.

## تأثير الخطة على تجربة المستخدم

### قبل التحسين

- المستخدم قد يفوت تحذير خطر لأنه لم يصنف critical.
- بعض الرسائل قد لا تظهر بسبب تزاحم داخلي غير واضح.
- setup أو system cues قد تختفي إذا تغيرت إعدادات الصوت.
- motivation/tips قد تكون غير منتظمة أو نادرة بسبب شروط حجب واسعة.

### بعد التحسين

- رسائل الخطر ستصل دائما وبشكل واضح.
- التصحيحات لن تتكلم فوق بعضها، لكن لن تضيع بصمت.
- المستخدم في camera mode سيحصل على رسائل صوتية فقط لأنه أثناء التدريب بعيد عن الموبايل.
- المستخدم في video mode سيحصل على رسائل نصية فقط لأنه أنهى التمرين ويمسك الموبايل لمراجعة الأداء.
- tips والتحفيز سيظهران في أوقات مناسبة بدل أن ينافسا correction.
- تقليل spam سيكون أكثر ذكاء: أول مرة واضحة، التكرار أهدأ، ثم silence مدروس.

## التأثير على الصيانة والتطوير

- إضافة رسالة جديدة ستحتاج تعريف mapping واحد فقط بدل توزيع المنطق.
- QA يستطيع اختبار مصفوفة واضحة: نوع الرسالة x الشدة x mode.
- تقارير bugs ستكون أسهل لأن scheduler سيعرف سبب suppression.
- حذف legacy code يقلل الالتباس بين ما هو موثق وما هو مستخدم.

## توصية التنفيذ

أنصح بعدم إعادة كتابة النظام بالكامل مرة واحدة. الأفضل تنفيذ الخطة على ثلاث دفعات:

1. **دفعة أمان:** إصلاح `DANGER`, `REPLACE_LOWER`, وإضافة tests.
2. **دفعة توحيد delivery:** نقل cooldown النهائي إلى scheduler وتوحيد mapping.
3. **دفعة تجربة المستخدم:** setup/system/countdown routing، position/random refinement، وتنظيف legacy.

بهذا الشكل نصل إلى نظام feedback أكثر أمانا ووضوحا بدون مخاطرة كبيرة على تجربة التدريب الحالية.
