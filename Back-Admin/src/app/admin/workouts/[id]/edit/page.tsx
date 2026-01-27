'use client';

import { useEffect, useState } from 'react';
import { useRouter, useParams } from 'next/navigation';
import { LocalizedText } from '@/lib/types/localized';
import { Input, Select, Label, Button, Card, Textarea } from '@/components/ui';

interface Exercise {
  id: string;
  slug: string;
  name: LocalizedText;
  status: string;
  countingMethod: {
    code: string;
    name: LocalizedText;
  };
  poseVariants: {
    id: string;
    name: LocalizedText;
    sortOrder: number;
  }[];
}

interface WorkoutExercise {
  id?: string;
  exerciseId: string;
  exercise?: Exercise;
  variantIndex: number;
  difficulty: 'beginner' | 'normal' | 'advanced';
  targetReps?: number;
  targetDuration?: number;
  notes: { ar: string; en: string };
}

interface Workout {
  id: string;
  name: LocalizedText;
  description: LocalizedText | null;
  slug: string;
  type: 'circuit' | 'super_set';
  executionMode: 'sequential' | 'alternating';
  rounds: number;
  repsPerSwitch: number | null;
  restBetweenSwitchMs: number | null;
  restBetweenExercisesMs: number | null;
  restBetweenRoundsMs: number;
  status: string;
  exercises: {
    id: string;
    exerciseId: string;
    variantIndex: number;
    difficulty: string;
    targetReps: number | null;
    targetDuration: number | null;
    notes: LocalizedText | null;
    sortOrder: number;
    exercise: Exercise;
  }[];
}

export default function EditWorkoutPage() {
  const router = useRouter();
  const params = useParams();
  const workoutId = params.id as string;

  const [loading, setLoading] = useState(false);
  const [loadingWorkout, setLoadingWorkout] = useState(true);
  const [exercises, setExercises] = useState<Exercise[]>([]);
  const [loadingExercises, setLoadingExercises] = useState(true);

  // Form state
  const [name, setName] = useState({ ar: '', en: '' });
  const [description, setDescription] = useState({ ar: '', en: '' });
  const [type, setType] = useState<'circuit' | 'super_set'>('circuit');
  const [executionMode, setExecutionMode] = useState<'sequential' | 'alternating'>('sequential');
  const [rounds, setRounds] = useState(1);
  const [repsPerSwitch, setRepsPerSwitch] = useState(3);
  const [restBetweenSwitchMs, setRestBetweenSwitchMs] = useState(5000);
  const [restBetweenExercisesMs, setRestBetweenExercisesMs] = useState(15000);
  const [restBetweenRoundsMs, setRestBetweenRoundsMs] = useState(60000);
  const [workoutExercises, setWorkoutExercises] = useState<WorkoutExercise[]>([]);
  const [status, setStatus] = useState<'draft' | 'published'>('draft');

  // Fetch workout data
  useEffect(() => {
    const fetchWorkout = async () => {
      try {
        const res = await fetch(`/api/workouts/${workoutId}`);
        const data = await res.json();

        if (data.success && data.data) {
          const workout: Workout = data.data;
          setName(workout.name);
          setDescription(workout.description || { ar: '', en: '' });
          setType(workout.type);
          setExecutionMode(workout.executionMode);
          setRounds(workout.rounds);
          setRepsPerSwitch(workout.repsPerSwitch || 3);
          setRestBetweenSwitchMs(workout.restBetweenSwitchMs || 5000);
          setRestBetweenExercisesMs(workout.restBetweenExercisesMs || 15000);
          setRestBetweenRoundsMs(workout.restBetweenRoundsMs);
          setStatus(workout.status as 'draft' | 'published');

          // Map exercises
          setWorkoutExercises(
            workout.exercises.map((we) => ({
              id: we.id,
              exerciseId: we.exerciseId,
              exercise: we.exercise,
              variantIndex: we.variantIndex,
              difficulty: we.difficulty as 'beginner' | 'normal' | 'advanced',
              targetReps: we.targetReps || undefined,
              targetDuration: we.targetDuration || undefined,
              notes: (we.notes as LocalizedText) || { ar: '', en: '' },
            }))
          );
        } else {
          alert('Workout not found');
          router.push('/admin/workouts');
        }
      } catch (error) {
        console.error('Error fetching workout:', error);
        alert('Error loading workout');
        router.push('/admin/workouts');
      } finally {
        setLoadingWorkout(false);
      }
    };

    fetchWorkout();
  }, [workoutId, router]);

  // Fetch published exercises
  useEffect(() => {
    const fetchExercises = async () => {
      try {
        const res = await fetch('/api/exercises?status=published&limit=100');
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

  const addExercise = () => {
    if (exercises.length === 0) return;
    const firstExercise = exercises[0];
    const isHold = firstExercise.countingMethod?.code === 'hold';

    setWorkoutExercises([
      ...workoutExercises,
      {
        exerciseId: firstExercise.id,
        exercise: firstExercise,
        variantIndex: 0,
        difficulty: 'beginner',
        targetReps: isHold ? undefined : 10,
        targetDuration: isHold ? 30 : undefined,
        notes: { ar: '', en: '' },
      },
    ]);
  };

  const removeExercise = (index: number) => {
    setWorkoutExercises(workoutExercises.filter((_, i) => i !== index));
  };

  const updateExercise = (index: number, updates: Partial<WorkoutExercise>) => {
    setWorkoutExercises(
      workoutExercises.map((ex, i) => {
        if (i !== index) return ex;

        // If exerciseId changed, update the exercise reference
        if (updates.exerciseId && updates.exerciseId !== ex.exerciseId) {
          const newExercise = exercises.find((e) => e.id === updates.exerciseId);
          const isHold = newExercise?.countingMethod?.code === 'hold';
          return {
            ...ex,
            ...updates,
            exercise: newExercise,
            variantIndex: 0,
            targetReps: isHold ? undefined : ex.targetReps || 10,
            targetDuration: isHold ? ex.targetDuration || 30 : undefined,
          };
        }

        return { ...ex, ...updates };
      })
    );
  };

  const moveExercise = (index: number, direction: 'up' | 'down') => {
    if (
      (direction === 'up' && index === 0) ||
      (direction === 'down' && index === workoutExercises.length - 1)
    ) {
      return;
    }

    const newIndex = direction === 'up' ? index - 1 : index + 1;
    const newExercises = [...workoutExercises];
    [newExercises[index], newExercises[newIndex]] = [newExercises[newIndex], newExercises[index]];
    setWorkoutExercises(newExercises);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);

    try {
      const payload = {
        name,
        description: description.en || description.ar ? description : undefined,
        type,
        executionMode,
        rounds,
        repsPerSwitch: executionMode === 'alternating' ? repsPerSwitch : undefined,
        restBetweenSwitchMs: executionMode === 'alternating' ? restBetweenSwitchMs : undefined,
        restBetweenExercisesMs: executionMode === 'sequential' ? restBetweenExercisesMs : undefined,
        restBetweenRoundsMs,
        exercises: workoutExercises.map((ex, index) => ({
          exerciseId: ex.exerciseId,
          variantIndex: ex.variantIndex,
          difficulty: ex.difficulty,
          targetReps: ex.targetReps || undefined,
          targetDuration: ex.targetDuration || undefined,
          notes: ex.notes.en || ex.notes.ar ? ex.notes : undefined,
          sortOrder: index,
        })),
      };

      const res = await fetch(`/api/workouts/${workoutId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });

      const data = await res.json();

      if (data.success) {
        router.push('/admin/workouts');
      } else {
        alert(data.errors?.join('\n') || data.error || 'Failed to update workout');
      }
    } catch (error) {
      console.error('Error updating workout:', error);
      alert('Failed to update workout');
    } finally {
      setLoading(false);
    }
  };

  if (loadingWorkout) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <div className="text-gray-500">Loading workout...</div>
      </div>
    );
  }

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      {/* Header */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Edit Workout</h1>
          <p className="text-gray-600 mt-1">Update workout configuration</p>
        </div>
        <span
          className={`inline-flex px-3 py-1 text-sm font-medium rounded-full ${
            status === 'published'
              ? 'bg-green-100 text-green-800'
              : 'bg-yellow-100 text-yellow-800'
          }`}
        >
          {status}
        </span>
      </div>

      <form onSubmit={handleSubmit} className="space-y-6">
        {/* Basic Info */}
        <Card className="p-6">
          <h2 className="text-lg font-semibold mb-4">Basic Information</h2>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <Label>Name (English) *</Label>
              <Input
                value={name.en}
                onChange={(e) => setName({ ...name, en: e.target.value })}
                placeholder="Enter workout name"
                required
              />
            </div>
            <div>
              <Label>Name (Arabic) *</Label>
              <Input
                value={name.ar}
                onChange={(e) => setName({ ...name, ar: e.target.value })}
                placeholder="أدخل اسم التمرين"
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

        {/* Workout Configuration */}
        <Card className="p-6">
          <h2 className="text-lg font-semibold mb-4">Workout Configuration</h2>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <Label>Type *</Label>
              <Select
                value={type}
                onChange={(e) => setType(e.target.value as 'circuit' | 'super_set')}
                options={[
                  { value: 'circuit', label: 'Circuit' },
                  { value: 'super_set', label: 'Super Set' },
                ]}
              />
            </div>
            <div>
              <Label>Execution Mode *</Label>
              <Select
                value={executionMode}
                onChange={(e) => setExecutionMode(e.target.value as 'sequential' | 'alternating')}
                options={[
                  { value: 'sequential', label: 'Sequential (One exercise then next)' },
                  { value: 'alternating', label: 'Alternating (Switch between exercises)' },
                ]}
              />
            </div>
          </div>

          <div className="grid grid-cols-3 gap-4 mt-4">
            <div>
              <Label>Rounds</Label>
              <Input
                type="number"
                min={1}
                value={rounds}
                onChange={(e) => setRounds(parseInt(e.target.value) || 1)}
              />
            </div>

            {executionMode === 'alternating' && (
              <>
                <div>
                  <Label>Reps Per Switch</Label>
                  <Input
                    type="number"
                    min={1}
                    value={repsPerSwitch}
                    onChange={(e) => setRepsPerSwitch(parseInt(e.target.value) || 1)}
                  />
                </div>
                <div>
                  <Label>Rest Between Switch (sec)</Label>
                  <Input
                    type="number"
                    min={0}
                    value={restBetweenSwitchMs / 1000}
                    onChange={(e) => setRestBetweenSwitchMs((parseInt(e.target.value) || 0) * 1000)}
                  />
                </div>
              </>
            )}

            {executionMode === 'sequential' && (
              <div>
                <Label>Rest Between Exercises (sec)</Label>
                <Input
                  type="number"
                  min={0}
                  value={restBetweenExercisesMs / 1000}
                  onChange={(e) => setRestBetweenExercisesMs((parseInt(e.target.value) || 0) * 1000)}
                />
              </div>
            )}

            <div>
              <Label>Rest Between Rounds (sec)</Label>
              <Input
                type="number"
                min={0}
                value={restBetweenRoundsMs / 1000}
                onChange={(e) => setRestBetweenRoundsMs((parseInt(e.target.value) || 0) * 1000)}
              />
            </div>
          </div>
        </Card>

        {/* Exercises */}
        <Card className="p-6">
          <div className="flex justify-between items-center mb-4">
            <h2 className="text-lg font-semibold">Exercises</h2>
            <Button
              type="button"
              onClick={addExercise}
              disabled={loadingExercises || exercises.length === 0}
            >
              + Add Exercise
            </Button>
          </div>

          {loadingExercises ? (
            <div className="text-center py-8 text-gray-500">Loading exercises...</div>
          ) : exercises.length === 0 ? (
            <div className="text-center py-8 text-gray-500">
              No published exercises available. Please publish some exercises first.
            </div>
          ) : workoutExercises.length === 0 ? (
            <div className="text-center py-8 text-gray-500 border-2 border-dashed rounded-lg">
              No exercises added yet. Click "Add Exercise" to start.
            </div>
          ) : (
            <div className="space-y-4">
              {workoutExercises.map((we, index) => (
                <div key={we.id || index} className="border rounded-lg p-4 bg-gray-50">
                  <div className="flex items-start gap-4">
                    {/* Order controls */}
                    <div className="flex flex-col gap-1">
                      <button
                        type="button"
                        onClick={() => moveExercise(index, 'up')}
                        disabled={index === 0}
                        className="p-1 text-gray-500 hover:text-gray-700 disabled:opacity-30"
                      >
                        ▲
                      </button>
                      <span className="text-center text-sm font-medium text-gray-600">
                        {index + 1}
                      </span>
                      <button
                        type="button"
                        onClick={() => moveExercise(index, 'down')}
                        disabled={index === workoutExercises.length - 1}
                        className="p-1 text-gray-500 hover:text-gray-700 disabled:opacity-30"
                      >
                        ▼
                      </button>
                    </div>

                    {/* Exercise details */}
                    <div className="flex-1 grid grid-cols-4 gap-4">
                      <div className="col-span-2">
                        <Label>Exercise</Label>
                        <Select
                          value={we.exerciseId}
                          onChange={(e) => updateExercise(index, { exerciseId: e.target.value })}
                          options={exercises.map((ex) => ({
                            value: ex.id,
                            label: `${ex.name.en} (${ex.name.ar})`,
                          }))}
                        />
                      </div>

                      <div>
                        <Label>Difficulty</Label>
                        <Select
                          value={we.difficulty}
                          onChange={(e) =>
                            updateExercise(index, {
                              difficulty: e.target.value as 'beginner' | 'normal' | 'advanced',
                            })
                          }
                          options={[
                            { value: 'beginner', label: 'Beginner' },
                            { value: 'normal', label: 'Normal' },
                            { value: 'advanced', label: 'Advanced' },
                          ]}
                        />
                      </div>

                      <div>
                        {we.exercise?.countingMethod?.code === 'hold' ? (
                          <>
                            <Label>Duration (sec)</Label>
                            <Input
                              type="number"
                              min={1}
                              value={we.targetDuration || ''}
                              onChange={(e) =>
                                updateExercise(index, {
                                  targetDuration: parseInt(e.target.value) || undefined,
                                })
                              }
                            />
                          </>
                        ) : (
                          <>
                            <Label>Target Reps</Label>
                            <Input
                              type="number"
                              min={1}
                              value={we.targetReps || ''}
                              onChange={(e) =>
                                updateExercise(index, {
                                  targetReps: parseInt(e.target.value) || undefined,
                                })
                              }
                            />
                          </>
                        )}
                      </div>
                    </div>

                    {/* Remove button */}
                    <button
                      type="button"
                      onClick={() => removeExercise(index)}
                      className="p-2 text-red-500 hover:text-red-700"
                    >
                      ✕
                    </button>
                  </div>

                  {/* Notes */}
                  <div className="mt-3 grid grid-cols-2 gap-4">
                    <div>
                      <Label className="text-xs">Note (English)</Label>
                      <Input
                        value={we.notes.en}
                        onChange={(e) =>
                          updateExercise(index, {
                            notes: { ...we.notes, en: e.target.value },
                          })
                        }
                        placeholder="Optional note..."
                        className="text-sm"
                      />
                    </div>
                    <div>
                      <Label className="text-xs">Note (Arabic)</Label>
                      <Input
                        value={we.notes.ar}
                        onChange={(e) =>
                          updateExercise(index, {
                            notes: { ...we.notes, ar: e.target.value },
                          })
                        }
                        placeholder="ملاحظة اختيارية..."
                        dir="rtl"
                        className="text-sm"
                      />
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </Card>

        {/* Actions */}
        <div className="flex justify-end gap-4">
          <Button type="button" variant="outline" onClick={() => router.push('/admin/workouts')}>
            Cancel
          </Button>
          <Button type="submit" disabled={loading || workoutExercises.length < 2}>
            {loading ? 'Saving...' : 'Save Changes'}
          </Button>
        </div>
      </form>
    </div>
  );
}
