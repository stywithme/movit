'use client';

import { useEffect, useState } from 'react';
import type { Attribute, AttributeValue } from '@/app/admin/attributes/types';
import { Label } from '@/components/ui';
import {
  filterPrescriptionAttributes,
  type ProgramAttributeFormRow,
  type ProgramAttributeMode,
} from '../_lib/program-prescription-attributes';

function labelEn(v: AttributeValue) {
  return v.name?.en || v.name?.ar || v.code;
}

const MODE_CYCLE: Array<ProgramAttributeMode | null> = [null, 'REQUIRED', 'OPTIONAL', 'EXCLUDED'];

function modeForValue(rows: ProgramAttributeFormRow[], valueId: string): ProgramAttributeMode | null {
  return rows.find((r) => r.attributeValueId === valueId)?.mode ?? null;
}

function setRowMode(
  rows: ProgramAttributeFormRow[],
  valueId: string,
  next: ProgramAttributeMode | null,
  onChange: (nextRows: ProgramAttributeFormRow[]) => void,
) {
  const filtered = rows.filter((r) => r.attributeValueId !== valueId);
  if (next) {
    onChange([...filtered, { attributeValueId: valueId, mode: next }]);
  } else {
    onChange(filtered);
  }
}

function cycleMode(current: ProgramAttributeMode | null): ProgramAttributeMode | null {
  const idx = MODE_CYCLE.indexOf(current);
  return MODE_CYCLE[(idx + 1) % MODE_CYCLE.length];
}

const MODE_STYLE: Record<ProgramAttributeMode, string> = {
  REQUIRED: 'border-blue-500 bg-blue-50 text-blue-900',
  OPTIONAL: 'border-gray-300 bg-gray-50 text-gray-800',
  EXCLUDED: 'border-red-400 bg-red-50 text-red-900',
};

interface ProgramAttributesSectionProps {
  /** Full attribute catalog (will be filtered to prescription types). */
  catalog: Attribute[];
  value: ProgramAttributeFormRow[];
  onChange: (rows: ProgramAttributeFormRow[]) => void;
}

/**
 * Multi-select per attribute value with REQUIRED / OPTIONAL / EXCLUDED cycle (fourth click clears).
 */
export function ProgramAttributesSection({ catalog, value, onChange }: ProgramAttributesSectionProps) {
  const prescriptionAttrs = filterPrescriptionAttributes(catalog);

  return (
    <div className="space-y-6">
      <p className="text-sm text-gray-600">
        Set how each value applies to prescription and the builder. Click a value to cycle: off → Required →
        Optional → Excluded → off.
      </p>
      {prescriptionAttrs.map((attr) => (
        <div key={attr.id} className="rounded-lg border border-gray-200 p-4">
          <Label className="text-sm font-semibold text-gray-900">
            {attr.name?.en || attr.name?.ar || attr.code}
          </Label>
          <p className="text-xs text-gray-500 mb-3">{attr.code}</p>
          <div className="flex flex-wrap gap-2">
            {attr.values
              .filter((v) => v.isActive)
              .map((v) => {
                const m = modeForValue(value, v.id);
                return (
                  <button
                    key={v.id}
                    type="button"
                    onClick={() => setRowMode(value, v.id, cycleMode(m), onChange)}
                    className={`rounded-md border px-2.5 py-1 text-xs font-medium transition-colors ${
                      m ? MODE_STYLE[m] : 'border-dashed border-gray-300 bg-white text-gray-500'
                    }`}
                  >
                    {labelEn(v)}
                    {m ? ` · ${m}` : ''}
                  </button>
                );
              })}
          </div>
        </div>
      ))}
      {prescriptionAttrs.length === 0 ? (
        <p className="text-sm text-amber-700">No prescription attribute types loaded yet.</p>
      ) : null}
    </div>
  );
}

/** Fetch full attribute catalog once (parent can lift state). */
export function useAttributesCatalog() {
  const [catalog, setCatalog] = useState<Attribute[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const res = await fetch('/api/attributes?includeInactive=false');
        const data = await res.json();
        if (cancelled) return;
        if (data.success && Array.isArray(data.data)) {
          setCatalog(data.data as Attribute[]);
        } else {
          setError(data.error || 'Failed to load attributes');
        }
      } catch {
        if (!cancelled) setError('Failed to load attributes');
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  return { catalog, loading, error };
}
