'use client';

/**
 * Bulk TTS — sends small batches (BATCH_SIZE per request) to avoid proxy/browser timeouts,
 * then aggregates results and shows live progress.
 */

import { useRef, useState } from 'react';
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

const BATCH_SIZE = 5;

interface FailedItem {
  messageId: string;
  code: string;
  language: 'ar' | 'en';
  error: string;
}

interface BatchResult {
  plannedGenerations: number;
  completedGenerations: number;
  skippedAlreadyPresent: number;
  failed: FailedItem[];
  stoppedDueToLimit: boolean;
}

interface ProgressState {
  totalGenerated: number;
  totalFailed: number;
  batchesDone: number;
  currentBatchPlanned: number;
  failed: FailedItem[];
  done: boolean;
  remaining: boolean;
}

interface MessageBulkAudioModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
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
  const [progress, setProgress] = useState<ProgressState | null>(null);
  const cancelledRef = useRef(false);

  const reset = () => {
    setProgress(null);
    setError(null);
  };

  const run = async () => {
    cancelledRef.current = false;
    setRunning(true);
    setError(null);
    setProgress(null);

    const total = Math.min(200, Math.max(1, maxGenerations));
    let generated = 0;
    let batchesDone = 0;
    const allFailed: FailedItem[] = [];
    let remaining = true;

    try {
      while (generated < total && remaining && !cancelledRef.current) {
        const batchMax = Math.min(BATCH_SIZE, total - generated);

        const res = await fetch('/api/messages/bulk-audio', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            includeInactive: true,
            ...(categoryFilter ? { category: categoryFilter } : {}),
            maxGenerations: batchMax,
            languages: ['ar', 'en'],
          }),
        });

        if (!res.ok) {
          setError(`Server responded ${res.status}`);
          break;
        }

        const data = await res.json();
        if (!data.success || !data.data) {
          setError(data.error || 'Batch failed');
          break;
        }

        const batch = data.data as BatchResult;
        generated += batch.completedGenerations;
        allFailed.push(...batch.failed);
        batchesDone++;

        remaining = batch.plannedGenerations > batch.completedGenerations + batch.failed.length;
        if (batch.completedGenerations === 0 && batch.failed.length === 0) {
          remaining = false;
        }

        setProgress({
          totalGenerated: generated,
          totalFailed: allFailed.length,
          batchesDone,
          currentBatchPlanned: batch.plannedGenerations,
          failed: allFailed,
          done: false,
          remaining,
        });
      }

      setProgress((prev) =>
        prev ? { ...prev, done: true, remaining } : null
      );
      onCompleted();
    } catch (e) {
      console.error(e);
      if (generated > 0) {
        setProgress((prev) =>
          prev ? { ...prev, done: true } : null
        );
        setError(`Connection interrupted after generating ${generated} files. You can run again to continue.`);
        onCompleted();
      } else {
        setError('Request failed — check that the backend is running.');
      }
    } finally {
      setRunning(false);
    }
  };

  const handleCancel = () => {
    cancelledRef.current = true;
  };

  const isIdle = !running && !progress;
  const isDone = progress?.done;

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (running) return;
        if (!next) reset();
        onOpenChange(next);
      }}
    >
      <DialogContent size="lg">
        <DialogHeader>
          <DialogTitle>Generate missing audio (AI)</DialogTitle>
          <DialogDescription>
            Sends small batches to Gemini TTS so each request finishes quickly. Progress updates after every{' '}
            {BATCH_SIZE} files.
          </DialogDescription>
        </DialogHeader>

        <DialogBody>
          <div className="space-y-4">
            {error && (
              <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">{error}</div>
            )}

            {isIdle && (
              <div className="space-y-2">
                <Label htmlFor="bulk-max-gen">Max audio files this run</Label>
                <Input
                  id="bulk-max-gen"
                  type="number"
                  min={1}
                  max={200}
                  value={maxGenerations}
                  onChange={(e) => setMaxGenerations(Number(e.target.value) || 50)}
                />
                <p className="text-xs text-gray-500">
                  {categoryFilter
                    ? `Only "${categoryFilter}" messages will be processed.`
                    : 'All categories will be processed.'}{' '}
                  Inactive messages are included.
                </p>
              </div>
            )}

            {progress && (
              <div className="space-y-3 text-sm">
                {/* Progress bar */}
                <div>
                  <div className="flex justify-between text-xs text-gray-600 mb-1">
                    <span>
                      Generated: {progress.totalGenerated}
                      {progress.totalFailed > 0 && (
                        <span className="text-red-600 ml-2">Failed: {progress.totalFailed}</span>
                      )}
                    </span>
                    <span>
                      {running ? `Batch ${progress.batchesDone + 1}…` : isDone ? 'Done' : 'Paused'}
                    </span>
                  </div>
                  <div className="w-full bg-gray-200 rounded-full h-2.5 overflow-hidden">
                    <div
                      className={`h-full rounded-full transition-all duration-500 ${isDone ? 'bg-green-500' : 'bg-blue-500'}`}
                      style={{
                        width: `${Math.min(100, (progress.totalGenerated / maxGenerations) * 100)}%`,
                      }}
                    />
                  </div>
                </div>

                {/* Summary when done */}
                {isDone && (
                  <>
                    <div className="grid grid-cols-2 gap-2">
                      <div className="p-2 bg-gray-50 rounded border border-gray-100">
                        <div className="text-xs text-gray-500">Total generated</div>
                        <div className="font-semibold text-green-700">{progress.totalGenerated}</div>
                      </div>
                      <div className="p-2 bg-gray-50 rounded border border-gray-100">
                        <div className="text-xs text-gray-500">Batches</div>
                        <div className="font-semibold">{progress.batchesDone}</div>
                      </div>
                    </div>
                    {progress.remaining && (
                      <p className="text-amber-700 bg-amber-50 border border-amber-100 rounded p-2">
                        More messages still need audio. Run again to continue.
                      </p>
                    )}
                    {!progress.remaining && progress.totalGenerated > 0 && (
                      <p className="text-green-700 bg-green-50 border border-green-100 rounded p-2">
                        All messages with text now have audio.
                      </p>
                    )}
                  </>
                )}

                {/* Failed list */}
                {progress.failed.length > 0 && (
                  <div>
                    <div className="font-medium text-red-700 mb-1">Failed ({progress.failed.length})</div>
                    <div className="max-h-40 overflow-y-auto border border-gray-200 rounded p-2 text-xs font-mono space-y-1">
                      {progress.failed.map((f, i) => (
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
          {running ? (
            <Button variant="ghost" onClick={handleCancel}>
              Stop after current batch
            </Button>
          ) : (
            <>
              <Button variant="ghost" onClick={() => onOpenChange(false)}>
                {isDone ? 'Close' : 'Cancel'}
              </Button>
              {!isDone && (
                <Button onClick={run}>Start</Button>
              )}
            </>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
