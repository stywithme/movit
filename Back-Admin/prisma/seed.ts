import { PrismaClient } from '../src/generated/prisma/client.js';
import { PrismaPg } from '@prisma/adapter-pg';
import bcrypt from 'bcryptjs';
import * as fs from 'fs/promises';
import * as path from 'path';

if (!process.env.DATABASE_URL) {
  throw new Error('DATABASE_URL is not set in environment variables');
}

// Create PrismaPg adapter
const adapter = new PrismaPg({ 
  connectionString: process.env.DATABASE_URL 
});

// Create PrismaClient with adapter
const prisma = new PrismaClient({ adapter });

const categoryCodeMap: Record<string, string> = {
  glutes: 'legs',
};

const muscleCodeMap: Record<string, string> = {
  chest: 'chest_muscle',
  shoulders: 'front_delts',
  arms: 'biceps',
  back: 'lats',
  core: 'abs_muscle',
  abs: 'abs_muscle',
  deltoids: 'side_delts',
  trapezius: 'traps',
  upper_chest: 'chest_muscle',
  soleus: 'calves',
};

const muscleNameMap: Record<string, { ar: string; en: string }> = {
  chest: { ar: 'الصدر', en: 'Chest' },
  shoulders: { ar: 'الأكتاف', en: 'Shoulders' },
  arms: { ar: 'الذراعين', en: 'Arms' },
  back: { ar: 'الظهر', en: 'Back' },
  core: { ar: 'الجذع', en: 'Core' },
  abs: { ar: 'البطن', en: 'Abs' },
  deltoids: { ar: 'الدالية', en: 'Deltoids' },
  trapezius: { ar: 'شبه المنحرفة', en: 'Trapezius' },
  upper_chest: { ar: 'الصدر العلوي', en: 'Upper Chest' },
  soleus: { ar: 'عضلة النعلية', en: 'Soleus' },
};

const equipmentNameMap: Record<string, { ar: string; en: string }> = {
  cable_machine: { ar: 'جهاز الكابلات', en: 'Cable Machine' },
  chair: { ar: 'كرسي', en: 'Chair' },
  parallel_bars: { ar: 'متوازي', en: 'Parallel Bars' },
  smith_machine: { ar: 'جهاز سميث', en: 'Smith Machine' },
};

const tagNameMap: Record<string, { ar: string; en: string }> = {
  compound: { ar: 'مركب', en: 'Compound' },
  upper_body: { ar: 'الجزء العلوي', en: 'Upper Body' },
  lower_body: { ar: 'الجزء السفلي', en: 'Lower Body' },
  full_body: { ar: 'الجسم بالكامل', en: 'Full Body' },
  no_equipment: { ar: 'بدون معدات', en: 'No Equipment' },
  bodyweight: { ar: 'وزن الجسم', en: 'Bodyweight' },
  isolation: { ar: 'عزل', en: 'Isolation' },
  isometric: { ar: 'ثابت', en: 'Isometric' },
  endurance: { ar: 'تحمل', en: 'Endurance' },
  strength: { ar: 'قوة', en: 'Strength' },
  functional: { ar: 'وظيفي', en: 'Functional' },
  balance: { ar: 'توازن', en: 'Balance' },
  unilateral: { ar: 'أحادي', en: 'Unilateral' },
  left: { ar: 'يسار', en: 'Left' },
  right: { ar: 'يمين', en: 'Right' },
  beginner_friendly: { ar: 'مناسب للمبتدئين', en: 'Beginner Friendly' },
  easy: { ar: 'سهل', en: 'Easy' },
  rehab: { ar: 'تأهيل', en: 'Rehab' },
  desk: { ar: 'مكتب', en: 'Desk' },
  test: { ar: 'اختبار', en: 'Test' },
  hold: { ar: 'ثبات', en: 'Hold' },
  core: { ar: 'جذع', en: 'Core' },
  shoulders: { ar: 'أكتاف', en: 'Shoulders' },
  back: { ar: 'ظهر', en: 'Back' },
};

function toTitleCase(value: string): string {
  return value
    .replace(/[_-]+/g, ' ')
    .replace(/\b\w/g, (char) => char.toUpperCase())
    .trim();
}

function buildLocalizedName(code: string, map?: Record<string, { ar: string; en: string }>) {
  const normalized = code.replace(/-/g, '_');
  if (map && map[normalized]) return map[normalized];
  const title = toTitleCase(normalized);
  return { ar: title, en: title };
}

function normalizeMessageContent(content: { ar?: string; en?: string; audioAr?: string; audioEn?: string }) {
  return {
    ar: content.ar || '',
    en: content.en || '',
    audioAr: content.audioAr,
    audioEn: content.audioEn,
  };
}

async function clearDatabase() {
  console.log('🧹 Clearing existing data...');
  await prisma.$transaction([
    prisma.repMetrics.deleteMany(),
    prisma.sessionMetrics.deleteMany(),
    prisma.trainingSession.deleteMany(),
    prisma.refreshToken.deleteMany(),
    prisma.workoutExercise.deleteMany(),
    prisma.workout.deleteMany(),
    prisma.feedbackMessageAssignment.deleteMany(),
    prisma.feedbackMessageTemplate.deleteMany(),
    prisma.positionCheck.deleteMany(),
    prisma.poseVariant.deleteMany(),
    prisma.exerciseMedia.deleteMany(),
    prisma.exerciseAttribute.deleteMany(),
    prisma.exercise.deleteMany(),
    prisma.cameraPositionJoint.deleteMany(),
    prisma.cameraPosition.deleteMany(),
    prisma.difficultyLevel.deleteMany(),
    prisma.attributeValue.deleteMany(),
    prisma.attribute.deleteMany(),
    prisma.user.deleteMany(),
    prisma.admin.deleteMany(),
  ]);
  console.log('✅ Database cleared');
}

async function main() {
  console.log('🌱 Seeding database...');

  await clearDatabase();

  const messageIdByKey = new Map<string, string>();
  const messageCounters = new Map<string, number>();

  const nextMessageCode = (category: string) => {
    const current = messageCounters.get(category) || 0;
    const next = current + 1;
    messageCounters.set(category, next);
    return `MSG_${category.toUpperCase()}_${String(next).padStart(4, '0')}`;
  };

  const ensureMessageTemplate = async (params: {
    category: string;
    context?: string | null;
    content: { ar?: string; en?: string; audioAr?: string; audioEn?: string };
    tags?: string[];
    isSystem?: boolean;
  }) => {
    const normalized = normalizeMessageContent(params.content);
    const key = [
      params.category,
      params.context || '',
      normalized.ar,
      normalized.en,
      normalized.audioAr || '',
      normalized.audioEn || '',
    ].join('|');

    const existingId = messageIdByKey.get(key);
    if (existingId) return existingId;

    const created = await prisma.feedbackMessageTemplate.create({
      data: {
        code: nextMessageCode(params.category),
        category: params.category,
        context: params.context || null,
        content: normalized as object,
        tags: params.tags || [],
        isSystem: params.isSystem ?? false,
        isActive: true,
      },
    });

    messageIdByKey.set(key, created.id);
    return created.id;
  };

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
  const tagAttr = await prisma.attribute.findUnique({ where: { code: 'tag' } });
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
  // IMPORTANT: These are split into three categories:
  // 1. UNILATERAL_JOINTS: Single-side joints (left_* or right_*)
  // 2. BILATERAL_JOINTS: Virtual joints representing a pair (e.g., "knees" = left_knee + right_knee)
  // 3. LANDMARKS: Full list for positionChecks (position-based validation)

  // Unilateral joints (individual left/right)
  const unilateralJoints = [
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

  for (const joint of unilateralJoints) {
    await prisma.attributeValue.upsert({
      where: { code: joint.code },
      update: {},
      create: { ...joint, attributeId: jointAttr!.id },
    });
  }

  // ============================================
  // BILATERAL JOINTS (Virtual paired joints)
  // ============================================
  // When admin selects "Knees", system creates both left_knee and right_knee
  // with Symmetry metric automatically enabled
  
  const bilateralJoints = [
    { 
      code: 'shoulders', 
      name: { ar: 'الكتفين', en: 'Shoulders' }, 
      description: { ar: 'الكتف الأيسر والأيمن معاً', en: 'Left and Right Shoulders together' },
      leftJoint: 'left_shoulder',
      rightJoint: 'right_shoulder',
      sortOrder: 201 
    },
    { 
      code: 'elbows', 
      name: { ar: 'الكوعين', en: 'Elbows' }, 
      description: { ar: 'المرفق الأيسر والأيمن معاً', en: 'Left and Right Elbows together' },
      leftJoint: 'left_elbow',
      rightJoint: 'right_elbow',
      sortOrder: 202 
    },
    { 
      code: 'wrists', 
      name: { ar: 'الرسغين', en: 'Wrists' }, 
      description: { ar: 'الرسغ الأيسر والأيمن معاً', en: 'Left and Right Wrists together' },
      leftJoint: 'left_wrist',
      rightJoint: 'right_wrist',
      sortOrder: 203 
    },
    { 
      code: 'hips', 
      name: { ar: 'الوركين', en: 'Hips' }, 
      description: { ar: 'الورك الأيسر والأيمن معاً', en: 'Left and Right Hips together' },
      leftJoint: 'left_hip',
      rightJoint: 'right_hip',
      sortOrder: 204 
    },
    { 
      code: 'knees', 
      name: { ar: 'الركبتين', en: 'Knees' }, 
      description: { ar: 'الركبة اليسرى واليمنى معاً', en: 'Left and Right Knees together' },
      leftJoint: 'left_knee',
      rightJoint: 'right_knee',
      sortOrder: 205 
    },
    { 
      code: 'ankles', 
      name: { ar: 'الكاحلين', en: 'Ankles' }, 
      description: { ar: 'الكاحل الأيسر والأيمن معاً', en: 'Left and Right Ankles together' },
      leftJoint: 'left_ankle',
      rightJoint: 'right_ankle',
      sortOrder: 206 
    },
  ];

  for (const joint of bilateralJoints) {
    await prisma.attributeValue.upsert({
      where: { code: joint.code },
      update: { description: joint.description },
      create: { 
        code: joint.code,
        name: joint.name, 
        description: joint.description,
        // Store left/right references in metadata (JSON)
        metadata: { 
          type: 'bilateral',
          leftJoint: joint.leftJoint, 
          rightJoint: joint.rightJoint 
        },
        attributeId: jointAttr!.id,
        sortOrder: joint.sortOrder,
      },
    });
  }

  console.log('✅ Bilateral joints created');

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
  // MESSAGE TEMPLATES (Base Library)
  // ============================================
  const baseMessageTemplates = [
    { category: 'state', context: 'perfect', content: { ar: 'ممتاز!', en: 'Perfect!' }, tags: ['state', 'perfect'] },
    { category: 'state', context: 'normal', content: { ar: 'جيد', en: 'Good' }, tags: ['state', 'normal'] },
    { category: 'state', context: 'pad', content: { ar: 'مقبول', en: 'Acceptable' }, tags: ['state', 'pad'] },
    { category: 'state', context: 'warning', content: { ar: 'تحقق من وضعك', en: 'Check your position' }, tags: ['state', 'warning'] },
    { category: 'state', context: 'danger', content: { ar: 'توقف! وضع خطير', en: 'Stop! Dangerous position' }, tags: ['state', 'danger'] },
    { category: 'motivational', context: 'motivational', content: { ar: 'استمر!', en: 'Keep going!' }, tags: ['motivational'] },
    { category: 'motivational', context: 'motivational', content: { ar: 'أداء ممتاز!', en: 'Great job!' }, tags: ['motivational'] },
    { category: 'tip', context: 'tip', content: { ar: 'حافظ على الوضع الصحيح', en: 'Maintain proper form' }, tags: ['tip'] },
    { category: 'position', context: 'error', content: { ar: 'ضعية غير صحيحة', en: 'Incorrect position' }, tags: ['position', 'error'] },
  ];

  for (const template of baseMessageTemplates) {
    await ensureMessageTemplate(template);
  }

  console.log('✅ Base message templates created');

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

  // ============================================
  // EXERCISES & WORKOUTS (Android Assets)
  // ============================================

  const assetsDir = path.resolve(__dirname, '../../Docs/Old-way-json');
  const exercisesDir = path.join(assetsDir, 'exercises');
  const workoutsDir = path.join(assetsDir, 'workouts');

  const attributeValues = await prisma.attributeValue.findMany();
  const attributeValueByCode = new Map(attributeValues.map((value) => [value.code, value]));

  if (!categoryAttr || !muscleAttr || !equipmentAttr || !tagAttr || !countingMethodAttr) {
    throw new Error('Required attributes are missing');
  }

  const ensureAttributeValue = async (
    attributeId: string,
    code: string,
    name: { ar: string; en: string }
  ) => {
    const existing = attributeValueByCode.get(code);
    if (existing) return existing;
    const created = await prisma.attributeValue.upsert({
      where: { code },
      update: { name },
      create: { code, name, attributeId, sortOrder: 999 },
    });
    attributeValueByCode.set(code, created);
    return created;
  };

  const resolveTagCode = (rawCode: string) => {
    const normalized = rawCode.replace(/-/g, '_');
    const existing = attributeValueByCode.get(normalized);
    if (!existing) return normalized;
    if (existing.attributeId === tagAttr.id) return normalized;
    return `tag_${normalized}`;
  };

  const cameraPositions = await prisma.cameraPosition.findMany({
    where: { schemaCode: { in: ['side_view', 'front_view', 'back_view'] } },
    orderBy: { sortOrder: 'asc' },
  });
  const cameraBySchema = new Map<string, string>();
  for (const camera of cameraPositions) {
    if (!camera.schemaCode) continue;
    if (!cameraBySchema.has(camera.schemaCode)) {
      cameraBySchema.set(camera.schemaCode, camera.id);
    }
  }

  const exercisesDirExists = await fs
    .stat(exercisesDir)
    .then(() => true)
    .catch(() => false);
  const workoutsDirExists = await fs
    .stat(workoutsDir)
    .then(() => true)
    .catch(() => false);

  if (!exercisesDirExists) {
    console.warn(`⚠️ Exercises directory not found: ${exercisesDir}`);
  }
  if (!workoutsDirExists) {
    console.warn(`⚠️ Workouts directory not found: ${workoutsDir}`);
  }

  const exerciseFiles = exercisesDirExists
    ? (await fs.readdir(exercisesDir)).filter((file) => file.endsWith('.json'))
    : [];

  for (const file of exerciseFiles) {
    const filePath = path.join(exercisesDir, file);
    const raw = await fs.readFile(filePath, 'utf8');
    const exerciseJson = JSON.parse(raw) as {
      name: { ar: string; en: string };
      description?: { ar: string; en: string };
      instructions?: { ar: string; en: string };
      category: { code: string; name: { ar: string; en: string } };
      countingMethod: string;
      muscles?: string[];
      equipment?: string[];
      tags?: string[];
      repCountingConfig?: Record<string, unknown>;
      poseVariants?: Array<{
        name: { ar: string; en: string };
        cameraPosition: string;
        expectedFacingDirection?: string;
        trackedJoints?: unknown[];
        positionChecks?: Array<{
          id: string;
          type: string;
          landmarks: Record<string, unknown>;
          condition: Record<string, unknown>;
          activePhases?: string[];
          errorMessage: { ar: string; en: string };
          severity?: string;
          cooldownMs?: number;
          minErrorFrames?: number;
        }>;
        feedbackMessages?: {
          motivational?: Array<{ ar: string; en: string }>;
          tips?: Array<{ ar: string; en: string }>;
        };
      }>;
    };

    const slug = path.basename(file, '.json');
    const categoryCode = categoryCodeMap[exerciseJson.category.code] || exerciseJson.category.code;
    const categoryValue = await ensureAttributeValue(
      categoryAttr.id,
      categoryCode,
      exerciseJson.category.name
    );

    const countingMethodValue = attributeValueByCode.get(exerciseJson.countingMethod);
    if (!countingMethodValue || countingMethodValue.attributeId !== countingMethodAttr.id) {
      throw new Error(`Counting method not found for ${exerciseJson.countingMethod}`);
    }

    const exerciseRecord = await prisma.exercise.upsert({
      where: { slug },
      update: {
        name: exerciseJson.name,
        description: exerciseJson.description || undefined,
        instructions: exerciseJson.instructions || undefined,
        categoryId: categoryValue.id,
        countingMethodId: countingMethodValue.id,
        repCountingConfig: (exerciseJson.repCountingConfig as object) || undefined,
        status: 'published',
        publishedAt: new Date(),
      },
      create: {
        slug,
        name: exerciseJson.name,
        description: exerciseJson.description || undefined,
        instructions: exerciseJson.instructions || undefined,
        categoryId: categoryValue.id,
        countingMethodId: countingMethodValue.id,
        repCountingConfig: (exerciseJson.repCountingConfig as object) || undefined,
        status: 'published',
        publishedAt: new Date(),
      },
    });

    const muscleIds: string[] = [];
    for (const rawCode of exerciseJson.muscles || []) {
      const normalized = rawCode.replace(/-/g, '_');
      const resolved = muscleCodeMap[normalized] || normalized;
      const existing = attributeValueByCode.get(resolved);
      if (existing) {
        if (existing.attributeId !== muscleAttr.id) {
          console.warn(`Skipping muscle code due to conflict: ${resolved}`);
          continue;
        }
        muscleIds.push(existing.id);
        continue;
      }

      const created = await ensureAttributeValue(
        muscleAttr.id,
        resolved,
        buildLocalizedName(normalized, muscleNameMap)
      );
      muscleIds.push(created.id);
    }

    const equipmentIds: string[] = [];
    for (const rawCode of exerciseJson.equipment || []) {
      const normalized = rawCode.replace(/-/g, '_');
      const existing = attributeValueByCode.get(normalized);
      if (existing) {
        if (existing.attributeId !== equipmentAttr.id) {
          console.warn(`Skipping equipment code due to conflict: ${normalized}`);
          continue;
        }
        equipmentIds.push(existing.id);
        continue;
      }

      const created = await ensureAttributeValue(
        equipmentAttr.id,
        normalized,
        buildLocalizedName(normalized, equipmentNameMap)
      );
      equipmentIds.push(created.id);
    }

    const tagIds: string[] = [];
    for (const rawCode of exerciseJson.tags || []) {
      const normalized = rawCode.replace(/-/g, '_');
      const tagCode = resolveTagCode(normalized);
      const existing = attributeValueByCode.get(tagCode);
      if (existing) {
        if (existing.attributeId !== tagAttr.id) {
          console.warn(`Skipping tag code due to conflict: ${tagCode}`);
          continue;
        }
        tagIds.push(existing.id);
        continue;
      }

      const created = await ensureAttributeValue(
        tagAttr.id,
        tagCode,
        buildLocalizedName(normalized, tagNameMap)
      );
      tagIds.push(created.id);
    }

    await prisma.exerciseAttribute.deleteMany({
      where: { exerciseId: exerciseRecord.id },
    });

    const attributeIds = [...new Set([...muscleIds, ...equipmentIds, ...tagIds])];
    if (attributeIds.length > 0) {
      await prisma.exerciseAttribute.createMany({
        data: attributeIds.map((attributeValueId) => ({
          exerciseId: exerciseRecord.id,
          attributeValueId,
        })),
        skipDuplicates: true,
      });
    }

    await prisma.poseVariant.deleteMany({
      where: { exerciseId: exerciseRecord.id },
    });

    for (let pvIndex = 0; pvIndex < (exerciseJson.poseVariants || []).length; pvIndex++) {
      const variant = exerciseJson.poseVariants![pvIndex];
      const cameraPositionId = cameraBySchema.get(variant.cameraPosition);
      if (!cameraPositionId) {
        throw new Error(`Camera position not found for ${variant.cameraPosition}`);
      }

      const poseVariant = await prisma.poseVariant.create({
        data: {
          exerciseId: exerciseRecord.id,
          cameraPositionId,
          name: variant.name,
          expectedFacingDirection: variant.expectedFacingDirection || 'auto_detect',
          trackedJointsConfig: (variant.trackedJoints as object) || undefined,
          sortOrder: pvIndex + 1,
        },
      });

      const assignments: Array<{
        poseVariantId: string;
        messageId: string;
        target: string;
        context?: string | null;
        jointCode?: string | null;
        zone?: string | null;
        checkId?: string | null;
        sortOrder: number;
      }> = [];
      let assignmentOrder = 1;
      const addAssignment = (data: Omit<(typeof assignments)[number], 'sortOrder'>) => {
        assignments.push({ ...data, sortOrder: assignmentOrder++ });
      };

      if (variant.positionChecks && variant.positionChecks.length > 0) {
        await prisma.positionCheck.createMany({
          data: variant.positionChecks.map((check, index) => ({
            poseVariantId: poseVariant.id,
            checkId: check.id,
            type: check.type,
            landmarks: check.landmarks as object,
            condition: check.condition as object,
            activePhases: check.activePhases || [],
            errorMessage: check.errorMessage,
            severity: check.severity || 'warning',
            cooldownMs: check.cooldownMs ?? 2000,
            minErrorFrames: check.minErrorFrames ?? 3,
            sortOrder: index + 1,
          })),
        });

        for (const check of variant.positionChecks) {
          if (!check.errorMessage) continue;
          const messageId = await ensureMessageTemplate({
            category: 'position',
            context: 'error',
            content: check.errorMessage,
            tags: ['position', 'error'],
          });
          addAssignment({
            poseVariantId: poseVariant.id,
            messageId,
            target: 'position',
            context: 'error',
            checkId: check.id,
          });
        }
      }

      const trackedJoints = Array.isArray(variant.trackedJoints) ? variant.trackedJoints : [];
      for (const joint of trackedJoints) {
        const jointCode = joint?.joint || joint?.code;
        if (!jointCode || !joint.stateMessages) continue;
        const stateMessages = joint.stateMessages as Record<string, unknown>;
        for (const [state, value] of Object.entries(stateMessages)) {
          if (!value) continue;
          if (typeof value === 'object' && ('up' in value || 'down' in value)) {
            const zoneValue = value as Record<string, { ar?: string; en?: string; audioAr?: string; audioEn?: string } | undefined>;
            for (const zone of ['up', 'down'] as const) {
              const msg = zoneValue[zone];
              if (!msg || (!msg.ar && !msg.en)) continue;
              const messageId = await ensureMessageTemplate({
                category: 'state',
                context: state,
                content: msg,
                tags: ['state', state],
              });
              addAssignment({
                poseVariantId: poseVariant.id,
                messageId,
                target: 'joint_state',
                context: state,
                jointCode,
                zone,
              });
            }
          } else {
            const msg = value as { ar?: string; en?: string; audioAr?: string; audioEn?: string };
            if (!msg || (!msg.ar && !msg.en)) continue;
            const messageId = await ensureMessageTemplate({
              category: 'state',
              context: state,
              content: msg,
              tags: ['state', state],
            });
            addAssignment({
              poseVariantId: poseVariant.id,
              messageId,
              target: 'joint_state',
              context: state,
              jointCode,
            });
          }
        }
      }

      const feedbackMessages = variant.feedbackMessages || {};
      const motivational = feedbackMessages.motivational || [];
      const tips = feedbackMessages.tips || [];

      for (const msg of motivational) {
        const messageId = await ensureMessageTemplate({
          category: 'motivational',
          context: 'motivational',
          content: msg,
          tags: ['motivational'],
        });
        addAssignment({
          poseVariantId: poseVariant.id,
          messageId,
          target: 'feedback',
          context: 'motivational',
        });
      }

      for (const msg of tips) {
        const messageId = await ensureMessageTemplate({
          category: 'tip',
          context: 'tip',
          content: msg,
          tags: ['tip'],
        });
        addAssignment({
          poseVariantId: poseVariant.id,
          messageId,
          target: 'feedback',
          context: 'tip',
        });
      }

      if (assignments.length > 0) {
        await prisma.feedbackMessageAssignment.createMany({
          data: assignments,
        });
      }
    }
  }

  console.log('✅ Exercises seeded from assets');

  const workoutFiles = workoutsDirExists
    ? (await fs.readdir(workoutsDir)).filter((file) => file.endsWith('.json'))
    : [];

  for (const file of workoutFiles) {
    const filePath = path.join(workoutsDir, file);
    const raw = await fs.readFile(filePath, 'utf8');
    const workoutJson = JSON.parse(raw) as {
      name: { ar: string; en: string };
      description?: { ar: string; en: string };
      type: 'circuit' | 'super_set';
      executionMode: 'sequential' | 'alternating';
      repsPerSwitch?: number;
      restBetweenSwitchMs?: number;
      restBetweenExercisesMs?: number;
      restBetweenRoundsMs?: number;
      rounds?: number;
      exercises: Array<{
        exercise: string;
        variantIndex?: number;
        difficulty?: 'beginner' | 'normal' | 'advanced';
        target?: { reps?: number; durationSec?: number };
        notes?: { ar: string; en: string };
      }>;
    };

    const slug = path.basename(file, '.json');

    const workoutRecord = await prisma.workout.upsert({
      where: { slug },
      update: {
        name: workoutJson.name,
        description: workoutJson.description || undefined,
        type: workoutJson.type,
        executionMode: workoutJson.executionMode,
        rounds: workoutJson.rounds ?? 1,
        repsPerSwitch: workoutJson.repsPerSwitch ?? undefined,
        restBetweenSwitchMs: workoutJson.restBetweenSwitchMs ?? undefined,
        restBetweenExercisesMs: workoutJson.restBetweenExercisesMs ?? undefined,
        restBetweenRoundsMs: workoutJson.restBetweenRoundsMs ?? 60000,
        status: 'published',
        publishedAt: new Date(),
      },
      create: {
        slug,
        name: workoutJson.name,
        description: workoutJson.description || undefined,
        type: workoutJson.type,
        executionMode: workoutJson.executionMode,
        rounds: workoutJson.rounds ?? 1,
        repsPerSwitch: workoutJson.repsPerSwitch ?? undefined,
        restBetweenSwitchMs: workoutJson.restBetweenSwitchMs ?? undefined,
        restBetweenExercisesMs: workoutJson.restBetweenExercisesMs ?? undefined,
        restBetweenRoundsMs: workoutJson.restBetweenRoundsMs ?? 60000,
        status: 'published',
        publishedAt: new Date(),
      },
    });

    await prisma.workoutExercise.deleteMany({
      where: { workoutId: workoutRecord.id },
    });

    for (let index = 0; index < workoutJson.exercises.length; index++) {
      const exerciseEntry = workoutJson.exercises[index];
      const exerciseRecord = await prisma.exercise.findUnique({
        where: { slug: exerciseEntry.exercise },
        select: { id: true },
      });

      if (!exerciseRecord) {
        throw new Error(`Workout exercise not found: ${exerciseEntry.exercise}`);
      }

      await prisma.workoutExercise.create({
        data: {
          workoutId: workoutRecord.id,
          exerciseId: exerciseRecord.id,
          variantIndex: exerciseEntry.variantIndex ?? 0,
          difficulty: exerciseEntry.difficulty ?? 'beginner',
          targetReps: exerciseEntry.target?.reps ?? undefined,
          targetDuration: exerciseEntry.target?.durationSec ?? undefined,
          notes: exerciseEntry.notes || undefined,
          sortOrder: index,
        },
      });
    }
  }

  console.log('✅ Workouts seeded from assets');

  // ============================================
  // SUPER ADMIN (Dashboard)
  // ============================================
  const adminEmail = (process.env.ADMIN_SEED_EMAIL || 'alustadh.manager@gmail.com').toLowerCase();
  const adminName = process.env.ADMIN_SEED_NAME || 'Super Admin';
  const adminPassword = process.env.ADMIN_SEED_PASSWORD || 'password';
  const hashedPassword = await bcrypt.hash(adminPassword, 12);

  await prisma.admin.upsert({
    where: { email: adminEmail },
    update: {
      name: adminName,
      role: 'super_admin',
      isActive: true,
      deletedAt: null,
      ...(process.env.ADMIN_SEED_PASSWORD ? { password: hashedPassword } : {}),
    },
    create: {
      name: adminName,
      email: adminEmail,
      password: hashedPassword,
      role: 'super_admin',
      isActive: true,
    },
  });

  console.log(`✅ Super admin seeded: ${adminEmail}`);

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
