import type { LocalizedText } from '@/lib/types/localized';

/** Minimal week/day/planned-workout shapes used by program editor pages (subset of WeekForm/DayForm). */
export interface EditorPlannedWorkoutLike {
  id?: string;
  name: LocalizedText;
  sortOrder: number;
  estimatedDurationMin?: number | null;
  items: unknown[];
}

export interface EditorDayLike {
  id?: string;
  dayNumber: number;
  isRestDay: boolean;
  name: LocalizedText;
  dayFocus?: string;
  plannedWorkouts: EditorPlannedWorkoutLike[];
}

export interface EditorWeekLike {
  id?: string;
  days: EditorDayLike[];
}

function createPlaceholderDay(dayNumber: number): EditorDayLike {
  return {
    dayNumber,
    isRestDay: false,
    name: { ar: '', en: '' },
    dayFocus: '',
    plannedWorkouts: [],
  };
}

/**
 * Ensures each week has exactly 7 training slots (dayNumber 1…7).
 * Preserves existing days by index; pads missing indices with empty days.
 */
export function padWeekDaysToSeven<W extends EditorWeekLike>(week: W): W {
  const nextDays: EditorDayLike[] = [];
  for (let i = 0; i < 7; i++) {
    const existing = week.days[i];
    if (existing) {
      nextDays.push({
        ...existing,
        dayNumber: i + 1,
      });
    } else {
      nextDays.push(createPlaceholderDay(i + 1));
    }
  }
  return { ...week, days: nextDays as W['days'] };
}

export function padProgramWeeksToSevenDays<W extends EditorWeekLike>(weeks: W[]): W[] {
  return weeks.map(padWeekDaysToSeven);
}
