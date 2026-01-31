/**
 * useTextToSpeech Hook
 * =====================
 * 
 * Hook for generating and playing speech from text.
 */

'use client';

import { useState, useCallback, useRef, useEffect } from 'react';
import type { SupportedLanguage, TTSResponse } from '../types';

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
  generateSpeech: (text: string, language: SupportedLanguage, existingUrl?: string) => Promise<string | null>;
  play: (audioUrl: string) => void;
  pause: () => void;
  stop: () => void;
  deleteAudio: (audioUrl: string) => Promise<boolean>;
  
  // Utils
  clearError: () => void;
}

export function useTextToSpeech(): UseTextToSpeechReturn {
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
    existingUrl?: string
  ): Promise<string | null> => {
    if (!text.trim()) {
      setError('No text to generate speech from');
      return null;
    }

    setIsGenerating(true);
    setError(null);

    try {
      // Delete existing audio if regenerating
      if (existingUrl) {
        await fetch('/api/ai/tts', {
          method: 'DELETE',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ audioPath: existingUrl }),
        });
      }

      const response = await fetch('/api/ai/tts', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ text, language }),
      });

      const data: TTSResponse = await response.json();

      if (!data.success || !data.audioUrl) {
        throw new Error(data.error || 'TTS generation failed');
      }

      return data.audioUrl;
    } catch (err) {
      const message = err instanceof Error ? err.message : 'TTS generation failed';
      setError(message);
      return null;
    } finally {
      setIsGenerating(false);
    }
  }, []);

  const play = useCallback((audioUrl: string) => {
    console.log('[TTS Play] Attempting to play:', audioUrl);
    
    // Stop any existing playback
    if (audioRef.current) {
      audioRef.current.pause();
    }

    // Create new audio element
    const audio = new Audio(audioUrl);
    audioRef.current = audio;

    audio.onloadstart = () => console.log('[TTS Play] Loading started');
    audio.oncanplay = () => console.log('[TTS Play] Can play');
    audio.onplay = () => {
      console.log('[TTS Play] Playing');
      setIsPlaying(true);
    };
    audio.onpause = () => {
      console.log('[TTS Play] Paused');
      setIsPlaying(false);
    };
    audio.onended = () => {
      console.log('[TTS Play] Ended');
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
      const response = await fetch('/api/ai/tts', {
        method: 'DELETE',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ audioPath: audioUrl }),
      });

      const data = await response.json();
      return data.success;
    } catch {
      return false;
    }
  }, []);

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
