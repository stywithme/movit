'use client';

import { AttributeSelect } from '@/components/forms/AttributeSelect';
import { LocalizedText } from '@/lib/types/localized';

interface AttributeValue {
  id: string;
  code: string;
  name: LocalizedText;
  description?: LocalizedText | null;
  icon?: string | null;
  color?: string | null;
}

interface AttributesData {
  muscles: string[];
  equipment: string[];
  tags: string[];
}

interface AttributesStepProps {
  data: AttributesData;
  onChange: (data: AttributesData) => void;
  muscles: AttributeValue[];
  equipment: AttributeValue[];
  tags: AttributeValue[];
}

export function AttributesStep({
  data,
  onChange,
  muscles,
  equipment,
  tags,
}: AttributesStepProps) {
  return (
    <div className="space-y-6">
      <div className="border-b border-gray-200 pb-4">
        <h2 className="text-lg font-semibold text-gray-900">Additional Attributes</h2>
        <p className="text-sm text-gray-500 mt-1">
          Select the target muscles, required equipment, and tags for this exercise.
        </p>
      </div>

      <AttributeSelect
        label="Target Muscles"
        value={data.muscles}
        onChange={(value) => onChange({ ...data, muscles: value as string[] })}
        options={muscles}
        placeholder="Select muscles..."
        multiple
      />

      <AttributeSelect
        label="Required Equipment"
        value={data.equipment}
        onChange={(value) => onChange({ ...data, equipment: value as string[] })}
        options={equipment}
        placeholder="Select equipment..."
        multiple
      />

      <AttributeSelect
        label="Tags"
        value={data.tags}
        onChange={(value) => onChange({ ...data, tags: value as string[] })}
        options={tags}
        placeholder="Select or add tags..."
        multiple
      />

      {/* Summary */}
      <div className="bg-gray-50 rounded-lg p-4">
        <h3 className="text-sm font-medium text-gray-700 mb-2">Selected Attributes</h3>
        <div className="space-y-2 text-sm">
          <div>
            <span className="text-gray-500">Muscles:</span>{' '}
            <span className="text-gray-900">
              {data.muscles.length > 0
                ? data.muscles
                    .map((id) => muscles.find((m) => m.id === id)?.name.en)
                    .filter(Boolean)
                    .join(', ')
                : 'None selected'}
            </span>
          </div>
          <div>
            <span className="text-gray-500">Equipment:</span>{' '}
            <span className="text-gray-900">
              {data.equipment.length > 0
                ? data.equipment
                    .map((id) => equipment.find((e) => e.id === id)?.name.en)
                    .filter(Boolean)
                    .join(', ')
                : 'None selected'}
            </span>
          </div>
          <div>
            <span className="text-gray-500">Tags:</span>{' '}
            <span className="text-gray-900">
              {data.tags.length > 0
                ? data.tags
                    .map((id) => tags.find((t) => t.id === id)?.name.en)
                    .filter(Boolean)
                    .join(', ')
                : 'None selected'}
            </span>
          </div>
        </div>
      </div>
    </div>
  );
}

export default AttributesStep;

