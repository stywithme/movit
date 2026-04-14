import type { PrismaClient } from '@prisma/client';

interface PosePositionSeed {
  code: string;
  name: { ar: string; en: string };
  description: { ar: string; en: string };
  postures: string[];
  directions: string[];
  regions: string[];
  jointCodes: string[];
}

const POSITIONS: PosePositionSeed[] = [
  // ── Standing, Full Body ────────────────────────────────────────────────
  {
    code: 'standing_front',
    name: { ar: 'أمامي - واقف', en: 'Standing Front' },
    description: { ar: 'الجسم بالكامل من الأمام', en: 'Full body from front' },
    postures: ['standing'],
    directions: ['front'],
    regions: ['full_body'],
    jointCodes: [
      'left_knee', 'right_knee', 'left_hip', 'right_hip',
      'left_shoulder', 'right_shoulder', 'left_elbow', 'right_elbow',
      'left_ankle', 'right_ankle', 'spine',
    ],
  },
  {
    code: 'standing_back',
    name: { ar: 'خلفي - واقف', en: 'Standing Back' },
    description: { ar: 'الجسم بالكامل من الخلف', en: 'Full body from back' },
    postures: ['standing'],
    directions: ['back'],
    regions: ['full_body'],
    jointCodes: [
      'left_knee', 'right_knee', 'left_hip', 'right_hip',
      'left_shoulder', 'right_shoulder', 'spine',
    ],
  },
  {
    code: 'standing_side',
    name: { ar: 'جانبي - واقف', en: 'Standing Side' },
    description: { ar: 'الجسم بالكامل من الجانب', en: 'Full body from the side (auto-detect L/R)' },
    postures: ['standing'],
    directions: ['side'],
    regions: ['full_body'],
    jointCodes: [
      'left_knee', 'right_knee', 'left_hip', 'right_hip',
      'left_shoulder', 'right_shoulder', 'left_elbow', 'right_elbow',
      'left_ankle', 'right_ankle', 'spine',
    ],
  },
  {
    code: 'standing_side_left',
    name: { ar: 'جانبي أيسر - واقف', en: 'Standing Side Left' },
    description: { ar: 'الجسم بالكامل من الجانب الأيسر', en: 'Full body from left side' },
    postures: ['standing'],
    directions: ['side_left'],
    regions: ['full_body'],
    jointCodes: ['left_knee', 'left_hip', 'left_shoulder', 'left_elbow', 'left_ankle', 'spine'],
  },
  {
    code: 'standing_side_right',
    name: { ar: 'جانبي أيمن - واقف', en: 'Standing Side Right' },
    description: { ar: 'الجسم بالكامل من الجانب الأيمن', en: 'Full body from right side' },
    postures: ['standing'],
    directions: ['side_right'],
    regions: ['full_body'],
    jointCodes: ['right_knee', 'right_hip', 'right_shoulder', 'right_elbow', 'right_ankle', 'spine'],
  },
  {
    code: 'standing_diagonal',
    name: { ar: 'مائل - واقف', en: 'Standing Diagonal' },
    description: { ar: 'الجسم بوضع مائل قليلاً', en: 'Slightly angled view' },
    postures: ['standing'],
    directions: ['front', 'side'],
    regions: ['full_body'],
    jointCodes: [
      'left_knee', 'right_knee', 'left_hip', 'right_hip',
      'left_shoulder', 'right_shoulder', 'left_elbow', 'right_elbow', 'spine',
    ],
  },

  // ── Sitting, Full Body ──────────────────────────────────────────────────
  {
    code: 'sitting_front',
    name: { ar: 'أمامي - جالس', en: 'Sitting Front' },
    description: { ar: 'جالس، الجسم بالكامل من الأمام', en: 'Sitting, full body from front' },
    postures: ['sitting'],
    directions: ['front'],
    regions: ['full_body'],
    jointCodes: [
      'left_knee',
      'right_knee',
      'left_hip',
      'right_hip',
      'left_shoulder',
      'right_shoulder',
      'left_elbow',
      'right_elbow',
      'spine',
    ],
  },
  {
    code: 'sitting_side',
    name: { ar: 'جانبي - جالس', en: 'Sitting Side' },
    description: { ar: 'جالس، الجسم بالكامل من الجانب', en: 'Sitting, full body from side' },
    postures: ['sitting'],
    directions: ['side'],
    regions: ['full_body'],
    jointCodes: [
      'left_knee',
      'right_knee',
      'left_hip',
      'right_hip',
      'left_shoulder',
      'right_shoulder',
      'left_elbow',
      'right_elbow',
      'spine',
    ],
  },
  // ── Sitting, Upper Body ───────────────────────────────────────────────
  {
    code: 'sitting_front_upper',
    name: { ar: 'أمامي علوي - جالس', en: 'Sitting Front Upper' },
    description: { ar: 'جالس، الجزء العلوي من الأمام', en: 'Sitting, upper body from front' },
    postures: ['sitting'],
    directions: ['front'],
    regions: ['upper_body'],
    jointCodes: ['left_shoulder', 'right_shoulder', 'left_elbow', 'right_elbow', 'spine'],
  },
  {
    code: 'sitting_side_upper',
    name: { ar: 'جانبي علوي - جالس', en: 'Sitting Side Upper' },
    description: { ar: 'جالس، الجزء العلوي من الجانب', en: 'Sitting, upper body from side' },
    postures: ['sitting'],
    directions: ['side'],
    regions: ['upper_body'],
    jointCodes: ['left_shoulder', 'right_shoulder', 'left_elbow', 'right_elbow', 'spine'],
  },

  // ── Standing, Upper Body ───────────────────────────────────────────────
  {
    code: 'standing_front_upper',
    name: { ar: 'أمامي علوي - واقف', en: 'Standing Front Upper' },
    description: { ar: 'الجزء العلوي من الأمام', en: 'Upper body from front' },
    postures: ['standing'],
    directions: ['front'],
    regions: ['upper_body'],
    jointCodes: ['left_shoulder', 'right_shoulder', 'left_elbow', 'right_elbow', 'spine'],
  },
  {
    code: 'standing_back_upper',
    name: { ar: 'خلفي علوي - واقف', en: 'Standing Back Upper' },
    description: { ar: 'الجزء العلوي من الخلف', en: 'Upper body from back' },
    postures: ['standing'],
    directions: ['back'],
    regions: ['upper_body'],
    jointCodes: ['left_shoulder', 'right_shoulder', 'spine'],
  },
  {
    code: 'standing_side_upper',
    name: { ar: 'جانبي علوي - واقف', en: 'Standing Side Upper' },
    description: { ar: 'الجزء العلوي من الجانب', en: 'Upper body from side' },
    postures: ['standing'],
    directions: ['side'],
    regions: ['upper_body'],
    jointCodes: ['left_shoulder', 'right_shoulder', 'left_elbow', 'right_elbow', 'spine'],
  },

  // ── Standing, Lower Body ───────────────────────────────────────────────
  {
    code: 'standing_front_lower',
    name: { ar: 'أمامي سفلي - واقف', en: 'Standing Front Lower' },
    description: { ar: 'الجزء السفلي من الأمام', en: 'Lower body from front' },
    postures: ['standing'],
    directions: ['front'],
    regions: ['lower_body'],
    jointCodes: ['left_knee', 'right_knee', 'left_hip', 'right_hip', 'left_ankle', 'right_ankle'],
  },
  {
    code: 'standing_back_lower',
    name: { ar: 'خلفي سفلي - واقف', en: 'Standing Back Lower' },
    description: { ar: 'الجزء السفلي من الخلف', en: 'Lower body from back' },
    postures: ['standing'],
    directions: ['back'],
    regions: ['lower_body'],
    jointCodes: ['left_knee', 'right_knee', 'left_hip', 'right_hip', 'left_ankle', 'right_ankle'],
  },
  {
    code: 'standing_side_lower',
    name: { ar: 'جانبي سفلي - واقف', en: 'Standing Side Lower' },
    description: { ar: 'الجزء السفلي من الجانب', en: 'Lower body from side' },
    postures: ['standing'],
    directions: ['side'],
    regions: ['lower_body'],
    jointCodes: ['left_knee', 'right_knee', 'left_hip', 'right_hip', 'left_ankle', 'right_ankle'],
  },

  // ── Lying Prone (face down) ────────────────────────────────────────────
  {
    code: 'prone_side',
    name: { ar: 'نائم على الوجه - جانبي', en: 'Prone Side' },
    description: { ar: 'نائم على الوجه والكاميرا من الجانب', en: 'Lying face down, camera from side' },
    postures: ['lying_prone'],
    directions: ['side'],
    regions: ['full_body'],
    jointCodes: [
      'left_knee', 'right_knee', 'left_hip', 'right_hip',
      'left_shoulder', 'right_shoulder', 'left_elbow', 'right_elbow',
      'left_ankle', 'right_ankle', 'spine',
    ],
  },
  {
    code: 'prone_front',
    name: { ar: 'نائم على الوجه - أمامي', en: 'Prone Front' },
    description: { ar: 'نائم على الوجه والكاميرا من الأمام', en: 'Lying face down, camera from front' },
    postures: ['lying_prone'],
    directions: ['front'],
    regions: ['full_body'],
    jointCodes: [
      'left_shoulder', 'right_shoulder', 'left_hip', 'right_hip',
      'left_elbow', 'right_elbow', 'spine',
    ],
  },

  // ── Lying Supine (face up) ─────────────────────────────────────────────
  {
    code: 'supine_side',
    name: { ar: 'نائم على الظهر - جانبي', en: 'Supine Side' },
    description: { ar: 'نائم على الظهر والكاميرا من الجانب', en: 'Lying face up, camera from side' },
    postures: ['lying_supine'],
    directions: ['side'],
    regions: ['full_body'],
    jointCodes: [
      'left_knee', 'right_knee', 'left_hip', 'right_hip',
      'left_shoulder', 'right_shoulder', 'left_elbow', 'right_elbow',
      'left_ankle', 'right_ankle', 'spine',
    ],
  },
  {
    code: 'supine_front',
    name: { ar: 'نائم على الظهر - أمامي', en: 'Supine Front' },
    description: { ar: 'نائم على الظهر والكاميرا من الأمام', en: 'Lying face up, camera from front' },
    postures: ['lying_supine'],
    directions: ['front'],
    regions: ['full_body'],
    jointCodes: [
      'left_shoulder', 'right_shoulder', 'left_hip', 'right_hip',
      'left_knee', 'right_knee', 'spine',
    ],
  },

  // ── Lying on Side ──────────────────────────────────────────────────────
  {
    code: 'side_lying',
    name: { ar: 'نائم على الجانب', en: 'Side Lying' },
    description: { ar: 'نائم على الجانب والكاميرا من الجانب', en: 'Lying on side, camera from side' },
    postures: ['lying_side'],
    directions: ['side'],
    regions: ['full_body'],
    jointCodes: [
      'left_knee', 'right_knee', 'left_hip', 'right_hip',
      'left_shoulder', 'right_shoulder', 'spine',
    ],
  },
];

export async function seedPosePositions(prisma: PrismaClient) {
  const allJointCodes = [...new Set(POSITIONS.flatMap((p) => p.jointCodes))];
  const joints = await prisma.attributeValue.findMany({
    where: { code: { in: allJointCodes } },
  });
  const jointByCode = new Map(joints.map((j) => [j.code, j]));

  for (let i = 0; i < POSITIONS.length; i++) {
    const pos = POSITIONS[i];

    const record = await prisma.posePosition.upsert({
      where: { code: pos.code },
      update: {
        name: pos.name,
        description: pos.description,
        postures: pos.postures,
        directions: pos.directions,
        regions: pos.regions,
        sortOrder: i + 1,
      },
      create: {
        code: pos.code,
        name: pos.name,
        description: pos.description,
        postures: pos.postures,
        directions: pos.directions,
        regions: pos.regions,
        sortOrder: i + 1,
      },
    });

    for (const jCode of pos.jointCodes) {
      const joint = jointByCode.get(jCode);
      if (!joint) continue;

      await prisma.posePositionJoint.upsert({
        where: {
          posePositionId_jointId: {
            posePositionId: record.id,
            jointId: joint.id,
          },
        },
        update: {},
        create: {
          posePositionId: record.id,
          jointId: joint.id,
        },
      });
    }
  }

  console.log(`✅ ${POSITIONS.length} pose positions created`);
}
