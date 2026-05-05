import type { LocalizedText } from '@/lib/types/localized';

/**
 * Build `phases` payload for program create/update when each phase's weeks share an identical
 * weekly calendar (same as "Apply Pattern" semantics). Otherwise returns null — caller should
 * send flat `weeks` only so per-week edits are preserved.
 */

export interface PhaseFormLike {
  id: string;
  name: LocalizedText;
  startWeek: number;
  endWeek: number;
}

export interface SessionItemFormLike {
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

export interface SessionFormLike {
  id?: string;
  name: LocalizedText;
  sortOrder: number;
  role?: string;
  estimatedDurationMin?: number | null;
  items: SessionItemFormLike[];
}

export interface DayFormLike {
  id?: string;
  dayNumber: number;
  isRestDay: boolean;
  name: LocalizedText;
  dayFocus?: string;
  sessions: SessionFormLike[];
}

export interface WeekFormLike {
  weekNumber: number;
  weekType: 'NORMAL' | 'DELOAD';
  name: LocalizedText;
  description: LocalizedText;
  sortOrder: number;
  days: DayFormLike[];
}

export interface ProgramPhaseApiPayload {
  id?: string;
  name: LocalizedText;
  description?: LocalizedText;
  weekType?: 'NORMAL' | 'DELOAD';
  startWeek: number;
  endWeek: number;
  sortOrder?: number;
  weeklyPattern?: {
    days: Array<{
      dayNumber: number;
      isRestDay?: boolean;
      name?: LocalizedText;
      dayFocus?: string;
      sessions?: Array<Record<string, unknown>>;
    }>;
  };
}

function mapItemsForApi(
  items: SessionItemFormLike[],
): Array<Record<string, unknown>> {
  return items.map((item, itemIndex) => ({
    ...(item.id ? { id: item.id } : {}),
    type: item.type,
    exerciseId: item.type === 'exercise' ? item.exerciseId : undefined,
    sets: item.type === 'exercise' ? item.sets : undefined,
    targetReps: item.type === 'exercise' ? item.targetReps || undefined : undefined,
    targetDuration: item.type === 'exercise' ? item.targetDuration || undefined : undefined,
    restBetweenSetsMs: item.type === 'exercise' ? item.restBetweenSetsMs : undefined,
    weightKg: item.type === 'exercise' ? item.weightKg || undefined : undefined,
    weightPerSet:
      item.type === 'exercise' && item.weightPerSetText
        ? item.weightPerSetText
            .split(',')
            .map((value) => Number.parseFloat(value.trim()))
            .filter((value) => Number.isFinite(value))
        : undefined,
    notes: item.type === 'exercise' && (item.notes.en || item.notes.ar) ? item.notes : undefined,
    restDurationMs: item.type === 'rest' ? item.restDurationMs : undefined,
    sortOrder: itemIndex,
  }));
}

function mapSessionsForPattern(sessions: SessionFormLike[]): Array<Record<string, unknown>> {
  return sessions.map((session, sessionIndex) => ({
    ...(session.id ? { id: session.id } : {}),
    name: session.name,
    sortOrder: session.sortOrder ?? sessionIndex,
    role: session.role || 'MAIN',
    estimatedDurationMin:
      session.estimatedDurationMin === undefined || session.estimatedDurationMin === null
        ? undefined
        : session.estimatedDurationMin,
    items: mapItemsForApi(session.items),
  }));
}

/** Semantic calendar fingerprint for one week — ignores DB ids on weeks/days/sessions/items. */
export function weekCalendarFingerprint(week: WeekFormLike): string {
  const normalized = {
    weekType: week.weekType,
    days: week.days.map((day, dayIndex) => ({
      dayNumber: day.dayNumber || dayIndex + 1,
      isRestDay: day.isRestDay,
      dayFocus: day.dayFocus ?? '',
      name: day.name,
      sessions: day.sessions.map((session, sessionIndex) => ({
        name: session.name,
        sortOrder: session.sortOrder ?? sessionIndex,
        role: session.role ?? 'MAIN',
        estimatedDurationMin: session.estimatedDurationMin ?? undefined,
        items: session.items.map((item, itemIndex) => ({
          type: item.type,
          exerciseId: item.type === 'exercise' ? item.exerciseId : undefined,
          sets: item.type === 'exercise' ? item.sets : undefined,
          targetReps: item.type === 'exercise' ? item.targetReps ?? undefined : undefined,
          targetDuration: item.type === 'exercise' ? item.targetDuration ?? undefined : undefined,
          restBetweenSetsMs: item.type === 'exercise' ? item.restBetweenSetsMs : undefined,
          weightKg: item.type === 'exercise' ? item.weightKg ?? undefined : undefined,
          weightPerSetText: item.type === 'exercise' ? item.weightPerSetText : undefined,
          notes: item.type === 'exercise' ? item.notes : undefined,
          restDurationMs: item.type === 'rest' ? item.restDurationMs : undefined,
          sortOrder: itemIndex,
        })),
      })),
    })),
  };
  return JSON.stringify(normalized);
}

export function phaseWeeksAreHomogeneous(
  phase: PhaseFormLike,
  weeks: WeekFormLike[],
): boolean {
  const firstIdx = phase.startWeek - 1;
  const first = weeks[firstIdx];
  if (!first) return false;
  const sig = weekCalendarFingerprint(first);
  for (let wn = phase.startWeek; wn <= phase.endWeek; wn++) {
    const w = weeks[wn - 1];
    if (!w) return false;
    if (weekCalendarFingerprint(w) !== sig) return false;
  }
  return true;
}

export function allPhasesHomogeneous(
  phases: PhaseFormLike[],
  weeks: WeekFormLike[],
): boolean {
  return phases.every((p) => phaseWeeksAreHomogeneous(p, weeks));
}

export function buildProgramPhasesPayload(
  phases: PhaseFormLike[],
  weeks: WeekFormLike[],
): ProgramPhaseApiPayload[] {
  return phases.map((phase, idx) => {
    const templateWeek = weeks[phase.startWeek - 1];
    if (!templateWeek) {
      throw new Error(`Invalid phase ${phase.startWeek}: missing template week`);
    }
    return {
      ...(phase.id && !phase.id.startsWith('phase-') ? { id: phase.id } : {}),
      name: phase.name,
      description:
        templateWeek.description.en || templateWeek.description.ar ? templateWeek.description : undefined,
      weekType: templateWeek.weekType,
      startWeek: phase.startWeek,
      endWeek: phase.endWeek,
      sortOrder: idx,
      weeklyPattern: {
        days: templateWeek.days.map((day, dIdx) => ({
          dayNumber: day.dayNumber || dIdx + 1,
          isRestDay: day.isRestDay,
          name: day.name.en || day.name.ar ? day.name : undefined,
          dayFocus: day.dayFocus?.trim() ? day.dayFocus : undefined,
          sessions: mapSessionsForPattern(day.sessions),
        })),
      },
    };
  });
}
