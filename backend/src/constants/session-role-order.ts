/**
 * Canonical ordering for session item roles (warm-up → cooldown).
 * Used when sorting or prioritizing items in effective plan / UI.
 */

import type { SessionItemRole } from '@prisma/client';

export const SESSION_ROLE_ORDER: SessionItemRole[] = [
  'WARMUP',
  'ACTIVATION',
  'MAIN',
  'ACCESSORY',
  'CORRECTIVE',
  'COOLDOWN',
  'TEST',
];

const ORDER_MAP: Record<SessionItemRole, number> = SESSION_ROLE_ORDER.reduce(
  (acc, role, index) => {
    acc[role] = index;
    return acc;
  },
  {} as Record<SessionItemRole, number>,
);

export function compareSessionRoles(a: SessionItemRole | null | undefined, b: SessionItemRole | null | undefined): number {
  const ia = a !== undefined && a !== null ? (ORDER_MAP[a] ?? 99) : 99;
  const ib = b !== undefined && b !== null ? (ORDER_MAP[b] ?? 99) : 99;
  return ia - ib;
}
