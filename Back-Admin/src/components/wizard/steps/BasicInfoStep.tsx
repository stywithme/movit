'use client';

import { LocalizedInput } from '@/components/forms/LocalizedInput';
import { AttributeSelect } from '@/components/forms/AttributeSelect';
import { LocalizedText } from '@/lib/types/localized';

interface AttributeValue {
  id: string;
  code: string;
  name: LocalizedText;
  description?: LocalizedText | null;
}

interface BasicInfoData {
  name: LocalizedText;
  description: LocalizedText;
  instructions: LocalizedText;
  categoryId: string;
  countingMethodId: string;
}

interface BasicInfoStepProps {
  data: BasicInfoData;
  onChange: (data: BasicInfoData) => void;
  categories: AttributeValue[];
  countingMethods: AttributeValue[];
  loading?: boolean;
}

export function BasicInfoStep({
  data,
  onChange,
  categories,
  countingMethods,
  loading = false,
}: BasicInfoStepProps) {
  return (
    <div className="space-y-6">
      <div className="border-b border-gray-200 pb-4">
        <h2 className="text-lg font-semibold text-gray-900">Basic Information</h2>
        <p className="text-sm text-gray-500 mt-1">
          Enter the basic details of the exercise in both English and Arabic.
        </p>
      </div>

      <LocalizedInput
        label="Exercise Name"
        value={data.name}
        onChange={(name) => onChange({ ...data, name })}
        required
        placeholder={{
          en: 'e.g., Squat',
          ar: 'مثال: القرفصاء',
        }}
      />

      <LocalizedInput
        label="Description"
        value={data.description}
        onChange={(description) => onChange({ ...data, description })}
        multiline
        rows={3}
        placeholder={{
          en: 'Describe the exercise...',
          ar: 'وصف التمرين...',
        }}
      />

      <LocalizedInput
        label="Instructions"
        value={data.instructions}
        onChange={(instructions) => onChange({ ...data, instructions })}
        multiline
        rows={4}
        placeholder={{
          en: 'Step-by-step instructions...',
          ar: 'تعليمات خطوة بخطوة...',
        }}
      />

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <AttributeSelect
          label="Category"
          value={data.categoryId}
          onChange={(value) => onChange({ ...data, categoryId: value as string })}
          options={categories}
          placeholder="Select category..."
          required
          loading={loading}
        />

        <AttributeSelect
          label="Counting Method"
          value={data.countingMethodId}
          onChange={(value) => onChange({ ...data, countingMethodId: value as string })}
          options={countingMethods}
          placeholder="Select counting method..."
          required
          loading={loading}
        />
      </div>
    </div>
  );
}

export default BasicInfoStep;

