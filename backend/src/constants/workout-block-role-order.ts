/**
 * Canonical ordering for planned workout block roles (warm-up → cooldown).
 * Used when sorting or prioritizing items in effective plan / UI.
 */

import type { WorkoutBlockRole } from '@prisma/client';

export const WORKOUT_BLOCK_ROLE_ORDER: WorkoutBlockRole[] = [
  'WARMUP',
  'ACTIVATION',
  'MAIN',
  'ACCESSORY',
  'CORRECTIVE',
  'COOLDOWN',
  'TEST',
];

const ORDER_MAP: Record<WorkoutBlockRole, number> = WORKOUT_BLOCK_ROLE_ORDER.reduce(
  (acc, role, index) => {
    acc[role] = index;
    return acc;
  },
  {} as Record<WorkoutBlockRole, number>,
);

export function compareWorkoutBlockRoles(a: WorkoutBlockRole | null | undefined, b: WorkoutBlockRole | null | undefined): number {
  const ia = a !== undefined && a !== null ? (ORDER_MAP[a] ?? 99) : 99;
  const ib = b !== undefined && b !== null ? (ORDER_MAP[b] ?? 99) : 99;
  return ia - ib;
}
