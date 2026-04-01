'use client';

import { useEffect, useMemo, useState, useCallback } from 'react';
import {
  Input,
  Select,
  Label,
  Button,
  Card,
  Badge,
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogBody,
  DialogFooter,
} from '@/components/ui';

interface Condition {
  metric: string;
  operator: string;
  value: number;
  window: string;
}

interface RuleAction {
  type: string;
  amount?: number;
  messageEn: string;
  messageAr: string;
}

interface ProgressionRule {
  id: string;
  name: string;
  scope: 'global' | 'program' | 'exercise';
  programId: string | null;
  exerciseSlug: string | null;
  trigger: string;
  conditions: Condition[];
  action: RuleAction;
  priority: number;
  isActive: boolean;
  createdAt: string;
  _count: { history: number };
}

interface ProgramSummary {
  id: string;
  name: { en: string; ar: string };
}

const METRIC_OPTIONS = [
  { value: 'avgFormScore', label: 'Avg Form Score' },
  { value: 'completionRate', label: 'Completion Rate' },
  { value: 'avgROM', label: 'Avg ROM' },
  { value: 'totalVolume', label: 'Total Volume' },
  { value: 'symmetryScore', label: 'Symmetry Score' },
];

const OPERATOR_OPTIONS = [
  { value: '>=', label: '>=' },
  { value: '<=', label: '<=' },
  { value: '>', label: '>' },
  { value: '<', label: '<' },
  { value: '==', label: '==' },
];

const WINDOW_OPTIONS = [
  { value: 'last_session', label: 'Last Session' },
  { value: 'last_2_sessions', label: 'Last 2 Sessions' },
  { value: 'last_week', label: 'Last Week' },
  { value: 'entire_program', label: 'Entire Program' },
];

const TRIGGER_OPTIONS = [
  { value: 'session_complete', label: 'Session Complete' },
  { value: 'week_complete', label: 'Week Complete' },
  { value: 'set_complete', label: 'Set Complete' },
];

const SCOPE_OPTIONS = [
  { value: 'global', label: 'Global' },
  { value: 'program', label: 'Program' },
  { value: 'exercise', label: 'Exercise' },
];

const ACTION_TYPE_OPTIONS = [
  { value: 'increase_weight', label: 'Increase Weight' },
  { value: 'decrease_weight', label: 'Decrease Weight' },
  { value: 'increase_reps', label: 'Increase Reps' },
  { value: 'decrease_reps', label: 'Decrease Reps' },
  { value: 'increase_sets', label: 'Increase Sets' },
  { value: 'suggest_reassessment', label: 'Suggest Reassessment' },
];

const SCOPE_BADGE_VARIANT: Record<string, 'primary' | 'purple' | 'teal'> = {
  global: 'primary',
  program: 'purple',
  exercise: 'teal',
};

const emptyCondition = (): Condition => ({
  metric: 'avgFormScore',
  operator: '>=',
  value: 0,
  window: 'last_session',
});

const emptyAction = (): RuleAction => ({
  type: 'increase_weight',
  amount: 0,
  messageEn: '',
  messageAr: '',
});

export default function ProgressionRulesPage() {
  const [rules, setRules] = useState<ProgressionRule[]>([]);
  const [programs, setPrograms] = useState<ProgramSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingRule, setEditingRule] = useState<ProgressionRule | null>(null);

  const [formName, setFormName] = useState('');
  const [formScope, setFormScope] = useState<'global' | 'program' | 'exercise'>('global');
  const [formProgramId, setFormProgramId] = useState('');
  const [formExerciseSlug, setFormExerciseSlug] = useState('');
  const [formTrigger, setFormTrigger] = useState('session_complete');
  const [formPriority, setFormPriority] = useState(50);
  const [formIsActive, setFormIsActive] = useState(true);
  const [formConditions, setFormConditions] = useState<Condition[]>([emptyCondition()]);
  const [formAction, setFormAction] = useState<RuleAction>(emptyAction());

  const fetchRules = useCallback(async () => {
    setLoading(true);
    try {
      const res = await fetch('/api/admin/progression-rules');
      const data = await res.json();
      if (data.success) {
        setRules(data.data);
      }
    } catch (error) {
      console.error('Error fetching progression rules:', error);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchRules();
  }, [fetchRules]);

  useEffect(() => {
    const fetchPrograms = async () => {
      try {
        const res = await fetch('/api/programs?status=published&limit=200');
        const data = await res.json();
        if (data.success) {
          setPrograms(data.data);
        }
      } catch (error) {
        console.error('Error fetching programs:', error);
      }
    };
    fetchPrograms();
  }, []);

  const programOptions = useMemo(
    () => [
      { value: '', label: 'Select Program' },
      ...programs.map((p) => ({
        value: p.id,
        label: `${p.name.en} / ${p.name.ar}`,
      })),
    ],
    [programs]
  );

  const resetForm = () => {
    setFormName('');
    setFormScope('global');
    setFormProgramId('');
    setFormExerciseSlug('');
    setFormTrigger('session_complete');
    setFormPriority(50);
    setFormIsActive(true);
    setFormConditions([emptyCondition()]);
    setFormAction(emptyAction());
    setEditingRule(null);
  };

  const openCreateDialog = () => {
    resetForm();
    setDialogOpen(true);
  };

  const openEditDialog = (rule: ProgressionRule) => {
    setEditingRule(rule);
    setFormName(rule.name);
    setFormScope(rule.scope);
    setFormProgramId(rule.programId || '');
    setFormExerciseSlug(rule.exerciseSlug || '');
    setFormTrigger(rule.trigger);
    setFormPriority(rule.priority);
    setFormIsActive(rule.isActive);

    const conditions = Array.isArray(rule.conditions) ? rule.conditions : [];
    setFormConditions(conditions.length > 0 ? conditions : [emptyCondition()]);

    const action = rule.action && typeof rule.action === 'object' ? rule.action : emptyAction();
    setFormAction({
      type: action.type || 'increase_weight',
      amount: action.amount ?? 0,
      messageEn: action.messageEn || '',
      messageAr: action.messageAr || '',
    });

    setDialogOpen(true);
  };

  const updateCondition = (index: number, updates: Partial<Condition>) => {
    setFormConditions((prev) =>
      prev.map((c, i) => (i === index ? { ...c, ...updates } : c))
    );
  };

  const removeCondition = (index: number) => {
    setFormConditions((prev) => prev.filter((_, i) => i !== index));
  };

  const addCondition = () => {
    setFormConditions((prev) => [...prev, emptyCondition()]);
  };

  const buildPayload = () => ({
    name: formName,
    scope: formScope,
    programId: formScope === 'program' ? formProgramId || null : null,
    exerciseSlug: formScope === 'exercise' ? formExerciseSlug || null : null,
    trigger: formTrigger,
    priority: formPriority,
    isActive: formIsActive,
    conditions: formConditions,
    action: formAction,
  });

  const handleSave = async () => {
    if (!formName.trim()) {
      alert('Rule name is required');
      return;
    }
    setSaving(true);
    try {
      const url = editingRule
        ? `/api/admin/progression-rules/${editingRule.id}`
        : '/api/admin/progression-rules';
      const method = editingRule ? 'PUT' : 'POST';

      const res = await fetch(url, {
        method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(buildPayload()),
      });

      const data = await res.json();
      if (data.success) {
        setDialogOpen(false);
        resetForm();
        fetchRules();
      } else {
        alert(data.error || 'Failed to save rule');
      }
    } catch (error) {
      console.error('Error saving rule:', error);
      alert('Failed to save rule');
    } finally {
      setSaving(false);
    }
  };

  const handleToggle = async (rule: ProgressionRule) => {
    try {
      const res = await fetch(`/api/admin/progression-rules/${rule.id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ isActive: !rule.isActive }),
      });
      const data = await res.json();
      if (data.success) {
        fetchRules();
      }
    } catch (error) {
      console.error('Error toggling rule:', error);
    }
  };

  const handleDelete = async (rule: ProgressionRule) => {
    if (!confirm(`Are you sure you want to delete "${rule.name}"?`)) return;
    try {
      const res = await fetch(`/api/admin/progression-rules/${rule.id}`, {
        method: 'DELETE',
      });
      const data = await res.json();
      if (data.success) {
        fetchRules();
      } else {
        alert(data.error || 'Failed to delete rule');
      }
    } catch (error) {
      console.error('Error deleting rule:', error);
    }
  };

  const showAmountField = ['increase_weight', 'decrease_weight', 'increase_reps', 'decrease_reps', 'increase_sets'].includes(formAction.type);

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Progression Rules (Archive)</h1>
          <p className="text-gray-600 mt-1">Legacy rules — read-only archive. Progression logic is now managed from Exercise Progression.</p>
        </div>
        <a href="/admin/exercise-progression" className="text-blue-600 hover:underline text-sm font-medium">
          Go to Exercise Progression &rarr;
        </a>
      </div>

      {/* Archive Notice */}
      <div className="bg-amber-50 border border-amber-200 rounded-lg p-4">
        <p className="text-sm text-amber-800 font-medium">
          This page is now a read-only archive. The progression engine no longer reads from these rules.
          All progression logic is managed through Exercise Progression Profiles.
        </p>
      </div>

      {/* Rules Table */}
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
        {loading ? (
          <div className="p-8 text-center text-gray-500">Loading...</div>
        ) : rules.length === 0 ? (
          <div className="p-8 text-center text-gray-500">
            <p>No progression rules found.</p>
            <button
              onClick={openCreateDialog}
              className="text-blue-600 hover:underline mt-2 inline-block"
            >
              Create your first rule
            </button>
          </div>
        ) : (
          <table className="w-full">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                  Name
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                  Scope
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                  Trigger
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                  Priority
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                  Status
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                  Executions
                </th>
                <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase">
                  Status
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {rules.map((rule) => (
                <tr key={rule.id} className="hover:bg-gray-50">
                  <td className="px-6 py-4">
                    <p className="font-medium text-gray-900">{rule.name}</p>
                    <p className="text-xs text-gray-500 mt-0.5">
                      {rule.scope === 'program' && rule.programId
                        ? `Program: ${programs.find((p) => p.id === rule.programId)?.name.en || rule.programId}`
                        : rule.scope === 'exercise' && rule.exerciseSlug
                          ? `Exercise: ${rule.exerciseSlug}`
                          : ''}
                    </p>
                  </td>
                  <td className="px-6 py-4">
                    <Badge variant={SCOPE_BADGE_VARIANT[rule.scope] || 'default'}>
                      {rule.scope}
                    </Badge>
                  </td>
                  <td className="px-6 py-4 text-sm text-gray-600">
                    {rule.trigger.replace(/_/g, ' ')}
                  </td>
                  <td className="px-6 py-4 text-sm text-gray-600">{rule.priority}</td>
                  <td className="px-6 py-4">
                    <span
                      className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium ${
                        rule.isActive
                          ? 'bg-green-100 text-green-700'
                          : 'bg-gray-100 text-gray-500'
                      }`}
                    >
                      <span
                        className={`w-1.5 h-1.5 rounded-full ${
                          rule.isActive ? 'bg-green-500' : 'bg-gray-400'
                        }`}
                      />
                      {rule.isActive ? 'Active' : 'Inactive'}
                    </span>
                  </td>
                  <td className="px-6 py-4 text-sm text-gray-600">
                    {rule._count?.history ?? 0}
                  </td>
                  <td className="px-6 py-4 text-right">
                    <span className="text-xs text-gray-400">Read-only</span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* Create / Edit Dialog */}
      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent size="xl">
          <DialogHeader>
            <DialogTitle>{editingRule ? 'Edit Rule' : 'New Progression Rule'}</DialogTitle>
          </DialogHeader>

          <DialogBody>
            <div className="space-y-6">
              {/* Basic Info */}
              <Card className="p-4 space-y-4">
                <h3 className="text-sm font-semibold text-gray-700">Basic Information</h3>
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <Label>Name *</Label>
                    <Input
                      value={formName}
                      onChange={(e) => setFormName(e.target.value)}
                      placeholder="e.g. Auto Increase Weight"
                    />
                  </div>
                  <div>
                    <Label>Scope</Label>
                    <Select
                      value={formScope}
                      onChange={(e) => setFormScope(e.target.value as 'global' | 'program' | 'exercise')}
                      options={SCOPE_OPTIONS}
                    />
                  </div>
                </div>

                {formScope === 'program' && (
                  <div>
                    <Label>Program</Label>
                    <Select
                      value={formProgramId}
                      onChange={(e) => setFormProgramId(e.target.value)}
                      options={programOptions}
                    />
                  </div>
                )}

                {formScope === 'exercise' && (
                  <div>
                    <Label>Exercise Slug</Label>
                    <Input
                      value={formExerciseSlug}
                      onChange={(e) => setFormExerciseSlug(e.target.value)}
                      placeholder="e.g. bicep-curl"
                    />
                  </div>
                )}

                <div className="grid grid-cols-3 gap-4">
                  <div>
                    <Label>Trigger</Label>
                    <Select
                      value={formTrigger}
                      onChange={(e) => setFormTrigger(e.target.value)}
                      options={TRIGGER_OPTIONS}
                    />
                  </div>
                  <div>
                    <Label>Priority (1-100)</Label>
                    <Input
                      type="number"
                      min={1}
                      max={100}
                      value={formPriority}
                      onChange={(e) => setFormPriority(Number.parseInt(e.target.value, 10) || 1)}
                    />
                  </div>
                  <div className="flex items-end pb-1">
                    <label className="flex items-center gap-2 cursor-pointer">
                      <input
                        type="checkbox"
                        checked={formIsActive}
                        onChange={(e) => setFormIsActive(e.target.checked)}
                        className="h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                      />
                      <span className="text-sm font-medium text-gray-700">Is Active</span>
                    </label>
                  </div>
                </div>
              </Card>

              {/* Conditions Builder */}
              <Card className="p-4 space-y-4">
                <div className="flex items-center justify-between">
                  <h3 className="text-sm font-semibold text-gray-700">Conditions</h3>
                  <Button type="button" variant="outline" size="sm" onClick={addCondition}>
                    Add Condition
                  </Button>
                </div>

                {formConditions.map((condition, index) => (
                  <div key={index} className="flex items-end gap-3 p-3 bg-gray-50 rounded-lg">
                    <div className="flex-1">
                      <Label>Metric</Label>
                      <Select
                        value={condition.metric}
                        onChange={(e) => updateCondition(index, { metric: e.target.value })}
                        options={METRIC_OPTIONS}
                      />
                    </div>
                    <div className="w-24">
                      <Label>Operator</Label>
                      <Select
                        value={condition.operator}
                        onChange={(e) => updateCondition(index, { operator: e.target.value })}
                        options={OPERATOR_OPTIONS}
                      />
                    </div>
                    <div className="w-28">
                      <Label>Value</Label>
                      <Input
                        type="number"
                        value={condition.value}
                        onChange={(e) =>
                          updateCondition(index, { value: Number.parseFloat(e.target.value) || 0 })
                        }
                      />
                    </div>
                    <div className="flex-1">
                      <Label>Window</Label>
                      <Select
                        value={condition.window}
                        onChange={(e) => updateCondition(index, { window: e.target.value })}
                        options={WINDOW_OPTIONS}
                      />
                    </div>
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      onClick={() => removeCondition(index)}
                      disabled={formConditions.length === 1}
                      className="text-red-500 hover:text-red-700 hover:bg-red-50 shrink-0"
                    >
                      Remove
                    </Button>
                  </div>
                ))}
              </Card>

              {/* Action Section */}
              <Card className="p-4 space-y-4">
                <h3 className="text-sm font-semibold text-gray-700">Action</h3>

                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <Label>Action Type</Label>
                    <Select
                      value={formAction.type}
                      onChange={(e) =>
                        setFormAction((prev) => ({ ...prev, type: e.target.value }))
                      }
                      options={ACTION_TYPE_OPTIONS}
                    />
                  </div>
                  {showAmountField && (
                    <div>
                      <Label>Amount</Label>
                      <Input
                        type="number"
                        value={formAction.amount ?? 0}
                        onChange={(e) =>
                          setFormAction((prev) => ({
                            ...prev,
                            amount: Number.parseFloat(e.target.value) || 0,
                          }))
                        }
                        placeholder="e.g. 2.5"
                      />
                    </div>
                  )}
                </div>

                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <Label>Message (English)</Label>
                    <Input
                      value={formAction.messageEn}
                      onChange={(e) =>
                        setFormAction((prev) => ({ ...prev, messageEn: e.target.value }))
                      }
                      placeholder="Great progress! Weight increased."
                    />
                  </div>
                  <div>
                    <Label>Message (Arabic)</Label>
                    <Input
                      dir="rtl"
                      value={formAction.messageAr}
                      onChange={(e) =>
                        setFormAction((prev) => ({ ...prev, messageAr: e.target.value }))
                      }
                      placeholder="تقدم رائع! تم زيادة الوزن."
                    />
                  </div>
                </div>
              </Card>
            </div>
          </DialogBody>

          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogOpen(false)} disabled={saving}>
              Cancel
            </Button>
            <Button onClick={handleSave} loading={saving}>
              {editingRule ? 'Save Changes' : 'Create Rule'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
