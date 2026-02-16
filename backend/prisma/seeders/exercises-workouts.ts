import type { PrismaClient } from '@prisma/client';
import * as fs from 'fs/promises';
import * as path from 'path';
import {
  buildLocalizedName,
  categoryCodeMap,
  equipmentNameMap,
  muscleCodeMap,
  muscleNameMap,
  tagNameMap,
} from './utils';
import type { EnsureMessageTemplate } from './messages';

export async function seedExercisesAndWorkouts(
  prisma: PrismaClient,
  ensureMessageTemplate: EnsureMessageTemplate
) {
  const assetsDir = path.resolve(__dirname, '../../../Docs/Old-way-json');
  const exercisesDir = path.join(assetsDir, 'exercises');
  const workoutsDir = path.join(assetsDir, 'workouts');

  const attributeValues = await prisma.attributeValue.findMany();
  const attributeValueByCode = new Map(attributeValues.map((value) => [value.code, value]));

  const categoryAttr = await prisma.attribute.findUnique({ where: { code: 'category' } });
  const muscleAttr = await prisma.attribute.findUnique({ where: { code: 'muscle' } });
  const equipmentAttr = await prisma.attribute.findUnique({ where: { code: 'equipment' } });
  const tagAttr = await prisma.attribute.findUnique({ where: { code: 'tag' } });
  const countingMethodAttr = await prisma.attribute.findUnique({ where: { code: 'counting_method' } });

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
    if (normalized === 'hold') return 'hold_tag';
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
      isBilateral?: boolean;
      bilateralConfig?: { switchEvery?: number; startSide?: string };
      supportsWeight?: boolean;
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

    const countingMethodValue = await prisma.attributeValue.findFirst({
      where: { code: exerciseJson.countingMethod, attributeId: countingMethodAttr.id },
    });
    if (!countingMethodValue) {
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
        isBilateral: exerciseJson.isBilateral ?? false,
        bilateralConfig: exerciseJson.bilateralConfig ? (exerciseJson.bilateralConfig as object) : undefined,
        supportsWeight: exerciseJson.supportsWeight ?? false,
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
        isBilateral: exerciseJson.isBilateral ?? false,
        bilateralConfig: exerciseJson.bilateralConfig ? (exerciseJson.bilateralConfig as object) : undefined,
        supportsWeight: exerciseJson.supportsWeight ?? false,
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

      const trackedJoints = Array.isArray(variant.trackedJoints)
        ? (variant.trackedJoints as Array<Record<string, unknown>>)
        : [];
      for (const joint of trackedJoints) {
        const jointData = joint as {
          joint?: string;
          code?: string;
          stateMessages?: Record<string, unknown>;
        };
        const jointCode = jointData.joint || jointData.code;
        if (!jointCode || !jointData.stateMessages) continue;
        const stateMessages = jointData.stateMessages as Record<string, unknown>;
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
      restBetweenExercisesMs?: number;
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
        status: 'published',
        publishedAt: new Date(),
      },
      create: {
        slug,
        name: workoutJson.name,
        description: workoutJson.description || undefined,
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

      const isLast = index >= workoutJson.exercises.length - 1;
      const restAfterExerciseMs = isLast
        ? 0
        : workoutJson.restBetweenExercisesMs ?? 60000;

      await prisma.workoutExercise.create({
        data: {
          workoutId: workoutRecord.id,
          exerciseId: exerciseRecord.id,
          variantIndex: exerciseEntry.variantIndex ?? 0,
          difficulty: exerciseEntry.difficulty ?? 'beginner',
          targetReps: exerciseEntry.target?.reps ?? undefined,
          targetDuration: exerciseEntry.target?.durationSec ?? undefined,
          sets: 1,
          restBetweenSetsMs: 30000,
          restAfterExerciseMs,
          notes: exerciseEntry.notes || undefined,
          sortOrder: index,
        },
      });
    }
  }

  console.log('✅ Workouts seeded from assets');
}
