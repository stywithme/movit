/**
 * Gemini AI Module Exports
 */

export { geminiClient, geminiConfig, type SupportedLanguage } from './client';
export { translateText } from './translate';
export { generateSpeech, deleteAudioFile } from './tts';
export {
  DEFAULT_TTS_MODEL,
  LEGACY_TTS_MODEL_ALIASES,
  TTS_VOICES,
  TTS_MODELS,
  TTS_LANGUAGE_CODES,
  normalizeTtsModelId,
  isSupportedTtsModel,
  type TtsVoiceEntry,
  type TtsModelEntry,
  type TtsLanguageCodeEntry,
} from './tts-constants';
