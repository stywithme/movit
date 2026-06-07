import type { LocalizedText } from '@/lib/types/localized';

/**
 * Build phase metadata for program create/update.
 * Program weeks/days/workouts are always sent separately as the calendar source of truth.
 */

export interface PhaseFormLike {
  id: string;
  name: LocalizedText;
  startWeek: number;
  endWeek: number;
}

export interface WeekFormLike {
  weekNumber: number;
  weekType: 'NORMAL' | 'DELOAD';
  name: LocalizedText;
  description: LocalizedText;
  sortOrder: number;
}

export interface ProgramPhaseApiPayload {
  id?: string;
  name: LocalizedText;
  description?: LocalizedText;
  weekType?: 'NORMAL' | 'DELOAD';
  startWeek: number;
  endWeek: number;
  sortOrder?: number;
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
    };
  });
}
