/**
 * AudioControls Component
 * ========================
 * 
 * Controls for generating and playing audio.
 */

'use client';

import { Volume2, Play, Square, Loader2, RefreshCw, Trash2 } from 'lucide-react';
import type { SupportedLanguage } from './types';

interface AudioControlsProps {
  language: SupportedLanguage;
  audioUrl?: string;
  hasText: boolean;
  isGenerating: boolean;
  isPlaying: boolean;
  onGenerate: () => void;
  onPlay: () => void;
  onStop: () => void;
  onDelete: () => void;
  size?: 'sm' | 'md';
  readOnly?: boolean;
}

export function AudioControls({
  language,
  audioUrl,
  hasText,
  isGenerating,
  isPlaying,
  onGenerate,
  onPlay,
  onStop,
  onDelete,
  size = 'sm',
  readOnly = false,
}: AudioControlsProps) {
  const iconSize = size === 'sm' ? 14 : 16;
  const buttonClass = `
    p-1.5 rounded-md transition-all duration-200
    disabled:text-gray-300 disabled:cursor-not-allowed
  `;

  const hasAudio = !!audioUrl;
  const langLabel = language === 'ar' ? 'AR' : 'EN';

  return (
    <div className="flex items-center gap-1">
      {/* Audio indicator */}
      {!hasAudio && readOnly ? (
        <span className="text-xs text-gray-400 opacity-50">
          <Volume2 size={iconSize} />
        </span>
      ) : (
        <span
          className={`text-xs font-medium ${hasAudio ? 'text-green-600' : 'text-gray-400'}`}
          title={hasAudio ? `${langLabel} audio available` : `No ${langLabel} audio`}
        >
          {/* Only show indicator if not readOnly or if audio exists */}
          {!readOnly && <Volume2 size={iconSize} className={hasAudio ? '' : 'opacity-50'} />}
        </span>
      )}

      {/* Play/Stop button */}
      {hasAudio && (
        <button
          type="button"
          onClick={isPlaying ? onStop : onPlay}
          className={`${buttonClass} text-green-600 hover:text-green-700 hover:bg-green-50`}
          title={isPlaying ? 'Stop' : 'Play'}
        >
          {isPlaying ? (
            <Square size={iconSize} className="fill-current" />
          ) : (
            <Play size={iconSize} className="fill-current" />
          )}
        </button>
      )}

      {/* Generate/Regenerate button - Hidden in readOnly */}
      {!readOnly && (
        <button
          type="button"
          onClick={onGenerate}
          disabled={!hasText || isGenerating}
          className={`
            ${buttonClass}
            ${hasAudio
              ? 'text-amber-600 hover:text-amber-700 hover:bg-amber-50'
              : 'text-blue-600 hover:text-blue-700 hover:bg-blue-50'
            }
          `}
          title={hasAudio ? 'Regenerate audio' : 'Generate audio'}
        >
          {isGenerating ? (
            <Loader2 size={iconSize} className="animate-spin" />
          ) : hasAudio ? (
            <RefreshCw size={iconSize} />
          ) : (
            <Volume2 size={iconSize} />
          )}
        </button>
      )}

      {/* Delete button - Hidden in readOnly */}
      {!readOnly && hasAudio && (
        <button
          type="button"
          onClick={onDelete}
          disabled={isGenerating}
          className={`${buttonClass} text-red-500 hover:text-red-600 hover:bg-red-50`}
          title="Delete audio"
        >
          <Trash2 size={iconSize} />
        </button>
      )}
    </div>
  );
}
