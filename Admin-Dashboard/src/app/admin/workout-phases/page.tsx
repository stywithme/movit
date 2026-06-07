'use client';

import { useEffect, useMemo, useState } from 'react';
import { toast } from 'sonner';
import { Layers, Plus } from 'lucide-react';
import { LocalizedText } from '@/lib/types/localized';
import {
  Badge,
  Button,
  Checkbox,
  Dialog,
  DialogBody,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  Input,
  Label,
  Select,
  Textarea,
} from '@/components/ui';
import { ConfirmDialog, DataTable, FilterBar, PageHeader, type DataTableColumn } from '@/components/common';

type WorkoutBlockRole =
  | 'WARMUP'
  | 'ACTIVATION'
  | 'MAIN'
  | 'ACCESSORY'
  | 'CORRECTIVE'
  | 'COOLDOWN'
  | 'TEST';

interface WorkoutPhase {
  id: string;
  slug: string;
  name: LocalizedText;
  description: LocalizedText | null;
  role: WorkoutBlockRole;
  canSkip: boolean;
  canContinue: boolean;
  maxContinueTimeMs: number | null;
  color: string | null;
  icon: string | null;
  isActive: boolean;
  sortOrder: number;
  _count?: {
    templatePhases: number;
  };
}

type WorkoutPhaseForm = Omit<WorkoutPhase, 'id' | '_count'>;

const ROLE_OPTIONS: { value: WorkoutBlockRole; label: string }[] = [
  { value: 'WARMUP', label: 'Warm-up' },
  { value: 'ACTIVATION', label: 'Activation' },
  { value: 'MAIN', label: 'Main' },
  { value: 'ACCESSORY', label: 'Accessory' },
  { value: 'CORRECTIVE', label: 'Corrective' },
  { value: 'COOLDOWN', label: 'Cool-down' },
  { value: 'TEST', label: 'Test' },
];

const EMPTY_FORM: WorkoutPhaseForm = {
  slug: '',
  name: { en: '', ar: '' },
  description: { en: '', ar: '' },
  role: 'MAIN',
  canSkip: false,
  canContinue: true,
  maxContinueTimeMs: null,
  color: '#2563eb',
  icon: '',
  isActive: true,
  sortOrder: 0,
};

function msToSeconds(value: number | null) {
  return value === null ? '' : Math.round(value / 1000).toString();
}

function secondsToMs(value: string) {
  if (!value.trim()) return null;
  return Math.max(0, Number(value) * 1000);
}

export default function WorkoutPhasesPage() {
  const [phases, setPhases] = useState<WorkoutPhase[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [activeFilter, setActiveFilter] = useState('');
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingPhase, setEditingPhase] = useState<WorkoutPhase | null>(null);
  const [form, setForm] = useState<WorkoutPhaseForm>(EMPTY_FORM);
  const [deletePhase, setDeletePhase] = useState<WorkoutPhase | null>(null);

  const fetchPhases = async () => {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      if (searchQuery.trim()) params.set('search', searchQuery.trim());
      if (activeFilter) params.set('active', activeFilter);

      const res = await fetch(`/api/workout-phases?${params}`);
      const data = await res.json();
      if (data.success) {
        setPhases(data.data);
      } else {
        toast.error(data.error || 'Failed to load workout phases');
      }
    } catch (error) {
      console.error('Error fetching workout phases:', error);
      toast.error('Failed to load workout phases');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    const timer = window.setTimeout(fetchPhases, 250);
    return () => window.clearTimeout(timer);
  }, [searchQuery, activeFilter]);

  const openCreateDialog = () => {
    setEditingPhase(null);
    setForm({
      ...EMPTY_FORM,
      sortOrder: phases.length > 0 ? Math.max(...phases.map((phase) => phase.sortOrder)) + 10 : 0,
    });
    setDialogOpen(true);
  };

  const openEditDialog = (phase: WorkoutPhase) => {
    setEditingPhase(phase);
    setForm({
      slug: phase.slug,
      name: { ...phase.name },
      description: phase.description ? { ...phase.description } : { en: '', ar: '' },
      role: phase.role,
      canSkip: phase.canSkip,
      canContinue: phase.canContinue,
      maxContinueTimeMs: phase.maxContinueTimeMs,
      color: phase.color || '#2563eb',
      icon: phase.icon || '',
      isActive: phase.isActive,
      sortOrder: phase.sortOrder,
    });
    setDialogOpen(true);
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      const payload = {
        ...form,
        slug: form.slug.trim() || undefined,
        description:
          form.description?.en || form.description?.ar
            ? form.description
            : undefined,
        color: form.color || null,
        icon: form.icon || null,
      };

      const url = editingPhase ? `/api/workout-phases/${editingPhase.id}` : '/api/workout-phases';
      const method = editingPhase ? 'PUT' : 'POST';
      const res = await fetch(url, {
        method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      const data = await res.json();

      if (data.success) {
        setDialogOpen(false);
        toast.success(editingPhase ? 'Workout phase updated' : 'Workout phase created');
        fetchPhases();
      } else {
        toast.error(data.errors?.join('\n') || data.error || 'Failed to save workout phase');
      }
    } catch (error) {
      console.error('Error saving workout phase:', error);
      toast.error('Failed to save workout phase');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    if (!deletePhase) return;
    setSaving(true);
    try {
      const res = await fetch(`/api/workout-phases/${deletePhase.id}`, { method: 'DELETE' });
      const data = await res.json();
      if (data.success) {
        setDeletePhase(null);
        toast.success('Workout phase deleted');
        fetchPhases();
      } else {
        toast.error(data.error || 'Failed to delete workout phase');
      }
    } catch (error) {
      console.error('Error deleting workout phase:', error);
      toast.error('Failed to delete workout phase');
    } finally {
      setSaving(false);
    }
  };

  const columns = useMemo<DataTableColumn<WorkoutPhase>[]>(
    () => [
      {
        key: 'phase',
        header: 'Phase',
        cell: (phase) => (
          <div className="flex min-w-[260px] items-center gap-3">
            <span
              className="flex size-9 shrink-0 items-center justify-center rounded-lg border text-xs font-semibold text-white"
              style={{ backgroundColor: phase.color || '#64748b' }}
            >
              {phase.role.slice(0, 1)}
            </span>
            <div className="min-w-0">
              <p className="truncate font-medium">{phase.name.en}</p>
              <p className="truncate text-sm text-muted-foreground" dir="rtl">
                {phase.name.ar}
              </p>
            </div>
          </div>
        ),
      },
      {
        key: 'slug',
        header: 'Slug',
        cell: (phase) => <Badge variant="outline">{phase.slug}</Badge>,
      },
      {
        key: 'role',
        header: 'Role',
        cell: (phase) => <Badge variant="secondary">{ROLE_OPTIONS.find((role) => role.value === phase.role)?.label}</Badge>,
      },
      {
        key: 'settings',
        header: 'Settings',
        cell: (phase) => (
          <div className="flex min-w-[220px] flex-wrap gap-1.5">
            <Badge variant={phase.canSkip ? 'primary' : 'outline'} size="sm">
              {phase.canSkip ? 'Skippable' : 'Required'}
            </Badge>
            <Badge variant={phase.canContinue ? 'secondary' : 'outline'} size="sm">
              {phase.canContinue ? 'Continue' : 'Restart'}
            </Badge>
            {phase.maxContinueTimeMs !== null && (
              <Badge variant="outline" size="sm">
                {Math.round(phase.maxContinueTimeMs / 1000)}s window
              </Badge>
            )}
          </div>
        ),
      },
      {
        key: 'active',
        header: 'Status',
        cell: (phase) => (
          <Badge variant={phase.isActive ? 'success' : 'outline'}>{phase.isActive ? 'Active' : 'Inactive'}</Badge>
        ),
      },
      {
        key: 'actions',
        header: <span className="sr-only">Actions</span>,
        headerClassName: 'text-right',
        className: 'text-right',
        cell: (phase) => (
          <div className="flex justify-end gap-1">
            <Button type="button" variant="ghost" size="sm" onClick={() => openEditDialog(phase)}>
              Edit
            </Button>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              className="text-destructive hover:text-destructive"
              onClick={() => setDeletePhase(phase)}
            >
              Delete
            </Button>
          </div>
        ),
      },
    ],
    []
  );

  return (
    <div className="space-y-6">
      <PageHeader
        title="Workout Phases"
        description="Manage reusable phase presets for workout templates."
        actions={
          <Button type="button" onClick={openCreateDialog}>
            <Plus className="size-4" />
            Add phase
          </Button>
        }
      />

      <FilterBar
        searchValue={searchQuery}
        searchPlaceholder="Name or slug..."
        onSearchChange={setSearchQuery}
        selects={[
          {
            id: 'active',
            value: activeFilter,
            onChange: setActiveFilter,
            options: [
              { value: '', label: 'All statuses' },
              { value: 'true', label: 'Active' },
              { value: 'false', label: 'Inactive' },
            ],
          },
        ]}
        onReset={() => {
          setSearchQuery('');
          setActiveFilter('');
        }}
      />

      <DataTable
        columns={columns}
        data={phases}
        getRowKey={(phase) => phase.id}
        loading={loading}
        emptyTitle="No workout phases found"
        emptyDescription="Create reusable Warm-up, Main, Cool-down, or custom phase presets."
      />

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent size="xl">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <Layers className="size-5 text-muted-foreground" />
              {editingPhase ? 'Edit Workout Phase' : 'Create Workout Phase'}
            </DialogTitle>
            <DialogDescription>
              Configure the catalog preset used by workout template phases.
            </DialogDescription>
          </DialogHeader>

          <DialogBody>
            <div className="space-y-6">
              <fieldset>
                <legend className="mb-3 w-full border-b pb-1.5 text-sm font-semibold text-foreground">
                  Basic Information
                </legend>
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                  <div>
                    <Label required>Name (English)</Label>
                    <Input
                      value={form.name.en}
                      onChange={(event) => setForm({ ...form, name: { ...form.name, en: event.target.value } })}
                    />
                  </div>
                  <div>
                    <Label required>Name (Arabic)</Label>
                    <Input
                      dir="rtl"
                      value={form.name.ar}
                      onChange={(event) => setForm({ ...form, name: { ...form.name, ar: event.target.value } })}
                    />
                  </div>
                </div>
                <div className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-2">
                  <div>
                    <Label>Description (English)</Label>
                    <Textarea
                      rows={2}
                      value={form.description?.en || ''}
                      onChange={(event) =>
                        setForm({
                          ...form,
                          description: { ...(form.description || { en: '', ar: '' }), en: event.target.value },
                        })
                      }
                    />
                  </div>
                  <div>
                    <Label>Description (Arabic)</Label>
                    <Textarea
                      dir="rtl"
                      rows={2}
                      value={form.description?.ar || ''}
                      onChange={(event) =>
                        setForm({
                          ...form,
                          description: { ...(form.description || { en: '', ar: '' }), ar: event.target.value },
                        })
                      }
                    />
                  </div>
                </div>
                <div className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-3">
                  <div>
                    <Label>Slug</Label>
                    <Input
                      value={form.slug}
                      placeholder="auto-generated"
                      onChange={(event) => setForm({ ...form, slug: event.target.value })}
                    />
                  </div>
                  <div>
                    <Label required>Role</Label>
                    <Select
                      value={form.role}
                      onChange={(event) => setForm({ ...form, role: event.target.value as WorkoutBlockRole })}
                      options={ROLE_OPTIONS}
                    />
                  </div>
                  <div>
                    <Label>Sort Order</Label>
                    <Input
                      type="number"
                      min={0}
                      value={form.sortOrder}
                      onChange={(event) => setForm({ ...form, sortOrder: Number(event.target.value) })}
                    />
                  </div>
                </div>
              </fieldset>

              <fieldset>
                <legend className="mb-3 w-full border-b pb-1.5 text-sm font-semibold text-foreground">
                  Runtime Behavior
                </legend>
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
                  <label className="flex min-h-10 items-center gap-2 rounded-lg border px-3 py-2 text-sm">
                    <Checkbox
                      checked={form.canSkip}
                      onCheckedChange={(checked) => setForm({ ...form, canSkip: Boolean(checked) })}
                    />
                    Can skip
                  </label>
                  <label className="flex min-h-10 items-center gap-2 rounded-lg border px-3 py-2 text-sm">
                    <Checkbox
                      checked={form.canContinue}
                      onCheckedChange={(checked) => setForm({ ...form, canContinue: Boolean(checked) })}
                    />
                    Can continue
                  </label>
                  <label className="flex min-h-10 items-center gap-2 rounded-lg border px-3 py-2 text-sm">
                    <Checkbox
                      checked={form.isActive}
                      onCheckedChange={(checked) => setForm({ ...form, isActive: Boolean(checked) })}
                    />
                    Active
                  </label>
                </div>
                <div className="mt-4 max-w-xs">
                  <Label>Max Continue Window (seconds)</Label>
                  <Input
                    type="number"
                    min={0}
                    placeholder="No limit"
                    value={msToSeconds(form.maxContinueTimeMs)}
                    onChange={(event) => setForm({ ...form, maxContinueTimeMs: secondsToMs(event.target.value) })}
                  />
                </div>
              </fieldset>

              <fieldset>
                <legend className="mb-3 w-full border-b pb-1.5 text-sm font-semibold text-foreground">
                  Display Hints
                </legend>
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                  <div>
                    <Label>Color</Label>
                    <div className="flex items-center gap-3">
                      <input
                        type="color"
                        value={form.color || '#2563eb'}
                        onChange={(event) => setForm({ ...form, color: event.target.value })}
                        className="h-10 w-10 cursor-pointer rounded-lg border-2 border-input p-0.5"
                      />
                      <Input
                        value={form.color || ''}
                        onChange={(event) => setForm({ ...form, color: event.target.value })}
                        placeholder="#2563eb"
                      />
                    </div>
                  </div>
                  <div>
                    <Label>Icon</Label>
                    <Input
                      value={form.icon || ''}
                      placeholder="e.g. dumbbell"
                      onChange={(event) => setForm({ ...form, icon: event.target.value })}
                    />
                  </div>
                </div>
              </fieldset>
            </div>
          </DialogBody>

          <DialogFooter>
            <Button type="button" variant="secondary" onClick={() => setDialogOpen(false)} disabled={saving}>
              Cancel
            </Button>
            <Button type="button" onClick={handleSave} loading={saving}>
              {editingPhase ? 'Save changes' : 'Create phase'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={!!deletePhase}
        onOpenChange={(open) => !open && setDeletePhase(null)}
        title="Delete workout phase?"
        description={
          deletePhase
            ? `Delete "${deletePhase.name.en}"? Existing workout phase instances keep their data only until templates are updated.`
            : 'This action cannot be undone.'
        }
        confirmLabel="Delete phase"
        destructive
        loading={saving}
        onConfirm={handleDelete}
      />
    </div>
  );
}
