import { getPrisma } from '@/lib/prisma/client';
import type { TrainingPositionMeta } from '@/modules/active-plan/plan-position';

export type CatchUpResetType = 'none' | 'week_restart' | 'program_restart';

export interface CatchUpSuggestion {
  resetType: CatchUpResetType;
  resetToWeek: number;
  resetToDay: number;
  calendarDaysSinceLastWorkout: number;
  messageAr: string;
  messageEn: string;
}

/**
 * Informational: auto catch-up already applied in position resolution when naturalIndex !== snappedIndex.
 */
export function buildCatchUpSuggestionFromMeta<TWeek, TDay>(
  meta: TrainingPositionMeta<TWeek, TDay>,
): CatchUpSuggestion | null {
  const { naturalIndex, snappedIndex, calendarDaysSinceLastWorkout, position, orderedTrainingDays } =
    meta;
  if (orderedTrainingDays.length === 0) return null;
  if (position.isProgramComplete) return null;
  if (calendarDaysSinceLastWorkout <= 2) return null;
  if (naturalIndex === snappedIndex) return null;

  const resetType: CatchUpResetType =
    calendarDaysSinceLastWorkout >= 30 ? 'program_restart' : 'week_restart';

  const w = position.targetWeekNumber;
  const d = position.targetDayNumber;

  if (resetType === 'program_restart') {
    return {
      resetType,
      resetToWeek: w,
      resetToDay: d,
      calendarDaysSinceLastWorkout,
      messageAr: `?? ??? ????? ???????? ?????? ???????? (????? ${w}? ??? ${d}) ??? ???? ${calendarDaysSinceLastWorkout} ????? ????????.`,
      messageEn: `Your position was reset to the start of the program (week ${w}, day ${d}) after ${calendarDaysSinceLastWorkout} calendar days away.`,
    };
  }

  return {
    resetType,
    resetToWeek: w,
    resetToDay: d,
    calendarDaysSinceLastWorkout,
    messageAr: `?? ??? ????? ???????? ?????? ??????? ?????? ?? ???????? (????? ${w}? ??? ${d}) ??? ???? ${calendarDaysSinceLastWorkout} ????? ????????.`,
    messageEn: `Your position was reset to the start of the current program week (week ${w}, day ${d}) after ${calendarDaysSinceLastWorkout} calendar days away.`,
  };
}

export async function getLastPlannedWorkoutCompletedAt(
  userId: string,
  programId: string,
): Promise<Date | null> {
  const prisma = await getPrisma();
  const row = await prisma.plannedWorkoutReport.findFirst({
    where: { userId, programId, status: 'completed' },
    orderBy: { completedAt: 'desc' },
    select: { completedAt: true },
  });
  return row?.completedAt ?? null;
}
