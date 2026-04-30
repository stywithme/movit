/**
 * Calendar-based program structure checks (no DB / Prisma).
 * Used at publish time and in unit tests without loading programs.service.
 */

import { isProgramTrainingDaySlot } from '@/modules/active-plan/plan-position';

/** True if `message` came from {@link validateCalendarProgramStructure} (HTTP 400, not 500). */
export function isCalendarProgramStructureErrorMessage(message: string): boolean {
  return (
    message.startsWith('Calendar-based program:') ||
    message.startsWith('durationWeeks must')
  );
}

/** Count training (non-rest) days in week 1 for prescription matching. */
export function inferWeeklySessionTargetFromWeeks(
  weeks: { weekNumber: number; days: { isRestDay?: boolean; dayType?: string | null }[] }[],
): number | null {
  if (!weeks.length) return null;
  const sorted = [...weeks].sort((a, b) => a.weekNumber - b.weekNumber);
  const w1 = sorted.find((w) => w.weekNumber === 1) ?? sorted[0];
  if (!w1?.days?.length) return null;
  const n = w1.days.filter((d) => isProgramTrainingDaySlot(d)).length;
  return n > 0 ? n : null;
}

/**
 * Each published program week must exist; each week needs at least one training day
 * and day numbers 1..N sequential (no forced 7-day calendar).
 */
export function validateCalendarProgramStructure(
  durationWeeks: number,
  weeks: { weekNumber: number; days: { dayNumber: number; isRestDay?: boolean; dayType?: string | null }[] }[],
): void {
  if (durationWeeks < 1) {
    throw new Error('durationWeeks must be at least 1');
  }
  const outOfRangeWeek = weeks.find((w) => w.weekNumber < 1 || w.weekNumber > durationWeeks);
  if (outOfRangeWeek) {
    throw new Error(
      `Calendar-based program: week ${outOfRangeWeek.weekNumber} is outside durationWeeks ${durationWeeks}`,
    );
  }
  const byWeek = new Map(weeks.map((w) => [w.weekNumber, w]));
  if (byWeek.size !== weeks.length) {
    throw new Error('Calendar-based program: duplicate week numbers are not allowed');
  }
  for (let wn = 1; wn <= durationWeeks; wn++) {
    const week = byWeek.get(wn);
    if (!week) {
      throw new Error(`Calendar-based program: missing week ${wn} of ${durationWeeks}`);
    }
    const trainingDays = week.days.filter((d) => isProgramTrainingDaySlot(d));
    if (trainingDays.length < 1) {
      throw new Error(`Calendar-based program: week ${wn} must have at least one training day`);
    }
    const nums = trainingDays.map((d) => d.dayNumber).sort((a, b) => a - b);
    for (let i = 0; i < nums.length; i++) {
      if (nums[i] !== i + 1) {
        throw new Error(
          `Calendar-based program: week ${wn} training days must use dayNumber 1..N sequentially, got ${nums.join(',')}`,
        );
      }
    }
  }
}
