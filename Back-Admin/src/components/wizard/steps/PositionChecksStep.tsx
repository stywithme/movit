'use client';

/**
 * Step 5: Position Checks (Optional)
 * ==================================
 * 
 * No modals - all inline configuration
 */

import { useState } from 'react';
import { useWizardStore } from '../WizardContext';
import { Card, CardHeader, CardTitle, CardContent, Button, Badge, Input, Select, Label } from '@/components/ui';
import { Plus, X, ChevronDown, ChevronUp, Copy } from 'lucide-react';
import type { PositionCheckData } from '@/modules/exercises/exercises.validation';
import type { PhaseName, PositionCheckType } from '@/lib/types/localized';

// Position check templates
const TEMPLATES: Array<{
  id: string;
  name: string;
  description: string;
  data: Omit<PositionCheckData, 'checkId'>;
}> = [
  {
    id: 'knee_over_toe',
    name: 'Knee Over Toe',
    description: 'Check that knees don\'t go past toes during squat',
    data: {
      type: 'forward_comparison',
      landmarks: { primary: 'left_knee', secondary: 'left_foot_index' },
      condition: { operator: 'should_not_exceed', thresholds: { beginner: 0.08, normal: 0.05, advanced: 0.03 } },
      activePhases: ['down', 'bottom'],
      errorMessage: { ar: 'لا تدع ركبتك تتجاوز أصابع قدميك', en: 'Don\'t let your knee go past your toes' },
      severity: 'warning',
      cooldownMs: 2000,
      minErrorFrames: 3,
    },
  },
  {
    id: 'back_straight',
    name: 'Back Straightness',
    description: 'Check that back remains straight',
    data: {
      type: 'vertical_alignment',
      landmarks: { primary: 'left_shoulder', secondary: 'left_hip' },
      condition: { operator: 'should_be_within', thresholds: { beginner: 0.15, normal: 0.10, advanced: 0.05 } },
      activePhases: ['start', 'down', 'bottom', 'up'],
      errorMessage: { ar: 'حافظ على استقامة ظهرك', en: 'Keep your back straight' },
      severity: 'warning',
      cooldownMs: 2000,
      minErrorFrames: 5,
    },
  },
  {
    id: 'hip_hinge',
    name: 'Hip Behind Knee',
    description: 'Ensure proper hip hinge pattern',
    data: {
      type: 'relative_position',
      landmarks: { primary: 'left_hip', secondary: 'left_knee' },
      condition: { operator: 'should_exceed', thresholds: { beginner: 0.02, normal: 0.03, advanced: 0.04 } },
      activePhases: ['down', 'bottom'],
      errorMessage: { ar: 'ادفع وركك للخلف', en: 'Push your hips back' },
      severity: 'tip',
      cooldownMs: 3000,
      minErrorFrames: 5,
    },
  },
];

const POSITION_CHECK_TYPES: Array<{ value: PositionCheckType; label: string }> = [
  { value: 'forward_comparison', label: 'Forward Comparison' },
  { value: 'vertical_alignment', label: 'Vertical Alignment' },
  { value: 'horizontal_alignment', label: 'Horizontal Alignment' },
  { value: 'distance_ratio', label: 'Distance Ratio' },
  { value: 'angle_constraint', label: 'Angle Constraint' },
  { value: 'relative_position', label: 'Relative Position' },
  { value: 'symmetry_check', label: 'Symmetry Check' },
];

const PHASE_OPTIONS: PhaseName[] = ['start', 'down', 'bottom', 'up', 'push', 'extended', 'pull', 'hold', 'count'];

const AVAILABLE_LANDMARKS = [
  'left_knee', 'right_knee', 'left_hip', 'right_hip',
  'left_shoulder', 'right_shoulder', 'left_ankle', 'right_ankle',
  'left_elbow', 'right_elbow', 'left_wrist', 'right_wrist',
  'left_foot_index', 'right_foot_index', 'nose', 'left_ear', 'right_ear',
];

export function PositionChecksStep() {
  const { positionChecks, addPositionCheck, updatePositionCheck, removePositionCheck } = useWizardStore();
  const [showAddForm, setShowAddForm] = useState(false);
  
  const checks = positionChecks.positionChecks || [];
  
  const addFromTemplate = (template: typeof TEMPLATES[number]) => {
    const check: PositionCheckData = {
      ...template.data,
      checkId: `${template.id}_${Date.now()}`,
    };
    addPositionCheck(check);
  };
  
  return (
    <div className="space-y-8 max-w-4xl mx-auto">
      <div>
        <div className="flex items-center gap-2 mb-2">
          <h2 className="text-2xl font-bold text-gray-900">Position Checks</h2>
          <Badge variant="outline">Optional</Badge>
          <Label tooltip="Add optional position-based validation for better form feedback. These checks run in real-time on the user's device." />
        </div>
        <p className="text-gray-500">Add optional position-based validation for better form feedback.</p>
      </div>
      
      {/* Info Banner */}
      <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
        <p className="text-sm text-blue-700">
          💡 Position checks validate body positioning beyond angles. They provide real-time form feedback. 
          <strong> This step is optional</strong> - skip if angle-based tracking is sufficient.
        </p>
      </div>
      
      {/* Quick Templates */}
      <div className="space-y-3">
        <Label tooltip="Pre-configured checks for common exercises. Click to add.">Quick Templates</Label>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
          {TEMPLATES.map((template) => (
            <Card
              key={template.id}
              interactive
              className="p-3"
              onClick={() => addFromTemplate(template)}
            >
              <div className="flex items-center justify-between">
                <div>
                  <p className="font-medium text-gray-900">{template.name}</p>
                  <p className="text-xs text-gray-500 line-clamp-1">{template.description}</p>
                </div>
                <Copy className="h-4 w-4 text-gray-400" />
              </div>
            </Card>
          ))}
        </div>
      </div>
      
      {/* Existing Checks */}
      <div className="space-y-4">
        {checks.map((check, index) => (
          <PositionCheckCard
            key={check.checkId}
            check={check}
            index={index}
            onUpdate={updatePositionCheck}
            onRemove={removePositionCheck}
          />
        ))}
        
        {checks.length === 0 && (
          <Card className="p-8 text-center border-dashed">
            <p className="text-gray-500">No position checks added</p>
            <p className="text-sm text-gray-400 mt-1">Use templates above or add a custom check</p>
          </Card>
        )}
      </div>
      
      {/* Add Custom Form (Inline - No Modal) */}
      {showAddForm ? (
        <AddPositionCheckForm 
          onAdd={(check) => {
            addPositionCheck(check);
            setShowAddForm(false);
          }}
          onCancel={() => setShowAddForm(false)}
        />
      ) : (
        <Button 
          variant="outline" 
          className="w-full border-dashed"
          onClick={() => setShowAddForm(true)}
          icon={<Plus className="h-4 w-4" />}
        >
          Add Custom Position Check
        </Button>
      )}
    </div>
  );
}

// Position Check Card
interface PositionCheckCardProps {
  check: PositionCheckData;
  index: number;
  onUpdate: (index: number, check: PositionCheckData) => void;
  onRemove: (index: number) => void;
}

function PositionCheckCard({ check, index, onUpdate, onRemove }: PositionCheckCardProps) {
  const [expanded, setExpanded] = useState(false);
  
  const severityColors = {
    error: 'error' as const,
    warning: 'warning' as const,
    tip: 'primary' as const,
  };
  
  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Badge variant={severityColors[check.severity]}>
              {check.severity.toUpperCase()}
            </Badge>
            <div>
              <h4 className="font-medium text-gray-900">
                {check.checkId.replace(/_\d+$/, '').replace(/_/g, ' ').replace(/\b\w/g, l => l.toUpperCase())}
              </h4>
              <p className="text-sm text-gray-500">{check.type.replace(/_/g, ' ')}</p>
            </div>
          </div>
          <div className="flex items-center gap-1">
            <Button variant="ghost" size="icon" onClick={() => setExpanded(!expanded)}>
              {expanded ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
            </Button>
            <Button variant="ghost" size="icon" onClick={() => onRemove(index)}>
              <X className="h-4 w-4 text-red-500" />
            </Button>
          </div>
        </div>
      </CardHeader>
      
      {expanded && (
        <CardContent className="space-y-4">
          {/* Landmarks */}
          <div>
            <Label>Landmarks</Label>
            <div className="text-sm bg-gray-50 p-2 rounded border border-gray-200 mt-1">
              <span className="text-gray-500">Primary:</span> <code className="text-blue-600">{check.landmarks.primary}</code>
              <span className="mx-2 text-gray-300">|</span>
              <span className="text-gray-500">Secondary:</span> <code className="text-blue-600">{check.landmarks.secondary}</code>
            </div>
          </div>
          
          {/* Thresholds */}
          <div>
            <Label tooltip="Threshold values for each difficulty level">Thresholds</Label>
            <div className="flex gap-4 mt-1">
              {(['beginner', 'normal', 'advanced'] as const).map((level) => (
                <div key={level} className="flex items-center gap-2 bg-white p-1 rounded border border-gray-200">
                  <Badge variant="outline" className="capitalize">{level}</Badge>
                  <Input
                    type="number"
                    step="0.01"
                    value={check.condition.thresholds[level]}
                    onChange={(e) => onUpdate(index, {
                      ...check,
                      condition: {
                        ...check.condition,
                        thresholds: { ...check.condition.thresholds, [level]: Number(e.target.value) },
                      },
                    })}
                    className="w-20"
                  />
                </div>
              ))}
            </div>
          </div>
          
          {/* Active Phases */}
          <div>
            <Label tooltip="Which exercise phases should trigger this check">Active Phases</Label>
            <div className="flex flex-wrap gap-2 mt-1">
              {PHASE_OPTIONS.map((phase) => (
                <button
                  key={phase}
                  type="button"
                  onClick={() => {
                    const current = check.activePhases;
                    const updated = current.includes(phase)
                      ? current.filter(p => p !== phase)
                      : [...current, phase];
                    onUpdate(index, { ...check, activePhases: updated });
                  }}
                  className={`px-2 py-1 text-xs rounded transition-colors ${
                    check.activePhases.includes(phase)
                      ? 'bg-blue-100 text-blue-700 font-medium'
                      : 'bg-gray-100 text-gray-500 hover:bg-gray-200'
                  }`}
                >
                  {phase}
                </button>
              ))}
            </div>
          </div>
          
          {/* Error Message */}
          <div>
            <Label>Error Message</Label>
            <div className="grid grid-cols-2 gap-2 mt-1">
              <Input
                value={check.errorMessage.en}
                onChange={(e) => onUpdate(index, {
                  ...check,
                  errorMessage: { ...check.errorMessage, en: e.target.value },
                })}
                placeholder="English message"
              />
              <Input
                dir="rtl"
                value={check.errorMessage.ar}
                onChange={(e) => onUpdate(index, {
                  ...check,
                  errorMessage: { ...check.errorMessage, ar: e.target.value },
                })}
                placeholder="الرسالة بالعربية"
              />
            </div>
          </div>
        </CardContent>
      )}
    </Card>
  );
}

// Add Position Check Form (Inline)
interface AddPositionCheckFormProps {
  onAdd: (check: PositionCheckData) => void;
  onCancel: () => void;
}

function AddPositionCheckForm({ onAdd, onCancel }: AddPositionCheckFormProps) {
  const [formData, setFormData] = useState<Omit<PositionCheckData, 'checkId'>>({
    type: 'forward_comparison',
    landmarks: { primary: '', secondary: '' },
    condition: {
      operator: 'should_not_exceed',
      thresholds: { beginner: 0.08, normal: 0.05, advanced: 0.03 },
    },
    activePhases: ['down', 'bottom'],
    errorMessage: { ar: '', en: '' },
    severity: 'warning',
    cooldownMs: 2000,
    minErrorFrames: 3,
  });
  
  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!formData.landmarks.primary || !formData.landmarks.secondary) {
      alert('Please select primary and secondary landmarks');
      return;
    }
    if (!formData.errorMessage.en || !formData.errorMessage.ar) {
      alert('Please enter error messages in both languages');
      return;
    }
    
    const check: PositionCheckData = {
      ...formData,
      checkId: `custom_${Date.now()}`,
    };
    onAdd(check);
  };
  
  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <CardTitle>Add Custom Check</CardTitle>
          <Button variant="ghost" size="icon" onClick={onCancel}>
            <X className="h-4 w-4" />
          </Button>
        </div>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <Label required>Check Type</Label>
              <Select
                value={formData.type}
                onChange={(e) => setFormData({ ...formData, type: e.target.value as PositionCheckType })}
                options={POSITION_CHECK_TYPES}
              />
            </div>
            <div>
              <Label required>Condition Operator</Label>
              <Select
                value={formData.condition.operator}
                onChange={(e) => setFormData({
                  ...formData,
                  condition: { ...formData.condition, operator: e.target.value as string },
                })}
                options={[
                  { value: 'should_not_exceed', label: 'Should NOT Exceed' },
                  { value: 'should_exceed', label: 'Should Exceed' },
                  { value: 'should_be_within', label: 'Should Be Within' },
                  { value: 'should_equal', label: 'Should Equal' },
                ]}
              />
            </div>
          </div>
          
          <div className="grid grid-cols-2 gap-4">
            <div>
              <Label required>Primary Landmark</Label>
              <Select
                value={formData.landmarks.primary}
                onChange={(e) => setFormData({
                  ...formData,
                  landmarks: { ...formData.landmarks, primary: e.target.value },
                })}
                placeholder="Select..."
                options={AVAILABLE_LANDMARKS.map(l => ({ value: l, label: l.replace(/_/g, ' ') }))}
              />
            </div>
            <div>
              <Label required>Secondary Landmark</Label>
              <Select
                value={formData.landmarks.secondary}
                onChange={(e) => setFormData({
                  ...formData,
                  landmarks: { ...formData.landmarks, secondary: e.target.value },
                })}
                placeholder="Select..."
                options={AVAILABLE_LANDMARKS.map(l => ({ value: l, label: l.replace(/_/g, ' ') }))}
              />
            </div>
          </div>
          
          <div className="grid grid-cols-3 gap-4">
            {(['beginner', 'normal', 'advanced'] as const).map((level) => (
              <div key={level}>
                <Label>{level.charAt(0).toUpperCase() + level.slice(1)} Threshold</Label>
                <Input
                  type="number"
                  step="0.01"
                  value={formData.condition.thresholds[level]}
                  onChange={(e) => setFormData({
                    ...formData,
                    condition: {
                      ...formData.condition,
                      thresholds: { ...formData.condition.thresholds, [level]: Number(e.target.value) },
                    },
                  })}
                />
              </div>
            ))}
          </div>
          
          <div className="grid grid-cols-2 gap-4">
            <div>
              <Label required>Error Message (English)</Label>
              <Input
                value={formData.errorMessage.en}
                onChange={(e) => setFormData({
                  ...formData,
                  errorMessage: { ...formData.errorMessage, en: e.target.value },
                })}
                placeholder="Keep your knee behind your toes"
              />
            </div>
            <div>
              <Label required>Error Message (Arabic)</Label>
              <Input
                dir="rtl"
                value={formData.errorMessage.ar}
                onChange={(e) => setFormData({
                  ...formData,
                  errorMessage: { ...formData.errorMessage, ar: e.target.value },
                })}
                placeholder="حافظ على ركبتك خلف أصابع قدميك"
              />
            </div>
          </div>
          
          <div className="flex gap-4 pt-4 border-t">
            <Button variant="outline" onClick={onCancel} className="flex-1">Cancel</Button>
            <Button type="submit" className="flex-1">Add Check</Button>
          </div>
        </form>
      </CardContent>
    </Card>
  );
}

export default PositionChecksStep;
