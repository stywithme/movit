/**
 * Training profile — CRUD for mobile onboarding (1:1 with User).
 */

import { getPrisma } from '@/lib/prisma/client';
import type { Prisma } from '@prisma/client';
import type { TrainingProfilePayload } from './training-profile.types';

function parseDate(value: string | null | undefined): Date | null | undefined {
  if (value === undefined) return undefined;
  if (value === null || value === '') return null;
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? null : d;
}

/** Scalar fields shared between create and update (unchecked). */
function buildProfileScalars(
  payload: TrainingProfilePayload
): Omit<Prisma.TrainingProfileUncheckedCreateInput, 'id' | 'userId' | 'createdAt' | 'updatedAt'> {
  const impliedDaysPerWeek =
    payload.trainingWeekdays && payload.trainingWeekdays.length > 0
      ? payload.trainingWeekdays.length
      : undefined;
  return {
    heightCm: payload.heightCm ?? undefined,
    weightKg: payload.weightKg ?? undefined,
    dateOfBirth: parseDate(payload.dateOfBirth),
    biologicalSex: payload.biologicalSex ?? undefined,
    currentActivityLevel: payload.currentActivityLevel ?? undefined,
    trainingExperienceMonths: payload.trainingExperienceMonths ?? undefined,
    resistanceExperience: payload.resistanceExperience ?? undefined,
    availableDaysPerWeek: impliedDaysPerWeek ?? payload.availableDaysPerWeek ?? undefined,
    trainingWeekdays: payload.trainingWeekdays ?? undefined,
    maxSessionMinutes: payload.maxSessionMinutes ?? undefined,
    availableEquipment: payload.availableEquipment as Prisma.InputJsonValue | undefined,
    trainingLocation: payload.trainingLocation ?? undefined,
    knownInjuries: payload.knownInjuries as Prisma.InputJsonValue | undefined,
    healthDisclaimerAccepted: payload.healthDisclaimerAccepted ?? undefined,
  };
}

export async function getTrainingProfileForUser(userId: string) {
  const prisma = await getPrisma();
  const [user, profile] = await Promise.all([
    prisma.user.findUnique({
      where: { id: userId },
      select: { id: true, trainingGoal: true },
    }),
    prisma.trainingProfile.findUnique({ where: { userId } }),
  ]);
  if (!user) return null;
  return {
    trainingGoal: user.trainingGoal,
    profile,
  };
}

export async function upsertTrainingProfile(userId: string, payload: TrainingProfilePayload) {
  const prisma = await getPrisma();
  const scalars = buildProfileScalars(payload);

  await prisma.$transaction(async (tx) => {
    if (payload.trainingGoal !== undefined) {
      await tx.user.update({
        where: { id: userId },
        data: { trainingGoal: payload.trainingGoal },
      });
    }

    await tx.trainingProfile.upsert({
      where: { userId },
      create: {
        userId,
        ...scalars,
      },
      update: scalars,
    });
  });

  return getTrainingProfileForUser(userId);
}
