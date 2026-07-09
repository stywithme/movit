import type { PrismaClient } from '@prisma/client';

const CORE_ENTRIES = [
  {
    slug: 'assessment_overhead_squat',
    sortOrder: 0,
    targetRegion: 'hip',
    side: 'bilateral',
    referenceNormDegrees: 115,
    thresholds: { excellent: 85, good: 65, average: 45, limited: 25 },
  },
  {
    slug: 'assessment_lunge',
    sortOrder: 1,
    targetRegion: 'knee',
    side: 'bilateral',
    referenceNormDegrees: 135,
    thresholds: { excellent: 85, good: 65, average: 45, limited: 25 },
  },
  {
    slug: 'assessment_shoulder_mobility',
    sortOrder: 2,
    targetRegion: 'shoulder',
    side: 'bilateral',
    referenceNormDegrees: 165,
    thresholds: { excellent: 85, good: 65, average: 45, limited: 25 },
  },
] as const;

const ADAPTIVE_ENTRIES = [
  {
    slug: 'assessment_forward_fold',
    sortOrder: 3,
    targetRegion: 'lower_back',
    side: 'center',
    referenceNormDegrees: 82.5,
    thresholds: { excellent: 85, good: 65, average: 45, limited: 25 },
    activationCondition: {
      metric: 'avgROM',
      operator: '<',
      value: 70,
      sourceExercise: 'assessment_overhead_squat',
    },
  },
  {
    slug: 'assessment_single_leg_balance',
    sortOrder: 4,
    targetRegion: 'balance',
    side: 'bilateral',
    referenceNormDegrees: null,
    thresholds: { excellent: 85, good: 65, average: 45, limited: 25 },
    activationCondition: {
      metric: 'avgStability',
      operator: '<',
      value: 60,
      sourceExercise: 'assessment_lunge',
    },
  },
] as const;

const TEMPLATE_DATA = {
  name: {
    en: 'Body Scan — Initial Assessment',
    ar: 'مسح الجسم — التقييم الأولي',
  },
  description: {
    en: 'Comprehensive body scan assessment to evaluate mobility, control, symmetry, and safety.',
    ar: 'تقييم شامل لمسح الجسم لتقييم المرونة والتحكم والتناظر والسلامة.',
  },
  type: 'initial',
  domainWeights: {
    mobility: 0.35,
    control: 0.25,
    symmetry: 0.2,
    safety: 0.2,
  },
  isDefault: true,
  isPublished: true,
  sortOrder: 0,
};

/**
 * Seed default assessment template (idempotent upsert).
 */
export async function seedAssessmentTemplates(prisma: PrismaClient) {
  console.log('📋 Seeding assessment templates...');

  const exercises = await prisma.exercise.findMany({
    where: {
      slug: {
        in: [
          'assessment_overhead_squat',
          'assessment_lunge',
          'assessment_shoulder_mobility',
          'assessment_forward_fold',
          'assessment_single_leg_balance',
        ],
      },
    },
    select: { id: true, slug: true },
  });

  const exerciseMap = new Map(exercises.map((e) => [e.slug, e.id]));

  const existing = await prisma.assessmentTemplate.findFirst({
    where: { type: 'initial', isDefault: true, deletedAt: null },
    select: { id: true },
  });

  const template = existing
    ? await prisma.assessmentTemplate.update({
        where: { id: existing.id },
        data: { ...TEMPLATE_DATA, deletedAt: null },
      })
    : await prisma.assessmentTemplate.create({
        data: TEMPLATE_DATA,
      });

  await prisma.assessmentTemplateExercise.deleteMany({
    where: { templateId: template.id },
  });

  let exerciseCount = 0;

  for (const entry of CORE_ENTRIES) {
    const exerciseId = exerciseMap.get(entry.slug);
    if (!exerciseId) {
      console.log(`  ⚠️  Exercise not found: ${entry.slug} — skipping`);
      continue;
    }
    await prisma.assessmentTemplateExercise.create({
      data: {
        templateId: template.id,
        exerciseId,
        sortOrder: entry.sortOrder,
        targetRegion: entry.targetRegion,
        side: entry.side,
        entryType: 'core',
        referenceNormDegrees: entry.referenceNormDegrees,
        thresholds: entry.thresholds,
      },
    });
    exerciseCount += 1;
  }

  for (const entry of ADAPTIVE_ENTRIES) {
    const exerciseId = exerciseMap.get(entry.slug);
    if (!exerciseId) {
      console.log(`  ⚠️  Exercise not found: ${entry.slug} — skipping`);
      continue;
    }
    await prisma.assessmentTemplateExercise.create({
      data: {
        templateId: template.id,
        exerciseId,
        sortOrder: entry.sortOrder,
        targetRegion: entry.targetRegion,
        side: entry.side,
        entryType: 'adaptive',
        activationCondition: entry.activationCondition,
        referenceNormDegrees: entry.referenceNormDegrees,
        thresholds: entry.thresholds,
      },
    });
    exerciseCount += 1;
  }

  console.log(`  ✅ Assessment template upserted with ${exerciseCount} exercises`);
}
