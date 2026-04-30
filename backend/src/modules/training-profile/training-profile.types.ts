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
  /** 0=Sun … 6=Sat — preferred training weekdays (length should match program weeklySessionTarget). */
  trainingWeekdays?: number[] | null;
  maxSessionMinutes?: number | null;
  availableEquipment?: unknown;
  trainingLocation?: string | null;
  knownInjuries?: unknown;
  /** User accepted in-app health disclaimer (replaces PAR-Q on profile). */
  healthDisclaimerAccepted?: boolean | null;
  /** Updates `User.trainingGoal` when set */
  trainingGoal?: TrainingGoal;
}
