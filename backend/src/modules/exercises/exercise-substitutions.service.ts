/**
 * Exercise Substitutions Service
 * Computes allowed substitutions for an exercise based on familyKey/familyOrder first,
 * then fallback to movementPattern + archetype.
 * Single source for substitution logic used by effective-plan and mobile API.
 */

import { getPrisma } from '@/lib/prisma/client';

export interface ExerciseSubstitution {
  id: string;
  slug: string;
  name: { en: string; ar: string };
  archetype: string | null;
}

export const exerciseSubstitutionsService = {
  async getSubstitutions(exerciseId: string): Promise<ExerciseSubstitution[]> {
    const prisma = await getPrisma();

    const exercise = await prisma.exercise.findUnique({
      where: { id: exerciseId },
      select: {
        familyKey: true,
        movementPattern: true,
        archetype: true,
      },
    });

    if (!exercise) {
      return [];
    }

    // 1. Primary: same familyKey, ordered by familyOrder
    if (exercise.familyKey) {
      const familySubs = await prisma.exercise.findMany({
        where: {
          familyKey: exercise.familyKey,
          id: { not: exerciseId },
          status: 'published',
          deletedAt: null,
        },
        orderBy: { familyOrder: 'asc' },
        select: {
          id: true,
          slug: true,
          name: true,
          archetype: true,
        },
      });

      if (familySubs.length > 0) {
        return familySubs as ExerciseSubstitution[];
      }
    }

    // 2. Fallback: same movementPattern + archetype (exclude family if already covered)
    if (exercise.movementPattern && exercise.archetype) {
      const similarSubs = await prisma.exercise.findMany({
        where: {
          movementPattern: exercise.movementPattern,
          archetype: exercise.archetype,
          id: { not: exerciseId },
          status: 'published',
          deletedAt: null,
          ...(exercise.familyKey ? { familyKey: { not: exercise.familyKey } } : {}),
        },
        orderBy: { familyOrder: 'asc' },
        take: 6,
        select: {
          id: true,
          slug: true,
          name: true,
          archetype: true,
        },
      });

      return similarSubs as ExerciseSubstitution[];
    }

    return [];
  },
};