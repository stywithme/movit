'use client';

/**
 * Bulk TTS for message library — generates missing Arabic/English audio via Gemini (same pipeline as SmartLocalizedInput).
 */

import { useState } from 'react';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogBody,
  DialogFooter,
  Button,
  Input,
  Label,
} from '@/components/ui';

export interface BulkAudioResult {
  plannedGenerations: number;
  completedGenerations: number;
  skippedAlreadyPresent: number;
  failed: Array<{ messageId: string; code: string; language: 'ar' | 'en'; error: string }>;
  stoppedDueToLimit: boolean;
}

interface MessageBulkAudioModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** When set, only messages in this category are processed */
  categoryFilter: string;
  onCompleted: () => void;
}

export function MessageBulkAudioModal({
  open,
  onOpenChange,
  categoryFilter,
  onCompleted,
}: MessageBulkAudioModalProps) {
  const [maxGenerations, setMaxGenerations] = useState(50);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<BulkAudioResult | null>(null);

  const run = async () => {
    setRunning(true);
    setError(null);
    setResult(null);
    try {
      const res = await fetch('/api/messages/bulk-audio', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          includeInactive: true,
          ...(categoryFilter ? { category: categoryFilter } : {}),
          maxGenerations: Math.min(200, Math.max(1, maxGenerations)),
          languages: ['ar', 'en'],
        }),
      });
      const data = await res.json();
      if (data.success && data.data) {
        setResult(data.data as BulkAudioResult);
        onCompleted();
      } else {
        setError(data.error || 'Bulk audio generation failed');
      }
    } catch (e) {
      console.error(e);
      setError('Request failed');
    } finally {
      setRunning(false);
    }
  };

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (running) return;
        if (!next) {
          setResult(null);
          setError(null);
        }
        onOpenChange(next);
      }}
    >
      <DialogContent size="lg">
        <DialogHeader>
          <DialogTitle>Generate missing audio (AI)</DialogTitle>
          <DialogDescription>
            Uses the same Gemini TTS as the message editor. Processes messages in order, one clip at a time, with a short
            delay between calls to reduce rate limits. Run again if the batch stops due to the per-request limit.
          </DialogDescription>
        </DialogHeader>

        <DialogBody>
          <div className="space-y-4">
            {error && (
              <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">{error}</div>
            )}

            {!result && (
              <div className="space-y-2">
                <Label htmlFor="bulk-max-gen">Max audio files this run</Label>
                <Input
                  id="bulk-max-gen"
                  type="number"
                  min={1}
                  max={200}
                  value={maxGenerations}
                  onChange={(e) => setMaxGenerations(Number(e.target.value) || 50)}
                  disabled={running}
                />
                <p className="text-xs text-gray-500">
                  When a category is selected on the list page, only that category is processed. Inactive messages are
                  included.
                </p>
              </div>
            )}

            {result && (
              <div className="space-y-3 text-sm">
                <div className="grid grid-cols-2 gap-2">
                  <div className="p-2 bg-gray-50 rounded border border-gray-100">
                    <div className="text-xs text-gray-500">Planned (missing slots)</div>
                    <div className="font-semibold">{result.plannedGenerations}</div>
                  </div>
                  <div className="p-2 bg-gray-50 rounded border border-gray-100">
                    <div className="text-xs text-gray-500">Generated this run</div>
                    <div className="font-semibold text-green-700">{result.completedGenerations}</div>
                  </div>
                </div>
                {result.stoppedDueToLimit && (
                  <p className="text-amber-700 bg-amber-50 border border-amber-100 rounded p-2">
                    Stopped at the per-run limit. Run again to continue with remaining messages.
                  </p>
                )}
                {result.failed.length > 0 && (
                  <div>
                    <div className="font-medium text-red-700 mb-1">Failed ({result.failed.length})</div>
                    <div className="max-h-40 overflow-y-auto border border-gray-200 rounded p-2 text-xs font-mono space-y-1">
                      {result.failed.map((f, i) => (
                        <div key={`${f.messageId}-${f.language}-${i}`}>
                          {f.code} [{f.language}]: {f.error}
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
          <Button variant="ghost" onClick={() => onOpenChange(false)} disabled={running}>
            {result ? 'Close' : 'Cancel'}
          </Button>
          {!result && (
            <Button onClick={run} disabled={running}>
              {running ? 'Generating…' : 'Start'}
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
