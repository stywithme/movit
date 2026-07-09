import type { LoadCapability, MovementPattern, Prisma, PrismaClient } from '@prisma/client';
import * as fs from 'fs/promises';
import * as path from 'path';
import { validateExerciseConfig } from '../../src/lib/types/android-schema';
import { resolveExerciseBlueprintForSlug } from './exercise-manifest';
import type { EnsureMessageTemplate } from './messages';
import { applyPoseVariantsForExercise, type SeedPoseVariantJson } from './pose-variant-seed-helper';
import {
  buildLocalizedName,
  categoryCodeMap,
  equipmentNameMap,
  muscleCodeMap,
  muscleNameMap,
  tagNameMap,
} from './utils';

export type ExerciseJson = {
  slug?: string;
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
  bilateralConfig?: { switchMode?: 'every_rep' | 'after_all_reps'; switchEvery?: number; startSide?: string };
  supportsWeight?: boolean;
  poseVariants?: SeedPoseVariantJson[];
  movementPattern?: MovementPattern;
  loadCapability?: LoadCapability;
  familyKey?: string;
  familyOrder?: number;
  reportMetrics?: Prisma.InputJsonValue;
  status?: string;
  minWeight?: number | null;
  maxWeight?: number | null;
  defaultWeight?: number | null;
  _database?: {
    minWeight?: number | null;
    maxWeight?: number | null;
    defaultWeight?: number | null;
  };
};

export type ParsedExerciseJsonFile = {
  file: string;
  slug: string;
  data: ExerciseJson;
};

export async function existsDir(dir: string): Promise<boolean> {
  return fs.stat(dir).then((stat) => stat.isDirectory()).catch(() => false);
}

export async function resolveExerciseJsonDir(input: string): Promise<string> {
  const absolute = path.resolve(process.cwd(), input);
  const nested = path.join(absolute, 'exercises-from-db');
  if (await existsDir(nested)) return nested;
  if (await existsDir(absolute)) return absolute;
  throw new Error(`Exercise JSON directory not found: ${absolute}`);
}

export async function loadExerciseJsonFilesFromDir(
  dir: string,
  onlySlugs: Set<string> | null = null,
): Promise<ParsedExerciseJsonFile[]> {
  const dirExists = await existsDir(dir);
  if (!dirExists) {
    throw new Error(`Exercise JSON directory not found: ${dir}`);
  }

  const files = (await fs.readdir(dir))
    .filter((file) => file.endsWith('.json'))
    .sort();

  const parsed: ParsedExerciseJsonFile[] = [];
  for (const file of files) {
    const slug = path.basename(file, '.json');
    if (onlySlugs && !onlySlugs.has(slug)) continue;
    const fullPath = path.join(dir, file);
    const data = JSON.parse(await fs.readFile(fullPath, 'utf8')) as ExerciseJson;
    parsed.push({ file: fullPath, slug, data });
  }

  return parsed;
}

export function validateExerciseJsonFiles(files: ParsedExerciseJsonFile[]): void {
  const errors: string[] = [];
  for (const item of files) {
    if (item.data.slug && item.data.slug !== item.slug) {
      errors.push(`${item.file}: embedded slug "${item.data.slug}" does not match filename "${item.slug}"`);
    }
    const validationErrors = validateExerciseConfig(item.data as never);
    for (const err of validationErrors) {
      errors.push(`${item.file}: ${err}`);
    }
  }

  if (errors.length > 0) {
    throw new Error(`Exercise JSON validation failed:\n${errors.map((err) => `- ${err}`).join('\n')}`);
  }
}

export async function seedExerciseJsonBatch(
  prisma: PrismaClient,
  files: ParsedExerciseJsonFile[],
  ensureMessageTemplate: EnsureMessageTemplate,
): Promise<Set<string>> {
  const seededSlugs = new Set<string>();
  const attributeValues = await prisma.attributeValue.findMany();
  const attributeValueByCode = new Map(attributeValues.map((value) => [value.code, value]));

  const categoryAttr = await prisma.attribute.findUnique({ where: { code: 'category' } });
  const muscleAttr = await prisma.attribute.findUnique({ where: { code: 'muscle' } });
  const equipmentAttr = await prisma.attribute.findUnique({ where: { code: 'equipment' } });
  const tagAttr = await prisma.attribute.findUnique({ where: { code: 'tag' } });
  const countingMethodAttr = await prisma.attribute.findUnique({ where: { code: 'counting_method' } });

  if (!categoryAttr || !muscleAttr || !equipmentAttr || !tagAttr || !countingMethodAttr) {
    throw new Error('Required attributes are missing. Run seed:base first.');
  }

  const ensureAttributeValue = async (
    attributeId: string,
    code: string,
    name: { ar: string; en: string },
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

  const posePositions = await prisma.posePosition.findMany({
    where: { isActive: true },
    orderBy: { sortOrder: 'asc' },
  });
  const positionByCode = new Map<string, string>();
  for (const pp of posePositions) {
    positionByCode.set(pp.code, pp.id);
  }

  for (const { slug, data: exerciseJson } of files) {
    seededSlugs.add(slug);

    const categoryCode = categoryCodeMap[exerciseJson.category.code] || exerciseJson.category.code;
    const categoryValue = await ensureAttributeValue(
      categoryAttr.id,
      categoryCode,
      exerciseJson.category.name,
    );

    const countingMethodValue = await prisma.attributeValue.findFirst({
      where: { code: exerciseJson.countingMethod, attributeId: countingMethodAttr.id },
    });
    if (!countingMethodValue) {
      throw new Error(`Counting method not found for ${slug}: ${exerciseJson.countingMethod}`);
    }

    const inferredBlueprint = resolveExerciseBlueprintForSlug(slug, {
      countingMethodCode: countingMethodValue.code,
      supportsWeight: exerciseJson.supportsWeight ?? false,
      equipmentCodes: exerciseJson.equipment || [],
      categoryCode: categoryValue.code,
    });

    const blueprint = {
      movementPattern: exerciseJson.movementPattern ?? inferredBlueprint.movementPattern,
      loadCapability: exerciseJson.loadCapability ?? inferredBlueprint.loadCapability,
      familyKey: exerciseJson.familyKey ?? inferredBlueprint.familyKey,
      familyOrder: exerciseJson.familyOrder ?? inferredBlueprint.familyOrder,
      reportMetrics: (exerciseJson.reportMetrics ?? inferredBlueprint.reportMetrics) as Prisma.InputJsonValue,
    };

    const dbWeights = exerciseJson._database;
    const minWeight = exerciseJson.minWeight ?? dbWeights?.minWeight ?? undefined;
    const maxWeight = exerciseJson.maxWeight ?? dbWeights?.maxWeight ?? undefined;
    const defaultWeight = exerciseJson.defaultWeight ?? dbWeights?.defaultWeight ?? undefined;

    const status = exerciseJson.status === 'draft' ? 'draft' : 'published';
    const publishedAt = status === 'published' ? new Date() : null;

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
        minWeight: minWeight ?? null,
        maxWeight: maxWeight ?? null,
        defaultWeight: defaultWeight ?? null,
        status,
        publishedAt,
        movementPattern: blueprint.movementPattern as MovementPattern,
        loadCapability: blueprint.loadCapability as LoadCapability,
        familyKey: blueprint.familyKey,
        familyOrder: blueprint.familyOrder,
        reportMetrics: blueprint.reportMetrics,
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
        minWeight: minWeight ?? null,
        maxWeight: maxWeight ?? null,
        defaultWeight: defaultWeight ?? null,
        status,
        publishedAt,
        movementPattern: blueprint.movementPattern as MovementPattern,
        loadCapability: blueprint.loadCapability as LoadCapability,
        familyKey: blueprint.familyKey,
        familyOrder: blueprint.familyOrder,
        reportMetrics: blueprint.reportMetrics,
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
        buildLocalizedName(normalized, muscleNameMap),
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
        buildLocalizedName(normalized, equipmentNameMap),
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
        buildLocalizedName(normalized, tagNameMap),
      );
      tagIds.push(created.id);
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

    await applyPoseVariantsForExercise(prisma, {
      exerciseId: exerciseRecord.id,
      slug,
      poseVariants: exerciseJson.poseVariants || [],
      positionByCode,
      ensureMessageTemplate,
    });

    console.log(`  - ${slug}`);
  }

  return seededSlugs;
}

export async function seedExerciseJsonFromDirectories(
  prisma: PrismaClient,
  ensureMessageTemplate: EnsureMessageTemplate,
  directories: string[],
): Promise<Set<string>> {
  const allSeeded = new Set<string>();

  for (const dirInput of directories) {
    const dir = await resolveExerciseJsonDir(dirInput);
    const files = await loadExerciseJsonFilesFromDir(dir);
    if (files.length === 0) {
      console.warn(`⚠️ No exercise JSON files in ${dir}`);
      continue;
    }

    validateExerciseJsonFiles(files);
    console.log(`📚 Seeding ${files.length} exercise(s) from ${dir}`);
    const seeded = await seedExerciseJsonBatch(prisma, files, ensureMessageTemplate);
    for (const slug of seeded) allSeeded.add(slug);
  }

  return allSeeded;
}
