/**
 * Levels Admin Service
 * ====================
 *
 * CRUD operations for the Level model.
 * Used by the admin dashboard to manage training levels.
 *
 *   list()              — All levels ordered by number, with user counts
 *   getById(id)         — Single level by ID
 *   create(data)        — Create new level (validates threshold overlaps)
 *   update(id, data)    — Update level (validates threshold overlaps)
 *   delete(id)          — Delete level (with safety checks)
 *   reorder(orderedIds) — Reorder levels by updating number field
 */

import { getPrisma } from '@/lib/prisma/client';
import { Prisma } from '@prisma/client';

// ── Types ──

export interface LevelCreateData {
  number: number;
  code: string;
  name: Record<string, string>; // { en: "...", ar: "..." }
  description?: Record<string, string> | null;
  color?: string | null;
  icon?: string | null;
  entryThreshold: number;
  maxThreshold?: number | null;
  defaultSetsMin?: number;
  defaultSetsMax?: number;
  defaultRepsMin?: number;
  defaultRepsMax?: number;
  defaultIntensityGuide?: string;
  defaultRestBetweenSetsMs?: number;
  defaultWorkoutDurMin?: number;
  defaultWorkoutDurMax?: number;
  defaultWeeklyFreqMin?: number;
  defaultWeeklyFreqMax?: number;
}

function toPrismaJson(val: Record<string, string> | null | undefined): Prisma.InputJsonValue | undefined {
  if (val === null || val === undefined) return undefined;
  return val as unknown as Prisma.InputJsonValue;
}

export type LevelUpdateData = Partial<LevelCreateData>;

// ── Helpers ──

interface ThresholdRange {
  id: string;
  number: number;
  entryThreshold: number;
  maxThreshold: number | null;
}

/**
 * Validate that level thresholds don't overlap or leave gaps.
 * Each level's entryThreshold must equal the previous level's maxThreshold.
 * The first level must start at 0, the last level's maxThreshold should be null (unbounded).
 *
 * @throws Error if thresholds are invalid.
 */
function validateThresholds(levels: ThresholdRange[]): void {
  if (levels.length === 0) return;

  const sorted = [...levels].sort((a, b) => a.number - b.number);

  // First level must start at 0
  if (sorted[0].entryThreshold !== 0) {
    throw new Error('First level must have entryThreshold of 0');
  }

  // Check consecutive levels for overlaps/gaps
  for (let i = 0; i < sorted.length - 1; i++) {
    const current = sorted[i];
    const next = sorted[i + 1];

    if (current.maxThreshold == null) {
      throw new Error(
        `Level ${current.number} ("number ${current.number}") has no upper threshold but is not the last level`,
      );
    }

    // The next level's entry must equal current level's max (no gaps or overlaps)
    if (next.entryThreshold !== current.maxThreshold) {
      throw new Error(
        `Threshold gap/overlap between level ${current.number} (max ${current.maxThreshold}) ` +
          `and level ${next.number} (entry ${next.entryThreshold})`,
      );
    }
  }

  // Last level should have maxThreshold = null (unbounded upper end)
  const last = sorted[sorted.length - 1];
  if (last.maxThreshold != null) {
    throw new Error('Last level must have maxThreshold of null (unbounded upper end)');
  }
}

// ── Service ──

export const levelsAdminService = {
  /**
   * List all levels ordered by number ascending, including user counts.
   */
  async list() {
    const prisma = await getPrisma();

    const levels = await prisma.level.findMany({
      orderBy: { number: 'asc' },
    });

    // Count users at each level via UserLevelProfile.overallLevel.
    // We get the latest profile per user to avoid double-counting.
    const userCounts = await prisma.$queryRawUnsafe<
      { overallLevel: number; count: bigint }[]
    >(
      `SELECT ulp."overallLevel", COUNT(DISTINCT ulp."userId") as count
       FROM "user_level_profiles" ulp
       INNER JOIN (
         SELECT "userId", MAX("classifiedAt") as "latestAt"
         FROM "user_level_profiles"
         GROUP BY "userId"
       ) latest ON ulp."userId" = latest."userId" AND ulp."classifiedAt" = latest."latestAt"
       GROUP BY ulp."overallLevel"`,
    );

    const countMap = new Map<number, number>();
    for (const row of userCounts) {
      countMap.set(row.overallLevel, Number(row.count));
    }

    return levels.map((level) => ({
      ...level,
      userCount: countMap.get(level.number) ?? 0,
    }));
  },

  /**
   * Get a single level by ID.
   */
  async getById(id: string) {
    const prisma = await getPrisma();
    return prisma.level.findUnique({ where: { id } });
  },

  /**
   * Create a new level. Validates threshold consistency.
   */
  async create(data: LevelCreateData) {
    const prisma = await getPrisma();

    // Check for duplicate number or code
    const existing = await prisma.level.findFirst({
      where: { OR: [{ number: data.number }, { code: data.code }] },
    });
    if (existing) {
      throw new Error(
        existing.number === data.number
          ? `A level with number ${data.number} already exists`
          : `A level with code "${data.code}" already exists`,
      );
    }

    // Build projected list including the new level and validate thresholds
    const allLevels = await prisma.level.findMany({
      select: { id: true, number: true, entryThreshold: true, maxThreshold: true },
    });

    const projected: ThresholdRange[] = [
      ...allLevels,
      {
        id: 'new',
        number: data.number,
        entryThreshold: data.entryThreshold,
        maxThreshold: data.maxThreshold ?? null,
      },
    ];

    validateThresholds(projected);

    const prismaData: Prisma.LevelCreateInput = {
      number: data.number,
      code: data.code,
      name: data.name as unknown as Prisma.InputJsonValue,
      description: data.description ? (data.description as unknown as Prisma.InputJsonValue) : Prisma.JsonNull,
      color: data.color ?? undefined,
      icon: data.icon ?? undefined,
      entryThreshold: data.entryThreshold,
      maxThreshold: data.maxThreshold ?? undefined,
      defaultSetsMin: data.defaultSetsMin,
      defaultSetsMax: data.defaultSetsMax,
      defaultRepsMin: data.defaultRepsMin,
      defaultRepsMax: data.defaultRepsMax,
      defaultIntensityGuide: data.defaultIntensityGuide,
      defaultRestBetweenSetsMs: data.defaultRestBetweenSetsMs,
      defaultWorkoutDurMin: data.defaultWorkoutDurMin,
      defaultWorkoutDurMax: data.defaultWorkoutDurMax,
      defaultWeeklyFreqMin: data.defaultWeeklyFreqMin,
      defaultWeeklyFreqMax: data.defaultWeeklyFreqMax,
    };

    return prisma.level.create({ data: prismaData });
  },

  /**
   * Update a level. Validates threshold consistency.
   */
  async update(id: string, data: LevelUpdateData) {
    const prisma = await getPrisma();

    const level = await prisma.level.findUnique({ where: { id } });
    if (!level) throw new Error('Level not found');

    // Check for duplicate number or code (excluding current)
    if (data.number != null || data.code != null) {
      const conditions: any[] = [];
      if (data.number != null) conditions.push({ number: data.number });
      if (data.code != null) conditions.push({ code: data.code });

      const conflict = await prisma.level.findFirst({
        where: { OR: conditions, NOT: { id } },
      });
      if (conflict) {
        throw new Error(
          conflict.number === data.number
            ? `A level with number ${data.number} already exists`
            : `A level with code "${data.code}" already exists`,
        );
      }
    }

    // Validate thresholds if any threshold fields changed
    if (data.entryThreshold != null || data.maxThreshold !== undefined || data.number != null) {
      const allLevels = await prisma.level.findMany({
        select: { id: true, number: true, entryThreshold: true, maxThreshold: true },
      });

      const projected: ThresholdRange[] = allLevels.map((l) =>
        l.id === id
          ? {
              id: l.id,
              number: data.number ?? l.number,
              entryThreshold: data.entryThreshold ?? l.entryThreshold,
              maxThreshold: data.maxThreshold !== undefined ? (data.maxThreshold ?? null) : l.maxThreshold,
            }
          : l,
      );

      validateThresholds(projected);
    }

    const prismaData: Prisma.LevelUpdateInput = {};
    if (data.number != null) prismaData.number = data.number;
    if (data.code != null) prismaData.code = data.code;
    if (data.name) prismaData.name = data.name as unknown as Prisma.InputJsonValue;
    if (data.description !== undefined) {
      prismaData.description = data.description
        ? (data.description as unknown as Prisma.InputJsonValue)
        : Prisma.JsonNull;
    }
    if (data.color !== undefined) prismaData.color = data.color;
    if (data.icon !== undefined) prismaData.icon = data.icon;
    if (data.entryThreshold != null) prismaData.entryThreshold = data.entryThreshold;
    if (data.maxThreshold !== undefined) prismaData.maxThreshold = data.maxThreshold;
    if (data.defaultSetsMin != null) prismaData.defaultSetsMin = data.defaultSetsMin;
    if (data.defaultSetsMax != null) prismaData.defaultSetsMax = data.defaultSetsMax;
    if (data.defaultRepsMin != null) prismaData.defaultRepsMin = data.defaultRepsMin;
    if (data.defaultRepsMax != null) prismaData.defaultRepsMax = data.defaultRepsMax;
    if (data.defaultIntensityGuide) prismaData.defaultIntensityGuide = data.defaultIntensityGuide;
    if (data.defaultRestBetweenSetsMs != null) prismaData.defaultRestBetweenSetsMs = data.defaultRestBetweenSetsMs;
    if (data.defaultWorkoutDurMin != null) prismaData.defaultWorkoutDurMin = data.defaultWorkoutDurMin;
    if (data.defaultWorkoutDurMax != null) prismaData.defaultWorkoutDurMax = data.defaultWorkoutDurMax;
    if (data.defaultWeeklyFreqMin != null) prismaData.defaultWeeklyFreqMin = data.defaultWeeklyFreqMin;
    if (data.defaultWeeklyFreqMax != null) prismaData.defaultWeeklyFreqMax = data.defaultWeeklyFreqMax;

    return prisma.level.update({ where: { id }, data: prismaData });
  },

  /**
   * Delete a level with safety checks:
   *   1. Cannot delete the last remaining level.
   *   2. Cannot delete if users are at this level.
   *   3. Cannot delete if programs reference this level number.
   */
  async delete(id: string) {
    const prisma = await getPrisma();

    const level = await prisma.level.findUnique({ where: { id } });
    if (!level) throw new Error('Level not found');

    // 1. Not the last level
    const totalLevels = await prisma.level.count();
    if (totalLevels <= 1) {
      throw new Error('Cannot delete the last remaining level');
    }

    // 2. No users at this level (check latest UserLevelProfile per user)
    const usersAtLevel = await prisma.userLevelProfile.findFirst({
      where: { overallLevel: level.number },
    });
    if (usersAtLevel) {
      throw new Error(
        `Cannot delete level ${level.number}: users are currently at this level`,
      );
    }

    // 3. No programs with levelRangeMin/Max matching this level number
    const programsUsingLevel = await prisma.program.findFirst({
      where: {
        deletedAt: null,
        OR: [
          { levelRangeMin: level.number },
          { levelRangeMax: level.number },
        ],
      },
    });
    if (programsUsingLevel) {
      throw new Error(
        `Cannot delete level ${level.number}: programs reference this level range`,
      );
    }

    return prisma.level.delete({ where: { id } });
  },

  /**
   * Reorder levels by updating their `number` field sequentially.
   * @param orderedIds - Array of level IDs in desired order (first = number 1, etc.)
   */
  async reorder(orderedIds: string[]) {
    const prisma = await getPrisma();

    const allLevels = await prisma.level.findMany();
    if (orderedIds.length !== allLevels.length) {
      throw new Error(
        `Expected ${allLevels.length} level IDs but received ${orderedIds.length}`,
      );
    }

    // Verify all IDs are valid
    const idSet = new Set(allLevels.map((l) => l.id));
    for (const id of orderedIds) {
      if (!idSet.has(id)) throw new Error(`Unknown level ID: ${id}`);
    }

    // Update each level's number sequentially
    const updates = orderedIds.map((id, index) =>
      prisma.level.update({
        where: { id },
        data: { number: index + 1 },
      }),
    );

    await prisma.$transaction(updates);

    return prisma.level.findMany({ orderBy: { number: 'asc' } });
  },
};
