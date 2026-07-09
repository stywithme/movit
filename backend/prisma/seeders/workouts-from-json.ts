import type { PrismaClient } from '@prisma/client';
import * as fs from 'fs/promises';
import * as path from 'path';
import { existsDir } from './exercise-json-batch';
import { getWorkoutPhaseIdBySlug } from './workout-phases';

type WorkoutJson = {
  name: { ar: string; en: string };
  description?: { ar: string; en: string };
  restBetweenExercisesMs?: number;
  exercises: Array<{
    exercise: string;
    variantIndex?: number;
    target?: { reps?: number; durationSec?: number };
    notes?: { ar: string; en: string };
  }>;
};

export async function resolveWorkoutsDir(input?: string): Promise<string | null> {
  const candidates = [
    input,
    process.env.SEED_WORKOUTS_DIR,
    path.resolve(__dirname, '../Exercise-json/workouts'),
    path.resolve(__dirname, '../Exercise-json/workout_templates'),
  ].filter((dir): dir is string => Boolean(dir));

  for (const candidate of candidates) {
    const absolute = path.resolve(process.cwd(), candidate);
    if (await existsDir(absolute)) return absolute;
  }

  return null;
}

export async function seedWorkoutsFromJsonDir(
  prisma: PrismaClient,
  workoutsDirInput?: string,
): Promise<number> {
  const workoutsDir = await resolveWorkoutsDir(workoutsDirInput);
  if (!workoutsDir) {
    console.warn('⚠️ Workouts directory not found — skipping workout template seed.');
    return 0;
  }

  const mainPhaseId = await getWorkoutPhaseIdBySlug(prisma, 'main');
  const workoutFiles = (await fs.readdir(workoutsDir)).filter((file) => file.endsWith('.json'));
  console.log(`🏋️ Seeding ${workoutFiles.length} workout template(s) from ${workoutsDir}`);

  for (const file of workoutFiles) {
    const filePath = path.join(workoutsDir, file);
    const raw = await fs.readFile(filePath, 'utf8');
    const workoutJson = JSON.parse(raw) as WorkoutJson;
    const slug = path.basename(file, '.json');

    const workoutRecord = await prisma.workoutTemplate.upsert({
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

    await prisma.workoutTemplatePhase.deleteMany({
      where: { workoutTemplateId: workoutRecord.id },
    });
    await prisma.workoutTemplateExercise.deleteMany({
      where: { workoutTemplateId: workoutRecord.id },
    });

    const workoutTemplatePhase = await prisma.workoutTemplatePhase.create({
      data: {
        workoutTemplateId: workoutRecord.id,
        phaseId: mainPhaseId,
        sortOrder: 0,
      },
    });

    for (let index = 0; index < workoutJson.exercises.length; index++) {
      const exerciseEntry = workoutJson.exercises[index]!;
      const exerciseRecord = await prisma.exercise.findUnique({
        where: { slug: exerciseEntry.exercise },
        select: { id: true },
      });

      if (!exerciseRecord) {
        throw new Error(`Workout "${slug}" references missing exercise slug: ${exerciseEntry.exercise}`);
      }

      const isLast = index >= workoutJson.exercises.length - 1;
      const restAfterExerciseMs = isLast ? 0 : workoutJson.restBetweenExercisesMs ?? 60_000;

      await prisma.workoutTemplateExercise.create({
        data: {
          workoutTemplateId: workoutRecord.id,
          workoutTemplatePhaseId: workoutTemplatePhase.id,
          exerciseId: exerciseRecord.id,
          variantIndex: exerciseEntry.variantIndex ?? 0,
          targetReps: exerciseEntry.target?.reps ?? undefined,
          targetRepsPerSet: exerciseEntry.target?.reps != null ? [exerciseEntry.target.reps] : undefined,
          targetDuration: exerciseEntry.target?.durationSec ?? undefined,
          sets: 1,
          restBetweenSetsMs: 30_000,
          restBetweenSetsPerSetMs: [30_000],
          restAfterExerciseMs,
          notes: exerciseEntry.notes || undefined,
          sortOrder: index,
        },
      });
    }

    console.log(`  - ${slug}`);
  }

  console.log(`✅ Workout templates seeded (${workoutFiles.length}).`);
  return workoutFiles.length;
}
