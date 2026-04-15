'use client';

import { useEffect, useMemo, useRef, useState } from 'react';
import {
  Button,
  Checkbox,
  Dialog,
  DialogBody,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  Input,
  Label,
  SearchableSelect,
  Select,
  Textarea,
} from '@/components/ui';
import { useTtsConfig } from '@/hooks/useTtsConfig';
import { readTtsDefaultsFromStorage } from '@/hooks/useTtsDefaults';
import type { TtsUserDefaults } from '@/lib/types/tts';

const BATCH_SIZE = 5;
const LOCAL_STORAGE_KEY = 'pose.messages.bulk-audio-settings.v1';

const MODE_OPTIONS = [
  { value: 'missing_only', label: 'Only missing audio' },
  { value: 'regenerate_selected', label: 'All selected messages (replace existing audio)' },
];

const LANGUAGE_TARGET_OPTIONS = [
  { value: 'both', label: 'Arabic + English' },
  { value: 'ar', label: 'Arabic only' },
  { value: 'en', label: 'English only' },
];

interface FailedItem {
  slotKey: string;
  messageId: string;
  code: string;
  language: 'ar' | 'en';
  error: string;
}

interface PreviewResult {
  matchedMessages: number;
  eligibleMessages: number;
  plannedGenerations: number;
  missingAudioSlots: number;
  existingAudioSlots: number;
  byLanguage: Record<'ar' | 'en', number>;
}

interface BatchResult extends PreviewResult {
  completedGenerations: number;
  skippedAlreadyPresent: number;
  failed: FailedItem[];
  stoppedDueToLimit: boolean;
  remainingGenerations: number;
  completedSlots: string[];
}

interface ProgressState {
  totalGenerated: number;
  totalFailed: number;
  batchesDone: number;
  failed: FailedItem[];
  remaining: boolean;
  done: boolean;
  plannedTotal: number;
}

interface MessageBulkAudioCurrentFilters {
  category: string;
  status: string;
  search: string;
  audioMissing: string;
}

interface BulkAudioSettings {
  useCurrentFilters: boolean;
  mode: 'missing_only' | 'regenerate_selected';
  languageTarget: 'both' | 'ar' | 'en';
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
  delayMsBetweenCalls: string;
  maxGenerations: string;
}

interface MessageBulkAudioModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  currentFilters: MessageBulkAudioCurrentFilters;
  onCompleted: () => void;
}

const DEFAULT_SETTINGS: BulkAudioSettings = {
  useCurrentFilters: true,
  mode: 'missing_only',
  languageTarget: 'both',
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
  delayMsBetweenCalls: '350',
  maxGenerations: '50',
};

function toLanguages(target: BulkAudioSettings['languageTarget']): ('ar' | 'en')[] {
  if (target === 'ar') return ['ar'];
  if (target === 'en') return ['en'];
  return ['ar', 'en'];
}

function parsePositiveInt(value: string, fallback: number, min: number, max: number): number {
  const parsed = Number.parseInt(value, 10);
  if (!Number.isFinite(parsed)) return fallback;
  return Math.min(max, Math.max(min, parsed));
}

function parseOptionalInt(value: string): number | undefined {
  const trimmed = value.trim();
  if (!trimmed) return undefined;
  const parsed = Number.parseInt(trimmed, 10);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function parseOptionalFloat(value: string): number | undefined {
  const trimmed = value.trim();
  if (!trimmed) return undefined;
  const parsed = Number.parseFloat(trimmed);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function buildFilterSummary(filters: MessageBulkAudioCurrentFilters): string {
  const parts: string[] = [];
  if (filters.category) parts.push(`category: ${filters.category}`);
  if (filters.status) parts.push(`status: ${filters.status}`);
  if (filters.audioMissing) parts.push(`audio: ${filters.audioMissing}`);
  if (filters.search.trim()) parts.push(`search: "${filters.search.trim()}"`);
  return parts.length > 0 ? parts.join(' | ') : 'No page filters are currently active.';
}

function mergeStoredSettings(value: unknown): BulkAudioSettings {
  if (!value || typeof value !== 'object') return DEFAULT_SETTINGS;
  const raw = value as Partial<BulkAudioSettings>;
  return {
    ...DEFAULT_SETTINGS,
    ...raw,
  };
}

/** Fill empty bulk fields from gear-icon TTS defaults (localStorage). */
function mergeBulkFillEmptyFromTtsDefaults(bulk: BulkAudioSettings, tts: TtsUserDefaults): BulkAudioSettings {
  const empty = (s: string) => !s.trim();
  return {
    ...bulk,
    model: empty(bulk.model) && tts.model.trim() ? tts.model : bulk.model,
    voiceNameAr: empty(bulk.voiceNameAr) && tts.voiceNameAr.trim() ? tts.voiceNameAr : bulk.voiceNameAr,
    voiceNameEn: empty(bulk.voiceNameEn) && tts.voiceNameEn.trim() ? tts.voiceNameEn : bulk.voiceNameEn,
    languageCodeAr: empty(bulk.languageCodeAr) && tts.languageCodeAr.trim() ? tts.languageCodeAr : bulk.languageCodeAr,
    languageCodeEn: empty(bulk.languageCodeEn) && tts.languageCodeEn.trim() ? tts.languageCodeEn : bulk.languageCodeEn,
    sharedStylePrompt:
      empty(bulk.sharedStylePrompt) && tts.sharedStylePrompt.trim() ? tts.sharedStylePrompt : bulk.sharedStylePrompt,
    stylePromptAr: empty(bulk.stylePromptAr) && tts.stylePromptAr.trim() ? tts.stylePromptAr : bulk.stylePromptAr,
    stylePromptEn: empty(bulk.stylePromptEn) && tts.stylePromptEn.trim() ? tts.stylePromptEn : bulk.stylePromptEn,
    systemInstruction:
      empty(bulk.systemInstruction) && tts.systemInstruction.trim() ? tts.systemInstruction : bulk.systemInstruction,
    temperature: empty(bulk.temperature) && tts.temperature.trim() ? tts.temperature : bulk.temperature,
    seed: empty(bulk.seed) && tts.seed.trim() ? tts.seed : bulk.seed,
  };
}

export function MessageBulkAudioModal({
  open,
  onOpenChange,
  currentFilters,
  onCompleted,
}: MessageBulkAudioModalProps) {
  const [settings, setSettings] = useState<BulkAudioSettings>(DEFAULT_SETTINGS);
  const [settingsReady, setSettingsReady] = useState(false);
  const [showAdvanced, setShowAdvanced] = useState(false);
  const [preview, setPreview] = useState<PreviewResult | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewError, setPreviewError] = useState<string | null>(null);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [progress, setProgress] = useState<ProgressState | null>(null);
  const cancelledRef = useRef(false);
  const completedSlotsRef = useRef<string[]>([]);
  const previewRequestRef = useRef(0);

  const filterSummary = useMemo(() => buildFilterSummary(currentFilters), [currentFilters]);

  const { data: ttsConfig, loading: ttsConfigLoading, error: ttsConfigError } = useTtsConfig();

  const modelSearchOptions = useMemo(() => {
    return (ttsConfig?.models ?? []).map((m) => ({ value: m.id, label: m.label }));
  }, [ttsConfig?.models]);

  const voiceSearchOptions = useMemo(() => {
    return (ttsConfig?.voices ?? []).map((v) => ({
      value: v.name,
      label: `${v.name} — ${v.style}`,
    }));
  }, [ttsConfig?.voices]);

  const languageSearchOptions = useMemo(() => {
    return (ttsConfig?.languageCodes ?? []).map((l) => ({
      value: l.code,
      label: `${l.label} (${l.code})`,
    }));
  }, [ttsConfig?.languageCodes]);

  useEffect(() => {
    try {
      const stored = window.localStorage.getItem(LOCAL_STORAGE_KEY);
      if (stored) {
        setSettings(mergeStoredSettings(JSON.parse(stored)));
      }
    } catch (err) {
      console.error('Failed to load bulk audio settings:', err);
    } finally {
      setSettingsReady(true);
    }
  }, []);

  useEffect(() => {
    if (!open || !settingsReady) return;
    const tts = readTtsDefaultsFromStorage();
    setSettings((prev) => mergeBulkFillEmptyFromTtsDefaults(prev, tts));
  }, [open, settingsReady]);

  useEffect(() => {
    if (!settingsReady) return;
    try {
      window.localStorage.setItem(LOCAL_STORAGE_KEY, JSON.stringify(settings));
    } catch (err) {
      console.error('Failed to save bulk audio settings:', err);
    }
  }, [settings, settingsReady]);

  const updateSettings = (patch: Partial<BulkAudioSettings>) => {
    setSettings((prev) => ({ ...prev, ...patch }));
  };

  const buildRequestPayload = (includeBatchFields: boolean) => {
    const payload: Record<string, unknown> = {
      includeInactive: true,
      languages: toLanguages(settings.languageTarget),
      mode: settings.mode,
    };

    if (settings.useCurrentFilters) {
      if (currentFilters.category) payload.category = currentFilters.category;
      if (currentFilters.status === 'active' || currentFilters.status === 'inactive') {
        payload.status = currentFilters.status;
      }
      if (currentFilters.search.trim()) payload.search = currentFilters.search.trim();
      if (currentFilters.audioMissing) payload.audioMissing = currentFilters.audioMissing;
    }

    if (settings.model.trim()) payload.model = settings.model.trim();
    if (settings.voiceNameAr.trim()) payload.voiceNameAr = settings.voiceNameAr.trim();
    if (settings.voiceNameEn.trim()) payload.voiceNameEn = settings.voiceNameEn.trim();
    if (settings.languageCodeAr.trim()) payload.languageCodeAr = settings.languageCodeAr.trim();
    if (settings.languageCodeEn.trim()) payload.languageCodeEn = settings.languageCodeEn.trim();
    if (settings.sharedStylePrompt.trim()) payload.sharedStylePrompt = settings.sharedStylePrompt.trim();
    if (settings.stylePromptAr.trim()) payload.stylePromptAr = settings.stylePromptAr.trim();
    if (settings.stylePromptEn.trim()) payload.stylePromptEn = settings.stylePromptEn.trim();
    if (settings.systemInstruction.trim()) payload.systemInstruction = settings.systemInstruction.trim();

    const temperature = parseOptionalFloat(settings.temperature);
    if (temperature !== undefined) payload.temperature = temperature;

    const seed = parseOptionalInt(settings.seed);
    if (seed !== undefined) payload.seed = seed;

    if (includeBatchFields) {
      payload.maxGenerations = parsePositiveInt(settings.maxGenerations, 50, 1, 200);
      payload.delayMsBetweenCalls = parsePositiveInt(settings.delayMsBetweenCalls, 350, 0, 5000);
    }

    if (completedSlotsRef.current.length > 0) {
      payload.excludeSlots = completedSlotsRef.current;
    }

    return payload;
  };

  useEffect(() => {
    if (!settingsReady || running) return;
    if (completedSlotsRef.current.length === 0) return;
    completedSlotsRef.current = [];
    setProgress(null);
    setError(null);
  }, [
    settingsReady,
    running,
    settings,
    currentFilters.category,
    currentFilters.status,
    currentFilters.search,
    currentFilters.audioMissing,
  ]);

  useEffect(() => {
    if (!open || !settingsReady || running) return;

    const timeout = window.setTimeout(async () => {
      const requestId = previewRequestRef.current + 1;
      previewRequestRef.current = requestId;
      setPreviewLoading(true);
      setPreviewError(null);

      try {
        const res = await fetch('/api/messages/bulk-audio/preview', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(buildRequestPayload(false)),
        });
        const data = await res.json();

        if (previewRequestRef.current !== requestId) return;

        if (!res.ok || !data.success || !data.data) {
          setPreview(null);
          setPreviewError(data.error || `Preview failed (${res.status})`);
          return;
        }

        setPreview(data.data as PreviewResult);
      } catch (err) {
        if (previewRequestRef.current !== requestId) return;
        console.error(err);
        setPreview(null);
        setPreviewError('Preview request failed');
      } finally {
        if (previewRequestRef.current === requestId) {
          setPreviewLoading(false);
        }
      }
    }, 300);

    return () => window.clearTimeout(timeout);
  }, [
    open,
    settingsReady,
    settings,
    currentFilters.category,
    currentFilters.status,
    currentFilters.search,
    currentFilters.audioMissing,
    running,
  ]);

  const resetTransientState = () => {
    setPreviewError(null);
    setError(null);
    setProgress(null);
    completedSlotsRef.current = [];
    cancelledRef.current = false;
  };

  const run = async () => {
    const continuingPreviousRun = !!progress?.done && completedSlotsRef.current.length > 0;
    cancelledRef.current = false;
    if (!continuingPreviousRun) {
      completedSlotsRef.current = [];
    }
    setRunning(true);
    setError(null);
    setProgress(null);

    const requestedTotal = parsePositiveInt(settings.maxGenerations, 50, 1, 200);
    const previewTotal = preview?.plannedGenerations ?? requestedTotal;
    const plannedTotal = Math.min(requestedTotal, previewTotal);
    let generated = 0;
    let batchesDone = 0;
    let remaining = plannedTotal > 0;
    const allFailed: FailedItem[] = [];

    try {
      while (generated < requestedTotal && remaining && !cancelledRef.current) {
        const batchMax = Math.min(BATCH_SIZE, requestedTotal - generated);
        const payload = {
          ...buildRequestPayload(true),
          maxGenerations: batchMax,
        };

        const res = await fetch('/api/messages/bulk-audio', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload),
        });

        const data = await res.json();
        if (!res.ok || !data.success || !data.data) {
          setError(data.error || `Batch failed (${res.status})`);
          break;
        }

        const batch = data.data as BatchResult;
        generated += batch.completedGenerations;
        completedSlotsRef.current = [...completedSlotsRef.current, ...batch.completedSlots];
        allFailed.push(...batch.failed);
        batchesDone++;

        remaining = batch.remainingGenerations > 0 && batch.completedGenerations > 0;
        setProgress({
          totalGenerated: generated,
          totalFailed: allFailed.length,
          batchesDone,
          failed: allFailed,
          remaining,
          done: false,
          plannedTotal: Math.max(plannedTotal, generated),
        });

        if (batch.completedGenerations === 0) {
          break;
        }
      }

      setProgress((prev) =>
        prev
          ? {
              ...prev,
              done: true,
              remaining: prev.remaining,
            }
          : {
              totalGenerated: generated,
              totalFailed: allFailed.length,
              batchesDone,
              failed: allFailed,
              remaining: false,
              done: true,
              plannedTotal: Math.max(plannedTotal, generated),
            }
      );
      onCompleted();
    } catch (err) {
      console.error(err);
      if (generated > 0) {
        setProgress((prev) =>
          prev
            ? { ...prev, done: true }
            : {
                totalGenerated: generated,
                totalFailed: allFailed.length,
                batchesDone,
                failed: allFailed,
                remaining: true,
                done: true,
                plannedTotal: Math.max(plannedTotal, generated),
              }
        );
        setError(`Connection interrupted after generating ${generated} files. You can run again to continue.`);
        onCompleted();
      } else {
        setError('Request failed. Check that the backend is reachable and your TTS settings are valid.');
      }
    } finally {
      setRunning(false);
    }
  };

  const handleStop = () => {
    cancelledRef.current = true;
  };

  const canStart = !running && !previewLoading && !!preview && preview.plannedGenerations > 0;
  const isDone = !!progress?.done;
  const canRunAgain = isDone && (progress?.remaining || progress?.totalFailed);

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (running) return;
        if (!next) resetTransientState();
        onOpenChange(next);
      }}
    >
      <DialogContent size="lg">
        <DialogHeader>
          <DialogTitle>Bulk audio generation (AI)</DialogTitle>
          <DialogDescription>
            Configure Gemini TTS, preview what will be affected, then run generation in small batches of {BATCH_SIZE}{' '}
            files to avoid long request timeouts.
          </DialogDescription>
        </DialogHeader>

        <DialogBody>
          <div className="space-y-5">
            {error && (
              <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">{error}</div>
            )}

            {ttsConfigError && (
              <div className="p-3 bg-amber-50 border border-amber-200 rounded-lg text-sm text-amber-800">
                TTS catalog: {ttsConfigError}. Voice/model lists may be empty until the backend is reachable.
              </div>
            )}

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <Label>Generation mode</Label>
                <Select
                  value={settings.mode}
                  onChange={(e) => updateSettings({ mode: e.target.value as BulkAudioSettings['mode'] })}
                  options={MODE_OPTIONS}
                  disabled={running}
                />
              </div>

              <div>
                <Label>Languages</Label>
                <Select
                  value={settings.languageTarget}
                  onChange={(e) =>
                    updateSettings({ languageTarget: e.target.value as BulkAudioSettings['languageTarget'] })
                  }
                  options={LANGUAGE_TARGET_OPTIONS}
                  disabled={running}
                />
              </div>
            </div>

            <div className="rounded-lg border border-gray-200 bg-gray-50 p-4 space-y-3">
              <label className="flex items-center gap-3 text-sm text-gray-700">
                <Checkbox
                  checked={settings.useCurrentFilters}
                  onCheckedChange={(checked) => updateSettings({ useCurrentFilters: !!checked })}
                  disabled={running}
                />
                Use current page filters
              </label>
              <p className="text-xs text-gray-500">{filterSummary}</p>
              {settings.mode === 'regenerate_selected' && (
                <p className="text-xs text-amber-700">
                  Existing audio URLs for the selected languages will be replaced after successful regeneration.
                </p>
              )}
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <Label>AI model</Label>
                <SearchableSelect
                  options={modelSearchOptions}
                  value={settings.model}
                  onChange={(v) => updateSettings({ model: v })}
                  placeholder="Server default model"
                  searchPlaceholder="Search models…"
                  disabled={running || ttsConfigLoading}
                  allowEmpty
                  emptyLabel="Server default model"
                />
              </div>

              <div>
                <Label htmlFor="bulk-max-gen">Max audio files this run</Label>
                <Input
                  id="bulk-max-gen"
                  type="number"
                  min={1}
                  max={200}
                  value={settings.maxGenerations}
                  onChange={(e) => updateSettings({ maxGenerations: e.target.value })}
                  disabled={running}
                  helperText={`The UI sends batches of ${BATCH_SIZE}; this is the total cap for the full run.`}
                />
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <Label>Arabic voice</Label>
                <SearchableSelect
                  options={voiceSearchOptions}
                  value={settings.voiceNameAr}
                  onChange={(v) => updateSettings({ voiceNameAr: v })}
                  placeholder={
                    ttsConfig?.defaults.voiceAr
                      ? `Server default (${ttsConfig.defaults.voiceAr})`
                      : 'Server default'
                  }
                  searchPlaceholder="Search voices…"
                  disabled={running || ttsConfigLoading}
                  allowEmpty
                  emptyLabel="Server default"
                />
              </div>
              <div>
                <Label>English voice</Label>
                <SearchableSelect
                  options={voiceSearchOptions}
                  value={settings.voiceNameEn}
                  onChange={(v) => updateSettings({ voiceNameEn: v })}
                  placeholder={
                    ttsConfig?.defaults.voiceEn
                      ? `Server default (${ttsConfig.defaults.voiceEn})`
                      : 'Server default'
                  }
                  searchPlaceholder="Search voices…"
                  disabled={running || ttsConfigLoading}
                  allowEmpty
                  emptyLabel="Server default"
                />
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <Label>Arabic language code</Label>
                <SearchableSelect
                  options={languageSearchOptions}
                  value={settings.languageCodeAr}
                  onChange={(v) => updateSettings({ languageCodeAr: v })}
                  placeholder="Optional"
                  searchPlaceholder="Search codes…"
                  disabled={running || ttsConfigLoading}
                  allowEmpty
                  emptyLabel="None"
                />
              </div>
              <div>
                <Label>English language code</Label>
                <SearchableSelect
                  options={languageSearchOptions}
                  value={settings.languageCodeEn}
                  onChange={(v) => updateSettings({ languageCodeEn: v })}
                  placeholder="Optional"
                  searchPlaceholder="Search codes…"
                  disabled={running || ttsConfigLoading}
                  allowEmpty
                  emptyLabel="None"
                />
              </div>
            </div>

            <div>
              <Label htmlFor="bulk-shared-style">Shared style instructions</Label>
              <Textarea
                id="bulk-shared-style"
                value={settings.sharedStylePrompt}
                onChange={(e) => updateSettings({ sharedStylePrompt: e.target.value })}
                placeholder="Optional. Example: Speak slowly, warmly, and clearly like a supportive rehab coach."
                rows={3}
                disabled={running}
              />
            </div>

            <div className="border-t border-gray-200 pt-4">
              <button
                type="button"
                onClick={() => setShowAdvanced((prev) => !prev)}
                className="text-sm font-medium text-blue-600 hover:text-blue-700"
                disabled={running}
              >
                {showAdvanced ? 'Hide advanced settings' : 'Show advanced settings'}
              </button>
            </div>

            {showAdvanced && (
              <div className="space-y-4 rounded-lg border border-gray-200 p-4">
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div>
                    <Label htmlFor="bulk-style-ar">Arabic style override</Label>
                    <Textarea
                      id="bulk-style-ar"
                      value={settings.stylePromptAr}
                      onChange={(e) => updateSettings({ stylePromptAr: e.target.value })}
                      placeholder="Optional. Example: Use gentle pacing with precise Arabic pronunciation."
                      rows={3}
                      disabled={running}
                    />
                  </div>
                  <div>
                    <Label htmlFor="bulk-style-en">English style override</Label>
                    <Textarea
                      id="bulk-style-en"
                      value={settings.stylePromptEn}
                      onChange={(e) => updateSettings({ stylePromptEn: e.target.value })}
                      placeholder="Optional. Example: Sound upbeat, concise, and confident."
                      rows={3}
                      disabled={running}
                    />
                  </div>
                </div>

                <div>
                  <Label htmlFor="bulk-system-instruction">System instruction</Label>
                  <Textarea
                    id="bulk-system-instruction"
                    value={settings.systemInstruction}
                    onChange={(e) => updateSettings({ systemInstruction: e.target.value })}
                    placeholder="Advanced. Use only if you need tighter model steering."
                    rows={3}
                    disabled={running}
                  />
                </div>

                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                  <div>
                    <Label htmlFor="bulk-temperature">Temperature</Label>
                    <Input
                      id="bulk-temperature"
                      type="number"
                      step="0.1"
                      min={0}
                      max={2}
                      value={settings.temperature}
                      onChange={(e) => updateSettings({ temperature: e.target.value })}
                      placeholder="Optional"
                      disabled={running}
                    />
                  </div>
                  <div>
                    <Label htmlFor="bulk-seed">Seed</Label>
                    <Input
                      id="bulk-seed"
                      type="number"
                      step="1"
                      value={settings.seed}
                      onChange={(e) => updateSettings({ seed: e.target.value })}
                      placeholder="Optional"
                      disabled={running}
                    />
                  </div>
                  <div>
                    <Label htmlFor="bulk-delay">Delay between calls (ms)</Label>
                    <Input
                      id="bulk-delay"
                      type="number"
                      min={0}
                      max={5000}
                      value={settings.delayMsBetweenCalls}
                      onChange={(e) => updateSettings({ delayMsBetweenCalls: e.target.value })}
                      disabled={running}
                    />
                  </div>
                </div>
              </div>
            )}

            <div className="rounded-lg border border-gray-200 p-4 space-y-3">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-semibold text-gray-900">Preview</h3>
                {previewLoading && <span className="text-xs text-gray-500">Calculating...</span>}
              </div>

              {previewError && (
                <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">{previewError}</div>
              )}

              {preview && !previewError && (
                <div className="space-y-3">
                  <div className="grid grid-cols-2 md:grid-cols-3 gap-3 text-sm">
                    <div className="rounded border border-gray-200 bg-gray-50 p-3">
                      <div className="text-xs text-gray-500">Matched messages</div>
                      <div className="font-semibold">{preview.matchedMessages}</div>
                    </div>
                    <div className="rounded border border-gray-200 bg-gray-50 p-3">
                      <div className="text-xs text-gray-500">Eligible messages</div>
                      <div className="font-semibold">{preview.eligibleMessages}</div>
                    </div>
                    <div className="rounded border border-gray-200 bg-gray-50 p-3">
                      <div className="text-xs text-gray-500">Audio files to generate</div>
                      <div className="font-semibold">{preview.plannedGenerations}</div>
                    </div>
                    <div className="rounded border border-gray-200 bg-gray-50 p-3">
                      <div className="text-xs text-gray-500">Arabic slots</div>
                      <div className="font-semibold">{preview.byLanguage.ar}</div>
                    </div>
                    <div className="rounded border border-gray-200 bg-gray-50 p-3">
                      <div className="text-xs text-gray-500">English slots</div>
                      <div className="font-semibold">{preview.byLanguage.en}</div>
                    </div>
                    <div className="rounded border border-gray-200 bg-gray-50 p-3">
                      <div className="text-xs text-gray-500">
                        {settings.mode === 'regenerate_selected' ? 'Will replace existing audio' : 'Missing audio slots'}
                      </div>
                      <div className="font-semibold">
                        {settings.mode === 'regenerate_selected' ? preview.existingAudioSlots : preview.missingAudioSlots}
                      </div>
                    </div>
                  </div>

                  {preview.plannedGenerations === 0 && (
                    <p className="text-sm text-amber-700 bg-amber-50 border border-amber-100 rounded p-3">
                      No eligible audio slots match the current settings.
                    </p>
                  )}
                </div>
              )}
            </div>

            {progress && (
              <div className="rounded-lg border border-gray-200 p-4 space-y-3 text-sm">
                <div>
                  <div className="flex justify-between text-xs text-gray-600 mb-1">
                    <span>
                      Generated: {progress.totalGenerated}
                      {progress.totalFailed > 0 && (
                        <span className="ml-2 text-red-600">Failed: {progress.totalFailed}</span>
                      )}
                    </span>
                    <span>{running ? `Batch ${progress.batchesDone + 1}...` : progress.done ? 'Done' : 'Paused'}</span>
                  </div>
                  <div className="w-full bg-gray-200 rounded-full h-2.5 overflow-hidden">
                    <div
                      className={`h-full rounded-full transition-all duration-500 ${
                        progress.done ? 'bg-green-500' : 'bg-blue-500'
                      }`}
                      style={{
                        width: `${Math.min(100, (progress.totalGenerated / Math.max(progress.plannedTotal, 1)) * 100)}%`,
                      }}
                    />
                  </div>
                </div>

                {progress.done && (
                  <div className="grid grid-cols-2 gap-3">
                    <div className="rounded border border-gray-200 bg-gray-50 p-3">
                      <div className="text-xs text-gray-500">Generated this run</div>
                      <div className="font-semibold text-green-700">{progress.totalGenerated}</div>
                    </div>
                    <div className="rounded border border-gray-200 bg-gray-50 p-3">
                      <div className="text-xs text-gray-500">Batches</div>
                      <div className="font-semibold">{progress.batchesDone}</div>
                    </div>
                  </div>
                )}

                {progress.done && progress.remaining && (
                  <p className="text-amber-700 bg-amber-50 border border-amber-100 rounded p-3">
                    More eligible slots remain. You can continue with another run.
                  </p>
                )}

                {progress.failed.length > 0 && (
                  <div>
                    <div className="font-medium text-red-700 mb-1">Failed ({progress.failed.length})</div>
                    <div className="max-h-40 overflow-y-auto border border-gray-200 rounded p-2 text-xs font-mono space-y-1">
                      {progress.failed.map((item, index) => (
                        <div key={`${item.slotKey}-${index}`}>
                          {item.code} [{item.language}]: {item.error}
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
        </DialogBody>

        <DialogFooter>
          {running ? (
            <Button variant="ghost" onClick={handleStop}>
              Stop after current batch
            </Button>
          ) : (
            <>
              <Button
                variant="ghost"
                onClick={() => {
                  resetTransientState();
                  onOpenChange(false);
                }}
              >
                {isDone ? 'Close' : 'Cancel'}
              </Button>
              {canRunAgain ? (
                <Button onClick={run}>Run again</Button>
              ) : (
                <Button onClick={run} disabled={!canStart}>
                  Start
                </Button>
              )}
            </>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
