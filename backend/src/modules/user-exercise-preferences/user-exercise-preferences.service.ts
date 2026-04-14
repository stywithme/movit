/**
 * User exercise preferences — per-user overrides for reps, hold duration, and weight (standalone exercises).
 */

import { prisma } from '@/lib/prisma/client';

export interface UserExercisePreferencePatch {
  customReps?: number | null;
  customDurationSec?: number | null;
  customWeightKg?: number | null;
}

export interface UserExercisePreferenceExport {
  exerciseId: string;
  exerciseSlug: string;
  customReps?: number;
  customDurationSec?: number;
  customWeightKg?: number;
  updatedAt: string;
}

function toExport(
  row: {
    exerciseId: string;
    customReps: number | null;
    customDurationSec: number | null;
    customWeightKg: number | null;
    updatedAt: Date;
  },
  slug: string
): UserExercisePreferenceExport {
  return {
    exerciseId: row.exerciseId,
    exerciseSlug: slug,
    ...(row.customReps != null ? { customReps: row.customReps } : {}),
    ...(row.customDurationSec != null ? { customDurationSec: row.customDurationSec } : {}),
    ...(row.customWeightKg != null ? { customWeightKg: row.customWeightKg } : {}),
    updatedAt: row.updatedAt.toISOString(),
  };
}

export async function listUserExercisePreferences(
  userId: string
): Promise<UserExercisePreferenceExport[]> {
  const rows = await prisma.userExercisePreference.findMany({
    where: { userId },
    include: { exercise: { select: { slug: true } } },
    orderBy: { updatedAt: 'desc' },
  });
  return rows.map((r) => toExport(r, r.exercise.slug));
}

export async function upsertUserExercisePreference(
  userId: string,
  exerciseId: string,
  patch: UserExercisePreferencePatch
): Promise<{ ok: true; data: UserExercisePreferenceExport | null } | { ok: false; error: 'NOT_FOUND' }> {
  const exercise = await prisma.exercise.findFirst({
    where: { id: exerciseId, deletedAt: null },
    select: { id: true, slug: true },
  });
  if (!exercise) {
    return { ok: false, error: 'NOT_FOUND' };
  }

  const existing = await prisma.userExercisePreference.findUnique({
    where: { userId_exerciseId: { userId, exerciseId } },
  });

  const customReps =
    patch.customReps !== undefined ? patch.customReps : existing?.customReps ?? null;
  const customDurationSec =
    patch.customDurationSec !== undefined ? patch.customDurationSec : existing?.customDurationSec ?? null;
  const customWeightKg =
    patch.customWeightKg !== undefined ? patch.customWeightKg : existing?.customWeightKg ?? null;

  const allEmpty =
    customReps == null && customDurationSec == null && customWeightKg == null;

  if (allEmpty) {
    if (existing) {
      await prisma.userExercisePreference.delete({
        where: { id: existing.id },
      });
    }
    return { ok: true, data: null };
  }

  const row = await prisma.userExercisePreference.upsert({
    where: { userId_exerciseId: { userId, exerciseId } },
    create: {
      userId,
      exerciseId,
      customReps,
      customDurationSec,
      customWeightKg,
    },
    update: {
      customReps,
      customDurationSec,
      customWeightKg,
    },
  });

  return { ok: true, data: toExport(row, exercise.slug) };
}

export async function deleteUserExercisePreference(
  userId: string,
  exerciseId: string
): Promise<{ deleted: boolean }> {
  const result = await prisma.userExercisePreference.deleteMany({
    where: { userId, exerciseId },
  });
  return { deleted: result.count > 0 };
}
