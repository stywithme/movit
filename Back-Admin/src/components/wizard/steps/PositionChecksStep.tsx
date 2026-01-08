'use client';

/**
 * Step 5: Position Checks (Optional)
 * ==================================
 */

import { useState } from 'react';
import { useWizardStore } from '../WizardContext';
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

export function PositionChecksStep() {
  const { positionChecks, addPositionCheck, updatePositionCheck, removePositionCheck } = useWizardStore();
  const [showAddModal, setShowAddModal] = useState(false);
  
  const checks = positionChecks.positionChecks || [];
  
  const addFromTemplate = (template: typeof TEMPLATES[number]) => {
    const check: PositionCheckData = {
      ...template.data,
      checkId: `${template.id}_${Date.now()}`,
    };
    addPositionCheck(check);
    setShowAddModal(false);
  };
  
  return (
    <div className="space-y-8 max-w-4xl mx-auto">
      <div>
        <h2 className="text-2xl font-bold text-gray-900 mb-2">Position Checks</h2>
        <p className="text-gray-500">Add optional position-based validation for better form feedback.</p>
      </div>
      
      {/* Info Banner */}
      <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
        <p className="text-sm text-blue-700">
          💡 Position checks validate body positioning beyond angles. They provide real-time form feedback during exercise. 
          <strong> This step is optional</strong> - you can skip it if angle-based tracking is sufficient.
        </p>
      </div>
      
      {/* Quick Templates */}
      <div className="space-y-3">
        <h3 className="font-medium text-gray-700">Quick Templates</h3>
        <div className="flex flex-wrap gap-2">
          {TEMPLATES.map((template) => (
            <button
              key={template.id}
              type="button"
              onClick={() => addFromTemplate(template)}
              className="px-3 py-1.5 bg-gray-100 hover:bg-gray-200 rounded-full text-sm transition-colors text-gray-900"
            >
              {template.name}
            </button>
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
          <div className="border-2 border-dashed border-gray-200 rounded-xl p-8 text-center">
            <p className="text-gray-500">No position checks added</p>
            <p className="text-sm text-gray-400 mt-1">Use the templates above or add a custom check</p>
          </div>
        )}
      </div>
      
      {/* Add Custom Button */}
      <button
        type="button"
        onClick={() => setShowAddModal(true)}
        className="px-4 py-2 border-2 border-dashed border-gray-300 text-gray-700 rounded-lg hover:border-gray-400 hover:text-gray-900 transition-colors w-full"
      >
        + Add Custom Position Check
      </button>
      
      {/* Add Custom Modal */}
      {showAddModal && (
        <AddPositionCheckModal
          onAdd={(check) => {
            addPositionCheck(check);
            setShowAddModal(false);
          }}
          onClose={() => setShowAddModal(false)}
        />
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
    error: 'bg-red-100 text-red-700',
    warning: 'bg-yellow-100 text-yellow-700',
    tip: 'bg-blue-100 text-blue-700',
  };
  
  return (
    <div className="border rounded-xl bg-white shadow-sm overflow-hidden">
      {/* Header */}
      <div className="flex items-center justify-between p-4 cursor-pointer" onClick={() => setExpanded(!expanded)}>
        <div className="flex items-center gap-3">
          <span className={`px-2 py-1 text-xs font-medium rounded ${severityColors[check.severity]}`}>
            {check.severity.toUpperCase()}
          </span>
          <div>
            <h4 className="font-medium text-gray-900">{check.checkId.replace(/_/g, ' ').replace(/\b\w/g, l => l.toUpperCase())}</h4>
            <p className="text-sm text-gray-500">{check.type.replace(/_/g, ' ')}</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={(e) => { e.stopPropagation(); onRemove(index); }}
            className="text-red-500 hover:text-red-700 p-1"
          >
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
            </svg>
          </button>
          <svg className={`w-5 h-5 text-gray-400 transition-transform ${expanded ? 'rotate-180' : ''}`} fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
          </svg>
        </div>
      </div>
      
      {/* Expanded Content */}
      {expanded && (
        <div className="p-4 border-t bg-gray-50 space-y-4">
          {/* Landmarks */}
          <div>
            <label className="text-sm font-medium text-gray-700">Landmarks</label>
            <p className="text-sm text-gray-600">
              Primary: <code>{check.landmarks.primary}</code> | Secondary: <code>{check.landmarks.secondary}</code>
            </p>
          </div>
          
          {/* Thresholds */}
          <div>
            <label className="text-sm font-medium text-gray-700">Thresholds</label>
            <div className="flex gap-4 mt-1">
              {(['beginner', 'normal', 'advanced'] as const).map((level) => (
                <div key={level} className="flex items-center gap-1">
                  <span className="text-xs text-gray-500 capitalize">{level}:</span>
                  <input
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
                    className="w-16 px-2 py-1 border rounded text-sm text-center text-gray-900"
                  />
                </div>
              ))}
            </div>
          </div>
          
          {/* Active Phases */}
          <div>
            <label className="text-sm font-medium text-gray-700">Active Phases</label>
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
                  className={`px-2 py-1 text-xs rounded ${
                    check.activePhases.includes(phase)
                      ? 'bg-blue-100 text-blue-700'
                      : 'bg-gray-100 text-gray-700'
                  }`}
                >
                  {phase}
                </button>
              ))}
            </div>
          </div>
          
          {/* Error Message */}
          <div>
            <label className="text-sm font-medium text-gray-700">Error Message</label>
            <div className="grid grid-cols-2 gap-2 mt-1">
              <input
                type="text"
                value={check.errorMessage.en}
                onChange={(e) => onUpdate(index, {
                  ...check,
                  errorMessage: { ...check.errorMessage, en: e.target.value },
                })}
                placeholder="English message"
                className="px-3 py-2 border rounded text-sm text-gray-900 placeholder:text-gray-500"
              />
              <input
                type="text"
                dir="rtl"
                value={check.errorMessage.ar}
                onChange={(e) => onUpdate(index, {
                  ...check,
                  errorMessage: { ...check.errorMessage, ar: e.target.value },
                })}
                placeholder="الرسالة بالعربية"
                className="px-3 py-2 border rounded text-sm text-gray-900 placeholder:text-gray-500"
              />
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

// Add Position Check Modal
interface AddPositionCheckModalProps {
  onAdd: (check: PositionCheckData) => void;
  onClose: () => void;
}

function AddPositionCheckModal({ onAdd, onClose }: AddPositionCheckModalProps) {
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
  
  // Get available landmarks (you might want to fetch these from props or API)
  const availableLandmarks = [
    'left_knee', 'right_knee', 'left_hip', 'right_hip',
    'left_shoulder', 'right_shoulder', 'left_elbow', 'right_elbow',
    'left_ankle', 'right_ankle', 'left_foot_index', 'right_foot_index',
    'left_wrist', 'right_wrist', 'spine',
  ];
  
  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50" onClick={onClose}>
      <div 
        className="bg-white rounded-xl p-6 max-w-2xl w-full mx-4 max-h-[90vh] overflow-y-auto" 
        onClick={e => e.stopPropagation()}
      >
        <div className="flex items-center justify-between mb-6">
          <h3 className="text-xl font-bold text-gray-900">Add Custom Position Check</h3>
          <button
            type="button"
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600"
          >
            <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
        
        <form onSubmit={handleSubmit} className="space-y-6">
          {/* Type */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Check Type *
            </label>
            <select
              value={formData.type}
              onChange={(e) => setFormData({ ...formData, type: e.target.value as PositionCheckType })}
              className="w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500 text-gray-900 bg-white"
            >
              {POSITION_CHECK_TYPES.map((type) => (
                <option key={type.value} value={type.value}>
                  {type.label}
                </option>
              ))}
            </select>
          </div>
          
          {/* Landmarks */}
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Primary Landmark *
              </label>
              <select
                value={formData.landmarks.primary}
                onChange={(e) => setFormData({
                  ...formData,
                  landmarks: { ...formData.landmarks, primary: e.target.value },
                })}
                className="w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 text-gray-900 bg-white"
              >
                <option value="">Select...</option>
                {availableLandmarks.map((landmark) => (
                  <option key={landmark} value={landmark}>
                    {landmark.replace(/_/g, ' ')}
                  </option>
                ))}
              </select>
            </div>
            
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Secondary Landmark *
              </label>
              <select
                value={formData.landmarks.secondary}
                onChange={(e) => setFormData({
                  ...formData,
                  landmarks: { ...formData.landmarks, secondary: e.target.value },
                })}
                className="w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 text-gray-900 bg-white"
              >
                <option value="">Select...</option>
                {availableLandmarks.map((landmark) => (
                  <option key={landmark} value={landmark}>
                    {landmark.replace(/_/g, ' ')}
                  </option>
                ))}
              </select>
            </div>
          </div>
          
          {/* Condition Operator */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Condition Operator *
            </label>
            <select
              value={formData.condition.operator}
              onChange={(e) => setFormData({
                ...formData,
                condition: { ...formData.condition, operator: e.target.value as any },
              })}
              className="w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 text-gray-900 bg-white"
            >
              <option value="should_not_exceed">Should NOT Exceed</option>
              <option value="should_exceed">Should Exceed</option>
              <option value="should_be_within">Should Be Within</option>
              <option value="should_equal">Should Equal</option>
            </select>
          </div>
          
          {/* Thresholds */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Thresholds *
            </label>
            <div className="grid grid-cols-3 gap-4">
              {(['beginner', 'normal', 'advanced'] as const).map((level) => (
                <div key={level}>
                  <label className="block text-xs text-gray-500 mb-1 capitalize">{level}</label>
                  <input
                    type="number"
                    step="0.01"
                    value={formData.condition.thresholds[level]}
                    onChange={(e) => setFormData({
                      ...formData,
                      condition: {
                        ...formData.condition,
                        thresholds: {
                          ...formData.condition.thresholds,
                          [level]: Number(e.target.value),
                        },
                      },
                    })}
                    className="w-full px-3 py-2 border rounded-lg text-sm text-gray-900 bg-white placeholder:text-gray-400"
                    placeholder="0.05"
                  />
                </div>
              ))}
            </div>
          </div>
          
          {/* Active Phases */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Active Phases *
            </label>
            <div className="flex flex-wrap gap-2">
              {PHASE_OPTIONS.map((phase) => (
                <button
                  key={phase}
                  type="button"
                  onClick={() => {
                    const current = formData.activePhases;
                    const updated = current.includes(phase)
                      ? current.filter(p => p !== phase)
                      : [...current, phase];
                    setFormData({ ...formData, activePhases: updated });
                  }}
                  className={`px-3 py-1.5 text-sm rounded-lg transition-colors ${
                    formData.activePhases.includes(phase)
                      ? 'bg-blue-100 text-blue-700 border-2 border-blue-300'
                      : 'bg-gray-100 text-gray-600 border-2 border-transparent hover:bg-gray-200'
                  }`}
                >
                  {phase}
                </button>
              ))}
            </div>
          </div>
          
          {/* Severity */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Severity *
            </label>
            <div className="flex gap-4">
              {(['error', 'warning', 'tip'] as const).map((severity) => (
                <button
                  key={severity}
                  type="button"
                  onClick={() => setFormData({ ...formData, severity })}
                  className={`px-4 py-2 rounded-lg border-2 transition-colors ${
                    formData.severity === severity
                      ? 'border-blue-500 bg-blue-50 text-blue-700'
                      : 'border-gray-200 text-gray-600 hover:border-gray-300'
                  }`}
                >
                  {severity.charAt(0).toUpperCase() + severity.slice(1)}
                </button>
              ))}
            </div>
          </div>
          
          {/* Error Messages */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Error Messages *
            </label>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <input
                  type="text"
                  value={formData.errorMessage.en}
                  onChange={(e) => setFormData({
                    ...formData,
                    errorMessage: { ...formData.errorMessage, en: e.target.value },
                  })}
                  placeholder="English message"
                  className="w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 text-gray-900 bg-white placeholder:text-gray-400"
                />
              </div>
              <div>
                <input
                  type="text"
                  dir="rtl"
                  value={formData.errorMessage.ar}
                  onChange={(e) => setFormData({
                    ...formData,
                    errorMessage: { ...formData.errorMessage, ar: e.target.value },
                  })}
                  placeholder="الرسالة بالعربية"
                  className="w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 text-gray-900 bg-white placeholder:text-gray-400"
                />
              </div>
            </div>
          </div>
          
          {/* Advanced Settings */}
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Cooldown (ms)
              </label>
              <input
                type="number"
                min={0}
                value={formData.cooldownMs}
                onChange={(e) => setFormData({ ...formData, cooldownMs: Number(e.target.value) })}
                className="w-full px-4 py-2 border rounded-lg text-gray-900 bg-white placeholder:text-gray-400"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Min Error Frames
              </label>
              <input
                type="number"
                min={1}
                value={formData.minErrorFrames}
                onChange={(e) => setFormData({ ...formData, minErrorFrames: Number(e.target.value) })}
                className="w-full px-4 py-2 border rounded-lg text-gray-900 bg-white placeholder:text-gray-400"
              />
            </div>
          </div>
          
          {/* Actions */}
          <div className="flex gap-4 pt-4 border-t">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 px-4 py-2 border-2 border-gray-300 rounded-lg text-gray-700 hover:bg-gray-50 transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              className="flex-1 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
            >
              Add Check
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default PositionChecksStep;
