/**
 * useTranslation Hook
 * ====================
 * 
 * Hook for translating text between Arabic and English.
 */

'use client';

import { useState, useCallback } from 'react';
import type { SupportedLanguage, TranslateResponse } from '../types';

interface UseTranslationOptions {
  context?: string;
}

interface UseTranslationReturn {
  isTranslating: boolean;
  error: string | null;
  translate: (text: string, from: SupportedLanguage, to: SupportedLanguage) => Promise<string | null>;
  clearError: () => void;
}

export function useTranslation(options: UseTranslationOptions = {}): UseTranslationReturn {
  const [isTranslating, setIsTranslating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const translate = useCallback(async (
    text: string, 
    from: SupportedLanguage, 
    to: SupportedLanguage
  ): Promise<string | null> => {
    if (!text.trim()) {
      setError('No text to translate');
      return null;
    }

    setIsTranslating(true);
    setError(null);

    try {
      const response = await fetch('/api/ai/translate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          text,
          from,
          to,
          context: options.context,
        }),
      });

      const data: TranslateResponse = await response.json();

      if (!data.success || !data.translatedText) {
        throw new Error(data.error || 'Translation failed');
      }

      return data.translatedText;
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Translation failed';
      setError(message);
      return null;
    } finally {
      setIsTranslating(false);
    }
  }, [options.context]);

  const clearError = useCallback(() => {
    setError(null);
  }, []);

  return {
    isTranslating,
    error,
    translate,
    clearError,
  };
}
