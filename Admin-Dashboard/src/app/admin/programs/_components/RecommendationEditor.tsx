'use client';

import { Input, Label, Textarea } from '@/components/ui';

type RecommendationRecord = Record<string, unknown>;

interface RecommendationEditorProps {
  title: string;
  description?: string;
  value: string;
  onChange: (value: string) => void;
  mode: 'entry' | 'exit';
}

function safeParse(value: string): RecommendationRecord {
  if (!value.trim()) return {};
  try {
    const parsed = JSON.parse(value);
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
      return parsed as RecommendationRecord;
    }
  } catch {
    // Keep the raw editor as the escape hatch for invalid JSON.
  }
  return {};
}

function serializeRecord(record: RecommendationRecord): string {
  return Object.keys(record).length > 0 ? JSON.stringify(record, null, 2) : '';
}

function readScalarNumber(record: RecommendationRecord, key: string): string {
  return typeof record[key] === 'number' ? String(record[key]) : '';
}

function writeScalarNumber(record: RecommendationRecord, key: string, rawValue: string) {
  const next = { ...record };
  const value = rawValue.trim();
  if (!value) {
    delete next[key];
    return next;
  }

  const parsed = Number.parseFloat(value);
  if (Number.isFinite(parsed)) {
    next[key] = parsed;
  }
  return next;
}

function readMetricMin(record: RecommendationRecord, key: string): string {
  const value = record[key];
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    const min = (value as Record<string, unknown>).min;
    if (typeof min === 'number') return String(min);
  }
  return '';
}

function writeMetricMin(record: RecommendationRecord, key: string, rawValue: string) {
  const next = { ...record };
  const value = rawValue.trim();
  if (!value) {
    delete next[key];
    return next;
  }

  const parsed = Number.parseFloat(value);
  if (!Number.isFinite(parsed)) return next;

  const existing =
    next[key] && typeof next[key] === 'object' && !Array.isArray(next[key])
      ? { ...(next[key] as Record<string, unknown>) }
      : {};

  next[key] = { ...existing, min: parsed };
  return next;
}

export function RecommendationEditor({
  title,
  description,
  value,
  onChange,
  mode,
}: RecommendationEditorProps) {
  const record = safeParse(value);

  const updateRecord = (next: RecommendationRecord) => {
    onChange(serializeRecord(next));
  };

  const metricFields =
    mode === 'entry'
      ? [
          { key: 'bodyScore', label: 'Min body score' },
          { key: 'mobilityScore', label: 'Min mobility score' },
          { key: 'overallLevel', label: 'Min overall level' },
        ]
      : [
          { key: 'bodyScore', label: 'Min body score' },
          { key: 'mobilityScore', label: 'Min mobility score' },
          { key: 'controlScore', label: 'Min control score' },
          { key: 'symmetryScore', label: 'Min symmetry score' },
          { key: 'safetyScore', label: 'Min safety score' },
          { key: 'overallLevel', label: 'Min overall level' },
        ];

  return (
    <div className="rounded-xl border border-gray-200 bg-gray-50/70 p-4 space-y-4">
      <div>
        <p className="text-sm font-semibold text-gray-900">{title}</p>
        {description ? <p className="mt-1 text-sm text-gray-500">{description}</p> : null}
      </div>

      <div className="grid grid-cols-2 gap-4">
        {mode === 'entry' ? (
          <>
            <div>
              <Label>Min form score</Label>
              <Input
                type="number"
                min={0}
                max={100}
                value={readScalarNumber(record, 'minFormScore')}
                onChange={(e) => updateRecord(writeScalarNumber(record, 'minFormScore', e.target.value))}
                placeholder="70"
              />
            </div>
            <div>
              <Label>Min completion rate (0-1)</Label>
              <Input
                type="number"
                min={0}
                max={1}
                step="0.05"
                value={readScalarNumber(record, 'minCompletionRate')}
                onChange={(e) =>
                  updateRecord(writeScalarNumber(record, 'minCompletionRate', e.target.value))
                }
                placeholder="0.8"
              />
            </div>
          </>
        ) : (
          <div>
            <Label>Min weeks completed</Label>
            <Input
              type="number"
              min={0}
              value={readScalarNumber(record, 'minWeeksCompleted')}
              onChange={(e) => updateRecord(writeScalarNumber(record, 'minWeeksCompleted', e.target.value))}
              placeholder="4"
            />
          </div>
        )}

        {metricFields.map((field) => (
          <div key={field.key}>
            <Label>{field.label}</Label>
            <Input
              type="number"
              min={0}
              value={readMetricMin(record, field.key)}
              onChange={(e) => updateRecord(writeMetricMin(record, field.key, e.target.value))}
              placeholder="Optional"
            />
          </div>
        ))}
      </div>

      <details className="rounded-lg border border-gray-200 bg-white">
        <summary className="cursor-pointer px-3 py-2 text-sm font-medium text-gray-700">
          Advanced JSON
        </summary>
        <div className="border-t border-gray-100 p-3">
          <Textarea
            value={value}
            onChange={(e) => onChange(e.target.value)}
            rows={6}
            placeholder="{}"
          />
          <p className="mt-2 text-xs text-gray-500">
            Structured fields update this JSON automatically. You can still add advanced keys here.
          </p>
        </div>
      </details>
    </div>
  );
}
