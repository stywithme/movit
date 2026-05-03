import type { Prisma, PrismaClient } from '@prisma/client';
import { ProgramAttributeMode } from '@prisma/client';
import {
  PROGRAM_DOMAIN_VALUE_CODE,
  TRAINING_GOAL_VALUE_CODE,
  bodyRegionValueCodeFromLabel,
  equipmentValueCodeFromProfileString,
  focusValueCodeFromTargetHint,
} from '../../src/lib/program-attribute-codes';
import { PROGRAM_CATALOG, type ProgramCatalogEntry } from './program-catalog';

type ExerciseRow = {
  id: string;
  slug: string;
  countingMethod: { code: string } | null;
};

export async function seedPrograms(prisma: PrismaClient) {
  const exercises = await prisma.exercise.findMany({
    select: {
      id: true,
      slug: true,
      countingMethod: { select: { code: true } },
    },
  });
  const bySlug = new Map(exercises.map((e) => [e.slug, e]));

  const requireExercise = (slug: string): ExerciseRow => {
    const row = bySlug.get(slug);
    if (!row) {
      throw new Error(`Program seed: missing exercise slug "${slug}"`);
    }
    return row;
  };

  const buildExerciseTarget = (exercise: ExerciseRow, preferred: { reps?: number; duration?: number }) => {
    const isHold = exercise.countingMethod?.code === 'hold';
    if (isHold) {
      return {
        targetReps: undefined as number | undefined,
        targetDuration: preferred.duration ?? 30,
      };
    }
    return {
      targetReps: preferred.reps ?? 10,
      targetDuration: undefined as number | undefined,
    };
  };

  const exItem = (
    exercise: ExerciseRow,
    opts: {
      sets?: number;
      reps?: number;
      duration?: number;
      restMs?: number;
      weight?: number;
      weightPerSet?: number[];
      sortOrder: number;
      role?: 'WARMUP' | 'ACTIVATION' | 'MAIN' | 'ACCESSORY' | 'CORRECTIVE' | 'COOLDOWN' | 'TEST';
      intent?: 'STANDARD' | 'POWER' | 'ECCENTRIC' | 'VELOCITY_BASED';
    },
  ) => ({
    type: 'exercise' as const,
    exerciseId: exercise.id,
    sets: opts.sets ?? 3,
    ...buildExerciseTarget(exercise, { reps: opts.reps, duration: opts.duration }),
    restBetweenSetsMs: opts.restMs ?? 30000,
    weightKg: opts.weight,
    weightPerSet: opts.weightPerSet,
    sortOrder: opts.sortOrder,
    role: opts.role ?? 'MAIN',
    intent: opts.intent ?? 'STANDARD',
    isPersonalized: false,
  });

  const restItem = (durationMs: number, sortOrder: number) => ({
    type: 'rest' as const,
    restDurationMs: durationMs,
    sortOrder,
  });

  const buildSession = (
    name: { ar: string; en: string },
    items: Record<string, unknown>[],
    sortOrder = 1,
    estimatedDurationMin = 35,
    sessionCategory: 'strength' | 'mobility' | 'conditioning' | 'recovery' | 'mixed' = 'mixed',
  ) => ({
    name,
    sortOrder,
    estimatedDurationMin,
    sessionCategory,
    items: { create: items as Prisma.ProgramSessionItemCreateWithoutSessionInput[] },
  });

  type DayDef = {
    dayNumber: number;
    isRestDay?: boolean;
    dayType?: string;
    dayFocus?: string | null;
    sessions?: ReturnType<typeof buildSession>[];
  };

  const mapDayCreate = (day: DayDef): Prisma.ProgramDayCreateWithoutWeekInput => ({
    dayNumber: day.dayNumber,
    isRestDay: day.isRestDay ?? false,
    dayType: day.isRestDay ? 'rest' : (day.dayType ?? 'training'),
    dayFocus: day.dayFocus ?? (day.isRestDay ? null : 'general'),
    name: day.isRestDay ? { ar: 'راحة', en: 'Rest' } : undefined,
    sessions: day.sessions ? { create: day.sessions } : undefined,
  });

  const createWeek = async (
    programId: string,
    weekNumber: number,
    days: DayDef[],
    weekType: 'NORMAL' | 'DELOAD' = 'NORMAL',
  ) => {
    await prisma.programWeek.create({
      data: {
        programId,
        weekNumber,
        sortOrder: weekNumber,
        weekType,
        name: { ar: `الأسبوع ${weekNumber}`, en: `Week ${weekNumber}` },
        days: {
          create: days.map(mapDayCreate),
        },
      },
    });
  };

  const programIdBySlug = new Map<string, string>();

  async function replaceProgramAttributesFromCatalogEntry(programId: string, def: ProgramCatalogEntry) {
    const rows: { attributeValueId: string; mode: ProgramAttributeMode }[] = [];

    const domainCode = PROGRAM_DOMAIN_VALUE_CODE[def.programDomain];
    const domainVal = await prisma.attributeValue.findUnique({ where: { code: domainCode } });
    if (domainVal) rows.push({ attributeValueId: domainVal.id, mode: ProgramAttributeMode.REQUIRED });

    if (def.trainingGoal) {
      const goalCode = TRAINING_GOAL_VALUE_CODE[def.trainingGoal];
      const goalVal = goalCode ? await prisma.attributeValue.findUnique({ where: { code: goalCode } }) : null;
      if (goalVal) rows.push({ attributeValueId: goalVal.id, mode: ProgramAttributeMode.REQUIRED });
    }

    const te = def.targetEquipment;
    if (Array.isArray(te)) {
      for (const raw of te) {
        const code = typeof raw === 'string' ? equipmentValueCodeFromProfileString(raw) : null;
        if (!code) continue;
        const ev = await prisma.attributeValue.findUnique({
          where: { code },
          include: { attribute: true },
        });
        if (ev?.attribute?.code === 'equipment') {
          rows.push({ attributeValueId: ev.id, mode: ProgramAttributeMode.REQUIRED });
        }
      }
    }

    for (const region of def.targetRegions || []) {
      const br = bodyRegionValueCodeFromLabel(region);
      if (!br) continue;
      const v = await prisma.attributeValue.findUnique({ where: { code: br } });
      if (v) rows.push({ attributeValueId: v.id, mode: ProgramAttributeMode.REQUIRED });
    }

    for (const region of def.contraindications || []) {
      const br = bodyRegionValueCodeFromLabel(region);
      if (!br) continue;
      const v = await prisma.attributeValue.findUnique({ where: { code: br } });
      if (v) rows.push({ attributeValueId: v.id, mode: ProgramAttributeMode.EXCLUDED });
    }

    if (def.targetDomain) {
      const fc = focusValueCodeFromTargetHint(def.targetDomain);
      if (fc) {
        const v = await prisma.attributeValue.findUnique({ where: { code: fc } });
        if (v) rows.push({ attributeValueId: v.id, mode: ProgramAttributeMode.REQUIRED });
      }
    }

    await prisma.programAttribute.deleteMany({ where: { programId } });
    const seen = new Set<string>();
    const uniqueRows = rows.filter((r) => {
      if (seen.has(r.attributeValueId)) return false;
      seen.add(r.attributeValueId);
      return true;
    });
    if (uniqueRows.length > 0) {
      await prisma.programAttribute.createMany({
        data: uniqueRows.map((r) => ({
          programId,
          attributeValueId: r.attributeValueId,
          mode: r.mode,
        })),
      });
    }
  }

  for (const def of PROGRAM_CATALOG) {
    const prerequisiteId = def.prerequisiteProgramSlug
      ? programIdBySlug.get(def.prerequisiteProgramSlug) ?? null
      : null;
    const nextId = def.nextProgramSlug ? programIdBySlug.get(def.nextProgramSlug) ?? null : null;

    const program = await prisma.program.upsert({
      where: { slug: def.slug },
      update: {
        name: def.name,
        description: def.description,
        durationWeeks: def.durationWeeks,
        isDefault: def.isDefault ?? false,
        isPublished: def.isPublished,
        tags: def.tags,
        programType: def.programType,
        levelRangeMin: def.levelRangeMin,
        levelRangeMax: def.levelRangeMax,
        prescriptionPriority: def.prescriptionPriority,
        autoAssignable: def.autoAssignable,
        version: def.version,
        weeklySessionTarget: def.weeklySessionTarget ?? undefined,
        estimatedSessionMinutes: def.estimatedSessionMinutes ?? undefined,
        coachingNotes: def.coachingNotes ?? undefined,
        prerequisiteProgramId: prerequisiteId,
        nextProgramId: nextId,
      },
      create: {
        slug: def.slug,
        name: def.name,
        description: def.description,
        durationWeeks: def.durationWeeks,
        isDefault: def.isDefault ?? false,
        isPublished: def.isPublished,
        tags: def.tags,
        programType: def.programType,
        levelRangeMin: def.levelRangeMin,
        levelRangeMax: def.levelRangeMax,
        prescriptionPriority: def.prescriptionPriority,
        autoAssignable: def.autoAssignable,
        version: def.version,
        weeklySessionTarget: def.weeklySessionTarget ?? undefined,
        estimatedSessionMinutes: def.estimatedSessionMinutes ?? undefined,
        coachingNotes: def.coachingNotes ?? undefined,
        prerequisiteProgramId: prerequisiteId,
        nextProgramId: nextId,
      },
    });

    programIdBySlug.set(def.slug, program.id);

    await replaceProgramAttributesFromCatalogEntry(program.id, def);

    await prisma.programWeek.deleteMany({ where: { programId: program.id } });

    for (const w of def.weeks) {
      const days: DayDef[] = w.days.map((d) => {
        if (d.isRestDay || !d.sessions?.length) {
          return {
            dayNumber: d.dayNumber,
            isRestDay: d.isRestDay,
            dayType: d.dayType,
            dayFocus: d.dayFocus,
          };
        }
        const sessions = d.sessions.map((sess) => {
          const prismaItems: Record<string, unknown>[] = [];
          let sortOrder = 1;
          for (const raw of sess.items) {
            if ('restMs' in raw && !('slug' in raw)) {
              prismaItems.push(restItem(raw.restMs, sortOrder));
              sortOrder++;
              continue;
            }
            const slot = raw as import('./program-catalog').CatalogExerciseSlot;
            const ex = requireExercise(slot.slug);
            prismaItems.push(
              exItem(ex, {
                sets: slot.sets,
                reps: slot.reps,
                duration: slot.duration,
                restMs: slot.restMs,
                weight: slot.weight,
                weightPerSet: slot.weightPerSet,
                sortOrder,
                role: slot.role,
                intent: slot.intent,
              }),
            );
            sortOrder++;
          }
          return buildSession(
            sess.name,
            prismaItems,
            sess.sortOrder ?? 1,
            sess.estimatedDurationMin ?? 35,
            sess.sessionCategory ?? 'mixed',
          );
        });
        return {
          dayNumber: d.dayNumber,
          isRestDay: d.isRestDay,
          dayType: d.dayType,
          dayFocus: d.dayFocus,
          sessions,
        };
      });

      await createWeek(program.id, w.weekNumber, days, w.weekType ?? 'NORMAL');
    }

    console.log(`  ✅ Program seeded: ${def.slug}`);
  }

  const starter = programIdBySlug.get('starter-4-weeks');
  const strength = programIdBySlug.get('intermediate-strength-4w');
  if (starter && strength) {
    await prisma.program.update({ where: { id: starter }, data: { nextProgramId: strength } });
  }

  console.log(`✅ Programs seeded from catalog (${PROGRAM_CATALOG.length} programs)`);
}
