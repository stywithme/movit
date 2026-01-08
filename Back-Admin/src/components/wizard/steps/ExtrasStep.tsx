'use client';

/**
 * Step 7: Extras (Attributes + Feedback Messages)
 * ================================================
 */

import { useWizardStore } from '../WizardContext';
import type { FeedbackMessageData } from '@/modules/exercises/exercises.validation';

interface ExtrasStepProps {
  muscles: Array<{ id: string; code: string; name: { ar: string; en: string } }>;
  equipment: Array<{ id: string; code: string; name: { ar: string; en: string } }>;
  tags: Array<{ id: string; code: string; name: { ar: string; en: string } }>;
}

export function ExtrasStep({ muscles, equipment, tags }: ExtrasStepProps) {
  const { extras, setExtras, addFeedbackMessage, updateFeedbackMessage, removeFeedbackMessage } = useWizardStore();
  
  const toggleAttribute = (type: 'muscles' | 'equipment' | 'tags', id: string) => {
    const current = extras[type] || [];
    const updated = current.includes(id)
      ? current.filter(i => i !== id)
      : [...current, id];
    setExtras({ [type]: updated });
  };
  
  const feedbackMessages = extras.feedbackMessages || [];
  
  const addNewMessage = (type: FeedbackMessageData['type']) => {
    addFeedbackMessage({
      type,
      message: { ar: '', en: '' },
    });
  };
  
  return (
    <div className="space-y-8 max-w-4xl mx-auto">
      <div>
        <h2 className="text-2xl font-bold text-gray-900 mb-2">Attributes & Feedback</h2>
        <p className="text-gray-500">Add muscles, equipment, and feedback messages.</p>
      </div>
      
      {/* Target Muscles */}
      <div className="space-y-3">
        <h3 className="font-semibold text-gray-900">Target Muscles</h3>
        <div className="flex flex-wrap gap-2">
          {muscles.map((muscle) => {
            const isSelected = (extras.muscles || []).includes(muscle.id);
            return (
              <button
                key={muscle.id}
                type="button"
                onClick={() => toggleAttribute('muscles', muscle.id)}
                className={`
                  px-3 py-1.5 rounded-full text-sm transition-colors
                  ${isSelected 
                    ? 'bg-purple-100 text-purple-700 ring-2 ring-purple-300' 
                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                  }
                `}
              >
                {isSelected && '✓ '}{muscle.name.en}
              </button>
            );
          })}
        </div>
      </div>
      
      {/* Equipment */}
      <div className="space-y-3">
        <h3 className="font-semibold text-gray-900">Equipment</h3>
        <div className="flex flex-wrap gap-2">
          {equipment.map((eq) => {
            const isSelected = (extras.equipment || []).includes(eq.id);
            return (
              <button
                key={eq.id}
                type="button"
                onClick={() => toggleAttribute('equipment', eq.id)}
                className={`
                  px-3 py-1.5 rounded-full text-sm transition-colors
                  ${isSelected 
                    ? 'bg-orange-100 text-orange-700 ring-2 ring-orange-300' 
                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                  }
                `}
              >
                {isSelected && '✓ '}{eq.name.en}
              </button>
            );
          })}
        </div>
      </div>
      
      {/* Tags */}
      {tags.length > 0 && (
        <div className="space-y-3">
          <h3 className="font-semibold text-gray-900">Tags</h3>
          <div className="flex flex-wrap gap-2">
            {tags.map((tag) => {
              const isSelected = (extras.tags || []).includes(tag.id);
              return (
                <button
                  key={tag.id}
                  type="button"
                  onClick={() => toggleAttribute('tags', tag.id)}
                className={`
                  px-3 py-1.5 rounded-full text-sm transition-colors
                  ${isSelected 
                    ? 'bg-teal-100 text-teal-700 ring-2 ring-teal-300' 
                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                  }
                `}
                >
                  {isSelected && '✓ '}{tag.name.en}
                </button>
              );
            })}
          </div>
        </div>
      )}
      
      {/* Divider */}
      <hr className="border-gray-200" />
      
      {/* Feedback Messages */}
      <div className="space-y-4">
        <h3 className="font-semibold text-gray-900">Feedback Messages</h3>
        
        {/* Message Types */}
        {(['motivational', 'common_mistake', 'tip'] as const).map((type) => {
          const typeMessages = feedbackMessages.filter(m => m.type === type);
          const typeLabels = {
            motivational: { label: '💪 Motivational', color: 'green' },
            common_mistake: { label: '⚠️ Common Mistakes', color: 'amber' },
            tip: { label: '💡 Tips', color: 'blue' },
          };
          const { label, color } = typeLabels[type];
          
          return (
            <div key={type} className="space-y-2">
              <div className="flex items-center justify-between">
                <h4 className="font-medium text-gray-700">{label}</h4>
                <button
                  type="button"
                  onClick={() => addNewMessage(type)}
                  className="text-sm text-blue-600 hover:text-blue-700"
                >
                  + Add
                </button>
              </div>
              
              <div className="space-y-2">
                {typeMessages.map((msg, idx) => {
                  const globalIndex = feedbackMessages.indexOf(msg);
                  return (
                    <div key={idx} className={`flex gap-2 p-3 bg-${color}-50 rounded-lg`}>
                      <div className="flex-1 grid grid-cols-2 gap-2">
                        <input
                          type="text"
                          value={msg.message.en}
                          onChange={(e) => updateFeedbackMessage(globalIndex, {
                            ...msg,
                            message: { ...msg.message, en: e.target.value },
                          })}
                          placeholder="English message"
                          className="px-3 py-2 border rounded text-sm text-gray-900 placeholder:text-gray-500"
                        />
                        <input
                          type="text"
                          dir="rtl"
                          value={msg.message.ar}
                          onChange={(e) => updateFeedbackMessage(globalIndex, {
                            ...msg,
                            message: { ...msg.message, ar: e.target.value },
                          })}
                          placeholder="الرسالة بالعربية"
                          className="px-3 py-2 border rounded text-sm text-gray-900 placeholder:text-gray-500"
                        />
                      </div>
                      <button
                        type="button"
                        onClick={() => removeFeedbackMessage(globalIndex)}
                        className="text-red-500 hover:text-red-700 p-1"
                      >
                        <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                        </svg>
                      </button>
                    </div>
                  );
                })}
                
                {typeMessages.length === 0 && (
                  <p className="text-sm text-gray-400 italic">No {type.replace('_', ' ')} messages</p>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

export default ExtrasStep;
