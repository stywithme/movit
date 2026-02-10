'use client';

import { LocalizedText } from '@/lib/types/localized';
import { Select } from '@/components/ui';

interface AttributeValue {
  id: string;
  code: string;
  name: LocalizedText;
  description?: LocalizedText | null;
  icon?: string | null;
  color?: string | null;
}

interface AttributeSelectProps {
  label: string;
  value: string | string[];
  onChange: (value: string | string[]) => void;
  options: AttributeValue[];
  placeholder?: string;
  required?: boolean;
  multiple?: boolean;
  error?: string;
  className?: string;
  lang?: 'en' | 'ar';
  loading?: boolean;
}

/**
 * AttributeSelect - Select component for choosing attribute values
 * Supports single and multi-select modes
 */
export function AttributeSelect({
  label,
  value,
  onChange,
  options,
  placeholder = 'Select...',
  required = false,
  multiple = false,
  error,
  className = '',
  lang = 'en',
  loading = false,
}: AttributeSelectProps) {
  const selectedValues = Array.isArray(value) ? value : value ? [value] : [];

  const handleSingleChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    onChange(e.target.value);
  };

  const handleMultiChange = (optionId: string) => {
    if (selectedValues.includes(optionId)) {
      onChange(selectedValues.filter((v) => v !== optionId));
    } else {
      onChange([...selectedValues, optionId]);
    }
  };

  const getName = (option: AttributeValue) => {
    const name = option.name as LocalizedText;
    if (!name) return option.code;
    // Handle both object and parsed JSON
    if (typeof name === 'object') {
      return name[lang] || name.en || option.code;
    }
    return option.code;
  };

  if (multiple) {
    return (
      <div className={`space-y-2 ${className}`}>
        <label className="block text-sm font-medium text-gray-700">
          {label}
          {required && <span className="text-red-500 ml-1">*</span>}
        </label>

        <div className="border border-gray-300 rounded-md p-2 max-h-48 overflow-y-auto space-y-1">
          {options.length === 0 ? (
            <p className="text-sm text-gray-500 p-2">No options available</p>
          ) : (
            options.map((option) => (
              <label
                key={option.id}
                className={`flex items-center gap-2 p-2 rounded cursor-pointer hover:bg-gray-50 ${
                  selectedValues.includes(option.id) ? 'bg-blue-50' : ''
                }`}
              >
                <input
                  type="checkbox"
                  checked={selectedValues.includes(option.id)}
                  onChange={() => handleMultiChange(option.id)}
                  className="w-4 h-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500 flex-shrink-0"
                />
                {option.icon && <span className="text-gray-700">{option.icon}</span>}
                <span className="text-sm text-gray-900 font-medium">{getName(option)}</span>
                {option.color && (
                  <span
                    className="w-3 h-3 rounded-full ml-auto"
                    style={{ backgroundColor: option.color }}
                  />
                )}
              </label>
            ))
          )}
        </div>

        {selectedValues.length > 0 && (
          <div className="flex flex-wrap gap-1">
            {selectedValues.map((id) => {
              const option = options.find((o) => o.id === id);
              if (!option) return null;
              return (
                <span
                  key={id}
                  className="inline-flex items-center gap-1 px-2 py-1 text-xs bg-blue-100 text-blue-800 rounded-full"
                >
                  {getName(option)}
                  <button
                    type="button"
                    onClick={() => handleMultiChange(id)}
                    className="hover:text-blue-600"
                  >
                    ×
                  </button>
                </span>
              );
            })}
          </div>
        )}

        {error && <p className="text-sm text-red-500">{error}</p>}
      </div>
    );
  }

  // Build options for Select component
  const selectOptions = [
    { value: '', label: placeholder },
    ...options.map((option) => ({
      value: option.id,
      label: `${option.icon ? option.icon + ' ' : ''}${getName(option)}`,
    })),
  ];

  return (
    <div className={`space-y-2 ${className}`}>
      <label className="block text-sm font-medium text-gray-700">
        {label}
        {required && <span className="text-red-500 ml-1">*</span>}
      </label>

      {options.length === 0 ? (
        <div className="px-3 py-2 border border-gray-300 rounded-md bg-gray-50 text-sm text-gray-500">
          {loading ? (
            'Loading options...'
          ) : (
            <span>
              No options available. Please run: <code className="bg-gray-200 px-1 rounded">npm run db:seed</code>
            </span>
          )}
        </div>
      ) : (
        <Select
          value={typeof value === 'string' ? value : ''}
          onChange={handleSingleChange}
          required={required}
          error={!!error}
          helperText={error}
          options={selectOptions}
        />
      )}
    </div>
  );
}

export default AttributeSelect;
