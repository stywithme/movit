/**
 * Training profile API types (mobile onboarding).
 */

import type { TrainingGoal } from '@prisma/client';

export interface TrainingProfilePayload {
  heightCm?: number | null;
  weightKg?: number | null;
  dateOfBirth?: string | null;
  biologicalSex?: string | null;
  currentActivityLevel?: string | null;
  trainingExperienceMonths?: number | null;
  resistanceExperience?: string | null;
  availableDaysPerWeek?: number | null;
  maxSessionMinutes?: number | null;
  availableEquipment?: unknown;
  trainingLocation?: string | null;
  knownInjuries?: unknown;
  painFlags?: unknown;
  parqPassed?: boolean | null;
  parqFlags?: unknown;
  parqCompletedAt?: string | null;
  /** Updates `User.trainingGoal` when set */
  trainingGoal?: TrainingGoal;
}
