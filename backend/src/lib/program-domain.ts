import type { ProgramDomain } from '@prisma/client';

/** Legacy program "type" string (domain) from API / mobile. */
export type LegacyProgramTypeString = 'training' | 'mobility' | 'therapeutic';

/**
 * Map legacy lowercase domain string to Prisma ProgramDomain enum.
 */
export function legacyTypeToProgramDomain(legacy?: string | null): ProgramDomain {
  if (legacy === 'mobility') return 'MOBILITY';
  if (legacy === 'therapeutic') return 'THERAPEUTIC';
  return 'TRAINING';
}

/**
 * Map Prisma ProgramDomain to legacy string for APIs that still expose `type`.
 */
export function programDomainToLegacyString(domain: ProgramDomain): LegacyProgramTypeString {
  switch (domain) {
    case 'MOBILITY':
      return 'mobility';
    case 'THERAPEUTIC':
      return 'therapeutic';
    default:
      return 'training';
  }
}
