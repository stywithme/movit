'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { LocalizedText } from '@/lib/types/localized';
import { Input, Select, Label, Button, Card, Textarea } from '@/components/ui';
import { FileUpload } from '@/components/forms/FileUpload';

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
  exerciseId: string;
  exercise?: Exercise;
  variantIndex: number;
  difficulty: 'beginner' | 'normal' | 'advanced';
  targetReps?: number;
  targetDuration?: number;
  sets: number;
  restBetweenSetsMs: number;
  restAfterExerciseMs: number;
  weightKg?: number;
  weightPerSet?: number[];
  notes: { ar: string; en: string };
}

export default function NewWorkoutPage() {
  const router = useRouter();
  const [loading, setLoading] = useState(false);
  const [exercises, setExercises] = useState<Exercise[]>([]);
  const [loadingExercises, setLoadingExercises] = useState(true);

  // Form state
  const [name, setName] = useState({ ar: '', en: '' });
  const [description, setDescription] = useState({ ar: '', en: '' });
  const [coverImageUrl, setCoverImageUrl] = useState('');
  const [difficulty, setDifficulty] = useState<'beginner' | 'intermediate' | 'advanced'>('beginner');
  const [estimatedDurationMin, setEstimatedDurationMin] = useState<number | ''>('');
  const [tags, setTags] = useState('');
  const [workoutExercises, setWorkoutExercises] = useState<WorkoutExercise[]>([]);

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
        sets: 3,
        restBetweenSetsMs: 30000,
        restAfterExerciseMs: 60000,
        weightKg: undefined,
        weightPerSet: undefined,
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
        coverImageUrl: coverImageUrl || undefined,
        difficulty,
        estimatedDurationMin: typeof estimatedDurationMin === 'number' ? estimatedDurationMin : undefined,
        tags: tags
          .split(',')
          .map((tag) => tag.trim())
          .filter(Boolean),
        exercises: workoutExercises.map((ex, index) => ({
          exerciseId: ex.exerciseId,
          variantIndex: ex.variantIndex,
          difficulty: ex.difficulty,
          targetReps: ex.targetReps || undefined,
          targetDuration: ex.targetDuration || undefined,
          sets: ex.sets,
          restBetweenSetsMs: ex.restBetweenSetsMs,
          restAfterExerciseMs: ex.restAfterExerciseMs,
          weightKg: ex.weightKg || undefined,
          weightPerSet: ex.weightPerSet && ex.weightPerSet.length > 0 ? ex.weightPerSet : undefined,
          notes: ex.notes.en || ex.notes.ar ? ex.notes : undefined,
          sortOrder: index,
        })),
      };

      const res = await fetch('/api/workouts', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });

      const data = await res.json();

      if (data.success) {
        router.push('/admin/workouts');
      } else {
        alert(data.errors?.join('\n') || data.error || 'Failed to create workout');
      }
    } catch (error) {
      console.error('Error creating workout:', error);
      alert('Failed to create workout');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-gray-900">New Workout</h1>
        <p className="text-gray-600 mt-1">Create a reusable workout template</p>
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

        {/* Workout Template Configuration */}
        <Card className="p-6">
          <h2 className="text-lg font-semibold mb-4">Workout Template Configuration</h2>
          
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-3">
              <FileUpload
                label="Workout Cover"
                value={coverImageUrl}
                onChange={(imageUrl) => setCoverImageUrl(imageUrl)}
                uploadType="workout-image"
                accept="image/*"
                helperText="Upload JPG, PNG, WEBP, or GIF cover image"
              />
              <div>
                <Label>Cover Image URL</Label>
                <Input
                  value={coverImageUrl}
                  onChange={(e) => setCoverImageUrl(e.target.value)}
                  placeholder="https://..."
                />
              </div>
            </div>
            <div>
              <Label>Difficulty</Label>
              <Select
                value={difficulty}
                onChange={(e) =>
                  setDifficulty(e.target.value as 'beginner' | 'intermediate' | 'advanced')
                }
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
              <Label>Estimated Duration (min)</Label>
              <Input
                type="number"
                min={0}
                value={estimatedDurationMin}
                onChange={(e) =>
                  setEstimatedDurationMin(e.target.value ? parseInt(e.target.value) : '')
                }
              />
            </div>
            <div>
              <Label>Tags (comma separated)</Label>
              <Input
                value={tags}
                onChange={(e) => setTags(e.target.value)}
                placeholder="upper-body, no-equipment"
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
                <div
                  key={index}
                  className="border rounded-lg p-4 bg-gray-50"
                >
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
                    <div className="flex-1">
                      <div className="grid grid-cols-4 gap-4">
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

                      <div className="mt-3 grid grid-cols-5 gap-4">
                        <div>
                          <Label>Sets</Label>
                          <Input
                            type="number"
                            min={1}
                            value={we.sets}
                            onChange={(e) =>
                              updateExercise(index, {
                                sets: parseInt(e.target.value) || 1,
                              })
                            }
                          />
                        </div>
                        <div>
                          <Label>Rest Between Sets (sec)</Label>
                          <Input
                            type="number"
                            min={0}
                            value={we.restBetweenSetsMs / 1000}
                            onChange={(e) =>
                              updateExercise(index, {
                                restBetweenSetsMs: (parseInt(e.target.value) || 0) * 1000,
                              })
                            }
                          />
                        </div>
                        <div>
                          <Label>Rest After Exercise (sec)</Label>
                          <Input
                            type="number"
                            min={0}
                            value={we.restAfterExerciseMs / 1000}
                            onChange={(e) =>
                              updateExercise(index, {
                                restAfterExerciseMs: (parseInt(e.target.value) || 0) * 1000,
                              })
                            }
                          />
                        </div>
                        <div>
                          <Label>Weight (kg)</Label>
                          <Input
                            type="number"
                            min={0}
                            value={we.weightKg ?? ''}
                            onChange={(e) =>
                              updateExercise(index, {
                                weightKg: e.target.value ? parseFloat(e.target.value) : undefined,
                              })
                            }
                          />
                        </div>
                        <div>
                          <Label>Weight per Set</Label>
                          <Input
                            value={we.weightPerSet?.join(', ') || ''}
                            onChange={(e) =>
                              updateExercise(index, {
                                weightPerSet: e.target.value
                                  .split(',')
                                  .map((val) => parseFloat(val.trim()))
                                  .filter((val) => !Number.isNaN(val)),
                              })
                            }
                            placeholder="10, 12.5, 15"
                          />
                        </div>
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

                  {/* Notes (collapsed by default, could expand) */}
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
          <Button
            type="button"
            variant="outline"
            onClick={() => router.push('/admin/workouts')}
          >
            Cancel
          </Button>
          <Button type="submit" disabled={loading || workoutExercises.length < 1}>
            {loading ? 'Creating...' : 'Create Workout'}
          </Button>
        </div>
      </form>
    </div>
  );
}
