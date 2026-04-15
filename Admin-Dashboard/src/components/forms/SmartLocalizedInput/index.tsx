/**
 * SmartLocalizedInput Component
 * ==============================
 * 
 * An intelligent input component for bilingual text (Arabic & English)
 * with AI-powered translation and text-to-speech capabilities.
 * 
 * Features:
 * - Tab-based language switching
 * - One-click translation between AR/EN
 * - TTS audio generation for both languages
 * - Audio playback controls
 * - Compact, minimalist design
 */

'use client';

import { useState, useCallback } from 'react';
import { Input, Textarea } from '@/components/ui';
import { TranslateButton } from './TranslateButton';
import { AudioControls } from './AudioControls';
import { useTranslation, useTextToSpeech } from './hooks';
import type { SmartLocalizedInputProps, SupportedLanguage } from './types';
import { buildTtsGeneratePayloadFromDefaults } from '@/lib/utils/tts';

export function SmartLocalizedInput({
  label,
  value,
  onChange,
  placeholder = {},
  required = false,
  multiline = false,
  rows = 3,
  error,
  className = '',
  enableTranslation = true,
  enableTTS = true,
  ttsUserDefaults,
  audioValue: externalAudioValue,
  onAudioChange: externalOnAudioChange,
  translationContext,
  variant = 'default',
  readOnly = false,
}: SmartLocalizedInputProps) {
  const [activeTab, setActiveTab] = useState<SupportedLanguage>('en');

  // Internal audio state (used when external props not provided)
  const [internalAudioValue, setInternalAudioValue] = useState<{ ar?: string; en?: string }>({});

  // Use external or internal audio state
  const audioValue = externalAudioValue ?? internalAudioValue;
  const onAudioChange = externalOnAudioChange ?? setInternalAudioValue;

  // Translation hook
  const {
    isTranslating,
    translate,
    error: translateError
  } = useTranslation({ context: translationContext });

  // TTS hook
  const {
    isGenerating,
    isPlaying,
    generateSpeech,
    play,
    stop,
    deleteAudio,
    error: ttsError
  } = useTextToSpeech();

  // Handle text change
  const handleChange = useCallback((lang: SupportedLanguage, newValue: string) => {
    if (readOnly) return;
    onChange({
      ...value,
      [lang]: newValue,
    });
  }, [onChange, value, readOnly]);

  // Handle translation
  const handleTranslate = useCallback(async (fromLang: SupportedLanguage) => {
    if (readOnly) return;
    const toLang = fromLang === 'en' ? 'ar' : 'en';
    const sourceText = value[fromLang];

    if (!sourceText?.trim()) return;

    const translated = await translate(sourceText, fromLang, toLang);
    if (translated) {
      onChange({
        ...value,
        [toLang]: translated,
      });
      // Switch to target tab to show result
      setActiveTab(toLang);
    }
  }, [translate, value, onChange, readOnly]);

  // Handle TTS generation
  const handleGenerateAudio = useCallback(async (lang: SupportedLanguage) => {
    if (readOnly) return;
    const text = value[lang];
    if (!text?.trim()) return;

    const existingUrl = lang === 'ar' ? audioValue?.ar : audioValue?.en;
    const ttsPayload = ttsUserDefaults ? buildTtsGeneratePayloadFromDefaults(lang, ttsUserDefaults) : undefined;
    const audioUrl = await generateSpeech(text, lang, existingUrl, ttsPayload);

    console.log('[SmartInput] Generated audio URL:', audioUrl);

    if (audioUrl) {
      // Update audio state
      const newAudioValue = {
        ...audioValue,
        [lang]: audioUrl,
      };
      console.log('[SmartInput] Updating audio value:', newAudioValue);
      onAudioChange(newAudioValue);
      // Auto-play the generated audio
      play(audioUrl);
    }
  }, [value, audioValue, generateSpeech, onAudioChange, play, readOnly, ttsUserDefaults]);

  // Handle audio playback
  const handlePlay = useCallback((lang: SupportedLanguage) => {
    console.log('[SmartInput] Play clicked, lang:', lang, 'audioValue:', audioValue);
    const url = audioValue?.[lang];
    console.log('[SmartInput] URL to play:', url);
    if (url) play(url);
  }, [audioValue, play]);

  // Handle audio deletion
  const handleDeleteAudio = useCallback(async (lang: SupportedLanguage) => {
    if (readOnly) return;
    const url = audioValue?.[lang];
    if (url) {
      await deleteAudio(url);
      const newAudioValue = {
        ...audioValue,
        [lang]: undefined,
      };
      onAudioChange(newAudioValue);
    }
  }, [audioValue, deleteAudio, onAudioChange, readOnly]);

  // Render input based on type
  const renderInput = (lang: SupportedLanguage) => {
    const isArabic = lang === 'ar';
    const currentValue = value[lang] || '';
    const currentPlaceholder = isArabic
      ? (placeholder.ar || `أدخل ${label} بالعربية`)
      : (placeholder.en || `Enter ${label.toLowerCase()} in English`);

    const InputComponent = multiline ? Textarea : Input;

    return (
      <div className="relative">
        <InputComponent
          value={currentValue}
          onChange={(e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => handleChange(lang, e.target.value)}
          placeholder={currentPlaceholder}
          required={required}
          error={!!error}
          dir={isArabic ? 'rtl' : 'ltr'}
          rows={multiline ? rows : undefined}
          className="pr-10"
          readOnly={readOnly}
          disabled={readOnly}
        />

        {/* Translate button inside input */}
        {enableTranslation && !readOnly && (
          <div className="absolute top-1/2 -translate-y-1/2 right-1">
            <TranslateButton
              isTranslating={isTranslating}
              sourceLanguage={lang}
              hasSourceText={!!currentValue.trim()}
              onClick={() => handleTranslate(lang)}
            />
          </div>
        )}
      </div>
    );
  };

  // Compact variant - side by side with inline controls
  if (variant === 'compact') {
    return (
      <div className={`space-y-2 ${className}`}>
        <label className="block text-sm font-medium text-gray-700">
          {label}
          {required && <span className="text-red-500 ml-1">*</span>}
        </label>

        <div className="grid grid-cols-2 gap-3">
          {/* English */}
          <div className="space-y-1">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-gray-500">EN</span>
              <div className="flex items-center gap-1">
                {enableTranslation && !readOnly && (
                  <TranslateButton
                    isTranslating={isTranslating}
                    sourceLanguage="en"
                    hasSourceText={!!(value.en?.trim())}
                    onClick={() => handleTranslate('en')}
                    size="sm"
                  />
                )}
                {enableTTS && (
                  <AudioControls
                    language="en"
                    audioUrl={audioValue?.en}
                    hasText={!!(value.en?.trim())}
                    isGenerating={isGenerating}
                    isPlaying={isPlaying}
                    onGenerate={() => handleGenerateAudio('en')}
                    onPlay={() => handlePlay('en')}
                    onStop={stop}
                    onDelete={() => handleDeleteAudio('en')}
                    size="sm"
                    readOnly={readOnly}
                  />
                )}
              </div>
            </div>
            {multiline ? (
              <Textarea
                value={value.en || ''}
                onChange={(e) => handleChange('en', e.target.value)}
                placeholder={placeholder.en || `Enter ${label.toLowerCase()}`}
                required={required}
                error={!!error}
                rows={rows}
                dir="ltr"
                readOnly={readOnly}
                disabled={readOnly}
              />
            ) : (
              <Input
                value={value.en || ''}
                onChange={(e) => handleChange('en', e.target.value)}
                placeholder={placeholder.en || `Enter ${label.toLowerCase()}`}
                required={required}
                error={!!error}
                dir="ltr"
                readOnly={readOnly}
                disabled={readOnly}
              />
            )}
          </div>

          {/* Arabic */}
          <div className="space-y-1">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-gray-500">AR</span>
              <div className="flex items-center gap-1">
                {enableTranslation && !readOnly && (
                  <TranslateButton
                    isTranslating={isTranslating}
                    sourceLanguage="ar"
                    hasSourceText={!!(value.ar?.trim())}
                    onClick={() => handleTranslate('ar')}
                    size="sm"
                  />
                )}
                {enableTTS && (
                  <AudioControls
                    language="ar"
                    audioUrl={audioValue?.ar}
                    hasText={!!(value.ar?.trim())}
                    isGenerating={isGenerating}
                    isPlaying={isPlaying}
                    onGenerate={() => handleGenerateAudio('ar')}
                    onPlay={() => handlePlay('ar')}
                    onStop={stop}
                    onDelete={() => handleDeleteAudio('ar')}
                    size="sm"
                    readOnly={readOnly}
                  />
                )}
              </div>
            </div>
            {multiline ? (
              <Textarea
                value={value.ar || ''}
                onChange={(e) => handleChange('ar', e.target.value)}
                placeholder={placeholder.ar || `أدخل ${label}`}
                required={required}
                error={!!error}
                rows={rows}
                dir="rtl"
                readOnly={readOnly}
                disabled={readOnly}
              />
            ) : (
              <Input
                value={value.ar || ''}
                onChange={(e) => handleChange('ar', e.target.value)}
                placeholder={placeholder.ar || `أدخل ${label}`}
                required={required}
                error={!!error}
                dir="rtl"
                readOnly={readOnly}
                disabled={readOnly}
              />
            )}
          </div>
        </div>

        {/* Error message */}
        {(error || translateError || ttsError) && (
          <p className="text-sm text-red-500">{error || translateError || ttsError}</p>
        )}
      </div>
    );
  }

  // Inline variant - side by side with controls above each input
  if (variant === 'inline') {
    return (
      <div className={`grid grid-cols-2 gap-3 ${className}`}>
        {/* English */}
        <div className="space-y-1">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-gray-500">EN</span>
            <div className="flex items-center gap-0.5">
              {enableTranslation && !readOnly && (
                <TranslateButton
                  isTranslating={isTranslating}
                  sourceLanguage="en"
                  hasSourceText={!!(value.en?.trim())}
                  onClick={() => handleTranslate('en')}
                  size="sm"
                />
              )}
              {enableTTS && (
                <AudioControls
                  language="en"
                  audioUrl={audioValue?.en}
                  hasText={!!(value.en?.trim())}
                  isGenerating={isGenerating}
                  isPlaying={isPlaying}
                  onGenerate={() => handleGenerateAudio('en')}
                  onPlay={() => handlePlay('en')}
                  onStop={stop}
                  onDelete={() => handleDeleteAudio('en')}
                  size="sm"
                  readOnly={readOnly}
                />
              )}
            </div>
          </div>
          <Input
            value={value.en || ''}
            onChange={(e) => handleChange('en', e.target.value)}
            placeholder={placeholder.en || 'English'}
            required={required}
            error={!!error}
            dir="ltr"
            className="text-sm"
            readOnly={readOnly}
            disabled={readOnly}
          />
        </div>

        {/* Arabic */}
        <div className="space-y-1">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-gray-500">AR</span>
            <div className="flex items-center gap-0.5">
              {enableTranslation && !readOnly && (
                <TranslateButton
                  isTranslating={isTranslating}
                  sourceLanguage="ar"
                  hasSourceText={!!(value.ar?.trim())}
                  onClick={() => handleTranslate('ar')}
                  size="sm"
                />
              )}
              {enableTTS && (
                <AudioControls
                  language="ar"
                  audioUrl={audioValue?.ar}
                  hasText={!!(value.ar?.trim())}
                  isGenerating={isGenerating}
                  isPlaying={isPlaying}
                  onGenerate={() => handleGenerateAudio('ar')}
                  onPlay={() => handlePlay('ar')}
                  onStop={stop}
                  onDelete={() => handleDeleteAudio('ar')}
                  size="sm"
                  readOnly={readOnly}
                />
              )}
            </div>
          </div>
          <Input
            value={value.ar || ''}
            onChange={(e) => handleChange('ar', e.target.value)}
            placeholder={placeholder.ar || 'العربية'}
            required={required}
            error={!!error}
            dir="rtl"
            className="text-sm"
            readOnly={readOnly}
            disabled={readOnly}
          />
        </div>
      </div>
    );
  }

  // Default variant - tabbed interface
  return (
    <div className={`space-y-2 ${className}`}>
      <label className="block text-sm font-medium text-gray-700">
        {label}
        {required && <span className="text-red-500 ml-1">*</span>}
      </label>

      {/* Language Tabs with Audio Controls */}
      <div className="flex items-center justify-between border-b border-gray-200">
        <div className="flex">
          <button
            type="button"
            onClick={() => setActiveTab('en')}
            className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${activeTab === 'en'
              ? 'border-blue-500 text-blue-600'
              : 'border-transparent text-gray-500 hover:text-gray-700'
              }`}
          >
            English
            {audioValue?.en && <span className="ml-1 text-green-500">🔊</span>}
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('ar')}
            className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${activeTab === 'ar'
              ? 'border-blue-500 text-blue-600'
              : 'border-transparent text-gray-500 hover:text-gray-700'
              }`}
          >
            العربية
            {audioValue?.ar && <span className="ml-1 text-green-500">🔊</span>}
          </button>
        </div>

        {/* Audio controls for active tab */}
        {enableTTS && (
          <AudioControls
            language={activeTab}
            audioUrl={activeTab === 'ar' ? audioValue?.ar : audioValue?.en}
            hasText={!!(value[activeTab]?.trim())}
            isGenerating={isGenerating}
            isPlaying={isPlaying}
            onGenerate={() => handleGenerateAudio(activeTab)}
            onPlay={() => handlePlay(activeTab)}
            onStop={stop}
            onDelete={() => handleDeleteAudio(activeTab)}
          />
        )}
      </div>

      {/* Input Fields */}
      <div className="relative">
        {activeTab === 'en' && renderInput('en')}
        {activeTab === 'ar' && renderInput('ar')}
      </div>

      {/* Preview of both languages */}
      <div className="flex gap-4 text-xs text-gray-500">
        <span className={value.en ? '' : 'text-red-400'}>
          EN: {value.en || '(empty)'}
        </span>
        <span className={value.ar ? '' : 'text-red-400'}>
          AR: {value.ar || '(فارغ)'}
        </span>
      </div>

      {/* Error messages */}
      {(error || translateError || ttsError) && (
        <p className="text-sm text-red-500">{error || translateError || ttsError}</p>
      )}
    </div>
  );
}

export default SmartLocalizedInput;

// Re-export types and hooks for external use
export type { SmartLocalizedInputProps, SupportedLanguage, LocalizedTextWithAudio } from './types';
export { useTranslation, useTextToSpeech } from './hooks';
