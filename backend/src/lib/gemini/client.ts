/**
 * Gemini AI Client Configuration
 * ================================
 * 
 * Centralized client setup for Google Gemini API.
 * Uses environment variables for configuration.
 */

import { GoogleGenAI } from '@google/genai';
import { DEFAULT_TTS_MODEL, normalizeTtsModelId } from './tts-constants';

// Validate API key exists
if (!process.env.GEMINI_API_KEY) {
  console.warn('⚠️ GEMINI_API_KEY is not set. AI features will not work.');
}

let geminiClientInstance: GoogleGenAI | null = null;

export function getGeminiClient(): GoogleGenAI {
  const apiKey = process.env.GEMINI_API_KEY?.trim();
  if (!apiKey) {
    throw new Error('GEMINI_API_KEY is not set. AI features are unavailable.');
  }
  if (!geminiClientInstance) {
    geminiClientInstance = new GoogleGenAI({ apiKey });
  }
  return geminiClientInstance;
}

/** @deprecated Use getGeminiClient() — kept for existing imports. */
export const geminiClient = new Proxy({} as GoogleGenAI, {
  get(_target, prop, receiver) {
    return Reflect.get(getGeminiClient(), prop, receiver);
  },
});

/**
 * Model configurations from environment
 */
export const geminiConfig = {
  // Text generation model (for translation)
  textModel: process.env.GEMINI_TEXT_MODEL || 'gemini-2.0-flash',
  
  // TTS model
  ttsModel: normalizeTtsModelId(process.env.GEMINI_TTS_MODEL) || DEFAULT_TTS_MODEL,
  
  // Voice configurations
  voices: {
    ar: process.env.GEMINI_VOICE_AR || 'Kore',
    en: process.env.GEMINI_VOICE_EN || 'Kore',
  },
} as const;

export type SupportedLanguage = 'ar' | 'en';
