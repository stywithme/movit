'use client';

import { useEffect, useState } from 'react';
import { toast } from 'sonner';
import { BarChart3, Plus } from 'lucide-react';
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
import { ConfirmDialog, DataTable, PageHeader, type DataTableColumn } from '@/components/common';

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
  defaultWorkoutDurMin: number;
  defaultWorkoutDurMax: number;
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
  defaultWorkoutDurMin: 20,
  defaultWorkoutDurMax: 40,
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
      defaultWorkoutDurMin: level.defaultWorkoutDurMin,
      defaultWorkoutDurMax: level.defaultWorkoutDurMax,
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
        toast.success(editingLevel ? 'Level updated' : 'Level created');
        fetchLevels();
      } else {
        const err = await res.json();
        toast.error(err.message || 'Failed to save level');
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

  const columns: DataTableColumn<Level>[] = [
    {
      key: 'level',
      header: 'Level',
      cell: (level) => (
        <span
          className="flex size-8 items-center justify-center rounded-lg text-sm font-bold text-white shadow-sm"
          style={{ backgroundColor: level.color }}
        >
          {level.number}
        </span>
      ),
      className: 'w-20',
      headerClassName: 'w-20',
    },
    {
      key: 'code',
      header: 'Code',
      cell: (level) => (
        <Badge variant="outline" size="sm">
          {level.code}
        </Badge>
      ),
    },
    {
      key: 'name',
      header: 'Name',
      cell: (level) => (
        <div className="min-w-[220px]">
          <p className="font-medium">{level.name.en}</p>
          <p className="text-sm text-muted-foreground" dir="rtl">
            {level.name.ar}
          </p>
        </div>
      ),
    },
    {
      key: 'threshold',
      header: 'Threshold Range',
      cell: (level) => (
        <div className="flex items-center gap-1.5">
          <Badge variant="secondary">{level.entryThreshold}</Badge>
          <span className="text-muted-foreground">→</span>
          <Badge variant="secondary">{level.maxThreshold ?? 100}</Badge>
        </div>
      ),
    },
    {
      key: 'users',
      header: 'Users',
      headerClassName: 'text-center',
      className: 'text-center',
      cell: (level) => <span className="font-medium text-muted-foreground">{level.userCount ?? 0}</span>,
    },
    {
      key: 'defaults',
      header: 'Training Defaults',
      cell: (level) => (
        <div className="flex min-w-[260px] flex-wrap gap-1.5">
          <Badge variant="secondary" size="sm">
            {level.defaultSetsMin}–{level.defaultSetsMax} sets
          </Badge>
          <Badge variant="secondary" size="sm">
            {level.defaultRepsMin}–{level.defaultRepsMax} reps
          </Badge>
          <Badge variant="primary" size="sm">
            {intensityLabel(level.defaultIntensityGuide)}
          </Badge>
        </div>
      ),
    },
    {
      key: 'actions',
      header: <span className="sr-only">Actions</span>,
      headerClassName: 'text-right',
      className: 'text-right',
      cell: (level) => (
        <div className="flex justify-end gap-1">
          <Button type="button" variant="ghost" size="sm" onClick={() => openEditDialog(level)}>
            Edit
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="text-destructive hover:text-destructive"
            onClick={() => openDeleteDialog(level)}
          >
            Delete
          </Button>
        </div>
      ),
    },
  ];

  /* ── Render ─────────────────────────────────────────────────── */

  return (
    <div className="space-y-6">
      <PageHeader
        title="Levels Management"
        description="Manage training levels, thresholds, and default prescription settings."
        actions={
          <Button type="button" onClick={openCreateDialog}>
            <Plus className="size-4" />
            Add Level
          </Button>
        }
      />

      {/* Threshold Bar Visualization */}
      {levels.length > 0 && (
        <div className="rounded-xl border bg-card p-5 shadow-sm">
          <h2 className="mb-3 flex items-center gap-2 text-sm font-semibold text-foreground">
            <BarChart3 className="size-4 text-muted-foreground" />
            Threshold Distribution (0–100)
          </h2>

          {/* Tick marks */}
          <div className="relative h-3 mb-1">
            {[0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100].map((tick) => (
              <span
                key={tick}
                className="absolute -translate-x-1/2 text-[10px] text-muted-foreground"
                style={{ left: `${tick}%` }}
              >
                {tick}
              </span>
            ))}
          </div>

          {/* Bar */}
          <div className="relative h-10 overflow-hidden rounded-lg border bg-muted">
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
              <div key={level.id} className="flex items-center gap-1.5 text-xs text-muted-foreground">
                <span
                  className="w-3 h-3 rounded-sm inline-block flex-shrink-0"
                  style={{ backgroundColor: level.color }}
                />
                <span className="font-medium">{level.name.en}</span>
                <span className="text-muted-foreground">
                  ({level.entryThreshold}–{level.maxThreshold ?? 100})
                </span>
              </div>
            ))}
          </div>
        </div>
      )}

      <DataTable
        columns={columns}
        data={levels}
        getRowKey={(level) => level.id}
        loading={loading}
        emptyTitle="No levels found"
        emptyDescription="Create your first level to define threshold ranges and training defaults."
      />

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
                <legend className="mb-3 w-full border-b pb-1.5 text-sm font-semibold text-foreground">
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
                        className="h-10 w-10 cursor-pointer rounded-lg border-2 border-input p-0.5"
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
                <legend className="mb-3 w-full border-b pb-1.5 text-sm font-semibold text-foreground">
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
                      <span className="font-medium text-muted-foreground">–</span>
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
                      <span className="font-medium text-muted-foreground">–</span>
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

                  {/* Workout duration range */}
                  <div>
                    <Label required>Workout Duration (min – max minutes)</Label>
                    <div className="flex items-center gap-2">
                      <Input
                        type="number"
                        min={1}
                        value={form.defaultWorkoutDurMin}
                        onChange={(e) =>
                          setForm({
                            ...form,
                            defaultWorkoutDurMin: Number(e.target.value),
                          })
                        }
                      />
                      <span className="font-medium text-muted-foreground">–</span>
                      <Input
                        type="number"
                        min={1}
                        value={form.defaultWorkoutDurMax}
                        onChange={(e) =>
                          setForm({
                            ...form,
                            defaultWorkoutDurMax: Number(e.target.value),
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
                      <span className="font-medium text-muted-foreground">–</span>
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

      <ConfirmDialog
        open={deleteDialogOpen}
        onOpenChange={setDeleteDialogOpen}
        title="Delete level?"
        description={
          deletingLevel
            ? `Delete "${deletingLevel.name.en}"? This action cannot be undone${
                (deletingLevel.userCount ?? 0) > 0
                  ? `, and ${deletingLevel.userCount} user(s) are currently at this level`
                  : ''
              }.`
            : 'This action cannot be undone.'
        }
        confirmLabel="Delete Level"
        destructive
        loading={saving}
        onConfirm={handleDelete}
      />
    </div>
  );
}
