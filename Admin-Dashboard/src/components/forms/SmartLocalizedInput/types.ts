/**
 * SmartLocalizedInput Types
 * ==========================
 */

import type { LocalizedText } from '@/lib/types/localized';
import type { TtsUserDefaults } from '@/lib/types/tts';

export type SupportedLanguage = 'ar' | 'en';

/**
 * Localized text with optional audio URLs
 */
export interface LocalizedTextWithAudio extends LocalizedText {
  audioAr?: string;
  audioEn?: string;
}

/**
 * Audio state for each language
 */
export interface AudioState {
  ar?: {
    url?: string;
    isPlaying: boolean;
    isGenerating: boolean;
  };
  en?: {
    url?: string;
    isPlaying: boolean;
    isGenerating: boolean;
  };
}

/**
 * Translation state
 */
export interface TranslationState {
  isTranslating: boolean;
  direction: 'ar-to-en' | 'en-to-ar' | null;
  error?: string;
}

/**
 * SmartLocalizedInput Props
 */
export interface SmartLocalizedInputProps {
  // Basic props
  label: string;
  value: LocalizedText;
  onChange: (value: LocalizedText) => void;

  // Optional props
  placeholder?: { ar?: string; en?: string };
  required?: boolean;
  multiline?: boolean;
  rows?: number;
  error?: string;
  className?: string;
  readOnly?: boolean;

  // AI Features
  enableTranslation?: boolean;
  enableTTS?: boolean;
  /** When set, TTS requests include model/voice/style from these saved defaults */
  ttsUserDefaults?: TtsUserDefaults;

  // Audio
  audioValue?: { ar?: string; en?: string };
  onAudioChange?: (audio: { ar?: string; en?: string }) => void;

  // Translation context
  translationContext?: string;

  // Variant
  variant?: 'default' | 'compact' | 'inline';
}

/**
 * API Response types
 */
export interface TranslateResponse {
  success: boolean;
  translatedText?: string;
  error?: string;
}

export interface TTSResponse {
  success: boolean;
  audioUrl?: string;
  error?: string;
}
