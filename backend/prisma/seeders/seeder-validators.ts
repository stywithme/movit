import type { PrismaClient } from '@prisma/client';
import {
  ASSESSMENT_EXERCISE_SLUGS,
  MIN_CURATED_EXERCISE_COUNT,
  PROGRAM_DOMAINS,
  REQUIRED_USER_FIXTURE_PROGRAM_SLUGS,
  TRAINING_GOALS,
} from './coverage-matrix';
import { FAMILY_LADDERS } from './exercise-families';
import { PROGRAM_CATALOG } from './program-catalog';

export type CoverageReport = {
  exerciseTotal: number;
  byMovementPattern: Record<string, number>;
  byFamilyKey: Record<string, number>;
  programTotal: number;
  programsByDomainGoal: string;
  fillerSlugs: string[];
};

function assert(cond: unknown, msg: string): asserts cond {
  if (!cond) throw new Error(`Seed validator: ${msg}`);
}

export async function runSeedValidatorsAndReport(prisma: PrismaClient): Promise<CoverageReport> {
  const exercises = await prisma.exercise.findMany({
    select: {
      slug: true,
      movementPattern: true,
      loadCapability: true,
      familyKey: true,
      familyOrder: true,
      countingMethod: { select: { code: true } },
    },
  });

  assert(exercises.length >= MIN_CURATED_EXERCISE_COUNT, `expected >= ${MIN_CURATED_EXERCISE_COUNT} exercises, got ${exercises.length}`);

  const fillerSlugs = exercises.filter((e) => e.slug.startsWith('seed-catalog-')).map((e) => e.slug);
  assert(fillerSlugs.length === 0, `filler catalog slugs must be removed (found: ${fillerSlugs.join(', ')})`);

  for (const ex of exercises) {
    assert(ex.movementPattern, `exercise ${ex.slug} missing movementPattern`);
    assert(ex.loadCapability, `exercise ${ex.slug} missing loadCapability`);
    assert(ex.familyKey, `exercise ${ex.slug} missing familyKey`);
    assert(ex.familyOrder != null, `exercise ${ex.slug} missing familyOrder`);
  }

  for (const slug of ASSESSMENT_EXERCISE_SLUGS) {
    assert(exercises.some((e) => e.slug === slug), `missing assessment exercise slug: ${slug}`);
  }

  const byFamily = new Map<string, { slug: string; order: number }[]>();
  for (const ex of exercises) {
    if (!ex.familyKey) continue;
    const list = byFamily.get(ex.familyKey) ?? [];
    list.push({ slug: ex.slug, order: ex.familyOrder! });
    byFamily.set(ex.familyKey, list);
  }
  for (const [fk, rows] of byFamily) {
    const sorted = [...rows].sort((a, b) => a.order - b.order);
    for (let i = 1; i < sorted.length; i++) {
      assert(
        sorted[i].order >= sorted[i - 1].order,
        `family ${fk}: decreasing familyOrder between ${sorted[i - 1].slug} (${sorted[i - 1].order}) and ${sorted[i].slug} (${sorted[i].order})`,
      );
      assert(
        !(sorted[i].order === sorted[i - 1].order && sorted[i].slug !== sorted[i - 1].slug),
        `family ${fk}: duplicate familyOrder ${sorted[i].order} for ${sorted[i - 1].slug} and ${sorted[i].slug}`,
      );
    }
  }

  for (const [family, ladder] of Object.entries(FAMILY_LADDERS)) {
    for (const slug of ladder) {
      assert(exercises.some((e) => e.slug === slug), `documented ladder ${family} references missing slug: ${slug}`);
    }
  }

  for (const goal of TRAINING_GOALS) {
    for (let level = 1; level <= 5; level++) {
      const hit = PROGRAM_CATALOG.some(
        (p) =>
          p.programDomain === 'TRAINING' &&
          p.programType === 'SYSTEM' &&
          p.trainingGoal === goal &&
          p.autoAssignable &&
          p.levelRangeMin <= level &&
          p.levelRangeMax >= level,
      );
      assert(hit, `no SYSTEM TRAINING auto-assignable program for goal=${goal} covering level=${level}`);
    }
  }

  const mobilityCount = PROGRAM_CATALOG.filter((p) => p.programDomain === 'MOBILITY' && p.programType === 'SYSTEM').length;
  assert(mobilityCount >= 1, 'expected at least 1 SYSTEM MOBILITY program');

  const therapeuticCount = PROGRAM_CATALOG.filter((p) => p.programDomain === 'THERAPEUTIC' && p.programType === 'SYSTEM').length;
  assert(therapeuticCount >= 2, 'expected at least 2 SYSTEM THERAPEUTIC programs');

  for (const slug of REQUIRED_USER_FIXTURE_PROGRAM_SLUGS) {
    const p = await prisma.program.findUnique({ where: { slug } });
    assert(p, `missing required fixture program: ${slug}`);
  }

  const programs = await prisma.program.findMany({
    where: { programType: 'SYSTEM', autoAssignable: true },
    select: {
      slug: true,
      programDomain: true,
      trainingGoal: true,
      weeklySessionTarget: true,
      estimatedSessionMinutes: true,
      targetEquipment: true,
      version: true,
      entryRecommendations: true,
    },
  });

  for (const p of programs) {
    assert(p.version != null, `program ${p.slug} missing version`);
    assert(p.targetEquipment != null, `program ${p.slug} missing targetEquipment`);
    assert(p.weeklySessionTarget != null && p.weeklySessionTarget >= 2, `program ${p.slug} weeklySessionTarget < 2`);
    assert(p.estimatedSessionMinutes != null && p.estimatedSessionMinutes > 0, `program ${p.slug} invalid estimatedSessionMinutes`);
    assert(p.entryRecommendations != null, `program ${p.slug} missing entryRecommendations`);
  }

  const byMovementPattern: Record<string, number> = {};
  for (const ex of exercises) {
    const k = ex.movementPattern ?? 'UNKNOWN';
    byMovementPattern[k] = (byMovementPattern[k] ?? 0) + 1;
  }

  const byFamilyKey: Record<string, number> = {};
  for (const ex of exercises) {
    const k = ex.familyKey ?? 'UNKNOWN';
    byFamilyKey[k] = (byFamilyKey[k] ?? 0) + 1;
  }

  const allPrograms = await prisma.program.count();
  const grouped = await prisma.program.groupBy({
    by: ['programDomain', 'trainingGoal'],
    _count: { _all: true },
  });
  const lines = grouped.map((g) => `${g.programDomain}/${g.trainingGoal ?? 'null'}:${g._count._all}`);

  const report: CoverageReport = {
    exerciseTotal: exercises.length,
    byMovementPattern,
    byFamilyKey,
    programTotal: allPrograms,
    programsByDomainGoal: lines.join(' | '),
    fillerSlugs,
  };

  console.log('\n📊 Seed coverage report');
  console.log(`  Exercises: ${report.exerciseTotal}`);
  console.log(`  Programs: ${report.programTotal}`);
  console.log('  Movement patterns:', JSON.stringify(report.byMovementPattern));
  console.log('  Program domain/goal counts:', report.programsByDomainGoal);
  console.log('  Family keys:', Object.keys(report.byFamilyKey).length, 'distinct');
  console.log('✅ Seed validators passed\n');

  return report;
}
