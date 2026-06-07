/**
 * Shared form types, factories, and clone/normalize helpers for the program
 * Calendar builder (used by the create and edit pages and the
 * ProgramCalendarBuilder component).
 */
import type { LocalizedText } from '@/lib/types/localized';

export type ProgramDayType = 'training' | 'rest' | 'active_recovery';

export interface PlannedWorkoutItemForm {
  id?: string;
  type: 'exercise' | 'rest';
  exerciseId?: string;
  sets: number;
  targetReps?: number;
  targetDuration?: number;
  restBetweenSetsMs: number;
  weightKg?: number;
  weightPerSetText: string;
  notes: LocalizedText;
  restDurationMs?: number;
}

export interface PlannedWorkoutForm {
  id?: string;
  name: LocalizedText;
  sortOrder: number;
  estimatedDurationMin?: number | null;
  items: PlannedWorkoutItemForm[];
}

export interface DayForm {
  id?: string;
  dayNumber: number;
  dayType: ProgramDayType;
  isRestDay: boolean;
  targetMuscleIds: string[];
  plannedWorkouts: PlannedWorkoutForm[];
}

export interface WeekForm {
  id?: string;
  weekNumber: number;
  target: LocalizedText;
  description: LocalizedText;
  sortOrder: number;
  days: DayForm[];
}

export const createEmptyItem = (
  type: 'exercise' | 'rest',
  exerciseId?: string,
): PlannedWorkoutItemForm => ({
  type,
  exerciseId,
  sets: 3,
  targetReps: type === 'exercise' ? 10 : undefined,
  targetDuration: undefined,
  restBetweenSetsMs: 30000,
  weightKg: undefined,
  weightPerSetText: '',
  notes: { ar: '', en: '' },
  restDurationMs: type === 'rest' ? 60000 : undefined,
});

export const createEmptyPlannedWorkout = (sortOrder: number): PlannedWorkoutForm => ({
  name: { ar: 'صباحا', en: 'Morning' },
  sortOrder,
  estimatedDurationMin: undefined,
  items: [],
});

export const createEmptyDay = (dayNumber: number): DayForm => ({
  dayNumber,
  dayType: 'training',
  isRestDay: false,
  targetMuscleIds: [],
  plannedWorkouts: [createEmptyPlannedWorkout(0)],
});

export const createEmptyWeek = (weekNumber: number): WeekForm => ({
  weekNumber,
  target: { ar: '', en: '' },
  description: { ar: '', en: '' },
  sortOrder: weekNumber - 1,
  days: Array.from({ length: 7 }, (_, i) => createEmptyDay(i + 1)),
});

export function deriveDayTypeFields(dayType: ProgramDayType): Pick<DayForm, 'dayType' | 'isRestDay'> {
  return {
    dayType,
    isRestDay: dayType !== 'training',
  };
}

export function cloneItem(item: PlannedWorkoutItemForm): PlannedWorkoutItemForm {
  return {
    ...item,
    notes: { ...item.notes },
  };
}

export function clonePlannedWorkout(plannedWorkout: PlannedWorkoutForm): PlannedWorkoutForm {
  return {
    ...plannedWorkout,
    name: { ...plannedWorkout.name },
    items: plannedWorkout.items.map(cloneItem),
  };
}

export function cloneDay(day: DayForm): DayForm {
  return {
    ...day,
    targetMuscleIds: [...day.targetMuscleIds],
    plannedWorkouts: day.plannedWorkouts.map(clonePlannedWorkout),
  };
}

export function cloneWeek(week: WeekForm): WeekForm {
  return {
    ...week,
    target: { ...week.target },
    description: { ...week.description },
    days: week.days.map(cloneDay),
  };
}

export function normalizePlannedWorkout(
  plannedWorkout: PlannedWorkoutForm,
  plannedWorkoutIndex: number,
): PlannedWorkoutForm {
  return {
    ...plannedWorkout,
    sortOrder: plannedWorkoutIndex,
  };
}

export function normalizeDay(day: DayForm, dayIndex: number): DayForm {
  const dayType = day.dayType ?? 'training';
  return {
    ...day,
    dayNumber: dayIndex + 1,
    ...deriveDayTypeFields(dayType),
    plannedWorkouts: day.plannedWorkouts.map(normalizePlannedWorkout),
  };
}

export function normalizeWeek(week: WeekForm, weekIndex: number): WeekForm {
  return {
    ...week,
    weekNumber: weekIndex + 1,
    sortOrder: weekIndex,
    days: week.days.map(normalizeDay),
  };
}
