/**
 * Calendar-based program structure checks (no DB / Prisma).
 * Used at publish time and in unit tests without loading programs.service.
 */

/** Each published program week must have days 1–7 (calendar-based). */
export function validateCalendarProgramStructure(
  durationWeeks: number,
  weeks: { weekNumber: number; days: { dayNumber: number }[] }[],
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
    if (week.days.length !== 7) {
      throw new Error(
        `Calendar-based program: week ${wn} must have exactly 7 days, got ${week.days.length}`,
      );
    }
    const nums = new Set(week.days.map((d) => d.dayNumber));
    for (let d = 1; d <= 7; d++) {
      if (!nums.has(d)) {
        throw new Error(`Calendar-based program: week ${wn} must include day ${d}`);
      }
    }
  }
}
