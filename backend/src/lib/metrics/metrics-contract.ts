/**
 * Metrics Contract — Single Source of Truth for Metric Scales
 * ============================================================
 *
 * Problem: The same metric name (avgFormScore) means different things
 * depending on which table it comes from:
 *   - SessionMetrics / RepMetrics: Int × 10 (850 = 85.0%)
 *   - ProgramSessionReport:       Float 0-100 (85.0 = 85.0%)
 *
 * Contract:
 *   STORAGE (DB):  Int × 10 for kinematic metrics (SessionMetrics, RepMetrics)
 *                  Float 0-100 for session reports (ProgramSessionReport)
 *   API (output):  Always Float 0-100 (percentages) or natural units
 *
 * The Progression Engine (Phase 4) always works with Float 0-100.
 * This file is the single conversion point.
 */

import { getPrisma } from '@/lib/prisma/client';

// ============================================
// CONVERSION FUNCTIONS
// ============================================

/**
 * Convert Int×10 storage value to API Float 0-100.
 * Used when reading from SessionMetrics / RepMetrics.
 *
 * @example intX10ToFloat(850) → 85.0
 */
export function intX10ToFloat(value: number | null | undefined): number {
  if (value == null) return 0;
  return value / 10;
}

/**
 * Convert API Float 0-100 to Int×10 storage value.
 * Used when writing to SessionMetrics / RepMetrics.
 *
 * @example floatToIntX10(85.0) → 850
 */
export function floatToIntX10(value: number | null | undefined): number {
  if (value == null) return 0;
  return Math.round(value * 10);
}

/**
 * Normalize a form score to Float 0-100 regardless of source.
 * Detects whether the value is Int×10 (>100) or already Float 0-100.
 *
 * This is a safety net for cases where the source is ambiguous.
 * Prefer explicit intX10ToFloat() or direct pass-through when the source is known.
 *
 * @example normalizeFormScore(850)  → 85.0  (detected as Int×10)
 * @example normalizeFormScore(85.0) → 85.0  (already Float 0-100)
 */
export function normalizeFormScore(value: number | null | undefined): number {
  if (value == null) return 0;
  if (value > 100) return value / 10;
  return value;
}

// ============================================
// SCORE TO LEVEL MAPPING (Dynamic DB-backed with cache)
// ============================================

interface CachedLevel {
  number: number;
  entryThreshold: number;
  maxThreshold: number | null;
}

/** In-memory cache for DB-backed levels. */
let _cachedLevels: CachedLevel[] | null = null;
let _cacheTimestamp = 0;
const CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes

/**
 * Default hardcoded levels — used as fallback when DB is unavailable or cache is empty.
 * Matches the original hardcoded scoreToLevel() thresholds.
 */
const DEFAULT_LEVELS: CachedLevel[] = [
  { number: 1, entryThreshold: 0, maxThreshold: 25 },
  { number: 2, entryThreshold: 25, maxThreshold: 45 },
  { number: 3, entryThreshold: 45, maxThreshold: 65 },
  { number: 4, entryThreshold: 65, maxThreshold: 85 },
  { number: 5, entryThreshold: 85, maxThreshold: null },
];

/**
 * Load levels from DB and update the in-memory cache.
 */
async function loadLevelsFromDb(): Promise<CachedLevel[]> {
  try {
    const prisma = await getPrisma();
    const dbLevels = await prisma.level.findMany({
      orderBy: { number: 'asc' },
      select: { number: true, entryThreshold: true, maxThreshold: true },
    });

    if (dbLevels.length > 0) {
      _cachedLevels = dbLevels;
      _cacheTimestamp = Date.now();
      return _cachedLevels;
    }
  } catch (error) {
    console.warn('[Metrics] Failed to load levels from DB, using defaults:', error);
  }

  return DEFAULT_LEVELS;
}

/**
 * Get levels from cache or DB. Returns cached value if within TTL.
 */
export async function getDynamicLevels(): Promise<CachedLevel[]> {
  const now = Date.now();
  if (_cachedLevels && now - _cacheTimestamp < CACHE_TTL_MS) {
    return _cachedLevels;
  }
  return loadLevelsFromDb();
}

/**
 * Force-refresh the levels cache. Call after admin changes levels.
 */
export async function refreshLevelsCache(): Promise<void> {
  await loadLevelsFromDb();
}

/**
 * Map a score (Float 0-100) to a Level number using DB-backed levels.
 * Async version — preferred when caller can await.
 *
 * Levels are sorted by number descending; returns the first level
 * whose entryThreshold the score meets or exceeds.
 */
export async function scoreToLevelAsync(score: number): Promise<number> {
  const levels = await getDynamicLevels();
  return matchScoreToLevel(levels, score);
}

/**
 * Map a score (Float 0-100) to a Level number (1-5).
 *
 * SYNC version — uses the in-memory cache if available, otherwise falls
 * back to hardcoded defaults. This preserves backward compatibility
 * for callers that cannot be async.
 *
 * This is THE function used everywhere: backend, Android, admin dashboard.
 * Must be kept in sync with Android's FitnessLevel.fromBodyScore().
 *
 *   Default thresholds (when DB not loaded):
 *   0-24   → Level 1 (Foundation / needs_rehab)
 *   25-44  → Level 2 (Building / limited)
 *   45-64  → Level 3 (Intermediate / average)
 *   65-84  → Level 4 (Advanced / good)
 *   85-100 → Level 5 (Elite / excellent)
 */
export function scoreToLevel(score: number): number {
  const levels = _cachedLevels ?? DEFAULT_LEVELS;
  return matchScoreToLevel(levels, score);
}

/**
 * Sync fallback alias — explicit name for callers that know they need sync access.
 */
export const scoreToLevelSync = scoreToLevel;

/**
 * Core matching logic: find the highest-numbered level whose entryThreshold ≤ score.
 */
function matchScoreToLevel(levels: CachedLevel[], score: number): number {
  // Walk from highest level down; first match wins
  for (let i = levels.length - 1; i >= 0; i--) {
    if (score >= levels[i].entryThreshold) {
      return levels[i].number;
    }
  }
  return levels.length > 0 ? levels[0].number : 1;
}

// ============================================
// LEVEL METADATA (static fallback)
// ============================================

/**
 * Level metadata — display info for each level.
 * Kept as a hardcoded fallback; prefer reading from the DB Level table.
 */
export const LEVELS = [
  {
    number: 1,
    code: 'foundation',
    name: { en: 'Foundation', ar: 'أساسي' },
    description: {
      en: 'Safety first. Corrective focus. Bodyweight only.',
      ar: 'السلامة أولاً. تركيز تصحيحي. وزن الجسم فقط.',
    },
    entryThreshold: 0,
    color: '#FF5252',
  },
  {
    number: 2,
    code: 'building',
    name: { en: 'Building', ar: 'بناء' },
    description: {
      en: 'Basic movement patterns. Light resistance.',
      ar: 'أنماط حركة أساسية. مقاومة خفيفة.',
    },
    entryThreshold: 25,
    color: '#FF9800',
  },
  {
    number: 3,
    code: 'intermediate',
    name: { en: 'Intermediate', ar: 'متوسط' },
    description: {
      en: 'Progressive overload. Moderate intensity.',
      ar: 'زيادة تدريجية. شدة متوسطة.',
    },
    entryThreshold: 45,
    color: '#FFC107',
  },
  {
    number: 4,
    code: 'advanced',
    name: { en: 'Advanced', ar: 'متقدم' },
    description: {
      en: 'Complex movements. Heavy loads.',
      ar: 'حركات معقدة. أحمال ثقيلة.',
    },
    entryThreshold: 65,
    color: '#8BC34A',
  },
  {
    number: 5,
    code: 'elite',
    name: { en: 'Elite', ar: 'نخبة' },
    description: {
      en: 'Performance optimization. Peak training.',
      ar: 'تحسين الأداء. تدريب الذروة.',
    },
    entryThreshold: 85,
    color: '#4CAF50',
  },
] as const;

/**
 * Get level metadata by number.
 */
export function getLevelByNumber(level: number) {
  return LEVELS.find((l) => l.number === level) ?? LEVELS[0];
}

/**
 * Map fitnessLevel string to level number.
 * Aligns with the existing FitnessLevel enum values in the system.
 */
export function fitnessLevelToNumber(fitnessLevel: string): number {
  switch (fitnessLevel) {
    case 'excellent':
      return 5;
    case 'good':
      return 4;
    case 'average':
      return 3;
    case 'limited':
      return 2;
    case 'needs_rehab':
      return 1;
    default:
      return 1;
  }
}

/**
 * Map level number to fitnessLevel string.
 */
export function levelNumberToFitnessLevel(level: number): string {
  switch (level) {
    case 5:
      return 'excellent';
    case 4:
      return 'good';
    case 3:
      return 'average';
    case 2:
      return 'limited';
    case 1:
    default:
      return 'needs_rehab';
  }
}
