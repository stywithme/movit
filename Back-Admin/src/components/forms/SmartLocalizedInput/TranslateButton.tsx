/**
 * TranslateButton Component
 * ==========================
 * 
 * Button to trigger translation between languages.
 */

'use client';

import { ArrowRightLeft, Loader2 } from 'lucide-react';
import type { SupportedLanguage } from './types';

interface TranslateButtonProps {
  isTranslating: boolean;
  sourceLanguage: SupportedLanguage;
  hasSourceText: boolean;
  onClick: () => void;
  disabled?: boolean;
  size?: 'sm' | 'md';
}

export function TranslateButton({
  isTranslating,
  sourceLanguage,
  hasSourceText,
  onClick,
  disabled = false,
  size = 'sm',
}: TranslateButtonProps) {
  const targetLang = sourceLanguage === 'en' ? 'AR' : 'EN';
  const iconSize = size === 'sm' ? 14 : 16;
  const padding = size === 'sm' ? 'p-1.5' : 'p-2';

  const isDisabled = disabled || isTranslating || !hasSourceText;

  return (
    <button
      type="button"
      onClick={onClick}
      disabled={isDisabled}
      title={hasSourceText 
        ? `Translate to ${targetLang}` 
        : `Enter ${sourceLanguage === 'en' ? 'English' : 'Arabic'} text first`
      }
      className={`
        ${padding} rounded-md transition-all duration-200
        ${isDisabled
          ? 'text-gray-300 cursor-not-allowed'
          : 'text-gray-500 hover:text-blue-600 hover:bg-blue-50 active:bg-blue-100'
        }
      `}
    >
      {isTranslating ? (
        <Loader2 size={iconSize} className="animate-spin" />
      ) : (
        <ArrowRightLeft size={iconSize} />
      )}
    </button>
  );
}
