'use client';

/**
 * Inline UI: audio availability + play + library code for assigned messages.
 */

import { useCallback, useRef } from 'react';
import { Volume2, VolumeX, Play } from 'lucide-react';
export interface MessageAudioStatusProps {
  /** Arabic / English text (for accessibility) */
  audioAr?: string | null;
  audioEn?: string | null;
  /** Optional library message code (dashboard-only metadata) */
  sourceMessageCode?: string | null;
  /** Compact = one row */
  compact?: boolean;
  className?: string;
}

function resolvePlayableUrl(url: string | undefined | null): string | null {
  if (!url || typeof url !== 'string' || url.trim() === '') return null;
  if (url.startsWith('http://') || url.startsWith('https://')) return url;
  if (url.startsWith('/')) return url;
  return `https://storage.googleapis.com/waytofix/exercises/audio/${url.replace(/^\//, '')}`;
}

export function MessageAudioStatus({
  audioAr,
  audioEn,
  sourceMessageCode,
  compact = false,
  className = '',
}: MessageAudioStatusProps) {
  const arRef = useRef<HTMLAudioElement | null>(null);
  const enRef = useRef<HTMLAudioElement | null>(null);

  const hasAr = Boolean(audioAr?.trim());
  const hasEn = Boolean(audioEn?.trim());
  const hasAny = hasAr || hasEn;

  const play = useCallback((lang: 'ar' | 'en') => {
    const url = lang === 'ar' ? audioAr : audioEn;
    const resolved = resolvePlayableUrl(url || undefined);
    if (!resolved) return;
    const ref = lang === 'ar' ? arRef : enRef;
    if (!ref.current) {
      ref.current = new Audio(resolved);
    } else {
      ref.current.src = resolved;
    }
    void ref.current.play().catch(() => {});
  }, [audioAr, audioEn]);

  return (
    <div
      className={`flex flex-wrap items-center gap-1.5 ${compact ? 'text-[11px]' : 'text-xs'} ${className}`}
    >
      {sourceMessageCode && (
        <span
          className="font-mono text-[10px] px-1.5 py-0.5 rounded bg-slate-100 text-slate-700 max-w-[140px] truncate"
          title={sourceMessageCode}
        >
          {sourceMessageCode}
        </span>
      )}

      {hasAny ? (
        <>
          {hasAr && (
            <span className="inline-flex items-center gap-0.5 rounded-full border border-emerald-200 bg-emerald-50 px-1.5 py-0 text-[10px] font-medium text-emerald-800">
              <Volume2 className="h-3 w-3" />
              AR
              <button
                type="button"
                onClick={() => play('ar')}
                className="ml-0.5 inline-flex items-center rounded p-0.5 hover:bg-emerald-100"
                title="Play Arabic audio"
                aria-label="Play Arabic audio"
              >
                <Play className="h-3 w-3" />
              </button>
            </span>
          )}
          {hasEn && (
            <span className="inline-flex items-center gap-0.5 rounded-full border border-sky-200 bg-sky-50 px-1.5 py-0 text-[10px] font-medium text-sky-800">
              <Volume2 className="h-3 w-3" />
              EN
              <button
                type="button"
                onClick={() => play('en')}
                className="ml-0.5 inline-flex items-center rounded p-0.5 hover:bg-sky-100"
                title="Play English audio"
                aria-label="Play English audio"
              >
                <Play className="h-3 w-3" />
              </button>
            </span>
          )}
        </>
      ) : (
        <span className="inline-flex items-center gap-0.5 rounded-full border border-amber-200 bg-amber-50 px-1.5 py-0 text-[10px] font-medium text-amber-900">
          <VolumeX className="h-3 w-3" />
          No library audio
        </span>
      )}
    </div>
  );
}
