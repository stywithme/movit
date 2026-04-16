/**
 * useTextToSpeech Hook
 * =====================
 * 
 * Hook for generating and playing speech from text.
 */

'use client';

import { useState, useCallback, useRef, useEffect } from 'react';
import type { SupportedLanguage, TTSResponse } from '../types';
import type { TtsGeneratePayload } from '@/lib/types/tts';

interface AudioPlayerState {
  isPlaying: boolean;
  isGenerating: boolean;
  currentTime: number;
  duration: number;
}

interface UseTextToSpeechReturn {
  // State
  isGenerating: boolean;
  isPlaying: boolean;
  error: string | null;
  
  // Actions
  generateSpeech: (
    text: string,
    language: SupportedLanguage,
    existingUrl?: string,
    ttsOptions?: TtsGeneratePayload
  ) => Promise<string | null>;
  play: (audioUrl: string) => void;
  pause: () => void;
  stop: () => void;
  deleteAudio: (audioUrl: string) => Promise<boolean>;
  
  // Utils
  clearError: () => void;
}

interface UseTextToSpeechOptions {
  routeBase?: string;
}

export function useTextToSpeech(options: UseTextToSpeechOptions = {}): UseTextToSpeechReturn {
  const routeBase = options.routeBase || '/api/ai';
  const [isGenerating, setIsGenerating] = useState(false);
  const [isPlaying, setIsPlaying] = useState(false);
  const [error, setError] = useState<string | null>(null);
  
  const audioRef = useRef<HTMLAudioElement | null>(null);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (audioRef.current) {
        audioRef.current.pause();
        audioRef.current = null;
      }
    };
  }, []);

  const generateSpeech = useCallback(async (
    text: string, 
    language: SupportedLanguage,
    existingUrl?: string,
    ttsOptions?: TtsGeneratePayload
  ): Promise<string | null> => {
    if (!text.trim()) {
      setError('No text to generate speech from');
      return null;
    }

    setIsGenerating(true);
    setError(null);

    try {
      const body: Record<string, unknown> = { text, language };
      if (ttsOptions) {
        if (ttsOptions.model) body.model = ttsOptions.model;
        if (ttsOptions.voiceName) body.voiceName = ttsOptions.voiceName;
        if (ttsOptions.languageCode) body.languageCode = ttsOptions.languageCode;
        if (ttsOptions.sharedStylePrompt) body.sharedStylePrompt = ttsOptions.sharedStylePrompt;
        if (ttsOptions.stylePrompt) body.stylePrompt = ttsOptions.stylePrompt;
        if (ttsOptions.systemInstruction) body.systemInstruction = ttsOptions.systemInstruction;
        if (typeof ttsOptions.temperature === 'number') body.temperature = ttsOptions.temperature;
        if (typeof ttsOptions.seed === 'number') body.seed = ttsOptions.seed;
      }

      const response = await fetch(`${routeBase}/tts`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });

      const data: TTSResponse = await response.json().catch(() => ({
        success: false,
        error: `TTS request failed (${response.status})`,
      }));

      if (!response.ok || !data.success || !data.audioUrl) {
        throw new Error(data.error || 'TTS generation failed');
      }

      // Delete replaced audio only after new audio is safely generated.
      if (existingUrl && existingUrl !== data.audioUrl) {
        try {
          await fetch(`${routeBase}/tts`, {
            method: 'DELETE',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ audioPath: existingUrl }),
          });
        } catch (deleteError) {
          console.warn('Failed to delete replaced audio:', deleteError);
        }
      }

      return data.audioUrl;
    } catch (err) {
      const message = err instanceof Error ? err.message : 'TTS generation failed';
      setError(message);
      return null;
    } finally {
      setIsGenerating(false);
    }
  }, [routeBase]);

  const play = useCallback((audioUrl: string) => {
    // Stop any existing playback
    if (audioRef.current) {
      audioRef.current.pause();
    }

    // Create new audio element
    const audio = new Audio(audioUrl);
    audioRef.current = audio;

    audio.onplay = () => {
      setIsPlaying(true);
    };
    audio.onpause = () => {
      setIsPlaying(false);
    };
    audio.onended = () => {
      setIsPlaying(false);
    };
    audio.onerror = (e) => {
      const errorCode = audio.error?.code;
      const errorMessage = audio.error?.message || 'Unknown error';
      console.error('[TTS Play] Error:', { code: errorCode, message: errorMessage, url: audioUrl }, e);
      
      // Provide helpful error message
      if (errorCode === 4) {
        setError('Audio format not supported or file not accessible. Check if the URL is publicly accessible.');
      } else {
        setError(`Failed to play audio: ${errorMessage}`);
      }
      setIsPlaying(false);
    };

    audio.play().catch((err) => {
      console.error('[TTS Play] Play failed:', err);
      setError('Failed to play audio');
    });
  }, []);

  const pause = useCallback(() => {
    if (audioRef.current) {
      audioRef.current.pause();
    }
  }, []);

  const stop = useCallback(() => {
    if (audioRef.current) {
      audioRef.current.pause();
      audioRef.current.currentTime = 0;
    }
    setIsPlaying(false);
  }, []);

  const deleteAudio = useCallback(async (audioUrl: string): Promise<boolean> => {
    try {
      const response = await fetch(`${routeBase}/tts`, {
        method: 'DELETE',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ audioPath: audioUrl }),
      });

      const data = await response.json().catch(() => ({ success: false }));
      if (!response.ok || !data.success) {
        setError(data.error || 'Failed to delete audio');
        return false;
      }
      return true;
    } catch {
      setError('Failed to delete audio');
      return false;
    }
  }, [routeBase]);

  const clearError = useCallback(() => {
    setError(null);
  }, []);

  return {
    isGenerating,
    isPlaying,
    error,
    generateSpeech,
    play,
    pause,
    stop,
    deleteAudio,
    clearError,
  };
}
