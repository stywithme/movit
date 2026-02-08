import type { PrismaClient } from '@prisma/client';

export async function seedAttributes(prisma: PrismaClient) {
  // ============================================
  // ATTRIBUTES (Types)
  // ============================================
  const attributes = [
    { code: 'category', name: { ar: 'قسم التمرين', en: 'Exercise Category' }, description: 'Exercise categories', isSystem: true, sortOrder: 1 },
    { code: 'muscle', name: { ar: 'العضلة المستهدفة', en: 'Target Muscle' }, description: 'Target muscles', isSystem: true, sortOrder: 2 },
    { code: 'equipment', name: { ar: 'المعدات', en: 'Equipment' }, description: 'Required equipment', isSystem: true, sortOrder: 3 },
    { code: 'tag', name: { ar: 'وسم', en: 'Tag' }, description: 'Search tags', isSystem: true, sortOrder: 4 },
    { code: 'counting_method', name: { ar: 'طريقة العد', en: 'Counting Method' }, description: 'Rep counting methods', isSystem: true, sortOrder: 5 },
    { code: 'difficulty_type', name: { ar: 'مستوى الصعوبة', en: 'Difficulty Type' }, description: 'Difficulty levels', isSystem: true, sortOrder: 6 },
    { code: 'joint', name: { ar: 'المفصل', en: 'Joint' }, description: 'Body joints', isSystem: true, sortOrder: 8 },
    { code: 'priority', name: { ar: 'الأولوية', en: 'Priority' }, description: 'Error priorities', isSystem: true, sortOrder: 9 },
    { code: 'feedback_type', name: { ar: 'نوع الرسالة', en: 'Feedback Type' }, description: 'Feedback message types', isSystem: true, sortOrder: 10 },
  ];

  for (const attr of attributes) {
    await prisma.attribute.upsert({
      where: { code: attr.code },
      update: {},
      create: attr,
    });
  }

  console.log('✅ Attributes created');

  // ============================================
  // ATTRIBUTE VALUES
  // ============================================
  const categoryAttr = await prisma.attribute.findUnique({ where: { code: 'category' } });
  const muscleAttr = await prisma.attribute.findUnique({ where: { code: 'muscle' } });
  const equipmentAttr = await prisma.attribute.findUnique({ where: { code: 'equipment' } });
  const tagAttr = await prisma.attribute.findUnique({ where: { code: 'tag' } });
  const countingMethodAttr = await prisma.attribute.findUnique({ where: { code: 'counting_method' } });
  const difficultyTypeAttr = await prisma.attribute.findUnique({ where: { code: 'difficulty_type' } });
  const jointAttr = await prisma.attribute.findUnique({ where: { code: 'joint' } });
  const priorityAttr = await prisma.attribute.findUnique({ where: { code: 'priority' } });
  const feedbackTypeAttr = await prisma.attribute.findUnique({ where: { code: 'feedback_type' } });

  const categories = [
    { code: 'legs', name: { ar: 'تمارين الأرجل', en: 'Legs' }, sortOrder: 1 },
    { code: 'chest', name: { ar: 'تمارين الصدر', en: 'Chest' }, sortOrder: 2 },
    { code: 'back', name: { ar: 'تمارين الظهر', en: 'Back' }, sortOrder: 3 },
    { code: 'abs', name: { ar: 'تمارين البطن', en: 'Abs' }, sortOrder: 4 },
    { code: 'shoulders', name: { ar: 'تمارين الأكتاف', en: 'Shoulders' }, sortOrder: 5 },
    { code: 'arms', name: { ar: 'تمارين الذراعين', en: 'Arms' }, sortOrder: 6 },
    { code: 'full_body', name: { ar: 'تمارين الجسم كامل', en: 'Full Body' }, sortOrder: 7 },
  ];

  for (const cat of categories) {
    await prisma.attributeValue.upsert({
      where: { code: cat.code },
      update: {},
      create: { ...cat, attributeId: categoryAttr!.id },
    });
  }

  const muscles = [
    { code: 'quadriceps', name: { ar: 'العضلة الرباعية', en: 'Quadriceps' }, sortOrder: 1 },
    { code: 'hamstrings', name: { ar: 'عضلات الفخذ الخلفية', en: 'Hamstrings' }, sortOrder: 2 },
    { code: 'glutes', name: { ar: 'عضلات الأرداف', en: 'Glutes' }, sortOrder: 3 },
    { code: 'calves', name: { ar: 'عضلات السمانة', en: 'Calves' }, sortOrder: 4 },
    { code: 'chest_muscle', name: { ar: 'عضلات الصدر', en: 'Chest' }, sortOrder: 5 },
    { code: 'lats', name: { ar: 'العضلة الظهرية العريضة', en: 'Lats' }, sortOrder: 6 },
    { code: 'traps', name: { ar: 'العضلة شبه المنحرفة', en: 'Traps' }, sortOrder: 7 },
    { code: 'lower_back', name: { ar: 'أسفل الظهر', en: 'Lower Back' }, sortOrder: 8 },
    { code: 'abs_muscle', name: { ar: 'عضلات البطن', en: 'Abs' }, sortOrder: 9 },
    { code: 'obliques', name: { ar: 'العضلات المائلة', en: 'Obliques' }, sortOrder: 10 },
    { code: 'front_delts', name: { ar: 'الكتف الأمامي', en: 'Front Delts' }, sortOrder: 11 },
    { code: 'side_delts', name: { ar: 'الكتف الجانبي', en: 'Side Delts' }, sortOrder: 12 },
    { code: 'rear_delts', name: { ar: 'الكتف الخلفي', en: 'Rear Delts' }, sortOrder: 13 },
    { code: 'biceps', name: { ar: 'العضلة ذات الرأسين', en: 'Biceps' }, sortOrder: 14 },
    { code: 'triceps', name: { ar: 'العضلة ثلاثية الرؤوس', en: 'Triceps' }, sortOrder: 15 },
    { code: 'forearms', name: { ar: 'الساعد', en: 'Forearms' }, sortOrder: 16 },
  ];

  for (const muscle of muscles) {
    await prisma.attributeValue.upsert({
      where: { code: muscle.code },
      update: {},
      create: { ...muscle, attributeId: muscleAttr!.id },
    });
  }

  const equipment = [
    { code: 'bodyweight', name: { ar: 'وزن الجسم', en: 'Bodyweight' }, sortOrder: 1 },
    { code: 'dumbbell', name: { ar: 'دمبل', en: 'Dumbbell' }, sortOrder: 2 },
    { code: 'barbell', name: { ar: 'بار', en: 'Barbell' }, sortOrder: 3 },
    { code: 'kettlebell', name: { ar: 'كيتل بيل', en: 'Kettlebell' }, sortOrder: 4 },
    { code: 'resistance_band', name: { ar: 'حبل مقاومة', en: 'Resistance Band' }, sortOrder: 5 },
    { code: 'pull_up_bar', name: { ar: 'بار علوي', en: 'Pull-up Bar' }, sortOrder: 6 },
    { code: 'bench', name: { ar: 'مقعد', en: 'Bench' }, sortOrder: 7 },
    { code: 'mat', name: { ar: 'سجادة', en: 'Mat' }, sortOrder: 8 },
  ];

  for (const eq of equipment) {
    await prisma.attributeValue.upsert({
      where: { code: eq.code },
      update: {},
      create: { ...eq, attributeId: equipmentAttr!.id },
    });
  }

  const tags = [
    { code: 'compound', name: { ar: 'مركب', en: 'Compound' }, sortOrder: 1 },
    { code: 'upper_body', name: { ar: 'الجزء العلوي', en: 'Upper Body' }, sortOrder: 2 },
    { code: 'lower_body', name: { ar: 'الجزء السفلي', en: 'Lower Body' }, sortOrder: 3 },
    { code: 'full_body', name: { ar: 'الجسم بالكامل', en: 'Full Body' }, sortOrder: 4 },
    { code: 'no_equipment', name: { ar: 'بدون معدات', en: 'No Equipment' }, sortOrder: 5 },
    { code: 'bodyweight', name: { ar: 'وزن الجسم', en: 'Bodyweight' }, sortOrder: 6 },
    { code: 'isolation', name: { ar: 'عزل', en: 'Isolation' }, sortOrder: 7 },
    { code: 'isometric', name: { ar: 'ثابت', en: 'Isometric' }, sortOrder: 8 },
    { code: 'endurance', name: { ar: 'تحمل', en: 'Endurance' }, sortOrder: 9 },
    { code: 'strength', name: { ar: 'قوة', en: 'Strength' }, sortOrder: 10 },
    { code: 'functional', name: { ar: 'وظيفي', en: 'Functional' }, sortOrder: 11 },
    { code: 'balance', name: { ar: 'توازن', en: 'Balance' }, sortOrder: 12 },
    { code: 'unilateral', name: { ar: 'أحادي', en: 'Unilateral' }, sortOrder: 13 },
    { code: 'left', name: { ar: 'يسار', en: 'Left' }, sortOrder: 14 },
    { code: 'right', name: { ar: 'يمين', en: 'Right' }, sortOrder: 15 },
    { code: 'beginner_friendly', name: { ar: 'مناسب للمبتدئين', en: 'Beginner Friendly' }, sortOrder: 16 },
    { code: 'easy', name: { ar: 'سهل', en: 'Easy' }, sortOrder: 17 },
    { code: 'rehab', name: { ar: 'تأهيل', en: 'Rehab' }, sortOrder: 18 },
    { code: 'desk', name: { ar: 'مكتب', en: 'Desk' }, sortOrder: 19 },
    { code: 'test', name: { ar: 'اختبار', en: 'Test' }, sortOrder: 20 },
    { code: 'hold_tag', name: { ar: 'ثبات', en: 'Hold' }, sortOrder: 21 },
    { code: 'core', name: { ar: 'جذع', en: 'Core' }, sortOrder: 22 },
    { code: 'shoulders', name: { ar: 'أكتاف', en: 'Shoulders' }, sortOrder: 23 },
    { code: 'back', name: { ar: 'ظهر', en: 'Back' }, sortOrder: 24 },
  ];

  for (const tag of tags) {
    await prisma.attributeValue.upsert({
      where: { code: tag.code },
      update: {},
      create: { ...tag, attributeId: tagAttr!.id },
    });
  }

  const countingMethods = [
    {
      code: 'up_down',
      name: { ar: 'أعلى وأسفل', en: 'Up & Down' },
      description: { ar: 'يعد التكرارات عند النزول والصعود (مثل السكوات)', en: 'Counts reps on down and up movement (like squat)' },
      sortOrder: 1,
    },
    {
      code: 'push_pull',
      name: { ar: 'دفع وسحب', en: 'Push & Pull' },
      description: { ar: 'يعد التكرارات عند الدفع والسحب (مثل تمارين الضغط)', en: 'Counts reps on push and pull movement (like push-ups)' },
      sortOrder: 2,
    },
    {
      code: 'hold',
      name: { ar: 'ثبات', en: 'Hold' },
      description: { ar: 'تمارين الثبات - يحسب الوقت بدلاً من التكرارات (مثل البلانك)', en: 'Isometric exercises - counts time instead of reps (like plank)' },
      sortOrder: 3,
    },
  ];

  for (const cm of countingMethods) {
    await prisma.attributeValue.upsert({
      where: { code: cm.code },
      update: { name: cm.name, description: cm.description },
      create: { ...cm, attributeId: countingMethodAttr!.id },
    });
  }

  await prisma.attributeValue.deleteMany({
    where: { code: 'counter', attributeId: countingMethodAttr!.id },
  });

  const difficultyTypes = [
    { code: 'beginner', name: { ar: 'مبتدئ', en: 'Beginner' }, description: { ar: 'مناسب للمبتدئين', en: 'Suitable for beginners' }, sortOrder: 1 },
    { code: 'normal', name: { ar: 'عادي', en: 'Normal' }, description: { ar: 'مستوى عادي', en: 'Normal level' }, sortOrder: 2 },
    { code: 'advanced', name: { ar: 'محترف', en: 'Advanced' }, description: { ar: 'للمحترفين', en: 'For advanced users' }, sortOrder: 3 },
  ];

  for (const dt of difficultyTypes) {
    await prisma.attributeValue.upsert({
      where: { code: dt.code },
      update: {},
      create: { ...dt, attributeId: difficultyTypeAttr!.id },
    });
  }

  const unilateralJoints = [
    { code: 'left_shoulder', name: { ar: 'الكتف الأيسر', en: 'Left Shoulder' }, sortOrder: 11 },
    { code: 'right_shoulder', name: { ar: 'الكتف الأيمن', en: 'Right Shoulder' }, sortOrder: 12 },
    { code: 'left_elbow', name: { ar: 'المرفق الأيسر', en: 'Left Elbow' }, sortOrder: 13 },
    { code: 'right_elbow', name: { ar: 'المرفق الأيمن', en: 'Right Elbow' }, sortOrder: 14 },
    { code: 'left_wrist', name: { ar: 'الرسغ الأيسر', en: 'Left Wrist' }, sortOrder: 15 },
    { code: 'right_wrist', name: { ar: 'الرسغ الأيمن', en: 'Right Wrist' }, sortOrder: 16 },
    { code: 'left_pinky', name: { ar: 'الخنصر الأيسر', en: 'Left Pinky' }, sortOrder: 17 },
    { code: 'right_pinky', name: { ar: 'الخنصر الأيمن', en: 'Right Pinky' }, sortOrder: 18 },
    { code: 'left_index', name: { ar: 'السبابة اليسرى', en: 'Left Index' }, sortOrder: 19 },
    { code: 'right_index', name: { ar: 'السبابة اليمنى', en: 'Right Index' }, sortOrder: 20 },
    { code: 'left_thumb', name: { ar: 'الإبهام الأيسر', en: 'Left Thumb' }, sortOrder: 21 },
    { code: 'right_thumb', name: { ar: 'الإبهام الأيمن', en: 'Right Thumb' }, sortOrder: 22 },
    { code: 'left_hip', name: { ar: 'الورك الأيسر', en: 'Left Hip' }, sortOrder: 23 },
    { code: 'right_hip', name: { ar: 'الورك الأيمن', en: 'Right Hip' }, sortOrder: 24 },
    { code: 'left_knee', name: { ar: 'الركبة اليسرى', en: 'Left Knee' }, sortOrder: 25 },
    { code: 'right_knee', name: { ar: 'الركبة اليمنى', en: 'Right Knee' }, sortOrder: 26 },
    { code: 'left_ankle', name: { ar: 'الكاحل الأيسر', en: 'Left Ankle' }, sortOrder: 27 },
    { code: 'right_ankle', name: { ar: 'الكاحل الأيمن', en: 'Right Ankle' }, sortOrder: 28 },
    { code: 'left_heel', name: { ar: 'كعب القدم الأيسر', en: 'Left Heel' }, sortOrder: 29 },
    { code: 'right_heel', name: { ar: 'كعب القدم الأيمن', en: 'Right Heel' }, sortOrder: 30 },
    { code: 'left_foot_index', name: { ar: 'أصبع القدم الأيسر', en: 'Left Foot Index' }, sortOrder: 31 },
    { code: 'right_foot_index', name: { ar: 'أصبع القدم الأيمن', en: 'Right Foot Index' }, sortOrder: 32 },
    { code: 'spine', name: { ar: 'العمود الفقري', en: 'Spine' }, sortOrder: 100 },
  ];

  for (const joint of unilateralJoints) {
    await prisma.attributeValue.upsert({
      where: { code: joint.code },
      update: {},
      create: { ...joint, attributeId: jointAttr!.id },
    });
  }

  const bilateralJoints = [
    { code: 'shoulders', name: { ar: 'الكتفين', en: 'Shoulders' }, description: { ar: 'الكتف الأيسر والأيمن معاً', en: 'Left and Right Shoulders together' }, leftJoint: 'left_shoulder', rightJoint: 'right_shoulder', sortOrder: 201 },
    { code: 'elbows', name: { ar: 'الكوعين', en: 'Elbows' }, description: { ar: 'المرفق الأيسر والأيمن معاً', en: 'Left and Right Elbows together' }, leftJoint: 'left_elbow', rightJoint: 'right_elbow', sortOrder: 202 },
    { code: 'wrists', name: { ar: 'الرسغين', en: 'Wrists' }, description: { ar: 'الرسغ الأيسر والأيمن معاً', en: 'Left and Right Wrists together' }, leftJoint: 'left_wrist', rightJoint: 'right_wrist', sortOrder: 203 },
    { code: 'hips', name: { ar: 'الوركين', en: 'Hips' }, description: { ar: 'الورك الأيسر والأيمن معاً', en: 'Left and Right Hips together' }, leftJoint: 'left_hip', rightJoint: 'right_hip', sortOrder: 204 },
    { code: 'knees', name: { ar: 'الركبتين', en: 'Knees' }, description: { ar: 'الركبة اليسرى واليمنى معاً', en: 'Left and Right Knees together' }, leftJoint: 'left_knee', rightJoint: 'right_knee', sortOrder: 205 },
    { code: 'ankles', name: { ar: 'الكاحلين', en: 'Ankles' }, description: { ar: 'الكاحل الأيسر والأيمن معاً', en: 'Left and Right Ankles together' }, leftJoint: 'left_ankle', rightJoint: 'right_ankle', sortOrder: 206 },
  ];

  for (const joint of bilateralJoints) {
    await prisma.attributeValue.upsert({
      where: { code: joint.code },
      update: { description: joint.description },
      create: {
        code: joint.code,
        name: joint.name,
        description: joint.description,
        metadata: {
          type: 'bilateral',
          leftJoint: joint.leftJoint,
          rightJoint: joint.rightJoint,
        },
        attributeId: jointAttr!.id,
        sortOrder: joint.sortOrder,
      },
    });
  }

  const priorities = [
    { code: 'high', name: { ar: 'عالي', en: 'High' }, description: { ar: 'خطأ خطير يظهر فوراً', en: 'Critical error shown immediately' }, color: '#ef4444', sortOrder: 1 },
    { code: 'medium', name: { ar: 'متوسط', en: 'Medium' }, description: { ar: 'خطأ متوسط', en: 'Medium priority error' }, color: '#f59e0b', sortOrder: 2 },
    { code: 'low', name: { ar: 'منخفض', en: 'Low' }, description: { ar: 'ملاحظة تظهر في التقرير', en: 'Note shown in report only' }, color: '#22c55e', sortOrder: 3 },
  ];

  for (const priority of priorities) {
    await prisma.attributeValue.upsert({
      where: { code: priority.code },
      update: {},
      create: { ...priority, attributeId: priorityAttr!.id },
    });
  }

  const feedbackTypes = [
    { code: 'motivational', name: { ar: 'تحفيزي', en: 'Motivational' }, description: { ar: 'رسائل تحفيزية عند الأداء الصحيح', en: 'Motivational messages for correct form' }, sortOrder: 1 },
    { code: 'common_mistake', name: { ar: 'خطأ شائع', en: 'Common Mistake' }, description: { ar: 'أخطاء شائعة يجب تجنبها', en: 'Common mistakes to avoid' }, sortOrder: 2 },
    { code: 'tip', name: { ar: 'نصيحة', en: 'Tip' }, description: { ar: 'نصائح لتحسين الأداء', en: 'Tips for better performance' }, sortOrder: 3 },
  ];

  for (const ft of feedbackTypes) {
    await prisma.attributeValue.upsert({
      where: { code: ft.code },
      update: {},
      create: { ...ft, attributeId: feedbackTypeAttr!.id },
    });
  }

  console.log('✅ Attribute values created');
}
