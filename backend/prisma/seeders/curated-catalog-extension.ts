import type { LoadCapability, MovementPattern, Prisma } from '@prisma/client';
import { CURATED_EXTENSION_ROWS } from './curated-extension-rows';

/**
 * Curated library exercises used only when no JSON exists in `exercises-from-db` for the same slug.
 * Each row closes coverage gaps; the seeder passes `skipSlugs` so JSON remains authoritative.
 */

export type CuratedExtensionExercise = {
  slug: string;
  name: { ar: string; en: string };
  description: { ar: string; en: string };
  instructions: { ar: string; en: string };
  categoryCode: string;
  countingMethodCode: 'up_down' | 'hold';
  muscles: string[];
  equipment: string[];
  tags: string[];
  supportsWeight: boolean;
  isBilateral: boolean;
  movementPattern: MovementPattern;
  loadCapability: LoadCapability;
  familyKey: string;
  familyOrder: number;
  minWeight?: number | null;
  maxWeight?: number | null;
  defaultWeight?: number | null;
  /** Optional; passed through to Exercise when set. */
  intent?: string | null;
  coachingNotes?: Prisma.InputJsonValue | null;
};

function rowToExercise(
  row: (typeof CURATED_EXTENSION_ROWS)[number],
): CuratedExtensionExercise {
  const [slug, en, ar, categoryCode, countingMethodCode, mp, lc, fk, fo, muscles, equipment, tags, opts] = row;
  return {
    slug,
    name: { en, ar },
    description: {
      en: `Library exercise: ${en}.`,
      ar: `تمرين مكتبة: ${ar}.`,
    },
    instructions: {
      en: 'Maintain neutral spine, steady breathing, and stop if sharp pain appears.',
      ar: 'حافظ على عمود فقري محايد وتنفس بثبات وتوقف عند أي ألم حاد.',
    },
    categoryCode,
    countingMethodCode,
    muscles: [...muscles],
    equipment: [...equipment],
    tags: ['curated_library', ...tags],
    supportsWeight: opts?.sw ?? false,
    isBilateral: opts?.bi ?? false,
    movementPattern: mp as MovementPattern,
    loadCapability: lc as LoadCapability,
    familyKey: fk,
    familyOrder: fo,
    minWeight: opts?.minW ?? null,
    maxWeight: opts?.maxW ?? null,
    defaultWeight: opts?.defW ?? null,
  };
}

export const CURATED_EXTENSION_EXERCISES: CuratedExtensionExercise[] =
  CURATED_EXTENSION_ROWS.map(rowToExercise);
