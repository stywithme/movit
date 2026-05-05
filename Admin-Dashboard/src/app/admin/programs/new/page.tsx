'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { toast } from 'react-hot-toast';
import { useRouter, useSearchParams } from 'next/navigation';
import { Input, Select, Label, Button, Card, Textarea, SearchableSelect } from '@/components/ui';
import type { LocalizedText } from '@/lib/types/localized';
import { CollapsibleBuilderSection } from '../_components/CollapsibleBuilderSection';
import { getAutoAssignmentReadiness } from '../_lib/auto-assignment';
import { ProgramAttributesSection, useAttributesCatalog } from '../_components/ProgramAttributesSection';
import { buildValueIdMeta, type ProgramAttributeFormRow } from '../_lib/program-prescription-attributes';
import { exerciseProgramAttributeStatus } from '../_lib/exercise-program-attribute-status';
import { PROGRAM_EDITOR_TABS, type ProgramEditorTabId } from '../_components/program-editor-tabs';
import { ProgramEditorTabBar } from '../_components/ProgramEditorTabBar';

interface ProgramSummary {
  id: string;
  name: LocalizedText;
}

const SESSION_ITEM_ROLE_OPTIONS = [
  { value: '', label: '—' },
  { value: 'WARMUP', label: 'WARMUP' },
  { value: 'ACTIVATION', label: 'ACTIVATION' },
  { value: 'MAIN', label: 'MAIN' },
  { value: 'ACCESSORY', label: 'ACCESSORY' },
  { value: 'CORRECTIVE', label: 'CORRECTIVE' },
  { value: 'COOLDOWN', label: 'COOLDOWN' },
  { value: 'TEST', label: 'TEST' },
];

const SESSION_ITEM_INTENT_OPTIONS = [
  { value: '', label: '—' },
  { value: 'STANDARD', label: 'STANDARD' },
  { value: 'POWER', label: 'POWER' },
  { value: 'ECCENTRIC', label: 'ECCENTRIC' },
  { value: 'VELOCITY_BASED', label: 'VELOCITY_BASED' },
];

interface ExerciseSummary {
  id: string;
  name: LocalizedText;
  countingMethod?: {
    code: string;
  };
  attributes?: Array<{
    attributeValueId: string;
    attributeValue?: { id: string; code: string; attribute?: { code: string } };
  }>;
}

interface WorkoutSummary {
  id: string;
  name: LocalizedText;
}

interface WorkoutDetails {
  id: string;
  name: LocalizedText;
  exercises: Array<{
    exerciseId: string;
    targetReps?: number;
    targetDuration?: number;
    sets: number;
    restBetweenSetsMs: number;
    restAfterExerciseMs: number;
    weightKg?: number;
    weightPerSet?: number[];
    notes?: LocalizedText;
    sortOrder: number;
  }>;
}

interface SessionItemForm {
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
  role?: string;
  intent?: string;
  coachingNotesJson?: string;
}

interface SessionForm {
  name: LocalizedText;
  sortOrder: number;
  items: SessionItemForm[];
}

interface DayForm {
  dayNumber: number;
  isRestDay: boolean;
  name: LocalizedText;
  dayFocus?: string;
  sessions: SessionForm[];
}

interface WeekForm {
  weekNumber: number;
  name: LocalizedText;
  description: LocalizedText;
  sortOrder: number;
  weekType: 'NORMAL' | 'DELOAD';
  days: DayForm[];
}

interface PhaseForm {
  id: string;
  name: LocalizedText;
  startWeek: number;
  endWeek: number;
}

const createEmptyItem = (type: 'exercise' | 'rest', exerciseId?: string): SessionItemForm => ({
  type,
  exerciseId,
  sets: 3,
  targetReps: type === 'exercise' ? 10 : undefined,
  targetDuration: type === 'exercise' ? undefined : undefined,
  restBetweenSetsMs: 30000,
  weightKg: undefined,
  weightPerSetText: '',
  notes: { ar: '', en: '' },
  restDurationMs: type === 'rest' ? 60000 : undefined,
  role: '',
  intent: '',
  coachingNotesJson: '',
});

const createEmptySession = (sortOrder: number): SessionForm => ({
  name: { ar: 'صباحا', en: 'Morning' },
  sortOrder,
  items: [],
});

const createEmptyDay = (dayNumber: number): DayForm => ({
  dayNumber,
  isRestDay: false,
  name: { ar: '', en: '' },
  dayFocus: '',
  sessions: [createEmptySession(0)],
});

const createEmptyWeek = (weekNumber: number): WeekForm => ({
  weekNumber,
  name: { ar: '', en: '' },
  description: { ar: '', en: '' },
  sortOrder: weekNumber - 1,
  weekType: 'NORMAL',
  days: [createEmptyDay(1)],
});

const createEmptyPhase = (start: number, end: number): PhaseForm => ({
  id: `phase-${Date.now()}`,
  name: { ar: '', en: `Phase ${start}-${end}` },
  startWeek: start,
  endWeek: end,
});

function cloneItem(item: SessionItemForm): SessionItemForm {
  return {
    ...item,
    notes: { ...item.notes },
  };
}

function cloneSession(session: SessionForm): SessionForm {
  return {
    ...session,
    name: { ...session.name },
    items: session.items.map(cloneItem),
  };
}

function cloneDay(day: DayForm): DayForm {
  return {
    ...day,
    name: { ...day.name },
    sessions: day.sessions.map(cloneSession),
  };
}

function cloneWeek(week: WeekForm): WeekForm {
  return {
    ...week,
    name: { ...week.name },
    description: { ...week.description },
    days: week.days.map(cloneDay),
  };
}

function clonePhase(phase: PhaseForm): PhaseForm {
  return {
    ...phase,
    name: { ...phase.name },
  };
}

function normalizeSession(session: SessionForm, sessionIndex: number): SessionForm {
  return {
    ...session,
    sortOrder: sessionIndex,
  };
}

function normalizeDay(day: DayForm, dayIndex: number): DayForm {
  return {
    ...day,
    dayNumber: dayIndex + 1,
    isRestDay: false,
    sessions: day.sessions.map(normalizeSession),
  };
}

function normalizeWeek(week: WeekForm, weekIndex: number): WeekForm {
  return {
    ...week,
    weekNumber: weekIndex + 1,
    sortOrder: weekIndex,
    days: week.days.map(normalizeDay),
  };
}

export default function NewProgramPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [loading, setLoading] = useState(false);
  const [loadingExercises, setLoadingExercises] = useState(true);
  const [loadingWorkouts, setLoadingWorkouts] = useState(true);

  const [name, setName] = useState({ ar: '', en: '' });
  const [description, setDescription] = useState({ ar: '', en: '' });
  const [coverImageUrl, setCoverImageUrl] = useState('');
  const [durationWeeks, setDurationWeeks] = useState(4);
  const [version, setVersion] = useState(1);
  const [tags, setTags] = useState('');
  const [weeks, setWeeks] = useState<WeekForm[]>([createEmptyWeek(1)]);
  const [phases, setPhases] = useState<PhaseForm[]>([createEmptyPhase(1, 4)]);

  const [programOwnership, setProgramOwnership] = useState<'SYSTEM' | 'COACH' | 'CUSTOM'>('SYSTEM');
  const { catalog: attributeCatalog, loading: loadingAttributeCatalog, error: attributeCatalogError } =
    useAttributesCatalog();
  const [programAttributeRows, setProgramAttributeRows] = useState<ProgramAttributeFormRow[]>([]);
  const [autoAssignable, setAutoAssignable] = useState(false);
  const [coachingNotesProgram, setCoachingNotesProgram] = useState('');
  const [weeklySessionTarget, setWeeklySessionTarget] = useState<number | ''>('');
  const [estimatedSessionMinutes, setEstimatedSessionMinutes] = useState<number | ''>('');
  /** Single training level (stored as levelRangeMin === levelRangeMax on the API). */
  const [trainingLevel, setTrainingLevel] = useState(1);
  const [prescriptionPriority, setPrescriptionPriority] = useState(50);
  const [prerequisiteProgramId, setPrerequisiteProgramId] = useState('');
  const [nextProgramId, setNextProgramId] = useState('');

  const [editorTab, setEditorTab] = useState<ProgramEditorTabId>('basics');

  const [exercises, setExercises] = useState<ExerciseSummary[]>([]);
  const [workouts, setWorkouts] = useState<WorkoutSummary[]>([]);
  const [publishedPrograms, setPublishedPrograms] = useState<ProgramSummary[]>([]);

  useEffect(() => {
    const fetchExercises = async () => {
      try {
        const res = await fetch('/api/exercises?status=published&limit=200&includeAttributes=true');
        const data = await res.json();
        if (data.success) {
          setExercises(data.data);
        }
      } catch (error) {
        console.error('Error fetching exercises:', error);
      } finally {
        setLoadingExercises(false);
      }
    };
    fetchExercises();
  }, []);

  useEffect(() => {
    const fetchWorkouts = async () => {
      try {
        const res = await fetch('/api/workouts?status=published&limit=200');
        const data = await res.json();
        if (data.success) {
          setWorkouts(data.data);
        }
      } catch (error) {
        console.error('Error fetching workouts:', error);
      } finally {
        setLoadingWorkouts(false);
      }
    };
    fetchWorkouts();
  }, []);

  useEffect(() => {
    const fetchPublishedPrograms = async () => {
      try {
        const res = await fetch('/api/programs?status=published&limit=200');
        const data = await res.json();
        if (data.success) {
          setPublishedPrograms(data.data);
        }
      } catch (error) {
        console.error('Error fetching published programs:', error);
      }
    };
    fetchPublishedPrograms();
  }, []);

  const publishedProgramOptions = useMemo(
    () => [
      { value: '', label: 'None' },
      ...publishedPrograms.map((p) => ({
        value: p.id,
        label: `${p.name.en} / ${p.name.ar}`,
      })),
    ],
    [publishedPrograms]
  );

  const exerciseOptions = useMemo(
    () =>
      exercises.map((exercise) => ({
        value: exercise.id,
        label: `${exercise.name.en} / ${exercise.name.ar}`,
      })),
    [exercises]
  );

  const workoutOptions = useMemo(
    () =>
      workouts.map((workout) => ({
        value: workout.id,
        label: `${workout.name.en} / ${workout.name.ar}`,
      })),
    [workouts]
  );

  const exerciseLabelById = useMemo(
    () =>
      new Map(
        exercises.map((exercise) => [exercise.id, `${exercise.name.en} / ${exercise.name.ar}`])
      ),
    [exercises]
  );

  const valueIdMeta = useMemo(() => buildValueIdMeta(attributeCatalog), [attributeCatalog]);

  const programAttributesForReadiness = useMemo(
    () =>
      programAttributeRows.map((row) => {
        const m = valueIdMeta.get(row.attributeValueId);
        return {
          mode: row.mode,
          attributeValue: {
            code: m?.valueCode ?? '',
            attribute: { code: m?.attributeCode ?? '' },
          },
        };
      }),
    [programAttributeRows, valueIdMeta],
  );

  const autoAssignmentReadiness = useMemo(
    () =>
      getAutoAssignmentReadiness({
        programType: programOwnership,
        autoAssignable,
        levelRangeMin: trainingLevel,
        levelRangeMax: trainingLevel,
        prescriptionPriority,
        programAttributes: programAttributesForReadiness,
      }),
    [programOwnership, autoAssignable, trainingLevel, prescriptionPriority, programAttributesForReadiness],
  );

  const exerciseAttributeCheck = useCallback(
    (exerciseId: string | undefined) => {
      if (!exerciseId || programAttributeRows.length === 0) return null;
      const ex = exercises.find((e) => e.id === exerciseId);
      const ids = ex?.attributes?.map((a) => a.attributeValueId) ?? [];
      return exerciseProgramAttributeStatus(ids, programAttributeRows, valueIdMeta);
    },
    [exercises, programAttributeRows, valueIdMeta],
  );

  const prefillSummary = useMemo(() => {
    const parts: string[] = [];
    const domain = searchParams.get('programDomain');
    const min = searchParams.get('levelRangeMin');
    const max = searchParams.get('levelRangeMax');
    const goal = searchParams.get('trainingGoal');

    if (domain) parts.push(domain);
    if (min || max) {
      parts.push(min && max && min === max ? `Level ${min}` : `Levels ${min ?? '—'}-${max ?? '—'}`);
    }
    if (goal) parts.push(goal);

    return parts;
  }, [searchParams]);

  const builderSummary = useMemo(() => {
    const days = weeks.reduce((acc, week) => acc + week.days.length, 0);
    const sessions = weeks.reduce(
      (acc, week) => acc + week.days.reduce((dayAcc, day) => dayAcc + day.sessions.length, 0),
      0
    );
    const items = weeks.reduce(
      (acc, week) =>
        acc +
        week.days.reduce(
          (dayAcc, day) =>
            dayAcc + day.sessions.reduce((sessionAcc, session) => sessionAcc + session.items.length, 0),
          0
        ),
      0
    );

    return { weeks: weeks.length, days, sessions, items };
  }, [weeks]);

  // --- Phase helpers (minimal Phase Builder) ---
  const getPhaseStats = (phase: PhaseForm) => {
    const phaseWeeks = weeks.slice(Math.max(0, phase.startWeek - 1), phase.endWeek);
    if (!phaseWeeks.length) return { weeks: 0, days: 0, sessions: 0, items: 0 };
    const days = phaseWeeks.reduce((acc, w) => acc + w.days.length, 0);
    const sessions = phaseWeeks.reduce(
      (acc, w) => acc + w.days.reduce((dAcc, d) => dAcc + d.sessions.length, 0),
      0
    );
    const items = phaseWeeks.reduce(
      (acc, w) =>
        acc +
        w.days.reduce(
          (dAcc, d) =>
            dAcc + d.sessions.reduce((sAcc, s) => sAcc + s.items.length, 0),
          0
        ),
      0
    );
    return { weeks: phaseWeeks.length, days, sessions, items };
  };

  const phasesOverlap = (list: PhaseForm[]): boolean => {
    const sorted = [...list].sort((a, b) => a.startWeek - b.startWeek);
    for (let i = 1; i < sorted.length; i++) {
      if (sorted[i].startWeek <= sorted[i - 1].endWeek) return true;
    }
    return false;
  };

  const syncPhasesToWeeks = (newPhases: PhaseForm[]) => {
    if (!newPhases.length) {
      setDurationWeeks(1);
      setWeeks([createEmptyWeek(1)].map(normalizeWeek));
      return;
    }
    const maxEnd = Math.max(...newPhases.map((p) => p.endWeek));
    const target = Math.max(1, maxEnd);
    setDurationWeeks(target);
    setWeeks((prev) => {
      const next: WeekForm[] = [];
      for (let wn = 1; wn <= target; wn++) {
        const existing = prev.find((w) => w.weekNumber === wn);
        if (existing) {
          next.push({ ...existing, weekNumber: wn, sortOrder: wn - 1 });
        } else {
          next.push(createEmptyWeek(wn));
        }
      }
      return next.map(normalizeWeek);
    });
  };

  const addPhase = () => {
    const lastEnd = phases.length > 0 ? Math.max(...phases.map((p) => p.endWeek)) : 0;
    const start = lastEnd + 1;
    const end = start + 3; // default length 4 weeks
    const newPhase = createEmptyPhase(start, end);
    const nextPhases = [...phases, newPhase];
    setPhases(nextPhases);
    syncPhasesToWeeks(nextPhases);
  };

  const updatePhase = (phaseId: string, updates: Partial<PhaseForm>) => {
    setPhases((prev) => {
      const next = prev.map((p) => (p.id === phaseId ? { ...p, ...updates } : p));
      // simple overlap check
      if (phasesOverlap(next)) {
        toast.error('Phase ranges must not overlap');
        return prev; // revert
      }
      syncPhasesToWeeks(next);
      return next;
    });
  };

  const removePhase = (phaseId: string) => {
    if (phases.length === 1) {
      toast.error('At least one phase is required');
      return;
    }
    const next = phases.filter((p) => p.id !== phaseId);
    setPhases(next);
    syncPhasesToWeeks(next);
  };

  const applyPatternToPhase = (phaseId: string) => {
    const phase = phases.find((p) => p.id === phaseId);
    if (!phase) return;
    const phaseLen = phase.endWeek - phase.startWeek + 1;
    if (phaseLen < 2) {
      toast('Phase needs at least 2 weeks to apply pattern');
      return;
    }
    const sourceIdx = phase.startWeek - 1;
    const source = weeks[sourceIdx];
    if (!source) return;
    setWeeks((prev) =>
      prev.map((week, idx) => {
        const wn = idx + 1;
        if (wn > phase.startWeek && wn <= phase.endWeek) {
          return {
            ...cloneWeek(source),
            weekNumber: wn,
            sortOrder: wn - 1,
          };
        }
        return week;
      }).map(normalizeWeek)
    );
  };

  const applyBulkWeekTypeToPhase = (phaseId: string, weekType: 'NORMAL' | 'DELOAD') => {
    const phase = phases.find((p) => p.id === phaseId);
    if (!phase) return;
    setWeeks((prev) =>
      prev.map((week, idx) => {
        const wn = idx + 1;
        if (wn >= phase.startWeek && wn <= phase.endWeek) {
          return { ...week, weekType };
        }
        return week;
      })
    );
  };

  const calendarStructureWarnings = useMemo(() => {
    const messages: string[] = [];
    if (durationWeeks !== weeks.length) {
      messages.push(
        `Duration is set to ${durationWeeks} week(s), but the builder currently contains ${weeks.length} week block(s).`
      );
    }
    weeks.forEach((week, wi) => {
      if (week.days.length < 1) {
        messages.push(`Week ${wi + 1}: add at least one training day before publishing.`);
      }
      const badDayNumber = week.days.some((d) => d.dayNumber < 1 || d.dayNumber > 14);
      if (badDayNumber) {
        messages.push(`Week ${wi + 1}: day numbers should be between 1 and 14.`);
      }
    });
    if (phasesOverlap(phases)) {
      messages.push('Phases have overlapping week ranges.');
    }
    const covered = new Set<number>();
    phases.forEach((p) => {
      for (let i = p.startWeek; i <= p.endWeek; i++) covered.add(i);
    });
    if (covered.size !== durationWeeks) {
      messages.push('Phases do not fully cover weeks 1 to duration.');
    }
    return messages;
  }, [durationWeeks, weeks, phases]);

  const getWeekSummary = (week: WeekForm) => {
    const sessions = week.days.reduce((acc, day) => acc + day.sessions.length, 0);
    const items = week.days.reduce(
      (acc, day) => acc + day.sessions.reduce((sessionAcc, session) => sessionAcc + session.items.length, 0),
      0
    );
    return `${week.days.length} day(s) • ${sessions} session(s) • ${items} item(s)`;
  };

  const getDaySummary = (day: DayForm) => {
    const items = day.sessions.reduce((acc, session) => acc + session.items.length, 0);
    return `Training day • ${day.sessions.length} session(s) • ${items} item(s)`;
  };

  const getSessionSummary = (session: SessionForm) => {
    const exerciseCount = session.items.filter((item) => item.type === 'exercise').length;
    const restCount = session.items.length - exerciseCount;
    return `${session.items.length} item(s) • ${exerciseCount} exercise(s)${restCount ? ` • ${restCount} rest` : ''}`;
  };

  const getItemSummary = (item: SessionItemForm) => {
    if (item.type === 'rest') {
      return `${item.restDurationMs ?? 0} ms rest`;
    }

    const exerciseName = item.exerciseId ? exerciseLabelById.get(item.exerciseId) : null;
    const target =
      item.targetReps != null
        ? `${item.targetReps} reps`
        : item.targetDuration != null
          ? `${item.targetDuration}s`
          : 'No target';
    return `${exerciseName ?? 'No exercise selected'} • ${item.sets} set(s) • ${target}`;
  };

  useEffect(() => {
    const min = Number.parseInt(searchParams.get('levelRangeMin') || '', 10);
    const max = Number.parseInt(searchParams.get('levelRangeMax') || '', 10);
    if (Number.isFinite(min) && min > 0) {
      setTrainingLevel(Number.isFinite(max) && max > 0 ? Math.min(min, max) : min);
    } else if (Number.isFinite(max) && max > 0) {
      setTrainingLevel(max);
    }
  }, [searchParams]);

  useEffect(() => {
    if (!attributeCatalog.length) return;
    if (programAttributeRows.length > 0) return;
    const programDomain = searchParams.get('programDomain');
    if (programDomain !== 'TRAINING' && programDomain !== 'MOBILITY' && programDomain !== 'THERAPEUTIC') {
      return;
    }
    const domainCode =
      programDomain === 'MOBILITY' ? 'pd_mobility' : programDomain === 'THERAPEUTIC' ? 'pd_therapeutic' : 'pd_training';
    const domainAttr = attributeCatalog.find((a) => a.code === 'domain');
    const dv = domainAttr?.values.find((v) => v.code === domainCode);
    const rows: ProgramAttributeFormRow[] = [];
    if (dv) rows.push({ attributeValueId: dv.id, mode: 'REQUIRED' });
    const goalParam = searchParams.get('trainingGoal');
    if (goalParam && programDomain === 'TRAINING') {
      const goalCodeMap: Record<string, string> = {
        STRENGTH: 'pg_strength',
        HYPERTROPHY: 'pg_hypertrophy',
        POWER: 'pg_power',
        GENERAL_HEALTH: 'pg_general_health',
      };
      const gc = goalCodeMap[goalParam];
      if (gc) {
        const goalAttr = attributeCatalog.find((a) => a.code === 'goal');
        const gv = goalAttr?.values.find((v) => v.code === gc);
        if (gv) rows.push({ attributeValueId: gv.id, mode: 'REQUIRED' });
      }
    }
    if (rows.length > 0) setProgramAttributeRows(rows);
  }, [attributeCatalog, searchParams, programAttributeRows.length]);

  // Auto-align Duration with phases (phases drive duration)
  useEffect(() => {
    if (!phases.length) return;
    const maxEnd = Math.max(...phases.map((p) => p.endWeek));
    if (maxEnd !== durationWeeks && maxEnd > 0) {
      setDurationWeeks(maxEnd);
    }
  }, [phases]);

  // When duration changed manually, extend/truncate last phase (simple, no confirm for basic)
  useEffect(() => {
    if (!phases.length || durationWeeks < 1) return;
    const maxEnd = Math.max(...phases.map((p) => p.endWeek));
    if (maxEnd !== durationWeeks) {
      const lastPhase = phases[phases.length - 1];
      if (durationWeeks > maxEnd) {
        // extend last phase
        setPhases((prev) =>
          prev.map((p, idx) => (idx === prev.length - 1 ? { ...p, endWeek: durationWeeks } : p))
        );
      } else {
        // truncate
        setPhases((prev) =>
          prev.map((p, idx) => (idx === prev.length - 1 ? { ...p, endWeek: durationWeeks } : p))
        );
      }
    }
  }, [durationWeeks]);

  const updateWeek = (weekIndex: number, updates: Partial<WeekForm>) => {
    setWeeks((prev) => prev.map((week, index) => (index === weekIndex ? { ...week, ...updates } : week)));
  };

  const updateDay = (weekIndex: number, dayIndex: number, updates: Partial<DayForm>) => {
    setWeeks((prev) =>
      prev.map((week, wIndex) => {
        if (wIndex !== weekIndex) return week;
        const days = week.days.map((day, dIndex) => (dIndex === dayIndex ? { ...day, ...updates } : day));
        return { ...week, days };
      })
    );
  };

  const updateSession = (
    weekIndex: number,
    dayIndex: number,
    sessionIndex: number,
    updates: Partial<SessionForm>
  ) => {
    setWeeks((prev) =>
      prev.map((week, wIndex) => {
        if (wIndex !== weekIndex) return week;
        const days = week.days.map((day, dIndex) => {
          if (dIndex !== dayIndex) return day;
          const sessions = day.sessions.map((session, sIndex) =>
            sIndex === sessionIndex ? { ...session, ...updates } : session
          );
          return { ...day, sessions };
        });
        return { ...week, days };
      })
    );
  };

  const updateItem = (
    weekIndex: number,
    dayIndex: number,
    sessionIndex: number,
    itemIndex: number,
    updates: Partial<SessionItemForm>
  ) => {
    setWeeks((prev) =>
      prev.map((week, wIndex) => {
        if (wIndex !== weekIndex) return week;
        const days = week.days.map((day, dIndex) => {
          if (dIndex !== dayIndex) return day;
          const sessions = day.sessions.map((session, sIndex) => {
            if (sIndex !== sessionIndex) return session;
            const items = session.items.map((item, iIndex) =>
              iIndex === itemIndex ? { ...item, ...updates } : item
            );
            return { ...session, items };
          });
          return { ...day, sessions };
        });
        return { ...week, days };
      })
    );
  };

  const addWeek = () => {
    const nextWeekNumber = weeks.length + 1;
    setWeeks((prev) => [...prev, createEmptyWeek(nextWeekNumber)].map(normalizeWeek));
    setDurationWeeks(nextWeekNumber);
  };

  const removeWeek = (index: number) => {
    setWeeks((prev) => prev.filter((_, wIndex) => wIndex !== index).map(normalizeWeek));
    setDurationWeeks(Math.max(1, weeks.length - 1));
  };

  const duplicateWeek = (weekIndex: number) => {
    setWeeks((prev) => {
      const next = [...prev];
      next.splice(weekIndex + 1, 0, cloneWeek(prev[weekIndex]));
      return next.map(normalizeWeek);
    });
    setDurationWeeks(weeks.length + 1);
  };

  const addDay = (weekIndex: number) => {
    setWeeks((prev) =>
      prev.map((week, wIndex) => {
        if (wIndex !== weekIndex) return week;
        const nextDayNumber = week.days.length + 1;
        return { ...week, days: [...week.days, createEmptyDay(nextDayNumber)].map(normalizeDay) };
      })
    );
  };

  const removeDay = (weekIndex: number, dayIndex: number) => {
    setWeeks((prev) =>
      prev.map((week, wIndex) => {
        if (wIndex !== weekIndex) return week;
        return { ...week, days: week.days.filter((_, dIndex) => dIndex !== dayIndex).map(normalizeDay) };
      })
    );
  };

  const duplicateDay = (weekIndex: number, dayIndex: number) => {
    setWeeks((prev) =>
      prev.map((week, wIndex) => {
        if (wIndex !== weekIndex) return week;
        const nextDays = [...week.days];
        nextDays.splice(dayIndex + 1, 0, cloneDay(week.days[dayIndex]));
        return { ...week, days: nextDays.map(normalizeDay) };
      })
    );
  };

  const addSession = (weekIndex: number, dayIndex: number) => {
    setWeeks((prev) =>
      prev.map((week, wIndex) => {
        if (wIndex !== weekIndex) return week;
        const days = week.days.map((day, dIndex) => {
          if (dIndex !== dayIndex) return day;
          return {
            ...day,
            sessions: [...day.sessions, createEmptySession(day.sessions.length)].map(normalizeSession),
          };
        });
        return { ...week, days };
      })
    );
  };

  const removeSession = (weekIndex: number, dayIndex: number, sessionIndex: number) => {
    setWeeks((prev) =>
      prev.map((week, wIndex) => {
        if (wIndex !== weekIndex) return week;
        const days = week.days.map((day, dIndex) => {
          if (dIndex !== dayIndex) return day;
          return { ...day, sessions: day.sessions.filter((_, sIndex) => sIndex !== sessionIndex).map(normalizeSession) };
        });
        return { ...week, days };
      })
    );
  };

  const duplicateSession = (weekIndex: number, dayIndex: number, sessionIndex: number) => {
    setWeeks((prev) =>
      prev.map((week, wIndex) => {
        if (wIndex !== weekIndex) return week;
        const days = week.days.map((day, dIndex) => {
          if (dIndex !== dayIndex) return day;
          const nextSessions = [...day.sessions];
          nextSessions.splice(sessionIndex + 1, 0, cloneSession(day.sessions[sessionIndex]));
          return { ...day, sessions: nextSessions.map(normalizeSession) };
        });
        return { ...week, days };
      })
    );
  };

  const addItem = (weekIndex: number, dayIndex: number, sessionIndex: number, type: 'exercise' | 'rest') => {
    const firstExerciseId = exercises[0]?.id;
    setWeeks((prev) =>
      prev.map((week, wIndex) => {
        if (wIndex !== weekIndex) return week;
        const days = week.days.map((day, dIndex) => {
          if (dIndex !== dayIndex) return day;
          const sessions = day.sessions.map((session, sIndex) => {
            if (sIndex !== sessionIndex) return session;
            return {
              ...session,
              items: [...session.items, createEmptyItem(type, type === 'exercise' ? firstExerciseId : undefined)],
            };
          });
          return { ...day, sessions };
        });
        return { ...week, days };
      })
    );
  };

  const removeItem = (weekIndex: number, dayIndex: number, sessionIndex: number, itemIndex: number) => {
    setWeeks((prev) =>
      prev.map((week, wIndex) => {
        if (wIndex !== weekIndex) return week;
        const days = week.days.map((day, dIndex) => {
          if (dIndex !== dayIndex) return day;
          const sessions = day.sessions.map((session, sIndex) => {
            if (sIndex !== sessionIndex) return session;
            return { ...session, items: session.items.filter((_, iIndex) => iIndex !== itemIndex) };
          });
          return { ...day, sessions };
        });
        return { ...week, days };
      })
    );
  };

  const importWorkout = async (weekIndex: number, dayIndex: number, sessionIndex: number, workoutId: string) => {
    try {
      const res = await fetch(`/api/workouts/${workoutId}`);
      const data = await res.json();
      if (!data.success || !data.data) return;
      const workout: WorkoutDetails = data.data;

      const items: SessionItemForm[] = [];
      workout.exercises.forEach((exercise) => {
        items.push({
          type: 'exercise',
          exerciseId: exercise.exerciseId,
          sets: exercise.sets,
          targetReps: exercise.targetReps ?? undefined,
          targetDuration: exercise.targetDuration ?? undefined,
          restBetweenSetsMs: exercise.restBetweenSetsMs,
          weightKg: exercise.weightKg ?? undefined,
          weightPerSetText: exercise.weightPerSet ? exercise.weightPerSet.join(', ') : '',
          notes: exercise.notes || { ar: '', en: '' },
          restDurationMs: undefined,
          role: '',
          intent: '',
          coachingNotesJson: '',
        });

        if (exercise.restAfterExerciseMs > 0) {
          items.push({
            type: 'rest',
            sets: 1,
            restBetweenSetsMs: 0,
            weightPerSetText: '',
            notes: { ar: '', en: '' },
            restDurationMs: exercise.restAfterExerciseMs,
            role: '',
            intent: '',
            coachingNotesJson: '',
          });
        }
      });

      setWeeks((prev) =>
        prev.map((week, wIndex) => {
          if (wIndex !== weekIndex) return week;
          const days = week.days.map((day, dIndex) => {
            if (dIndex !== dayIndex) return day;
            const sessions = day.sessions.map((session, sIndex) => {
              if (sIndex !== sessionIndex) return session;
              return {
                ...session,
                items: [...session.items, ...items],
              };
            });
            return { ...day, sessions };
          });
          return { ...week, days };
        })
      );
    } catch (error) {
      console.error('Error importing workout:', error);
    }
  };

  const parseJsonField = (value: string): object | undefined => {
    if (!value.trim()) return undefined;
    try {
      return JSON.parse(value);
    } catch {
      return undefined;
    }
  };

  const buildPayload = () => ({
    name,
    description: description.en || description.ar ? description : undefined,
    coverImageUrl: coverImageUrl || undefined,
    durationWeeks,
    tags: tags
      .split(',')
      .map((tag) => tag.trim())
      .filter(Boolean),
    programType: programOwnership,
    autoAssignable,
    version,
    coachingNotes: parseJsonField(coachingNotesProgram),
    weeklySessionTarget: weeklySessionTarget === '' ? undefined : weeklySessionTarget,
    estimatedSessionMinutes: estimatedSessionMinutes === '' ? undefined : estimatedSessionMinutes,
    levelRangeMin: trainingLevel,
    levelRangeMax: trainingLevel,
    prescriptionPriority,
    prerequisiteProgramId: prerequisiteProgramId || undefined,
    nextProgramId: nextProgramId || undefined,
    programAttributes: programAttributeRows,
    weeks: weeks.map((week, weekIndex) => ({
      weekNumber: week.weekNumber || weekIndex + 1,
      weekType: week.weekType,
      name: week.name.en || week.name.ar ? week.name : undefined,
      description: week.description.en || week.description.ar ? week.description : undefined,
      sortOrder: week.sortOrder ?? weekIndex,
      days: week.days.map((day, dayIndex) => ({
        dayNumber: day.dayNumber || dayIndex + 1,
        isRestDay: false,
        name: day.name.en || day.name.ar ? day.name : undefined,
        dayFocus: day.dayFocus?.trim() ? day.dayFocus : undefined,
        sessions: day.sessions.map((session, sessionIndex) => ({
          name: session.name,
          sortOrder: session.sortOrder ?? sessionIndex,
          items: session.items.map((item, itemIndex) => ({
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
            role: item.role || undefined,
            intent: item.intent || undefined,
            coachingNotes: item.coachingNotesJson?.trim()
              ? parseJsonField(item.coachingNotesJson)
              : undefined,
          })),
        })),
      })),
    })),
  });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);

    try {
      const res = await fetch('/api/programs', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(buildPayload()),
      });

      const data = await res.json();
      if (data.success) {
        toast.success('Program created successfully');
        router.push('/admin/programs');
      } else {
        toast.error(data.errors?.join('\n') || data.error || 'Failed to create program');
      }
    } catch (error) {
      console.error('Error creating program:', error);
      toast.error('Failed to create program');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-5xl mx-auto space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">New Program</h1>
          <p className="text-gray-600 mt-1">Create a structured training program</p>
        </div>
        <div className="flex items-center gap-3">
          <Link href="/admin/programs/map">
            <Button type="button" variant="outline">Open Map</Button>
          </Link>
          <Link href="/admin/programs">
            <Button type="button" variant="secondary">Back to Programs</Button>
          </Link>
        </div>
      </div>

      {prefillSummary.length > 0 ? (
        <Card className="border-blue-100 bg-blue-50/70 shadow-none">
          <div className="p-4">
            <p className="text-sm font-semibold text-blue-950">Context prefilled</p>
            <p className="mt-1 text-sm text-blue-900">
              This form was opened with preset values: {prefillSummary.join(' • ')}
            </p>
          </div>
        </Card>
      ) : null}

      <form onSubmit={handleSubmit} className="space-y-6">
        <Card className="sticky top-4 z-10 border-blue-100 bg-white/95 backdrop-blur p-4">
          <ProgramEditorTabBar
            active={editorTab}
            onChange={setEditorTab}
            tabs={PROGRAM_EDITOR_TABS}
            right={
              <span>
                {builderSummary.weeks}w · {builderSummary.days}d · {builderSummary.sessions} sess · {builderSummary.items}{' '}
                items
              </span>
            }
          />
        </Card>

        {editorTab === 'basics' && (
          <Card className="p-6 space-y-6">
            <div>
              <h2 className="text-lg font-semibold text-gray-900">Identity &amp; schedule</h2>
              <p className="mt-1 text-sm text-gray-500">
                Program names are required. Duration should match the number of week blocks in the Calendar tab.
              </p>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div>
                <Label>Name (English) *</Label>
                <Input
                  value={name.en}
                  onChange={(e) => setName({ ...name, en: e.target.value })}
                  placeholder="Enter program name"
                  required
                />
              </div>
              <div>
                <Label>Name (Arabic) *</Label>
                <Input
                  value={name.ar}
                  onChange={(e) => setName({ ...name, ar: e.target.value })}
                  placeholder="أدخل اسم البرنامج"
                  dir="rtl"
                  required
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div>
                <Label>Description (English)</Label>
                <Textarea
                  value={description.en}
                  onChange={(e) => setDescription({ ...description, en: e.target.value })}
                  placeholder="Enter description"
                  rows={2}
                />
              </div>
              <div>
                <Label>Description (Arabic)</Label>
                <Textarea
                  value={description.ar}
                  onChange={(e) => setDescription({ ...description, ar: e.target.value })}
                  placeholder="أدخل الوصف"
                  dir="rtl"
                  rows={2}
                />
              </div>
            </div>

            <div>
              <Label>Cover image URL</Label>
              <Input
                value={coverImageUrl}
                onChange={(e) => setCoverImageUrl(e.target.value)}
                placeholder="https://..."
              />
              <p className="mt-1 text-xs text-gray-500">Shown in catalog and mobile explore.</p>
            </div>

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div>
                <Label>Duration (weeks)</Label>
                <Input
                  type="number"
                  min={1}
                  value={durationWeeks}
                  onChange={(e) => setDurationWeeks(Number.parseInt(e.target.value, 10) || 1)}
                />
              </div>
              <div>
                <Label>Tags (comma separated)</Label>
                <Input
                  value={tags}
                  onChange={(e) => setTags(e.target.value)}
                  placeholder="strength, mobility"
                />
                <p className="mt-1 text-xs text-gray-500">Optional — internal search and filtering only.</p>
              </div>
            </div>

            {durationWeeks !== weeks.length ? (
              <p className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
                Duration is set to {durationWeeks} week(s), but the calendar builder currently has {weeks.length}{' '}
                week block(s). Align them before publishing.
              </p>
            ) : null}
          </Card>
        )}

        {editorTab === 'prescription' && (
          <Card className="p-6 space-y-4">
          <div>
          <h2 className="text-lg font-semibold text-gray-900">Prescription &amp; matching</h2>
          <p className="text-sm text-gray-600 mt-1">
            Program attributes drive auto-assignment and the prescription engine. Use the Advanced tab for schema version and structured coaching notes.
          </p>
          </div>

          {attributeCatalogError ? (
            <p className="text-sm text-red-600 mb-4">{attributeCatalogError}</p>
          ) : null}
          {loadingAttributeCatalog ? (
            <p className="text-sm text-gray-500 mb-4">Loading attribute catalog…</p>
          ) : null}

          <div className="grid grid-cols-3 gap-4">
            <div>
              <Label>Program ownership</Label>
              <Select
                value={programOwnership}
                onChange={(e) => setProgramOwnership(e.target.value as 'SYSTEM' | 'COACH' | 'CUSTOM')}
                options={[
                  { value: 'SYSTEM', label: 'System' },
                  { value: 'COACH', label: 'Coach' },
                  { value: 'CUSTOM', label: 'Custom' },
                ]}
              />
            </div>
            <div>
              <Label>Prescription Priority (1-100)</Label>
              <Input
                type="number"
                min={1}
                max={100}
                value={prescriptionPriority}
                onChange={(e) => setPrescriptionPriority(Number.parseInt(e.target.value, 10) || 1)}
              />
            </div>
            <div className="flex items-end pb-2">
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={autoAssignable}
                  onChange={(e) => setAutoAssignable(e.target.checked)}
                  className="h-4 w-4 rounded border-gray-300"
                />
                <span className="text-sm text-gray-700">Auto-assignable</span>
              </label>
            </div>
          </div>

          <div className="mt-4 rounded-lg border border-gray-200 p-4">
            <div className="flex items-center justify-between gap-4">
              <div>
                <p className="text-sm font-semibold text-gray-900">Auto-assignment readiness</p>
                <p className="text-sm text-gray-600">
                  {!autoAssignmentReadiness.entersAutoAssignment
                    ? 'This program stays manual-only until ownership settings allow auto-assignment.'
                    : autoAssignmentReadiness.ready
                      ? 'Ready for auto-assignment.'
                      : `Missing: ${autoAssignmentReadiness.missingFields.join(', ')}`}
                </p>
              </div>
              <span
                className={`inline-flex rounded-full px-3 py-1 text-xs font-medium ${
                  autoAssignmentReadiness.status === 'ready'
                    ? 'bg-green-100 text-green-800'
                    : autoAssignmentReadiness.status === 'incomplete'
                      ? 'bg-yellow-100 text-yellow-800'
                      : 'bg-gray-100 text-gray-700'
                }`}
              >
                {autoAssignmentReadiness.status === 'ready'
                  ? 'Ready'
                  : autoAssignmentReadiness.status === 'incomplete'
                    ? 'Incomplete'
                    : 'Manual only'}
              </span>
            </div>
          </div>

          <div className="mt-4 max-w-xs">
            <Label>Training level</Label>
            <Input
              type="number"
              min={1}
              value={trainingLevel}
              onChange={(e) => setTrainingLevel(Number.parseInt(e.target.value, 10) || 1)}
            />
            <p className="text-xs text-gray-500 mt-1">
              Single user level for matching (stored as min = max in the API).
            </p>
          </div>

          <div className="mt-6 border-t border-gray-200 pt-6">
            <h3 className="text-md font-semibold text-gray-900 mb-2">Program attributes</h3>
            <ProgramAttributesSection
              catalog={attributeCatalog}
              value={programAttributeRows}
              onChange={setProgramAttributeRows}
            />
          </div>

          <div className="grid grid-cols-2 gap-4 mt-4">
            <div>
              <Label>Weekly session target</Label>
              <Input
                type="number"
                min={1}
                value={weeklySessionTarget}
                onChange={(e) =>
                  setWeeklySessionTarget(e.target.value === '' ? '' : Number.parseInt(e.target.value, 10) || '')
                }
              />
            </div>
            <div>
              <Label>Estimated session minutes</Label>
              <Input
                type="number"
                min={1}
                value={estimatedSessionMinutes}
                onChange={(e) =>
                  setEstimatedSessionMinutes(e.target.value === '' ? '' : Number.parseInt(e.target.value, 10) || '')
                }
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4 mt-4">
            <div>
              <Label>Prerequisite Program</Label>
              <SearchableSelect
                value={prerequisiteProgramId}
                onChange={setPrerequisiteProgramId}
                options={publishedProgramOptions}
                placeholder="Select prerequisite"
                searchPlaceholder="Search programs..."
              />
            </div>
            <div>
              <Label>Next Program</Label>
              <SearchableSelect
                value={nextProgramId}
                onChange={setNextProgramId}
                options={publishedProgramOptions}
                placeholder="Select next program"
                searchPlaceholder="Search programs..."
              />
            </div>
          </div>
        </Card>
        )}

        {editorTab === 'advanced' && (
          <Card className="p-6 space-y-4">
            <div>
              <h2 className="text-lg font-semibold text-gray-900">Advanced</h2>
              <p className="text-sm text-gray-600 mt-1">
                Optional metadata. Bump version when you make meaningful template changes to a published program.
              </p>
            </div>
            <div className="max-w-xs">
              <Label>Schema / content version</Label>
              <Input
                type="number"
                min={1}
                value={version}
                onChange={(e) => setVersion(Number.parseInt(e.target.value, 10) || 1)}
              />
            </div>
            <div>
              <Label>Coaching notes (JSON)</Label>
              <Textarea
                value={coachingNotesProgram}
                onChange={(e) => setCoachingNotesProgram(e.target.value)}
                rows={4}
                placeholder='{}'
                className="font-mono text-sm"
              />
              <p className="mt-1 text-xs text-gray-500">Structured notes for coaches — omit if unused.</p>
            </div>
          </Card>
        )}

        {editorTab === 'builder' && (
        <>
          {/* Training Phases Section - Phase Builder (basic) */}
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <h2 className="text-lg font-semibold">Training Phases</h2>
                <p className="text-sm text-gray-500">Group weeks into named phases for easier pattern reuse and bulk edits. Phases auto-align program duration.</p>
              </div>
              <Button type="button" variant="outline" onClick={addPhase}>
                Add Phase
              </Button>
            </div>

            <div className="grid gap-4 md:grid-cols-2">
              {phases.map((phase, phaseIndex) => {
                const stats = getPhaseStats(phase);
                return (
                  <Card key={phase.id} className="p-4 space-y-4 border-blue-100">
                    <div className="flex items-center justify-between">
                      <div className="text-sm font-semibold text-gray-900">
                        Phase {phaseIndex + 1}
                      </div>
                      <div className="flex gap-2">
                        <Button type="button" variant="secondary" size="sm" onClick={() => applyPatternToPhase(phase.id)}>
                          Apply Pattern
                        </Button>
                        <Button type="button" variant="outline" size="sm" onClick={() => applyBulkWeekTypeToPhase(phase.id, 'DELOAD')}>
                          Set Deload
                        </Button>
                        <Button type="button" variant="outline" size="sm" onClick={() => applyBulkWeekTypeToPhase(phase.id, 'NORMAL')}>
                          Set Normal
                        </Button>
                        <Button type="button" variant="outline" size="sm" onClick={() => removePhase(phase.id)} disabled={phases.length === 1}>
                          Remove
                        </Button>
                      </div>
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                      <div>
                        <Label>Name (English)</Label>
                        <Input
                          value={phase.name.en}
                          onChange={(e) => updatePhase(phase.id, { name: { ...phase.name, en: e.target.value } })}
                        />
                      </div>
                      <div>
                        <Label>Name (Arabic)</Label>
                        <Input
                          value={phase.name.ar}
                          onChange={(e) => updatePhase(phase.id, { name: { ...phase.name, ar: e.target.value } })}
                          dir="rtl"
                        />
                      </div>
                      <div>
                        <Label>Start Week</Label>
                        <Input
                          type="number"
                          min={1}
                          value={phase.startWeek}
                          onChange={(e) => updatePhase(phase.id, { startWeek: Number.parseInt(e.target.value, 10) || 1 })}
                        />
                      </div>
                      <div>
                        <Label>End Week</Label>
                        <Input
                          type="number"
                          min={phase.startWeek}
                          value={phase.endWeek}
                          onChange={(e) => updatePhase(phase.id, { endWeek: Number.parseInt(e.target.value, 10) || phase.startWeek })}
                        />
                      </div>
                    </div>

                    <div className="rounded bg-gray-50 px-3 py-2 text-xs text-gray-600 flex gap-4">
                      <span>{stats.weeks} weeks</span>
                      <span>{stats.days} training days</span>
                      <span>{stats.sessions} sessions</span>
                      <span>{stats.items} items</span>
                    </div>
                  </Card>
                );
              })}
            </div>
          </div>

          {/* Existing Program Builder header */}
          <div className="flex items-center justify-between pt-4 border-t">
            <div>
              <h2 className="text-lg font-semibold">Program Builder</h2>
              <p className="text-sm text-gray-500">Build weeks, days, sessions, and items. Weeks inherit phase grouping above.</p>
            </div>
            <Button type="button" variant="outline" onClick={addWeek}>
              Add Week
            </Button>
          </div>

          <details className="rounded-lg border border-blue-100 bg-blue-50/70 text-sm text-blue-900">
            <summary className="cursor-pointer px-4 py-3 font-semibold text-blue-950">Calendar builder tips</summary>
            <ul className="list-disc space-y-1 px-4 pb-3 pl-8">
              <li>Define phases first (auto-aligns Duration). Use Apply Pattern to repeat the first week of a phase across its weeks.</li>
              <li>Use Bulk (Deload/Normal) buttons per phase for quick changes. Fine-tune individual weeks below.</li>
              <li>Phases must not overlap and must cover 1..durationWeeks.</li>
            </ul>
          </details>

          {calendarStructureWarnings.length > 0 ? (
            <div className="space-y-2 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900">
              <p className="font-medium">Calendar structure (hints — save is not blocked)</p>
              <ul className="list-disc space-y-1 pl-5">
                {calendarStructureWarnings.map((msg) => (
                  <li key={msg}>{msg}</li>
                ))}
              </ul>
            </div>
          ) : null}

          {weeks.map((week, weekIndex) => (
            <CollapsibleBuilderSection
              key={`week-${weekIndex}`}
              title={`Week ${weekIndex + 1}`}
              subtitle={getWeekSummary(week)}
              defaultOpen={weekIndex === 0}
              meta={[
                { label: week.weekType === 'DELOAD' ? 'Deload' : 'Normal', variant: week.weekType === 'DELOAD' ? 'warning' : 'default' },
                { label: `${week.days.length} day(s)` },
                ...(phases.find((p) => week.weekNumber >= p.startWeek && week.weekNumber <= p.endWeek)
                  ? [{ label: phases.find((p) => week.weekNumber >= p.startWeek && week.weekNumber <= p.endWeek)!.name.en || 'Phase', variant: 'primary' as const }]
                  : []),
              ]}
              actions={
                <div className="flex items-center gap-2">
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => removeWeek(weekIndex)}
                    disabled={weeks.length === 1}
                  >
                    Remove Week
                  </Button>
                  <Button
                    type="button"
                    variant="secondary"
                    onClick={() => duplicateWeek(weekIndex)}
                  >
                    Duplicate Week
                  </Button>
                </div>
              }
            >
              <div className="space-y-4">
              <div className="grid grid-cols-4 gap-4">
                <div>
                  <Label>Week Number</Label>
                  <Input
                    type="number"
                    min={1}
                    value={week.weekNumber}
                    onChange={(e) => updateWeek(weekIndex, { weekNumber: Number.parseInt(e.target.value, 10) || 1 })}
                  />
                </div>
                <div>
                  <Label>Week type</Label>
                  <Select
                    value={week.weekType}
                    onChange={(e) =>
                      updateWeek(weekIndex, { weekType: e.target.value as 'NORMAL' | 'DELOAD' })
                    }
                    options={[
                      { value: 'NORMAL', label: 'Normal' },
                      { value: 'DELOAD', label: 'Deload' },
                    ]}
                  />
                </div>
                <div>
                  <Label>Week Name (EN)</Label>
                  <Input
                    value={week.name.en}
                    onChange={(e) => updateWeek(weekIndex, { name: { ...week.name, en: e.target.value } })}
                  />
                </div>
                <div>
                  <Label>Week Name (AR)</Label>
                  <Input
                    dir="rtl"
                    value={week.name.ar}
                    onChange={(e) => updateWeek(weekIndex, { name: { ...week.name, ar: e.target.value } })}
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <Label>Description (EN)</Label>
                  <Textarea
                    rows={2}
                    value={week.description.en}
                    onChange={(e) => updateWeek(weekIndex, { description: { ...week.description, en: e.target.value } })}
                  />
                </div>
                <div>
                  <Label>Description (AR)</Label>
                  <Textarea
                    rows={2}
                    dir="rtl"
                    value={week.description.ar}
                    onChange={(e) => updateWeek(weekIndex, { description: { ...week.description, ar: e.target.value } })}
                  />
                </div>
              </div>

              <div className="flex items-center justify-between">
                <h4 className="text-sm font-semibold text-gray-700">
                  Training days
                  <span className="ml-2 rounded-full bg-primary/10 px-2 py-0.5 text-xs text-primary">
                    {week.days.length}/week
                  </span>
                </h4>
                <Button type="button" variant="outline" onClick={() => addDay(weekIndex)}>
                  Add Day
                </Button>
              </div>

              {week.days.map((day, dayIndex) => (
                <CollapsibleBuilderSection
                  key={`day-${dayIndex}`}
                  title={`Training day ${dayIndex + 1}`}
                  subtitle={getDaySummary(day)}
                  defaultOpen={weekIndex === 0 && dayIndex === 0}
                  meta={[
                    { label: 'Training', variant: 'primary' },
                    { label: `${day.sessions.length} session(s)` },
                  ]}
                  actions={
                    <div className="flex items-center gap-2">
                      <Button
                        type="button"
                        variant="outline"
                        onClick={() => removeDay(weekIndex, dayIndex)}
                        disabled={week.days.length === 1}
                      >
                        Remove Day
                      </Button>
                      <Button
                        type="button"
                        variant="secondary"
                        onClick={() => duplicateDay(weekIndex, dayIndex)}
                      >
                        Duplicate Day
                      </Button>
                    </div>
                  }
                  className="border-gray-100"
                >
                  <div className="space-y-4">
                  <div className="grid grid-cols-4 gap-4">
                    <div>
                      <Label>Day Number</Label>
                      <Input
                        type="number"
                        min={1}
                        max={14}
                        value={day.dayNumber}
                        onChange={(e) =>
                          updateDay(weekIndex, dayIndex, { dayNumber: Number.parseInt(e.target.value, 10) || 1 })
                        }
                      />
                    </div>
                    <div>
                      <Label>Day focus</Label>
                      <Input
                        value={day.dayFocus ?? ''}
                        onChange={(e) => updateDay(weekIndex, dayIndex, { dayFocus: e.target.value })}
                        placeholder="e.g. lower body"
                      />
                    </div>
                    <div>
                      <Label>Day Name (EN)</Label>
                      <Input
                        value={day.name.en}
                        onChange={(e) => updateDay(weekIndex, dayIndex, { name: { ...day.name, en: e.target.value } })}
                      />
                    </div>
                    <div>
                      <Label>Day Name (AR)</Label>
                      <Input
                        dir="rtl"
                        value={day.name.ar}
                        onChange={(e) => updateDay(weekIndex, dayIndex, { name: { ...day.name, ar: e.target.value } })}
                      />
                    </div>
                  </div>

                  <div className="flex items-center justify-between">
                    <h6 className="text-sm font-semibold text-gray-600">Sessions</h6>
                    <Button type="button" variant="outline" onClick={() => addSession(weekIndex, dayIndex)}>
                      Add Session
                    </Button>
                  </div>

                  {day.sessions.map((session, sessionIndex) => (
                    <CollapsibleBuilderSection
                      key={`session-${sessionIndex}`}
                      title={`Session ${sessionIndex + 1}`}
                      subtitle={getSessionSummary(session)}
                      defaultOpen={weekIndex === 0 && dayIndex === 0 && sessionIndex === 0}
                      meta={[
                        { label: `${session.items.length} item(s)` },
                      ]}
                      actions={
                        <div className="flex items-center gap-2">
                          <Button
                            type="button"
                            variant="outline"
                            onClick={() => removeSession(weekIndex, dayIndex, sessionIndex)}
                            disabled={day.sessions.length === 1}
                          >
                            Remove Session
                          </Button>
                          <Button
                            type="button"
                            variant="secondary"
                            onClick={() => duplicateSession(weekIndex, dayIndex, sessionIndex)}
                          >
                            Duplicate Session
                          </Button>
                        </div>
                      }
                      className="border-gray-100"
                    >
                      <div className="space-y-4">
                      <div className="grid grid-cols-2 gap-4">
                        <div>
                          <Label>Session Name (EN)</Label>
                          <Input
                            value={session.name.en}
                            onChange={(e) =>
                              updateSession(weekIndex, dayIndex, sessionIndex, {
                                name: { ...session.name, en: e.target.value },
                              })
                            }
                          />
                        </div>
                        <div>
                          <Label>Session Name (AR)</Label>
                          <Input
                            dir="rtl"
                            value={session.name.ar}
                            onChange={(e) =>
                              updateSession(weekIndex, dayIndex, sessionIndex, {
                                name: { ...session.name, ar: e.target.value },
                              })
                            }
                          />
                        </div>
                      </div>

                      <div className="flex items-center justify-between">
                        <div>
                          <h6 className="text-sm font-semibold text-gray-600">Session Items</h6>
                          <p className="text-xs text-gray-500">Add exercises and rest periods</p>
                        </div>
                        <div className="flex items-center gap-2">
                          <SearchableSelect
                            value=""
                            onChange={(value) => {
                              if (value) {
                                importWorkout(weekIndex, dayIndex, sessionIndex, value);
                              }
                            }}
                            options={workoutOptions}
                            placeholder={loadingWorkouts ? 'Loading workouts...' : 'Import workout'}
                            searchPlaceholder="Search workouts..."
                          />
                          <Button
                            type="button"
                            variant="outline"
                            onClick={() => addItem(weekIndex, dayIndex, sessionIndex, 'exercise')}
                            disabled={loadingExercises || exercises.length === 0}
                          >
                            Add Exercise
                          </Button>
                          <Button
                            type="button"
                            variant="outline"
                            onClick={() => addItem(weekIndex, dayIndex, sessionIndex, 'rest')}
                          >
                            Add Rest
                          </Button>
                        </div>
                      </div>

                      {session.items.map((item, itemIndex) => (
                        <CollapsibleBuilderSection
                          key={`item-${itemIndex}`}
                          title={`${item.type === 'exercise' ? 'Exercise' : 'Rest'} ${itemIndex + 1}`}
                          subtitle={getItemSummary(item)}
                          defaultOpen={session.items.length <= 2}
                          meta={[
                            {
                              label: item.type === 'exercise' ? 'Exercise' : 'Rest',
                              variant: item.type === 'exercise' ? 'success' : 'warning',
                            },
                          ]}
                          actions={
                            <Button
                              type="button"
                              variant="outline"
                              onClick={() => removeItem(weekIndex, dayIndex, sessionIndex, itemIndex)}
                            >
                              Remove
                            </Button>
                          }
                          className="border-gray-200"
                        >
                          <div className="space-y-3">
                          <div className="grid grid-cols-2 gap-4">
                            <div>
                              <Label>Type</Label>
                              <Select
                                value={item.type}
                                onChange={(e) =>
                                  updateItem(weekIndex, dayIndex, sessionIndex, itemIndex, {
                                    type: e.target.value as 'exercise' | 'rest',
                                  })
                                }
                                options={[
                                  { value: 'exercise', label: 'Exercise' },
                                  { value: 'rest', label: 'Rest' },
                                ]}
                              />
                            </div>
                            {item.type === 'exercise' ? (
                              <div className="space-y-2">
                                <Label>Exercise</Label>
                                <SearchableSelect
                                  value={item.exerciseId || ''}
                                  onChange={(value) =>
                                    updateItem(weekIndex, dayIndex, sessionIndex, itemIndex, {
                                      exerciseId: value,
                                    })
                                  }
                                  options={exerciseOptions}
                                  placeholder="Select exercise"
                                  searchPlaceholder="Search exercises..."
                                />
                                {(() => {
                                  const check = exerciseAttributeCheck(item.exerciseId);
                                  if (!check || check.status === 'ok') return null;
                                  return (
                                    <div
                                      className={`text-xs rounded px-2 py-1.5 ${
                                        check.status === 'red'
                                          ? 'bg-red-50 text-red-900 border border-red-200'
                                          : 'bg-amber-50 text-amber-950 border border-amber-200'
                                      }`}
                                    >
                                      {check.messages.join(' · ')}
                                    </div>
                                  );
                                })()}
                              </div>
                            ) : (
                              <div>
                                <Label>Rest Duration (ms)</Label>
                                <Input
                                  type="number"
                                  min={0}
                                  value={item.restDurationMs || 0}
                                  onChange={(e) =>
                                    updateItem(weekIndex, dayIndex, sessionIndex, itemIndex, {
                                      restDurationMs: Number.parseInt(e.target.value, 10) || 0,
                                    })
                                  }
                                />
                              </div>
                            )}
                          </div>

                          {item.type === 'exercise' && (
                            <>
                              <div className="grid grid-cols-4 gap-4">
                                <div>
                                  <Label>Sets</Label>
                                  <Input
                                    type="number"
                                    min={1}
                                    value={item.sets}
                                    onChange={(e) =>
                                      updateItem(weekIndex, dayIndex, sessionIndex, itemIndex, {
                                        sets: Number.parseInt(e.target.value, 10) || 1,
                                      })
                                    }
                                  />
                                </div>
                                <div>
                                  <Label>Target Reps</Label>
                                  <Input
                                    type="number"
                                    min={1}
                                    value={item.targetReps || ''}
                                    onChange={(e) =>
                                      updateItem(weekIndex, dayIndex, sessionIndex, itemIndex, {
                                        targetReps: Number.parseInt(e.target.value, 10) || undefined,
                                      })
                                    }
                                  />
                                </div>
                                <div>
                                  <Label>Target Duration (sec)</Label>
                                  <Input
                                    type="number"
                                    min={1}
                                    value={item.targetDuration || ''}
                                    onChange={(e) =>
                                      updateItem(weekIndex, dayIndex, sessionIndex, itemIndex, {
                                        targetDuration: Number.parseInt(e.target.value, 10) || undefined,
                                      })
                                    }
                                  />
                                </div>
                                <div>
                                  <Label>Rest Between Sets (ms)</Label>
                                  <Input
                                    type="number"
                                    min={0}
                                    value={item.restBetweenSetsMs}
                                    onChange={(e) =>
                                      updateItem(weekIndex, dayIndex, sessionIndex, itemIndex, {
                                        restBetweenSetsMs: Number.parseInt(e.target.value, 10) || 0,
                                      })
                                    }
                                  />
                                </div>
                              </div>

                              <div className="grid grid-cols-3 gap-4">
                                <div>
                                  <Label>Weight (kg)</Label>
                                  <Input
                                    type="number"
                                    min={0}
                                    value={item.weightKg || ''}
                                    onChange={(e) =>
                                      updateItem(weekIndex, dayIndex, sessionIndex, itemIndex, {
                                        weightKg: Number.parseFloat(e.target.value) || undefined,
                                      })
                                    }
                                  />
                                </div>
                                <div>
                                  <Label>Weight Per Set</Label>
                                  <Input
                                    value={item.weightPerSetText}
                                    onChange={(e) =>
                                      updateItem(weekIndex, dayIndex, sessionIndex, itemIndex, {
                                        weightPerSetText: e.target.value,
                                      })
                                    }
                                    placeholder="10, 12.5, 15"
                                  />
                                </div>
                              </div>

                              <details className="rounded-lg border border-gray-200 bg-gray-50">
                                <summary className="cursor-pointer px-3 py-2 text-sm font-medium text-gray-700">
                                  Advanced item settings
                                </summary>
                                <div className="space-y-4 border-t border-gray-200 p-3">
                                  <div className="grid grid-cols-2 gap-4">
                                    <div>
                                      <Label>Notes (EN)</Label>
                                      <Input
                                        value={item.notes.en}
                                        onChange={(e) =>
                                          updateItem(weekIndex, dayIndex, sessionIndex, itemIndex, {
                                            notes: { ...item.notes, en: e.target.value },
                                          })
                                        }
                                      />
                                    </div>
                                    <div>
                                      <Label>Notes (AR)</Label>
                                      <Input
                                        dir="rtl"
                                        value={item.notes.ar}
                                        onChange={(e) =>
                                          updateItem(weekIndex, dayIndex, sessionIndex, itemIndex, {
                                            notes: { ...item.notes, ar: e.target.value },
                                          })
                                        }
                                      />
                                    </div>
                                  </div>

                                  <div className="grid grid-cols-3 gap-4">
                                    <div>
                                      <Label>Role</Label>
                                      <Select
                                        value={item.role ?? ''}
                                        onChange={(e) =>
                                          updateItem(weekIndex, dayIndex, sessionIndex, itemIndex, {
                                            role: e.target.value,
                                          })
                                        }
                                        options={SESSION_ITEM_ROLE_OPTIONS}
                                      />
                                    </div>
                                    <div>
                                      <Label>Intent</Label>
                                      <Select
                                        value={item.intent ?? ''}
                                        onChange={(e) =>
                                          updateItem(weekIndex, dayIndex, sessionIndex, itemIndex, {
                                            intent: e.target.value,
                                          })
                                        }
                                        options={SESSION_ITEM_INTENT_OPTIONS}
                                      />
                                    </div>
                                    <div>
                                      <Label>Coaching notes (JSON)</Label>
                                      <Textarea
                                        rows={2}
                                        value={item.coachingNotesJson ?? ''}
                                        onChange={(e) =>
                                          updateItem(weekIndex, dayIndex, sessionIndex, itemIndex, {
                                            coachingNotesJson: e.target.value,
                                          })
                                        }
                                        placeholder="{}"
                                      />
                                    </div>
                                  </div>
                                </div>
                              </details>
                            </>
                          )}
                          </div>
                        </CollapsibleBuilderSection>
                      ))}
                      </div>
                    </CollapsibleBuilderSection>
                  ))}
                  </div>
                </CollapsibleBuilderSection>
              ))}
              </div>
            </CollapsibleBuilderSection>
          ))}
        </>
        )}

        <div className="sticky bottom-4 z-10 flex justify-end gap-4 rounded-2xl border border-gray-200 bg-white/95 p-4 shadow-sm backdrop-blur">
          <Button type="button" variant="outline" onClick={() => router.push('/admin/programs')}>
            Cancel
          </Button>
          <Button type="submit" disabled={loading}>
            {loading ? 'Creating...' : 'Create Program'}
          </Button>
        </div>
      </form>
    </div>
  );
}
