import type { PrismaClient } from '@prisma/client';
import type { EnsureMessageTemplate } from './messages';
import { seedExerciseJsonFromDirectories } from './exercise-json-batch';
import { seedWorkoutsFromJsonDir } from './workouts-from-json';

const CANONICAL_EXERCISE_DIR = 'prisma/Exercise-json/exercises-from-db';
const MISSING_EXERCISE_DIR = 'prisma/Exercise-json/missing-exercises/exercises-from-db';

/**
 * @deprecated Use seed-orchestrator runSeedContent() or seedExerciseJsonFromDirectories() directly.
 */
export async function seedExercisesAndWorkouts(
  prisma: PrismaClient,
  ensureMessageTemplate: EnsureMessageTemplate,
) {
  await seedExerciseJsonFromDirectories(prisma, ensureMessageTemplate, [
    CANONICAL_EXERCISE_DIR,
    MISSING_EXERCISE_DIR,
  ]);
  await seedWorkoutsFromJsonDir(prisma);
}
