/**
 * Gemini AI Client Configuration
 * ================================
 * 
 * Centralized client setup for Google Gemini API.
 * Uses environment variables for configuration.
 */

import { GoogleGenAI } from '@google/genai';

// Validate API key exists
if (!process.env.GEMINI_API_KEY) {
  console.warn('⚠️ GEMINI_API_KEY is not set. AI features will not work.');
}

/**
 * Gemini AI Client instance
 */
export const geminiClient = new GoogleGenAI({
  apiKey: process.env.GEMINI_API_KEY || '',
});

/**
 * Model configurations from environment
 */
export const geminiConfig = {
  // Text generation model (for translation)
  textModel: process.env.GEMINI_TEXT_MODEL || 'gemini-2.0-flash',
  
  // TTS model
  ttsModel: process.env.GEMINI_TTS_MODEL || 'gemini-2.5-flash-preview-tts',
  
  // Voice configurations
  voices: {
    ar: process.env.GEMINI_VOICE_AR || 'Kore',
    en: process.env.GEMINI_VOICE_EN || 'Kore',
  },
} as const;

export type SupportedLanguage = 'ar' | 'en';
