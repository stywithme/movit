قريت الدراسة وملفات الـ roadmap/spec، وبصيت على الموجود فعلا في الـ backend وAndroid. الخلاصة عندي: اتجاهك صح جدا، والدراسة تدي المشروع “عمود فقري” علمي واضح، لكن محتاجين نترجمها إلى 3 طبقات: ملف تدريبي للعميل، تصنيف واضح للبرامج، واختبارات تقود التسكين والتطور.

الخلاصة
أنا شايف إننا ما نعملش “onboarding أسئلة” وخلاص. نعمل Coaching Profile للعميل. ده يبقى مصدر الحقيقة لكل قرارات التسكين والتقدم:

البيانات الأساسية:
الوزن، الطول، تاريخ الميلاد أو العمر، الجنس اختياري، اللغة، وحدة القياس، هدفه الأساسي، هدف ثانوي اختياري.

البيانات التدريبية:
هل يتمرن حاليا، بقاله قد إيه، كام يوم في الأسبوع، خبرته مع المقاومة، هل يعرف يستخدم أوزان، أقصى وقت للجلسة، مستوى الثقة، الالتزام المتوقع.

القيود:
إصابات، ألم حالي، PAR-Q، مناطق ممنوع الضغط عليها، معدات متاحة، مكان التدريب: بيت/جيم/مختلط.

التقييم:
Body Scan الموجود حاليا، ومعاه اختبار بسيط حسب الهدف. مش لازم كل الناس تعمل كل الاختبارات.

أنواع البرامج
الموجود حاليا عندنا Program, UserProgram, ActivePlan, ProgramWeek/Day/PlannedWorkout/Item، وده أساس كويس. لكن محتاجين نفصل بين نوع البرنامج من ناحية الاستخدام وtype الحالي اللي معناه training/mobility/therapeutic.

أقترح 3 تصنيفات واضحة:

System Program
ده برنامج النظام. يتعمل بواسطة الأدمن فقط، قابل للتسكين التلقائي، وله entryCriteria, exitCriteria, nextProgramId, levelRange, targetDomain, targetRegions, وهدف تدريبي واضح. ده اللي الـ prescription engine يستخدمه تلقائيا.

General Program
برنامج عام يتعمل بواسطة أدمن أو مدرب، ويستخدمه أكتر من متدرب يدويًا. ممكن يبقى public أو خاص بمدرب/مؤسسة. لا يدخل في التسكين التلقائي إلا لو الأدمن علّمه كـ auto-assignable.

Custom Program
برنامج خاص بمتدرب واحد. يحصل لما المتدرب يبني برنامج من الصفر، أو يعدل برنامج System/General بشكل يغيّر هويته. هنا لازم نعمل fork: البرنامج الجديد يبقى private، وعنده forkedFromProgramId.

النقطة المهمة جدا: تقدم مستخدم واحد لا يجب أن يغير template البرنامج الأصلي. حاليا محرك الـ progression عنده خطر واضح لأنه يقدر يحدّث PlannedWorkoutItem نفسه، وده لو البرنامج مستخدم من ناس كتير ممكن يلمس template مش نسخة المستخدم. الأفضل نخلي التعديلات في UserProgramExerciseProgressionState أو جدول overrides خاص بالمستخدم، والـ API يرجع “effective plan” بعد دمج template + overrides.

الأهداف التدريبية
من الدراسة، الأهداف الأساسية التي تستحق تدخل في النظام:

STRENGTH
قوة. الأولوية: الحمل. نحتاج ≥80% 1RM تقريبا، 2+ جلسة أسبوعيا، ROM كويس، والتمارين المهمة في بداية الجلسة.

HYPERTROPHY
تضخم. الأولوية: الحجم الأسبوعي. نحتاج حساب sets لكل muscle group، والهدف العملي ≥10 مجموعات/أسبوع/عضلة. الحمل أوسع، والفشل مش ضروري.

POWER
قدرة/سرعة. الأولوية: سرعة الحركة ونية الأداء. 30-70% 1RM، حجم قليل إلى متوسط، ووقف/راحة لو velocity loss عالي.

GENERAL_HEALTH أو FUNCTIONAL_FITNESS
صحة عامة ووظيفة. الأهم الالتزام، الأمان، والجسم كله مرتين أسبوعيا. الاختبارات الوظيفية هنا مهمة: chair stand, balance, TUG/gait لو قابلين للتنفيذ.

الاختبارات
عندنا أساس ممتاز: AssessmentTemplate, BodyScanResult, UserLevelProfile, وreassessment. أنا شايف نوسعهم بدل ما نرميهم.

نحتاج 5 أنواع اختبارات:

Safety Gate
PAR-Q، ألم، قيود، إشارات توقف. لو فشل: لا auto program قوي، يتحول therapeutic أو مراجعة مدرب/طبيب.

Placement Test
Body Scan الحالي: mobility/control/symmetry/safety/body score. ده يحدد level وlimiting factors.

Goal-Specific Test
للقوة: تقدير 1RM من submax أو من أول جلسات وزن آمنة.
للتضخم: لا نحتاج اختبار تضخم مباشر، نحتاج volume tolerance وتاريخ تدريب.
للقدرة: velocity/jump أو حركة سريعة آمنة.
للوظيفة: chair stand/balance/TUG-like حسب قابلية التطبيق.

Progression Test
مش اختبار منفصل دايمًا. جلسات التدريب نفسها تنتج metrics: formScore, ROM, completion, est1RM, velocityLoss.

Exit / Next Program Test
بعد نهاية البرنامج أو plateau. يعيد Body Scan مختصر + اختبار الهدف، ثم يقرر: نفس المستوى، برنامج تالي، أو برنامج تصحيحي.

ما نستفيد به من الدراسة مباشرة
لا نبني engine معقد للـ periodization الآن. الدراسة لا تعطيه أفضلية حاسمة عند وجود progressive overload.

لا نخلي الفشل العضلي هدف. الأفضل RIR 2-3 أو velocity loss كعلامة “كفاية مجهود”.

لا نضخم أهمية الراحة وTUT في القرارات الأساسية. نخليهم تفاصيل/راحة/UX، مش محور progression.

نركز على:
trainingGoal, %1RM/intensity, weekly sets per muscle, ROM, velocityLoss, trainingIntent, وexercise order.

خطة التنفيذ المقترحة
المرحلة الأولى: تثبيت الدومين
إضافة UserTrainingProfile, trainingGoal, وحقول program origin/visibility/ownership/forking. وإصلاح مسار progression بحيث يكون user-specific بالكامل.

المرحلة الثانية: Onboarding حقيقي
بعد التسجيل: profile أساسي، هدف، تدريب حالي، معدات، وقت، safety، ثم Body Scan. بعدها prescription v2 يختار System Program مناسب.

المرحلة الثالثة: Program taxonomy
System/General/Custom في الأدمن والموبايل. أي تعديل من المستخدم يتحول إلى override أو custom fork حسب حجمه.

المرحلة الرابعة: Scientific progression
تفعيل intensityPercentage فعليا، حفظ velocityLoss في backend، إضافة RPE/RIR اختياري، وحساب weekly volume per muscle.

المرحلة الخامسة: Tests → Next Program
Formalize entry/exit criteria، وتخلي نهاية البرنامج تقود تلقائيا إلى reassessment ثم البرنامج التالي.

رأيي: نبدأ من الدومين والموديل قبل الواجهات. لو ثبتنا UserTrainingProfile + ProgramOrigin + user-specific progression + trainingGoal، باقي النظام هيركب عليه بنظافة بدل ما نفضل نزود patches فوق نظام مش محدد الهوية.




الخلاصة التصميمية

أنا شايف إن أهم قرار هنا: لا نعامل البرنامج كـ “قائمة تمارين”، بل كـ وصفة تدريبية كاملة لها هدف، جمهور، جرعة، تقدم، اختبارات دخول وخروج، ونسخ مختلفة حسب المتدرب. الموجود عندنا حاليًا قريب من الهيكل: Program -> Week -> Day -> Planned Workout -> PlannedWorkoutItem -> Exercise في schema.prisma، لكن ينقصه طبقة “المعنى التدريبي” التي تجعل البرنامج احترافيًا.

الهرم الذي أقترحه يكون كالتالي:

Training Program
  -> Phase / Block
    -> Week / Microcycle
      -> Day
        -> Planned Workout
          -> Planned Workout Block
            -> Exercise Slot
              -> Set Prescription
                -> Exercise
1. البرنامج التدريبي

البرنامج هو أكبر وحدة، ويجب أن يجيب على: “لمن؟ لماذا؟ لمدة كم؟ بأي مستوى خطورة؟ وما معيار النجاح؟”

المواصفات المطلوبة:

النوع: SYSTEM, GENERAL/COACH, CUSTOM.
الهدف الأساسي: قوة، ضخامة، قدرة، صحة عامة، تحمل، تأهيل/تصحيح.
الجمهور المستهدف: مستوى، عمر تقريبي، قيود صحية، خبرة تدريبية.
مدة البرنامج، عدد الأسابيع، أيام التدريب أسبوعيًا، متوسط مدة الجلسة.
متطلبات الدخول: تقييمات، مستوى، إصابات ممنوعة، معدات لازمة.
معايير الخروج: نتائج اختبار، جودة حركة، التزام، تحسن قوة/ROM/اتزان.
سياسة التقدم: أي متغير يتغير أولًا؟ reps/load/sets/difficulty.
سياسة التسكين: متى يرشحه النظام؟ ومتى يمنعه؟
البرنامج التالي المقترح بعد النجاح أو الفشل أو التعثر.
الناقص تصميميًا:

البرنامج يحتاج TrainingGoal واضح وليس فقط نوع عام.
يحتاج Versioning لأن برامج النظام لا يجب أن تتغير تحت أرجل المستخدمين.
يحتاج علاقة أصل/نسخة: البرنامج المخصص يجب أن يكون fork من برنامج سابق.
يحتاج “وصفة أسبوعية” واضحة: حجم، كثافة، تكرار، راحة، وليس فقط أيام وتمارين.
2. المرحلة / Block

دي وحدة ناقصة ومهمة جدًا. الأسبوع وحده لا يكفي. المرحلة تقول: “الأسابيع 1-4 هدفها تأسيس، 5-8 بناء، 9 اختبار/انتقال”.

المواصفات:

اسم المرحلة: Foundation, Build, Intensify, Deload, Test.
نطاق الأسابيع.
هدف المرحلة.
حدود الحجم والكثافة.
هل فيها اختبار في النهاية؟
هل تسمح بالتقدم السريع أم المحافظ؟
الناقص حاليًا:

عندنا ProgramWeek لكن لا يوجد معنى أعلى يربط مجموعة أسابيع بهدف تدريبي واحد.
بدون Phase سيصبح التقدم أسبوعي فقط، لا “خطة تدريبية” مفهومة.
3. الأسبوع / Microcycle

الأسبوع ليس مجرد رقم. هو توزيع الجرعة التدريبية.

المواصفات:

نوع الأسبوع: عادي، تخفيف، اختبار، إعادة تأهيل.
إجمالي الأيام التدريبية.
إجمالي الحجم لكل عضلة أو نمط حركة.
توزيع الشدة: ثقيل/متوسط/خفيف.
أيام الراحة أو الاستشفاء.
شرط الانتقال للأسبوع التالي.
الناقص:

Weekly targets: عدد sets لكل muscle group أو movement pattern.
منطق يمنع تضخم حجم التدريب بدون قصد، خصوصًا في hypertrophy.
4. اليوم

اليوم هو “حاوية نية” وليس مجرد ترتيب.

المواصفات:

نوع اليوم: تمرين، راحة، active recovery، اختبار.
تركيز اليوم: upper/lower/full body/push/pull/squat/hinge.
مستوى الإجهاد المتوقع.
هل اليوم قابل للتبديل في الجدول؟
علاقته باليوم السابق واللاحق.
الناقص:

نحتاج نية اليوم حتى لا ينتج النظام أيامًا متضاربة: مثل hinge ثقيل بعد lower ثقيل بدون تعافٍ.
5. الجلسة التدريبية

الجلسة هي الوحدة التي ينفذها المستخدم فعليًا.

المواصفات:

هدف الجلسة: قوة، ضخامة، حركة، اختبار، تصحيح.
مدة تقديرية.
ترتيب منطقي: إحماء، تنشيط، رئيسي، مساعد، كور، تهدئة.
قواعد الإكمال: ماذا يعني أن الجلسة نجحت؟
قواعد التعديل: لو الأداء ضعيف، ماذا يتغير؟
الناقص:

عندنا planned workout تقريبًا، لكن لا يوجد تقسيم داخلي احترافي للجلسة.
الجلسة يجب ألا تكون flat list من exercises.
6. Planned Workout Block

دي وحدة أرى أنها ضرورية جدًا.

أنواعها:

Warm-up / RAMP
Activation
Main Lift / Main Work
Accessory
Corrective
Conditioning
Core
Cool-down
Assessment/Test
المواصفات:

نوع البلوك.
هدفه.
ترتيبه.
هل إجباري أم اختياري؟
intensity range.
completion rule.
القيمة:

تمنع خلط التمارين.
تجعل ترتيب الجلسة علميًا: power قبل strength، المركب قبل العزل، التصحيح/الإحماء قبل الحمل العالي، الكور المجهد قرب النهاية.
7. Exercise Slot

دي أهم نقطة تصميمية: الـ Exercise شيء، واستخدامه داخل جلسة شيء آخر.

مثال: Squat كتمرين في المكتبة شيء، لكن Squat في برنامج قوة كمين لعضلات الرجلين بوزن 85% 1RM شيء مختلف تمامًا.

المواصفات:

exerciseId.
الدور: main, accessory, warmup, corrective, test.
الهدف التدريبي من هذا التمرين داخل الجلسة.
sets/reps/duration/rest.
intensity: %1RM أو RPE/RIR أو وزن مقترح.
tempo أو intent: controlled, explosive, eccentric focus.
بدائل مسموحة.
regressions/progressions.
هل يحتسب في volume لأي عضلات؟
شروط الإيقاف: ألم، form score منخفض، velocity loss عالي.
الناقص:

PlannedWorkoutItem يحتاج معنى تدريبي أوسع من مجرد وصفة أداء.
يجب ألا نعدّل item الأصلي للمستخدم أثناء التقدم؛ الأفضل وجود “effective prescription” أو user override. خطر موجود في منطق التقدم الحالي داخل progression.service.ts.
8. Set Prescription

المستوى الأدق من Exercise Slot.

المواصفات:

set type: warmup, ramp, work, backoff, AMRAP, test.
target reps أو duration.
load rule: ثابت، تصاعدي، %1RM، RPE.
rest بعد الجولة.
RIR/RPE target.
tempo.
side: left/right/bilateral لو التمرين أحادي.
success criteria.
الناقص:

weightPerSet كأرقام فقط غير كافية. نحتاج set structure لأن جولة warm-up غير جولة العمل.
9. Exercise

التمرين نفسه يجب أن يكون “تعريف حركة”، لا وصفة تدريب.

المواصفات:

الاسم والتعليمات.
counting method: reps/hold/time/distance.
supports weight.
movement pattern: push/pull/squat/hinge/lunge/carry/rotation.
primary/secondary muscles.
joints involved.
equipment.
difficulty.
contraindications.
regression/progression links.
supported metrics: ROM, symmetry, stability, velocity, TUT.
هل يصلح كاختبار؟ هل يصلح كإحماء؟ هل يصلح main lift؟
الناقص:

التصنيف الحالي يحتاج طبقة biomechanical taxonomy، وليس فقط muscle أو difficulty.
نحتاج علاقة بدائل ذكية: “لو لا يوجد بار، استخدم dumbbell goblet squat”، “لو knee pain، لا تستخدم lunge عميق”.
الوحدات الإضافية الضرورية

أنا شايف إن هذه الوحدات يجب إضافتها حول الهرم، لأنها تجعل البرنامج قابل للتسكين والتخصيص:

Client Training Profile: العمر، الطول، الوزن، الهدف، الخبرة، المعدات، الوقت المتاح، الإصابات، التدريب الحالي، تفضيلات الالتزام.
PAR-Q / Medical Readiness: منفصل عن onboarding، ويعمل كبوابة أمان.
Assessment Battery: مجموعة اختبارات حسب الهدف والمستوى، لا اختبار واحد عام.
Assessment Result: نتيجة كل اختبار مع تفسيرها ومستوى المستخدم في كل محور.
Goal Prescription Profile: القواعد العلمية لكل هدف: reps, sets, intensity, rest, progression priority.
Movement Taxonomy: قاموس رسمي للأنماط الحركية والعضلات والمفاصل.
Substitution Rules: بدائل وتمارين ممنوعة حسب معدات/ألم/مستوى.
Program Assignment Rule: لماذا اختار النظام هذا البرنامج لهذا المستخدم؟
Program Version / Fork Lineage: لحماية برامج النظام، وتتبع النسخ المخصصة.
Progression Policy / Quality Gate: موجود جزئيًا، لكن يجب ربطه بالهدف والبلوك والتمرين.
تصوري النهائي

البرنامج الاحترافي عندنا يجب أن يكون مكوّنًا من 3 طبقات:

Template علمي ثابت: برنامج نظام أو برنامج عام مبني يدويًا.
Assignment ذكي: يختار البرنامج بناءً على profile + assessment + goal + safety.
Effective User Plan: نسخة تنفيذية للمستخدم، تتغير بالتقدم دون تلويث البرنامج الأصلي.
وبكده نكون حافظنا على فلسفة الـ Charter: لا نولد برامج بالـ AI، لا نعقد periodization بلا داعي، لكن نبني مكتبة برامج قوية، قابلة للتسكين، قابلة للتخصيص، ومبنية على جرعة تدريبية واضحة.

اقتراحي للخطوة التالية: نثبت سويًا “قاموس الوحدات” النهائي، ثم نرسم لكل وحدة fields المطلوبة والعلاقات بينها قبل أي تعديل برمجي.




