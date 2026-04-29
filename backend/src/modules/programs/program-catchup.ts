import { getPrisma } from '@/lib/prisma/client';

export interface CatchUpSuggestion {
  missedTrainingDays: number;
  message: string;
  missedSlots: { weekNumber: number; dayNumber: number }[];
}

function programDayIndexToWeekDay(
  idx: number,
  durationWeeks: number,
): { weekNumber: number; dayNumber: number } {
  const maxIdx = Math.max(0, durationWeeks * 7 - 1);
  const capped = Math.max(0, Math.min(idx, maxIdx));
  return {
    weekNumber: Math.floor(capped / 7) + 1,
    dayNumber: (capped % 7) + 1,
  };
}

function findProgramDayMeta(
  weeks: { weekNumber: number; days: { dayNumber: number; isRestDay: boolean }[] }[],
  weekNumber: number,
  dayNumber: number,
) {
  const w = weeks.find((x) => x.weekNumber === weekNumber);
  return w?.days.find((d) => d.dayNumber === dayNumber);
}

/**
 * When calendar position is ahead of last completed session by more than 2 training days.
 */
export async function computeCatchUpSuggestion(
  userId: string,
  programId: string,
  weeks: { weekNumber: number; days: { dayNumber: number; isRestDay: boolean }[] }[],
  durationWeeks: number,
  targetWeek: number,
  targetDay: number,
): Promise<CatchUpSuggestion | null> {
  const prisma = await getPrisma();
  const expectedIndex = (targetWeek - 1) * 7 + (targetDay - 1);
  const lastReport = await prisma.programSessionReport.findFirst({
    where: { userId, programId, status: 'completed' },
    orderBy: { completedAt: 'desc' },
    select: { weekNumber: true, dayNumber: true },
  });
  const lastIndex = lastReport
    ? (lastReport.weekNumber - 1) * 7 + (lastReport.dayNumber - 1)
    : -1;
  const missedTrainingDays = Math.max(0, expectedIndex - lastIndex - 1);
  if (missedTrainingDays <= 2) return null;

  const missedSlots: { weekNumber: number; dayNumber: number }[] = [];
  for (let i = lastIndex + 1; i < expectedIndex; i++) {
    const wd = programDayIndexToWeekDay(i, durationWeeks);
    const pd = findProgramDayMeta(weeks, wd.weekNumber, wd.dayNumber);
    if (pd && !pd.isRestDay) {
      missedSlots.push({ weekNumber: wd.weekNumber, dayNumber: wd.dayNumber });
    }
    if (missedSlots.length >= 5) break;
  }

  return {
    missedTrainingDays,
    message: `You have ${missedTrainingDays} day(s) without a completed session vs your program calendar. Consider catching up or using pause if you need a break.`,
    missedSlots,
  };
}
