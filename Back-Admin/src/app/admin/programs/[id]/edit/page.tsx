'use client';

import { useEffect, useMemo, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { Input, Select, Label, Button, Card, Textarea } from '@/components/ui';
import type { LocalizedText } from '@/lib/types/localized';

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

interface SessionForm {
  name: LocalizedText;
  sortOrder: number;
  items: SessionItemForm[];
}

interface DayForm {
  dayNumber: number;
  isRestDay: boolean;
  name: LocalizedText;
  sessions: SessionForm[];
}

interface WeekForm {
  weekNumber: number;
  name: LocalizedText;
  description: LocalizedText;
  sortOrder: number;
  days: DayForm[];
}

interface ProgramResponse {
  id: string;
  name: LocalizedText;
  description: LocalizedText | null;
  coverImageUrl: string | null;
  durationWeeks: number;
  difficulty: 'beginner' | 'intermediate' | 'advanced';
  tags: string[] | null;
  weeks: Array<{
    weekNumber: number;
    name?: LocalizedText;
    description?: LocalizedText;
    sortOrder: number;
    days: Array<{
      dayNumber: number;
      isRestDay: boolean;
      name?: LocalizedText;
      sessions: Array<{
        name: LocalizedText;
        sortOrder: number;
        items: Array<{
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
  sessions: [createEmptySession(0)],
});

const createEmptyWeek = (weekNumber: number): WeekForm => ({
  weekNumber,
  name: { ar: '', en: '' },
  description: { ar: '', en: '' },
  sortOrder: weekNumber - 1,
  days: [createEmptyDay(1)],
});

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
  const [difficulty, setDifficulty] = useState<'beginner' | 'intermediate' | 'advanced'>('beginner');
  const [tags, setTags] = useState('');
  const [weeks, setWeeks] = useState<WeekForm[]>([createEmptyWeek(1)]);

  const [exercises, setExercises] = useState<ExerciseSummary[]>([]);
  const [workouts, setWorkouts] = useState<WorkoutSummary[]>([]);

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
        setName(program.name);
        setDescription(program.description || { ar: '', en: '' });
        setCoverImageUrl(program.coverImageUrl || '');
        setDurationWeeks(program.durationWeeks);
        setDifficulty(program.difficulty);
        setTags((program.tags || []).join(', '));

        const mappedWeeks: WeekForm[] =
          program.weeks?.map((week, weekIndex) => ({
            weekNumber: week.weekNumber || weekIndex + 1,
            name: week.name || { ar: '', en: '' },
            description: week.description || { ar: '', en: '' },
            sortOrder: week.sortOrder ?? weekIndex,
            days:
              week.days?.map((day, dayIndex) => ({
                dayNumber: day.dayNumber || dayIndex + 1,
                isRestDay: day.isRestDay || false,
                name: day.name || { ar: '', en: '' },
                sessions:
                  day.sessions?.map((session, sessionIndex) => ({
                    name: session.name || { ar: '', en: '' },
                    sortOrder: session.sortOrder ?? sessionIndex,
                    items:
                      session.items?.map((item) => ({
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
    setWeeks((prev) => [...prev, createEmptyWeek(nextWeekNumber)]);
    setDurationWeeks((prev) => Math.max(prev, nextWeekNumber));
  };

  const removeWeek = (index: number) => {
    setWeeks((prev) => prev.filter((_, wIndex) => wIndex !== index));
  };

  const addDay = (weekIndex: number) => {
    setWeeks((prev) =>
      prev.map((week, wIndex) => {
        if (wIndex !== weekIndex) return week;
        const nextDayNumber = week.days.length + 1;
        return { ...week, days: [...week.days, createEmptyDay(nextDayNumber)] };
      })
    );
  };

  const removeDay = (weekIndex: number, dayIndex: number) => {
    setWeeks((prev) =>
      prev.map((week, wIndex) => {
        if (wIndex !== weekIndex) return week;
        return { ...week, days: week.days.filter((_, dIndex) => dIndex !== dayIndex) };
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
            sessions: [...day.sessions, createEmptySession(day.sessions.length)],
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
          return { ...day, sessions: day.sessions.filter((_, sIndex) => sIndex !== sessionIndex) };
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
    weeks: weeks.map((week, weekIndex) => ({
      weekNumber: week.weekNumber || weekIndex + 1,
      name: week.name.en || week.name.ar ? week.name : undefined,
      description: week.description.en || week.description.ar ? week.description : undefined,
      sortOrder: week.sortOrder ?? weekIndex,
      days: week.days.map((day, dayIndex) => ({
        dayNumber: day.dayNumber || dayIndex + 1,
        isRestDay: day.isRestDay,
        name: day.name.en || day.name.ar ? day.name : undefined,
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
          })),
        })),
      })),
    })),
  });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);

    try {
      const res = await fetch(`/api/programs/${programId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(buildPayload()),
      });

      const data = await res.json();
      if (data.success) {
        router.push('/admin/programs');
      } else {
        alert(data.errors?.join('\n') || data.error || 'Failed to update program');
      }
    } catch (error) {
      console.error('Error updating program:', error);
      alert('Failed to update program');
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
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Edit Program</h1>
        <p className="text-gray-600 mt-1">Update training program</p>
      </div>

      <form onSubmit={handleSubmit} className="space-y-6">
        <Card className="p-6">
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

        <Card className="p-6">
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

          <div className="grid grid-cols-2 gap-4 mt-4">
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
                placeholder="weight-loss, beginner"
              />
            </div>
          </div>
        </Card>

        <Card className="p-6 space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-lg font-semibold">Program Builder</h2>
              <p className="text-sm text-gray-500">Build weeks, days, sessions, and items</p>
            </div>
            <Button type="button" variant="outline" onClick={addWeek}>
              Add Week
            </Button>
          </div>

          {weeks.map((week, weekIndex) => (
            <Card key={`week-${weekIndex}`} className="p-4 space-y-4 border border-gray-200">
              <div className="flex items-center justify-between">
                <h3 className="text-base font-semibold">Week {weekIndex + 1}</h3>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => removeWeek(weekIndex)}
                  disabled={weeks.length === 1}
                >
                  Remove Week
                </Button>
              </div>

              <div className="grid grid-cols-3 gap-4">
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
                <Card key={`day-${dayIndex}`} className="p-4 space-y-4 border border-gray-100">
                  <div className="flex items-center justify-between">
                    <h5 className="text-sm font-semibold">Day {dayIndex + 1}</h5>
                    <Button
                      type="button"
                      variant="outline"
                      onClick={() => removeDay(weekIndex, dayIndex)}
                      disabled={week.days.length === 1}
                    >
                      Remove Day
                    </Button>
                  </div>

                  <div className="grid grid-cols-4 gap-4">
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
                    <Card key={`session-${sessionIndex}`} className="p-4 space-y-4 border border-gray-100">
                      <div className="flex items-center justify-between">
                        <span className="text-sm font-semibold">Session {sessionIndex + 1}</span>
                        <Button
                          type="button"
                          variant="outline"
                          onClick={() => removeSession(weekIndex, dayIndex, sessionIndex)}
                          disabled={day.sessions.length === 1}
                        >
                          Remove Session
                        </Button>
                      </div>

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
                          <Select
                            value=""
                            onChange={(e) => {
                              const workoutId = e.target.value;
                              if (workoutId) {
                                importWorkout(weekIndex, dayIndex, sessionIndex, workoutId);
                              }
                            }}
                            options={[
                              { value: '', label: loadingWorkouts ? 'Loading workouts...' : 'Import Workout' },
                              ...workoutOptions,
                            ]}
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
                        <Card key={`item-${itemIndex}`} className="p-4 space-y-3 border border-gray-200">
                          <div className="flex items-center justify-between">
                            <span className="text-sm font-semibold">
                              {item.type === 'exercise' ? 'Exercise' : 'Rest'} {itemIndex + 1}
                            </span>
                            <Button
                              type="button"
                              variant="outline"
                              onClick={() => removeItem(weekIndex, dayIndex, sessionIndex, itemIndex)}
                            >
                              Remove
                            </Button>
                          </div>

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
                                <Select
                                  value={item.exerciseId || ''}
                                  onChange={(e) =>
                                    updateItem(weekIndex, dayIndex, sessionIndex, itemIndex, {
                                      exerciseId: e.target.value,
                                    })
                                  }
                                  options={exerciseOptions}
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
                            </>
                          )}
                        </Card>
                      ))}
                    </Card>
                  ))}
                </Card>
              ))}
            </Card>
          ))}
        </Card>

        <div className="flex justify-end gap-4">
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
