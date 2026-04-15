/**
 * Gemini TTS catalog (prebuilt voices, models, language codes).
 * Source: https://ai.google.dev/gemini-api/docs/speech-generation
 * Update this file when Google adds new voices or models.
 */

export interface TtsVoiceEntry {
  name: string;
  /** Short style hint from Google docs (for UI labels) */
  style: string;
}

export interface TtsModelEntry {
  id: string;
  label: string;
}

export interface TtsLanguageCodeEntry {
  code: string;
  label: string;
}

/**
 * Prebuilt Gemini TTS voices (30). Pass `name` as `voiceName` / `prebuiltVoiceConfig.voiceName`.
 */
export const TTS_VOICES: readonly TtsVoiceEntry[] = [
  { name: 'Zephyr', style: 'Bright' },
  { name: 'Puck', style: 'Upbeat' },
  { name: 'Charon', style: 'Informative' },
  { name: 'Kore', style: 'Firm' },
  { name: 'Fenrir', style: 'Excitable' },
  { name: 'Leda', style: 'Youthful' },
  { name: 'Orus', style: 'Firm' },
  { name: 'Aoede', style: 'Breezy' },
  { name: 'Callirrhoe', style: 'Easy-going' },
  { name: 'Autonoe', style: 'Bright' },
  { name: 'Enceladus', style: 'Breathy' },
  { name: 'Iapetus', style: 'Clear' },
  { name: 'Umbriel', style: 'Easy-going' },
  { name: 'Algieba', style: 'Smooth' },
  { name: 'Despina', style: 'Smooth' },
  { name: 'Erinome', style: 'Clear' },
  { name: 'Algenib', style: 'Gravelly' },
  { name: 'Rasalgethi', style: 'Informative' },
  { name: 'Laomedeia', style: 'Upbeat' },
  { name: 'Achernar', style: 'Soft' },
  { name: 'Alnilam', style: 'Firm' },
  { name: 'Schedar', style: 'Even' },
  { name: 'Gacrux', style: 'Mature' },
  { name: 'Pulcherrima', style: 'Forward' },
  { name: 'Achird', style: 'Friendly' },
  { name: 'Zubenelgenubi', style: 'Casual' },
  { name: 'Vindemiatrix', style: 'Gentle' },
  { name: 'Sadachbia', style: 'Lively' },
  { name: 'Sadaltager', style: 'Knowledgeable' },
  { name: 'Sulafat', style: 'Warm' },
] as const;

/**
 * Known Gemini TTS-capable model IDs. Empty string in UI means server default (env).
 */
export const TTS_MODELS: readonly TtsModelEntry[] = [
  { id: 'gemini-2.5-flash-preview-tts', label: 'Gemini 2.5 Flash Preview TTS' },
  { id: 'gemini-2.5-flash-lite-preview-tts', label: 'Gemini 2.5 Flash Lite Preview TTS' },
  { id: 'gemini-2.5-pro-preview-tts', label: 'Gemini 2.5 Pro Preview TTS' },
  { id: 'gemini-3.1-flash-preview-tts', label: 'Gemini 3.1 Flash Preview TTS' },
] as const;

/**
 * Common BCP-47 codes for Arabic / English TTS (optional `languageCode` in API).
 */
export const TTS_LANGUAGE_CODES: readonly TtsLanguageCodeEntry[] = [
  { code: 'ar-EG', label: 'Arabic (Egypt)' },
  { code: 'ar-SA', label: 'Arabic (Saudi Arabia)' },
  { code: 'ar-AE', label: 'Arabic (UAE)' },
  { code: 'ar-XA', label: 'Arabic (generic)' },
  { code: 'en-US', label: 'English (US)' },
  { code: 'en-GB', label: 'English (UK)' },
  { code: 'en-AU', label: 'English (Australia)' },
  { code: 'en-IN', label: 'English (India)' },
] as const;
