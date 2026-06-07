'use client';

import { useEffect, useState } from 'react';
import { useRouter, useParams } from 'next/navigation';
import { toast } from 'sonner';
import { LocalizedText } from '@/lib/types/localized';
import { Input, Select, Label, Button, Card, Textarea, Checkbox, Badge } from '@/components/ui';
import { ChevronDown, ChevronUp } from 'lucide-react';
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

interface WorkoutTemplateExercise {
  id?: string;
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

interface WorkoutTemplate {
  id: string;
  name: LocalizedText;
  description: LocalizedText | null;
  slug: string;
  coverImageUrl: string | null;
  difficulty: 'beginner' | 'intermediate' | 'advanced';
  estimatedDurationMin: number | null;
  tags: string[] | null;
  status: string;
  isFeatured?: boolean;
  exercises: {
    id: string;
    exerciseId: string;
    variantIndex: number;
    difficulty: string;
    targetReps: number | null;
    targetDuration: number | null;
    sets: number | null;
    restBetweenSetsMs: number | null;
    restAfterExerciseMs: number | null;
    weightKg: number | null;
    weightPerSet: number[] | null;
    notes: LocalizedText | null;
    sortOrder: number;
    exercise: Exercise;
  }[];
}

export default function EditWorkoutTemplatePage() {
  const router = useRouter();
  const params = useParams();
  const workoutId = params.id as string;

  const [loading, setLoading] = useState(false);
  const [loadingTemplate, setLoadingTemplate] = useState(true);
  const [exercises, setExercises] = useState<Exercise[]>([]);
  const [loadingExercises, setLoadingExercises] = useState(true);

  // Form state
  const [name, setName] = useState({ ar: '', en: '' });
  const [description, setDescription] = useState({ ar: '', en: '' });
  const [coverImageUrl, setCoverImageUrl] = useState('');
  const [difficulty, setDifficulty] = useState<'beginner' | 'intermediate' | 'advanced'>('beginner');
  const [estimatedDurationMin, setEstimatedDurationMin] = useState<number | ''>('');
  const [tags, setTags] = useState('');
  const [isFeatured, setIsFeatured] = useState(false);
  const [templateExercises, settemplateExercises] = useState<WorkoutTemplateExercise[]>([]);
  const [status, setStatus] = useState<'draft' | 'published'>('draft');

  // Fetch workout data
  useEffect(() => {
    const fetchTemplate = async () => {
      try {
        const res = await fetch(`/api/workout-templates/${workoutId}`);
        const data = await res.json();

        if (data.success && data.data) {
          const workout: WorkoutTemplate = data.data;
          setName(workout.name);
          setDescription(workout.description || { ar: '', en: '' });
          setCoverImageUrl(workout.coverImageUrl || '');
          setDifficulty(workout.difficulty);
          setEstimatedDurationMin(workout.estimatedDurationMin ?? '');
          setTags((workout.tags || []).join(', '));
          setStatus(workout.status as 'draft' | 'published');
          setIsFeatured(workout.isFeatured ?? false);

          // Map exercises
          settemplateExercises(
            workout.exercises.map((we) => ({
              id: we.id,
              exerciseId: we.exerciseId,
              exercise: we.exercise,
              variantIndex: we.variantIndex,
              difficulty: we.difficulty as 'beginner' | 'normal' | 'advanced',
              targetReps: we.targetReps || undefined,
              targetDuration: we.targetDuration || undefined,
              sets: we.sets ?? 1,
              restBetweenSetsMs: we.restBetweenSetsMs ?? 30000,
              restAfterExerciseMs: we.restAfterExerciseMs ?? 60000,
              weightKg: we.weightKg ?? undefined,
              weightPerSet: we.weightPerSet || undefined,
              notes: (we.notes as LocalizedText) || { ar: '', en: '' },
            }))
          );
        } else {
          toast.error('Workout template not found');
          router.push('/admin/workout-templates');
        }
      } catch (error) {
        console.error('Error fetching workout:', error);
        toast.error('Error loading template');
        router.push('/admin/workout-templates');
      } finally {
        setLoadingTemplate(false);
      }
    };

    fetchTemplate();
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

    settemplateExercises([
      ...templateExercises,
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
    settemplateExercises(templateExercises.filter((_, i) => i !== index));
  };

  const updateExercise = (index: number, updates: Partial<WorkoutTemplateExercise>) => {
    settemplateExercises(
      templateExercises.map((ex, i) => {
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
      (direction === 'down' && index === templateExercises.length - 1)
    ) {
      return;
    }

    const newIndex = direction === 'up' ? index - 1 : index + 1;
    const newExercises = [...templateExercises];
    [newExercises[index], newExercises[newIndex]] = [newExercises[newIndex], newExercises[index]];
    settemplateExercises(newExercises);
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
        isFeatured,
        estimatedDurationMin: typeof estimatedDurationMin === 'number' ? estimatedDurationMin : undefined,
        tags: tags
          .split(',')
          .map((tag) => tag.trim())
          .filter(Boolean),
        exercises: templateExercises.map((ex, index) => ({
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

      const res = await fetch(`/api/workout-templates/${workoutId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });

      const data = await res.json();

      if (data.success) {
        toast.success('Workout template updated');
        router.push('/admin/workout-templates');
      } else {
        toast.error(data.errors?.join('\n') || data.error || 'Failed to update template');
      }
    } catch (error) {
      console.error('Error updating workout:', error);
      toast.error('Failed to update template');
    } finally {
      setLoading(false);
    }
  };

  if (loadingTemplate) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <div className="text-gray-500">Loading template...</div>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-5xl space-y-8 pb-12">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight text-zinc-900">Edit template</h1>
          <p className="mt-1 text-sm text-zinc-500">Update template, Explore visibility, and exercise sequence.</p>
        </div>
        <Badge variant={status === 'published' ? 'success' : 'warning'} className="w-fit shrink-0 capitalize">
          {status}
        </Badge>
      </div>

      <form onSubmit={handleSubmit} className="space-y-8">
        <Card className="border-zinc-200/90 p-6 shadow-sm">
          <p className="text-xs font-semibold uppercase tracking-wider text-zinc-500">Basic information</p>
          <div className="mt-4 grid gap-4 sm:grid-cols-2">
            <div>
              <Label>Name (English) *</Label>
              <Input
                value={name.en}
                onChange={(e) => setName({ ...name, en: e.target.value })}
                placeholder="Enter template name"
                required
              />
            </div>
            <div>
              <Label>Name (Arabic) *</Label>
              <Input
                value={name.ar}
                onChange={(e) => setName({ ...name, ar: e.target.value })}
                placeholder="أدخل اسم القالب"
                dir="rtl"
                required
              />
            </div>
          </div>

          <div className="mt-4 grid gap-4 sm:grid-cols-2">
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

        <Card className="border-zinc-200/90 p-6 shadow-sm">
          <p className="text-xs font-semibold uppercase tracking-wider text-zinc-500">Template & Explore</p>
          <p className="mt-1 text-sm text-zinc-500">Cover, difficulty, duration, tags, and featured flag.</p>

          <div className="mt-6 grid gap-6 lg:grid-cols-2">
            <div className="space-y-4">
              <FileUpload
                label="Template cover"
                value={coverImageUrl}
                onChange={(imageUrl) => setCoverImageUrl(imageUrl)}
                uploadType="workout-image"
                accept="image/*"
                helperText="JPG, PNG, WEBP, or GIF"
              />
              <div>
                <Label>Cover image URL</Label>
                <Input
                  value={coverImageUrl}
                  onChange={(e) => setCoverImageUrl(e.target.value)}
                  placeholder="https://..."
                  className="font-mono text-sm"
                />
              </div>
            </div>

            <div className="flex flex-col gap-4">
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
              <div>
                <Label>Estimated duration (minutes)</Label>
                <Input
                  type="number"
                  min={0}
                  value={estimatedDurationMin}
                  onChange={(e) =>
                    setEstimatedDurationMin(e.target.value ? parseInt(e.target.value) : '')
                  }
                  placeholder="Optional"
                />
              </div>
              <div>
                <Label>Tags</Label>
                <Input
                  value={tags}
                  onChange={(e) => setTags(e.target.value)}
                  placeholder="Comma-separated, e.g. upper-body, no-equipment"
                />
                <p className="mt-1 text-xs text-zinc-500">Used for filtering and discovery.</p>
              </div>
              <label className="flex cursor-pointer items-start gap-3 rounded-xl border border-zinc-200 bg-zinc-50/50 p-4">
                <Checkbox
                  checked={isFeatured}
                  onCheckedChange={(v) => setIsFeatured(v === true)}
                  className="mt-0.5"
                  id="workout-featured-edit"
                />
                <span>
                  <span className="text-sm font-medium text-zinc-900">Featured template</span>
                  <span className="mt-0.5 block text-xs text-zinc-500">
                    Shown first in Explore and sync lists when published.
                  </span>
                </span>
              </label>
            </div>
          </div>
        </Card>

        <Card className="border-zinc-200/90 p-6 shadow-sm">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <p className="text-xs font-semibold uppercase tracking-wider text-zinc-500">Exercises</p>
              <p className="mt-1 text-sm text-zinc-500">Order, sets, rest, and targets per exercise.</p>
            </div>
            <Button
              type="button"
              variant="secondary"
              onClick={addExercise}
              disabled={loadingExercises || exercises.length === 0}
            >
              Add exercise
            </Button>
          </div>

          {loadingExercises ? (
            <div className="py-12 text-center text-sm text-zinc-500">Loading exercises…</div>
          ) : exercises.length === 0 ? (
            <div className="py-12 text-center text-sm text-zinc-500">
              No published exercises available. Publish exercises first.
            </div>
          ) : templateExercises.length === 0 ? (
            <div className="rounded-xl border border-dashed border-zinc-200 bg-zinc-50/50 py-12 text-center text-sm text-zinc-500">
              Add at least one exercise to save this template.
            </div>
          ) : (
            <div className="mt-6 space-y-4">
              {templateExercises.map((we, index) => (
                <div key={we.id || index} className="rounded-xl border border-zinc-200 bg-white p-4 shadow-sm sm:p-5">
                  <div className="flex items-start gap-3 sm:gap-4">
                    <div className="flex flex-col items-center gap-0.5 rounded-lg border border-zinc-200 bg-zinc-50 p-1">
                      <button
                        type="button"
                        onClick={() => moveExercise(index, 'up')}
                        disabled={index === 0}
                        className="rounded p-1 text-zinc-500 hover:bg-white hover:text-zinc-800 disabled:opacity-25"
                        aria-label="Move up"
                      >
                        <ChevronUp className="h-4 w-4" />
                      </button>
                      <span className="text-xs font-semibold tabular-nums text-zinc-600">{index + 1}</span>
                      <button
                        type="button"
                        onClick={() => moveExercise(index, 'down')}
                        disabled={index === templateExercises.length - 1}
                        className="rounded p-1 text-zinc-500 hover:bg-white hover:text-zinc-800 disabled:opacity-25"
                        aria-label="Move down"
                      >
                        <ChevronDown className="h-4 w-4" />
                      </button>
                    </div>

                    <div className="min-w-0 flex-1 space-y-4">
                      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
                        <div className="sm:col-span-2">
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

                        {we.exercise?.poseVariants && we.exercise.poseVariants.length > 1 && (
                          <div className="sm:col-span-2">
                            <Label>Variant</Label>
                            <Select
                              value={String(we.variantIndex)}
                              onChange={(e) =>
                                updateExercise(index, { variantIndex: parseInt(e.target.value) || 0 })
                              }
                              options={we.exercise.poseVariants.map((pv, vIdx) => ({
                                value: String(vIdx),
                                label: pv.name.en || pv.name.ar || `Variant ${vIdx + 1}`,
                              }))}
                            />
                          </div>
                        )}

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

                      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
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

                    <button
                      type="button"
                      onClick={() => removeExercise(index)}
                      className="shrink-0 rounded-lg px-2 py-1 text-xs font-medium text-red-600 hover:bg-red-50"
                    >
                      Remove
                    </button>
                  </div>

                  <div className="mt-4 grid gap-4 border-t border-zinc-100 pt-4 sm:grid-cols-2">
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

        <div className="flex flex-col-reverse gap-3 border-t border-zinc-100 pt-6 sm:flex-row sm:justify-end">
          <Button type="button" variant="outline" onClick={() => router.push('/admin/workout-templates')}>
            Cancel
          </Button>
          <Button type="submit" disabled={loading || templateExercises.length < 1}>
            {loading ? 'Saving…' : 'Save changes'}
          </Button>
        </div>
      </form>
    </div>
  );
}
