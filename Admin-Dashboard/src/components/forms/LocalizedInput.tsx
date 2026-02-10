'use client';

import { useState } from 'react';
import { LocalizedText } from '@/lib/types/localized';
import { Input, Textarea } from '@/components/ui';

interface LocalizedInputProps {
  label: string;
  value: LocalizedText;
  onChange: (value: LocalizedText) => void;
  placeholder?: { ar?: string; en?: string };
  required?: boolean;
  multiline?: boolean;
  rows?: number;
  error?: string;
  className?: string;
}

/**
 * LocalizedInput - Input component for multi-language text (Arabic & English)
 */
export function LocalizedInput({
  label,
  value,
  onChange,
  placeholder = {},
  required = false,
  multiline = false,
  rows = 3,
  error,
  className = '',
}: LocalizedInputProps) {
  const [activeTab, setActiveTab] = useState<'en' | 'ar'>('en');

  const handleChange = (lang: 'en' | 'ar', newValue: string) => {
    onChange({
      ...value,
      [lang]: newValue,
    });
  };

  return (
    <div className={`space-y-2 ${className}`}>
      <label className="block text-sm font-medium text-gray-700">
        {label}
        {required && <span className="text-red-500 ml-1">*</span>}
      </label>

      {/* Language Tabs */}
      <div className="flex border-b border-gray-200">
        <button
          type="button"
          onClick={() => setActiveTab('en')}
          className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${
            activeTab === 'en'
              ? 'border-blue-500 text-blue-600'
              : 'border-transparent text-gray-500 hover:text-gray-700'
          }`}
        >
          English
        </button>
        <button
          type="button"
          onClick={() => setActiveTab('ar')}
          className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${
            activeTab === 'ar'
              ? 'border-blue-500 text-blue-600'
              : 'border-transparent text-gray-500 hover:text-gray-700'
          }`}
        >
          العربية
        </button>
      </div>

      {/* Input Fields */}
      <div className="relative">
        {activeTab === 'en' && (
          <>
            {multiline ? (
              <Textarea
                value={value.en || ''}
                onChange={(e) => handleChange('en', e.target.value)}
                placeholder={placeholder.en || `Enter ${label.toLowerCase()} in English`}
                required={required}
                rows={rows}
                error={!!error}
                dir="ltr"
              />
            ) : (
              <Input
                type="text"
                value={value.en || ''}
                onChange={(e) => handleChange('en', e.target.value)}
                placeholder={placeholder.en || `Enter ${label.toLowerCase()} in English`}
                required={required}
                error={!!error}
                dir="ltr"
              />
            )}
          </>
        )}
        {activeTab === 'ar' && (
          <>
            {multiline ? (
              <Textarea
                value={value.ar || ''}
                onChange={(e) => handleChange('ar', e.target.value)}
                placeholder={placeholder.ar || `أدخل ${label} بالعربية`}
                required={required}
                rows={rows}
                error={!!error}
                dir="rtl"
              />
            ) : (
              <Input
                type="text"
                value={value.ar || ''}
                onChange={(e) => handleChange('ar', e.target.value)}
                placeholder={placeholder.ar || `أدخل ${label} بالعربية`}
                required={required}
                error={!!error}
                dir="rtl"
              />
            )}
          </>
        )}
      </div>

      {/* Preview of both languages */}
      <div className="flex gap-4 text-xs text-gray-500">
        <span className={value.en ? '' : 'text-red-400'}>
          EN: {value.en || '(empty)'}
        </span>
        <span className={value.ar ? '' : 'text-red-400'}>
          AR: {value.ar || '(فارغ)'}
        </span>
      </div>

      {error && <p className="text-sm text-red-500">{error}</p>}
    </div>
  );
}

export default LocalizedInput;

