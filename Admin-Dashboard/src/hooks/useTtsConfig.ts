'use client';

import { useCallback, useEffect, useState } from 'react';
import type { TtsConfigData } from '@/lib/types/tts';

interface UseTtsConfigResult {
  data: TtsConfigData | null;
  loading: boolean;
  error: string | null;
  refetch: () => Promise<void>;
}

let cached: TtsConfigData | null = null;
let inflight: Promise<TtsConfigData | null> | null = null;

async function fetchTtsConfig(): Promise<TtsConfigData | null> {
  if (cached) return cached;
  if (inflight) return inflight;

  inflight = (async () => {
    const res = await fetch('/api/ai/tts/config');
    const json = await res.json().catch(() => ({}));
    if (!res.ok || !json.success || !json.data) {
      throw new Error(json.error || `TTS config failed (${res.status})`);
    }
    cached = json.data as TtsConfigData;
    return cached;
  })();

  try {
    return await inflight;
  } finally {
    inflight = null;
  }
}

/**
 * Loads Gemini TTS catalog once per session (voices, models, language codes, server defaults).
 */
export function useTtsConfig(): UseTtsConfigResult {
  const [data, setData] = useState<TtsConfigData | null>(cached);
  const [loading, setLoading] = useState(!cached);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await fetchTtsConfig();
      setData(result);
    } catch (e) {
      const msg = e instanceof Error ? e.message : 'Failed to load TTS config';
      setError(msg);
      setData(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (cached) {
      setData(cached);
      setLoading(false);
      return;
    }
    void load();
  }, [load]);

  const refetch = useCallback(async () => {
    cached = null;
    await load();
  }, [load]);

  return { data, loading, error, refetch };
}
