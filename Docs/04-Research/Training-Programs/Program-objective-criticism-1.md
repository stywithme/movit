تحليل شامل وموضوعي لـ Program-Blueprint.md

قرأت الوثيقة كاملة (1551 سطر) بعناية، وراجعتها مقابل:

الـ Charter المعتمد سابقاً
ACSM 2026 + البحثين (Program-research-1.md و Program-Research-2.md)
الكود الحالي (prescription.service.ts، progression.service.ts، archetype-defaults.ts، schema.prisma)
1. هل المنطق متماسك؟
نعم، متماسك جداً في جوهره.

الـ Blueprint يمثل تطوراً طبيعياً وناضجاً لما اتفقنا عليه في الـ Charter. أهم نقاط القوة:

الفصل الثلاثي الواضح (Client Layer → Template Layer → Effective Plan Layer) هو أقوى جزء في الوثيقة. هذا حل معماري ممتاز يحل مشكلة حقيقية موجودة حالياً (أن progression.service.ts يلمس الـ Template).
القاعدة الذهبية (Effective Plan = Template + Overrides + ProgressionState) منطقية وعملية.
الرفض الصريح للـ AI Generator، Periodization Engine المعقد، Session Block entity، Rules Engine، وFailure-based prompts — كلها قرارات ممتازة ومتسقة مع ACSM 2026.
التركيز على مرونة صانع البرنامج (Admin/Coach) مع حماية النظام — هذا يلبي طلبك السابق بشكل جيد.
2. هل فيه تعقيد مبالغ فيه؟
نعم، في بعض الأجزاء هناك تعقيد زائد.

أبرز النقاط المبالغ فيها:

adaptationFocus + role + intent + progressionMode + effectiveProgressionMode — هذا كثير جداً على ProgramSessionItem.

role ممتاز.
intent مفيد.
أما adaptationFocus و progressionMode (والاشتقاق التلقائي له) فهما يميلان للنظرية أكثر من اللازم. قد يؤدي إلى عبء كبير على من يبني البرامج.
Exercise Family + familyKey + familyOrder + difficultyLevel — هذا أيضاً يميل للـ over-engineering.

الفكرة جيدة لـ Bodyweight و Mobility و Hold exercises.
لكن جعلها نظاماً رسمياً لكل التمارين (مع كيان ExerciseFamily) قد يصبح ثقيلاً في الإدارة.
weekType = INTRO/TEST + role = TEST + exitRecommendations — يمكن تبسيطه. وجود TEST كـ weekType وكـ role في نفس الوقت يخلق بعض الالتباس.

3. هل يمكن تبسيط مع نتائج مماثلة أو أفضل؟
نعم، يمكن تبسيط ملحوظ دون فقدان القيمة.

اقتراحي للنسخة المبسطة (الموصى بها):

الحقول المطلوبة على ProgramSessionItem (بدلاً من 5 حقول):
role (WARMUP / ACTIVATION / MAIN / ACCESSORY / CORRECTIVE / COOLDOWN / TEST) — إلزامي
intent (STANDARD / POWER / ECCENTRIC / VELOCITY_BASED) — اختياري
progressionOverride (Json بسيط) — يُستخدم فقط عند الحاجة لتجاوز الاشتقاق التلقائي
إزالة:

adaptationFocus (يُشتق من role + intent + trainingGoal البرنامج)
progressionMode كحقل منفصل (يُشتق تلقائياً في معظم الحالات)
Exercise Family:
نستخدمها فقط للتمارين التي تتدرج بالصعوبة (Bodyweight, Hold, Mobility, Motor Control).
لا نجعلها إلزامية للتمارين ذات LOAD_REPS أو LOAD_DURATION.
familyKey يكون String بسيط (مثل push_horizontal_bodyweight) بدون كيان ExerciseFamily منفصل في المرحلة الأولى.
weekType:
نكتفي بـ NORMAL | DELOAD | TEST فقط.
INTRO يُدمج داخل TEST أو NORMAL مع role = TEST.
هذا التبسيط يحافظ على 85-90% من القيمة العلمية مع تقليل التعقيد بنسبة كبيرة.

4. أي نطاقات يجب تركها يدوية 100%؟
يجب أن تبقى يدوية تماماً:

اختيار التمارين وترتيبها داخل الجلسة
تحديد role و intent
كتابة entryRecommendations و exitRecommendations (نص + شروط)
تحديد أيام الراحة و weekType
تحديد البدائل (allowedSubstitutions)
قرارات المدرب/الCoach في برامج COACH
النظام يقترح فقط (ولا يفرض):

نطاقات reps/sets/intensity حسب trainingGoal
ترتيب افتراضي حسب role
اقتراح البرنامج التالي
اقتراح وزن/تكرارات قبل الجلسة (بناءً على est1RM)
5. هل متسق مع المبادئ الحاكمة؟
متسق بنسبة 90%+.

ممتاز في: Safety First, Adherence > Perfection, Evidence-Based, Individualization.
جيد في: Specificity و Progressive Overload.
النقطة الوحيدة التي تحتاج ضبط: بعض أجزاء adaptationFocus + progressionMode تميل للنظرية أكثر مما يخدم المبدأ 5 (البساطة والالتزام). هذا يمكن أن يؤدي إلى تعقيد يضر بالمدربين.
6. هل متسق مع الأساس العلمي (ACSM 2026)؟
نعم، متسق جداً في الجوهر.

يعطي أولوية واضحة لـ Intensity, Volume per muscle, ROM, Order of exercises, Power intent, Eccentric focus.
يرفض الفشل ك هدف (RIR/velocityLoss).
يرفض تعقيد Periodization.
يركز على Adherence و Minimal Effective Dose.
يفصل جيداً بين Safety Gate, Profiling, Programming.
النقاط الوحيدة التي تحتاج ضبطاً طفيفاً:

adaptationFocus يجب ألا يصبح taxonomy معقدة (ACSM لا تذهب لهذا العمق).
Weekly volume per muscle مهم، لكن لا يجب أن يتحول إلى engine معقد — حساب بسيط في التقارير يكفي.
7. تحسينات وتعديلات جذرية مطلوبة (اقتراحي)
التعديل الجذري الموصى به:

تبسيط ProgramSessionItem إلى 3 حقول رئيسية فقط: role, intent, progressionOverride (Json).
إزالة adaptationFocus أو جعله مشتقاً تلقائياً.
جعل ExerciseFamily اختيارياً وخفيفاً (String فقط في المرحلة الأولى).
توحيد TEST: إما weekType = TEST أو role = TEST — لا الاثنين.
إضافة حقل coachingNotes Json على Program و ProgramSessionItem — يسمح للمدرب بكتابة توجيهات نصية حرة (هذا أهم من كثير من الحقول النظرية).
اقتراح هيكل نهائي مبسط لـ ProgramSessionItem:

model ProgramSessionItem {
  id                    String   @id @default(uuid())
  sessionId             String
  sortOrder             Int
  exerciseId            String?
  role                  Role     // WARMUP, MAIN, ACCESSORY...
  intent                Intent?  // STANDARD, POWER, ECCENTRIC...
  sets                  Int?
  targetReps            Int?
  targetDurationSeconds Int?
  restBetweenSetsMs     Int?
  weightKg              Float?
  weightPerSet          Json?
  progressionOverride   Json?    // override للـ mode أو القيم
  allowedSubstitutions  Json?
  coachingNotes         Json?    // نصيات للمدرب/المتدرب
  // ... 
}
الخلاصة النهائية
الـ Blueprint ممتاز في الرؤية والالتزام بالمبادئ، لكنه مبالغ في التعقيد في طبقة ProgramSessionItem و Exercise Family.

التوصية: نأخذ 80% منه كما هو، ونبسّط الـ 20% المتبقية (خاصة adaptationFocus, progressionMode, ExerciseFamily ككيان).

