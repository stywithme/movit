'use client';

import { useCallback, useEffect, useState } from 'react';
import { EMPTY_TTS_USER_DEFAULTS, type TtsUserDefaults } from '@/lib/types/tts';

const STORAGE_KEY = 'pose.tts.defaults.v1';
const TTS_DEFAULTS_UPDATED_EVENT = 'pose-tts-defaults-updated';

/** Read TTS defaults from localStorage (sync). Used when merging into bulk audio settings on open. */
export function readTtsDefaultsFromStorage(): TtsUserDefaults {
  if (typeof window === 'undefined') return { ...EMPTY_TTS_USER_DEFAULTS };
  try {
    return parseStored(window.localStorage.getItem(STORAGE_KEY));
  } catch {
    return { ...EMPTY_TTS_USER_DEFAULTS };
  }
}

function parseStored(raw: string | null): TtsUserDefaults {
  if (!raw) return { ...EMPTY_TTS_USER_DEFAULTS };
  try {
    const parsed = JSON.parse(raw) as Partial<TtsUserDefaults>;
    return { ...EMPTY_TTS_USER_DEFAULTS, ...parsed };
  } catch {
    return { ...EMPTY_TTS_USER_DEFAULTS };
  }
}

export function useTtsDefaults() {
  const [defaults, setDefaults] = useState<TtsUserDefaults>(EMPTY_TTS_USER_DEFAULTS);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    const syncFromStorage = () => {
      try {
        const stored = typeof window !== 'undefined' ? window.localStorage.getItem(STORAGE_KEY) : null;
        setDefaults(parseStored(stored));
      } catch {
        setDefaults({ ...EMPTY_TTS_USER_DEFAULTS });
      } finally {
        setReady(true);
      }
    };

    syncFromStorage();

    if (typeof window !== 'undefined') {
      window.addEventListener(TTS_DEFAULTS_UPDATED_EVENT, syncFromStorage);
      return () => window.removeEventListener(TTS_DEFAULTS_UPDATED_EVENT, syncFromStorage);
    }
    return undefined;
  }, []);

  const updateDefaults = useCallback((patch: Partial<TtsUserDefaults>) => {
    setDefaults((prev) => {
      const next = { ...prev, ...patch };
      try {
        window.localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
        window.dispatchEvent(new Event(TTS_DEFAULTS_UPDATED_EVENT));
      } catch (e) {
        console.error('Failed to save TTS defaults:', e);
      }
      return next;
    });
  }, []);

  const resetDefaults = useCallback(() => {
    const next = { ...EMPTY_TTS_USER_DEFAULTS };
    setDefaults(next);
    try {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
      window.dispatchEvent(new Event(TTS_DEFAULTS_UPDATED_EVENT));
    } catch (e) {
      console.error('Failed to reset TTS defaults:', e);
    }
  }, []);

  return { defaults, updateDefaults, resetDefaults, ready };
}
