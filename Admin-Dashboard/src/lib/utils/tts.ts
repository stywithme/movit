import type { TtsGeneratePayload, TtsUserDefaults } from '@/lib/types/tts';

const LEGACY_TTS_MODEL_ALIASES: Record<string, string> = {
  'gemini-3.1-flash-preview-tts': 'gemini-3.1-flash-tts-preview',
};

function parseOptionalFloat(value: string): number | undefined {
  const trimmed = value.trim();
  if (!trimmed) return undefined;
  const parsed = Number.parseFloat(trimmed);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function parseOptionalInt(value: string): number | undefined {
  const trimmed = value.trim();
  if (!trimmed) return undefined;
  const parsed = Number.parseInt(trimmed, 10);
  return Number.isFinite(parsed) ? parsed : undefined;
}

export function normalizeTtsModelId(value?: string | null): string {
  const trimmed = value?.trim() || '';
  if (!trimmed) return '';
  return LEGACY_TTS_MODEL_ALIASES[trimmed] ?? trimmed;
}

export function normalizeTtsUserDefaults(defaults: TtsUserDefaults): TtsUserDefaults {
  return {
    ...defaults,
    model: normalizeTtsModelId(defaults.model),
  };
}

/**
 * Build POST /api/ai/tts body options for one language from saved user defaults.
 */
export function buildTtsGeneratePayloadFromDefaults(
  language: 'ar' | 'en',
  d: TtsUserDefaults
): TtsGeneratePayload {
  const voiceName = language === 'ar' ? d.voiceNameAr : d.voiceNameEn;
  const languageCode = language === 'ar' ? d.languageCodeAr : d.languageCodeEn;
  const stylePrompt = language === 'ar' ? d.stylePromptAr : d.stylePromptEn;
  const temperature = parseOptionalFloat(d.temperature);
  const seed = parseOptionalInt(d.seed);

  const payload: TtsGeneratePayload = {};
  const model = normalizeTtsModelId(d.model);
  if (model) payload.model = model;
  if (voiceName.trim()) payload.voiceName = voiceName.trim();
  if (languageCode.trim()) payload.languageCode = languageCode.trim();
  if (d.sharedStylePrompt.trim()) payload.sharedStylePrompt = d.sharedStylePrompt.trim();
  if (stylePrompt.trim()) payload.stylePrompt = stylePrompt.trim();
  if (d.systemInstruction.trim()) payload.systemInstruction = d.systemInstruction.trim();
  if (temperature !== undefined) payload.temperature = temperature;
  if (seed !== undefined) payload.seed = seed;
  return payload;
}
