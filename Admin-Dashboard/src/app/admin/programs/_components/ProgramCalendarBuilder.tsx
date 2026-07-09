'use client';

import { Dispatch, SetStateAction, useMemo, useState } from 'react';
import { toast } from 'sonner';
import {
  ArrowDown,
  ArrowUp,
  Copy,
  Dumbbell,
  Moon,
  Plus,
  Timer,
  Trash2,
  Activity,
  X,
} from 'lucide-react';
import { Badge, Button, Input, Label, Select, Textarea, SearchableSelect } from '@/components/ui';
import type { LocalizedText } from '@/lib/types/localized';
import { cn } from '@/lib/utils';
import {
  type DayForm,
  type PlannedWorkoutForm,
  type PlannedWorkoutItemForm,
  type ProgramDayType,
  type WeekForm,
  clonePlannedWorkout,
  cloneWeek,
  createEmptyItem,
  createEmptyPlannedWorkout,
  createEmptyWeek,
  deriveDayTypeFields,
  normalizePlannedWorkout,
  normalizeWeek,
} from '../_lib/program-calendar';

interface Option {
  value: string;
  label: string;
}

interface ExerciseRef {
  id: string;
}

interface ExerciseAttributeCheckResult {
  status: string;
  messages: string[];
}

interface WorkoutTemplateDetails {
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

interface MuscleAttributeValue {
  id: string;
  code: string;
  name: LocalizedText;
}

const DAY_TYPE_META: Record<
  ProgramDayType,
  { label: string; icon: typeof Dumbbell; activeClass: string; mutedClass: string }
> = {
  training: {
    label: 'Training',
    icon: Dumbbell,
    activeClass: 'border-primary bg-primary text-primary-foreground',
    mutedClass: 'border-primary/30 bg-primary/5 text-primary',
  },
  rest: {
    label: 'Rest',
    icon: Moon,
    activeClass: 'border-slate-600 bg-slate-600 text-white shadow-sm',
    mutedClass: 'border-slate-200 bg-white text-slate-500 hover:bg-slate-50',
  },
  active_recovery: {
    label: 'Active Recovery',
    icon: Activity,
    activeClass: 'border-emerald-700 bg-emerald-700 text-white shadow-sm',
    mutedClass: 'border-emerald-200 bg-emerald-50 text-emerald-600',
  },
};

interface ProgramCalendarBuilderProps {
  weeks: WeekForm[];
  setWeeks: Dispatch<SetStateAction<WeekForm[]>>;
  durationWeeks: number;
  setDurationWeeks: Dispatch<SetStateAction<number>>;
  exercises: ExerciseRef[];
  exerciseOptions: Option[];
  workoutOptions: Option[];
  exerciseLabelById: Map<string, string>;
  muscleOptions: MuscleAttributeValue[];
  loadingMuscleOptions: boolean;
  loadingExercises: boolean;
  loadingWorkoutTemplates: boolean;
  exerciseAttributeCheck: (exerciseId: string | undefined) => ExerciseAttributeCheckResult | null;
}

type Selected =
  | { kind: 'day' }
  | { kind: 'plannedWorkout'; pwIndex: number }
  | { kind: 'item'; pwIndex: number; itemIndex: number };

export function ProgramCalendarBuilder({
  weeks,
  setWeeks,
  durationWeeks,
  setDurationWeeks,
  exercises,
  exerciseOptions,
  workoutOptions,
  exerciseLabelById,
  muscleOptions,
  loadingMuscleOptions,
  loadingExercises,
  loadingWorkoutTemplates,
  exerciseAttributeCheck,
}: ProgramCalendarBuilderProps) {
  const [weekTab, setWeekTab] = useState(0);
  const [dayTabByWeek, setDayTabByWeek] = useState<Record<number, number>>({});
  const [selected, setSelected] = useState<Selected>({ kind: 'day' });

  const activeWeekIndex = Math.min(weekTab, Math.max(0, weeks.length - 1));
  const activeWeek = weeks[activeWeekIndex];
  const activeDayIndex = Math.min(
    dayTabByWeek[activeWeekIndex] ?? 0,
    Math.max(0, (activeWeek?.days.length ?? 1) - 1),
  );
  const activeDay = activeWeek?.days[activeDayIndex];

  const selectWeek = (index: number) => {
    setWeekTab(index);
    setSelected({ kind: 'day' });
  };

  const selectDay = (index: number) => {
    setDayTabByWeek((prev) => ({ ...prev, [activeWeekIndex]: index }));
    setSelected({ kind: 'day' });
  };

  const muscleLabelById = useMemo(
    () => new Map(muscleOptions.map((m) => [m.id, m.name.en || m.name.ar || m.code])),
    [muscleOptions],
  );

  // ---- Week / day / planned workout / item mutations -----------------------
  const updateWeek = (weekIndex: number, updates: Partial<WeekForm>) => {
    setWeeks((prev) => prev.map((week, index) => (index === weekIndex ? { ...week, ...updates } : week)));
  };

  const updateDay = (weekIndex: number, dayIndex: number, updates: Partial<DayForm>) => {
    setWeeks((prev) =>
      prev.map((week, wIndex) => {
        if (wIndex !== weekIndex) return week;
        const days = week.days.map((day, dIndex) => (dIndex === dayIndex ? { ...day, ...updates } : day));
        return { ...week, days };
      }),
    );
  };

  const updatePlannedWorkout = (
    weekIndex: number,
    dayIndex: number,
    plannedWorkoutIndex: number,
    updates: Partial<PlannedWorkoutForm>,
  ) => {
    setWeeks((prev) =>
      prev.map((week, wIndex) => {
        if (wIndex !== weekIndex) return week;
        const days = week.days.map((day, dIndex) => {
          if (dIndex !== dayIndex) return day;
          const nextPlannedWorkouts = day.plannedWorkouts.map((plannedWorkout, sIndex) =>
            sIndex === plannedWorkoutIndex ? { ...plannedWorkout, ...updates } : plannedWorkout,
          );
          return { ...day, plannedWorkouts: nextPlannedWorkouts };
        });
        return { ...week, days };
      }),
    );
  };

  const updateItem = (
    weekIndex: number,
    dayIndex: number,
    plannedWorkoutIndex: number,
    itemIndex: number,
    updates: Partial<PlannedWorkoutItemForm>,
  ) => {
    setWeeks((prev) =>
      prev.map((week, wIndex) => {
        if (wIndex !== weekIndex) return week;
        const days = week.days.map((day, dIndex) => {
          if (dIndex !== dayIndex) return day;
          const nextPlannedWorkouts = day.plannedWorkouts.map((plannedWorkout, sIndex) => {
            if (sIndex !== plannedWorkoutIndex) return plannedWorkout;
            const items = plannedWorkout.items.map((item, iIndex) =>
              iIndex === itemIndex ? { ...item, ...updates } : item,
            );
            return { ...plannedWorkout, items };
          });
          return { ...day, plannedWorkouts: nextPlannedWorkouts };
        });
        return { ...week, days };
      }),
    );
  };

  const addWeek = () => {
    const nextWeekNumber = weeks.length + 1;
    setWeeks((prev) => [...prev, createEmptyWeek(nextWeekNumber)].map(normalizeWeek));
    setDurationWeeks(nextWeekNumber);
    setWeekTab(nextWeekNumber - 1);
    setSelected({ kind: 'day' });
  };

  const removeWeek = (index: number) => {
    if (weeks.length === 1) return;
    setWeeks((prev) => prev.filter((_, wIndex) => wIndex !== index).map(normalizeWeek));
    setDurationWeeks(Math.max(1, weeks.length - 1));
    setWeekTab((prev) => Math.max(0, Math.min(prev, weeks.length - 2)));
    setSelected({ kind: 'day' });
  };

  const duplicateWeek = (weekIndex: number) => {
    setWeeks((prev) => {
      const next = [...prev];
      next.splice(weekIndex + 1, 0, cloneWeek(prev[weekIndex]));
      return next.map(normalizeWeek);
    });
    setDurationWeeks(weeks.length + 1);
    setWeekTab(weekIndex + 1);
    setSelected({ kind: 'day' });
  };

  const addPlannedWorkout = (weekIndex: number, dayIndex: number) => {
    const newIndex = activeDay?.plannedWorkouts.length ?? 0;
    setWeeks((prev) =>
      prev.map((week, wIndex) => {
        if (wIndex !== weekIndex) return week;
        const days = week.days.map((day, dIndex) => {
          if (dIndex !== dayIndex) return day;
          return {
            ...day,
            plannedWorkouts: [
              ...day.plannedWorkouts,
              createEmptyPlannedWorkout(day.plannedWorkouts.length),
            ].map(normalizePlannedWorkout),
          };
        });
        return { ...week, days };
      }),
    );
    setSelected({ kind: 'plannedWorkout', pwIndex: newIndex });
  };

  const removePlannedWorkout = (weekIndex: number, dayIndex: number, plannedWorkoutIndex: number) => {
    setWeeks((prev) =>
      prev.map((week, wIndex) => {
        if (wIndex !== weekIndex) return week;
        const days = week.days.map((day, dIndex) => {
          if (dIndex !== dayIndex) return day;
          return {
            ...day,
            plannedWorkouts: day.plannedWorkouts
              .filter((_, sIndex) => sIndex !== plannedWorkoutIndex)
              .map(normalizePlannedWorkout),
          };
        });
        return { ...week, days };
      }),
    );
    setSelected({ kind: 'day' });
  };

  const duplicatePlannedWorkout = (weekIndex: number, dayIndex: number, plannedWorkoutIndex: number) => {
    setWeeks((prev) =>
      prev.map((week, wIndex) => {
        if (wIndex !== weekIndex) return week;
        const days = week.days.map((day, dIndex) => {
          if (dIndex !== dayIndex) return day;
          const nextPlannedWorkouts = [...day.plannedWorkouts];
          nextPlannedWorkouts.splice(
            plannedWorkoutIndex + 1,
            0,
            clonePlannedWorkout(day.plannedWorkouts[plannedWorkoutIndex]),
          );
          return { ...day, plannedWorkouts: nextPlannedWorkouts.map(normalizePlannedWorkout) };
        });
        return { ...week, days };
      }),
    );
    setSelected({ kind: 'plannedWorkout', pwIndex: plannedWorkoutIndex + 1 });
  };

  const movePlannedWorkout = (
    weekIndex: number,
    dayIndex: number,
    plannedWorkoutIndex: number,
    direction: -1 | 1,
  ) => {
    const target = plannedWorkoutIndex + direction;
    setWeeks((prev) =>
      prev.map((week, wIndex) => {
        if (wIndex !== weekIndex) return week;
        const days = week.days.map((day, dIndex) => {
          if (dIndex !== dayIndex) return day;
          if (target < 0 || target >= day.plannedWorkouts.length) return day;
          const next = [...day.plannedWorkouts];
          [next[plannedWorkoutIndex], next[target]] = [next[target], next[plannedWorkoutIndex]];
          return { ...day, plannedWorkouts: next.map(normalizePlannedWorkout) };
        });
        return { ...week, days };
      }),
    );
    if (target >= 0 && target < (activeDay?.plannedWorkouts.length ?? 0)) {
      setSelected({ kind: 'plannedWorkout', pwIndex: target });
    }
  };

  const addItem = (
    weekIndex: number,
    dayIndex: number,
    plannedWorkoutIndex: number,
    type: 'exercise' | 'rest',
  ) => {
    const firstExerciseId = exercises[0]?.id;
    const newItemIndex = activeDay?.plannedWorkouts[plannedWorkoutIndex]?.items.length ?? 0;
    setWeeks((prev) =>
      prev.map((week, wIndex) => {
        if (wIndex !== weekIndex) return week;
        const days = week.days.map((day, dIndex) => {
          if (dIndex !== dayIndex) return day;
          const nextPlannedWorkouts = day.plannedWorkouts.map((plannedWorkout, sIndex) => {
            if (sIndex !== plannedWorkoutIndex) return plannedWorkout;
            return {
              ...plannedWorkout,
              items: [
                ...plannedWorkout.items,
                createEmptyItem(type, type === 'exercise' ? firstExerciseId : undefined),
              ],
            };
          });
          return { ...day, plannedWorkouts: nextPlannedWorkouts };
        });
        return { ...week, days };
      }),
    );
    setSelected({ kind: 'item', pwIndex: plannedWorkoutIndex, itemIndex: newItemIndex });
  };

  const removeItem = (
    weekIndex: number,
    dayIndex: number,
    plannedWorkoutIndex: number,
    itemIndex: number,
  ) => {
    setWeeks((prev) =>
      prev.map((week, wIndex) => {
        if (wIndex !== weekIndex) return week;
        const days = week.days.map((day, dIndex) => {
          if (dIndex !== dayIndex) return day;
          const nextPlannedWorkouts = day.plannedWorkouts.map((plannedWorkout, sIndex) => {
            if (sIndex !== plannedWorkoutIndex) return plannedWorkout;
            return {
              ...plannedWorkout,
              items: plannedWorkout.items.filter((_, iIndex) => iIndex !== itemIndex),
            };
          });
          return { ...day, plannedWorkouts: nextPlannedWorkouts };
        });
        return { ...week, days };
      }),
    );
    setSelected({ kind: 'plannedWorkout', pwIndex: plannedWorkoutIndex });
  };

  const moveItem = (
    weekIndex: number,
    dayIndex: number,
    plannedWorkoutIndex: number,
    itemIndex: number,
    direction: -1 | 1,
  ) => {
    const target = itemIndex + direction;
    setWeeks((prev) =>
      prev.map((week, wIndex) => {
        if (wIndex !== weekIndex) return week;
        const days = week.days.map((day, dIndex) => {
          if (dIndex !== dayIndex) return day;
          const nextPlannedWorkouts = day.plannedWorkouts.map((plannedWorkout, sIndex) => {
            if (sIndex !== plannedWorkoutIndex) return plannedWorkout;
            if (target < 0 || target >= plannedWorkout.items.length) return plannedWorkout;
            const items = [...plannedWorkout.items];
            [items[itemIndex], items[target]] = [items[target], items[itemIndex]];
            return { ...plannedWorkout, items };
          });
          return { ...day, plannedWorkouts: nextPlannedWorkouts };
        });
        return { ...week, days };
      }),
    );
    const itemCount = activeDay?.plannedWorkouts[plannedWorkoutIndex]?.items.length ?? 0;
    if (target >= 0 && target < itemCount) {
      setSelected({ kind: 'item', pwIndex: plannedWorkoutIndex, itemIndex: target });
    }
  };

  const importWorkoutTemplate = async (
    weekIndex: number,
    dayIndex: number,
    plannedWorkoutIndex: number,
    workoutId: string,
  ) => {
    try {
      const res = await fetch(`/api/workout-templates/${workoutId}`);
      const data = await res.json();
      if (!data.success || !data.data) return;
      const workout: WorkoutTemplateDetails = data.data;

      const items: PlannedWorkoutItemForm[] = [];
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
        });

        if (exercise.restAfterExerciseMs > 0) {
          items.push({
            type: 'rest',
            sets: 1,
            restBetweenSetsMs: 0,
            weightPerSetText: '',
            notes: { ar: '', en: '' },
            restDurationMs: exercise.restAfterExerciseMs,
          });
        }
      });

      setWeeks((prev) =>
        prev.map((week, wIndex) => {
          if (wIndex !== weekIndex) return week;
          const days = week.days.map((day, dIndex) => {
            if (dIndex !== dayIndex) return day;
            const nextPlannedWorkouts = day.plannedWorkouts.map((plannedWorkout, sIndex) => {
              if (sIndex !== plannedWorkoutIndex) return plannedWorkout;
              return { ...plannedWorkout, items: [...plannedWorkout.items, ...items] };
            });
            return { ...day, plannedWorkouts: nextPlannedWorkouts };
          });
          return { ...week, days };
        }),
      );
      toast.success('Workout template imported');
    } catch (error) {
      console.error('Error importing workout:', error);
      toast.error('Failed to import workout template');
    }
  };

  // ---- Summaries -----------------------------------------------------------
  const getPlannedWorkoutSummary = (plannedWorkout: PlannedWorkoutForm) => {
    const exerciseCount = plannedWorkout.items.filter((item) => item.type === 'exercise').length;
    const restCount = plannedWorkout.items.length - exerciseCount;
    return `${plannedWorkout.items.length} item(s) • ${exerciseCount} exercise(s)${restCount ? ` • ${restCount} rest` : ''}`;
  };

  const getItemSummary = (item: PlannedWorkoutItemForm) => {
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

  const calendarStructureWarnings = useMemo(() => {
    const messages: string[] = [];
    if (durationWeeks !== weeks.length) {
      messages.push(
        `Duration is set to ${durationWeeks} week(s), but the builder currently contains ${weeks.length} week block(s).`,
      );
    }
    weeks.forEach((week, wi) => {
      if (week.days.length < 1) {
        messages.push(`Week ${wi + 1}: add at least one day before publishing.`);
      }
      const hasTrainingDay = week.days.some((d) => d.dayType === 'training');
      if (!hasTrainingDay) {
        messages.push(`Week ${wi + 1}: add at least one training day before publishing.`);
      }
    });
    return messages;
  }, [durationWeeks, weeks]);

  const selectedPlannedWorkout =
    selected.kind === 'plannedWorkout' || selected.kind === 'item'
      ? activeDay?.plannedWorkouts[selected.pwIndex]
      : undefined;
  const selectedItem =
    selected.kind === 'item' ? selectedPlannedWorkout?.items[selected.itemIndex] : undefined;

  if (!activeWeek || !activeDay) {
    return null;
  }

  const isTrainingDay = activeDay.dayType === 'training';
  const showInspectorAside = selected.kind !== 'day';

  return (
    <div className="space-y-5">
      {calendarStructureWarnings.length > 0 ? (
        <div className="space-y-1 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900">
          <p className="font-medium">Calendar structure (hints — save is not blocked)</p>
          <ul className="list-disc space-y-1 pl-5">
            {calendarStructureWarnings.map((msg) => (
              <li key={msg}>{msg}</li>
            ))}
          </ul>
        </div>
      ) : null}

      {/* Week navigator */}
      <section className="rounded-xl border bg-background">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b px-4 py-3">
          <div className="flex items-center gap-2">
            <h2 className="text-sm font-semibold">Weeks</h2>
            <Badge variant="outline">{weeks.length} weeks</Badge>
          </div>
          <div className="flex items-center gap-2">
            <Button type="button" size="sm" variant="ghost" onClick={() => duplicateWeek(activeWeekIndex)}>
              <Copy className="size-4" />
              Duplicate
            </Button>
            <Button
              type="button"
              size="sm"
              variant="ghost"
              onClick={() => removeWeek(activeWeekIndex)}
              disabled={weeks.length === 1}
            >
              <Trash2 className="size-4" />
              Remove
            </Button>
            <Button type="button" size="sm" variant="secondary" onClick={addWeek}>
              <Plus className="size-4" />
              Add week
            </Button>
          </div>
        </div>

        <div className="flex flex-wrap gap-2 border-b px-4 py-3">
          {weeks.map((week, index) => (
            <button
              key={week.id ?? `week-${index}`}
              type="button"
              onClick={() => selectWeek(index)}
              className={cn(
                'flex items-center gap-2 rounded-lg border px-3 py-1.5 text-sm font-medium transition-colors',
                index === activeWeekIndex
                  ? 'border-primary bg-primary/10 text-primary'
                  : 'border-transparent bg-muted text-muted-foreground hover:bg-accent',
              )}
              title={week.target.en || week.target.ar || undefined}
            >
              Week {index + 1}
            </button>
          ))}
        </div>

        {/* Active week settings */}
        <div className="grid grid-cols-1 gap-4 p-4 md:grid-cols-2">
          <div>
            <Label>Target (EN)</Label>
            <Input
              value={activeWeek.target.en}
              onChange={(e) => updateWeek(activeWeekIndex, { target: { ...activeWeek.target, en: e.target.value } })}
              placeholder="e.g. Hypertrophy block"
            />
          </div>
          <div>
            <Label>Target (AR)</Label>
            <Input
              dir="rtl"
              value={activeWeek.target.ar}
              onChange={(e) => updateWeek(activeWeekIndex, { target: { ...activeWeek.target, ar: e.target.value } })}
            />
          </div>
          <div className="md:col-span-2">
            <Label>Description (EN)</Label>
            <Textarea
              rows={2}
              value={activeWeek.description.en}
              onChange={(e) =>
                updateWeek(activeWeekIndex, { description: { ...activeWeek.description, en: e.target.value } })
              }
            />
          </div>
          <div className="md:col-span-2">
            <Label>Description (AR)</Label>
            <Textarea
              rows={2}
              dir="rtl"
              value={activeWeek.description.ar}
              onChange={(e) =>
                updateWeek(activeWeekIndex, { description: { ...activeWeek.description, ar: e.target.value } })
              }
            />
          </div>
        </div>

        {/* Day navigator — fixed 7 days */}
        <div className="border-t px-4 py-3">
          <div className="mb-2 flex items-center justify-between">
            <span className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
              Days (7-day cycle)
            </span>
          </div>
          <div className="grid grid-cols-7 gap-2">
            {activeWeek.days.map((day, di) => {
              const meta = DAY_TYPE_META[day.dayType];
              const Icon = meta.icon;
              const isActive = di === activeDayIndex;
              return (
                <button
                  key={`day-tab-${activeWeekIndex}-${di}`}
                  type="button"
                  onClick={() => selectDay(di)}
                  className={cn(
                    'flex flex-col items-center gap-1 rounded-lg border px-1 py-2 text-center transition-colors',
                    isActive ? meta.activeClass : meta.mutedClass,
                    isActive && 'ring-2 ring-offset-1',
                    isActive && day.dayType === 'training' && 'ring-primary',
                    isActive && day.dayType === 'rest' && 'ring-slate-600',
                    isActive && day.dayType === 'active_recovery' && 'ring-emerald-700',
                  )}
                  title={meta.label}
                >
                  <span className={cn('text-[10px] font-semibold uppercase', !isActive && 'opacity-70')}>
                    D{di + 1}
                  </span>
                  <Icon className="size-4" />
                  <span className="text-[10px] leading-tight">{meta.label}</span>
                </button>
              );
            })}
          </div>
        </div>
      </section>

      {/* Day status banner */}
      <DayStatusBanner
        day={activeDay}
        dayIndex={activeDayIndex}
        muscleOptions={muscleOptions}
        loadingMuscleOptions={loadingMuscleOptions}
        muscleLabelById={muscleLabelById}
        onChange={(updates) => updateDay(activeWeekIndex, activeDayIndex, updates)}
      />

      {/* Day flow + inspector */}
      <div
        className={cn(
          'grid min-h-[520px] gap-5',
          showInspectorAside && 'xl:grid-cols-[minmax(380px,1fr)_420px]',
        )}
      >
        {/* Flow */}
        <section className="min-w-0 rounded-xl border bg-background">
          <div className="flex flex-wrap items-center justify-between gap-3 border-b px-4 py-3">
            <div className="flex items-center gap-2">
              <h2 className="text-sm font-semibold">
                Week {activeWeekIndex + 1} · Day {activeDayIndex + 1} flow
              </h2>
              <Badge variant="outline">{activeDay.plannedWorkouts.length} planned</Badge>
            </div>
            <Button
              type="button"
              size="sm"
              variant="secondary"
              onClick={() => addPlannedWorkout(activeWeekIndex, activeDayIndex)}
              disabled={!isTrainingDay}
            >
              <Plus className="size-4" />
              Add Planned Workout
            </Button>
          </div>

          <div className="space-y-3 p-4">
            {!isTrainingDay ? (
              <div className="rounded-lg border border-dashed bg-muted/30 py-10 text-center">
                <p className="text-sm font-medium text-muted-foreground">
                  This is a {activeDay.dayType === 'rest' ? 'rest' : 'active recovery'} day.
                </p>
                <p className="mt-1 text-xs text-muted-foreground">
                  Switch the day type to <span className="font-semibold">Training</span> above to add workouts.
                </p>
              </div>
            ) : null}

            {activeDay.plannedWorkouts.map((plannedWorkout, pwIndex) => (
              <div key={plannedWorkout.id ?? `pw-${pwIndex}`} className="rounded-lg border bg-card">
                <div
                  className={cn(
                    'flex items-center gap-2 border-b px-3 py-2',
                    selected.kind === 'plannedWorkout' && selected.pwIndex === pwIndex && 'bg-primary/5',
                  )}
                >
                  <button
                    type="button"
                    className="min-w-0 flex-1 text-left"
                    onClick={() => setSelected({ kind: 'plannedWorkout', pwIndex })}
                  >
                    <span className="block truncate text-sm font-semibold">
                      {plannedWorkout.name.en || plannedWorkout.name.ar || `Planned Workout ${pwIndex + 1}`}
                    </span>
                    <span className="text-xs text-muted-foreground">{getPlannedWorkoutSummary(plannedWorkout)}</span>
                  </button>
                  <Button
                    type="button"
                    size="icon"
                    variant="ghost"
                    onClick={() => movePlannedWorkout(activeWeekIndex, activeDayIndex, pwIndex, -1)}
                    disabled={pwIndex === 0}
                    aria-label="Move planned workout up"
                  >
                    <ArrowUp className="size-4" />
                  </Button>
                  <Button
                    type="button"
                    size="icon"
                    variant="ghost"
                    onClick={() => movePlannedWorkout(activeWeekIndex, activeDayIndex, pwIndex, 1)}
                    disabled={pwIndex === activeDay.plannedWorkouts.length - 1}
                    aria-label="Move planned workout down"
                  >
                    <ArrowDown className="size-4" />
                  </Button>
                  <Button
                    type="button"
                    size="sm"
                    variant="ghost"
                    onClick={() => addItem(activeWeekIndex, activeDayIndex, pwIndex, 'exercise')}
                    disabled={loadingExercises || exercises.length === 0}
                  >
                    <Dumbbell className="size-4" />
                    Exercise
                  </Button>
                  <Button
                    type="button"
                    size="sm"
                    variant="ghost"
                    onClick={() => addItem(activeWeekIndex, activeDayIndex, pwIndex, 'rest')}
                  >
                    <Timer className="size-4" />
                    Rest
                  </Button>
                </div>

                <div className="space-y-2 p-3">
                  {plannedWorkout.items.length === 0 ? (
                    <div className="rounded-lg border border-dashed py-6 text-center text-sm text-muted-foreground">
                      No items yet — add an exercise or rest.
                    </div>
                  ) : (
                    plannedWorkout.items.map((item, itemIndex) => {
                      const isSelected =
                        selected.kind === 'item' &&
                        selected.pwIndex === pwIndex &&
                        selected.itemIndex === itemIndex;
                      return (
                        <div
                          key={item.id ?? `item-${itemIndex}`}
                          role="button"
                          tabIndex={0}
                          onClick={() => setSelected({ kind: 'item', pwIndex, itemIndex })}
                          onKeyDown={(event) => {
                            if (event.key === 'Enter' || event.key === ' ') {
                              event.preventDefault();
                              setSelected({ kind: 'item', pwIndex, itemIndex });
                            }
                          }}
                          className={cn(
                            'flex w-full cursor-pointer items-center gap-3 rounded-lg border bg-background px-3 py-2 text-left transition-colors hover:bg-accent',
                            item.type === 'rest' && 'border-dashed bg-muted/30',
                            isSelected && 'border-primary bg-primary/5',
                          )}
                        >
                          {item.type === 'exercise' ? (
                            <Dumbbell className="size-4 text-muted-foreground" />
                          ) : (
                            <Timer className="size-4 text-muted-foreground" />
                          )}
                          <span className="min-w-0 flex-1">
                            <span className="block truncate text-sm font-medium">
                              {item.type === 'exercise'
                                ? (item.exerciseId ? exerciseLabelById.get(item.exerciseId) : null) || 'Exercise'
                                : 'Rest'}
                            </span>
                            <span className="text-xs text-muted-foreground">{getItemSummary(item)}</span>
                          </span>
                          <Button
                            type="button"
                            size="icon"
                            variant="ghost"
                            onClick={(event) => {
                              event.stopPropagation();
                              moveItem(activeWeekIndex, activeDayIndex, pwIndex, itemIndex, -1);
                            }}
                            disabled={itemIndex === 0}
                            aria-label="Move item up"
                          >
                            <ArrowUp className="size-4" />
                          </Button>
                          <Button
                            type="button"
                            size="icon"
                            variant="ghost"
                            onClick={(event) => {
                              event.stopPropagation();
                              moveItem(activeWeekIndex, activeDayIndex, pwIndex, itemIndex, 1);
                            }}
                            disabled={itemIndex === plannedWorkout.items.length - 1}
                            aria-label="Move item down"
                          >
                            <ArrowDown className="size-4" />
                          </Button>
                        </div>
                      );
                    })
                  )}
                </div>
              </div>
            ))}
          </div>
        </section>

        {/* Inspector */}
        {showInspectorAside ? (
        <aside className="rounded-xl border bg-background">
          <div className="border-b px-4 py-3">
            <h2 className="text-sm font-semibold">
              {selected.kind === 'plannedWorkout' && 'Planned workout'}
              {selected.kind === 'item' && (selectedItem?.type === 'rest' ? 'Rest' : 'Exercise')}
            </h2>
          </div>
          <div className="space-y-5 p-4">
            {selected.kind === 'plannedWorkout' && selectedPlannedWorkout && (
              <PlannedWorkoutInspector
                key={`pw-${activeWeekIndex}-${activeDayIndex}-${selected.pwIndex}`}
                plannedWorkout={selectedPlannedWorkout}
                workoutOptions={workoutOptions}
                loadingWorkoutTemplates={loadingWorkoutTemplates}
                onChange={(updates) =>
                  updatePlannedWorkout(activeWeekIndex, activeDayIndex, selected.pwIndex, updates)
                }
                onImport={(workoutId) =>
                  importWorkoutTemplate(activeWeekIndex, activeDayIndex, selected.pwIndex, workoutId)
                }
                onDuplicate={() => duplicatePlannedWorkout(activeWeekIndex, activeDayIndex, selected.pwIndex)}
                onRemove={() => removePlannedWorkout(activeWeekIndex, activeDayIndex, selected.pwIndex)}
                canRemove={activeDay.plannedWorkouts.length > 1}
              />
            )}

            {selected.kind === 'item' && selectedItem && (
              <ItemInspector
                key={`item-${activeWeekIndex}-${activeDayIndex}-${selected.pwIndex}-${selected.itemIndex}`}
                item={selectedItem}
                exerciseOptions={exerciseOptions}
                exerciseAttributeCheck={exerciseAttributeCheck}
                onChange={(updates) =>
                  updateItem(activeWeekIndex, activeDayIndex, selected.pwIndex, selected.itemIndex, updates)
                }
                onRemove={() =>
                  removeItem(activeWeekIndex, activeDayIndex, selected.pwIndex, selected.itemIndex)
                }
              />
            )}
          </div>
        </aside>
        ) : null}
      </div>
    </div>
  );
}

function DayStatusBanner({
  day,
  dayIndex,
  muscleOptions,
  loadingMuscleOptions,
  muscleLabelById,
  onChange,
}: {
  day: DayForm;
  dayIndex: number;
  muscleOptions: MuscleAttributeValue[];
  loadingMuscleOptions: boolean;
  muscleLabelById: Map<string, string>;
  onChange: (updates: Partial<DayForm>) => void;
}) {
  const meta = DAY_TYPE_META[day.dayType];
  const Icon = meta.icon;
  const isTraining = day.dayType === 'training';
  const showMuscles = isTraining || day.dayType === 'active_recovery';

  const handleDayTypeChange = (value: string) => {
    const dayType = value as ProgramDayType;
    const updates = deriveDayTypeFields(dayType);
    if (dayType !== 'training' && day.plannedWorkouts.length > 0) {
      if (
        !window.confirm(
          `Switching to ${dayType === 'rest' ? 'Rest' : 'Active Recovery'} will keep existing workouts but they won't be shown to the trainee on this day. Continue?`,
        )
      ) {
        return;
      }
    }
    onChange(updates);
  };

  const addMuscle = (muscleId: string) => {
    if (!muscleId || day.targetMuscleIds.includes(muscleId)) return;
    onChange({ targetMuscleIds: [...day.targetMuscleIds, muscleId] });
  };

  const removeMuscle = (muscleId: string) => {
    onChange({ targetMuscleIds: day.targetMuscleIds.filter((id) => id !== muscleId) });
  };

  const availableMuscleOptions = muscleOptions
    .filter((m) => !day.targetMuscleIds.includes(m.id))
    .map((m) => ({
      value: m.id,
      label: m.name.en || m.name.ar || m.code,
    }));

  return (
    <div className="space-y-2">
      <div className="rounded-xl border bg-background px-4 py-3">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div className="flex min-w-0 items-center gap-3">
            <div
              className={cn(
                'flex size-10 shrink-0 items-center justify-center rounded-lg border',
                meta.activeClass,
              )}
            >
              <Icon className="size-5" />
            </div>
            <div className="min-w-0">
              <div className="text-sm font-semibold">Day {dayIndex + 1}</div>
              <div className="text-xs text-muted-foreground">
                {isTraining
                  ? `${day.plannedWorkouts.length} planned workout(s) · ${day.plannedWorkouts.reduce((a, p) => a + p.items.length, 0)} item(s)`
                  : meta.label + ' — no workouts'}
              </div>
            </div>
          </div>

          <div className="grid w-full grid-cols-1 gap-4 sm:w-auto sm:grid-cols-2 sm:gap-4">
            <div className="w-full sm:w-[180px]">
              <Label className="mb-1.5 block text-xs font-medium text-muted-foreground">Type</Label>
              <Select
                value={day.dayType}
                onChange={(e) => handleDayTypeChange(e.target.value)}
                options={[
                  { value: 'training', label: 'Training' },
                  { value: 'rest', label: 'Rest' },
                  { value: 'active_recovery', label: 'Active recovery' },
                ]}
              />
            </div>
            {showMuscles ? (
              <div className="w-full sm:w-[180px]">
                <Label className="mb-1.5 block text-xs font-medium text-muted-foreground">Target muscles</Label>
                <SearchableSelect
                  value=""
                  onChange={(value) => {
                    if (value) addMuscle(value);
                  }}
                  options={availableMuscleOptions}
                  placeholder={loadingMuscleOptions ? 'Loading muscles...' : 'Add muscle'}
                  searchPlaceholder="Search muscles..."
                  disabled={loadingMuscleOptions}
                />
              </div>
            ) : null}
          </div>
        </div>
      </div>

      {showMuscles && day.targetMuscleIds.length > 0 ? (
        <div className="flex flex-wrap gap-1.5 px-0.5">
          {day.targetMuscleIds.map((muscleId) => (
            <Badge key={muscleId} variant="secondary" className="gap-1 pr-1 font-normal">
              {muscleLabelById.get(muscleId) ?? muscleId}
              <button
                type="button"
                onClick={() => removeMuscle(muscleId)}
                className="rounded-sm p-0.5 hover:bg-muted-foreground/20"
                aria-label={`Remove ${muscleLabelById.get(muscleId) ?? muscleId}`}
              >
                <X className="size-3" />
              </button>
            </Badge>
          ))}
        </div>
      ) : null}
    </div>
  );
}

function PlannedWorkoutInspector({
  plannedWorkout,
  workoutOptions,
  loadingWorkoutTemplates,
  onChange,
  onImport,
  onDuplicate,
  onRemove,
  canRemove,
}: {
  plannedWorkout: PlannedWorkoutForm;
  workoutOptions: Option[];
  loadingWorkoutTemplates: boolean;
  onChange: (updates: Partial<PlannedWorkoutForm>) => void;
  onImport: (workoutId: string) => void;
  onDuplicate: () => void;
  onRemove: () => void;
  canRemove: boolean;
}) {
  return (
    <>
      <div className="grid grid-cols-2 gap-4">
        <div>
          <Label>Name (EN)</Label>
          <Input
            value={plannedWorkout.name.en}
            onChange={(e) => onChange({ name: { ...plannedWorkout.name, en: e.target.value } })}
          />
        </div>
        <div>
          <Label>Name (AR)</Label>
          <Input
            dir="rtl"
            value={plannedWorkout.name.ar}
            onChange={(e) => onChange({ name: { ...plannedWorkout.name, ar: e.target.value } })}
          />
        </div>
      </div>
      <div>
        <Label>Estimated duration (min)</Label>
        <Input
          type="number"
          min={1}
          value={plannedWorkout.estimatedDurationMin ?? ''}
          onChange={(e) =>
            onChange({
              estimatedDurationMin:
                e.target.value === '' ? undefined : Number.parseInt(e.target.value, 10) || undefined,
            })
          }
        />
      </div>
      <div>
        <Label>Import workout template</Label>
        <SearchableSelect
          value=""
          onChange={(value) => {
            if (value) onImport(value);
          }}
          options={workoutOptions}
          placeholder={loadingWorkoutTemplates ? 'Loading workout templates...' : 'Import workout template'}
          searchPlaceholder="Search workout templates..."
        />
        <p className="mt-1 text-xs text-muted-foreground">Appends the template&apos;s exercises and rests to this workout.</p>
      </div>
      <div className="flex flex-wrap gap-2 border-t pt-4">
        <Button type="button" variant="secondary" size="sm" onClick={onDuplicate}>
          <Copy className="size-4" />
          Duplicate
        </Button>
        <Button type="button" variant="destructive" size="sm" onClick={onRemove} disabled={!canRemove}>
          <Trash2 className="size-4" />
          Remove
        </Button>
      </div>
    </>
  );
}

function ItemInspector({
  item,
  exerciseOptions,
  exerciseAttributeCheck,
  onChange,
  onRemove,
}: {
  item: PlannedWorkoutItemForm;
  exerciseOptions: Option[];
  exerciseAttributeCheck: (exerciseId: string | undefined) => ExerciseAttributeCheckResult | null;
  onChange: (updates: Partial<PlannedWorkoutItemForm>) => void;
  onRemove: () => void;
}) {
  const check = item.type === 'exercise' ? exerciseAttributeCheck(item.exerciseId) : null;
  return (
    <>
      <div>
        <Label>Type</Label>
        <Select
          value={item.type}
          onChange={(e) => onChange({ type: e.target.value as 'exercise' | 'rest' })}
          options={[
            { value: 'exercise', label: 'Exercise' },
            { value: 'rest', label: 'Rest' },
          ]}
        />
      </div>

      {item.type === 'exercise' ? (
        <>
          <div>
            <Label>Exercise</Label>
            <SearchableSelect
              value={item.exerciseId || ''}
              onChange={(value) => onChange({ exerciseId: value })}
              options={exerciseOptions}
              placeholder="Select exercise"
              searchPlaceholder="Search exercises..."
            />
            {check && check.status !== 'ok' ? (
              <div
                className={cn(
                  'mt-2 rounded px-2 py-1.5 text-xs',
                  check.status === 'red'
                    ? 'border border-red-200 bg-red-50 text-red-900'
                    : 'border border-amber-200 bg-amber-50 text-amber-950',
                )}
              >
                {check.messages.join(' · ')}
              </div>
            ) : null}
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <Label>Sets</Label>
              <Input
                type="number"
                min={1}
                value={item.sets}
                onChange={(e) => onChange({ sets: Number.parseInt(e.target.value, 10) || 1 })}
              />
            </div>
            <div>
              <Label>Rest Between Sets (ms)</Label>
              <Input
                type="number"
                min={0}
                value={item.restBetweenSetsMs}
                onChange={(e) => onChange({ restBetweenSetsMs: Number.parseInt(e.target.value, 10) || 0 })}
              />
            </div>
            <div>
              <Label>Target Reps</Label>
              <Input
                type="number"
                min={1}
                value={item.targetReps || ''}
                onChange={(e) => onChange({ targetReps: Number.parseInt(e.target.value, 10) || undefined })}
              />
            </div>
            <div>
              <Label>Target Duration (sec)</Label>
              <Input
                type="number"
                min={1}
                value={item.targetDuration || ''}
                onChange={(e) => onChange({ targetDuration: Number.parseInt(e.target.value, 10) || undefined })}
              />
            </div>
            <div>
              <Label>Weight (kg)</Label>
              <Input
                type="number"
                min={0}
                value={item.weightKg || ''}
                onChange={(e) => onChange({ weightKg: Number.parseFloat(e.target.value) || undefined })}
              />
            </div>
            <div>
              <Label>Weight Per Set</Label>
              <Input
                value={item.weightPerSetText}
                onChange={(e) => onChange({ weightPerSetText: e.target.value })}
                placeholder="10, 12.5, 15"
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <Label>Notes (EN)</Label>
              <Input
                value={item.notes.en}
                onChange={(e) => onChange({ notes: { ...item.notes, en: e.target.value } })}
              />
            </div>
            <div>
              <Label>Notes (AR)</Label>
              <Input
                dir="rtl"
                value={item.notes.ar}
                onChange={(e) => onChange({ notes: { ...item.notes, ar: e.target.value } })}
              />
            </div>
          </div>
        </>
      ) : (
        <div>
          <Label>Rest Duration (ms)</Label>
          <Input
            type="number"
            min={0}
            value={item.restDurationMs || 0}
            onChange={(e) => onChange({ restDurationMs: Number.parseInt(e.target.value, 10) || 0 })}
          />
        </div>
      )}

      <Button type="button" variant="destructive" size="sm" onClick={onRemove}>
        <Trash2 className="size-4" />
        Remove item
      </Button>
    </>
  );
}
