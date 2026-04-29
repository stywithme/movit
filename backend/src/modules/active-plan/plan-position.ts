interface ProgressEntryLike {
  weekNumber: number;
  dayNumber: number;
  sessionId: string;
  status: string;
}

interface DayLike {
  dayNumber: number;
}

interface WeekLike<TDay extends DayLike = DayLike> {
  weekNumber: number;
  days: TDay[];
}

interface OrderedDayRef<TWeek, TDay> {
  week: TWeek;
  day: TDay;
  weekNumber: number;
  dayNumber: number;
}

export interface ResolvedProgramPosition<TWeek, TDay> {
  targetWeekNumber: number;
  targetDayNumber: number;
  targetWeek: TWeek | undefined;
  targetDay: TDay | undefined;
  completedDayCount: number;
  targetWeekCompletedDays: number;
  isProgramComplete: boolean;
  lastDay: { weekNumber: number; dayNumber: number } | null;
}

export interface ResolveCurrentProgramDayOptions {
  startDate?: Date | string | null;
  now?: Date;
}

function toDayKey(weekNumber: number, dayNumber: number): string {
  return `${weekNumber}:${dayNumber}`;
}

function toValidDate(value: Date | string | null | undefined): Date | null {
  if (!value) return null;
  const date = value instanceof Date ? value : new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

function normalizeDate(date: Date): Date {
  return new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate()));
}

function resolveDateBasedTargetIndex(
  totalDays: number,
  options: ResolveCurrentProgramDayOptions,
): { targetIndex: number; isProgramComplete: boolean } | null {
  const startDate = toValidDate(options.startDate);
  if (!startDate || totalDays <= 0) return null;

  const now = options.now ?? new Date();
  const start = normalizeDate(startDate);
  const today = normalizeDate(now);
  const diffMs = Math.max(0, today.getTime() - start.getTime());
  const dayIndex = Math.floor(diffMs / (24 * 60 * 60 * 1000));

  return {
    targetIndex: Math.min(dayIndex, totalDays - 1),
    isProgramComplete: dayIndex >= totalDays,
  };
}

export function resolveCurrentProgramDay<
  TDay extends DayLike,
  TWeek extends WeekLike<TDay>,
>(
  weeks: TWeek[],
  progressEntries: ProgressEntryLike[],
  options: ResolveCurrentProgramDayOptions = {},
): ResolvedProgramPosition<TWeek, TDay> {
  const orderedDays: Array<OrderedDayRef<TWeek, TDay>> = [...weeks]
    .sort((a, b) => a.weekNumber - b.weekNumber)
    .flatMap((week) =>
      [...week.days]
        .sort((a, b) => a.dayNumber - b.dayNumber)
        .map((day) => ({
          week,
          day,
          weekNumber: week.weekNumber,
          dayNumber: day.dayNumber,
        })),
    );

  if (orderedDays.length === 0) {
    return {
      targetWeekNumber: 1,
      targetDayNumber: 1,
      targetWeek: undefined,
      targetDay: undefined,
      completedDayCount: 0,
      targetWeekCompletedDays: 0,
      isProgramComplete: false,
      lastDay: null,
    };
  }

  const indexByDay = new Map(
    orderedDays.map((ref, index) => [toDayKey(ref.weekNumber, ref.dayNumber), index]),
  );
  const getDayIndex = (entry: { weekNumber: number; dayNumber: number }) =>
    indexByDay.get(toDayKey(entry.weekNumber, entry.dayNumber)) ?? -1;

  const completedDays = progressEntries
    .filter((entry) => entry.status === 'completed' && entry.sessionId === '__day__')
    .filter((entry) => getDayIndex(entry) >= 0)
    .sort((a, b) => getDayIndex(a) - getDayIndex(b));

  const completedSessions = progressEntries
    .filter((entry) => entry.status === 'completed' && entry.sessionId !== '__day__')
    .filter((entry) => getDayIndex(entry) >= 0)
    .sort((a, b) => getDayIndex(a) - getDayIndex(b));

  const latestCompletedDay = completedDays.at(-1) ?? null;
  const latestCompletedSession = completedSessions.at(-1) ?? null;

  const latestCompletedDayIndex = latestCompletedDay ? getDayIndex(latestCompletedDay) : -1;
  const latestCompletedSessionIndex = latestCompletedSession
    ? getDayIndex(latestCompletedSession)
    : -1;

  let targetIndex = 0;
  let isProgramComplete = false;

  const dateBasedTarget = resolveDateBasedTargetIndex(orderedDays.length, options);

  if (dateBasedTarget) {
    targetIndex = dateBasedTarget.targetIndex;
    isProgramComplete =
      dateBasedTarget.isProgramComplete || latestCompletedDayIndex >= orderedDays.length - 1;
  } else if (latestCompletedSessionIndex > latestCompletedDayIndex) {
    targetIndex = latestCompletedSessionIndex;
  } else if (latestCompletedDayIndex >= 0) {
    if (latestCompletedDayIndex >= orderedDays.length - 1) {
      targetIndex = latestCompletedDayIndex;
      isProgramComplete = true;
    } else {
      targetIndex = latestCompletedDayIndex + 1;
    }
  }

  const targetRef = orderedDays[targetIndex] ?? orderedDays[0];
  const lastRef = orderedDays.at(-1) ?? null;

  return {
    targetWeekNumber: targetRef.weekNumber,
    targetDayNumber: targetRef.dayNumber,
    targetWeek: targetRef.week,
    targetDay: targetRef.day,
    completedDayCount: completedDays.length,
    targetWeekCompletedDays: completedDays.filter(
      (entry) => entry.weekNumber === targetRef.weekNumber,
    ).length,
    isProgramComplete,
    lastDay: lastRef
      ? { weekNumber: lastRef.weekNumber, dayNumber: lastRef.dayNumber }
      : null,
  };
}
