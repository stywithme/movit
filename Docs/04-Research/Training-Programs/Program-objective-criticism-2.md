Findings
الفصل بين ProgressionState وOverride ما زال غير محسوم، وهذه أهم نقطة تحتاج حسم.
الوثيقة تبني الطبقة التنفيذية على مصدرين منفصلين: حالة التدرج، وتعديلات المستخدم/المدرب. هذا ممتاز. لكنك في موضع آخر جعلت UserProgramOverride نفسه يستقبل نتائج محرك التدرج، وهذا يعيد خلط المصدرين من جديد. لو استمر هذا الغموض، ستصبح أولوية الدمج، والتتبع، والتراجع، والتفسير للمستخدم مربكة.

Program-Blueprint.md
Lines 1153-1168
### الكيان الجديد 2: `UserProgramOverride`
كيان منفصل يرتبط بـ `UserProgram`.
يحمل التعديلات الخاصة بالمستخدم مثل:
- استبدال تمرين
- تعديل وصفة عنصر
- تخطي عنصر
- إضافة عنصر
- مصدر التعديل:
  - مستخدم
  - محرك التدرج
  - مدرب
والسبب أن هذا الخلط يعيد نفس المشكلة الموجودة الآن فعلاً في الكود الحالي، حيث محرك التدرج لا يكتفي بتحديث الحالة بل يلمس PlannedWorkoutItem نفسه:


progression.service.ts
Lines 637-675
async materializeToNextItem(userProgramId: string, exerciseId: string) {
  const prisma = await getPrisma();
  const state = await prisma.userProgramExerciseProgressionState.findUnique({
    where: { userProgramId_exerciseId: { userProgramId, exerciseId } },
  });
  if (!state) return;
  const nextItems = await prisma.programPlannedWorkoutItem.findMany({
    where: {
      exerciseId,
      session: {
        day: {
          week: {
            program: {
              userPrograms: { some: { id: userProgramId, isActive: true } },
            },
          },
        },
        reports: { none: { status: 'completed' } },
      },
    },
    orderBy: { sortOrder: 'asc' },
  });
  for (const item of nextItems) {
    await prisma.programPlannedWorkoutItem.update({
      where: { id: item.id },
      data: {
        ...(state.currentTargetReps != null ? { targetReps: state.currentTargetReps } : {}),
        ...(state.currentWeightKg != null ? { weightKg: state.currentWeightKg } : {}),
        ...(state.currentTargetDuration != null ? { targetDuration: state.currentTargetDuration } : {}),
        ...(state.currentTargetSets != null ? { sets: state.currentTargetSets } : {}),
        ...(state.currentDifficultyCode != null ? { difficultyCode: state.currentDifficultyCode } : {}),
        isPersonalized: true,
      },
    });
  }
},
توصيتي الحاسمة:

UserProgramExerciseProgressionState = للتدرج التلقائي فقط
UserProgramOverride = للتعديلات البشرية فقط
ترتيب الدمج: Template -> ProgressionState -> ManualOverrides
هذا يحل التناقض ويجعل المنظومة قابلة للفهم.
منطق التسكين يحتاج Contract structured أصغر وأكثر صراحة من الموجود في الوثيقة.
الوثيقة ممتازة في الجانب الفلسفي، لكنها تعطي حرية كبيرة جداً لصانع البرنامج، وفي المقابل لا تحدد بوضوح ما هي الحقول الإلزامية المنظمة التي يعتمد عليها Selection Algorithm. هذا خطر؛ لأن التسكين التلقائي لا يستطيع أن يعتمد على entryRecommendations وexitRecommendations أو النصوص الإرشادية وحدها.
الكود الحالي ينجح لأنه يعتمد على حقول structured واضحة:


prescription.service.ts
Lines 233-274
const programs = await prisma.program.findMany({
  where: {
    isPublished: true,
    deletedAt: null,
    type: classification.requiredType,
    levelRangeMin: { lte: overallLevel },
    levelRangeMax: { gte: overallLevel },
  },
  orderBy: { prescriptionPriority: 'asc' },
});
let matches = programs;
if (classification.safetyGateCodes.length > 0) {
  matches = matches.filter((p) => {
    const contra = p.contraindications || [];
    return !contra.some((c) => classification.safetyGateCodes.includes(c));
  });
}
if (classification.targetDomain) {
  const domainMatches = matches.filter(
    (p) => p.targetDomain === classification.targetDomain,
  );
  if (domainMatches.length > 0) {
    matches = domainMatches;
  }
}
if (classification.targetRegions.length > 0) {
  const regionMatches = matches.filter((p) => {
    const regions = p.targetRegions || [];
    return regions.some((r) => classification.targetRegions.includes(r));
  });
وموديل Program الحالي فيه بالفعل بعض هذه الحقول:


schema.prisma
Lines 348-363
tags                  Json?
createdBy             String?
updatedBy             String?
createdAt             DateTime               @default(now())
updatedAt             DateTime               @updatedAt
deletedAt             DateTime?
contraindications     String[]               @default([])
entryCriteria         Json?
exitCriteria          Json?
levelRangeMax         Int                    @default(5)
levelRangeMin         Int                    @default(1)
nextProgramId         String?
prerequisiteProgramId String?
prescriptionPriority  Int                    @default(100)
targetDomain          String?
targetRegions         String[]               @default([])
توصيتي:
اجعل الوثيقة تفرّق بوضوح بين:

Structured eligibility fields: هذه إلزامية للتسكين
Narrative recommendations: هذه للشرح فقط
الحد الأدنى الذي يجب أن يبقى structured:

programType
trainingGoal
levelRange
contraindications أو safety codes
targetEquipment
targetDomain/targetRegions أو بديل structured أبسط
الوثيقة ما زالت أعلى قليلاً من الحد المثالي في سطح البيانات الذي يجب على صانع البرنامج تحريره.
المعمارية العامة بسيطة، لكن سطح الـ authoring نفسه ما زال قريباً من التضخم. المشكلة ليست في عدد الـ entities فقط، بل في عدد المحاور التي سيتعامل معها الـ Admin/Coach عند كل عنصر وكل تمرين.

Program-Blueprint.md
Lines 1215-1229
### على `PlannedWorkoutItem`
- `adaptationFocus` (اختياري)
- `role`
- `intent`
- `progressionMode` (اختياري كـ override)
- `allowedSubstitutions`
### على `Exercise`
- `movementPattern`
- `loadCapability`
- `familyKey` (مرجع إلى `ExerciseFamily`)
- `familyOrder`
- `difficultyLevel`
هذا منطقي مفاهيمياً، لكن عملياً ما زال يحمل عبئاً تحريرياً ملحوظاً. أخطر نقطتين هنا:

adaptationFocus قد يتحول إلى Goal ثانٍ بشكل غير مقصود
difficultyLevel قريب جداً وظيفياً من familyOrder + difficulty
التبسيط الذي أنصح به ويعطي نفس القيمة تقريباً:

في v1 اجعل الإجباري فقط: movementPattern, loadCapability, role
اجعل intent اختياريًا
اجعل adaptationFocus مشتقاً ومخفياً إلا في الحالات الاستثنائية
أرجئ difficultyLevel، واكتفِ بـ familyOrder داخل العائلة + difficulty الموجود للعرض
لا تفعّل ExerciseFamily إلا لعائلات curated قليلة فعلاً
الوثيقة متسقة مع ACSM في القلب، لكنها تتجاوز ACSM في بعض الطبقات ويجب التصريح بذلك بوضوح أكبر.
الجزء المتعلق بـ STRENGTH, HYPERTROPHY, POWER, GENERAL_HEALTH، ونطاقات الشدة، ومبدأ عدم الحاجة إلى الفشل، وحماية الـ template، وتقليل الـ periodization المعقد: هذا كله متسق جداً مع ACSM 2026.
لكن عناصر مثل:

THERAPEUTIC
MOTOR_CONTROL
SKILL
ExerciseFamily
orchestration الخاص بالاختبارات وrole = TEST
هذه توسعات تصميمية منطقية للمنتج، وليست استنتاجات مباشرة من ACSM نفسها. هذا ليس خطأ، لكنه يجب أن يُذكر صراحةً حتى لا تبدو الوثيقة وكأنها تستند إلى ACSM في مناطق هي في الحقيقة اجتهاد Movit جيد، لا أكثر.

منطق الاختبارات جيد، لكن يحتاج سقفاً واضحاً حتى لا يتحول إلى نظام موازٍ للبرنامج.
الوثيقة في هذا الجزء جيدة جداً إجمالاً، خصوصاً فصلها بين:
قرار السلامة
قرار التوصيف
قرار البرمجة والتقدم
لكن عند الدمج بين:

weekType = INTRO/TEST
role = TEST
التقييم المستمر من الجلسات
إعادة التقييم النهائي
هناك خطر أن يصبح عندك "نظام اختبارات" كامل متداخل في البرنامج رغم أنك تعلن أنك لا تريد Test Engine منفصلاً.

توصيتي:
ضع قاعدة صريحة:

التقييم الأولي يبقى خارج البرنامج دائماً
TEST داخل البرنامج يستخدم فقط عندما توجد نتيجة قرارية واضحة ستتغير بناءً عليه
لا يُنشأ TEST week أو TEST item إلا إذا كان سيغيّر فعلاً: التسكين، أو الانتقال، أو التدرج، أو الأمان
أسئلة مفتوحة / افتراضات
أفترض أن entryRecommendations وexitRecommendations في Program-Blueprint.md تفسيرية وإرشادية، وليست المصدر الأساسي لفلترة البرامج آلياً.
أفترض أن ExerciseFamily ستبدأ كطبقة curated صغيرة، لا كتصنيف إجباري على كل التمارين في المكتبة.
أفترض أن COACH هنا هو نوع Ownership/Origin للبرنامج، وليس Role/Permissions مكتملة في النظام حالياً.
لو أي واحد من هذه الافتراضات غير صحيح، فدرجة التعقيد سترتفع مباشرة.

التقييم العام
المنطق متماسك إلى حد كبير، وأراه أقرب إلى الصواب من أي نسخة سابقة في المحادثة. أقوى ما فيه:

فصل الطبقات الثلاث
حماية الـ template
رفض المحركات الزائدة
الحفاظ على المرونة البشرية
مواءمة جيدة مع ACSM في صلب المقاومة والتدرج
لكن عندي حكم واضح: المخطط الآن جيد جداً فلسفياً، لكنه ما زال يحتاج 4 tightenings قبل أن يصبح “مرجعاً نهائياً نظيفاً”:

حسم الحدود بين ProgressionState وOverride
فرض حد أدنى structured للتسكين
تقليل سطح metadata الإجباري على صانع البرنامج
وسم ما هو ACSM-backed وما هو Movit design extension
ما يمكن تبسيطه الآن بدون خسارة حقيقية
اجعل adaptationFocus hidden-by-default، وليس محور تحرير ظاهر دائماً.
ألغِ difficultyLevel في المرحلة الأولى، واكتفِ بـ familyOrder + difficulty.
فعّل ExerciseFamily فقط لعائلات واضحة مثل: push-up ladder, plank ladder, squat ladder, mobility ladder.
أبقِ TEST داخل البرنامج نادراً ومقصوداً، لا pattern افتراضي.
ما أراه يجب أن يبقى يدوياً بالكامل
اختيار التمارين نفسها
ترتيب الجلسة النهائي
تحديد البدائل المسموحة
قرار استخدام ExerciseFamily أو تجاهلها
قرار Fork
قبول أو رفض البرنامج التالي المقترح
الخلاصة المختصرة
الـ Blueprint متسق مع المبادئ الحاكمة، ومتسق علمياً في القلب، وغير محتاج هدم.
التحسينات المطلوبة ليست جذرية في الرؤية، بل جذرية فقط في ضبط الحدود وتقليل حمل النمذجة.

