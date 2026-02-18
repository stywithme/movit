import { PrismaClient } from '@prisma/client';

/**
 * Seed default assessment template.
 *
 * Creates the "Body Scan — Initial Assessment" template
 * with the 3 core exercises and 2 adaptive exercises,
 * matching the hardcoded assessment in the Android app.
 */
export async function seedAssessmentTemplates(prisma: PrismaClient) {
  console.log('📋 Seeding assessment templates...');

  // Get exercise IDs by slug
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

  const template = await prisma.assessmentTemplate.create({
    data: {
      name: {
        en: 'Body Scan — Initial Assessment',
        ar: 'مسح الجسم — التقييم الأولي',
      },
      description: {
        en: 'Comprehensive body scan assessment to evaluate mobility, control, symmetry, and safety.',
        ar: 'تقييم شامل لمسح الجسم لتقييم المرونة والتحكم والتناظر والسلامة.',
      },
      type: 'initial',
      levelRangeMin: 1,
      levelRangeMax: 5,
      domainWeights: {
        mobility: 0.35,
        control: 0.25,
        symmetry: 0.20,
        safety: 0.20,
      },
      isDefault: true,
      isPublished: true,
      sortOrder: 0,
    },
  });

  // Core exercises
  const coreEntries = [
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
  ];

  for (const entry of coreEntries) {
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
  }

  // Adaptive exercises
  const adaptiveEntries = [
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
  ];

  for (const entry of adaptiveEntries) {
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
  }

  const exerciseCount = coreEntries.length + adaptiveEntries.filter((e) => exerciseMap.has(e.slug)).length;
  console.log(`  ✅ Default assessment template created with ${exerciseCount} exercises`);
}
