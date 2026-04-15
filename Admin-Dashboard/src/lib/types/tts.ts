/**
 * TTS configuration types (Admin Dashboard + API contract)
 */

export interface TtsConfigVoice {
  name: string;
  style: string;
}

export interface TtsConfigModel {
  id: string;
  label: string;
}

export interface TtsConfigLanguageCode {
  code: string;
  label: string;
}

export interface TtsConfigData {
  voices: TtsConfigVoice[];
  models: TtsConfigModel[];
  languageCodes: TtsConfigLanguageCode[];
  defaults: {
    ttsModel: string;
    voiceAr: string;
    voiceEn: string;
  };
}

/**
 * User-editable defaults (localStorage) — mirrors bulk advanced fields subset.
 */
export interface TtsUserDefaults {
  model: string;
  voiceNameAr: string;
  voiceNameEn: string;
  languageCodeAr: string;
  languageCodeEn: string;
  sharedStylePrompt: string;
  stylePromptAr: string;
  stylePromptEn: string;
  systemInstruction: string;
  temperature: string;
  seed: string;
}

/** Payload sent to POST /api/ai/tts (per single generation call) */
export interface TtsGeneratePayload {
  model?: string;
  voiceName?: string;
  languageCode?: string;
  sharedStylePrompt?: string;
  stylePrompt?: string;
  systemInstruction?: string;
  temperature?: number;
  seed?: number;
}

export const EMPTY_TTS_USER_DEFAULTS: TtsUserDefaults = {
  model: '',
  voiceNameAr: '',
  voiceNameEn: '',
  languageCodeAr: '',
  languageCodeEn: '',
  sharedStylePrompt: '',
  stylePromptAr: '',
  stylePromptEn: '',
  systemInstruction: '',
  temperature: '',
  seed: '',
};
