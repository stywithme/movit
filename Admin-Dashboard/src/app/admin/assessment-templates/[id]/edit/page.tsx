'use client';

import { useEffect, useMemo, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import {
  Input, Select, Label, Button, Card, Textarea, Badge, Checkbox,
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogBody, DialogFooter,
} from '@/components/ui';
import {
  ProgramAttributesSection,
  useAttributesCatalog,
} from '../../../programs/_components/ProgramAttributesSection';
import type { ProgramAttributeFormRow } from '../../../programs/_lib/program-prescription-attributes';
import type { LocalizedText } from '@/lib/types/localized';
import { ASSESSMENT_TEMPLATE_TYPE_OPTIONS } from '../../assessment-template-types';

interface ExerciseSummary {
  id: string;
  name: LocalizedText;
}

interface Level {
  id: string;
  name: LocalizedText;
  levelNumber: number;
}

interface ExerciseFormItem {
  exerciseId: string;
  targetRegion: string;
  side: string;
  entryType: 'core' | 'adaptive';
  activationCondition: string;
  referenceNormDegrees: string;
  thresholdExcellent: string;
  thresholdGood: string;
  thresholdAverage: string;
  thresholdLimited: string;
  sortOrder: number;
}

interface TemplateResponse {
  id: string;
  name: LocalizedText;
  description: LocalizedText | null;
  type: string;
  targetLevelId: string | null;
  isDefault: boolean;
  domainWeights: {
    mobility: number;
    control: number;
    symmetry: number;
    safety: number;
  } | null;
  status: string;
  exercises: Array<{
    id: string;
    exerciseId: string;
    exercise?: { id: string; name: LocalizedText };
    targetRegion: string;
    side: string;
    entryType: string;
    activationCondition: string | null;
    referenceNormDegrees: number | null;
    thresholdExcellent: number | null;
    thresholdGood: number | null;
    thresholdAverage: number | null;
    thresholdLimited: number | null;
    sortOrder: number;
  }>;
  assessmentAttributes?: Array<{
    id: string;
    mode: string;
    attributeValue: { id: string; code: string };
  }>;
}

const TARGET_REGION_OPTIONS = [
  { value: 'hip', label: 'Hip' },
  { value: 'knee', label: 'Knee' },
  { value: 'shoulder', label: 'Shoulder' },
  { value: 'core', label: 'Core' },
  { value: 'lower_back', label: 'Lower Back' },
  { value: 'balance', label: 'Balance' },
];

const SIDE_OPTIONS = [
  { value: 'left', label: 'Left' },
  { value: 'right', label: 'Right' },
  { value: 'center', label: 'Center' },
  { value: 'bilateral', label: 'Bilateral' },
];

const ENTRY_TYPE_OPTIONS = [
  { value: 'core', label: 'Core' },
  { value: 'adaptive', label: 'Adaptive' },
];

const DOMAIN_COLORS: Record<string, { bg: string; label: string; text: string }> = {
  mobility: { bg: 'bg-blue-500', label: 'Mobility', text: 'text-blue-700' },
  control: { bg: 'bg-green-500', label: 'Control', text: 'text-green-700' },
  symmetry: { bg: 'bg-purple-500', label: 'Symmetry', text: 'text-purple-700' },
  safety: { bg: 'bg-orange-500', label: 'Safety', text: 'text-orange-700' },
};

const REGION_BADGE_VARIANT: Record<string, 'primary' | 'success' | 'purple' | 'orange' | 'teal' | 'warning'> = {
  hip: 'primary',
  knee: 'success',
  shoulder: 'purple',
  core: 'orange',
  lower_back: 'teal',
  balance: 'warning',
};

const formatLabel = (s: string) =>
  s.split('_').map((w) => w.charAt(0).toUpperCase() + w.slice(1)).join(' ');

const createEmptyExerciseForm = (sortOrder: number): ExerciseFormItem => ({
  exerciseId: '',
  targetRegion: 'hip',
  side: 'bilateral',
  entryType: 'core',
  activationCondition: '',
  referenceNormDegrees: '',
  thresholdExcellent: '',
  thresholdGood: '',
  thresholdAverage: '',
  thresholdLimited: '',
  sortOrder,
});

export default function EditAssessmentTemplatePage() {
  const router = useRouter();
  const params = useParams();
  const templateId = params.id as string;

  const [loading, setLoading] = useState(false);
  const [loadingTemplate, setLoadingTemplate] = useState(true);

  const [name, setName] = useState<LocalizedText>({ en: '', ar: '' });
  const [description, setDescription] = useState<LocalizedText>({ en: '', ar: '' });
  const [templateType, setTemplateType] = useState('initial');
  const [targetLevelId, setTargetLevelId] = useState('');
  const [isDefault, setIsDefault] = useState(false);

  const [weights, setWeights] = useState({
    mobility: 0.25,
    control: 0.25,
    symmetry: 0.25,
    safety: 0.25,
  });

  const [exercises, setExercises] = useState<ExerciseFormItem[]>([]);
  const [attributeRows, setAttributeRows] = useState<ProgramAttributeFormRow[]>([]);

  const { catalog: attributesCatalog, loading: loadingAttributes } = useAttributesCatalog();
  const [levels, setLevels] = useState<Level[]>([]);
  const [allExercises, setAllExercises] = useState<ExerciseSummary[]>([]);
  const [loadingExercises, setLoadingExercises] = useState(true);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingIndex, setEditingIndex] = useState<number | null>(null);
  const [dialogForm, setDialogForm] = useState<ExerciseFormItem>(createEmptyExerciseForm(0));

  useEffect(() => {
    const fetchLevels = async () => {
      try {
        const res = await fetch('/api/admin/levels');
        const data = await res.json();
        if (data.success) setLevels(data.data);
      } catch (error) {
        console.error('Error fetching levels:', error);
      }
    };
    fetchLevels();
  }, []);

  useEffect(() => {
    const fetchExercises = async () => {
      try {
        const res = await fetch('/api/exercises?status=published&limit=200');
        const data = await res.json();
        if (data.success) setAllExercises(data.data);
      } catch (error) {
        console.error('Error fetching exercises:', error);
      } finally {
        setLoadingExercises(false);
      }
    };
    fetchExercises();
  }, []);

  useEffect(() => {
    const fetchTemplate = async () => {
      try {
        const res = await fetch(`/api/admin/assessment-templates/${templateId}`);
        const data = await res.json();
        if (!data.success || !data.data) {
          alert('Template not found');
          router.push('/admin/assessment-templates');
          return;
        }

        const template: TemplateResponse = data.data;
        setName(template.name);
        setDescription(template.description || { en: '', ar: '' });
        setTemplateType(template.type);
        setTargetLevelId(template.targetLevelId || '');
        setIsDefault(template.isDefault);

        if (template.domainWeights) {
          setWeights(template.domainWeights);
        }

        const mappedExercises: ExerciseFormItem[] = (template.exercises || []).map((ex) => ({
          exerciseId: ex.exerciseId,
          targetRegion: ex.targetRegion,
          side: ex.side,
          entryType: (ex.entryType as 'core' | 'adaptive') || 'core',
          activationCondition: ex.activationCondition || '',
          referenceNormDegrees: ex.referenceNormDegrees?.toString() || '',
          thresholdExcellent: ex.thresholdExcellent?.toString() || '',
          thresholdGood: ex.thresholdGood?.toString() || '',
          thresholdAverage: ex.thresholdAverage?.toString() || '',
          thresholdLimited: ex.thresholdLimited?.toString() || '',
          sortOrder: ex.sortOrder,
        }));

        setExercises(mappedExercises);

        setAttributeRows(
          (template.assessmentAttributes ?? []).map((a) => ({
            attributeValueId: a.attributeValue.id,
            mode: a.mode as ProgramAttributeFormRow['mode'],
          })),
        );
      } catch (error) {
        console.error('Error fetching template:', error);
        alert('Error loading template');
        router.push('/admin/assessment-templates');
      } finally {
        setLoadingTemplate(false);
      }
    };

    fetchTemplate();
  }, [templateId, router]);

  const levelOptions = useMemo(
    () => levels.map((l) => ({ value: l.id, label: `Level ${l.levelNumber} — ${l.name.en}` })),
    [levels]
  );

  const exerciseOptions = useMemo(
    () => allExercises.map((e) => ({ value: e.id, label: `${e.name.en} / ${e.name.ar}` })),
    [allExercises]
  );

  const exerciseLookup = useMemo(() => {
    const map = new Map<string, string>();
    allExercises.forEach((e) => map.set(e.id, e.name.en));
    return map;
  }, [allExercises]);

  const weightsTotal = weights.mobility + weights.control + weights.symmetry + weights.safety;
  const isWeightsValid = Math.abs(weightsTotal - 1.0) < 0.001;

  const updateWeight = (key: keyof typeof weights, value: string) => {
    const num = parseFloat(value);
    setWeights((prev) => ({ ...prev, [key]: isNaN(num) ? 0 : num }));
  };

  const openAddDialog = () => {
    setEditingIndex(null);
    setDialogForm(createEmptyExerciseForm(exercises.length));
    setDialogOpen(true);
  };

  const openEditDialog = (index: number) => {
    setEditingIndex(index);
    setDialogForm({ ...exercises[index] });
    setDialogOpen(true);
  };

  const saveDialogExercise = () => {
    if (!dialogForm.exerciseId) {
      alert('Please select an exercise');
      return;
    }
    if (editingIndex !== null) {
      setExercises((prev) =>
        prev.map((ex, i) => (i === editingIndex ? { ...dialogForm } : ex))
      );
    } else {
      setExercises((prev) => [...prev, { ...dialogForm, sortOrder: prev.length }]);
    }
    setDialogOpen(false);
  };

  const removeExercise = (index: number) => {
    setExercises((prev) =>
      prev.filter((_, i) => i !== index).map((ex, i) => ({ ...ex, sortOrder: i }))
    );
  };

  const moveExercise = (index: number, direction: 'up' | 'down') => {
    const newIndex = direction === 'up' ? index - 1 : index + 1;
    if (newIndex < 0 || newIndex >= exercises.length) return;
    setExercises((prev) => {
      const updated = [...prev];
      [updated[index], updated[newIndex]] = [updated[newIndex], updated[index]];
      return updated.map((ex, i) => ({ ...ex, sortOrder: i }));
    });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);

    try {
      const payload = {
        name,
        description: description.en || description.ar ? description : undefined,
        type: templateType,
        targetLevelId:
          (templateType === 'level_specific' ||
            templateType === 'progression' ||
            templateType === 'post_program') &&
          targetLevelId
            ? targetLevelId
            : undefined,
        isDefault,
        domainWeights: weights,
        assessmentAttributes: attributeRows,
        exercises: exercises.map((exercise) => ({
          exerciseId: exercise.exerciseId,
          targetRegion: exercise.targetRegion,
          side: exercise.side,
          entryType: exercise.entryType,
          activationCondition:
            exercise.entryType === 'adaptive' && exercise.activationCondition
              ? exercise.activationCondition
              : undefined,
          referenceNormDegrees: exercise.referenceNormDegrees ? Number(exercise.referenceNormDegrees) : undefined,
          thresholdExcellent: exercise.thresholdExcellent ? Number(exercise.thresholdExcellent) : undefined,
          thresholdGood: exercise.thresholdGood ? Number(exercise.thresholdGood) : undefined,
          thresholdAverage: exercise.thresholdAverage ? Number(exercise.thresholdAverage) : undefined,
          thresholdLimited: exercise.thresholdLimited ? Number(exercise.thresholdLimited) : undefined,
          sortOrder: exercise.sortOrder,
        })),
      };

      const res = await fetch(`/api/admin/assessment-templates/${templateId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });

      const data = await res.json();
      if (data.success) {
        router.push('/admin/assessment-templates');
      } else {
        alert(data.errors?.join('\n') || data.error || 'Failed to update template');
      }
    } catch (error) {
      console.error('Error updating template:', error);
      alert('Failed to update template');
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
    <div className="max-w-5xl mx-auto space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Edit Assessment Template</h1>
        <p className="text-gray-600 mt-1">Update assessment template configuration</p>
      </div>

      <form onSubmit={handleSubmit} className="space-y-6">
        {/* Basic Info Card */}
        <Card className="p-6">
          <h2 className="text-lg font-semibold mb-4">Basic Information</h2>

          <div className="grid grid-cols-2 gap-4">
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

          <div className="grid grid-cols-2 gap-4 mt-4">
            <div>
              <Label>Template Type *</Label>
              <Select
                value={templateType}
                onChange={(e) => setTemplateType(e.target.value)}
                options={ASSESSMENT_TEMPLATE_TYPE_OPTIONS}
              />
            </div>
            {['level_specific', 'progression', 'post_program'].includes(templateType) && (
              <div>
                <Label>Target Level</Label>
                <Select
                  value={targetLevelId}
                  onChange={(e) => setTargetLevelId(e.target.value)}
                  options={[{ value: '', label: 'Select a level...' }, ...levelOptions]}
                />
              </div>
            )}
          </div>

          <div className="flex items-center gap-2 mt-4">
            <Checkbox
              checked={isDefault}
              onCheckedChange={(checked) => setIsDefault(checked === true)}
            />
            <Label className="mb-0">Is Default Template</Label>
          </div>
        </Card>

        <Card className="p-6">
          <h2 className="text-lg font-semibold mb-2">Matching attributes</h2>
          <p className="text-sm text-gray-500 mb-4">
            Same rules as programs: required values must match the user profile; optional values improve ranking.
          </p>
          {loadingAttributes ? (
            <p className="text-sm text-gray-500">Loading attribute catalog…</p>
          ) : (
            <ProgramAttributesSection
              catalog={attributesCatalog}
              value={attributeRows}
              onChange={setAttributeRows}
            />
          )}
        </Card>

        {/* Domain Weights Card */}
        <Card className="p-6">
          <h2 className="text-lg font-semibold mb-4">Domain Weights</h2>
          <p className="text-sm text-gray-500 mb-4">
            Set the weight for each assessment domain. Weights should sum to 1.00.
          </p>

          <div className="grid grid-cols-4 gap-4">
            {(['mobility', 'control', 'symmetry', 'safety'] as const).map((key) => (
              <div key={key}>
                <Label>
                  <span className="flex items-center gap-2">
                    <span className={`w-3 h-3 rounded-full ${DOMAIN_COLORS[key].bg}`} />
                    {DOMAIN_COLORS[key].label}
                  </span>
                </Label>
                <Input
                  type="number"
                  min={0}
                  max={1}
                  step={0.05}
                  value={weights[key]}
                  onChange={(e) => updateWeight(key, e.target.value)}
                />
              </div>
            ))}
          </div>

          {/* Bar Visualization */}
          <div className="mt-6">
            <div className="flex h-8 rounded-lg overflow-hidden bg-gray-100">
              {(['mobility', 'control', 'symmetry', 'safety'] as const).map((key) => {
                const value = weights[key];
                const pct = weightsTotal > 0 ? (value / weightsTotal) * 100 : 25;
                return (
                  <div
                    key={key}
                    className={`${DOMAIN_COLORS[key].bg} flex items-center justify-center text-white text-xs font-medium transition-all`}
                    style={{ width: `${pct}%` }}
                  >
                    {pct > 12 && `${DOMAIN_COLORS[key].label} ${(value * 100).toFixed(0)}%`}
                  </div>
                );
              })}
            </div>
            <div className="mt-2 flex items-center gap-2">
              <span className={`text-sm font-medium ${isWeightsValid ? 'text-green-600' : 'text-red-600'}`}>
                Total: {weightsTotal.toFixed(2)}
              </span>
              {isWeightsValid ? (
                <svg className="w-4 h-4 text-green-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                </svg>
              ) : (
                <span className="text-red-600 text-sm">Weights must equal 1.00</span>
              )}
            </div>
          </div>
        </Card>

        {/* Exercises Card */}
        <Card className="p-6 space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-lg font-semibold">Template Exercises</h2>
              <p className="text-sm text-gray-500">
                {exercises.length} exercise{exercises.length !== 1 ? 's' : ''} configured
              </p>
            </div>
            <Button type="button" variant="outline" onClick={openAddDialog} disabled={loadingExercises}>
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
              </svg>
              Add Exercise
            </Button>
          </div>

          {exercises.length === 0 ? (
            <div className="text-center py-8 text-gray-400 border-2 border-dashed border-gray-200 rounded-lg">
              <svg className="w-12 h-12 mx-auto mb-3 text-gray-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
              </svg>
              <p className="text-sm font-medium">No exercises configured</p>
              <p className="text-xs mt-1">Click &quot;Add Exercise&quot; to start building the template</p>
            </div>
          ) : (
            <div className="space-y-2">
              {exercises.map((exercise, index) => (
                <div
                  key={index}
                  className="flex items-center gap-3 p-3 bg-gray-50 rounded-lg border border-gray-200 hover:border-gray-300 transition-colors"
                >
                  {/* Drag indicator (visual only) */}
                  <svg className="w-4 h-4 text-gray-300 cursor-grab flex-shrink-0" viewBox="0 0 16 16" fill="currentColor">
                    <circle cx="5" cy="3" r="1.5" />
                    <circle cx="11" cy="3" r="1.5" />
                    <circle cx="5" cy="8" r="1.5" />
                    <circle cx="11" cy="8" r="1.5" />
                    <circle cx="5" cy="13" r="1.5" />
                    <circle cx="11" cy="13" r="1.5" />
                  </svg>

                  <span className="text-sm font-medium text-gray-400 w-6 text-center flex-shrink-0">
                    {index + 1}
                  </span>

                  <div className="flex-1 min-w-0">
                    <span className="text-sm font-medium text-gray-900 truncate block">
                      {exerciseLookup.get(exercise.exerciseId) || 'Unknown Exercise'}
                    </span>
                  </div>

                  <Badge variant={REGION_BADGE_VARIANT[exercise.targetRegion] || 'default'} size="sm">
                    {formatLabel(exercise.targetRegion)}
                  </Badge>
                  <Badge variant="default" size="sm">
                    {formatLabel(exercise.side)}
                  </Badge>
                  <Badge variant={exercise.entryType === 'core' ? 'primary' : 'orange'} size="sm">
                    {formatLabel(exercise.entryType)}
                  </Badge>

                  <div className="flex items-center gap-1 flex-shrink-0">
                    <button
                      type="button"
                      onClick={() => moveExercise(index, 'up')}
                      disabled={index === 0}
                      className="p-1 text-gray-600 hover:text-gray-800 disabled:opacity-30 rounded"
                    >
                      <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 15l7-7 7 7" />
                      </svg>
                    </button>
                    <button
                      type="button"
                      onClick={() => moveExercise(index, 'down')}
                      disabled={index === exercises.length - 1}
                      className="p-1 text-gray-600 hover:text-gray-800 disabled:opacity-30 rounded"
                    >
                      <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                      </svg>
                    </button>
                    <button
                      type="button"
                      onClick={() => openEditDialog(index)}
                      className="p-1 text-blue-600 hover:text-blue-800 rounded"
                    >
                      <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                      </svg>
                    </button>
                    <button
                      type="button"
                      onClick={() => removeExercise(index)}
                      className="p-1 text-red-600 hover:text-red-800 rounded"
                    >
                      <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                      </svg>
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </Card>

        {/* Submit Buttons */}
        <div className="flex justify-end gap-4">
          <Button type="button" variant="outline" onClick={() => router.push('/admin/assessment-templates')}>
            Cancel
          </Button>
          <Button type="submit" disabled={loading}>
            {loading ? 'Saving...' : 'Save Changes'}
          </Button>
        </div>
      </form>

      {/* Exercise Dialog */}
      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent size="lg">
          <DialogHeader>
            <DialogTitle>{editingIndex !== null ? 'Edit Exercise' : 'Add Exercise'}</DialogTitle>
          </DialogHeader>
          <DialogBody>
            <div className="space-y-4">
              <div>
                <Label>Exercise *</Label>
                <Select
                  value={dialogForm.exerciseId}
                  onChange={(e) => setDialogForm({ ...dialogForm, exerciseId: e.target.value })}
                  options={[{ value: '', label: loadingExercises ? 'Loading...' : 'Select exercise...' }, ...exerciseOptions]}
                />
              </div>

              <div className="grid grid-cols-3 gap-4">
                <div>
                  <Label>Target Region</Label>
                  <Select
                    value={dialogForm.targetRegion}
                    onChange={(e) => setDialogForm({ ...dialogForm, targetRegion: e.target.value })}
                    options={TARGET_REGION_OPTIONS}
                  />
                </div>
                <div>
                  <Label>Side</Label>
                  <Select
                    value={dialogForm.side}
                    onChange={(e) => setDialogForm({ ...dialogForm, side: e.target.value })}
                    options={SIDE_OPTIONS}
                  />
                </div>
                <div>
                  <Label>Entry Type</Label>
                  <Select
                    value={dialogForm.entryType}
                    onChange={(e) => setDialogForm({ ...dialogForm, entryType: e.target.value as 'core' | 'adaptive' })}
                    options={ENTRY_TYPE_OPTIONS}
                  />
                </div>
              </div>

              {dialogForm.entryType === 'adaptive' && (
                <div>
                  <Label>Activation Condition (JSON)</Label>
                  <Textarea
                    value={dialogForm.activationCondition}
                    onChange={(e) => setDialogForm({ ...dialogForm, activationCondition: e.target.value })}
                    placeholder='{"minLevel": 3, "requiredScore": 80}'
                    rows={3}
                    className="font-mono text-sm"
                  />
                </div>
              )}

              <div>
                <Label>Reference Norm Degrees</Label>
                <Input
                  type="number"
                  min={0}
                  max={360}
                  value={dialogForm.referenceNormDegrees}
                  onChange={(e) => setDialogForm({ ...dialogForm, referenceNormDegrees: e.target.value })}
                  placeholder="e.g. 90"
                />
              </div>

              <div>
                <Label className="mb-2">Performance Thresholds (degrees)</Label>
                <div className="grid grid-cols-4 gap-4">
                  <div>
                    <label className="block text-xs font-medium text-green-700 mb-1">Excellent</label>
                    <Input
                      type="number"
                      min={0}
                      value={dialogForm.thresholdExcellent}
                      onChange={(e) => setDialogForm({ ...dialogForm, thresholdExcellent: e.target.value })}
                      placeholder="90"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-blue-700 mb-1">Good</label>
                    <Input
                      type="number"
                      min={0}
                      value={dialogForm.thresholdGood}
                      onChange={(e) => setDialogForm({ ...dialogForm, thresholdGood: e.target.value })}
                      placeholder="80"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-yellow-700 mb-1">Average</label>
                    <Input
                      type="number"
                      min={0}
                      value={dialogForm.thresholdAverage}
                      onChange={(e) => setDialogForm({ ...dialogForm, thresholdAverage: e.target.value })}
                      placeholder="70"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-red-700 mb-1">Limited</label>
                    <Input
                      type="number"
                      min={0}
                      value={dialogForm.thresholdLimited}
                      onChange={(e) => setDialogForm({ ...dialogForm, thresholdLimited: e.target.value })}
                      placeholder="60"
                    />
                  </div>
                </div>
              </div>
            </div>
          </DialogBody>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setDialogOpen(false)}>
              Cancel
            </Button>
            <Button type="button" onClick={saveDialogExercise}>
              {editingIndex !== null ? 'Save Changes' : 'Add Exercise'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
