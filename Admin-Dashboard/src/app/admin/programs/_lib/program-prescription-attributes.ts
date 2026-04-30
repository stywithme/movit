import type { Attribute } from '@/app/admin/attributes/types';

/** Attribute types shown on program prescription / builder / map filters. */
export const PRESCRIPTION_ATTRIBUTE_CODES = [
  'domain',
  'goal',
  'equipment',
  'gender',
  'place',
  'body_region',
  'focus',
] as const;

export type ProgramAttributeMode = 'REQUIRED' | 'OPTIONAL' | 'EXCLUDED';

export interface ProgramAttributeFormRow {
  attributeValueId: string;
  mode: ProgramAttributeMode;
}

export function filterPrescriptionAttributes(catalog: Attribute[]): Attribute[] {
  const allow = new Set<string>(PRESCRIPTION_ATTRIBUTE_CODES);
  return catalog.filter((a) => allow.has(a.code));
}

export function buildValueIdMeta(attributes: Attribute[]): Map<
  string,
  { valueCode: string; attributeCode: string }
> {
  const m = new Map<string, { valueCode: string; attributeCode: string }>();
  for (const a of attributes) {
    for (const v of a.values) {
      m.set(v.id, { valueCode: v.code, attributeCode: a.code });
    }
  }
  return m;
}
