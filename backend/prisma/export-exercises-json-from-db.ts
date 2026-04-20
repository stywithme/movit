/**
 * Export all exercises from the database to JSON files (Exercise-json shape + DB metadata).
 *
 * Usage:
 *   tsx --env-file=.env prisma/export-exercises-json-from-db.ts
 *   tsx --env-file=.env prisma/export-exercises-json-from-db.ts --out=prisma/Exercise-json/exercises-from-db
 *
 * Default --out: prisma/Exercise-json/exercises-from-db (does not overwrite hand-authored exercises/*.json)
 */

import { PrismaClient } from '@prisma/client';
import { PrismaPg } from '@prisma/adapter-pg';
import * as fs from 'fs/promises';
import * as path from 'path';

import {
  buildExerciseConfig,
  buildAndValidateExerciseConfig,
  exerciseFullInclude,
} from '../src/modules/exercises/json-builder';

function parseOutArg(): string {
  const raw = process.argv.find((a) => a.startsWith('--out='));
  if (raw) {
    const v = raw.slice('--out='.length).trim();
    return path.isAbsolute(v) ? v : path.resolve(process.cwd(), v);
  }
  return path.resolve(process.cwd(), 'prisma/Exercise-json/exercises-from-db');
}

/** Canonical pose code → legacy seed key (when unambiguous), for round-trip familiarity */
const POSE_TO_LEGACY_CAMERA: Record<string, string> = {
  standing_side: 'side_view',
  standing_front: 'front_view',
  standing_back: 'back_view',
  standing_side_left: 'side_view_left',
  standing_side_right: 'side_view_right',
  standing_diagonal: 'diagonal_view',
};

const exportInclude = {
  ...exerciseFullInclude,
  media: {
    orderBy: { sortOrder: 'asc' as const },
    select: {
      id: true,
      url: true,
      type: true,
      isPrimary: true,
      sortOrder: true,
      altText: true,
    },
  },
} as const;

function attachLegacyCameraPosition(config: ReturnType<typeof buildExerciseConfig>) {
  const next = structuredClone(config) as typeof config;
  for (const pv of next.poseVariants) {
    const legacy = POSE_TO_LEGACY_CAMERA[pv.posePosition];
    if (legacy) {
      (pv as unknown as Record<string, unknown>).cameraPosition = legacy;
    }
  }
  return next;
}

if (!process.env.DATABASE_URL) {
  throw new Error('DATABASE_URL is not set');
}

const adapter = new PrismaPg({ connectionString: process.env.DATABASE_URL });
const prisma = new PrismaClient({ adapter });

async function main() {
  const outDir = parseOutArg();
  await fs.mkdir(outDir, { recursive: true });

  const exercises = await prisma.exercise.findMany({
    where: { deletedAt: null },
    include: exportInclude,
    orderBy: { slug: 'asc' },
  });

  let ok = 0;
  let warn = 0;

  for (const ex of exercises) {
    let validationNote: string | null = null;
    try {
      buildAndValidateExerciseConfig(ex as never);
    } catch (e) {
      validationNote = e instanceof Error ? e.message : String(e);
      warn += 1;
    }

    const androidConfig = attachLegacyCameraPosition(buildExerciseConfig(ex as never));

    const payload = {
      slug: ex.slug,
      status: ex.status,
      movementPattern: ex.movementPattern,
      loadCapability: ex.loadCapability,
      familyKey: ex.familyKey,
      familyOrder: ex.familyOrder,
      ...androidConfig,
      _database: {
        id: ex.id,
        categoryId: ex.categoryId,
        countingMethodId: ex.countingMethodId,
        publishedAt: ex.publishedAt?.toISOString() ?? null,
        createdAt: ex.createdAt.toISOString(),
        updatedAt: ex.updatedAt.toISOString(),
        deletedAt: ex.deletedAt?.toISOString() ?? null,
        isFeatured: ex.isFeatured,
        createdBy: ex.createdBy,
        updatedBy: ex.updatedBy,
        repCountingConfig: ex.repCountingConfig,
        bilateralConfig: ex.bilateralConfig,
        isBilateral: ex.isBilateral,
        supportsWeight: ex.supportsWeight,
        minWeight: ex.minWeight,
        maxWeight: ex.maxWeight,
        defaultWeight: ex.defaultWeight,
        reportMetrics: ex.reportMetrics,
        media: ex.media,
        poseVariants: ex.poseVariants.map((pv) => ({
          id: pv.id,
          sortOrder: pv.sortOrder,
          description: pv.description,
          trackedJointsConfig: pv.trackedJointsConfig,
        })),
        validationWarning: validationNote,
      },
    };

    const filePath = path.join(outDir, `${ex.slug}.json`);
    await fs.writeFile(filePath, `${JSON.stringify(payload, null, 2)}\n`, 'utf8');
    ok += 1;
  }

  console.log(`Exported ${ok} exercise JSON file(s) → ${outDir}`);
  if (warn > 0) {
    console.warn(`⚠️ ${warn} exercise(s) had validation warnings (see _database.validationWarning per file)`);
  }
}

main()
  .catch((e) => {
    console.error(e);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
