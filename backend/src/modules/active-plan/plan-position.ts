interface ProgressEntryLike {
  weekNumber: number;
  dayNumber: number;
  plannedWorkoutId: string;
  status: string;
}

export interface DayLike {
  dayNumber: number;
  isRestDay?: boolean;
  dayType?: string | null;
  plannedWorkouts?: Array<{ id: string; sortOrder?: number }>;
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
  /** True when week/day exist in template but slot could not be resolved */
  calendarSlotMissing?: boolean;
  /** When user's trainingWeekdays is set, false on off days (still shows next program slot). */
  isTrainingDay?: boolean;
}

export interface ResolveCurrentProgramDayOptions {
  now?: Date;
  /**
   * Last completed planned workout time (for auto catch-up gaps).
   * When null/undefined, catch-up snap is not applied (treated as 0 days since last workout).
   */
  lastWorkoutCompletedAt?: Date | string | null;
  /**
   * 0=Sun ? 6=Sat. Empty or undefined = train any day (backward compatible).
   */
  trainingWeekdays?: number[] | null;
  /** Program length in weeks (for completion boundary). */
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

/** 0-based day offset from anchor to now (UTC calendar days). */
export function getProgramCalendarDayIndex(startDate: Date, now: Date): number {
  const start = normalizeDate(startDate);
  const today = normalizeDate(now);
  const diffMs = Math.max(0, today.getTime() - start.getTime());
  return Math.floor(diffMs / (24 * 60 * 60 * 1000));
}

/** True if this template day is a training day (has work to show). */
export function isProgramTrainingDaySlot(day: {
  isRestDay?: boolean;
  dayType?: string | null;
}): boolean {
  if (day.isRestDay) return false;
  if (day.dayType === 'rest' || day.dayType === 'active_recovery') return false;
  return true;
}

function sortPlannedWorkouts<T extends { sortOrder?: number }>(plannedWorkouts: T[] | undefined): T[] {
  if (!plannedWorkouts?.length) return [];
  return [...plannedWorkouts].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0));
}

function flattenTrainingDays<TDay extends DayLike, TWeek extends WeekLike<TDay>>(
  weeks: TWeek[],
): Array<OrderedDayRef<TWeek, TDay>> {
  return [...weeks]
    .sort((a, b) => a.weekNumber - b.weekNumber)
    .flatMap((week) =>
      [...week.days]
        .sort((a, b) => a.dayNumber - b.dayNumber)
        .filter((d) => isProgramTrainingDaySlot(d))
        .map((day) => ({
          week,
          day,
          weekNumber: week.weekNumber,
          dayNumber: day.dayNumber,
        })),
    );
}

function findDayRef<TWeek, TDay>(
  orderedDays: Array<OrderedDayRef<TWeek, TDay>>,
  weekNumber: number,
  dayNumber: number,
): OrderedDayRef<TWeek, TDay> | undefined {
  return orderedDays.find((r) => r.weekNumber === weekNumber && r.dayNumber === dayNumber);
}

function firstTrainingDayIndexInWeek<TWeek, TDay>(
  orderedDays: Array<OrderedDayRef<TWeek, TDay>>,
  weekNumber: number,
): number {
  const idx = orderedDays.findIndex((r) => r.weekNumber === weekNumber);
  return idx >= 0 ? idx : 0;
}

/** Calendar days since last completed planned workout (UTC); 0 if no anchor. */
export function calendarDaysSinceLastWorkout(
  lastWorkoutCompletedAt: Date | string | null | undefined,
  now: Date,
): number {
  const d = toValidDate(lastWorkoutCompletedAt);
  if (!d) return 0;
  return getProgramCalendarDayIndex(d, now);
}

function isUserTrainingWeekday(now: Date, trainingWeekdays: number[] | null | undefined): boolean {
  if (!trainingWeekdays || trainingWeekdays.length === 0) return true;
  const dow = now.getUTCDay(); // 0=Sun ? 6=Sat ? matches schema comment
  return trainingWeekdays.includes(dow);
}

function plannedWorkoutCompleted(
  progressEntries: ProgressEntryLike[],
  weekNumber: number,
  dayNumber: number,
  plannedWorkoutId: string,
): boolean {
  return progressEntries.some(
    (e) =>
      e.weekNumber === weekNumber &&
      e.dayNumber === dayNumber &&
      e.plannedWorkoutId === plannedWorkoutId &&
      e.status === 'completed',
  );
}

/**
 * First training-day index in template order where not all planned workouts are completed.
 * If all complete, returns orderedDays.length (past end).
 */
function findNaturalTrainingDayIndex<TDay extends DayLike, TWeek extends WeekLike<TDay>>(
  orderedDays: Array<OrderedDayRef<TWeek, TDay>>,
  progressEntries: ProgressEntryLike[],
): number {
  for (let i = 0; i < orderedDays.length; i++) {
    const ref = orderedDays[i]!;
    const dayPlannedWorkouts = sortPlannedWorkouts(ref.day.plannedWorkouts);
    if (dayPlannedWorkouts.length === 0) {
      const dayDone = progressEntries.some(
        (e) =>
          e.weekNumber === ref.weekNumber &&
          e.dayNumber === ref.dayNumber &&
          e.plannedWorkoutId === '__day__' &&
          e.status === 'completed',
      );
      if (!dayDone) return i;
      continue;
    }
    const allPlannedWorkoutsDone = dayPlannedWorkouts.every((pw) =>
      plannedWorkoutCompleted(progressEntries, ref.weekNumber, ref.dayNumber, pw.id),
    );
    if (!allPlannedWorkoutsDone) return i;
  }
  return orderedDays.length;
}

function applyCatchUpSnapIndex(
  naturalIndex: number,
  orderedDays: Array<OrderedDayRef<unknown, unknown>>,
  daysSinceLastSession: number,
): number {
  if (daysSinceLastSession <= 2) return naturalIndex;
  if (naturalIndex >= orderedDays.length) return naturalIndex;
  if (daysSinceLastSession >= 30) return 0;
  // 3?29 days: start of current program week (week of natural position)
  const wn = orderedDays[naturalIndex]!.weekNumber;
  return firstTrainingDayIndexInWeek(orderedDays as any, wn);
}

export interface TrainingPositionMeta<TWeek, TDay> {
  position: ResolvedProgramPosition<TWeek, TDay>;
  naturalIndex: number;
  snappedIndex: number;
  calendarDaysSinceLastWorkout: number;
  orderedTrainingDays: Array<OrderedDayRef<TWeek, TDay>>;
}

/**
 * Same as {@link resolveCurrentProgramDay} but exposes natural vs snapped index for catch-up UX.
 */
export function resolveTrainingPositionMeta<
  TDay extends DayLike,
  TWeek extends WeekLike<TDay>,
>(
  weeks: TWeek[],
  progressEntries: ProgressEntryLike[],
  options: ResolveCurrentProgramDayOptions = {},
): TrainingPositionMeta<TWeek, TDay> {
  const orderedDays = flattenTrainingDays(weeks);
  const now = options.now ?? new Date();
  const daysSince = calendarDaysSinceLastWorkout(options.lastWorkoutCompletedAt ?? null, now);

  if (orderedDays.length === 0) {
  return {
    position: {
      targetWeekNumber: 1,
      targetDayNumber: 1,
      targetWeek: undefined,
      targetDay: undefined,
      completedDayCount: 0,
      targetWeekCompletedDays: 0,
      isProgramComplete: false,
      lastDay: null,
      isTrainingDay: isUserTrainingWeekday(now, options.trainingWeekdays ?? null),
    } as ResolvedProgramPosition<TWeek, TDay>,
    naturalIndex: 0,
    snappedIndex: 0,
    calendarDaysSinceLastWorkout: daysSince,
    orderedTrainingDays: orderedDays as Array<OrderedDayRef<TWeek, TDay>>,
  };
  }

  const lastRef = orderedDays.at(-1)!;
  const completedDays = progressEntries
    .filter((entry) => entry.status === 'completed' && entry.plannedWorkoutId === '__day__')
    .filter((entry) => !!findDayRef(orderedDays, entry.weekNumber, entry.dayNumber))
    .sort(
      (a, b) =>
        orderedDays.findIndex((r) => r.weekNumber === a.weekNumber && r.dayNumber === a.dayNumber) -
        orderedDays.findIndex((r) => r.weekNumber === b.weekNumber && r.dayNumber === b.dayNumber),
    );

  const naturalIndex = findNaturalTrainingDayIndex(orderedDays, progressEntries);
  const isComplete = naturalIndex >= orderedDays.length;

  let snappedIndex: number;
  if (isComplete) {
    snappedIndex = orderedDays.length - 1;
  } else {
    snappedIndex = applyCatchUpSnapIndex(naturalIndex, orderedDays, daysSince);
    snappedIndex = Math.min(Math.max(0, snappedIndex), orderedDays.length - 1);
  }

  const targetRef = isComplete ? lastRef : orderedDays[snappedIndex]!;
  const weekNumber = targetRef.weekNumber;
  const dayNumber = targetRef.dayNumber;

  const position = {
    targetWeekNumber: weekNumber,
    targetDayNumber: dayNumber,
    targetWeek: targetRef.week,
    targetDay: targetRef.day,
    completedDayCount: completedDays.length,
    targetWeekCompletedDays: completedDays.filter((e) => e.weekNumber === weekNumber).length,
    isProgramComplete: isComplete,
    lastDay: { weekNumber: lastRef.weekNumber, dayNumber: lastRef.dayNumber },
    calendarSlotMissing: !findDayRef(orderedDays, weekNumber, dayNumber) || undefined,
    isTrainingDay: isUserTrainingWeekday(now, options.trainingWeekdays ?? null),
  } as ResolvedProgramPosition<TWeek, TDay>;

  return {
    position,
    naturalIndex,
    snappedIndex,
    calendarDaysSinceLastWorkout: daysSince,
    orderedTrainingDays: orderedDays as Array<OrderedDayRef<TWeek, TDay>>,
  };
}

/**
 * Completion-based program position + auto catch-up (calendar gap since last planned workout).
 * Training template days may include legacy rest rows; only non-rest slots participate.
 */
export function resolveCurrentProgramDay<
  TDay extends DayLike,
  TWeek extends WeekLike<TDay>,
>(
  weeks: TWeek[],
  progressEntries: ProgressEntryLike[],
  options: ResolveCurrentProgramDayOptions = {},
): ResolvedProgramPosition<TWeek, TDay> {
  return resolveTrainingPositionMeta(weeks, progressEntries, options).position as ResolvedProgramPosition<
    TWeek,
    TDay
  >;
}

/** Total training-day slots across all weeks (for progress %). */
export function countTrainingDaySlots<TDay extends DayLike, TWeek extends WeekLike<TDay>>(
  weeks: TWeek[],
): number {
  return flattenTrainingDays(weeks).length;
}
