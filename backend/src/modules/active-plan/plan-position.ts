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
  /** True when week/day exist in template but calendar slot could not be resolved */
  calendarSlotMissing?: boolean;
}

export interface ResolveCurrentProgramDayOptions {
  startDate?: Date | string | null;
  now?: Date;
  /**
   * With startDate, the program advances by calendar days: total length = durationWeeks * 7.
   * Should match Program.durationWeeks from the database.
   */
  durationWeeks?: number;
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

/** 0-based day offset from enrollment start (UTC midnight). */
export function getProgramCalendarDayIndex(startDate: Date, now: Date): number {
  const start = normalizeDate(startDate);
  const today = normalizeDate(now);
  const diffMs = Math.max(0, today.getTime() - start.getTime());
  return Math.floor(diffMs / (24 * 60 * 60 * 1000));
}

function findDayRef<TWeek, TDay>(
  orderedDays: Array<OrderedDayRef<TWeek, TDay>>,
  weekNumber: number,
  dayNumber: number,
): OrderedDayRef<TWeek, TDay> | undefined {
  return orderedDays.find((r) => r.weekNumber === weekNumber && r.dayNumber === dayNumber);
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

  const lastRef = orderedDays.at(-1) ?? null;
  const startDate = toValidDate(options.startDate);
  const now = options.now ?? new Date();

  // ── Calendar-based: anchor to enrollment date and durationWeeks * 7 ─────────
  if (startDate && options.durationWeeks != null && options.durationWeeks > 0) {
    const totalCalendarDays = options.durationWeeks * 7;
    const dayIndex = getProgramCalendarDayIndex(startDate, now);
    let isProgramComplete = dayIndex >= totalCalendarDays;
    let weekNumber: number;
    let dayNumber: number;

    if (isProgramComplete) {
      weekNumber = options.durationWeeks;
      dayNumber = 7;
    } else {
      weekNumber = Math.min(Math.floor(dayIndex / 7) + 1, options.durationWeeks);
      dayNumber = (dayIndex % 7) + 1;
    }

    const targetRef =
      findDayRef(orderedDays, weekNumber, dayNumber) ??
      (isProgramComplete && lastRef
        ? lastRef
        : undefined);

    const calendarSlotMissing = !findDayRef(orderedDays, weekNumber, dayNumber);

    const targetWeek = targetRef?.week;
    const targetDay = targetRef?.day;

    return {
      targetWeekNumber: weekNumber,
      targetDayNumber: dayNumber,
      targetWeek,
      targetDay,
      completedDayCount: completedDays.length,
      targetWeekCompletedDays: completedDays.filter((e) => e.weekNumber === weekNumber).length,
      isProgramComplete,
      lastDay: lastRef
        ? { weekNumber: lastRef.weekNumber, dayNumber: lastRef.dayNumber }
        : null,
      calendarSlotMissing: calendarSlotMissing || undefined,
    };
  }

  // ── Legacy: progress-only when no calendar anchor ───────────────────────────
  let targetIndex = 0;
  let isProgramComplete = false;

  const totalDaysLegacy = orderedDays.length;
  const dateBasedTarget =
    startDate && totalDaysLegacy > 0
      ? (() => {
          const dayIndex = getProgramCalendarDayIndex(startDate, now);
          return {
            targetIndex: Math.min(dayIndex, totalDaysLegacy - 1),
            isProgramComplete: dayIndex >= totalDaysLegacy,
          };
        })()
      : null;

  if (dateBasedTarget && !options.durationWeeks) {
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
