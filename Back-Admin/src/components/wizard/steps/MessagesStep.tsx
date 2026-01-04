'use client';

import { useState } from 'react';
import { LocalizedInput } from '@/components/forms/LocalizedInput';
import { LocalizedText } from '@/lib/types/localized';

interface DifficultyLevel {
  id: string;
  poseVariantId: string;
  name: LocalizedText;
  difficultyTypeId: string;
}

interface PoseVariant {
  id: string;
  name: LocalizedText;
}

interface AttributeValue {
  id: string;
  code: string;
  name: LocalizedText;
}

interface FeedbackMessage {
  id: string;
  type: string;
  message: LocalizedText;
}

interface MessagesData {
  [poseVariantId: string]: FeedbackMessage[];
}

interface MessagesStepProps {
  data: MessagesData;
  onChange: (data: MessagesData) => void;
  poseVariants: PoseVariant[];
  difficultyLevels: DifficultyLevel[];
  difficultyTypes: AttributeValue[];
}

export function MessagesStep({
  data,
  onChange,
  poseVariants,
}: MessagesStepProps) {
  // Use pose variant ID as key (messages are shared across all difficulty levels)
  const [selectedVariantId, setSelectedVariantId] = useState<string>(
    poseVariants[0]?.id || ''
  );
  const [selectedType, setSelectedType] = useState<string>('motivational');

  const messages = data[selectedVariantId] || [];
  const filteredMessages = messages.filter((m) => m.type === selectedType);

  const addMessage = () => {
    const newMessage: FeedbackMessage = {
      id: `temp-${Date.now()}`,
      type: selectedType,
      message: { ar: '', en: '' },
    };
    onChange({
      ...data,
      [selectedVariantId]: [...messages, newMessage],
    });
  };

  const updateMessage = (id: string, message: LocalizedText) => {
    onChange({
      ...data,
      [selectedVariantId]: messages.map((m) => (m.id === id ? { ...m, message } : m)),
    });
  };

  const removeMessage = (id: string) => {
    onChange({
      ...data,
      [selectedVariantId]: messages.filter((m) => m.id !== id),
    });
  };

  const messageTypes = [
    { code: 'motivational', name: 'Motivational', description: 'Shown when performing correctly', color: 'green' },
    { code: 'common_mistake', name: 'Common Mistakes', description: 'Tips about common errors', color: 'red' },
    { code: 'tip', name: 'Tips', description: 'General advice and tips', color: 'blue' },
  ];

  if (poseVariants.length === 0) {
    return (
      <div className="text-center py-12 text-gray-500">
        <p>Please add pose variants first (Step 3).</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="border-b border-gray-200 pb-4">
        <h2 className="text-lg font-semibold text-gray-900">Feedback Messages</h2>
        <p className="text-sm text-gray-500 mt-1">
          Add motivational messages and tips that will be shown during the exercise. 
          These messages are shared across all difficulty levels for each pose variant.
        </p>
      </div>

      {/* Pose Variant Tabs */}
      <div className="border-b border-gray-200 overflow-x-auto">
        <nav className="flex gap-2">
          {poseVariants.map((pv) => {
            const variantMessages = data[pv.id] || [];
            return (
              <button
                key={pv.id}
                type="button"
                onClick={() => setSelectedVariantId(pv.id)}
                className={`py-2 px-4 border-b-2 text-sm font-medium whitespace-nowrap ${
                  selectedVariantId === pv.id
                    ? 'border-blue-500 text-blue-600'
                    : 'border-transparent text-gray-500 hover:text-gray-700'
                }`}
              >
                {pv.name.en || 'Untitled'}
                <span className="ml-1 text-xs text-gray-400">
                  ({variantMessages.length} messages)
                </span>
              </button>
            );
          })}
        </nav>
      </div>

      {/* Message Type Tabs */}
      <div className="flex gap-2">
        {messageTypes.map((type) => {
          const typeMessages = messages.filter((m) => m.type === type.code);
          return (
            <button
              key={type.code}
              type="button"
              onClick={() => setSelectedType(type.code)}
              className={`py-2 px-4 rounded-lg text-sm font-medium transition-colors ${
                selectedType === type.code
                  ? type.color === 'green'
                    ? 'bg-green-600 text-white'
                    : type.color === 'red'
                    ? 'bg-red-600 text-white'
                    : 'bg-blue-600 text-white'
                  : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
              }`}
            >
              {type.name}
              <span className="ml-1 text-xs opacity-75">({typeMessages.length})</span>
            </button>
          );
        })}
      </div>

      {/* Type Description */}
      <div className={`p-4 rounded-lg ${
        selectedType === 'motivational' ? 'bg-green-50 border-green-200' :
        selectedType === 'common_mistake' ? 'bg-red-50 border-red-200' :
        'bg-blue-50 border-blue-200'
      } border`}>
        <p className="text-sm">
          {messageTypes.find((t) => t.code === selectedType)?.description}
        </p>
      </div>

      {/* Messages */}
      <div className="space-y-4">
        {filteredMessages.map((msg, index) => (
          <div
            key={msg.id}
            className="border border-gray-200 rounded-lg p-4"
          >
            <div className="flex justify-between items-start mb-3">
              <span className="text-sm font-medium text-gray-700">
                Message #{index + 1}
              </span>
              <button
                type="button"
                onClick={() => removeMessage(msg.id)}
                className="text-sm text-red-600 hover:text-red-800"
              >
                Remove
              </button>
            </div>

            <LocalizedInput
              label=""
              value={msg.message}
              onChange={(message) => updateMessage(msg.id, message)}
              placeholder={{
                en: selectedType === 'motivational' 
                  ? 'e.g., Great form! Keep it up!'
                  : selectedType === 'common_mistake'
                  ? 'e.g., Avoid locking your knees'
                  : 'e.g., Focus on controlled movements',
                ar: selectedType === 'motivational'
                  ? 'مثال: أداء ممتاز! استمر!'
                  : selectedType === 'common_mistake'
                  ? 'مثال: تجنب قفل الركبتين'
                  : 'مثال: ركز على الحركات المتحكم بها',
              }}
            />
          </div>
        ))}

        <button
          type="button"
          onClick={addMessage}
          className="w-full py-3 border-2 border-dashed border-gray-300 rounded-lg text-gray-500 hover:border-blue-500 hover:text-blue-500 transition-colors flex items-center justify-center gap-2"
        >
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
          </svg>
          Add {messageTypes.find((t) => t.code === selectedType)?.name} Message
        </button>
      </div>

      {/* Suggestions */}
      {selectedType === 'motivational' && filteredMessages.length === 0 && (
        <div className="bg-green-50 border border-green-200 rounded-lg p-4">
          <p className="font-medium text-green-800 mb-2">Suggested Messages</p>
          <ul className="text-sm text-green-700 space-y-1">
            <li>• &quot;Great form!&quot; / &quot;أداء ممتاز!&quot;</li>
            <li>• &quot;Keep it up!&quot; / &quot;استمر!&quot;</li>
            <li>• &quot;Perfect rep!&quot; / &quot;تكرار مثالي!&quot;</li>
            <li>• &quot;You&apos;re doing great!&quot; / &quot;أنت تبلي حسناً!&quot;</li>
          </ul>
        </div>
      )}
    </div>
  );
}

export default MessagesStep;

