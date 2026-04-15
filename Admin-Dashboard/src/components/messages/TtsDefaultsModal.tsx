'use client';

import { useEffect, useMemo, useState } from 'react';
import {
  Button,
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
  Textarea,
} from '@/components/ui';
import { useTtsConfig } from '@/hooks/useTtsConfig';
import { useTtsDefaults } from '@/hooks/useTtsDefaults';
import type { TtsUserDefaults } from '@/lib/types/tts';
import { EMPTY_TTS_USER_DEFAULTS } from '@/lib/types/tts';

interface TtsDefaultsModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function TtsDefaultsModal({ open, onOpenChange }: TtsDefaultsModalProps) {
  const { data: config, loading: configLoading, error: configError } = useTtsConfig();
  const { defaults, updateDefaults, resetDefaults, ready: defaultsReady } = useTtsDefaults();

  const [draft, setDraft] = useState<TtsUserDefaults>(EMPTY_TTS_USER_DEFAULTS);

  useEffect(() => {
    if (open && defaultsReady) {
      setDraft({ ...defaults });
    }
  }, [open, defaultsReady, defaults]);

  const modelOptions = useMemo(() => {
    return (config?.models ?? []).map((m) => ({ value: m.id, label: m.label }));
  }, [config?.models]);

  const voiceOptions = useMemo(() => {
    return (config?.voices ?? []).map((v) => ({
      value: v.name,
      label: `${v.name} — ${v.style}`,
    }));
  }, [config?.voices]);

  const langOptions = useMemo(() => {
    return (config?.languageCodes ?? []).map((l) => ({
      value: l.code,
      label: `${l.label} (${l.code})`,
    }));
  }, [config?.languageCodes]);

  const patch = (partial: Partial<TtsUserDefaults>) => setDraft((prev) => ({ ...prev, ...partial }));

  const handleSave = () => {
    updateDefaults(draft);
    onOpenChange(false);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent size="lg">
        <DialogHeader>
          <DialogTitle>Default TTS settings</DialogTitle>
          <DialogDescription>
            Applied when generating audio for single messages and as initial values for bulk audio. Model/voice
            catalog is served from the backend (Gemini TTS).
          </DialogDescription>
        </DialogHeader>

        <DialogBody>
          {configError && (
            <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">{configError}</div>
          )}

          <div className="space-y-4">
            <div>
              <Label>AI model</Label>
              <SearchableSelect
                options={modelOptions}
                value={draft.model}
                onChange={(v) => patch({ model: v })}
                placeholder="Server default model"
                searchPlaceholder="Search models…"
                disabled={configLoading || !!configError}
                allowEmpty
                emptyLabel="Server default model"
              />
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <Label>Arabic voice</Label>
                <SearchableSelect
                  options={voiceOptions}
                  value={draft.voiceNameAr}
                  onChange={(v) => patch({ voiceNameAr: v })}
                  placeholder={config?.defaults.voiceAr ? `Server default (${config.defaults.voiceAr})` : 'Server default'}
                  searchPlaceholder="Search voices…"
                  disabled={configLoading || !!configError}
                  allowEmpty
                  emptyLabel="Server default"
                />
              </div>
              <div>
                <Label>English voice</Label>
                <SearchableSelect
                  options={voiceOptions}
                  value={draft.voiceNameEn}
                  onChange={(v) => patch({ voiceNameEn: v })}
                  placeholder={config?.defaults.voiceEn ? `Server default (${config.defaults.voiceEn})` : 'Server default'}
                  searchPlaceholder="Search voices…"
                  disabled={configLoading || !!configError}
                  allowEmpty
                  emptyLabel="Server default"
                />
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <Label>Arabic language code</Label>
                <SearchableSelect
                  options={langOptions}
                  value={draft.languageCodeAr}
                  onChange={(v) => patch({ languageCodeAr: v })}
                  placeholder="Optional"
                  searchPlaceholder="Search codes…"
                  disabled={configLoading || !!configError}
                  allowEmpty
                  emptyLabel="None"
                />
              </div>
              <div>
                <Label>English language code</Label>
                <SearchableSelect
                  options={langOptions}
                  value={draft.languageCodeEn}
                  onChange={(v) => patch({ languageCodeEn: v })}
                  placeholder="Optional"
                  searchPlaceholder="Search codes…"
                  disabled={configLoading || !!configError}
                  allowEmpty
                  emptyLabel="None"
                />
              </div>
            </div>

            <div>
              <Label htmlFor="tts-def-shared">Shared style instructions</Label>
              <Textarea
                id="tts-def-shared"
                value={draft.sharedStylePrompt}
                onChange={(e) => patch({ sharedStylePrompt: e.target.value })}
                placeholder="Optional. Example: Speak clearly like a supportive coach."
                rows={3}
              />
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <Label htmlFor="tts-def-style-ar">Arabic style override</Label>
                <Textarea
                  id="tts-def-style-ar"
                  value={draft.stylePromptAr}
                  onChange={(e) => patch({ stylePromptAr: e.target.value })}
                  rows={3}
                />
              </div>
              <div>
                <Label htmlFor="tts-def-style-en">English style override</Label>
                <Textarea
                  id="tts-def-style-en"
                  value={draft.stylePromptEn}
                  onChange={(e) => patch({ stylePromptEn: e.target.value })}
                  rows={3}
                />
              </div>
            </div>

            <div>
              <Label htmlFor="tts-def-sys">System instruction</Label>
              <Textarea
                id="tts-def-sys"
                value={draft.systemInstruction}
                onChange={(e) => patch({ systemInstruction: e.target.value })}
                rows={2}
              />
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <Label htmlFor="tts-def-temp">Temperature</Label>
                <Input
                  id="tts-def-temp"
                  type="number"
                  step="0.1"
                  min={0}
                  max={2}
                  value={draft.temperature}
                  onChange={(e) => patch({ temperature: e.target.value })}
                  placeholder="Optional"
                />
              </div>
              <div>
                <Label htmlFor="tts-def-seed">Seed</Label>
                <Input
                  id="tts-def-seed"
                  type="number"
                  step={1}
                  value={draft.seed}
                  onChange={(e) => patch({ seed: e.target.value })}
                  placeholder="Optional"
                />
              </div>
            </div>
          </div>
        </DialogBody>

        <DialogFooter className="flex flex-wrap gap-2 justify-between">
          <Button
            type="button"
            variant="ghost"
            onClick={() => {
              resetDefaults();
              setDraft({ ...EMPTY_TTS_USER_DEFAULTS });
            }}
          >
            Reset to empty
          </Button>
          <div className="flex gap-2">
            <Button type="button" variant="ghost" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="button" onClick={handleSave} disabled={!defaultsReady}>
              Save defaults
            </Button>
          </div>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
