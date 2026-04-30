import { getPrisma } from '@/lib/prisma/client';
import type { TrainingPositionMeta } from '@/modules/active-plan/plan-position';

export type CatchUpResetType = 'none' | 'week_restart' | 'program_restart';

export interface CatchUpSuggestion {
  resetType: CatchUpResetType;
  resetToWeek: number;
  resetToDay: number;
  calendarDaysSinceLastSession: number;
  messageAr: string;
  messageEn: string;
}

/**
 * Informational: auto catch-up already applied in position resolution when naturalIndex !== snappedIndex.
 */
export function buildCatchUpSuggestionFromMeta<TWeek, TDay>(
  meta: TrainingPositionMeta<TWeek, TDay>,
): CatchUpSuggestion | null {
  const { naturalIndex, snappedIndex, calendarDaysSinceLastSession, position, orderedTrainingDays } =
    meta;
  if (orderedTrainingDays.length === 0) return null;
  if (position.isProgramComplete) return null;
  if (calendarDaysSinceLastSession <= 2) return null;
  if (naturalIndex === snappedIndex) return null;

  const resetType: CatchUpResetType =
    calendarDaysSinceLastSession >= 30 ? 'program_restart' : 'week_restart';

  const w = position.targetWeekNumber;
  const d = position.targetDayNumber;

  if (resetType === 'program_restart') {
    return {
      resetType,
      resetToWeek: w,
      resetToDay: d,
      calendarDaysSinceLastSession,
      messageAr: `تم ضبط موضعك تلقائيًا لبداية البرنامج (أسبوع ${w}، يوم ${d}) بعد غياب ${calendarDaysSinceLastSession} يومًا تقويميًا.`,
      messageEn: `Your position was reset to the start of the program (week ${w}, day ${d}) after ${calendarDaysSinceLastSession} calendar days away.`,
    };
  }

  return {
    resetType,
    resetToWeek: w,
    resetToDay: d,
    calendarDaysSinceLastSession,
    messageAr: `تم ضبط موضعك تلقائيًا لبداية الأسبوع الحالي في البرنامج (أسبوع ${w}، يوم ${d}) بعد غياب ${calendarDaysSinceLastSession} يومًا تقويميًا.`,
    messageEn: `Your position was reset to the start of the current program week (week ${w}, day ${d}) after ${calendarDaysSinceLastSession} calendar days away.`,
  };
}

export async function getLastProgramSessionCompletedAt(
  userId: string,
  programId: string,
): Promise<Date | null> {
  const prisma = await getPrisma();
  const row = await prisma.programSessionReport.findFirst({
    where: { userId, programId, status: 'completed' },
    orderBy: { completedAt: 'desc' },
    select: { completedAt: true },
  });
  return row?.completedAt ?? null;
}
