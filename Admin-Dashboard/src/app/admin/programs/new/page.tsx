'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { toast } from 'sonner';
import { useRouter, useSearchParams } from 'next/navigation';
import { Input, Select, Label, Button, Card, Textarea, SearchableSelect } from '@/components/ui';
import type { LocalizedText } from '@/lib/types/localized';
import { ProgramCalendarBuilder } from '../_components/ProgramCalendarBuilder';
import {
  type WeekForm,
  createEmptyWeek,
} from '../_lib/program-calendar';
import { getAutoAssignmentReadiness } from '../_lib/auto-assignment';
import { ProgramAttributesSection, useAttributesCatalog } from '../_components/ProgramAttributesSection';
import { buildValueIdMeta, type ProgramAttributeFormRow } from '../_lib/program-prescription-attributes';
import { exerciseProgramAttributeStatus } from '../_lib/exercise-program-attribute-status';
import { PROGRAM_EDITOR_TABS, type ProgramEditorTabId } from '../_components/program-editor-tabs';
import { ProgramEditorTabBar } from '../_components/ProgramEditorTabBar';
import { padProgramWeeksToSevenDays } from '../_lib/week-seven-days';

interface ProgramSummary {
  id: string;
  name: LocalizedText;
}

interface Level {
  id: string;
  name: LocalizedText;
  number?: number;
  levelNumber?: number;
}

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

interface WorkoutTemplateSummary {
  id: string;
  name: LocalizedText;
}

function levelNumberOf(level: Level): number {
  return level.number ?? level.levelNumber ?? 0;
}

function levelLabel(level: Level): string {
  const n = levelNumberOf(level);
  const name = level.name?.en || level.name?.ar || `Level ${n}`;
  return `L${n} - ${name}`;
}

export default function NewProgramPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [loading, setLoading] = useState(false);
  const [loadingExercises, setLoadingExercises] = useState(true);
  const [loadingWorkoutTemplates, setLoadingWorkoutTemplates] = useState(true);

  const [name, setName] = useState({ ar: '', en: '' });
  const [description, setDescription] = useState({ ar: '', en: '' });
  const [coverImageUrl, setCoverImageUrl] = useState('');
  const [durationWeeks, setDurationWeeks] = useState(4);
  const [version, setVersion] = useState(1);
  const [tags, setTags] = useState('');
  const [weeks, setWeeks] = useState<WeekForm[]>([createEmptyWeek(1)]);

  const [programOwnership, setProgramOwnership] = useState<'SYSTEM' | 'COACH' | 'CUSTOM'>('SYSTEM');
  const { catalog: attributeCatalog, loading: loadingAttributeCatalog, error: attributeCatalogError } =
    useAttributesCatalog();
  const [programAttributeRows, setProgramAttributeRows] = useState<ProgramAttributeFormRow[]>([]);
  const [autoAssignable, setAutoAssignable] = useState(false);
  const [coachingNotesProgram, setCoachingNotesProgram] = useState('');
  const [weeklyWorkoutTarget, setWeeklyWorkoutTarget] = useState<number | ''>('');
  const [estimatedWorkoutMinutes, setEstimatedWorkoutMinutes] = useState<number | ''>('');
  const [levels, setLevels] = useState<Level[]>([]);
  const [loadingLevels, setLoadingLevels] = useState(true);
  const [levelMinId, setLevelMinId] = useState('');
  const [levelMaxId, setLevelMaxId] = useState('');
  const [levelRangeMin, setLevelRangeMin] = useState(1);
  const [levelRangeMax, setLevelRangeMax] = useState(1);
  const [singleLevelMode, setSingleLevelMode] = useState(true);
  const [prerequisiteProgramId, setPrerequisiteProgramId] = useState('');
  const [nextProgramId, setNextProgramId] = useState('');

  const [editorTab, setEditorTab] = useState<ProgramEditorTabId>('basics');

  const [exercises, setExercises] = useState<ExerciseSummary[]>([]);
  const [workoutTemplates, setWorkoutTemplates] = useState<WorkoutTemplateSummary[]>([]);
  const [publishedPrograms, setPublishedPrograms] = useState<ProgramSummary[]>([]);

  useEffect(() => {
    const fetchLevels = async () => {
      try {
        const res = await fetch('/api/admin/levels');
        const data = await res.json();
        if (data.success) {
          const sorted = (Array.isArray(data.data) ? data.data : []).sort(
            (a: Level, b: Level) => levelNumberOf(a) - levelNumberOf(b),
          );
          setLevels(sorted);
        }
      } catch (error) {
        console.error('Error fetching levels:', error);
      } finally {
        setLoadingLevels(false);
      }
    };
    fetchLevels();
  }, []);

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
    const fetchWorkoutTemplates = async () => {
      try {
        const res = await fetch('/api/workout-templates?status=published&limit=200');
        const data = await res.json();
        if (data.success) {
          setWorkoutTemplates(data.data);
        }
      } catch (error) {
        console.error('Error fetching workout templates:', error);
      } finally {
        setLoadingWorkoutTemplates(false);
      }
    };
    fetchWorkoutTemplates();
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

  const levelOptions = useMemo(
    () =>
      levels.map((level) => ({
        value: level.id,
        label: levelLabel(level),
      })),
    [levels],
  );

  const levelById = useMemo(
    () => new Map(levels.map((level) => [level.id, level])),
    [levels],
  );

  const selectedLevelRange = useMemo(() => {
    const minLevel = levelById.get(levelMinId);
    const maxLevel = levelById.get(levelMaxId);
    if (minLevel && maxLevel && levelNumberOf(minLevel) > levelNumberOf(maxLevel)) {
      return { minLevel: maxLevel, maxLevel: minLevel };
    }
    return { minLevel, maxLevel };
  }, [levelById, levelMinId, levelMaxId]);

  const selectSingleLevel = (id: string) => {
    const level = levelById.get(id);
    if (!level) return;
    const n = levelNumberOf(level);
    setLevelMinId(id);
    setLevelMaxId(id);
    setLevelRangeMin(n);
    setLevelRangeMax(n);
  };

  const selectMinLevel = (id: string) => {
    const level = levelById.get(id);
    if (!level) return;
    const n = levelNumberOf(level);
    const currentMax = levelById.get(levelMaxId);
    setLevelMinId(id);
    setLevelRangeMin(n);
    if (!currentMax || n > levelNumberOf(currentMax)) {
      setLevelMaxId(id);
      setLevelRangeMax(n);
    }
  };

  const selectMaxLevel = (id: string) => {
    const level = levelById.get(id);
    if (!level) return;
    const n = levelNumberOf(level);
    const currentMin = levelById.get(levelMinId);
    setLevelMaxId(id);
    setLevelRangeMax(n);
    if (!currentMin || n < levelNumberOf(currentMin)) {
      setLevelMinId(id);
      setLevelRangeMin(n);
    }
  };

  useEffect(() => {
    if (levels.length === 0) return;
    if (!levelMinId) {
      const minLevel =
        levels.find((level) => levelNumberOf(level) === levelRangeMin) ?? levels[0];
      if (minLevel) setLevelMinId(minLevel.id);
    }
    if (!levelMaxId) {
      const maxLevel =
        levels.find((level) => levelNumberOf(level) === levelRangeMax) ??
        levels.find((level) => level.id === levelMinId) ??
        levels[0];
      if (maxLevel) setLevelMaxId(maxLevel.id);
    }
  }, [levels, levelMinId, levelMaxId, levelRangeMin, levelRangeMax]);

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
      workoutTemplates.map((workout) => ({
        value: workout.id,
        label: `${workout.name.en} / ${workout.name.ar}`,
      })),
    [workoutTemplates]
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
        levelRangeMin,
        levelRangeMax,
        programAttributes: programAttributesForReadiness,
      }),
    [programOwnership, autoAssignable, levelRangeMin, levelRangeMax, programAttributesForReadiness],
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
    const plannedWorkoutCount = weeks.reduce(
      (acc, week) => acc + week.days.reduce((dayAcc, day) => dayAcc + day.plannedWorkouts.length, 0),
      0
    );
    const items = weeks.reduce(
      (acc, week) =>
        acc +
        week.days.reduce(
          (dayAcc, day) =>
            dayAcc + day.plannedWorkouts.reduce((plannedWorkoutAcc, plannedWorkout) => plannedWorkoutAcc + plannedWorkout.items.length, 0),
          0
        ),
      0
    );

    return { weeks: weeks.length, days, plannedWorkouts: plannedWorkoutCount, items };
  }, [weeks]);

  useEffect(() => {
    const min = Number.parseInt(searchParams.get('levelRangeMin') || '', 10);
    const max = Number.parseInt(searchParams.get('levelRangeMax') || '', 10);
    if (Number.isFinite(min) && min > 0) {
      const resolvedMax = Number.isFinite(max) && max > 0 ? max : min;
      setLevelRangeMin(Math.min(min, resolvedMax));
      setLevelRangeMax(Math.max(min, resolvedMax));
      setSingleLevelMode(min === resolvedMax);
      const minLevel = levels.find((level) => levelNumberOf(level) === Math.min(min, resolvedMax));
      const maxLevel = levels.find((level) => levelNumberOf(level) === Math.max(min, resolvedMax));
      if (minLevel) setLevelMinId(minLevel.id);
      if (maxLevel) setLevelMaxId(maxLevel.id);
    } else if (Number.isFinite(max) && max > 0) {
      setLevelRangeMin(max);
      setLevelRangeMax(max);
      setSingleLevelMode(true);
      const level = levels.find((row) => levelNumberOf(row) === max);
      if (level) {
        setLevelMinId(level.id);
        setLevelMaxId(level.id);
      }
    }
  }, [searchParams, levels]);

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

  const muscleOptions = useMemo(() => {
    const muscleAttr = attributeCatalog.find((a) => a.code === 'muscle');
    return muscleAttr?.values ?? [];
  }, [attributeCatalog]);

  const parseJsonField = (value: string): object | undefined => {
    if (!value.trim()) return undefined;
    try {
      return JSON.parse(value);
    } catch {
      return undefined;
    }
  };

  const buildPayload = () => {
    return {
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
    weeklyWorkoutTarget: weeklyWorkoutTarget === '' ? undefined : weeklyWorkoutTarget,
    estimatedWorkoutMinutes: estimatedWorkoutMinutes === '' ? undefined : estimatedWorkoutMinutes,
	    levelMinId: selectedLevelRange.minLevel?.id || undefined,
	    levelMaxId: selectedLevelRange.maxLevel?.id || undefined,
	    levelRangeMin: Math.min(levelRangeMin, levelRangeMax),
	    levelRangeMax: Math.max(levelRangeMin, levelRangeMax),
    prerequisiteProgramId: prerequisiteProgramId || undefined,
    nextProgramId: nextProgramId || undefined,
    programAttributes: programAttributeRows,
    weeks: weeks.map((week, weekIndex) => ({
      target: week.target.en || week.target.ar ? week.target : undefined,
      description: week.description.en || week.description.ar ? week.description : undefined,
      sortOrder: week.sortOrder ?? weekIndex,
      days: week.days.map((day) => ({
        dayType: day.dayType,
        targetMuscleValueIds: day.targetMuscleIds,
        plannedWorkouts: day.plannedWorkouts.map((plannedWorkout, plannedWorkoutIndex) => ({
          name: plannedWorkout.name,
          sortOrder: plannedWorkout.sortOrder ?? plannedWorkoutIndex,
          estimatedDurationMin:
            plannedWorkout.estimatedDurationMin === undefined || plannedWorkout.estimatedDurationMin === null
              ? undefined
              : plannedWorkout.estimatedDurationMin,
          items: plannedWorkout.items.map((item, itemIndex) => ({
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
          })),
        })),
      })),
    })),
    };
  };

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
                {builderSummary.weeks}w · {builderSummary.days}d · {builderSummary.plannedWorkouts} pw · {builderSummary.items}{' '}
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

          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
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

          <div className="mt-4 space-y-3">
            <label className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={singleLevelMode}
                onChange={(e) => {
                  const checked = e.target.checked;
                  setSingleLevelMode(checked);
                  if (checked) {
                    setLevelMaxId(levelMinId);
                    setLevelRangeMax(levelRangeMin);
                  }
                }}
                className="size-4 rounded border-input accent-primary"
              />
              Single training level
            </label>
            {singleLevelMode ? (
              <div className="max-w-xs">
                <Label>Training level</Label>
                <Select
                  value={levelMinId}
                  onChange={(e) => selectSingleLevel(e.target.value)}
                  options={[
                    {
                      value: '',
                      label: loadingLevels ? 'Loading levels...' : 'Select a level...',
                      disabled: true,
                    },
                    ...levelOptions,
                  ]}
                />
              </div>
            ) : (
              <div className="grid max-w-md grid-cols-2 gap-4">
                <div>
                  <Label>Min level</Label>
                  <Select
                    value={levelMinId}
                    onChange={(e) => selectMinLevel(e.target.value)}
                    options={[
                      {
                        value: '',
                        label: loadingLevels ? 'Loading levels...' : 'Select min level...',
                        disabled: true,
                      },
                      ...levelOptions,
                    ]}
                  />
                </div>
                <div>
                  <Label>Max level</Label>
                  <Select
                    value={levelMaxId}
                    onChange={(e) => selectMaxLevel(e.target.value)}
                    options={[
                      {
                        value: '',
                        label: loadingLevels ? 'Loading levels...' : 'Select max level...',
                        disabled: true,
                      },
                      ...levelOptions,
                    ]}
                  />
                </div>
              </div>
            )}
            <p className="text-xs text-gray-500">
              Linked to real training levels; the range is used for auto-assignment and program map columns.
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
              <Label>Weekly Workout Target</Label>
              <Input
                type="number"
                min={1}
                value={weeklyWorkoutTarget}
                onChange={(e) =>
                  setWeeklyWorkoutTarget(e.target.value === '' ? '' : Number.parseInt(e.target.value, 10) || '')
                }
              />
            </div>
            <div>
              <Label>Estimated Workout Minutes</Label>
              <Input
                type="number"
                min={1}
                value={estimatedWorkoutMinutes}
                onChange={(e) =>
                  setEstimatedWorkoutMinutes(e.target.value === '' ? '' : Number.parseInt(e.target.value, 10) || '')
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
          <ProgramCalendarBuilder
            weeks={weeks}
            setWeeks={setWeeks}
            durationWeeks={durationWeeks}
            setDurationWeeks={setDurationWeeks}
            exercises={exercises}
            exerciseOptions={exerciseOptions}
            workoutOptions={workoutOptions}
            exerciseLabelById={exerciseLabelById}
            muscleOptions={muscleOptions}
            loadingMuscleOptions={loadingAttributeCatalog}
            loadingExercises={loadingExercises}
            loadingWorkoutTemplates={loadingWorkoutTemplates}
            exerciseAttributeCheck={exerciseAttributeCheck}
          />
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
