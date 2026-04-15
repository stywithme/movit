/**
 * Gemini AI Module Exports
 */

export { geminiClient, geminiConfig, type SupportedLanguage } from './client';
export { translateText } from './translate';
export { generateSpeech, deleteAudioFile } from './tts';
export {
  TTS_VOICES,
  TTS_MODELS,
  TTS_LANGUAGE_CODES,
  type TtsVoiceEntry,
  type TtsModelEntry,
  type TtsLanguageCodeEntry,
} from './tts-constants';
