'use client';

import { FormEvent, useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import * as Popover from '@radix-ui/react-popover';
import { toast } from 'sonner';
import {
  ArrowDown,
  ArrowUp,
  ChevronDown,
  ChevronRight,
  Dumbbell,
  Plus,
  Save,
  Search,
  Timer,
  Trash2,
  X,
} from 'lucide-react';
import { FileUpload } from '@/components/forms/FileUpload';
import { Badge, Button, Checkbox, Dialog, DialogBody, DialogContent, DialogFooter, DialogHeader, DialogTitle, Input, Label, Select, Textarea } from '@/components/ui';
import type { LocalizedText } from '@/lib/types/localized';
import { cn } from '@/lib/utils';
import {
  expandPerSetValues,
  formatPerSetNumbers,
} from '@/lib/per-set-values';
import { exercisesService } from '@/modules/exercises/exercises.service';

type Mode = 'create' | 'edit';
type Selected =
  | { kind: 'meta' }
  | { kind: 'phase'; phaseKey: string }
  | { kind: 'exercise'; phaseKey: string; exerciseKey: string }
  | { kind: 'rest'; phaseKey: string; exerciseKey: string };

interface Exercise {
  id: string;
  slug: string;
  name: LocalizedText;
  status: string;
  familyKey?: string | null;
  categoryId?: string;
  category?: {
    id?: string;
    code: string;
    name: LocalizedText;
  };
  supportsWeight?: boolean;
  defaultWeight?: number | null;
  loadCapability?: string | null;
  countingMethod?: {
    code: string;
    name: LocalizedText;
  };
  poseVariants?: {
    id: string;
    name: LocalizedText;
    sortOrder: number;
  }[];
}

interface WorkoutPhaseCatalog {
  id: string;
  slug: string;
  name: LocalizedText;
  description?: LocalizedText | null;
  role: string;
  canSkip: boolean;
  canContinue: boolean;
  maxContinueTimeMs: number | null;
  color?: string | null;
  icon?: string | null;
  sortOrder: number;
}

interface LevelSummary {
  id: string;
  number: number;
  code: string;
  name: LocalizedText;
}

interface CategorySummary {
  id: string;
  code: string;
  name: LocalizedText;
}

interface ExerciseDraft {
  key: string;
  id?: string;
  exerciseId: string;
  exercise?: Exercise;
  variantIndex: number;
  targetReps?: number;
  targetRepsPerSet?: number[];
  targetDuration?: number;
  sets: number;
  restBetweenSetsMs: number;
  restBetweenSetsPerSetMs?: number[];
  restAfterExerciseMs: number;
  weightPerSet?: number[];
  /** Raw text while editing comma-separated fields (preserves trailing commas). */
  targetRepsPerSetText?: string;
  restBetweenSetsPerSetText?: string;
  weightPerSetText?: string;
  notes: LocalizedText;
}

interface PhaseDraft {
  key: string;
  id?: string;
  phaseId: string;
  catalog?: WorkoutPhaseCatalog;
  sortOrder: number;
  nameOverride?: LocalizedText;
  canSkipOverride?: boolean;
  canContinueOverride?: boolean;
  maxContinueTimeMsOverride?: number | null;
  collapsed: boolean;
  exercises: ExerciseDraft[];
}

interface WorkoutTemplateResponse {
  id: string;
  name: LocalizedText;
  description: LocalizedText | null;
  coverImageUrl: string | null;
  levelId?: string | null;
  level?: LevelSummary | null;
  estimatedDurationMin: number | null;
  tags: string[] | null;
  status: 'draft' | 'published';
  isFeatured?: boolean;
  phases?: Array<{
    id: string;
    phaseId: string;
    sortOrder: number;
    nameOverride?: LocalizedText | null;
    canSkipOverride?: boolean | null;
    canContinueOverride?: boolean | null;
    maxContinueTimeMsOverride?: number | null;
    phase: WorkoutPhaseCatalog;
    exercises: WorkoutExerciseResponse[];
  }>;
  exercises?: WorkoutExerciseResponse[];
}

interface WorkoutExerciseResponse {
  id: string;
  exerciseId: string;
  variantIndex: number;
  targetReps: number | null;
  targetRepsPerSet: number[] | null;
  targetDuration: number | null;
  sets: number | null;
  restBetweenSetsMs: number | null;
  restBetweenSetsPerSetMs: number[] | null;
  restAfterExerciseMs: number | null;
  weightPerSet: number[] | null;
  notes: LocalizedText | null;
  sortOrder: number;
  exercise: Exercise;
}

interface WorkoutEditorProps {
  mode: Mode;
  workoutId?: string;
}

const EMPTY_TEXT: LocalizedText = { en: '', ar: '' };
const DEFAULT_TARGET_REPS = 10;
const DEFAULT_TARGET_DURATION_SEC = 30;
const DEFAULT_REST_BETWEEN_SETS_MS = 30000;
const DEFAULT_REST_BETWEEN_SETS_SEC = DEFAULT_REST_BETWEEN_SETS_MS / 1000;

function makeKey(prefix: string) {
  return `${prefix}_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 8)}`;
}

function secondsToMs(value: string) {
  if (!value.trim()) return undefined;
  return Math.max(0, Number(value) * 1000);
}

function msToSeconds(value?: number | null) {
  return value === undefined || value === null ? '' : String(Math.round(value / 1000));
}

function normalizeSetCount(value: number | string | undefined) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return 1;
  return Math.max(1, Math.floor(parsed));
}

function expandNumbers(values: number[] | undefined | null, sets: number, fallback?: number) {
  return expandPerSetValues(values, sets, fallback);
}

function exerciseSupportsWeight(exercise?: Exercise, weightPerSet?: number[]) {
  if (weightPerSet?.some((value) => value != null && value > 0)) return true;
  return Boolean(
    exercise?.supportsWeight ||
      exercise?.defaultWeight != null ||
      exercise?.loadCapability === 'EXTERNAL_LOAD_OPTIONAL' ||
      exercise?.loadCapability === 'EXTERNAL_LOAD_REQUIRED'
  );
}

function mergeExerciseMetadata(primary?: Exercise, secondary?: Exercise): Exercise | undefined {
  if (!primary && !secondary) return undefined;
  if (!primary) return secondary;
  if (!secondary) return primary;
  return {
    ...primary,
    ...secondary,
    supportsWeight: secondary.supportsWeight ?? primary.supportsWeight,
    loadCapability: secondary.loadCapability ?? primary.loadCapability,
    defaultWeight: secondary.defaultWeight ?? primary.defaultWeight,
    categoryId: secondary.categoryId ?? primary.categoryId,
    category: secondary.category ?? primary.category,
    familyKey: secondary.familyKey ?? primary.familyKey,
    countingMethod: secondary.countingMethod ?? primary.countingMethod,
    poseVariants: secondary.poseVariants?.length ? secondary.poseVariants : primary.poseVariants,
  };
}

function updateExpandedNumber(
  values: number[] | undefined,
  index: number,
  sets: number,
  rawValue: string,
  fallback?: number,
  options: { integer?: boolean } = {}
): number[] | undefined {
  const current: Array<number | undefined> =
    expandNumbers(values, sets, fallback) ?? Array.from({ length: sets }, () => undefined as number | undefined);
  const parsed = rawValue.trim() === '' ? undefined : Number(rawValue);
  const nextValue =
    parsed === undefined || Number.isNaN(parsed)
      ? undefined
      : Math.max(0, options.integer ? Math.round(parsed) : parsed);
  const next = current.map((value, itemIndex) => (itemIndex === index ? nextValue : value));

  if (next.every((value) => value === undefined)) return undefined;
  return next.map((value) => value ?? fallback ?? 0);
}

function mapExerciseResponse(we: WorkoutExerciseResponse): ExerciseDraft {
  return {
    key: makeKey('ex'),
    id: we.id,
    exerciseId: we.exerciseId,
    exercise: we.exercise,
    variantIndex: we.variantIndex ?? 0,
    targetReps: we.targetReps ?? undefined,
    targetRepsPerSet: we.targetRepsPerSet ?? (we.targetReps != null ? [we.targetReps] : undefined),
    targetDuration: we.targetDuration ?? undefined,
    sets: we.sets ?? 1,
    restBetweenSetsMs: we.restBetweenSetsMs ?? DEFAULT_REST_BETWEEN_SETS_MS,
    restBetweenSetsPerSetMs:
      we.restBetweenSetsPerSetMs ??
      (we.restBetweenSetsMs != null ? [we.restBetweenSetsMs] : undefined),
    restAfterExerciseMs: we.restAfterExerciseMs ?? 0,
    weightPerSet: we.weightPerSet ?? undefined,
    notes: we.notes || { ...EMPTY_TEXT },
  };
}

function exerciseLabel(exercise?: Exercise) {
  if (!exercise) return 'Select exercise';
  return exercise.name.en || exercise.name.ar || exercise.slug;
}

function mergeExercises(current: Exercise[], incoming: Exercise[]) {
  const map = new Map(current.map((exercise) => [exercise.id, exercise]));
  incoming.forEach((exercise) => {
    const prev = map.get(exercise.id);
    map.set(exercise.id, prev ? mergeExerciseMetadata(prev, exercise) ?? exercise : exercise);
  });
  return [...map.values()];
}

export function WorkoutEditor({ mode, workoutId }: WorkoutEditorProps) {
  const router = useRouter();
  const [loading, setLoading] = useState(mode === 'edit');
  const [saving, setSaving] = useState(false);
  const [exercises, setExercises] = useState<Exercise[]>([]);
  const [phaseCatalog, setPhaseCatalog] = useState<WorkoutPhaseCatalog[]>([]);
  const [levels, setLevels] = useState<LevelSummary[]>([]);
  const [categories, setCategories] = useState<CategorySummary[]>([]);
  const [addPhaseOpen, setAddPhaseOpen] = useState(false);
  const [selectedPhaseId, setSelectedPhaseId] = useState('');

  const [name, setName] = useState<LocalizedText>({ ...EMPTY_TEXT });
  const [description, setDescription] = useState<LocalizedText>({ ...EMPTY_TEXT });
  const [coverImageUrl, setCoverImageUrl] = useState('');
  const [levelId, setLevelId] = useState('');
  const [estimatedDurationMin, setEstimatedDurationMin] = useState<number | ''>('');
  const [tags, setTags] = useState('');
  const [isFeatured, setIsFeatured] = useState(false);
  const [status, setStatus] = useState<'draft' | 'published'>('draft');
  const [phases, setPhases] = useState<PhaseDraft[]>([]);
  const [selected, setSelected] = useState<Selected>({ kind: 'meta' });

  const mainCatalog = useMemo(
    () => phaseCatalog.find((phase) => phase.slug === 'main') || phaseCatalog.find((phase) => phase.role === 'MAIN') || phaseCatalog[0],
    [phaseCatalog]
  );

  const familyOptions = useMemo(
    () =>
      [...new Set(exercises.map((exercise) => exercise.familyKey).filter((familyKey): familyKey is string => Boolean(familyKey)))]
        .sort((a, b) => a.localeCompare(b)),
    [exercises]
  );

  const handleExercisesLoaded = useCallback((items: Exercise[]) => {
    setExercises((current) => mergeExercises(current, items));
  }, []);

  useEffect(() => {
    async function loadLookups() {
      const [exerciseData, phaseRes, levelRes, attributeRes] = await Promise.all([
        exercisesService.list({ status: 'published', limit: 50 }),
        fetch('/api/workout-phases?active=true'),
        fetch('/api/admin/levels'),
        fetch('/api/attributes/lookup'),
      ]);
      const [phaseData, levelData, attributeData] = await Promise.all([phaseRes.json(), levelRes.json(), attributeRes.json()]);
      if (exerciseData.success) setExercises(exerciseData.data as Exercise[]);
      if (levelData.success) setLevels(levelData.data || []);
      if (attributeData.success) setCategories(attributeData.data?.categories || []);
      if (phaseData.success) {
        setPhaseCatalog(phaseData.data);
        setSelectedPhaseId(phaseData.data?.[0]?.id || '');
      }
    }
    loadLookups().catch((error) => {
      console.error('Error loading workout editor lookups:', error);
      toast.error('Failed to load editor data');
    });
  }, []);

  useEffect(() => {
    if (mode !== 'edit' || !workoutId) return;
    async function loadTemplate() {
      setLoading(true);
      try {
        const res = await fetch(`/api/workout-templates/${workoutId}`);
        const data = await res.json();
        if (!data.success) {
          toast.error(data.error || 'Workout template not found');
          router.push('/admin/workout-templates');
          return;
        }
        const workout = data.data as WorkoutTemplateResponse;
        setName(workout.name || { ...EMPTY_TEXT });
        setDescription(workout.description || { ...EMPTY_TEXT });
        setCoverImageUrl(workout.coverImageUrl || '');
        setLevelId(workout.levelId || '');
        setEstimatedDurationMin(workout.estimatedDurationMin ?? '');
        setTags((workout.tags || []).join(', '));
        setIsFeatured(workout.isFeatured ?? false);
        setStatus(workout.status || 'draft');

        const loadedPhases =
          workout.phases && workout.phases.length > 0
            ? workout.phases.map((phase) => ({
                key: makeKey('phase'),
                id: phase.id,
                phaseId: phase.phaseId,
                catalog: phase.phase,
                sortOrder: phase.sortOrder,
                nameOverride: phase.nameOverride || undefined,
                canSkipOverride: phase.canSkipOverride ?? undefined,
                canContinueOverride: phase.canContinueOverride ?? undefined,
                maxContinueTimeMsOverride: phase.maxContinueTimeMsOverride ?? undefined,
                collapsed: false,
                exercises: [...phase.exercises].sort((a, b) => a.sortOrder - b.sortOrder).map(mapExerciseResponse),
              }))
            : [];

        const templateExercises = loadedPhases.flatMap((phase) =>
          phase.exercises.map((exercise) => exercise.exercise).filter((item): item is Exercise => Boolean(item))
        );
        if (templateExercises.length > 0) {
          setExercises((current) => mergeExercises(current, templateExercises));
        }

        if (loadedPhases.length > 0) {
          setPhases(loadedPhases);
          setSelected({ kind: 'phase', phaseKey: loadedPhases[0].key });
        } else if (workout.exercises && workout.exercises.length > 0) {
          const fallbackPhase = mainCatalog || {
            id: '',
            slug: 'main',
            name: { en: 'Main Workout', ar: 'التمرين الأساسي' },
            role: 'MAIN',
            canSkip: false,
            canContinue: true,
            maxContinueTimeMs: null,
            sortOrder: 0,
          };
          const phase = {
            key: makeKey('phase'),
            phaseId: fallbackPhase.id,
            catalog: fallbackPhase,
            sortOrder: 0,
            collapsed: false,
            exercises: [...workout.exercises].sort((a, b) => a.sortOrder - b.sortOrder).map(mapExerciseResponse),
          };
          setPhases([phase]);
          setSelected({ kind: 'phase', phaseKey: phase.key });
        }
      } catch (error) {
        console.error('Error loading workout template:', error);
        toast.error('Failed to load workout template');
      } finally {
        setLoading(false);
      }
    }
    loadTemplate();
  }, [mode, workoutId, router]);

  useEffect(() => {
    if (mode !== 'create' || phases.length > 0 || !mainCatalog) return;
    const phase = createPhaseDraft(mainCatalog);
    setPhases([phase]);
    setSelected({ kind: 'phase', phaseKey: phase.key });
  }, [mode, mainCatalog, phases.length]);

  const selectedPhase = selected.kind !== 'meta' ? phases.find((phase) => phase.key === selected.phaseKey) : undefined;
  const selectedExercise =
    selected.kind === 'exercise' || selected.kind === 'rest'
      ? selectedPhase?.exercises.find((exercise) => exercise.key === selected.exerciseKey)
      : undefined;

  function createPhaseDraft(catalog: WorkoutPhaseCatalog): PhaseDraft {
    return {
      key: makeKey('phase'),
      phaseId: catalog.id,
      catalog,
      sortOrder: phases.length,
      collapsed: false,
      exercises: [],
    };
  }

  function updatePhase(phaseKey: string, updates: Partial<PhaseDraft>) {
    setPhases((current) => current.map((phase) => (phase.key === phaseKey ? { ...phase, ...updates } : phase)));
  }

  function updateExercise(phaseKey: string, exerciseKey: string, updates: Partial<ExerciseDraft>) {
    setPhases((current) =>
      current.map((phase) =>
        phase.key !== phaseKey
          ? phase
          : {
              ...phase,
              exercises: phase.exercises.map((exercise) => {
                if (exercise.key !== exerciseKey) return exercise;
                if (updates.exerciseId && updates.exerciseId !== exercise.exerciseId) {
                  const nextExercise = updates.exercise ?? exercises.find((item) => item.id === updates.exerciseId);
                  const isHold = nextExercise?.countingMethod?.code === 'hold';
                  const sets = normalizeSetCount(exercise.sets);
                  const defaultWeight = nextExercise?.defaultWeight ?? undefined;
                  const isFirstSelection = !exercise.exerciseId;
                  return {
                    ...exercise,
                    ...updates,
                    exercise: nextExercise,
                    variantIndex: 0,
                    targetReps: isHold ? undefined : isFirstSelection ? DEFAULT_TARGET_REPS : exercise.targetReps || DEFAULT_TARGET_REPS,
                    targetRepsPerSet: isHold
                      ? undefined
                      : expandNumbers(
                          isFirstSelection ? undefined : exercise.targetRepsPerSet,
                          sets,
                          isFirstSelection ? DEFAULT_TARGET_REPS : exercise.targetReps || DEFAULT_TARGET_REPS
                        ),
                    targetDuration: isHold
                      ? isFirstSelection
                        ? DEFAULT_TARGET_DURATION_SEC
                        : exercise.targetDuration || DEFAULT_TARGET_DURATION_SEC
                      : undefined,
                    restBetweenSetsMs: exercise.restBetweenSetsMs ?? DEFAULT_REST_BETWEEN_SETS_MS,
                    restBetweenSetsPerSetMs: expandNumbers(
                      exercise.restBetweenSetsPerSetMs,
                      sets,
                      exercise.restBetweenSetsMs ?? DEFAULT_REST_BETWEEN_SETS_MS
                    ),
                    weightPerSet: exerciseSupportsWeight(nextExercise, exercise.weightPerSet)
                      ? expandNumbers(isFirstSelection ? undefined : exercise.weightPerSet, sets, defaultWeight)
                      : undefined,
                    targetRepsPerSetText: undefined,
                    restBetweenSetsPerSetText: undefined,
                    weightPerSetText: undefined,
                  };
                }
                return { ...exercise, ...updates };
              }),
            }
      )
    );
  }

  function addExercise(phaseKey: string) {
    const draft: ExerciseDraft = {
      key: makeKey('ex'),
      exerciseId: '',
      variantIndex: 0,
      sets: 3,
      restBetweenSetsMs: DEFAULT_REST_BETWEEN_SETS_MS,
      restBetweenSetsPerSetMs: expandNumbers(undefined, 3, DEFAULT_REST_BETWEEN_SETS_MS),
      restAfterExerciseMs: 0,
      notes: { ...EMPTY_TEXT },
    };
    setPhases((current) =>
      current.map((phase) => (phase.key === phaseKey ? { ...phase, collapsed: false, exercises: [...phase.exercises, draft] } : phase))
    );
    setSelected({ kind: 'exercise', phaseKey, exerciseKey: draft.key });
  }

  function addRest(phaseKey: string) {
    const phase = phases.find((item) => item.key === phaseKey);
    if (!phase || phase.exercises.length === 0) {
      toast.error('Add an exercise before adding rest');
      return;
    }

    const selectedExerciseKey =
      (selected.kind === 'exercise' || selected.kind === 'rest') && selected.phaseKey === phaseKey ? selected.exerciseKey : undefined;
    const selectedExercise = selectedExerciseKey ? phase.exercises.find((exercise) => exercise.key === selectedExerciseKey) : undefined;
    let target = selectedExercise && selectedExercise.restAfterExerciseMs <= 0 ? selectedExercise : undefined;

    if (!target) {
      for (let index = phase.exercises.length - 1; index >= 0; index -= 1) {
        if (phase.exercises[index].restAfterExerciseMs <= 0) {
          target = phase.exercises[index];
          break;
        }
      }
    }

    if (!target) {
      toast.error('Every exercise already has a rest item');
      return;
    }

    updateExercise(phaseKey, target.key, { restAfterExerciseMs: 60000 });
    setSelected({ kind: 'rest', phaseKey, exerciseKey: target.key });
  }

  function removeExercise(phaseKey: string, exerciseKey: string) {
    setPhases((current) =>
      current.map((phase) =>
        phase.key === phaseKey ? { ...phase, exercises: phase.exercises.filter((exercise) => exercise.key !== exerciseKey) } : phase
      )
    );
    setSelected({ kind: 'phase', phaseKey });
  }

  function removeRest(phaseKey: string, exerciseKey: string) {
    updateExercise(phaseKey, exerciseKey, { restAfterExerciseMs: 0 });
    setSelected({ kind: 'phase', phaseKey });
  }

  function removePhase(phaseKey: string) {
    if (phases.length <= 1) {
      toast.error('A workout needs at least one phase');
      return;
    }
    const remaining = phases.filter((phase) => phase.key !== phaseKey);
    setPhases(remaining);
    setSelected({ kind: 'phase', phaseKey: remaining[0].key });
  }

  function addPhaseFromDialog() {
    const catalog = phaseCatalog.find((phase) => phase.id === selectedPhaseId);
    if (!catalog) return;
    const draft = createPhaseDraft(catalog);
    setPhases((current) => [...current, draft]);
    setSelected({ kind: 'phase', phaseKey: draft.key });
    setAddPhaseOpen(false);
  }

  function movePhase(phaseKey: string, direction: -1 | 1) {
    setPhases((current) => {
      const index = current.findIndex((phase) => phase.key === phaseKey);
      const nextIndex = index + direction;
      if (index < 0 || nextIndex < 0 || nextIndex >= current.length) return current;
      const next = [...current];
      [next[index], next[nextIndex]] = [next[nextIndex], next[index]];
      return next.map((phase, sortOrder) => ({ ...phase, sortOrder }));
    });
  }

  function moveExercise(phaseKey: string, exerciseKey: string, direction: -1 | 1) {
    setPhases((current) =>
      current.map((phase) => {
        if (phase.key !== phaseKey) return phase;
        const index = phase.exercises.findIndex((exercise) => exercise.key === exerciseKey);
        const nextIndex = index + direction;
        if (index < 0 || nextIndex < 0 || nextIndex >= phase.exercises.length) return phase;
        const exercisesNext = [...phase.exercises];
        const currentExercise = exercisesNext[index];
        const nextExercise = exercisesNext[nextIndex];
        const currentRest = exercisesNext[index].restAfterExerciseMs;
        const nextRest = exercisesNext[nextIndex].restAfterExerciseMs;
        exercisesNext[index] = { ...nextExercise, restAfterExerciseMs: currentRest };
        exercisesNext[nextIndex] = { ...currentExercise, restAfterExerciseMs: nextRest };
        return { ...phase, exercises: exercisesNext };
      })
    );
  }

  function moveRest(phaseKey: string, exerciseKey: string, direction: -1 | 1) {
    const phase = phases.find((item) => item.key === phaseKey);
    const index = phase?.exercises.findIndex((exercise) => exercise.key === exerciseKey) ?? -1;
    const nextIndex = index + direction;
    const nextExerciseKey = phase?.exercises[nextIndex]?.key;
    if (!phase || index < 0 || !nextExerciseKey) return;

    setPhases((current) =>
      current.map((phase) => {
        if (phase.key !== phaseKey) return phase;
        const exercisesNext = [...phase.exercises];
        const currentRest = exercisesNext[index].restAfterExerciseMs;
        if (currentRest <= 0) return phase;

        exercisesNext[index] = { ...exercisesNext[index], restAfterExerciseMs: exercisesNext[nextIndex].restAfterExerciseMs };
        exercisesNext[nextIndex] = { ...exercisesNext[nextIndex], restAfterExerciseMs: currentRest };
        return { ...phase, exercises: exercisesNext };
      })
    );

    setSelected({ kind: 'rest', phaseKey, exerciseKey: nextExerciseKey });
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const totalExercises = phases.reduce((sum, phase) => sum + phase.exercises.length, 0);
    if (totalExercises === 0) {
      toast.error('Add at least one exercise');
      return;
    }
    const hasUnselectedExercise = phases.some((phase) => phase.exercises.some((item) => !item.exerciseId));
    if (hasUnselectedExercise) {
      toast.error('Select an exercise for every workout item');
      return;
    }
    setSaving(true);
    try {
      const payload = {
        name,
        description: description.en || description.ar ? description : undefined,
        coverImageUrl: coverImageUrl || undefined,
        levelId: levelId || null,
        isFeatured,
        estimatedDurationMin: typeof estimatedDurationMin === 'number' ? estimatedDurationMin : undefined,
        tags: tags.split(',').map((tag) => tag.trim()).filter(Boolean),
        phases: phases.map((phase, phaseIndex) => ({
          id: phase.id,
          phaseId: phase.phaseId,
          sortOrder: phaseIndex,
          nameOverride: phase.nameOverride?.en || phase.nameOverride?.ar ? phase.nameOverride : undefined,
          canSkipOverride: phase.canSkipOverride,
          canContinueOverride: phase.canContinueOverride,
          maxContinueTimeMsOverride: phase.maxContinueTimeMsOverride,
          exercises: phase.exercises.map((exercise, exerciseIndex) => ({
            id: exercise.id,
            exerciseId: exercise.exerciseId,
            variantIndex: exercise.variantIndex,
            targetRepsPerSet:
              exercise.targetRepsPerSet && exercise.targetRepsPerSet.length > 0
                ? exercise.targetRepsPerSet
                : exercise.targetReps
                  ? [exercise.targetReps]
                  : undefined,
            targetDuration: exercise.targetDuration || undefined,
            sets: exercise.sets,
            restBetweenSetsPerSetMs:
              exercise.restBetweenSetsPerSetMs && exercise.restBetweenSetsPerSetMs.length > 0
                ? exercise.restBetweenSetsPerSetMs
                : [exercise.restBetweenSetsMs],
            restAfterExerciseMs: exercise.restAfterExerciseMs,
            weightPerSet:
              exerciseSupportsWeight(exercise.exercise, exercise.weightPerSet) && exercise.weightPerSet && exercise.weightPerSet.length > 0
                ? exercise.weightPerSet
                : undefined,
            notes: exercise.notes.en || exercise.notes.ar ? exercise.notes : undefined,
            sortOrder: exerciseIndex,
          })),
        })),
      };

      const url = mode === 'edit' && workoutId ? `/api/workout-templates/${workoutId}` : '/api/workout-templates';
      const res = await fetch(url, {
        method: mode === 'edit' ? 'PUT' : 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      const data = await res.json();
      if (data.success) {
        toast.success(mode === 'edit' ? 'Workout template updated' : 'Workout template created');
        router.push('/admin/workout-templates');
      } else {
        toast.error(data.errors?.join('\n') || data.error || 'Failed to save template');
      }
    } catch (error) {
      console.error('Error saving workout template:', error);
      toast.error('Failed to save template');
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return <div className="flex min-h-[420px] items-center justify-center text-sm text-muted-foreground">Loading template...</div>;
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-5 pb-10">
      <header className="flex flex-col gap-3 border-b pb-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">{mode === 'edit' ? 'Edit Workout Template' : 'New Workout Template'}</h1>
          <p className="mt-1 text-sm text-muted-foreground">{name.en || 'Untitled template'}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          {mode === 'edit' && <Badge variant={status === 'published' ? 'success' : 'warning'}>{status}</Badge>}
          <Button type="button" variant="outline" onClick={() => router.push('/admin/workout-templates')}>
            Cancel
          </Button>
          <Button type="submit" loading={saving}>
            <Save className="size-4" />
            Save
          </Button>
        </div>
      </header>

      <section className="rounded-xl border bg-background">
        <div className="border-b px-4 py-3">
          <h2 className="text-sm font-semibold">Template</h2>
        </div>
        <div className="space-y-5 p-4">
          <TemplateInspector
            name={name}
            setName={setName}
            description={description}
            setDescription={setDescription}
            coverImageUrl={coverImageUrl}
            setCoverImageUrl={setCoverImageUrl}
            levels={levels}
            levelId={levelId}
            setLevelId={setLevelId}
            estimatedDurationMin={estimatedDurationMin}
            setEstimatedDurationMin={setEstimatedDurationMin}
            tags={tags}
            setTags={setTags}
            isFeatured={isFeatured}
            setIsFeatured={setIsFeatured}
          />
        </div>
      </section>

      <div className="grid min-h-[620px] gap-5 xl:grid-cols-[minmax(420px,1fr)_420px]">
        <section className="min-w-0 rounded-xl border bg-background">
          <div className="flex flex-wrap items-center justify-between gap-3 border-b px-4 py-3">
            <div className="flex items-center gap-2">
              <h2 className="text-sm font-semibold">Workout flow</h2>
              <Badge variant="outline">{phases.length} phases</Badge>
            </div>
            <Button type="button" size="sm" variant="secondary" onClick={() => setAddPhaseOpen(true)}>
              <Plus className="size-4" />
              Add phase
            </Button>
          </div>

          <div className="space-y-3 p-4">
            {phases.map((phase, phaseIndex) => (
              <div
                key={phase.key}
                className={cn('rounded-lg border bg-card', selected.kind === 'phase' && selected.phaseKey === phase.key && 'ring-2 ring-primary/40')}
              >
                <div className="flex items-center gap-2 border-b px-3 py-2">
                  <button type="button" className="rounded p-1 hover:bg-accent" onClick={() => updatePhase(phase.key, { collapsed: !phase.collapsed })}>
                    {phase.collapsed ? <ChevronRight className="size-4" /> : <ChevronDown className="size-4" />}
                  </button>
                  <button type="button" className="min-w-0 flex-1 text-left" onClick={() => setSelected({ kind: 'phase', phaseKey: phase.key })}>
                    <span className="block truncate text-sm font-semibold">{phase.nameOverride?.en || phase.catalog?.name.en || 'Phase'}</span>
                    <span className="text-xs text-muted-foreground">{phase.catalog?.role || 'MAIN'} · {phase.exercises.length} exercises</span>
                  </button>
                  <Button type="button" size="icon" variant="ghost" onClick={() => movePhase(phase.key, -1)} disabled={phaseIndex === 0} aria-label="Move phase up">
                    <ArrowUp className="size-4" />
                  </Button>
                  <Button type="button" size="icon" variant="ghost" onClick={() => movePhase(phase.key, 1)} disabled={phaseIndex === phases.length - 1} aria-label="Move phase down">
                    <ArrowDown className="size-4" />
                  </Button>
                  <Button type="button" size="sm" variant="ghost" onClick={() => addExercise(phase.key)}>
                    <Plus className="size-4" />
                    Add Exercise
                  </Button>
                  <Button type="button" size="sm" variant="ghost" onClick={() => addRest(phase.key)}>
                    <Timer className="size-4" />
                    Add Rest
                  </Button>
                </div>

                {!phase.collapsed && (
                  <div className="space-y-2 p-3">
                    {phase.exercises.length === 0 ? (
                      <div className="rounded-lg border border-dashed py-7 text-center text-sm text-muted-foreground">No exercises</div>
                    ) : (
                      phase.exercises.map((exercise, exerciseIndex) => (
                        <div key={exercise.key}>
                          <div
                            role="button"
                            tabIndex={0}
                            onClick={() => setSelected({ kind: 'exercise', phaseKey: phase.key, exerciseKey: exercise.key })}
                            onKeyDown={(event) => {
                              if (event.key === 'Enter' || event.key === ' ') {
                                event.preventDefault();
                                setSelected({ kind: 'exercise', phaseKey: phase.key, exerciseKey: exercise.key });
                              }
                            }}
                            className={cn(
                              'cursor-pointer',
                              'flex w-full items-center gap-3 rounded-lg border bg-background px-3 py-2 text-left transition-colors hover:bg-accent',
                              selected.kind === 'exercise' && selected.exerciseKey === exercise.key && 'border-primary bg-primary/5'
                            )}
                          >
                            <Dumbbell className="size-4 text-muted-foreground" />
                            <span className="min-w-0 flex-1">
                              <span className="block truncate text-sm font-medium">{exerciseLabel(exercise.exercise)}</span>
                              <span className="text-xs text-muted-foreground">
                                {exercise.sets} sets - {exercise.targetDuration ? `${exercise.targetDuration}s` : `${formatPerSetNumbers(exercise.targetRepsPerSet) || exercise.targetReps || 0} reps`}
                              </span>
                            </span>
                            <Button type="button" size="icon" variant="ghost" onClick={(event) => { event.stopPropagation(); moveExercise(phase.key, exercise.key, -1); }} disabled={exerciseIndex === 0} aria-label="Move exercise up">
                              <ArrowUp className="size-4" />
                            </Button>
                            <Button type="button" size="icon" variant="ghost" onClick={(event) => { event.stopPropagation(); moveExercise(phase.key, exercise.key, 1); }} disabled={exerciseIndex === phase.exercises.length - 1} aria-label="Move exercise down">
                              <ArrowDown className="size-4" />
                            </Button>
                          </div>

                          {exercise.restAfterExerciseMs > 0 && (
                            <div
                              role="button"
                              tabIndex={0}
                              onClick={() => setSelected({ kind: 'rest', phaseKey: phase.key, exerciseKey: exercise.key })}
                              onKeyDown={(event) => {
                                if (event.key === 'Enter' || event.key === ' ') {
                                  event.preventDefault();
                                  setSelected({ kind: 'rest', phaseKey: phase.key, exerciseKey: exercise.key });
                                }
                              }}
                              className={cn(
                                'mt-2 flex w-full cursor-pointer items-center gap-3 rounded-lg border border-dashed bg-muted/30 px-3 py-2 text-left transition-colors hover:bg-accent',
                                selected.kind === 'rest' && selected.exerciseKey === exercise.key && 'border-primary bg-primary/5 text-foreground'
                              )}
                            >
                              <Timer className="size-4 text-muted-foreground" />
                              <span className="min-w-0 flex-1">
                                <span className="block text-sm font-medium">Rest</span>
                                <span className="text-xs text-muted-foreground">{Math.round(exercise.restAfterExerciseMs / 1000)} sec</span>
                              </span>
                              <Button type="button" size="icon" variant="ghost" onClick={(event) => { event.stopPropagation(); moveRest(phase.key, exercise.key, -1); }} disabled={exerciseIndex === 0} aria-label="Move rest up">
                                <ArrowUp className="size-4" />
                              </Button>
                              <Button type="button" size="icon" variant="ghost" onClick={(event) => { event.stopPropagation(); moveRest(phase.key, exercise.key, 1); }} disabled={exerciseIndex === phase.exercises.length - 1} aria-label="Move rest down">
                                <ArrowDown className="size-4" />
                              </Button>
                            </div>
                          )}
                        </div>
                      ))
                    )}
                  </div>
                )}
              </div>
            ))}
          </div>
        </section>

        <aside className="rounded-xl border bg-background">
          <div className="border-b px-4 py-3">
            <h2 className="text-sm font-semibold">
              {selected.kind === 'meta' && 'Inspector'}
              {selected.kind === 'phase' && 'Phase'}
              {selected.kind === 'exercise' && 'Exercise'}
              {selected.kind === 'rest' && 'Rest'}
            </h2>
          </div>
          <div className="space-y-5 p-4">
            {selected.kind === 'phase' && selectedPhase && (
              <PhaseInspector phase={selectedPhase} catalog={phaseCatalog} updatePhase={updatePhase} removePhase={removePhase} />
            )}

            {selected.kind === 'exercise' && selectedPhase && selectedExercise && (
              <ExerciseInspector
                phaseKey={selectedPhase.key}
                exercise={selectedExercise}
                exercises={exercises}
                categories={categories}
                familyOptions={familyOptions}
                onExercisesLoaded={handleExercisesLoaded}
                updateExercise={updateExercise}
                removeExercise={removeExercise}
              />
            )}

            {selected.kind === 'rest' && selectedPhase && selectedExercise && (
              <RestInspector phaseKey={selectedPhase.key} exercise={selectedExercise} updateExercise={updateExercise} removeRest={removeRest} />
            )}

            {selected.kind === 'meta' && (
              <p className="text-sm text-muted-foreground">Select a phase, exercise, or rest item to edit its details.</p>
            )}
          </div>
        </aside>
      </div>

      <Dialog open={addPhaseOpen} onOpenChange={setAddPhaseOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Add Phase</DialogTitle>
          </DialogHeader>
          <DialogBody>
            <Label>Phase preset</Label>
            <Select
              value={selectedPhaseId}
              onChange={(event) => setSelectedPhaseId(event.target.value)}
              options={phaseCatalog.map((phase) => ({ value: phase.id, label: `${phase.name.en} · ${phase.role}` }))}
            />
          </DialogBody>
          <DialogFooter>
            <Button type="button" variant="secondary" onClick={() => setAddPhaseOpen(false)}>
              Cancel
            </Button>
            <Button type="button" onClick={addPhaseFromDialog} disabled={!selectedPhaseId}>
              Add
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </form>
  );
}

function TemplateInspector(props: {
  name: LocalizedText;
  setName: (value: LocalizedText) => void;
  description: LocalizedText;
  setDescription: (value: LocalizedText) => void;
  coverImageUrl: string;
  setCoverImageUrl: (value: string) => void;
  levels: LevelSummary[];
  levelId: string;
  setLevelId: (value: string) => void;
  estimatedDurationMin: number | '';
  setEstimatedDurationMin: (value: number | '') => void;
  tags: string;
  setTags: (value: string) => void;
  isFeatured: boolean;
  setIsFeatured: (value: boolean) => void;
}) {
  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 gap-4">
        <div>
          <Label required>Name (English)</Label>
          <Input value={props.name.en} onChange={(event) => props.setName({ ...props.name, en: event.target.value })} required />
        </div>
        <div>
          <Label required>Name (Arabic)</Label>
          <Input dir="rtl" value={props.name.ar} onChange={(event) => props.setName({ ...props.name, ar: event.target.value })} required />
        </div>
      </div>
      <div className="grid grid-cols-2 gap-4">
        <div>
          <Label>Description (English)</Label>
          <Textarea rows={4} value={props.description.en} onChange={(event) => props.setDescription({ ...props.description, en: event.target.value })} />
        </div>
        <div>
          <Label>Description (Arabic)</Label>
          <Textarea dir="rtl" rows={4} value={props.description.ar} onChange={(event) => props.setDescription({ ...props.description, ar: event.target.value })} />
        </div>
      </div>
      <div className="grid grid-cols-2 gap-4">
        <div>
          <Label>Cover URL</Label>
          <Input value={props.coverImageUrl} onChange={(event) => props.setCoverImageUrl(event.target.value)} placeholder="https://..." />
        </div>
        <FileUpload label="Template cover" value={props.coverImageUrl} onChange={props.setCoverImageUrl} uploadType="workout-image" accept="image/*" />
      </div>
      <div className="grid grid-cols-2 gap-4">
        <div>
          <Label>Level</Label>
          <Select
            value={props.levelId}
            onChange={(event) => props.setLevelId(event.target.value)}
            options={[
              { value: '', label: 'No level' },
              ...props.levels.map((level) => ({
                value: level.id,
                label: `Level ${level.number} — ${level.name.en || level.name.ar || level.code}`,
              })),
            ]}
          />
        </div>
        <div>
          <Label>Duration (min)</Label>
          <Input type="number" min={0} value={props.estimatedDurationMin} onChange={(event) => props.setEstimatedDurationMin(event.target.value ? Number(event.target.value) : '')} />
        </div>
      </div>
      <div className="grid grid-cols-2 gap-4">
        <div>
          <Label>Tags</Label>
          <Input value={props.tags} onChange={(event) => props.setTags(event.target.value)} placeholder="upper-body, no-equipment" />
        </div>
        <label className="flex items-center gap-2 self-end rounded-lg border px-3 py-2 text-sm">
          <Checkbox checked={props.isFeatured} onCheckedChange={(checked) => props.setIsFeatured(Boolean(checked))} />
          Featured
        </label>
      </div>
    </div>
  );
}

function PhaseInspector({
  phase,
  catalog,
  updatePhase,
  removePhase,
}: {
  phase: PhaseDraft;
  catalog: WorkoutPhaseCatalog[];
  updatePhase: (phaseKey: string, updates: Partial<PhaseDraft>) => void;
  removePhase: (phaseKey: string) => void;
}) {
  const inheritedName = phase.catalog?.name || { ...EMPTY_TEXT };
  return (
    <>
      <div>
        <Label>Catalog preset</Label>
        <Select
          value={phase.phaseId}
          onChange={(event) => {
            const nextCatalog = catalog.find((item) => item.id === event.target.value);
            updatePhase(phase.key, { phaseId: event.target.value, catalog: nextCatalog });
          }}
          options={catalog.map((item) => ({ value: item.id, label: `${item.name.en} · ${item.role}` }))}
        />
      </div>
      <div className="grid grid-cols-2 gap-4">
        <div>
          <Label>Name override (English)</Label>
          <Input
            value={phase.nameOverride?.en || ''}
            placeholder={inheritedName.en}
            onChange={(event) => updatePhase(phase.key, { nameOverride: { ...(phase.nameOverride || EMPTY_TEXT), en: event.target.value } })}
          />
        </div>
        <div>
          <Label>Name override (Arabic)</Label>
          <Input
            dir="rtl"
            value={phase.nameOverride?.ar || ''}
            placeholder={inheritedName.ar}
            onChange={(event) => updatePhase(phase.key, { nameOverride: { ...(phase.nameOverride || EMPTY_TEXT), ar: event.target.value } })}
          />
        </div>
      </div>
      <div className="grid gap-2">
        <label className="flex items-center gap-2 rounded-lg border px-3 py-2 text-sm">
          <Checkbox
            checked={phase.canSkipOverride ?? phase.catalog?.canSkip ?? false}
            onCheckedChange={(checked) => updatePhase(phase.key, { canSkipOverride: Boolean(checked) })}
          />
          Can skip
        </label>
        <label className="flex items-center gap-2 rounded-lg border px-3 py-2 text-sm">
          <Checkbox
            checked={phase.canContinueOverride ?? phase.catalog?.canContinue ?? true}
            onCheckedChange={(checked) => updatePhase(phase.key, { canContinueOverride: Boolean(checked) })}
          />
          Can continue
        </label>
      </div>
      <div>
        <Label>Max continue window (sec)</Label>
        <Input
          type="number"
          min={0}
          value={msToSeconds(phase.maxContinueTimeMsOverride ?? phase.catalog?.maxContinueTimeMs)}
          onChange={(event) => updatePhase(phase.key, { maxContinueTimeMsOverride: event.target.value ? Number(event.target.value) * 1000 : null })}
        />
      </div>
      <Button type="button" variant="destructive" onClick={() => removePhase(phase.key)}>
        <Trash2 className="size-4" />
        Delete phase
      </Button>
    </>
  );
}

function ExercisePicker({
  value,
  selectedExercise,
  cachedExercises,
  categories,
  familyOptions,
  onLoaded,
  onChange,
}: {
  value: string;
  selectedExercise?: Exercise;
  cachedExercises: Exercise[];
  categories: CategorySummary[];
  familyOptions: string[];
  onLoaded: (items: Exercise[]) => void;
  onChange: (exercise: Exercise) => void;
}) {
  const [open, setOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [familyKey, setFamilyKey] = useState('');
  const [results, setResults] = useState<Exercise[]>(cachedExercises);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedSearch(searchQuery.trim()), 250);
    return () => window.clearTimeout(timer);
  }, [searchQuery]);

  useEffect(() => {
    if (!open) return;

    let cancelled = false;
    async function loadExercises() {
      setLoading(true);
      try {
        const response = await exercisesService.list({
          status: 'published',
          limit: 50,
          search: debouncedSearch,
          categoryId,
          familyKey,
        });
        if (cancelled) return;
        const items = (response.data || []) as Exercise[];
        setResults(items);
        onLoaded(items);
      } catch (error) {
        if (!cancelled) {
          console.error('Error searching exercises:', error);
          toast.error('Failed to search exercises');
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    void loadExercises();
    return () => {
      cancelled = true;
    };
  }, [open, debouncedSearch, categoryId, familyKey, onLoaded]);

  function handleOpenChange(nextOpen: boolean) {
    if (nextOpen) {
      setSearchQuery('');
      if (selectedExercise) {
        setCategoryId(selectedExercise.categoryId || selectedExercise.category?.id || '');
        setFamilyKey(selectedExercise.familyKey || '');
      } else {
        setCategoryId('');
        setFamilyKey('');
      }
    }
    setOpen(nextOpen);
  }

  const availableFamilies = useMemo(
    () =>
      [...new Set([...familyOptions, ...results.map((exercise) => exercise.familyKey).filter((item): item is string => Boolean(item))])]
        .sort((a, b) => a.localeCompare(b)),
    [familyOptions, results]
  );

  const selectedLabel = selectedExercise
    ? `${selectedExercise.name.en || selectedExercise.slug}${selectedExercise.name.ar ? ` (${selectedExercise.name.ar})` : ''}`
    : 'Select exercise';
  return (
    <Popover.Root open={open} onOpenChange={handleOpenChange} modal={false}>
      <Popover.Trigger asChild>
        <button
          type="button"
          className={cn(
            'flex w-full items-center justify-between rounded-lg border bg-background px-3 py-2 text-left text-sm shadow-sm',
            'focus:outline-none focus:ring-2 focus:ring-ring'
          )}
        >
          <span className={cn('truncate', value ? 'text-foreground' : 'text-muted-foreground')}>{selectedLabel}</span>
          <ChevronDown className="size-4 shrink-0 text-muted-foreground" />
        </button>
      </Popover.Trigger>
      <Popover.Portal>
        <Popover.Content
          align="start"
          sideOffset={4}
          onOpenAutoFocus={(event) => event.preventDefault()}
          className="z-[100] w-[var(--radix-popover-trigger-width)] rounded-xl border bg-background p-3 shadow-xl"
        >
          <div className="space-y-3">
            <div className="relative">
              <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                value={searchQuery}
                onChange={(event) => setSearchQuery(event.target.value)}
                placeholder="Search exercise..."
                className={cn('pl-9', searchQuery && 'pr-9')}
                autoComplete="off"
              />
              {searchQuery && (
                <button
                  type="button"
                  className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-1 text-muted-foreground hover:bg-muted hover:text-foreground"
                  onClick={() => setSearchQuery('')}
                  aria-label="Clear search"
                >
                  <X className="size-4" />
                </button>
              )}
            </div>

            <div className="grid grid-cols-2 gap-2">
              <div className="flex items-center gap-1">
                <div className="min-w-0 flex-1">
                  <Select
                    value={categoryId}
                    onChange={(event) => setCategoryId(event.target.value)}
                    options={[
                      { value: '', label: 'All categories' },
                      ...categories.map((category) => ({
                        value: category.id,
                        label: category.name.en || category.name.ar || category.code,
                      })),
                    ]}
                  />
                </div>
                {categoryId && (
                  <Button type="button" variant="ghost" size="icon" className="shrink-0" onClick={() => setCategoryId('')} aria-label="Clear category filter">
                    <X className="size-4" />
                  </Button>
                )}
              </div>
              <div className="flex items-center gap-1">
                <div className="min-w-0 flex-1">
                  <Select
                    value={familyKey}
                    onChange={(event) => setFamilyKey(event.target.value)}
                    options={[
                      { value: '', label: 'All families' },
                      ...availableFamilies.map((family) => ({ value: family, label: family })),
                    ]}
                  />
                </div>
                {familyKey && (
                  <Button type="button" variant="ghost" size="icon" className="shrink-0" onClick={() => setFamilyKey('')} aria-label="Clear family filter">
                    <X className="size-4" />
                  </Button>
                )}
              </div>
            </div>

            <div className="max-h-72 overflow-y-auto rounded-lg border">
              {loading ? (
                <div className="px-3 py-4 text-center text-sm text-muted-foreground">Loading exercises...</div>
              ) : results.length === 0 ? (
                <div className="px-3 py-4 text-center text-sm text-muted-foreground">No exercises found</div>
              ) : (
                results.map((item) => {
                  const label = item.name.en || item.name.ar || item.slug;
                  return (
                    <button
                      key={item.id}
                      type="button"
                      className={cn(
                        'w-full border-b px-3 py-2 text-left text-sm transition-colors last:border-b-0 hover:bg-muted',
                        item.id === value && 'bg-muted font-medium'
                      )}
                      onClick={() => {
                        onLoaded([item]);
                        onChange(item);
                        setOpen(false);
                      }}
                    >
                      <span className="block truncate">{label}</span>
                      <span className="mt-1 flex flex-wrap gap-1 text-xs text-muted-foreground">
                        {item.name.ar && <span dir="rtl">{item.name.ar}</span>}
                        {item.category?.name?.en && <span>· {item.category.name.en}</span>}
                        {item.familyKey && <span>· {item.familyKey}</span>}
                      </span>
                    </button>
                  );
                })
              )}
            </div>
          </div>
        </Popover.Content>
      </Popover.Portal>
    </Popover.Root>
  );
}

function ExerciseInspector({
  phaseKey,
  exercise,
  exercises,
  categories,
  familyOptions,
  onExercisesLoaded,
  updateExercise,
  removeExercise,
}: {
  phaseKey: string;
  exercise: ExerciseDraft;
  exercises: Exercise[];
  categories: CategorySummary[];
  familyOptions: string[];
  onExercisesLoaded: (items: Exercise[]) => void;
  updateExercise: (phaseKey: string, exerciseKey: string, updates: Partial<ExerciseDraft>) => void;
  removeExercise: (phaseKey: string, exerciseKey: string) => void;
}) {
  const hasExercise = Boolean(exercise.exerciseId);
  const selectedExercise = hasExercise
    ? mergeExerciseMetadata(exercise.exercise, exercises.find((item) => item.id === exercise.exerciseId))
    : undefined;
  const isHold = selectedExercise?.countingMethod?.code === 'hold';
  const supportsWeight = exerciseSupportsWeight(selectedExercise, exercise.weightPerSet);
  const setCount = normalizeSetCount(exercise.sets);
  const defaultWeight = selectedExercise?.defaultWeight ?? undefined;
  const repsPerSet = expandNumbers(exercise.targetRepsPerSet, setCount, exercise.targetReps ?? DEFAULT_TARGET_REPS);
  const restBetweenSets = expandNumbers(
    exercise.restBetweenSetsPerSetMs,
    setCount,
    exercise.restBetweenSetsMs ?? DEFAULT_REST_BETWEEN_SETS_MS
  );
  const weightPerSet = supportsWeight ? expandNumbers(exercise.weightPerSet, setCount, defaultWeight) : undefined;

  function updateSetCount(nextCount: number) {
    const nextRest = expandNumbers(
      exercise.restBetweenSetsPerSetMs,
      nextCount,
      exercise.restBetweenSetsMs ?? DEFAULT_REST_BETWEEN_SETS_MS
    );
    const updates: Partial<ExerciseDraft> = {
      sets: nextCount,
      restBetweenSetsMs: nextRest?.[0] ?? DEFAULT_REST_BETWEEN_SETS_MS,
      restBetweenSetsPerSetMs: nextRest,
      restBetweenSetsPerSetText: undefined,
    };

    if (isHold) {
      updates.targetDuration = exercise.targetDuration || DEFAULT_TARGET_DURATION_SEC;
      updates.targetReps = undefined;
      updates.targetRepsPerSet = undefined;
      updates.targetRepsPerSetText = undefined;
    } else {
      const nextReps = expandNumbers(exercise.targetRepsPerSet, nextCount, exercise.targetReps ?? DEFAULT_TARGET_REPS);
      updates.targetRepsPerSet = nextReps;
      updates.targetReps = nextReps?.[0] ?? DEFAULT_TARGET_REPS;
      updates.targetRepsPerSetText = undefined;
      updates.targetDuration = undefined;
    }

    updates.weightPerSet = supportsWeight ? expandNumbers(exercise.weightPerSet, nextCount, defaultWeight) : undefined;
    updates.weightPerSetText = undefined;
    updateExercise(phaseKey, exercise.key, updates);
  }

  return (
    <>
      <div>
        <Label>Exercise</Label>
        <ExercisePicker
          value={exercise.exerciseId}
          selectedExercise={selectedExercise}
          cachedExercises={exercises}
          categories={categories}
          familyOptions={familyOptions}
          onLoaded={onExercisesLoaded}
          onChange={(nextExercise) => updateExercise(phaseKey, exercise.key, { exerciseId: nextExercise.id, exercise: nextExercise })}
        />
      </div>
      {!hasExercise && (
        <p className="text-sm text-muted-foreground">Choose an exercise to configure sets, weight, and rest.</p>
      )}
      {hasExercise && selectedExercise?.poseVariants && selectedExercise.poseVariants.length > 1 && (
        <div>
          <Label>Variant</Label>
          <Select
            value={String(exercise.variantIndex)}
            onChange={(event) => updateExercise(phaseKey, exercise.key, { variantIndex: Number(event.target.value) || 0 })}
            options={selectedExercise.poseVariants.map((variant, index) => ({ value: String(index), label: variant.name.en || variant.name.ar || `Variant ${index + 1}` }))}
          />
        </div>
      )}
      {hasExercise && <div className="rounded-lg border">
        <div className="flex items-center justify-between gap-3 border-b px-3 py-2">
          <div>
            <Label>Sets</Label>
            <p className="text-xs text-muted-foreground">Edit each set target, weight, and rest.</p>
          </div>
          <Input
            className="w-20"
            type="number"
            min={1}
            value={setCount}
            onChange={(event) => updateSetCount(normalizeSetCount(event.target.value))}
          />
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-muted/40 text-xs text-muted-foreground">
              <tr>
                <th className="px-3 py-2 text-left font-medium">Set</th>
                <th className="px-3 py-2 text-left font-medium">{isHold ? 'Duration (sec)' : 'Reps'}</th>
                {supportsWeight && <th className="px-3 py-2 text-left font-medium">Weight (kg)</th>}
                <th className="px-3 py-2 text-left font-medium">Rest (sec)</th>
              </tr>
            </thead>
            <tbody>
              {Array.from({ length: setCount }, (_, index) => {
                const restSeconds = Math.round((restBetweenSets?.[index] ?? DEFAULT_REST_BETWEEN_SETS_MS) / 1000);
                return (
                  <tr key={index} className="border-t">
                    <td className="whitespace-nowrap px-3 py-2 font-medium text-muted-foreground">Set {index + 1}</td>
                    <td className="px-3 py-2">
                      <Input
                        type="number"
                        min={0}
                        value={isHold ? (exercise.targetDuration ?? DEFAULT_TARGET_DURATION_SEC) : (repsPerSet?.[index] ?? DEFAULT_TARGET_REPS)}
                        onChange={(event) => {
                          if (isHold) {
                            updateExercise(phaseKey, exercise.key, {
                              targetDuration: Math.max(0, Math.round(Number(event.target.value) || DEFAULT_TARGET_DURATION_SEC)),
                            });
                            return;
                          }

                          const nextReps = updateExpandedNumber(
                            repsPerSet,
                            index,
                            setCount,
                            event.target.value,
                            DEFAULT_TARGET_REPS,
                            { integer: true }
                          );
                          updateExercise(phaseKey, exercise.key, {
                            targetRepsPerSet: nextReps,
                            targetReps: nextReps?.[0],
                            targetRepsPerSetText: undefined,
                          });
                        }}
                        inputMode="numeric"
                      />
                    </td>
                    {supportsWeight && (
                      <td className="px-3 py-2">
                        <Input
                          type="number"
                          min={0}
                          step="0.5"
                          value={weightPerSet?.[index] ?? ''}
                          onChange={(event) => {
                            updateExercise(phaseKey, exercise.key, {
                              weightPerSet: updateExpandedNumber(weightPerSet, index, setCount, event.target.value, defaultWeight),
                              weightPerSetText: undefined,
                            });
                          }}
                          inputMode="decimal"
                        />
                      </td>
                    )}
                    <td className="px-3 py-2">
                      <Input
                        type="number"
                        min={0}
                        value={restSeconds}
                        onChange={(event) => {
                          const restSecondsPerSet = updateExpandedNumber(
                            restBetweenSets?.map((value) => Math.round(value / 1000)),
                            index,
                            setCount,
                            event.target.value,
                            DEFAULT_REST_BETWEEN_SETS_SEC
                          );
                          const restMsPerSet = restSecondsPerSet?.map((value) => Math.max(0, Math.round(value * 1000)));
                          updateExercise(phaseKey, exercise.key, {
                            restBetweenSetsPerSetMs: restMsPerSet,
                            restBetweenSetsMs: restMsPerSet?.[0] ?? DEFAULT_REST_BETWEEN_SETS_MS,
                            restBetweenSetsPerSetText: undefined,
                          });
                        }}
                        inputMode="numeric"
                      />
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>}
      {hasExercise && (
        <div className="grid grid-cols-2 gap-4">
          <div>
            <Label>Note (English)</Label>
            <Input value={exercise.notes.en} onChange={(event) => updateExercise(phaseKey, exercise.key, { notes: { ...exercise.notes, en: event.target.value } })} />
          </div>
          <div>
            <Label>Note (Arabic)</Label>
            <Input dir="rtl" value={exercise.notes.ar} onChange={(event) => updateExercise(phaseKey, exercise.key, { notes: { ...exercise.notes, ar: event.target.value } })} />
          </div>
        </div>
      )}
      <Button type="button" variant="destructive" onClick={() => removeExercise(phaseKey, exercise.key)}>
        <Trash2 className="size-4" />
        Delete exercise
      </Button>
    </>
  );
}

function RestInspector({
  phaseKey,
  exercise,
  updateExercise,
  removeRest,
}: {
  phaseKey: string;
  exercise: ExerciseDraft;
  updateExercise: (phaseKey: string, exerciseKey: string, updates: Partial<ExerciseDraft>) => void;
  removeRest: (phaseKey: string, exerciseKey: string) => void;
}) {
  return (
    <>
      <div>
        <Label>Rest duration (sec)</Label>
        <Input
          type="number"
          min={0}
          value={Math.round(exercise.restAfterExerciseMs / 1000)}
          onChange={(event) => updateExercise(phaseKey, exercise.key, { restAfterExerciseMs: secondsToMs(event.target.value) ?? 0 })}
        />
      </div>
      <Button type="button" variant="destructive" onClick={() => removeRest(phaseKey, exercise.key)}>
        <Trash2 className="size-4" />
        Delete rest
      </Button>
    </>
  );
}
