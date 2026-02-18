'use client';

import { useEffect, useState } from 'react';
import { LocalizedText } from '@/lib/types/localized';
import {
  Input,
  Textarea,
  Select,
  Button,
  Badge,
  Label,
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogBody,
  DialogFooter,
} from '@/components/ui';

/* ─── Types ────────────────────────────────────────────────────── */

interface Level {
  id: string;
  number: number;
  code: string;
  name: LocalizedText;
  description: LocalizedText;
  icon: string | null;
  color: string;
  entryThreshold: number;
  maxThreshold: number | null;
  defaultSetsMin: number;
  defaultSetsMax: number;
  defaultRepsMin: number;
  defaultRepsMax: number;
  defaultIntensityGuide: string;
  defaultRestBetweenSetsMs: number;
  defaultSessionDurMin: number;
  defaultSessionDurMax: number;
  defaultWeeklyFreqMin: number;
  defaultWeeklyFreqMax: number;
  userCount?: number;
}

type IntensityGuide =
  | 'bodyweight_only'
  | 'light'
  | 'moderate'
  | 'heavy'
  | 'max_effort';

const INTENSITY_OPTIONS: { value: IntensityGuide; label: string }[] = [
  { value: 'bodyweight_only', label: 'Bodyweight Only' },
  { value: 'light', label: 'Light' },
  { value: 'moderate', label: 'Moderate' },
  { value: 'heavy', label: 'Heavy' },
  { value: 'max_effort', label: 'Max Effort' },
];

const EMPTY_FORM: Omit<Level, 'id' | 'userCount'> = {
  number: 1,
  code: '',
  name: { en: '', ar: '' },
  description: { en: '', ar: '' },
  icon: '',
  color: '#3b82f6',
  entryThreshold: 0,
  maxThreshold: null,
  defaultSetsMin: 2,
  defaultSetsMax: 4,
  defaultRepsMin: 8,
  defaultRepsMax: 12,
  defaultIntensityGuide: 'bodyweight_only',
  defaultRestBetweenSetsMs: 60000,
  defaultSessionDurMin: 20,
  defaultSessionDurMax: 40,
  defaultWeeklyFreqMin: 2,
  defaultWeeklyFreqMax: 4,
};

/* ─── Page ─────────────────────────────────────────────────────── */

export default function LevelsManagementPage() {
  const [levels, setLevels] = useState<Level[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingLevel, setEditingLevel] = useState<Level | null>(null);
  const [form, setForm] = useState<Omit<Level, 'id' | 'userCount'>>(EMPTY_FORM);

  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [deletingLevel, setDeletingLevel] = useState<Level | null>(null);

  /* ── Fetch ──────────────────────────────────────────────────── */

  const fetchLevels = async () => {
    setLoading(true);
    try {
      const res = await fetch('/api/admin/levels');
      const data = await res.json();
      if (data.success) {
        setLevels(
          [...data.data].sort((a: Level, b: Level) => a.number - b.number)
        );
      }
    } catch (error) {
      console.error('Error fetching levels:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchLevels();
  }, []);

  /* ── Create / Edit ──────────────────────────────────────────── */

  const openCreateDialog = () => {
    setEditingLevel(null);
    setForm({
      ...EMPTY_FORM,
      number: levels.length > 0 ? Math.max(...levels.map((l) => l.number)) + 1 : 1,
    });
    setDialogOpen(true);
  };

  const openEditDialog = (level: Level) => {
    setEditingLevel(level);
    setForm({
      number: level.number,
      code: level.code,
      name: { ...level.name },
      description: { ...level.description },
      icon: level.icon || '',
      color: level.color,
      entryThreshold: level.entryThreshold,
      maxThreshold: level.maxThreshold,
      defaultSetsMin: level.defaultSetsMin,
      defaultSetsMax: level.defaultSetsMax,
      defaultRepsMin: level.defaultRepsMin,
      defaultRepsMax: level.defaultRepsMax,
      defaultIntensityGuide: level.defaultIntensityGuide,
      defaultRestBetweenSetsMs: level.defaultRestBetweenSetsMs,
      defaultSessionDurMin: level.defaultSessionDurMin,
      defaultSessionDurMax: level.defaultSessionDurMax,
      defaultWeeklyFreqMin: level.defaultWeeklyFreqMin,
      defaultWeeklyFreqMax: level.defaultWeeklyFreqMax,
    });
    setDialogOpen(true);
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      const payload = {
        ...form,
        icon: form.icon || null,
        maxThreshold: form.maxThreshold ?? null,
      };

      const url = editingLevel
        ? `/api/admin/levels/${editingLevel.id}`
        : '/api/admin/levels';
      const method = editingLevel ? 'PUT' : 'POST';

      const res = await fetch(url, {
        method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });

      if (res.ok) {
        setDialogOpen(false);
        fetchLevels();
      } else {
        const err = await res.json();
        alert(err.message || 'Failed to save level');
      }
    } catch (error) {
      console.error('Error saving level:', error);
    } finally {
      setSaving(false);
    }
  };

  /* ── Delete ─────────────────────────────────────────────────── */

  const openDeleteDialog = (level: Level) => {
    setDeletingLevel(level);
    setDeleteDialogOpen(true);
  };

  const handleDelete = async () => {
    if (!deletingLevel) return;
    setSaving(true);
    try {
      const res = await fetch(`/api/admin/levels/${deletingLevel.id}`, {
        method: 'DELETE',
      });
      if (res.ok) {
        setDeleteDialogOpen(false);
        setDeletingLevel(null);
        fetchLevels();
      }
    } catch (error) {
      console.error('Error deleting level:', error);
    } finally {
      setSaving(false);
    }
  };

  /* ── Helpers ────────────────────────────────────────────────── */

  const intensityLabel = (guide: string) =>
    INTENSITY_OPTIONS.find((o) => o.value === guide)?.label ?? guide;

  const formatRestSeconds = (ms: number) => {
    const seconds = Math.round(ms / 1000);
    return seconds >= 60 ? `${Math.round(seconds / 60)}m` : `${seconds}s`;
  };

  const sortedLevels = [...levels].sort((a, b) => a.entryThreshold - b.entryThreshold);

  /* ── Render ─────────────────────────────────────────────────── */

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Levels Management</h1>
          <p className="text-gray-600 mt-1">Manage training levels and thresholds</p>
        </div>
        <Button onClick={openCreateDialog} icon={
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
          </svg>
        }>
          Add Level
        </Button>
      </div>

      {/* Threshold Bar Visualization */}
      {levels.length > 0 && (
        <div className="bg-white p-5 rounded-lg shadow-sm border border-gray-200">
          <h2 className="text-sm font-semibold text-gray-700 mb-3 flex items-center gap-2">
            <svg className="w-4 h-4 text-gray-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
            </svg>
            Threshold Distribution (0–100)
          </h2>

          {/* Tick marks */}
          <div className="relative h-3 mb-1">
            {[0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100].map((tick) => (
              <span
                key={tick}
                className="absolute text-[10px] text-gray-400 -translate-x-1/2"
                style={{ left: `${tick}%` }}
              >
                {tick}
              </span>
            ))}
          </div>

          {/* Bar */}
          <div className="relative h-10 bg-gray-100 rounded-lg overflow-hidden border border-gray-200">
            {sortedLevels.map((level) => {
              const start = level.entryThreshold;
              const end = level.maxThreshold ?? 100;
              const width = end - start;
              return (
                <div
                  key={level.id}
                  className="absolute top-0 bottom-0 flex items-center justify-center text-[11px] font-semibold text-white transition-all"
                  style={{
                    left: `${start}%`,
                    width: `${width}%`,
                    backgroundColor: level.color,
                  }}
                  title={`${level.name.en}: ${start}–${end}`}
                >
                  {width > 8 && (
                    <span className="truncate px-1 drop-shadow-sm">
                      {level.code}
                    </span>
                  )}
                </div>
              );
            })}
          </div>

          {/* Legend */}
          <div className="flex flex-wrap gap-3 mt-3">
            {sortedLevels.map((level) => (
              <div key={level.id} className="flex items-center gap-1.5 text-xs text-gray-600">
                <span
                  className="w-3 h-3 rounded-sm inline-block flex-shrink-0"
                  style={{ backgroundColor: level.color }}
                />
                <span className="font-medium">{level.name.en}</span>
                <span className="text-gray-400">
                  ({level.entryThreshold}–{level.maxThreshold ?? 100})
                </span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Levels Table */}
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
        {loading ? (
          <div className="p-8 text-center text-gray-500">Loading...</div>
        ) : levels.length === 0 ? (
          <div className="p-8 text-center text-gray-500">
            <svg className="w-12 h-12 mx-auto text-gray-300 mb-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
                d="M19.5 14.25v-2.625a3.375 3.375 0 00-3.375-3.375h-1.5A1.125 1.125 0 0113.5 7.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25m3.75 9v6m3-3H9m1.5-12H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 00-9-9z" />
            </svg>
            <p className="font-medium">No levels found</p>
            <button onClick={openCreateDialog} className="text-blue-600 hover:underline mt-2 inline-block text-sm">
              Create your first level
            </button>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-gray-50 border-b border-gray-200">
                <tr>
                  <th className="px-5 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Level
                  </th>
                  <th className="px-5 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Code
                  </th>
                  <th className="px-5 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Name
                  </th>
                  <th className="px-5 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Threshold Range
                  </th>
                  <th className="px-5 py-3 text-center text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Users
                  </th>
                  <th className="px-5 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Training Defaults
                  </th>
                  <th className="px-5 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Actions
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200">
                {levels.map((level) => (
                  <tr key={level.id} className="hover:bg-gray-50 transition-colors">
                    {/* Level number + color */}
                    <td className="px-5 py-4">
                      <div className="flex items-center gap-2.5">
                        <span
                          className="w-8 h-8 rounded-lg flex items-center justify-center text-white text-sm font-bold shadow-sm"
                          style={{ backgroundColor: level.color }}
                        >
                          {level.number}
                        </span>
                      </div>
                    </td>

                    {/* Code */}
                    <td className="px-5 py-4">
                      <Badge variant="outline" size="sm">
                        {level.code}
                      </Badge>
                    </td>

                    {/* Name EN / AR */}
                    <td className="px-5 py-4">
                      <div>
                        <p className="font-medium text-gray-900">{level.name.en}</p>
                        <p className="text-sm text-gray-500" dir="rtl">{level.name.ar}</p>
                      </div>
                    </td>

                    {/* Threshold range */}
                    <td className="px-5 py-4">
                      <div className="flex items-center gap-1.5">
                        <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-blue-50 text-blue-700">
                          {level.entryThreshold}
                        </span>
                        <svg className="w-3.5 h-3.5 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14 5l7 7m0 0l-7 7m7-7H3" />
                        </svg>
                        <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-blue-50 text-blue-700">
                          {level.maxThreshold ?? 100}
                        </span>
                      </div>
                    </td>

                    {/* User count */}
                    <td className="px-5 py-4 text-center">
                      <span className="inline-flex items-center justify-center min-w-[28px] px-2 py-0.5 rounded-full text-xs font-semibold bg-gray-100 text-gray-700">
                        {level.userCount ?? 0}
                      </span>
                    </td>

                    {/* Training defaults summary */}
                    <td className="px-5 py-4">
                      <div className="flex flex-wrap gap-1.5">
                        <Badge variant="default" size="sm">
                          {level.defaultSetsMin}–{level.defaultSetsMax} sets
                        </Badge>
                        <Badge variant="default" size="sm">
                          {level.defaultRepsMin}–{level.defaultRepsMax} reps
                        </Badge>
                        <Badge variant="primary" size="sm">
                          {intensityLabel(level.defaultIntensityGuide)}
                        </Badge>
                      </div>
                    </td>

                    {/* Actions */}
                    <td className="px-5 py-4 text-right">
                      <div className="flex justify-end gap-2">
                        <button
                          onClick={() => openEditDialog(level)}
                          className="text-blue-600 hover:text-blue-800 text-sm font-medium"
                        >
                          Edit
                        </button>
                        <button
                          onClick={() => openDeleteDialog(level)}
                          className="text-red-600 hover:text-red-800 text-sm font-medium"
                        >
                          Delete
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* ── Create / Edit Dialog ──────────────────────────────── */}
      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent size="xl">
          <DialogHeader>
            <DialogTitle>
              {editingLevel ? 'Edit Level' : 'Create Level'}
            </DialogTitle>
            <DialogDescription>
              {editingLevel
                ? `Editing "${editingLevel.name.en}" — update level details and training defaults.`
                : 'Fill in the details below to create a new training level.'}
            </DialogDescription>
          </DialogHeader>

          <DialogBody>
            <div className="space-y-6">
              {/* ── Basic Info ────────────────────────────────── */}
              <fieldset>
                <legend className="text-sm font-semibold text-gray-800 mb-3 pb-1.5 border-b border-gray-100 w-full">
                  Basic Information
                </legend>

                <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                  <div>
                    <Label required>Level Number</Label>
                    <Input
                      type="number"
                      min={1}
                      value={form.number}
                      onChange={(e) => setForm({ ...form, number: Number(e.target.value) })}
                    />
                  </div>
                  <div>
                    <Label required>Code</Label>
                    <Input
                      placeholder="e.g. foundation"
                      value={form.code}
                      onChange={(e) => setForm({ ...form, code: e.target.value })}
                    />
                  </div>
                  <div>
                    <Label>Icon</Label>
                    <Input
                      placeholder="e.g. star"
                      value={form.icon || ''}
                      onChange={(e) => setForm({ ...form, icon: e.target.value })}
                    />
                  </div>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mt-4">
                  <div>
                    <Label required>Name (English)</Label>
                    <Input
                      placeholder="e.g. Foundation"
                      value={form.name.en}
                      onChange={(e) =>
                        setForm({ ...form, name: { ...form.name, en: e.target.value } })
                      }
                    />
                  </div>
                  <div>
                    <Label required>Name (Arabic)</Label>
                    <Input
                      placeholder="e.g. الأساسيات"
                      dir="rtl"
                      value={form.name.ar}
                      onChange={(e) =>
                        setForm({ ...form, name: { ...form.name, ar: e.target.value } })
                      }
                    />
                  </div>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mt-4">
                  <div>
                    <Label>Description (English)</Label>
                    <Textarea
                      placeholder="Brief description..."
                      rows={2}
                      value={form.description.en}
                      onChange={(e) =>
                        setForm({
                          ...form,
                          description: { ...form.description, en: e.target.value },
                        })
                      }
                    />
                  </div>
                  <div>
                    <Label>Description (Arabic)</Label>
                    <Textarea
                      placeholder="وصف مختصر..."
                      dir="rtl"
                      rows={2}
                      value={form.description.ar}
                      onChange={(e) =>
                        setForm({
                          ...form,
                          description: { ...form.description, ar: e.target.value },
                        })
                      }
                    />
                  </div>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mt-4">
                  <div>
                    <Label required>Color</Label>
                    <div className="flex items-center gap-3">
                      <input
                        type="color"
                        value={form.color}
                        onChange={(e) => setForm({ ...form, color: e.target.value })}
                        className="w-10 h-10 rounded-lg border-2 border-gray-200 cursor-pointer p-0.5"
                      />
                      <Input
                        value={form.color}
                        onChange={(e) => setForm({ ...form, color: e.target.value })}
                        className="flex-1"
                        placeholder="#3b82f6"
                      />
                    </div>
                  </div>
                  <div>
                    <Label required tooltip="Minimum score to enter this level (0-100)">
                      Entry Threshold
                    </Label>
                    <Input
                      type="number"
                      min={0}
                      max={100}
                      value={form.entryThreshold}
                      onChange={(e) =>
                        setForm({ ...form, entryThreshold: Number(e.target.value) })
                      }
                    />
                  </div>
                  <div>
                    <Label tooltip="Maximum score for this level (leave empty for open-ended)">
                      Max Threshold
                    </Label>
                    <Input
                      type="number"
                      min={0}
                      max={100}
                      placeholder="100"
                      value={form.maxThreshold ?? ''}
                      onChange={(e) =>
                        setForm({
                          ...form,
                          maxThreshold: e.target.value ? Number(e.target.value) : null,
                        })
                      }
                    />
                  </div>
                </div>
              </fieldset>

              {/* ── Training Defaults ────────────────────────── */}
              <fieldset>
                <legend className="text-sm font-semibold text-gray-800 mb-3 pb-1.5 border-b border-gray-100 w-full">
                  Default Training Parameters
                </legend>

                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                  {/* Sets range */}
                  <div>
                    <Label required>Sets (min – max)</Label>
                    <div className="flex items-center gap-2">
                      <Input
                        type="number"
                        min={1}
                        value={form.defaultSetsMin}
                        onChange={(e) =>
                          setForm({ ...form, defaultSetsMin: Number(e.target.value) })
                        }
                      />
                      <span className="text-gray-400 font-medium">–</span>
                      <Input
                        type="number"
                        min={1}
                        value={form.defaultSetsMax}
                        onChange={(e) =>
                          setForm({ ...form, defaultSetsMax: Number(e.target.value) })
                        }
                      />
                    </div>
                  </div>

                  {/* Reps range */}
                  <div>
                    <Label required>Reps (min – max)</Label>
                    <div className="flex items-center gap-2">
                      <Input
                        type="number"
                        min={1}
                        value={form.defaultRepsMin}
                        onChange={(e) =>
                          setForm({ ...form, defaultRepsMin: Number(e.target.value) })
                        }
                      />
                      <span className="text-gray-400 font-medium">–</span>
                      <Input
                        type="number"
                        min={1}
                        value={form.defaultRepsMax}
                        onChange={(e) =>
                          setForm({ ...form, defaultRepsMax: Number(e.target.value) })
                        }
                      />
                    </div>
                  </div>

                  {/* Intensity guide */}
                  <div>
                    <Label required>Intensity Guide</Label>
                    <Select
                      value={form.defaultIntensityGuide}
                      onChange={(e) =>
                        setForm({ ...form, defaultIntensityGuide: e.target.value })
                      }
                      options={INTENSITY_OPTIONS.map((o) => ({
                        value: o.value,
                        label: o.label,
                      }))}
                    />
                  </div>

                  {/* Rest between sets */}
                  <div>
                    <Label required tooltip="Rest time in milliseconds between sets">
                      Rest Between Sets (ms)
                    </Label>
                    <Input
                      type="number"
                      min={0}
                      step={1000}
                      value={form.defaultRestBetweenSetsMs}
                      onChange={(e) =>
                        setForm({
                          ...form,
                          defaultRestBetweenSetsMs: Number(e.target.value),
                        })
                      }
                      helperText={`= ${formatRestSeconds(form.defaultRestBetweenSetsMs)}`}
                    />
                  </div>

                  {/* Session duration range */}
                  <div>
                    <Label required>Session Duration (min – max minutes)</Label>
                    <div className="flex items-center gap-2">
                      <Input
                        type="number"
                        min={1}
                        value={form.defaultSessionDurMin}
                        onChange={(e) =>
                          setForm({
                            ...form,
                            defaultSessionDurMin: Number(e.target.value),
                          })
                        }
                      />
                      <span className="text-gray-400 font-medium">–</span>
                      <Input
                        type="number"
                        min={1}
                        value={form.defaultSessionDurMax}
                        onChange={(e) =>
                          setForm({
                            ...form,
                            defaultSessionDurMax: Number(e.target.value),
                          })
                        }
                      />
                    </div>
                  </div>

                  {/* Weekly frequency range */}
                  <div>
                    <Label required>Weekly Frequency (min – max days/week)</Label>
                    <div className="flex items-center gap-2">
                      <Input
                        type="number"
                        min={1}
                        max={7}
                        value={form.defaultWeeklyFreqMin}
                        onChange={(e) =>
                          setForm({
                            ...form,
                            defaultWeeklyFreqMin: Number(e.target.value),
                          })
                        }
                      />
                      <span className="text-gray-400 font-medium">–</span>
                      <Input
                        type="number"
                        min={1}
                        max={7}
                        value={form.defaultWeeklyFreqMax}
                        onChange={(e) =>
                          setForm({
                            ...form,
                            defaultWeeklyFreqMax: Number(e.target.value),
                          })
                        }
                      />
                    </div>
                  </div>
                </div>
              </fieldset>
            </div>
          </DialogBody>

          <DialogFooter>
            <Button variant="secondary" onClick={() => setDialogOpen(false)} disabled={saving}>
              Cancel
            </Button>
            <Button onClick={handleSave} loading={saving}>
              {editingLevel ? 'Save Changes' : 'Create Level'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* ── Delete Confirmation Dialog ───────────────────────── */}
      <Dialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <DialogContent size="sm">
          <DialogHeader>
            <DialogTitle>Delete Level</DialogTitle>
            <DialogDescription>
              This action cannot be undone.
            </DialogDescription>
          </DialogHeader>

          <DialogBody>
            {deletingLevel && (
              <div className="flex items-start gap-3 p-3 bg-red-50 rounded-lg border border-red-100">
                <svg className="w-5 h-5 text-red-500 mt-0.5 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                    d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L4.082 16.5c-.77.833.192 2.5 1.732 2.5z" />
                </svg>
                <div>
                  <p className="text-sm text-gray-700">
                    Are you sure you want to delete level{' '}
                    <span className="font-semibold">"{deletingLevel.name.en}"</span>?
                  </p>
                  {(deletingLevel.userCount ?? 0) > 0 && (
                    <p className="text-sm text-red-600 font-medium mt-1">
                      Warning: {deletingLevel.userCount} user(s) are currently at this level.
                    </p>
                  )}
                </div>
              </div>
            )}
          </DialogBody>

          <DialogFooter>
            <Button
              variant="secondary"
              onClick={() => setDeleteDialogOpen(false)}
              disabled={saving}
            >
              Cancel
            </Button>
            <Button variant="danger" onClick={handleDelete} loading={saving}>
              Delete Level
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
