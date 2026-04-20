import type { LoadCapability, MovementPattern, Prisma, PrismaClient } from '@prisma/client';
import type { EnsureMessageTemplate } from './messages';
import { CURATED_EXTENSION_EXERCISES } from './curated-catalog-extension';
import { buildCuratedPoseVariants } from './curated-pose-blueprints';
import { applyPoseVariantsForExercise } from './pose-variant-seed-helper';

export function inferExerciseBlueprintFields(
  slug: string,
  ctx: {
    countingMethodCode: string;
    supportsWeight: boolean;
    equipmentCodes: string[];
    categoryCode: string;
  },
): {
  movementPattern: MovementPattern;
  loadCapability: LoadCapability;
  familyKey: string;
  familyOrder: number;
  reportMetrics: Prisma.InputJsonValue;
} {
  const s = slug.toLowerCase();
  const cm = (ctx.countingMethodCode || '').toLowerCase();
  const eq = ctx.equipmentCodes.map((c) => c.toLowerCase());

  let movementPattern: MovementPattern = 'OTHER';

  if (s.includes('assessment') || s.includes('mobility') || s.includes('fold')) {
    movementPattern = 'MOBILITY_DRILL';
  } else if (s.includes('squat') || s.includes('wall_sit')) {
    movementPattern = 'SQUAT';
  } else if (s.includes('deadlift') || s.includes('hinge') || s.includes('rdl')) {
    movementPattern = 'HINGE';
  } else if (s.includes('lunge') || s.includes('split')) {
    movementPattern = 'LUNGE';
  } else if (s.includes('pushup') || s.includes('push_up') || s.includes('dip')) {
    movementPattern = 'PUSH_HORIZONTAL';
  } else if (s.includes('press') || s.includes('shoulder')) {
    movementPattern = 'PUSH_VERTICAL';
  } else if (s.includes('row') || s.includes('pull')) {
    movementPattern = s.includes('up') ? 'PULL_VERTICAL' : 'PULL_HORIZONTAL';
  } else if (s.includes('plank') || s.includes('crunch') || s.includes('bridge') || s.includes('superman')) {
    movementPattern = 'CORE_BRACE';
  } else if (s.includes('calf') || s.includes('walk') || s.includes('march')) {
    movementPattern = 'GAIT';
  } else if (s.includes('lateral') || s.includes('raise')) {
    movementPattern = 'PUSH_VERTICAL';
  } else if (s.includes('curl')) {
    movementPattern = 'OTHER';
  } else if (ctx.categoryCode === 'legs') {
    movementPattern = 'LUNGE';
  } else if (ctx.categoryCode === 'chest') {
    movementPattern = 'PUSH_HORIZONTAL';
  } else if (ctx.categoryCode === 'back') {
    movementPattern = 'PULL_VERTICAL';
  } else if (ctx.categoryCode === 'abs') {
    movementPattern = 'CORE_BRACE';
  }

  if (cm.includes('hold') || cm.includes('duration') || cm.includes('time')) {
    if (movementPattern === 'OTHER') movementPattern = 'CORE_BRACE';
  }

  const heavyEquipment = eq.some((c) => c.includes('barbell'));
  const optionalLoad = eq.some((c) =>
    ['dumbbell', 'kettlebell', 'barbell', 'bench', 'cable'].some((x) => c.includes(x)),
  );

  let loadCapability: LoadCapability = 'BODYWEIGHT_ONLY';
  if (ctx.supportsWeight || heavyEquipment) {
    loadCapability = heavyEquipment ? 'EXTERNAL_LOAD_REQUIRED' : 'EXTERNAL_LOAD_OPTIONAL';
  } else if (optionalLoad) {
    loadCapability = 'EXTERNAL_LOAD_OPTIONAL';
  }

  const familyKey = `${movementPattern.toLowerCase()}_family`;
  const familyOrder = (slug.length + ctx.categoryCode.length) % 240;

  const isHold = cm.includes('hold');
  const reportMetrics: Prisma.InputJsonValue = {
    version: 1,
    mode: isHold ? 'duration' : 'reps',
    primaryKpi: isHold ? 'timeUnderTension' : 'repQuality',
    secondaryKpis: ['avgFormScore', 'avgRom'],
  };

  return { movementPattern, loadCapability, familyKey, familyOrder, reportMetrics };
}

function reportMetricsForCounting(countingMethodCode: string): Prisma.InputJsonValue {
  const isHold = countingMethodCode.toLowerCase() === 'hold';
  return {
    version: 1,
    mode: isHold ? 'duration' : 'reps',
    primaryKpi: isHold ? 'timeUnderTension' : 'repQuality',
    secondaryKpis: ['avgFormScore', 'avgRom'],
  };
}

/**
 * Upserts curated library exercises (no numeric filler). Safe after JSON import.
 */
export async function seedCuratedCatalogExtensions(
  prisma: PrismaClient,
  ensureMessageTemplate: EnsureMessageTemplate,
) {
  const posePositions = await prisma.posePosition.findMany({
    where: { isActive: true },
    orderBy: { sortOrder: 'asc' },
  });
  const positionByCode = new Map<string, string>();
  for (const pp of posePositions) {
    positionByCode.set(pp.code, pp.id);
  }
  if (!positionByCode.has('standing_side')) {
    throw new Error('Pose position standing_side is required before seeding curated catalog exercises');
  }

  const categoryAttr = await prisma.attribute.findUnique({ where: { code: 'category' } });
  const muscleAttr = await prisma.attribute.findUnique({ where: { code: 'muscle' } });
  const equipmentAttr = await prisma.attribute.findUnique({ where: { code: 'equipment' } });
  const tagAttr = await prisma.attribute.findUnique({ where: { code: 'tag' } });
  const countingMethodAttr = await prisma.attribute.findUnique({ where: { code: 'counting_method' } });
  if (!categoryAttr || !muscleAttr || !equipmentAttr || !tagAttr || !countingMethodAttr) {
    throw new Error('Required attributes are missing for curated catalog exercise seeding');
  }

  const upDown = await prisma.attributeValue.findFirst({
    where: { code: 'up_down', attributeId: countingMethodAttr.id },
  });
  const hold = await prisma.attributeValue.findFirst({
    where: { code: 'hold', attributeId: countingMethodAttr.id },
  });
  if (!upDown || !hold) {
    throw new Error('Counting methods up_down / hold are required');
  }

  const attributeValues = await prisma.attributeValue.findMany();
  const attributeValueByCode = new Map(attributeValues.map((v) => [v.code, v]));

  const ensureAttributeValue = async (attributeId: string, code: string, name: { ar: string; en: string }) => {
    const existing = attributeValueByCode.get(code);
    if (existing) return existing;
    const created = await prisma.attributeValue.upsert({
      where: { code },
      update: { name },
      create: { code, name, attributeId, sortOrder: 900 },
    });
    attributeValueByCode.set(code, created);
    return created;
  };

  console.log(`📚 Seeding ${CURATED_EXTENSION_EXERCISES.length} curated catalog exercises…`);

  for (const row of CURATED_EXTENSION_EXERCISES) {
    const categoryValue =
      attributeValueByCode.get(row.categoryCode) ||
      (await ensureAttributeValue(categoryAttr.id, row.categoryCode, {
        ar: row.categoryCode,
        en: row.categoryCode,
      }));

    const countingMethod = row.countingMethodCode === 'hold' ? hold : upDown;

    const exerciseRecord = await prisma.exercise.upsert({
      where: { slug: row.slug },
      update: {
        name: row.name,
        description: row.description,
        instructions: row.instructions,
        categoryId: categoryValue.id,
        countingMethodId: countingMethod.id,
        status: 'published',
        publishedAt: new Date(),
        supportsWeight: row.supportsWeight,
        minWeight: row.minWeight ?? null,
        maxWeight: row.maxWeight ?? null,
        defaultWeight: row.defaultWeight ?? null,
        isBilateral: row.isBilateral,
        movementPattern: row.movementPattern,
        loadCapability: row.loadCapability,
        familyKey: row.familyKey,
        familyOrder: row.familyOrder,
        reportMetrics: reportMetricsForCounting(row.countingMethodCode),
      },
      create: {
        slug: row.slug,
        name: row.name,
        description: row.description,
        instructions: row.instructions,
        categoryId: categoryValue.id,
        countingMethodId: countingMethod.id,
        status: 'published',
        publishedAt: new Date(),
        supportsWeight: row.supportsWeight,
        minWeight: row.minWeight ?? null,
        maxWeight: row.maxWeight ?? null,
        defaultWeight: row.defaultWeight ?? null,
        isBilateral: row.isBilateral,
        movementPattern: row.movementPattern,
        loadCapability: row.loadCapability,
        familyKey: row.familyKey,
        familyOrder: row.familyOrder,
        reportMetrics: reportMetricsForCounting(row.countingMethodCode),
      },
    });

    const muscleIds: string[] = [];
    for (const code of row.muscles) {
      const v =
        attributeValueByCode.get(code) ||
        (await ensureAttributeValue(muscleAttr.id, code, { ar: code, en: code }));
      if (v.attributeId === muscleAttr.id) muscleIds.push(v.id);
    }

    const equipmentIds: string[] = [];
    for (const code of row.equipment) {
      const v =
        attributeValueByCode.get(code) ||
        (await ensureAttributeValue(equipmentAttr.id, code, { ar: code, en: code }));
      if (v.attributeId === equipmentAttr.id) equipmentIds.push(v.id);
    }

    const tagIds: string[] = [];
    for (const code of row.tags) {
      const v =
        attributeValueByCode.get(code) ||
        (await ensureAttributeValue(tagAttr.id, code, { ar: code, en: code }));
      if (v.attributeId === tagAttr.id) tagIds.push(v.id);
    }

    await prisma.exerciseAttribute.deleteMany({ where: { exerciseId: exerciseRecord.id } });
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

    const poseVariants = buildCuratedPoseVariants(row);
    await applyPoseVariantsForExercise(prisma, {
      exerciseId: exerciseRecord.id,
      slug: row.slug,
      poseVariants,
      positionByCode,
      ensureMessageTemplate,
    });
  }

  const total = await prisma.exercise.count();
  console.log(`✅ Curated catalog exercises seeded (total exercises: ${total})`);
}
