import type { TtsGeneratePayload, TtsUserDefaults } from '@/lib/types/tts';

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
  if (d.model.trim()) payload.model = d.model.trim();
  if (voiceName.trim()) payload.voiceName = voiceName.trim();
  if (languageCode.trim()) payload.languageCode = languageCode.trim();
  if (d.sharedStylePrompt.trim()) payload.sharedStylePrompt = d.sharedStylePrompt.trim();
  if (stylePrompt.trim()) payload.stylePrompt = stylePrompt.trim();
  if (d.systemInstruction.trim()) payload.systemInstruction = d.systemInstruction.trim();
  if (temperature !== undefined) payload.temperature = temperature;
  if (seed !== undefined) payload.seed = seed;
  return payload;
}
