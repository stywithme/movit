import { PrismaClient } from '../src/generated/prisma/client.js';
import { PrismaPg } from '@prisma/adapter-pg';

if (!process.env.DATABASE_URL) {
  throw new Error('DATABASE_URL is not set in environment variables');
}

// Create PrismaPg adapter
const adapter = new PrismaPg({ 
  connectionString: process.env.DATABASE_URL 
});

// Create PrismaClient with adapter
const prisma = new PrismaClient({ adapter });

async function main() {
  console.log('🌱 Seeding database...');

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

  // Get attribute IDs
  const categoryAttr = await prisma.attribute.findUnique({ where: { code: 'category' } });
  const muscleAttr = await prisma.attribute.findUnique({ where: { code: 'muscle' } });
  const equipmentAttr = await prisma.attribute.findUnique({ where: { code: 'equipment' } });
  const countingMethodAttr = await prisma.attribute.findUnique({ where: { code: 'counting_method' } });
  const difficultyTypeAttr = await prisma.attribute.findUnique({ where: { code: 'difficulty_type' } });
  const jointAttr = await prisma.attribute.findUnique({ where: { code: 'joint' } });
  const priorityAttr = await prisma.attribute.findUnique({ where: { code: 'priority' } });
  const feedbackTypeAttr = await prisma.attribute.findUnique({ where: { code: 'feedback_type' } });

  // Categories
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

  // Muscles
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

  // Equipment
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

  // ============================================
  // COUNTING METHODS (3 types - ALIGNED WITH ANDROID CONTRACT)
  // ============================================
  // IMPORTANT: These codes MUST match the Android JSON schema exactly:
  // - up_down: For exercises like Squat, Lunge, Bicep Curl
  // - push_pull: For exercises like Push-up, Pull-up
  // - hold: For isometric exercises like Plank, Wall Sit
  const countingMethods = [
    { 
      code: 'up_down', 
      name: { ar: 'أعلى وأسفل', en: 'Up & Down' }, 
      description: { ar: 'يعد التكرارات عند النزول والصعود (مثل السكوات)', en: 'Counts reps on down and up movement (like squat)' }, 
      sortOrder: 1 
    },
    { 
      code: 'push_pull', 
      name: { ar: 'دفع وسحب', en: 'Push & Pull' }, 
      description: { ar: 'يعد التكرارات عند الدفع والسحب (مثل تمارين الضغط)', en: 'Counts reps on push and pull movement (like push-ups)' }, 
      sortOrder: 2 
    },
    { 
      code: 'hold', 
      name: { ar: 'ثبات', en: 'Hold' }, 
      description: { ar: 'تمارين الثبات - يحسب الوقت بدلاً من التكرارات (مثل البلانك)', en: 'Isometric exercises - counts time instead of reps (like plank)' }, 
      sortOrder: 3 
    },
  ];

  for (const cm of countingMethods) {
    await prisma.attributeValue.upsert({
      where: { code: cm.code },
      update: { name: cm.name, description: cm.description }, // Update existing if code changed
      create: { ...cm, attributeId: countingMethodAttr!.id },
    });
  }

  // Delete old 'counter' code if exists (migrating to 'hold')
  try {
    await prisma.attributeValue.deleteMany({
      where: { code: 'counter', attributeId: countingMethodAttr!.id },
    });
    console.log('🔄 Migrated counting method: counter → hold');
  } catch {
    // Ignore if doesn't exist
  }

  // Difficulty Types (Fixed 3 levels: beginner, normal, advanced)
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

  // ============================================
  // JOINTS (MediaPipe Pose landmarks + custom)
  // ============================================
  // IMPORTANT: These are split into two categories:
  // 1. ANGLE_JOINTS: Can be used for trackedJoints (angle-based tracking)
  // 2. LANDMARKS: Full list for positionChecks (position-based validation)

  const joints = [
    // Upper body - Shoulders (11-12)
    { code: 'left_shoulder', name: { ar: 'الكتف الأيسر', en: 'Left Shoulder' }, sortOrder: 11 },
    { code: 'right_shoulder', name: { ar: 'الكتف الأيمن', en: 'Right Shoulder' }, sortOrder: 12 },
    // Arms - Elbows (13-14)
    { code: 'left_elbow', name: { ar: 'المرفق الأيسر', en: 'Left Elbow' }, sortOrder: 13 },
    { code: 'right_elbow', name: { ar: 'المرفق الأيمن', en: 'Right Elbow' }, sortOrder: 14 },
    // Arms - Wrists (15-16)
    { code: 'left_wrist', name: { ar: 'الرسغ الأيسر', en: 'Left Wrist' }, sortOrder: 15 },
    { code: 'right_wrist', name: { ar: 'الرسغ الأيمن', en: 'Right Wrist' }, sortOrder: 16 },
    // Hands - Pinky (17-18)
    { code: 'left_pinky', name: { ar: 'الخنصر الأيسر', en: 'Left Pinky' }, sortOrder: 17 },
    { code: 'right_pinky', name: { ar: 'الخنصر الأيمن', en: 'Right Pinky' }, sortOrder: 18 },
    // Hands - Index (19-20)
    { code: 'left_index', name: { ar: 'السبابة اليسرى', en: 'Left Index' }, sortOrder: 19 },
    { code: 'right_index', name: { ar: 'السبابة اليمنى', en: 'Right Index' }, sortOrder: 20 },
    // Hands - Thumb (21-22)
    { code: 'left_thumb', name: { ar: 'الإبهام الأيسر', en: 'Left Thumb' }, sortOrder: 21 },
    { code: 'right_thumb', name: { ar: 'الإبهام الأيمن', en: 'Right Thumb' }, sortOrder: 22 },
    // Lower body - Hips (23-24)
    { code: 'left_hip', name: { ar: 'الورك الأيسر', en: 'Left Hip' }, sortOrder: 23 },
    { code: 'right_hip', name: { ar: 'الورك الأيمن', en: 'Right Hip' }, sortOrder: 24 },
    // Legs - Knees (25-26)
    { code: 'left_knee', name: { ar: 'الركبة اليسرى', en: 'Left Knee' }, sortOrder: 25 },
    { code: 'right_knee', name: { ar: 'الركبة اليمنى', en: 'Right Knee' }, sortOrder: 26 },
    // Legs - Ankles (27-28)
    { code: 'left_ankle', name: { ar: 'الكاحل الأيسر', en: 'Left Ankle' }, sortOrder: 27 },
    { code: 'right_ankle', name: { ar: 'الكاحل الأيمن', en: 'Right Ankle' }, sortOrder: 28 },
    // Feet - Heels (29-30)
    { code: 'left_heel', name: { ar: 'كعب القدم الأيسر', en: 'Left Heel' }, sortOrder: 29 },
    { code: 'right_heel', name: { ar: 'كعب القدم الأيمن', en: 'Right Heel' }, sortOrder: 30 },
    // Feet - Foot Index (31-32)
    { code: 'left_foot_index', name: { ar: 'أصبع القدم الأيسر', en: 'Left Foot Index' }, sortOrder: 31 },
    { code: 'right_foot_index', name: { ar: 'أصبع القدم الأيمن', en: 'Right Foot Index' }, sortOrder: 32 },
    // Custom - Spine (calculated, not a MediaPipe landmark)
    { code: 'spine', name: { ar: 'العمود الفقري', en: 'Spine' }, sortOrder: 100 },
  ];

  for (const joint of joints) {
    await prisma.attributeValue.upsert({
      where: { code: joint.code },
      update: {},
      create: { ...joint, attributeId: jointAttr!.id },
    });
  }

  // Priorities
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

  // Feedback Types
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

  // ============================================
  // CAMERA POSITIONS (With schemaCode for Android export)
  // ============================================
  // Internal codes (side_left, side_right, front, back) are more specific
  // schemaCode maps to Android contract (side_view, front_view, back_view)

  // Get joint IDs for camera position joints
  const leftKnee = await prisma.attributeValue.findUnique({ where: { code: 'left_knee' } });
  const rightKnee = await prisma.attributeValue.findUnique({ where: { code: 'right_knee' } });
  const leftHip = await prisma.attributeValue.findUnique({ where: { code: 'left_hip' } });
  const rightHip = await prisma.attributeValue.findUnique({ where: { code: 'right_hip' } });
  const leftShoulder = await prisma.attributeValue.findUnique({ where: { code: 'left_shoulder' } });
  const rightShoulder = await prisma.attributeValue.findUnique({ where: { code: 'right_shoulder' } });
  const leftElbow = await prisma.attributeValue.findUnique({ where: { code: 'left_elbow' } });
  const rightElbow = await prisma.attributeValue.findUnique({ where: { code: 'right_elbow' } });
  const leftAnkle = await prisma.attributeValue.findUnique({ where: { code: 'left_ankle' } });
  const rightAnkle = await prisma.attributeValue.findUnique({ where: { code: 'right_ankle' } });
  const spine = await prisma.attributeValue.findUnique({ where: { code: 'spine' } });

  // Camera Position: Side View (Left side visible)
  const sideViewLeft = await prisma.cameraPosition.upsert({
    where: { code: 'side_left' },
    update: { schemaCode: 'side_view' },
    create: {
      code: 'side_left',
      schemaCode: 'side_view', // Android contract code
      name: { ar: 'جانبي أيسر', en: 'Left Side View' },
      description: { ar: 'عرض جانبي للجسم من الناحية اليسرى', en: 'Side view from the left' },
      sortOrder: 1,
    },
  });

  // Add joints for side_left
  const sideLeftJoints = [leftKnee, leftHip, leftShoulder, leftElbow, leftAnkle, spine];
  for (const joint of sideLeftJoints) {
    if (joint) {
      await prisma.cameraPositionJoint.upsert({
        where: { cameraPositionId_jointId: { cameraPositionId: sideViewLeft.id, jointId: joint.id } },
        update: {},
        create: { cameraPositionId: sideViewLeft.id, jointId: joint.id },
      });
    }
  }

  // Camera Position: Side View (Right side visible)
  const sideViewRight = await prisma.cameraPosition.upsert({
    where: { code: 'side_right' },
    update: { schemaCode: 'side_view' },
    create: {
      code: 'side_right',
      schemaCode: 'side_view', // Android contract code
      name: { ar: 'جانبي أيمن', en: 'Right Side View' },
      description: { ar: 'عرض جانبي للجسم من الناحية اليمنى', en: 'Side view from the right' },
      sortOrder: 2,
    },
  });

  // Add joints for side_right
  const sideRightJoints = [rightKnee, rightHip, rightShoulder, rightElbow, rightAnkle, spine];
  for (const joint of sideRightJoints) {
    if (joint) {
      await prisma.cameraPositionJoint.upsert({
        where: { cameraPositionId_jointId: { cameraPositionId: sideViewRight.id, jointId: joint.id } },
        update: {},
        create: { cameraPositionId: sideViewRight.id, jointId: joint.id },
      });
    }
  }

  // Camera Position: Front View
  const frontView = await prisma.cameraPosition.upsert({
    where: { code: 'front' },
    update: { schemaCode: 'front_view' },
    create: {
      code: 'front',
      schemaCode: 'front_view', // Android contract code
      name: { ar: 'أمامي', en: 'Front View' },
      description: { ar: 'عرض أمامي للجسم', en: 'Front view of the body' },
      sortOrder: 3,
    },
  });

  // Add joints for front (both sides visible)
  const frontJoints = [leftKnee, rightKnee, leftHip, rightHip, leftShoulder, rightShoulder, leftElbow, rightElbow];
  for (const joint of frontJoints) {
    if (joint) {
      await prisma.cameraPositionJoint.upsert({
        where: { cameraPositionId_jointId: { cameraPositionId: frontView.id, jointId: joint.id } },
        update: {},
        create: { cameraPositionId: frontView.id, jointId: joint.id },
      });
    }
  }

  // Camera Position: Back View
  const backView = await prisma.cameraPosition.upsert({
    where: { code: 'back' },
    update: { schemaCode: 'back_view' },
    create: {
      code: 'back',
      schemaCode: 'back_view', // Android contract code
      name: { ar: 'خلفي', en: 'Back View' },
      description: { ar: 'عرض خلفي للجسم', en: 'Back view of the body' },
      sortOrder: 4,
    },
  });

  // Add joints for back (both sides visible from behind)
  const backJoints = [leftKnee, rightKnee, leftHip, rightHip, leftShoulder, rightShoulder, spine];
  for (const joint of backJoints) {
    if (joint) {
      await prisma.cameraPositionJoint.upsert({
        where: { cameraPositionId_jointId: { cameraPositionId: backView.id, jointId: joint.id } },
        update: {},
        create: { cameraPositionId: backView.id, jointId: joint.id },
      });
    }
  }

  console.log('✅ Camera positions created');

  console.log('🎉 Seeding completed!');
}

main()
  .catch((e) => {
    console.error('❌ Seeding failed:', e);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
