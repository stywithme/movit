'use client';

import { useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { toast } from 'react-hot-toast';
import { useParams, useRouter } from 'next/navigation';
import { Input, Select, Label, Button, Card, Textarea, SearchableSelect } from '@/components/ui';
import type { LocalizedText } from '@/lib/types/localized';
import { CollapsibleBuilderSection } from '../../_components/CollapsibleBuilderSection';
import { RecommendationEditor } from '../../_components/RecommendationEditor';
import { getAutoAssignmentReadiness } from '../../_lib/auto-assignment';

interface ProgramSummaryRef {
  id: string;
  name: LocalizedText;
}

const TARGET_REGION_OPTIONS = ['shoulder', 'hip', 'spine', 'knee', 'core', 'balance'] as const;

const TARGET_EQUIPMENT_OPTIONS = [
  'barbell',
  'dumbbell',
  'cable',
  'machine',
  'bodyweight',
  'kettlebell',
  'bands',
] as const;

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

function legacyTypeToDomain(t?: string): 'TRAINING' | 'MOBILITY' | 'THERAPEUTIC' {
  if (t === 'mobility') return 'MOBILITY';
  if (t === 'therapeutic') return 'THERAPEUTIC';
  return 'TRAINING';
}

interface ExerciseSummary {
  id: string;
  name: LocalizedText;
  countingMethod?: {
    code: string;
  };
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
  role?: string;
  intent?: string;
  coachingNotesJson?: string;
}

interface SessionForm {
  id?: string;
  name: LocalizedText;
  sortOrder: number;
  items: SessionItemForm[];
}

interface DayForm {
  id?: string;
  dayNumber: number;
  isRestDay: boolean;
  name: LocalizedText;
  dayFocus?: string;
  sessions: SessionForm[];
}

interface WeekForm {
  id?: string;
  weekNumber: number;
  name: LocalizedText;
  description: LocalizedText;
  sortOrder: number;
  weekType: 'NORMAL' | 'DELOAD';
  days: DayForm[];
}

interface ProgramResponse {
  id: string;
  name: LocalizedText;
  description: LocalizedText | null;
  coverImageUrl: string | null;
  durationWeeks: number;
  version?: number | null;
  difficulty: 'beginner' | 'intermediate' | 'advanced';
  tags: string[] | null;
  type?: string;
  programType?: string;
  programDomain?: string;
  trainingGoal?: string | null;
  autoAssignable?: boolean;
  coachingNotes?: object | null;
  targetEquipment?: unknown;
  weeklySessionTarget?: number | null;
  estimatedSessionMinutes?: number | null;
  targetDomain?: string | null;
  targetRegions?: string[] | null;
  levelRangeMin?: number;
  levelRangeMax?: number;
  entryCriteria?: object | null;
  exitCriteria?: object | null;
  entryRecommendations?: object | null;
  exitRecommendations?: object | null;
  contraindications?: string[] | null;
  prescriptionPriority?: number;
  prerequisiteProgramId?: string | null;
  nextProgramId?: string | null;
  isPublished?: boolean;
  activeEnrollmentCount?: number;
  weeks: Array<{
    id?: string;
    weekNumber: number;
    weekType?: string;
    name?: LocalizedText;
    description?: LocalizedText;
    sortOrder: number;
    days: Array<{
      id?: string;
      dayNumber: number;
      isRestDay: boolean;
      dayFocus?: string | null;
      name?: LocalizedText;
      sessions: Array<{
        id?: string;
        name: LocalizedText;
        sortOrder: number;
        items: Array<{
          id?: string;
          type: 'exercise' | 'rest';
          exerciseId?: string;
          sets?: number;
          targetReps?: number;
          targetDuration?: number;
          restBetweenSetsMs?: number;
          weightKg?: number;
          weightPerSet?: number[];
          notes?: LocalizedText;
          restDurationMs?: number;
          sortOrder: number;
          role?: string | null;
          intent?: string | null;
          coachingNotes?: unknown;
        }>;
      }>;
    }>;
  }>;
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

const PROGRAM_EDITOR_SECTIONS = [
  { id: 'basic-information', label: 'Basic Info' },
  { id: 'program-configuration', label: 'Configuration' },
  { id: 'prescription-settings', label: 'Prescription' },
  { id: 'program-builder', label: 'Builder' },
] as const;

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

export default function EditProgramPage() {
  const router = useRouter();
  const params = useParams();
  const programId = params.id as string;

  const [loading, setLoading] = useState(false);
  const [loadingProgram, setLoadingProgram] = useState(true);
  const [loadingExercises, setLoadingExercises] = useState(true);
  const [loadingWorkouts, setLoadingWorkouts] = useState(true);

  const [name, setName] = useState({ ar: '', en: '' });
  const [description, setDescription] = useState({ ar: '', en: '' });
  const [coverImageUrl, setCoverImageUrl] = useState('');
  const [durationWeeks, setDurationWeeks] = useState(4);
  const [version, setVersion] = useState(1);
  const [difficulty, setDifficulty] = useState<'beginner' | 'intermediate' | 'advanced'>('beginner');
  const [tags, setTags] = useState('');
  const [weeks, setWeeks] = useState<WeekForm[]>([createEmptyWeek(1)]);

  const [isPublished, setIsPublished] = useState(false);
  const [activeEnrollmentCount, setActiveEnrollmentCount] = useState(0);
  const [programDomainEnum, setProgramDomainEnum] = useState<'TRAINING' | 'MOBILITY' | 'THERAPEUTIC'>('TRAINING');
  const [trainingGoal, setTrainingGoal] = useState('');
  const [autoAssignable, setAutoAssignable] = useState(false);
  const [targetEquipment, setTargetEquipment] = useState<string[]>([]);
  const [coachingNotesProgram, setCoachingNotesProgram] = useState('');
  const [weeklySessionTarget, setWeeklySessionTarget] = useState<number | ''>('');
  const [estimatedSessionMinutes, setEstimatedSessionMinutes] = useState<number | ''>('');
  const [targetDomain, setTargetDomain] = useState('none');
  const [targetRegions, setTargetRegions] = useState<string[]>([]);
  const [levelRangeMin, setLevelRangeMin] = useState(1);
  const [levelRangeMax, setLevelRangeMax] = useState(10);
  const [entryRecommendations, setEntryRecommendations] = useState('');
  const [exitRecommendations, setExitRecommendations] = useState('');
  const [contraindications, setContraindications] = useState<string[]>([]);
  const [prescriptionPriority, setPrescriptionPriority] = useState(50);
  const [prerequisiteProgramId, setPrerequisiteProgramId] = useState('');
  const [nextProgramId, setNextProgramId] = useState('');

  const [exercises, setExercises] = useState<ExerciseSummary[]>([]);
  const [workouts, setWorkouts] = useState<WorkoutSummary[]>([]);
  const [publishedPrograms, setPublishedPrograms] = useState<ProgramSummaryRef[]>([]);

  useEffect(() => {
    const fetchExercises = async () => {
      try {
        const res = await fetch('/api/exercises?status=published&limit=200');
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

  useEffect(() => {
    const fetchProgram = async () => {
      try {
        const res = await fetch(`/api/programs/${programId}`);
        const data = await res.json();
        if (!data.success || !data.data) {
          alert('Program not found');
          router.push('/admin/programs');
          return;
        }

        const program: ProgramResponse = data.data;
        setIsPublished(program.isPublished ?? false);
        setActiveEnrollmentCount(program.activeEnrollmentCount ?? 0);
        setName(program.name);
        setDescription(program.description || { ar: '', en: '' });
        setCoverImageUrl(program.coverImageUrl || '');
        setDurationWeeks(program.durationWeeks);
        setVersion(program.version ?? 1);
        setDifficulty(program.difficulty);
        setTags((program.tags || []).join(', '));

        setProgramOwnership(
          (program.programType as 'SYSTEM' | 'COACH' | 'CUSTOM' | undefined) || 'SYSTEM'
        );
        setProgramDomainEnum(
          (program.programDomain as 'TRAINING' | 'MOBILITY' | 'THERAPEUTIC' | undefined) ||
            legacyTypeToDomain(program.type)
        );
        setTrainingGoal(program.trainingGoal || '');
        setAutoAssignable(program.autoAssignable ?? false);
        setTargetEquipment(
          Array.isArray(program.targetEquipment)
            ? (program.targetEquipment as string[])
            : []
        );
        setCoachingNotesProgram(
          program.coachingNotes ? JSON.stringify(program.coachingNotes, null, 2) : ''
        );
        setWeeklySessionTarget(program.weeklySessionTarget ?? '');
        setEstimatedSessionMinutes(program.estimatedSessionMinutes ?? '');
        setTargetDomain(program.targetDomain || 'none');
        setTargetRegions(program.targetRegions || []);
        setLevelRangeMin(program.levelRangeMin ?? 1);
        setLevelRangeMax(program.levelRangeMax ?? 10);
        const entryRec = program.entryRecommendations ?? program.entryCriteria;
        const exitRec = program.exitRecommendations ?? program.exitCriteria;
        setEntryRecommendations(entryRec ? JSON.stringify(entryRec, null, 2) : '');
        setExitRecommendations(exitRec ? JSON.stringify(exitRec, null, 2) : '');
        setContraindications(program.contraindications || []);
        setPrescriptionPriority(program.prescriptionPriority ?? 50);
        setPrerequisiteProgramId(program.prerequisiteProgramId || '');
        setNextProgramId(program.nextProgramId || '');

        const mappedWeeks: WeekForm[] =
          program.weeks?.map((week, weekIndex) => ({
            id: week.id,
            weekNumber: week.weekNumber || weekIndex + 1,
            weekType: (week.weekType as 'NORMAL' | 'DELOAD' | undefined) || 'NORMAL',
            name: week.name || { ar: '', en: '' },
            description: week.description || { ar: '', en: '' },
            sortOrder: week.sortOrder ?? weekIndex,
            days:
              week.days?.map((day, dayIndex) => ({
                id: day.id,
                dayNumber: day.dayNumber || dayIndex + 1,
                isRestDay: day.isRestDay || false,
                name: day.name || { ar: '', en: '' },
                dayFocus: day.dayFocus ?? '',
                sessions:
                  day.sessions?.map((session, sessionIndex) => ({
                    id: session.id,
                    name: session.name || { ar: '', en: '' },
                    sortOrder: session.sortOrder ?? sessionIndex,
                    items:
                      session.items?.map((item) => ({
                        id: item.id,
                        type: item.type,
                        exerciseId: item.exerciseId,
                        sets: item.sets || 1,
                        targetReps: item.targetReps ?? undefined,
                        targetDuration: item.targetDuration ?? undefined,
                        restBetweenSetsMs: item.restBetweenSetsMs ?? 30000,
                        weightKg: item.weightKg ?? undefined,
                        weightPerSetText: item.weightPerSet ? item.weightPerSet.join(', ') : '',
                        notes: item.notes || { ar: '', en: '' },
                        restDurationMs: item.restDurationMs ?? undefined,
                        role: item.role ?? '',
                        intent: item.intent ?? '',
                        coachingNotesJson: item.coachingNotes
                          ? JSON.stringify(item.coachingNotes, null, 2)
                          : '',
                      })) || [],
                  })) || [],
              })) || [],
          })) || [];

        setWeeks(mappedWeeks.length > 0 ? mappedWeeks : [createEmptyWeek(1)]);
      } catch (error) {
        console.error('Error fetching program:', error);
        alert('Error loading program');
        router.push('/admin/programs');
      } finally {
        setLoadingProgram(false);
      }
    };

    fetchProgram();
  }, [programId, router]);

  const calendarStructureWarnings = useMemo(() => {
    const messages: string[] = [];
    if (durationWeeks !== weeks.length) {
      messages.push(
        `Duration is set to ${durationWeeks} week(s), but the builder currently contains ${weeks.length} week block(s).`
      );
    }
    weeks.forEach((week, wi) => {
      if (week.days.length !== 7) {
        messages.push(
          `Week ${wi + 1}: publish-ready programs use 7 days per week; this week has ${week.days.length} day(s).`
        );
      }
      const badDayNumber = week.days.some((d) => d.dayNumber < 1 || d.dayNumber > 7);
      if (badDayNumber) {
        messages.push(`Week ${wi + 1}: day numbers should be between 1 and 7 for calendar alignment.`);
      }
    });
    return messages;
  }, [durationWeeks, weeks]);

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

  const autoAssignmentReadiness = useMemo(
    () =>
      getAutoAssignmentReadiness({
        programType: programOwnership,
        programDomain: programDomainEnum,
        trainingGoal: programDomainEnum === 'TRAINING' ? trainingGoal || null : null,
        autoAssignable,
        levelRangeMin,
        levelRangeMax,
        contraindications,
        targetEquipment,
        targetDomain: targetDomain !== 'none' ? targetDomain : null,
        targetRegions,
        prescriptionPriority,
      }),
    [
      autoAssignable,
      contraindications,
      levelRangeMax,
      levelRangeMin,
      prescriptionPriority,
      programDomainEnum,
      programOwnership,
      targetDomain,
      targetEquipment,
      targetRegions,
      trainingGoal,
    ]
  );

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
    return `${day.isRestDay ? 'Rest day' : 'Training day'} • ${day.sessions.length} session(s) • ${items} item(s)`;
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
    difficulty,
    tags: tags
      .split(',')
      .map((tag) => tag.trim())
      .filter(Boolean),
    programType: programOwnership,
    programDomain: programDomainEnum,
    trainingGoal: programDomainEnum === 'TRAINING' ? trainingGoal || undefined : undefined,
    autoAssignable,
    version,
    coachingNotes: parseJsonField(coachingNotesProgram),
    weeklySessionTarget: weeklySessionTarget === '' ? undefined : weeklySessionTarget,
    estimatedSessionMinutes: estimatedSessionMinutes === '' ? undefined : estimatedSessionMinutes,
    targetEquipment,
    targetDomain: targetDomain !== 'none' ? targetDomain : undefined,
    targetRegions,
    levelRangeMin,
    levelRangeMax,
    entryRecommendations: parseJsonField(entryRecommendations),
    exitRecommendations: parseJsonField(exitRecommendations),
    contraindications,
    prescriptionPriority,
    prerequisiteProgramId: prerequisiteProgramId || undefined,
    nextProgramId: nextProgramId || undefined,
    weeks: weeks.map((week, weekIndex) => ({
      ...(week.id ? { id: week.id } : {}),
      weekNumber: week.weekNumber || weekIndex + 1,
      weekType: week.weekType,
      name: week.name.en || week.name.ar ? week.name : undefined,
      description: week.description.en || week.description.ar ? week.description : undefined,
      sortOrder: week.sortOrder ?? weekIndex,
      days: week.days.map((day, dayIndex) => ({
        ...(day.id ? { id: day.id } : {}),
        dayNumber: day.dayNumber || dayIndex + 1,
        isRestDay: day.isRestDay,
        name: day.name.en || day.name.ar ? day.name : undefined,
        dayFocus: day.dayFocus?.trim() ? day.dayFocus : undefined,
        sessions: day.sessions.map((session, sessionIndex) => ({
          ...(session.id ? { id: session.id } : {}),
          name: session.name,
          sortOrder: session.sortOrder ?? sessionIndex,
          items: session.items.map((item, itemIndex) => ({
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

    if (isPublished && activeEnrollmentCount > 0) {
      const ok = window.confirm(
        `This program is published and has ${activeEnrollmentCount} active enrollment(s). Saving may change the template for users in progress. Continue?`
      );
      if (!ok) return;
    }

    setLoading(true);

    try {
      const res = await fetch(`/api/programs/${programId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(buildPayload()),
      });

      const data = await res.json();
      if (data.success) {
        toast.success('Program updated successfully');
        router.push('/admin/programs');
      } else {
        toast.error(data.errors?.join('\n') || data.error || 'Failed to update program');
      }
    } catch (error) {
      console.error('Error updating program:', error);
      toast.error('Failed to update program');
    } finally {
      setLoading(false);
    }
  };

  if (loadingProgram) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <div className="text-gray-500">Loading program...</div>
      </div>
    );
  }

  return (
    <div className="max-w-5xl mx-auto space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Edit Program</h1>
          <p className="text-gray-600 mt-1">Update training program</p>
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

      {activeEnrollmentCount > 0 ? (
        <div
          className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900"
          role="status"
        >
          This program has <strong>{activeEnrollmentCount}</strong> active enrollment
          {activeEnrollmentCount === 1 ? '' : 's'}. Structural changes may affect users in progress.
        </div>
      ) : null}

      <form onSubmit={handleSubmit} className="space-y-6">
        <Card className="sticky top-4 z-10 border-blue-100 bg-white/95 backdrop-blur p-4">
          <div className="flex flex-wrap items-center justify-between gap-4">
            <div className="flex flex-wrap gap-2">
              {PROGRAM_EDITOR_SECTIONS.map((section) => (
                <a
                  key={section.id}
                  href={`#${section.id}`}
                  className="rounded-lg border border-gray-200 px-3 py-1.5 text-sm font-medium text-gray-700 hover:border-blue-300 hover:text-blue-700"
                >
                  {section.label}
                </a>
              ))}
            </div>
            <div className="text-sm text-gray-500">
              {builderSummary.weeks} weeks • {builderSummary.days} days • {builderSummary.sessions} sessions • {builderSummary.items} items
            </div>
          </div>
        </Card>

        <Card id="basic-information" className="p-6 scroll-mt-24">
          <h2 className="text-lg font-semibold mb-4">Basic Information</h2>

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

          <div className="grid grid-cols-2 gap-4 mt-4">
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
        </Card>

        <Card id="program-configuration" className="p-6 scroll-mt-24">
          <h2 className="text-lg font-semibold mb-4">Program Configuration</h2>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <Label>Cover Image URL</Label>
              <Input
                value={coverImageUrl}
                onChange={(e) => setCoverImageUrl(e.target.value)}
                placeholder="https://..."
              />
            </div>
            <div>
              <Label>Difficulty</Label>
              <Select
                value={difficulty}
                onChange={(e) => setDifficulty(e.target.value as 'beginner' | 'intermediate' | 'advanced')}
                options={[
                  { value: 'beginner', label: 'Beginner' },
                  { value: 'intermediate', label: 'Intermediate' },
                  { value: 'advanced', label: 'Advanced' },
                ]}
              />
            </div>
          </div>

          <div className="grid grid-cols-3 gap-4 mt-4">
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
              <Label>Version</Label>
              <Input
                type="number"
                min={1}
                value={version}
                onChange={(e) => setVersion(Number.parseInt(e.target.value, 10) || 1)}
              />
            </div>
            <div>
              <Label>Tags (comma separated)</Label>
              <Input
                value={tags}
                onChange={(e) => setTags(e.target.value)}
                placeholder="weight-loss, beginner"
              />
            </div>
          </div>

          {durationWeeks !== weeks.length ? (
            <p className="mt-3 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
              Duration is set to {durationWeeks} week(s), but the builder currently contains {weeks.length} week block(s).
            </p>
          ) : null}
        </Card>

        <Card id="prescription-settings" className="p-6 scroll-mt-24">
          <h2 className="text-lg font-semibold mb-4">Prescription Settings</h2>

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
              <Label>Program domain</Label>
              <Select
                value={programDomainEnum}
                onChange={(e) =>
                  setProgramDomainEnum(e.target.value as 'TRAINING' | 'MOBILITY' | 'THERAPEUTIC')
                }
                options={[
                  { value: 'TRAINING', label: 'Training' },
                  { value: 'MOBILITY', label: 'Mobility' },
                  { value: 'THERAPEUTIC', label: 'Therapeutic' },
                ]}
              />
            </div>
            <div>
              <Label>Training goal</Label>
              <Select
                value={trainingGoal}
                onChange={(e) => setTrainingGoal(e.target.value)}
                options={[
                  { value: '', label: '—' },
                  { value: 'STRENGTH', label: 'Strength' },
                  { value: 'HYPERTROPHY', label: 'Hypertrophy' },
                  { value: 'POWER', label: 'Power' },
                  { value: 'GENERAL_HEALTH', label: 'General health' },
                ]}
              />
            </div>
          </div>

          <div className="grid grid-cols-3 gap-4 mt-4">
            <div>
              <Label>Target Domain</Label>
              <Select
                value={targetDomain}
                onChange={(e) => setTargetDomain(e.target.value)}
                options={[
                  { value: 'none', label: 'None' },
                  { value: 'mobility', label: 'Mobility' },
                  { value: 'strength', label: 'Strength' },
                  { value: 'control', label: 'Control' },
                  { value: 'symmetry', label: 'Symmetry' },
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

          <div className="mt-4">
            <Label>Target equipment</Label>
            <div className="flex flex-wrap gap-3 mt-1">
              {TARGET_EQUIPMENT_OPTIONS.map((eq) => (
                <label key={eq} className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={targetEquipment.includes(eq)}
                    onChange={(e) => {
                      if (e.target.checked) {
                        setTargetEquipment((prev) => [...prev, eq]);
                      } else {
                        setTargetEquipment((prev) => prev.filter((x) => x !== eq));
                      }
                    }}
                    className="h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                  />
                  <span className="text-sm text-gray-700">{eq}</span>
                </label>
              ))}
            </div>
          </div>

          <div className="mt-4">
            <Label>Coaching notes (JSON)</Label>
            <Textarea
              value={coachingNotesProgram}
              onChange={(e) => setCoachingNotesProgram(e.target.value)}
              rows={2}
              placeholder="{}"
            />
          </div>

          <div className="mt-4">
            <Label>Target Regions</Label>
            <div className="flex flex-wrap gap-3 mt-1">
              {TARGET_REGION_OPTIONS.map((region) => (
                <label key={region} className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={targetRegions.includes(region)}
                    onChange={(e) => {
                      if (e.target.checked) {
                        setTargetRegions((prev) => [...prev, region]);
                      } else {
                        setTargetRegions((prev) => prev.filter((r) => r !== region));
                      }
                    }}
                    className="h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                  />
                  <span className="text-sm text-gray-700 capitalize">{region}</span>
                </label>
              ))}
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4 mt-4">
            <div>
              <Label>Level Range Min</Label>
              <Input
                type="number"
                min={1}
                value={levelRangeMin}
                onChange={(e) => setLevelRangeMin(Number.parseInt(e.target.value, 10) || 1)}
              />
            </div>
            <div>
              <Label>Level Range Max</Label>
              <Input
                type="number"
                min={1}
                value={levelRangeMax}
                onChange={(e) => setLevelRangeMax(Number.parseInt(e.target.value, 10) || 1)}
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4 mt-4">
            <RecommendationEditor
              title="Entry recommendations"
              description="Guidance for when this program should start. Use structured thresholds instead of raw JSON where possible."
              value={entryRecommendations}
              onChange={setEntryRecommendations}
              mode="entry"
            />
            <RecommendationEditor
              title="Exit recommendations"
              description="Used during exit review to decide whether the athlete should reassess or move to the next program."
              value={exitRecommendations}
              onChange={setExitRecommendations}
              mode="exit"
            />
          </div>

          <div className="mt-4">
            <Label>Contraindications</Label>
            <div className="flex flex-wrap gap-3 mt-1">
              {TARGET_REGION_OPTIONS.map((region) => (
                <label key={region} className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={contraindications.includes(region)}
                    onChange={(e) => {
                      if (e.target.checked) {
                        setContraindications((prev) => [...prev, region]);
                      } else {
                        setContraindications((prev) => prev.filter((r) => r !== region));
                      }
                    }}
                    className="h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                  />
                  <span className="text-sm text-gray-700 capitalize">{region}</span>
                </label>
              ))}
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

        <Card className="p-6 border-dashed border-gray-300">
          <div className="grid grid-cols-4 gap-4">
            <div>
              <p className="text-sm text-gray-500">Weeks</p>
              <p className="text-2xl font-semibold text-gray-900">{builderSummary.weeks}</p>
            </div>
            <div>
              <p className="text-sm text-gray-500">Days</p>
              <p className="text-2xl font-semibold text-gray-900">{builderSummary.days}</p>
            </div>
            <div>
              <p className="text-sm text-gray-500">Sessions</p>
              <p className="text-2xl font-semibold text-gray-900">{builderSummary.sessions}</p>
            </div>
            <div>
              <p className="text-sm text-gray-500">Items</p>
              <p className="text-2xl font-semibold text-gray-900">{builderSummary.items}</p>
            </div>
          </div>
        </Card>

        <Card id="program-builder" className="p-6 space-y-4 scroll-mt-24">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-lg font-semibold">Program Builder</h2>
              <p className="text-sm text-gray-500">Build weeks, days, sessions, and items</p>
            </div>
            <Button type="button" variant="outline" onClick={addWeek}>
              Add Week
            </Button>
          </div>

          <Card className="border-blue-100 bg-blue-50/70 shadow-none">
            <div className="p-4">
              <p className="text-sm font-semibold text-blue-950">Recommended workflow</p>
              <ul className="mt-2 space-y-1 text-sm text-blue-900">
                <li>1. Adjust the week structure before editing fine details.</li>
                <li>2. Use rest-day markers early to keep the schedule clean.</li>
                <li>3. Duplicate repeated structures, then edit only the differences.</li>
                <li>4. Leave advanced item fields until the base plan is stable.</li>
              </ul>
            </div>
          </Card>

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
              key={week.id ?? `week-${weekIndex}`}
              title={`Week ${weekIndex + 1}`}
              subtitle={getWeekSummary(week)}
              defaultOpen={weekIndex === 0}
              meta={[
                { label: week.weekType === 'DELOAD' ? 'Deload' : 'Normal', variant: week.weekType === 'DELOAD' ? 'warning' : 'default' },
                { label: `${week.days.length} day(s)` },
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
                <h4 className="text-sm font-semibold text-gray-700">Days</h4>
                <Button type="button" variant="outline" onClick={() => addDay(weekIndex)}>
                  Add Day
                </Button>
              </div>

              {week.days.map((day, dayIndex) => (
                <CollapsibleBuilderSection
                  key={`day-${dayIndex}`}
                  title={`Day ${dayIndex + 1}`}
                  subtitle={getDaySummary(day)}
                  defaultOpen={weekIndex === 0 && dayIndex === 0}
                  meta={[
                    { label: day.isRestDay ? 'Rest' : 'Training', variant: day.isRestDay ? 'warning' : 'primary' },
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
                  <div className="grid grid-cols-5 gap-4">
                    <div>
                      <Label>Day Number</Label>
                      <Input
                        type="number"
                        min={1}
                        max={7}
                        value={day.dayNumber}
                        onChange={(e) =>
                          updateDay(weekIndex, dayIndex, { dayNumber: Number.parseInt(e.target.value, 10) || 1 })
                        }
                      />
                    </div>
                    <div className="flex items-center gap-2">
                      <input
                        type="checkbox"
                        checked={day.isRestDay}
                        onChange={(e) => updateDay(weekIndex, dayIndex, { isRestDay: e.target.checked })}
                      />
                      <Label>Rest Day</Label>
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
                              <div>
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
        </Card>

        <div className="sticky bottom-4 z-10 flex justify-end gap-4 rounded-2xl border border-gray-200 bg-white/95 p-4 shadow-sm backdrop-blur">
          <Button type="button" variant="outline" onClick={() => router.push('/admin/programs')}>
            Cancel
          </Button>
          <Button type="submit" disabled={loading}>
            {loading ? 'Saving...' : 'Save Changes'}
          </Button>
        </div>
      </form>
    </div>
  );
}
